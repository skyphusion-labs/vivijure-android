package org.skyphusion.vivijure

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.skyphusion.vivijure.kit.RenderConfigSchema
import org.skyphusion.vivijure.kit.StoryboardHelpers
import org.skyphusion.vivijure.kit.pretty

class MainActivity : ComponentActivity() {
  private val vm: AppViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        if (!vm.isConfigured) {
          OnboardingScreen(vm)
        } else {
          LaunchedEffect(Unit) { vm.bootstrap() }
          MainShell(vm)
        }
      }
    }
  }
}

@Composable
fun OnboardingScreen(vm: AppViewModel) {
  var url by remember { mutableStateOf(vm.studioUrl.ifBlank { "https://" }) }
  var token by remember { mutableStateOf("") }
  Column(
    Modifier
      .fillMaxSize()
      .padding(24.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Vivijure for Android", style = MaterialTheme.typography.headlineSmall)
    Text(
      "Paste your studio base URL and Bearer token (same as the web planner).",
      style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
      value = url,
      onValueChange = { url = it },
      label = { Text("Studio URL") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )
    OutlinedTextField(
      value = token,
      onValueChange = { token = it },
      label = { Text("Bearer token") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )
    Button(
      onClick = { vm.saveCredentials(url, token) },
      enabled = url.isNotBlank() && token.isNotBlank(),
    ) {
      Text("Connect")
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(vm: AppViewModel) {
  var tab by remember { mutableIntStateOf(0) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Vivijure") },
        actions = {
          if (vm.busy) {
            CircularProgressIndicator(Modifier.padding(16.dp), strokeWidth = 2.dp)
          }
        },
      )
    },
    bottomBar = {
      NavigationBar {
        NavigationBarItem(
          selected = tab == 0,
          onClick = { tab = 0 },
          icon = { Icon(Icons.Default.Movie, null) },
          label = { Text("Planner") },
        )
        NavigationBarItem(
          selected = tab == 1,
          onClick = { tab = 1 },
          icon = { Icon(Icons.Default.Groups, null) },
          label = { Text("Cast") },
        )
        NavigationBarItem(
          selected = tab == 2,
          onClick = { tab = 2 },
          icon = { Icon(Icons.Default.Widgets, null) },
          label = { Text("Modules") },
        )
        NavigationBarItem(
          selected = tab == 3,
          onClick = { tab = 3 },
          icon = { Icon(Icons.Default.Settings, null) },
          label = { Text("Settings") },
        )
      }
    },
  ) { pad ->
    Column(Modifier.padding(pad).fillMaxSize()) {
      StatusBanner(vm)
      when (tab) {
        0 -> PlannerScreen(vm)
        1 -> CastScreen(vm)
        2 -> ModulesScreen(vm)
        else -> SettingsScreen(vm)
      }
    }
  }
}

@Composable
fun StatusBanner(vm: AppViewModel) {
  Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
    vm.lastError?.let {
      Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (vm.statusMessage.isNotBlank()) {
      Text(vm.statusMessage, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
fun PlannerScreen(vm: AppViewModel) {
  Column(Modifier.fillMaxSize()) {
    Row(
      Modifier
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      PlannerStep.entries.forEach { step ->
        FilterChip(
          selected = vm.plannerStep == step,
          onClick = {
            vm.plannerStep = step
            if (step == PlannerStep.History) vm.refreshHistory()
          },
          label = { Text(step.label) },
        )
      }
    }
    HorizontalDivider()
    when (vm.plannerStep) {
      PlannerStep.Plan -> PlanStep(vm)
      PlannerStep.CastBundle -> CastBundleStep(vm)
      PlannerStep.Audio -> AudioStep(vm)
      PlannerStep.Render -> RenderStep(vm)
      PlannerStep.History -> HistoryStep(vm)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanStep(vm: AppViewModel) {
  var newProject by remember { mutableStateOf("") }
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Project", style = MaterialTheme.typography.titleMedium)
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
      OutlinedTextField(
        value = vm.projects.find { it.id == vm.selectedProjectId }?.name ?: "(transient)",
        onValueChange = {},
        readOnly = true,
        label = { Text("Active project") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        modifier = Modifier.menuAnchor().fillMaxWidth(),
      )
      ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
          text = { Text("(transient)") },
          onClick = {
            vm.selectProject(null)
            expanded = false
          },
        )
        vm.projects.forEach { p ->
          DropdownMenuItem(
            text = { Text(p.name) },
            onClick = {
              vm.selectProject(p.id)
              expanded = false
            },
          )
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = newProject,
        onValueChange = { newProject = it },
        label = { Text("New project") },
        modifier = Modifier.weight(1f),
      )
      Button(
        onClick = {
          val n = newProject.trim()
          if (n.isNotEmpty()) {
            vm.createProject(n)
            newProject = ""
          }
        },
      ) { Text("Create") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (vm.selectedProjectId != null) {
        TextButton(onClick = { vm.deleteSelectedProject() }) { Text("Delete project") }
        TextButton(onClick = { vm.saveStoryboardToProject() }) { Text("Save board") }
      }
    }

    Text("Cast slots A–D", style = MaterialTheme.typography.titleMedium)
    vm.castSlots.forEachIndexed { idx, slot ->
      Column {
        Row {
          Text("Slot ${slot.letter}", modifier = Modifier.weight(1f))
          Switch(
            checked = slot.included,
            onCheckedChange = { on ->
              vm.castSlots[idx].included = on
            },
          )
        }
        if (slot.included) {
          OutlinedTextField(
            value = slot.boundCastId.ifBlank { "inline" },
            onValueChange = {},
            label = { Text("Bound cast id (or leave inline)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
          )
          // Simple picker via chips
          Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
              selected = slot.boundCastId.isBlank(),
              onClick = { vm.castSlots[idx].boundCastId = "" },
              label = { Text("inline") },
            )
            vm.cast.forEach { m ->
              FilterChip(
                selected = slot.boundCastId == m.id,
                onClick = {
                  vm.castSlots[idx].boundCastId = m.id
                  vm.castSlots[idx].included = true
                },
                label = { Text(m.name) },
              )
            }
          }
          if (slot.boundCastId.isBlank()) {
            OutlinedTextField(
              value = slot.inlineName,
              onValueChange = { vm.castSlots[idx].inlineName = it },
              label = { Text("Name") },
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = slot.inlineBible,
              onValueChange = { vm.castSlots[idx].inlineBible = it },
              label = { Text("Bible") },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }

    Text("Plan", style = MaterialTheme.typography.titleMedium)
    if (vm.availableModels.isNotEmpty()) {
      Row(Modifier.horizontalScroll(rememberScrollState())) {
        vm.availableModels.forEach { m ->
          FilterChip(
            selected = vm.planModel == m,
            onClick = { vm.planModel = m },
            label = { Text(m) },
          )
        }
      }
    } else {
      OutlinedTextField(
        value = vm.planModel,
        onValueChange = { vm.planModel = it },
        label = { Text("Model id") },
        modifier = Modifier.fillMaxWidth(),
      )
    }
    OutlinedTextField(
      value = vm.brief,
      onValueChange = { vm.brief = it },
      label = { Text("Brief") },
      modifier = Modifier.fillMaxWidth().height(140.dp),
    )
    Button(onClick = { vm.runPlan() }, enabled = vm.brief.isNotBlank() && !vm.busy) {
      Text("Plan storyboard")
    }

    if (vm.storyboard != null) {
      OutlinedTextField(
        value = vm.refineInstruction,
        onValueChange = { vm.refineInstruction = it },
        label = { Text("Refine instruction") },
        modifier = Modifier.fillMaxWidth(),
      )
      Button(onClick = { vm.runRefine() }, enabled = !vm.busy) { Text("Refine") }

      Text("Scenes (${vm.sceneEdits.size})", style = MaterialTheme.typography.titleMedium)
      vm.sceneEdits.forEachIndexed { i, scene ->
        OutlinedTextField(
          value = scene.prompt,
          onValueChange = {
            vm.sceneEdits[i].prompt = it
          },
          label = { Text(scene.id) },
          modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { vm.deleteScene(i) }) { Text("Delete ${scene.id}") }
      }
      Button(onClick = { vm.commitSceneEdits() }) { Text("Apply scene edits") }
      if (vm.yamlPreview.isNotBlank()) {
        Text("YAML", style = MaterialTheme.typography.titleSmall)
        Text(vm.yamlPreview, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
      }
      Text("JSON", style = MaterialTheme.typography.titleSmall)
      Text(vm.storyboardPretty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = { vm.clearPlanner() }) { Text("New session") }
  }
}

@Composable
fun CastBundleStep(vm: AppViewModel) {
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (vm.storyboard == null) {
      Text("Plan a storyboard first.")
      return
    }
    Text("Bindings", style = MaterialTheme.typography.titleMedium)
    val use = StoryboardHelpers.useCharacters(vm.storyboard!!)
    if (use.isEmpty()) {
      Text("No use_characters; bundle will ship storyboard only.")
    } else {
      use.forEach { letter ->
        val idx = vm.castSlots.indexOfFirst { it.letter == letter }
        if (idx >= 0) {
          Text("Slot $letter")
          Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
              selected = vm.castSlots[idx].boundCastId.isBlank(),
              onClick = {
                vm.castSlots[idx].boundCastId = ""
                vm.castSlots[idx].included = true
              },
              label = { Text("none") },
            )
            vm.cast.forEach { m ->
              FilterChip(
                selected = vm.castSlots[idx].boundCastId == m.id,
                onClick = {
                  vm.castSlots[idx].boundCastId = m.id
                  vm.castSlots[idx].included = true
                },
                label = {
                  Text("${m.name} (${m.refKeyList.size} refs, LoRA ${m.loraStatus ?: "?"})")
                },
              )
            }
          }
        }
      }
    }
    Text("Scene start keyframes (optional)", style = MaterialTheme.typography.titleMedium)
    val ctx = LocalContext.current
    var pendingSceneId by remember { mutableStateOf<String?>(null) }
    val sceneStartPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val sid = pendingSceneId ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes =
          ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@rememberLauncherForActivityResult
        val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
        vm.stageSceneStart(sid, bytes, mime)
        pendingSceneId = null
      }
    val scenes = vm.storyboard?.let { StoryboardHelpers.sceneIds(it) }.orEmpty()
    scenes.forEach { sid ->
      Row {
        Column(Modifier.weight(1f)) {
          Text(sid)
          Text(
            vm.sceneStartImages[sid] ?: "no start image",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (vm.sceneStartImages[sid] != null) {
          TextButton(onClick = { vm.clearSceneStart(sid) }) { Text("Clear") }
        }
        TextButton(
          onClick = {
            pendingSceneId = sid
            sceneStartPicker.launch("image/*")
          },
        ) { Text("Pick") }
      }
    }
    Button(onClick = { vm.runPreflight() }, enabled = !vm.busy) { Text("Run preflight") }
    vm.preflight?.let {
      Text(if (it.ok) "Preflight OK" else "Issues present")
    }
    Button(onClick = { vm.runBundle() }, enabled = !vm.busy) { Text("Assemble bundle") }
    vm.bundleKey?.let { Text("bundleKey: $it", fontFamily = FontFamily.Monospace) }
  }
}

@Composable
fun AudioStep(vm: AppViewModel) {
  val ctx = LocalContext.current
  val audioPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
      val mime = ctx.contentResolver.getType(uri) ?: "audio/mpeg"
      vm.uploadAudio(bytes, mime)
    }
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedTextField(
      value = vm.scorePrompt,
      onValueChange = { vm.scorePrompt = it },
      label = { Text("Music prompt") },
      modifier = Modifier.fillMaxWidth().height(100.dp),
    )
    Button(onClick = { vm.suggestScorePrompt() }, enabled = vm.storyboard != null && !vm.busy) {
      Text("Suggest from storyboard")
    }
    Button(onClick = { vm.scoreBed() }, enabled = vm.storyboard != null && !vm.busy) {
      Text("Generate score-bed")
    }
    Button(onClick = { audioPicker.launch("audio/*") }, enabled = !vm.busy) {
      Text("Import audio file")
    }
    vm.audioKey?.let {
      Text("audioKey: $it", fontFamily = FontFamily.Monospace)
      TextButton(onClick = { vm.audioKey = null; vm.persistSession() }) { Text("Clear bed") }
    }
    OutlinedTextField(
      value = vm.bpm.toString(),
      onValueChange = { it.toDoubleOrNull()?.let { d -> vm.bpm = d } },
      label = { Text("BPM") },
      modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { vm.analyzeAudio() }, enabled = vm.audioKey != null && !vm.busy) {
      Text("Analyze bed BPM")
    }
    Button(onClick = { vm.snapBpm() }, enabled = vm.storyboard != null) {
      Text("Snap scene durations")
    }
  }
}

@Composable
fun RenderStep(vm: AppViewModel) {
  val tiers = vm.modules?.qualityTiers ?: listOf("draft", "standard", "final")
  val backends = vm.modules?.motionBackends().orEmpty()
  val configMods = remember(vm.modules) { RenderConfigSchema.modules(vm.modules) }
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Quality")
    Row(Modifier.horizontalScroll(rememberScrollState())) {
      tiers.forEach { t ->
        FilterChip(
          selected = vm.qualityTier == t,
          onClick = { vm.qualityTier = t },
          label = { Text(t) },
        )
      }
    }
    if (backends.isNotEmpty()) {
      Text("Motion backend")
      Row(Modifier.horizontalScroll(rememberScrollState())) {
        if (backends.size > 1) {
          FilterChip(
            selected = vm.motionBackend.isBlank(),
            onClick = { vm.motionBackend = "" },
            label = { Text("(pick)") },
          )
        }
        backends.forEach { b ->
          FilterChip(
            selected = vm.motionBackend == b,
            onClick = { vm.motionBackend = b },
            label = { Text(b) },
          )
        }
      }
    }
    Row {
      Text("Keyframes only", modifier = Modifier.weight(1f))
      Switch(checked = vm.keyframesOnly, onCheckedChange = { vm.keyframesOnly = it })
    }
    Row {
      Text("Scatter", modifier = Modifier.weight(1f))
      Switch(checked = vm.useScatter, onCheckedChange = { vm.useScatter = it })
    }
    if (vm.useScatter) {
      OutlinedTextField(
        value = vm.scatterShards.toString(),
        onValueChange = { it.toIntOrNull()?.let { n -> vm.scatterShards = n } },
        label = { Text("Shards") },
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (configMods.isNotEmpty()) {
      Text("Module render config", style = MaterialTheme.typography.titleMedium)
      configMods.forEach { mod ->
        Text(mod.label, style = MaterialTheme.typography.titleSmall)
        mod.fields.forEach { field ->
          when (field.type) {
            "bool" -> {
              val cur =
                (vm.renderFieldValues[field.id] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                  ?: (field.defaultValue as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                  ?: false
              Row {
                Text(field.label, modifier = Modifier.weight(1f))
                Switch(
                  checked = cur,
                  onCheckedChange = {
                    vm.renderFieldValues[field.id] = JsonPrimitive(it)
                  },
                )
              }
            }
            else -> {
              val cur =
                (vm.renderFieldValues[field.id] as? JsonPrimitive)?.content
                  ?: (field.defaultValue as? JsonPrimitive)?.content
                  ?: ""
              OutlinedTextField(
                value = cur,
                onValueChange = { vm.renderFieldValues[field.id] = JsonPrimitive(it) },
                label = { Text("${field.label} (${field.type})") },
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }
    }
    OutlinedTextField(
      value = vm.expertJson,
      onValueChange = { vm.expertJson = it },
      label = { Text("Expert JSON overrides") },
      modifier = Modifier.fillMaxWidth().height(100.dp),
    )
    vm.bundleKey?.let { Text("Bundle: $it", fontFamily = FontFamily.Monospace) }
    Button(onClick = { vm.runRender() }, enabled = vm.storyboard != null && !vm.busy) {
      Text(if (vm.useScatter) "Submit scatter" else "Submit render")
    }
    vm.renderJobId?.let {
      Text("Job: $it")
      Text("Status: ${vm.renderStatus}")
    }
  }
}

@Composable
fun HistoryStep(vm: AppViewModel) {
  val ctx = LocalContext.current
  LaunchedEffect(Unit) { vm.refreshHistory() }
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Button(onClick = { vm.refreshHistory() }) { Text("Refresh") }
    if (vm.renderTags.isNotEmpty()) {
      Text("Tags: ${vm.renderTags.joinToString()}", style = MaterialTheme.typography.bodySmall)
    }
    if (vm.renders.isEmpty()) Text("No renders loaded.")
    if (vm.cloudMotionModels.isNotEmpty()) {
      Text("Default cloud model")
      Row(Modifier.horizontalScroll(rememberScrollState())) {
        vm.cloudMotionModels.forEach { m ->
          FilterChip(
            selected = vm.cloudAnimateModel == m,
            onClick = { vm.cloudAnimateModel = m },
            label = { Text(m) },
          )
        }
      }
    }
    vm.renders.forEach { r ->
      var label by remember(r.id) { mutableStateOf(r.label.orEmpty()) }
      var tags by remember(r.id) { mutableStateOf(r.tags?.joinToString(", ").orEmpty()) }
      var narration by remember(r.id) { mutableStateOf("") }
      Column(Modifier.padding(vertical = 6.dp)) {
        Text(r.label ?: r.jobId ?: "#${r.id}", style = MaterialTheme.typography.titleSmall)
        Text("${r.status ?: "?"} · ${r.qualityTier ?: ""} · ${r.mode ?: ""}")
        if (r.isScatterParent) Text("scatter", color = MaterialTheme.colorScheme.tertiary)
        r.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        r.outputKey?.let { key ->
          Text(key, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
          TextButton(
            onClick = {
              vm.openArtifact(key) { url ->
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
              }
            },
          ) { Text("Open artifact") }
        }
        if (r.bundleKey != null) {
          TextButton(onClick = { vm.loadRender(r) }) { Text("Load into planner") }
        }
        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("Label") },
          modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { vm.patchRenderLabel(r.id, label) }) { Text("Save label") }
        OutlinedTextField(
          value = tags,
          onValueChange = { tags = it },
          label = { Text("Tags (comma)") },
          modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { vm.patchRenderTags(r.id, tags) }) { Text("Save tags") }
        TextButton(onClick = { vm.addAudioToHistory(r.id) }, enabled = vm.audioKey != null) {
          Text("Add audio bed")
        }
        OutlinedTextField(
          value = narration,
          onValueChange = { narration = it },
          label = { Text("Narration") },
          modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { vm.addNarrationToHistory(r.id, narration) }) { Text("Add narration") }
        TextButton(onClick = { vm.finalizeHistory(r.id) }) { Text("Finalize (GPU)") }
        TextButton(onClick = { vm.animateCloudHistory(r.id) }) { Text("Animate cloud") }
        TextButton(onClick = { vm.animateHybridHistory(r.id) }) { Text("Animate hybrid") }
        val shots = r.keyframeShotIds
        if (shots.isNotEmpty()) {
          LaunchedEffect(r.id) { vm.seedPerShotMaps(r) }
          Text("Keyframes", style = MaterialTheme.typography.titleSmall)
          shots.forEach { shot ->
            Row {
              Text(shot, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
              val locked = r.resolvedLocked.contains(shot)
              TextButton(onClick = { vm.toggleLockedShot(r.id, shot, r.resolvedLocked) }) {
                Text(if (locked) "Unlock" else "Lock")
              }
              TextButton(onClick = { vm.regenShot(r.id, shot) }) { Text("Regen") }
            }
            if (vm.cloudMotionModels.isNotEmpty()) {
              Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                  selected = (vm.cloudPerShot[shot] ?: "").isEmpty(),
                  onClick = { vm.cloudPerShot[shot] = "" },
                  label = { Text("cloud default") },
                )
                vm.cloudMotionModels.forEach { m ->
                  FilterChip(
                    selected = vm.cloudPerShot[shot] == m,
                    onClick = { vm.cloudPerShot[shot] = m },
                    label = { Text(m) },
                  )
                }
              }
            }
            Row {
              FilterChip(
                selected = (vm.hybridPerShot[shot] ?: "gpu") == "gpu",
                onClick = { vm.hybridPerShot[shot] = "gpu" },
                label = { Text("GPU") },
              )
              FilterChip(
                selected = vm.hybridPerShot[shot] == "cloud",
                onClick = { vm.hybridPerShot[shot] = "cloud" },
                label = { Text("Cloud") },
              )
            }
          }
        }
        TextButton(onClick = { vm.deleteRender(r.id) }) { Text("Delete") }
        HorizontalDivider()
      }
    }
  }
}

@Composable
fun CastScreen(vm: AppViewModel) {
  var name by remember { mutableStateOf("") }
  var bible by remember { mutableStateOf("") }
  val ctx = LocalContext.current
  var uploadKind by remember { mutableStateOf("portrait") }
  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      val id = vm.selectedCastId ?: return@rememberLauncherForActivityResult
      if (uri == null) return@rememberLauncherForActivityResult
      val bytes =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: return@rememberLauncherForActivityResult
      val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
      vm.uploadCastImage(id, uploadKind, bytes, mime)
    }
  val importPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      val bytes =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: return@rememberLauncherForActivityResult
      vm.importCast(bytes)
    }
  LaunchedEffect(Unit) { vm.refreshCast() }
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("New member", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = bible, onValueChange = { bible = it }, label = { Text("Bible") }, modifier = Modifier.fillMaxWidth())
    Button(
      onClick = {
        val n = name.trim()
        if (n.isNotEmpty()) {
          vm.createCast(n, bible.trim().ifEmpty { null })
          name = ""
          bible = ""
        }
      },
    ) { Text("Create") }
    Button(onClick = { importPicker.launch("*/*") }) { Text("Import .vvcast") }
    Text("Cast", style = MaterialTheme.typography.titleMedium)
    vm.cast.forEach { m ->
      val selected = vm.selectedCastId == m.id
      Column(Modifier.padding(vertical = 4.dp)) {
        TextButton(onClick = { vm.selectedCastId = if (selected) null else m.id }) {
          Text(if (selected) "▼ ${m.name}" else "▶ ${m.name}")
        }
        Text(m.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        m.bible?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(
          "portrait=${m.portraitKey != null} refs=${m.refKeyList.size} LoRA=${m.loraStatus ?: "?"}",
          style = MaterialTheme.typography.bodySmall,
        )
        if (selected) {
          var editName by remember(m.id) { mutableStateOf(m.name) }
          var editBible by remember(m.id) { mutableStateOf(m.bible.orEmpty()) }
          OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = editBible,
            onValueChange = { editBible = it },
            label = { Text("Bible") },
            modifier = Modifier.fillMaxWidth(),
          )
          TextButton(onClick = { vm.patchCast(m.id, editName, editBible) }) { Text("Save") }
          Text("Upload as")
          Row {
            listOf("portrait", "ref", "source").forEach { k ->
              FilterChip(
                selected = uploadKind == k,
                onClick = { uploadKind = k },
                label = { Text(k) },
              )
            }
          }
          TextButton(onClick = { imagePicker.launch("image/*") }) { Text("Pick photo") }
          TextButton(onClick = { vm.generateCastRefs(m.id) }) { Text("Generate refs") }
          TextButton(onClick = { vm.trainCast(m.id, wan = false) }) { Text("Train SDXL LoRA") }
          TextButton(onClick = { vm.trainCast(m.id, wan = true) }) { Text("Train Wan LoRA") }
          TextButton(
            onClick = {
              vm.exportCast(m.id) { bytes, filename ->
                val file = File(ctx.cacheDir, filename)
                file.writeBytes(bytes)
                val uri =
                  FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val share =
                  Intent(Intent.ACTION_SEND).apply {
                    type = "application/x-tar"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                ctx.startActivity(Intent.createChooser(share, "Export .vvcast"))
              }
            },
          ) { Text("Export .vvcast") }
          TextButton(onClick = { vm.deleteCast(m.id) }) { Text("Delete") }
        }
      }
    }
  }
}

@Composable
fun ModulesScreen(vm: AppViewModel) {
  var scriptName by remember { mutableStateOf("") }
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val mods = vm.modules
    if (mods == null) {
      Text("Connect and bootstrap to load modules.")
      return
    }
    Text("Quality tiers: ${mods.qualityTiers.joinToString()}")
    Text("Motion backends: ${mods.motionBackends().joinToString()}")
    Text("Install (MODULE_DISPATCH hosts)", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
      value = scriptName,
      onValueChange = { scriptName = it },
      label = { Text("script_name") },
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      onClick = {
        val n = scriptName.trim()
        if (n.isNotEmpty()) vm.installModule(n)
      },
    ) { Text("Install") }
    vm.installedModules.forEach { el ->
      val name =
        el.jsonObject["name"]?.jsonPrimitive?.contentOrNull
          ?: el.jsonObject["module"]?.jsonPrimitive?.contentOrNull
          ?: el.jsonPrimitive.contentOrNull
          ?: "?"
      Row {
        Text(name, modifier = Modifier.weight(1f))
        TextButton(onClick = { vm.setModuleEnabled(name, true) }) { Text("On") }
        TextButton(onClick = { vm.setModuleEnabled(name, false) }) { Text("Off") }
        TextButton(onClick = { vm.uninstallModule(name) }) { Text("Uninstall") }
        TextButton(onClick = { vm.loadModuleConfig(name) }) { Text("Config") }
      }
    }
    if (vm.moduleConfigName.isNotBlank()) {
      Text("Config: ${vm.moduleConfigName}")
      OutlinedTextField(
        value = vm.moduleConfigJson,
        onValueChange = { vm.moduleConfigJson = it },
        modifier = Modifier.fillMaxWidth().height(140.dp),
      )
      Button(onClick = { vm.saveModuleConfig() }) { Text("Save config") }
    }
    Text("Projection (raw)", style = MaterialTheme.typography.titleMedium)
    val raw =
      buildJsonObject {
        mods.modules?.let { put("modules", buildJsonArray { it.forEach { m -> add(m) } }) }
        mods.hooks?.let { put("hooks", it) }
        mods.render?.let { put("render", it) }
      }
    Text(raw.pretty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    Button(onClick = { vm.bootstrap() }) { Text("Refresh") }
  }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
  var demoMsg by remember { mutableStateOf("") }
  var demoReply by remember { mutableStateOf("") }
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Connection", style = MaterialTheme.typography.titleMedium)
    Text("URL: ${vm.studioUrl}")
    Text("User: ${vm.whoami?.user ?: vm.whoami?.email ?: "—"}")
    Button(onClick = { vm.bootstrap() }) { Text("Reconnect") }
    Button(onClick = { vm.signOut() }) { Text("Sign out") }
    Row {
      Text("Notify on render done", modifier = Modifier.weight(1f))
      Switch(checked = vm.notifyOnRender, onCheckedChange = { vm.enableRenderNotifications(it) })
    }
    if (vm.renderJobId != null) {
      Text("Poll: ${vm.renderJobId} · ${vm.renderStatus}", style = MaterialTheme.typography.bodySmall)
    }
    Text("Prefs", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
      value = vm.prefsJson,
      onValueChange = { vm.prefsJson = it },
      modifier = Modifier.fillMaxWidth().height(120.dp),
    )
    Button(onClick = { vm.savePrefs() }) { Text("Save prefs") }
    Text("Storage", style = MaterialTheme.typography.titleMedium)
    Button(onClick = { vm.refreshStorage() }) { Text("Refresh usage") }
    if (vm.storageSummary.isNotBlank()) Text(vm.storageSummary)
    Button(onClick = { vm.reconcileStorage() }) { Text("Reconcile") }
    if (vm.demoAvailable == true) {
      Text("Demo mode", style = MaterialTheme.typography.titleMedium)
      vm.demoScenes.forEach { scene ->
        val name =
          scene.jsonPrimitive.contentOrNull
            ?: scene.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            ?: scene.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            ?: scene.pretty().take(40)
        Button(onClick = { vm.runDemoRender(name) }) { Text("Demo render: $name") }
      }
      if (vm.demoStatus.isNotBlank()) Text("Demo: ${vm.demoStatus}")
      OutlinedTextField(
        value = demoMsg,
        onValueChange = { demoMsg = it },
        label = { Text("Demo chat") },
        modifier = Modifier.fillMaxWidth(),
      )
      Button(
        onClick = {
          vm.runDemoChat(demoMsg) { demoReply = it }
        },
      ) { Text("Send") }
      if (demoReply.isNotBlank()) Text(demoReply)
    } else if (vm.demoAvailable == false) {
      Text("Demo not available on this host.", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(12.dp))
    Text(
      "Vivijure for Android is a mobile frontend to the Storyboard Planner. " +
        "Same CONTRACT as the website and vivijure-ios (see docs/PARITY.md).",
      style = MaterialTheme.typography.bodySmall,
    )
    Text("AGPL-3.0-only", style = MaterialTheme.typography.labelSmall)
  }
}
