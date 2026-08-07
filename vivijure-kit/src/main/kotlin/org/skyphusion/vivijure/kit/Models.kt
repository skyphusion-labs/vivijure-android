package org.skyphusion.vivijure.kit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class WhoamiResponse(
  val user: String? = null,
  val email: String? = null,
  val readonly: Boolean? = null,
)

@Serializable
data class ModulesResponse(
  val modules: List<JsonElement>? = null,
  val hooks: JsonElement? = null,
  val catalog: JsonElement? = null,
  val render: JsonElement? = null,
  val readonly: Boolean? = null,
  val api: JsonElement? = null,
) {
  val qualityTiers: List<String>
    get() {
      val arr =
        render?.jsonObject?.get("quality_tiers")?.jsonArray
          ?: return listOf("draft", "standard", "final")
      return arr.mapNotNull { el ->
        when {
          el is JsonPrimitive -> el.contentOrNull
          else -> el.jsonObject["value"]?.jsonPrimitive?.contentOrNull
            ?: el.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }
      }.ifEmpty { listOf("draft", "standard", "final") }
    }

  val defaultQualityTier: String
    get() =
      render?.jsonObject?.get("default_tier")?.jsonPrimitive?.contentOrNull
        ?: qualityTiers.lastOrNull()
        ?: "final"

  fun motionBackends(): List<String> {
    val arr = hooks?.jsonObject?.get("motion.backend")?.jsonArray ?: return emptyList()
    return arr.mapNotNull { it.jsonPrimitive.contentOrNull }
  }
}

@Serializable
data class StoryboardProject(
  val id: Int,
  val slug: String? = null,
  val name: String,
  val prefs: JsonElement? = null,
  @SerialName("last_storyboard") val lastStoryboard: JsonElement? = null,
  @SerialName("created_at") val createdAt: String? = null,
  @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable data class ProjectsListResponse(val projects: List<StoryboardProject>)

@Serializable data class ProjectItemResponse(val project: StoryboardProject)

@Serializable
data class CastImageKey(
  val key: String,
  val mime: String? = null,
)

@Serializable
data class CastMember(
  val id: String,
  val name: String,
  val bible: String? = null,
  @SerialName("voice_id") val voiceId: String? = null,
  @SerialName("portrait_key") val portraitKey: String? = null,
  @SerialName("portrait_mime") val portraitMime: String? = null,
  @SerialName("lora_status") val loraStatus: String? = null,
  @SerialName("ref_keys") val refKeys: List<CastImageKey>? = null,
  @SerialName("source_keys") val sourceKeys: List<CastImageKey>? = null,
) {
  val refKeyList: List<String> get() = refKeys?.map { it.key }.orEmpty()
  val sourceKeyList: List<String> get() = sourceKeys?.map { it.key }.orEmpty()
}

@Serializable data class CastListResponse(val cast: List<CastMember>)

@Serializable data class CastItemResponse(val cast: CastMember)

@Serializable
data class PlanResponse(
  val ok: Boolean? = null,
  val storyboard: JsonElement? = null,
  val error: String? = null,
  val model: String? = null,
)

@Serializable
data class PreflightResponse(
  val ok: Boolean,
  val counts: JsonElement? = null,
  val issues: List<JsonElement>? = null,
)

@Serializable
data class BundleResponse(
  val bundleKey: String? = null,
  @SerialName("bundle_key") val bundleKeySnake: String? = null,
  val ok: Boolean? = null,
  val error: String? = null,
) {
  val key: String? get() = bundleKey ?: bundleKeySnake
}

@Serializable
data class RenderJobResponse(
  val jobId: String? = null,
  @SerialName("job_id") val jobIdSnake: String? = null,
  val id: String? = null,
  val status: String? = null,
  val phase: String? = null,
  val error: String? = null,
  @SerialName("output_key") val outputKey: String? = null,
  @SerialName("download_url") val downloadUrl: String? = null,
  val ok: Boolean? = null,
) {
  val resolvedJobId: String? get() = jobId ?: jobIdSnake ?: id
}

@Serializable
data class RenderRow(
  val id: Int,
  @SerialName("job_id") val jobId: String? = null,
  val project: String? = null,
  @SerialName("bundle_key") val bundleKey: String? = null,
  @SerialName("quality_tier") val qualityTier: String? = null,
  val status: String? = null,
  @SerialName("output_key") val outputKey: String? = null,
  val error: String? = null,
  val label: String? = null,
  val mode: String? = null,
  val tags: List<String>? = null,
  @SerialName("project_id") val projectId: Int? = null,
  @SerialName("locked_shots") val lockedShotsSnake: List<String>? = null,
  val lockedShots: List<String>? = null,
  val keyframes: List<JsonElement>? = null,
  val storyboard: JsonElement? = null,
  @SerialName("render_overrides") val renderOverrides: JsonElement? = null,
) {
  val isScatterParent: Boolean get() = jobId?.startsWith("scatter-") == true
  val resolvedLocked: List<String> get() = lockedShots ?: lockedShotsSnake.orEmpty()
  val keyframeShotIds: List<String>
    get() =
      keyframes.orEmpty().mapNotNull {
        it.jsonObject["shot_id"]?.jsonPrimitive?.contentOrNull
          ?: it.jsonObject["id"]?.jsonPrimitive?.contentOrNull
      }
}

@Serializable data class RendersListResponse(val renders: List<RenderRow>)

@Serializable data class TagsListResponse(val tags: List<String>)

@Serializable
data class UploadResponse(
  val key: String,
  val mime: String? = null,
  val size: Int? = null,
)

@Serializable
data class ArtifactURLResponse(
  val url: String? = null,
  @SerialName("download_url") val downloadUrl: String? = null,
) {
  val resolvedUrl: String? get() = url ?: downloadUrl
}

@Serializable
data class StoryboardModelsResponse(
  val models: List<JsonElement>? = null,
  @SerialName("default_model") val defaultModel: String? = null,
)

@Serializable
data class YamlResponse(
  val ok: Boolean? = null,
  val yaml: String? = null,
  val error: String? = null,
)

@Serializable
data class PrefsResponse(
  val ok: Boolean? = null,
  val prefs: JsonElement? = null,
)

@Serializable
data class ModuleConfigResponse(
  val ok: Boolean? = null,
  val module: String? = null,
  val config: JsonElement? = null,
  val error: String? = null,
)

@Serializable
data class InstalledModulesResponse(
  val ok: Boolean? = null,
  val modules: List<JsonElement>? = null,
)

@Serializable
data class StorageUsageResponse(
  @SerialName("used_bytes") val usedBytes: Long? = null,
  val objects: Int? = null,
  @SerialName("quota_bytes") val quotaBytes: Long? = null,
  val over: Boolean? = null,
)

@Serializable
data class ChatResponse(
  val output: String? = null,
  val error: String? = null,
  val reply: String? = null,
)

@Serializable
data class DemoMenuResponse(
  val available: Boolean? = null,
  val scenes: List<JsonElement>? = null,
)

@Serializable
data class DemoRenderResponse(
  val jobId: String? = null,
  @SerialName("job_id") val jobIdSnake: String? = null,
  val status: String? = null,
  val error: String? = null,
) {
  val resolvedJobId: String? get() = jobId ?: jobIdSnake
}

/** Plan-stage cast slot A–D. */
class PlanCastSlot(
  val letter: String,
  var included: Boolean = false,
  var boundCastId: String = "",
  var inlineName: String = "",
  var inlineBible: String = "",
)

class SceneEdit(
  val index: Int,
  val id: String,
  var prompt: String,
  var targetSeconds: Double? = null,
  var act: String = "",
  var characterSlots: MutableList<String> = mutableListOf(),
  var dialogueSlot: String = "",
  var dialogueText: String = "",
)

data class RenderConfigField(
  val moduleName: String,
  val key: String,
  val type: String,
  val label: String,
  val defaultValue: JsonElement? = null,
  val min: Double? = null,
  val max: Double? = null,
  val enumValues: List<String> = emptyList(),
) {
  val id: String get() = "$moduleName.$key"
}

data class RenderConfigModule(
  val name: String,
  val label: String,
  val fields: List<RenderConfigField>,
)

fun jsonObj(vararg pairs: Pair<String, JsonElement?>): JsonObject =
  buildJsonObject {
    for ((k, v) in pairs) if (v != null) put(k, v)
  }

fun jsonStr(s: String): JsonPrimitive = JsonPrimitive(s)

fun jsonNum(n: Number): JsonPrimitive = JsonPrimitive(n)

fun jsonBool(b: Boolean): JsonPrimitive = JsonPrimitive(b)

fun JsonElement?.asObject(): JsonObject? = this?.jsonObject

fun JsonElement?.asString(): String? = this?.jsonPrimitive?.contentOrNull

fun JsonElement?.asDouble(): Double? = this?.jsonPrimitive?.doubleOrNull

fun JsonElement.pretty(): String = studioJson.encodeToString(JsonElement.serializer(), this)
