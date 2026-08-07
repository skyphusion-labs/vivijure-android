package org.skyphusion.vivijure

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.skyphusion.vivijure.kit.CastMember
import org.skyphusion.vivijure.kit.ModulesResponse
import org.skyphusion.vivijure.kit.PlanCastSlot
import org.skyphusion.vivijure.kit.PreflightResponse
import org.skyphusion.vivijure.kit.RenderConfigSchema
import org.skyphusion.vivijure.kit.RenderRow
import org.skyphusion.vivijure.kit.SceneEdit
import org.skyphusion.vivijure.kit.StoryboardHelpers
import org.skyphusion.vivijure.kit.StoryboardProject
import org.skyphusion.vivijure.kit.VivijureClient
import org.skyphusion.vivijure.kit.WhoamiResponse
import org.skyphusion.vivijure.kit.plannerSlotIds
import org.skyphusion.vivijure.kit.pretty

enum class PlannerStep(val label: String) {
  Plan("Plan"),
  CastBundle("Cast & Bundle"),
  Audio("Audio"),
  Render("Render"),
  History("History"),
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
  private val store = TokenStore(app)

  var studioUrl by mutableStateOf(store.studioUrl)
    private set
  var isConfigured by mutableStateOf(store.isConfigured)
    private set
  var whoami by mutableStateOf<WhoamiResponse?>(null)
    private set
  var modules by mutableStateOf<ModulesResponse?>(null)
    private set
  var statusMessage by mutableStateOf("")
  var lastError by mutableStateOf<String?>(null)
  var busy by mutableStateOf(false)
    private set

  var projects by mutableStateOf<List<StoryboardProject>>(emptyList())
  var selectedProjectId by mutableStateOf<Int?>(null)
  var brief by mutableStateOf("")
  var planModel by mutableStateOf("")
  var availableModels = mutableStateListOf<String>()
  var storyboard by mutableStateOf<JsonElement?>(null)
  var sceneEdits = mutableStateListOf<SceneEdit>()
  var preflight by mutableStateOf<PreflightResponse?>(null)
  var bundleKey by mutableStateOf<String?>(null)
  var renderJobId by mutableStateOf<String?>(null)
  var renderStatus by mutableStateOf("")
  var cast by mutableStateOf<List<CastMember>>(emptyList())
  var renders by mutableStateOf<List<RenderRow>>(emptyList())
  var plannerStep by mutableStateOf(PlannerStep.Plan)
  var qualityTier by mutableStateOf("final")
  var keyframesOnly by mutableStateOf(false)
  var useScatter by mutableStateOf(false)
  var scatterShards by mutableStateOf(2)
  var motionBackend by mutableStateOf("")
  var audioKey by mutableStateOf<String?>(null)
  var bpm by mutableStateOf(120.0)
  var scorePrompt by mutableStateOf("")
  var refineInstruction by mutableStateOf("")
  var expertJson by mutableStateOf("")
  var yamlPreview by mutableStateOf("")
  var castSlots = mutableStateListOf<PlanCastSlot>().apply {
    addAll(plannerSlotIds.map { PlanCastSlot(it) })
  }
  var sceneStartImages = mutableStateMapOf<String, String>()
  var renderFieldValues = mutableStateMapOf<String, JsonElement>()
  var renderTags by mutableStateOf<List<String>>(emptyList())
  var cloudAnimateModel by mutableStateOf("")
  var cloudPerShot = mutableStateMapOf<String, String>()
  var hybridPerShot = mutableStateMapOf<String, String>()
  var installedModules by mutableStateOf<List<JsonElement>>(emptyList())
  var moduleConfigName by mutableStateOf("")
  var moduleConfigJson by mutableStateOf("")
  var prefsJson by mutableStateOf("")
  var storageSummary by mutableStateOf("")
  var demoAvailable by mutableStateOf<Boolean?>(null)
  var demoScenes by mutableStateOf<List<JsonElement>>(emptyList())
  var demoStatus by mutableStateOf("")
  var notifyOnRender by mutableStateOf(false)
  var selectedCastId by mutableStateOf<String?>(null)

  private var client: VivijureClient? = null
  private var pollJob: Job? = null
  private val appContext = app.applicationContext

  val castBindings: Map<String, String>
    get() =
      castSlots
        .filter { it.included && it.boundCastId.isNotBlank() }
        .associate { it.letter to it.boundCastId }

  init {
    notifyOnRender = store.notifyOnRender
    if (isConfigured) {
      client = VivijureClient(store.studioUrl, store.token)
      restoreSession()
    }
  }

  fun saveCredentials(url: String, token: String) {
    store.studioUrl = url.trim()
    store.token = token.trim()
    studioUrl = store.studioUrl
    isConfigured = store.isConfigured
    client = if (isConfigured) VivijureClient(store.studioUrl, store.token) else null
  }

  fun signOut() {
    store.clearToken()
    isConfigured = false
    client = null
    whoami = null
    modules = null
    clearPlanner()
  }

  fun clearPlanner() {
    brief = ""
    storyboard = null
    sceneEdits.clear()
    preflight = null
    bundleKey = null
    renderJobId = null
    renderStatus = ""
    audioKey = null
    plannerStep = PlannerStep.Plan
    selectedProjectId = null
    sceneStartImages.clear()
    persistSession()
  }

  fun enableRenderNotifications(on: Boolean) {
    notifyOnRender = on
    store.notifyOnRender = on
  }

  fun bootstrap() {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      lastError = null
      try {
        withContext(Dispatchers.IO) {
          whoami = c.whoami()
          modules = c.modules()
          projects = c.listProjects()
          cast = c.listCast()
          val models = runCatching { c.storyboardModels() }.getOrNull()
          availableModels.clear()
          models?.models?.forEach { el ->
            val id =
              el.jsonPrimitive.contentOrNull
                ?: el.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                ?: el.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            if (id != null) availableModels.add(id)
          }
          if (planModel.isBlank()) {
            planModel = models?.defaultModel ?: availableModels.firstOrNull() ?: "claude-sonnet-4-5"
          }
          qualityTier = modules?.defaultQualityTier ?: "final"
          val backends = modules?.motionBackends().orEmpty()
          if (motionBackend.isBlank() && backends.size == 1) motionBackend = backends.first()
          val cloud = backends.filter { !it.contains("own-gpu") }
          if (cloudAnimateModel.isBlank()) cloudAnimateModel = cloud.firstOrNull().orEmpty()
          installedModules = runCatching { c.listInstalledModules() }.getOrDefault(emptyList())
          prefsJson =
            runCatching { c.getPrefs().pretty() }.getOrDefault("{}")
          val menu = runCatching { c.demoMenu() }.getOrNull()
          demoAvailable = menu?.available
          demoScenes = menu?.scenes.orEmpty()
        }
        statusMessage = "Connected as ${whoami?.user ?: whoami?.email ?: "studio"}"
        // Resume in-flight poll after relaunch
        val jid = renderJobId
        if (!jid.isNullOrBlank() && !isTerminal(renderStatus)) {
          statusMessage = "Resuming poll for $jid"
          startPoll(c, jid)
        }
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  private fun isTerminal(status: String): Boolean =
    listOf("COMPLETED", "FAILED", "CANCELLED", "done", "failed")
      .any { it.equals(status, ignoreCase = true) }

  fun persistSession() {
    val slots =
      castSlots.map {
        buildJsonObject {
          put("letter", it.letter)
          put("included", it.included)
          put("boundCastId", it.boundCastId)
          put("inlineName", it.inlineName)
          put("inlineBible", it.inlineBible)
        }
      }
    val blob =
      buildJsonObject {
        put("brief", brief)
        put("planModel", planModel)
        selectedProjectId?.let { put("selectedProjectId", it) }
        storyboard?.let { put("storyboard", it) }
        bundleKey?.let { put("bundleKey", it) }
        audioKey?.let { put("audioKey", it) }
        put("qualityTier", qualityTier)
        renderJobId?.let { put("renderJobId", it) }
        put("renderStatus", renderStatus)
        put("plannerStep", plannerStep.name)
        put("bpm", bpm)
        put("motionBackend", motionBackend)
        put("keyframesOnly", keyframesOnly)
        put("expertJson", expertJson)
        put("scorePrompt", scorePrompt)
        put("slots", buildJsonArray { slots.forEach { add(it) } })
        put(
          "sceneStartImages",
          buildJsonObject { sceneStartImages.forEach { (k, v) -> put(k, v) } },
        )
      }
    store.sessionJson = blob.pretty()
  }

  private fun restoreSession() {
    val raw = store.sessionJson
    if (raw.isBlank()) return
    runCatching {
      val o = org.skyphusion.vivijure.kit.studioJson.parseToJsonElement(raw).jsonObject
      brief = o["brief"]?.jsonPrimitive?.contentOrNull.orEmpty()
      planModel = o["planModel"]?.jsonPrimitive?.contentOrNull.orEmpty()
      selectedProjectId = o["selectedProjectId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
      storyboard = o["storyboard"]
      storyboard?.let {
        sceneEdits.clear()
        sceneEdits.addAll(StoryboardHelpers.scenesFrom(it))
      }
      bundleKey = o["bundleKey"]?.jsonPrimitive?.contentOrNull
      audioKey = o["audioKey"]?.jsonPrimitive?.contentOrNull
      qualityTier = o["qualityTier"]?.jsonPrimitive?.contentOrNull ?: "final"
      renderJobId = o["renderJobId"]?.jsonPrimitive?.contentOrNull
      renderStatus = o["renderStatus"]?.jsonPrimitive?.contentOrNull.orEmpty()
      o["plannerStep"]?.jsonPrimitive?.contentOrNull?.let { name ->
        PlannerStep.entries.find { it.name == name }?.let { plannerStep = it }
      }
      bpm = o["bpm"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 120.0
      motionBackend = o["motionBackend"]?.jsonPrimitive?.contentOrNull.orEmpty()
      keyframesOnly = o["keyframesOnly"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
      expertJson = o["expertJson"]?.jsonPrimitive?.contentOrNull.orEmpty()
      scorePrompt = o["scorePrompt"]?.jsonPrimitive?.contentOrNull.orEmpty()
      o["slots"]?.jsonArray?.forEach { el ->
        val s = el.jsonObject
        val letter = s["letter"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        val slot = castSlots.find { it.letter == letter } ?: return@forEach
        slot.included = s["included"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        slot.boundCastId = s["boundCastId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        slot.inlineName = s["inlineName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        slot.inlineBible = s["inlineBible"]?.jsonPrimitive?.contentOrNull.orEmpty()
      }
      sceneStartImages.clear()
      o["sceneStartImages"]?.jsonObject?.forEach { (k, v) ->
        v.jsonPrimitive.contentOrNull?.let { sceneStartImages[k] = it }
      }
    }
  }

  private fun charactersJson(): JsonElement? {
    val chars =
      castSlots.filter { it.included }.mapNotNull { slot ->
        if (slot.boundCastId.isNotBlank()) {
          val m = cast.find { it.id == slot.boundCastId } ?: return@mapNotNull null
          buildJsonObject {
            put("slot", slot.letter)
            put("name", m.name)
            put("bible", m.bible.orEmpty())
          }
        } else {
          val name = slot.inlineName.trim()
          if (name.isEmpty()) return@mapNotNull null
          buildJsonObject {
            put("slot", slot.letter)
            put("name", name)
            put("bible", slot.inlineBible)
          }
        }
      }
    return if (chars.isEmpty()) null else buildJsonArray { chars.forEach { add(it) } }
  }

  fun runPlan() {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      lastError = null
      try {
        val resp =
          withContext(Dispatchers.IO) {
            c.plan(brief, planModel.ifBlank { null }, charactersJson())
          }
        if (resp.error != null) {
          lastError = resp.error
          return@launch
        }
        val sb = resp.storyboard ?: run {
          lastError = "Plan returned no storyboard"
          return@launch
        }
        applyStoryboard(sb)
        selectedProjectId?.let { pid ->
          withContext(Dispatchers.IO) { c.saveStoryboard(pid, sb) }
          projects = withContext(Dispatchers.IO) { c.listProjects() }
        }
        plannerStep = PlannerStep.CastBundle
        statusMessage = "Plan ready (${sceneEdits.size} scenes)"
        persistSession()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun runRefine() {
    val c = client ?: return
    val sb = storyboard ?: return
    val instruction = refineInstruction.trim()
    if (instruction.isEmpty()) {
      lastError = "Refine instruction required"
      return
    }
    viewModelScope.launch {
      busy = true
      try {
        val resp =
          withContext(Dispatchers.IO) {
            c.refine(sb, instruction, planModel.ifBlank { null })
          }
        if (resp.error != null) {
          lastError = resp.error
          return@launch
        }
        val next = resp.storyboard ?: return@launch
        applyStoryboard(next)
        refineInstruction = ""
        statusMessage = "Refined"
        persistSession()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  private fun applyStoryboard(sb: JsonElement) {
    storyboard = sb
    sceneEdits.clear()
    sceneEdits.addAll(StoryboardHelpers.scenesFrom(sb))
    viewModelScope.launch {
      val c = client ?: return@launch
      yamlPreview =
        runCatching {
          withContext(Dispatchers.IO) { c.storyboardYaml(sb) }.yaml
            ?: "yaml failed"
        }.getOrElse { it.message.orEmpty() }
    }
    persistSession()
  }

  fun commitSceneEdits() {
    val sb = storyboard ?: return
    applyStoryboard(StoryboardHelpers.applyScenes(sceneEdits, sb))
    preflight = null
    bundleKey = null
  }

  fun deleteScene(index: Int) {
    val sb = storyboard ?: return
    applyStoryboard(StoryboardHelpers.deleteScene(index, sb))
    preflight = null
    bundleKey = null
  }

  fun selectProject(id: Int?) {
    selectedProjectId = id
    val c = client ?: return
    if (id == null) {
      persistSession()
      return
    }
    viewModelScope.launch {
      try {
        val p = withContext(Dispatchers.IO) { c.getProject(id) }
        p.lastStoryboard?.let {
          applyStoryboard(it)
          statusMessage = "Loaded project storyboard"
        }
        persistSession()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun deleteSelectedProject() {
    val c = client ?: return
    val id = selectedProjectId ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.deleteProject(id) }
        selectedProjectId = null
        projects = withContext(Dispatchers.IO) { c.listProjects() }
        statusMessage = "Project deleted"
        persistSession()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun saveStoryboardToProject() {
    val c = client ?: return
    val sb = storyboard ?: return
    val pid = selectedProjectId ?: run {
      lastError = "Select a project"
      return
    }
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.saveStoryboard(pid, sb) }
        projects = withContext(Dispatchers.IO) { c.listProjects() }
        statusMessage = "Storyboard saved"
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun stageSceneStart(sceneId: String, bytes: ByteArray, mime: String) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val up = withContext(Dispatchers.IO) { c.uploadCharacterRef(bytes, mime) }
        sceneStartImages[sceneId] = up.key
        statusMessage = "Staged start for $sceneId"
        persistSession()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun clearSceneStart(sceneId: String) {
    sceneStartImages.remove(sceneId)
    persistSession()
  }

  fun runPreflight() {
    val c = client ?: return
    val sb = storyboard ?: return
    viewModelScope.launch {
      busy = true
      try {
        preflight =
          withContext(Dispatchers.IO) {
            c.preflight(sb, castBindings.ifEmpty { null }, qualityTier)
          }
        statusMessage = if (preflight?.ok == true) "Preflight OK" else "Preflight has issues"
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun runBundle() {
    val c = client ?: return
    val sb = storyboard ?: return
    viewModelScope.launch {
      busy = true
      try {
        val use = StoryboardHelpers.useCharacters(sb)
        val refs =
          if (use.isEmpty()) buildJsonObject {}
          else StoryboardHelpers.characterRefs(use, castBindings, cast)
        if (use.isNotEmpty()) {
          for (slot in use) {
            val entry = refs[slot]?.jsonObject
            val imgs = entry?.get("trainingImages")?.jsonArray
            if (imgs == null || imgs.isEmpty()) {
              lastError =
                "Slot $slot has no training images. Bind a cast member with portrait/refs."
              return@launch
            }
          }
        }
        val starts =
          if (sceneStartImages.isEmpty()) null
          else
            buildJsonObject {
              sceneStartImages.forEach { (id, key) ->
                put(id, buildJsonObject { put("key", key) })
              }
            }
        val resp = withContext(Dispatchers.IO) { c.bundle(sb, refs, starts) }
        val key = resp.key
        if (key == null) {
          lastError = resp.error ?: "Bundle returned no key"
          return@launch
        }
        bundleKey = key
        plannerStep = PlannerStep.Render
        statusMessage = "Bundled: $key"
        persistSession()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun runRender() {
    val c = client ?: return
    val sb = storyboard ?: return
    viewModelScope.launch {
      busy = true
      lastError = null
      try {
        if (useScatter) {
          runScatter(c, sb)
          return@launch
        }
        val backends = modules?.motionBackends().orEmpty()
        if (!keyframesOnly && backends.size > 1 && motionBackend.isBlank()) {
          lastError = "Pick a motion backend"
          return@launch
        }
        val overrides = buildOverrides()
        val body =
          buildJsonObject {
            put("storyboard", sb)
            bundleKey?.let { put("bundleKey", it) }
            put("qualityTier", qualityTier)
            selectedProjectId?.let { put("projectId", it) }
            if (castBindings.isNotEmpty()) {
              put("castLoras", buildJsonObject { castBindings.forEach { (k, v) -> put(k, v) } })
            }
            if (keyframesOnly) put("keyframesOnly", true)
            if (!keyframesOnly && motionBackend.isNotBlank()) put("motion_backend", motionBackend)
            audioKey?.let { put("audioKey", it) }
            if (overrides.isNotEmpty()) put("renderOverrides", overrides)
          }
        val job = withContext(Dispatchers.IO) { c.submitStoryboardRender(body) }
        val jid = job.resolvedJobId
        if (jid == null) {
          lastError = job.error ?: "No job id"
          return@launch
        }
        renderJobId = jid
        renderStatus = job.status ?: job.phase ?: "submitted"
        plannerStep = PlannerStep.History
        statusMessage = "Render $jid"
        persistSession()
        startPoll(c, jid)
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  private suspend fun runScatter(c: VivijureClient, sb: JsonElement) {
    val key = bundleKey
    if (key == null) {
      lastError = "Scatter needs a bundle"
      return
    }
    val shots = StoryboardHelpers.sceneIds(sb)
    if (shots.size < 2) {
      lastError = "Scatter requires >= 2 shots"
      return
    }
    if (castBindings.isEmpty()) {
      lastError = "Scatter requires bound cast"
      return
    }
    if (motionBackend.isBlank()) {
      lastError = "Scatter requires motion backend"
      return
    }
    selectedProjectId?.let { withContext(Dispatchers.IO) { c.saveStoryboard(it, sb) } }
    var shards = scatterShards.coerceAtLeast(2).coerceAtMost(shots.size)
    val body =
      buildJsonObject {
        put("bundleKey", key)
        put("shotIds", buildJsonArray { shots.forEach { add(JsonPrimitive(it)) } })
        put("shardCount", shards)
        put("qualityTier", qualityTier)
        put("castLoras", buildJsonObject { castBindings.forEach { (k, v) -> put(k, v) } })
        put("motion_backend", motionBackend)
        audioKey?.let { put("audioKey", it) }
        selectedProjectId?.let { put("projectId", it) }
        val overrides = buildOverrides()
        if (overrides.isNotEmpty()) put("renderOverrides", overrides)
      }
    val job = withContext(Dispatchers.IO) { c.submitScatter(body) }
    val jid = job.resolvedJobId ?: run {
      lastError = job.error ?: "No scatter job id"
      return
    }
    renderJobId = jid
    renderStatus = job.status ?: "submitted"
    plannerStep = PlannerStep.History
    statusMessage = "Scatter $jid"
    persistSession()
    startPoll(c, jid)
  }

  private fun buildOverrides(): JsonObject {
    var base =
      RenderConfigSchema.buildOverrides(
        if (keyframesOnly) null else motionBackend.ifBlank { null },
        renderFieldValues,
      )
    val expert = RenderConfigSchema.parseExpertJson(expertJson)
    if (expert != null) base = RenderConfigSchema.mergeExpert(base, expert)
    return base
  }

  private fun startPoll(c: VivijureClient, jid: String) {
    pollJob?.cancel()
    pollJob =
      viewModelScope.launch {
        repeat(120) {
          try {
            val job = withContext(Dispatchers.IO) { c.pollStoryboardRender(jid) }
            renderStatus = job.status ?: job.phase ?: renderStatus
            persistSession()
            if (isTerminal(renderStatus)) {
              statusMessage = "Render $renderStatus"
              if (notifyOnRender) {
                NotificationHelper.notifyRenderDone(appContext, jid, renderStatus)
              }
              refreshHistory()
              return@launch
            }
          } catch (e: Exception) {
            lastError = e.message
            return@launch
          }
          delay(8_000)
        }
      }
  }

  fun refreshHistory() {
    val c = client ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) {
          renders = c.listRenders(selectedProjectId)
          renderTags = runCatching { c.listRenderTags() }.getOrDefault(emptyList())
        }
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun refreshCast() {
    val c = client ?: return
    viewModelScope.launch {
      try {
        cast = withContext(Dispatchers.IO) { c.listCast() }
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun createProject(name: String) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        val p = withContext(Dispatchers.IO) { c.createProject(name) }
        selectedProjectId = p.id
        projects = withContext(Dispatchers.IO) { c.listProjects() }
        persistSession()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun createCast(name: String, bible: String?) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.createCast(name, bible) }
        refreshCast()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun patchCast(id: String, name: String?, bible: String?) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.patchCast(id, name, bible) }
        refreshCast()
        statusMessage = "Cast updated"
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun deleteCast(id: String) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.deleteCast(id) }
        if (selectedCastId == id) selectedCastId = null
        castSlots.forEach { if (it.boundCastId == id) it.boundCastId = "" }
        refreshCast()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun uploadCastImage(id: String, kind: String, bytes: ByteArray, mime: String) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        withContext(Dispatchers.IO) { c.uploadCastImage(id, kind, bytes, mime) }
        refreshCast()
        statusMessage = "Uploaded $kind"
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun trainCast(id: String, wan: Boolean) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val r = withContext(Dispatchers.IO) { c.trainLora(id, wan) }
        statusMessage = "Train ${if (wan) "Wan" else "SDXL"}: ${r.pretty().take(120)}"
        refreshCast()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun generateCastRefs(id: String) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val start = withContext(Dispatchers.IO) { c.generateRefs(id) }
        val jid =
          start.jsonObject["job_id"]?.jsonPrimitive?.contentOrNull
            ?: start.jsonObject["jobId"]?.jsonPrimitive?.contentOrNull
        if (jid != null) {
          statusMessage = "refs job $jid"
          repeat(60) {
            val job = withContext(Dispatchers.IO) { c.pollRefsJob(id, jid) }
            val phase = job.jsonObject["phase"]?.jsonPrimitive?.contentOrNull.orEmpty()
            statusMessage = "refs $jid: $phase"
            if (phase in listOf("done", "failed")) return@repeat
            delay(3_000)
          }
        } else {
          statusMessage = start.pretty().take(120)
        }
        refreshCast()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun exportCast(id: String, onBytes: (ByteArray, String) -> Unit) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val bytes = withContext(Dispatchers.IO) { c.exportCast(id) }
        val name = cast.find { it.id == id }?.name?.replace(" ", "-") ?: id
        onBytes(bytes, "$name.vvcast")
        statusMessage = "Exported $name.vvcast"
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun importCast(bytes: ByteArray) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val m = withContext(Dispatchers.IO) { c.importCast(bytes) }
        statusMessage = "Imported ${m.name}"
        refreshCast()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun scoreBed() {
    val c = client ?: return
    val sb = storyboard ?: return
    viewModelScope.launch {
      busy = true
      try {
        val prompt =
          scorePrompt.ifBlank { "cinematic underscore matching the storyboard mood" }
        val resp =
          withContext(Dispatchers.IO) {
            c.scoreBed(
              buildJsonObject {
                put("storyboard", sb)
                put("prompt", prompt)
              },
            )
          }
        statusMessage = resp.pretty().take(200)
        val o = resp.jsonObject
        val jid =
          o["jobId"]?.jsonPrimitive?.contentOrNull
            ?: o["job_id"]?.jsonPrimitive?.contentOrNull
            ?: o["id"]?.jsonPrimitive?.contentOrNull
        if (jid != null) {
          repeat(40) {
            val job = withContext(Dispatchers.IO) { c.pollJob(jid) }
            val st =
              job.jsonObject["status"]?.jsonPrimitive?.contentOrNull
                ?: job.jsonObject["phase"]?.jsonPrimitive?.contentOrNull
                ?: ""
            statusMessage = "score $jid: $st"
            job.jsonObject["key"]?.jsonPrimitive?.contentOrNull?.let { audioKey = it }
            job.jsonObject["audio_key"]?.jsonPrimitive?.contentOrNull?.let { audioKey = it }
            if (st.lowercase() in listOf("done", "completed", "failed", "error")) return@repeat
            delay(3_000)
          }
        }
        o["key"]?.jsonPrimitive?.contentOrNull?.let { audioKey = it }
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun suggestScorePrompt() {
    val c = client ?: return
    val sb = storyboard ?: return
    viewModelScope.launch {
      busy = true
      try {
        if (planModel.isNotBlank()) {
          val r =
            withContext(Dispatchers.IO) {
              c.chat(
                planModel,
                "Write ONE concise INSTRUMENTAL music prompt (2-4 sentences) for a short film score. " +
                  "Storyboard: ${sb.pretty().take(1500)}. Brief: $brief",
              )
            }
          val out = r.output?.trim().orEmpty()
          if (out.isNotEmpty()) {
            scorePrompt = out
            statusMessage = "Score prompt suggested"
            return@launch
          }
        }
        scorePrompt = RenderConfigSchema.scorePromptScaffold(sb, brief)
        statusMessage = "Score prompt scaffold (local)"
      } catch (e: Exception) {
        scorePrompt = RenderConfigSchema.scorePromptScaffold(sb, brief)
        statusMessage = "Score prompt scaffold (local)"
      } finally {
        busy = false
      }
    }
  }

  fun snapBpm() {
    val sb = storyboard ?: return
    applyStoryboard(StoryboardHelpers.snapToBeats(sb, bpm))
    statusMessage = "Snapped to $bpm BPM"
  }

  fun analyzeAudio() {
    val c = client ?: return
    val key = audioKey ?: run {
      lastError = "No audio key"
      return
    }
    viewModelScope.launch {
      busy = true
      try {
        val r = withContext(Dispatchers.IO) { c.analyzeAudio(key) }
        r.jsonObject["bpm"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.let {
          bpm = it
          statusMessage = "Analyzed BPM $it"
        } ?: run { statusMessage = r.pretty().take(160) }
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun uploadAudio(bytes: ByteArray, mime: String) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val up = withContext(Dispatchers.IO) { c.uploadAudio(bytes, mime) }
        audioKey = up.key
        statusMessage = "Audio staged: ${up.key}"
        persistSession()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun deleteRender(id: Int) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.deleteRender(id) }
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun patchRenderLabel(id: Int, label: String) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.patchRender(id, label = label) }
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun patchRenderTags(id: Int, tagsCsv: String) {
    val c = client ?: return
    val tags =
      tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.patchRender(id, tags = tags) }
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun toggleLockedShot(renderId: Int, shotId: String, currently: List<String>) {
    val c = client ?: return
    val next = currently.toMutableSet()
    if (!next.add(shotId)) next.remove(shotId)
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.patchRender(renderId, lockedShots = next.sorted()) }
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun regenShot(renderId: Int, shotId: String) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val job = withContext(Dispatchers.IO) { c.regenShot(renderId, shotId) }
        val jid = job.resolvedJobId
        if (jid != null) {
          renderJobId = jid
          renderStatus = job.status ?: "submitted"
          persistSession()
          startPoll(c, jid)
          statusMessage = "Regen $shotId job $jid"
        }
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun addAudioToHistory(id: Int) {
    val c = client ?: return
    val key = audioKey ?: run {
      lastError = "Stage an audio bed first"
      return
    }
    viewModelScope.launch {
      busy = true
      try {
        withContext(Dispatchers.IO) { c.addAudioToRender(id, key) }
        statusMessage = "Audio muxed onto #$id"
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun addNarrationToHistory(id: Int, text: String) {
    val c = client ?: return
    val t = text.trim()
    if (t.isEmpty()) {
      lastError = "Narration text required"
      return
    }
    viewModelScope.launch {
      busy = true
      try {
        withContext(Dispatchers.IO) { c.addNarrationToRender(id, t) }
        statusMessage = "Narration added to #$id"
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun finalizeHistory(id: Int) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        withContext(Dispatchers.IO) {
          c.finalizeRender(id, audioKey, castBindings.ifEmpty { null })
        }
        statusMessage = "Finalize submitted for #$id"
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun animateCloudHistory(id: Int) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val map = cloudPerShot.filter { it.value.isNotBlank() }
        withContext(Dispatchers.IO) {
          c.animateCloud(
            id,
            cloudAnimateModel.ifBlank { null },
            map.ifEmpty { null },
          )
        }
        statusMessage = "Cloud animate #$id"
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun animateHybridHistory(id: Int) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val backends =
          if (hybridPerShot.isEmpty()) null
          else
            buildJsonObject {
              hybridPerShot.forEach { (shot, backend) ->
                put(
                  shot,
                  buildJsonObject {
                    put("backend", backend)
                    if (backend == "cloud" && cloudAnimateModel.isNotBlank()) {
                      put("model", cloudAnimateModel)
                    }
                  },
                )
              }
            }
        withContext(Dispatchers.IO) {
          c.animateHybrid(
            id,
            backends,
            "gpu",
            cloudAnimateModel.ifBlank { null },
          )
        }
        statusMessage = "Hybrid animate #$id"
        refreshHistory()
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun seedPerShotMaps(row: RenderRow) {
    row.keyframeShotIds.forEach { shot ->
      if (!cloudPerShot.containsKey(shot)) cloudPerShot[shot] = ""
      if (!hybridPerShot.containsKey(shot)) hybridPerShot[shot] = "gpu"
    }
  }

  fun loadRender(row: RenderRow) {
    row.bundleKey?.let { bundleKey = it }
    row.qualityTier?.let { qualityTier = it }
    row.projectId?.let { selectedProjectId = it }
    row.storyboard?.let { applyStoryboard(it) }
    plannerStep = PlannerStep.Render
    statusMessage = "Loaded bundle ${row.bundleKey}"
    persistSession()
  }

  fun openArtifact(key: String, onUrl: (String) -> Unit) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        val r = withContext(Dispatchers.IO) { c.artifactUrl(key) }
        r.resolvedUrl?.let(onUrl)
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun installModule(scriptName: String) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        withContext(Dispatchers.IO) { c.installModule(scriptName) }
        installedModules = withContext(Dispatchers.IO) { c.listInstalledModules() }
        bootstrap()
        statusMessage = "Installed $scriptName"
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun uninstallModule(name: String) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.uninstallModule(name) }
        installedModules = withContext(Dispatchers.IO) { c.listInstalledModules() }
        bootstrap()
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun setModuleEnabled(name: String, enabled: Boolean) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.setModuleEnabled(name, enabled) }
        installedModules = withContext(Dispatchers.IO) { c.listInstalledModules() }
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun loadModuleConfig(name: String) {
    val c = client ?: return
    moduleConfigName = name
    viewModelScope.launch {
      try {
        val r = withContext(Dispatchers.IO) { c.getModuleConfig(name) }
        moduleConfigJson = r.config?.pretty() ?: "{}"
      } catch (e: Exception) {
        moduleConfigJson = "{}"
        lastError = e.message
      }
    }
  }

  fun saveModuleConfig() {
    val c = client ?: return
    val name = moduleConfigName
    if (name.isBlank()) return
    viewModelScope.launch {
      busy = true
      try {
        val el = org.skyphusion.vivijure.kit.studioJson.parseToJsonElement(moduleConfigJson)
        withContext(Dispatchers.IO) { c.patchModuleConfig(name, el) }
        statusMessage = "Saved config for $name"
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun savePrefs() {
    val c = client ?: return
    viewModelScope.launch {
      try {
        val el = org.skyphusion.vivijure.kit.studioJson.parseToJsonElement(prefsJson)
        val next = withContext(Dispatchers.IO) { c.patchPrefs(el) }
        prefsJson = next.pretty()
        statusMessage = "Prefs saved"
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun refreshStorage() {
    val c = client ?: return
    viewModelScope.launch {
      try {
        val u = withContext(Dispatchers.IO) { c.storageUsage() }
        storageSummary =
          "used=${u.usedBytes ?: 0} objects=${u.objects ?: 0} quota=${u.quotaBytes} over=${u.over}"
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  fun reconcileStorage() {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        withContext(Dispatchers.IO) { c.storageReconcile() }
        refreshStorage()
        statusMessage = "Storage reconcile submitted"
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun runDemoRender(scene: String) {
    val c = client ?: return
    viewModelScope.launch {
      busy = true
      try {
        val r = withContext(Dispatchers.IO) { c.demoRender(scene) }
        val jid = r.resolvedJobId ?: run {
          lastError = r.error ?: "No demo job"
          return@launch
        }
        demoStatus = r.status ?: "submitted"
        repeat(60) {
          val poll = withContext(Dispatchers.IO) { c.pollDemoRender(jid) }
          demoStatus = poll.jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: demoStatus
          if (demoStatus.lowercase() in listOf("completed", "failed", "done", "error")) return@repeat
          delay(3_000)
        }
        statusMessage = "Demo $demoStatus"
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
      }
    }
  }

  fun runDemoChat(message: String, onReply: (String) -> Unit) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        val r = withContext(Dispatchers.IO) { c.demoChat(message) }
        onReply(r.reply ?: r.output ?: "")
      } catch (e: Exception) {
        lastError = e.message
      }
    }
  }

  val cloudMotionModels: List<String>
    get() = modules?.motionBackends().orEmpty().filter { !it.contains("own-gpu") }

  fun storyboardPretty(): String = storyboard?.pretty() ?: ""
}
