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

  private var client: VivijureClient? = null
  private var pollJob: Job? = null

  val castBindings: Map<String, String>
    get() =
      castSlots
        .filter { it.included && it.boundCastId.isNotBlank() }
        .associate { it.letter to it.boundCastId }

  init {
    if (isConfigured) {
      client = VivijureClient(store.studioUrl, store.token)
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
        }
        statusMessage = "Connected as ${whoami?.user ?: whoami?.email ?: "studio"}"
      } catch (e: Exception) {
        lastError = e.message
      } finally {
        busy = false
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
  }

  fun commitSceneEdits() {
    val sb = storyboard ?: return
    applyStoryboard(StoryboardHelpers.applyScenes(sceneEdits, sb))
    preflight = null
    bundleKey = null
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
            val done =
              listOf("COMPLETED", "FAILED", "CANCELLED", "done", "failed")
                .any { it.equals(renderStatus, ignoreCase = true) }
            if (done) {
              statusMessage = "Render $renderStatus"
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
        renders =
          withContext(Dispatchers.IO) { c.listRenders(selectedProjectId) }
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

  fun deleteCast(id: String) {
    val c = client ?: return
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { c.deleteCast(id) }
        refreshCast()
      } catch (e: Exception) {
        lastError = e.message
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

  fun loadRender(row: RenderRow) {
    row.bundleKey?.let { bundleKey = it }
    row.qualityTier?.let { qualityTier = it }
    row.projectId?.let { selectedProjectId = it }
    row.storyboard?.let { applyStoryboard(it) }
    plannerStep = PlannerStep.Render
    statusMessage = "Loaded bundle ${row.bundleKey}"
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

  fun storyboardPretty(): String = storyboard?.pretty() ?: ""
}
