package org.skyphusion.vivijure.kit

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * Studio CONTRACT client (Bearer token). Mirrors vivijure-ios / web panel routes.
 */
class VivijureClient(
  baseUrl: String,
  var bearerToken: String? = null,
  client: OkHttpClient = HttpJson.defaultClient(),
) {
  val baseUrl: String = baseUrl.trimEnd('/')
  private val http = HttpJson(this.baseUrl, client)

  init {
    require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
  }

  private fun token(): String {
    val t = bearerToken?.trim().orEmpty()
    if (t.isEmpty()) throw VivijureError.MissingToken()
    return t
  }

  private inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): T {
    val raw = http.sendJson("GET", path, null, token(), query)
    return decode(raw)
  }

  private inline fun <reified T> send(
    method: String,
    path: String,
    body: JsonElement? = null,
    query: Map<String, String> = emptyMap(),
  ): T {
    val bodyJson = body?.let { studioJson.encodeToString(JsonElement.serializer(), it) }
    val raw = http.sendJson(method, path, bodyJson, token(), query)
    return decode(raw)
  }

  private inline fun <reified T> decode(raw: String): T =
    try {
      studioJson.decodeFromString(raw)
    } catch (e: Exception) {
      throw VivijureError.Decoding(e.message ?: e.toString(), e)
    }

  private fun enc(id: String): String = java.net.URLEncoder.encode(id, Charsets.UTF_8).replace("+", "%20")

  // Identity / registry
  fun whoami(): WhoamiResponse = get("/api/whoami")

  fun modules(): ModulesResponse = get("/api/modules")

  fun storyboardModels(): StoryboardModelsResponse = get("/api/storyboard/models")

  // Projects
  fun listProjects(): List<StoryboardProject> = get<ProjectsListResponse>("/api/storyboard/projects").projects

  fun getProject(id: Int): StoryboardProject = get<ProjectItemResponse>("/api/storyboard/projects/$id").project

  fun createProject(name: String): StoryboardProject =
    send<ProjectItemResponse>(
      "POST",
      "/api/storyboard/projects",
      buildJsonObject { put("name", name) },
    ).project

  fun saveStoryboard(projectId: Int, storyboard: JsonElement): StoryboardProject =
    send<ProjectItemResponse>(
      "POST",
      "/api/storyboard/projects/$projectId/storyboard",
      buildJsonObject { put("storyboard", storyboard) },
    ).project

  fun deleteProject(id: Int) {
    http.sendJson("DELETE", "/api/storyboard/projects/$id", null, token())
  }

  // Cast
  fun listCast(): List<CastMember> = get<CastListResponse>("/api/cast").cast

  fun createCast(name: String, bible: String? = null): CastMember =
    send<CastItemResponse>(
      "POST",
      "/api/cast",
      buildJsonObject {
        put("name", name)
        if (bible != null) put("bible", bible)
      },
    ).cast

  fun patchCast(id: String, name: String? = null, bible: String? = null): CastMember =
    send<CastItemResponse>(
      "PATCH",
      "/api/cast/${enc(id)}",
      buildJsonObject {
        if (name != null) put("name", name)
        if (bible != null) put("bible", bible)
      },
    ).cast

  fun deleteCast(id: String) {
    http.sendJson("DELETE", "/api/cast/${enc(id)}", null, token())
  }

  fun uploadCastImage(castId: String, kind: String, data: ByteArray, mime: String): CastMember {
    val path =
      when (kind) {
        "portrait" -> "/api/cast/${enc(castId)}/portrait"
        "ref" -> "/api/cast/${enc(castId)}/ref"
        "source" -> "/api/cast/${enc(castId)}/source"
        else -> throw IllegalArgumentException("kind=$kind")
      }
    val raw = http.sendBytes("POST", path, data, mime, token())
    return decode<CastItemResponse>(raw.toString(Charsets.UTF_8)).cast
  }

  fun trainLora(castId: String, wan: Boolean = false): JsonElement {
    val path =
      if (wan) "/api/cast/${enc(castId)}/train-wan-lora"
      else "/api/cast/${enc(castId)}/train-lora"
    return send("POST", path, buildJsonObject {})
  }

  fun generateRefs(castId: String): JsonElement =
    send("POST", "/api/cast/${enc(castId)}/generate-refs", buildJsonObject {})

  fun pollRefsJob(castId: String, jobId: String): JsonElement =
    get("/api/cast/${enc(castId)}/refs-job/${enc(jobId)}")

  fun exportCast(id: String): ByteArray {
    val req = http.request("GET", "/api/cast/export/${enc(id)}", bearer = token())
    val res = http.execute(req)
    val bytes = res.body?.bytes() ?: ByteArray(0)
    res.close()
    if (res.code !in 200..299) throw VivijureError.Http(res.code, bytes.toString(Charsets.UTF_8))
    return bytes
  }

  fun importCast(tar: ByteArray): CastMember {
    val raw = http.sendBytes("POST", "/api/cast/import", tar, "application/x-tar", token())
    return decode<CastItemResponse>(raw.toString(Charsets.UTF_8)).cast
  }

  // Plan / preflight / bundle
  fun plan(brief: String, model: String?, characters: JsonElement? = null): PlanResponse =
    send(
      "POST",
      "/api/storyboard/plan",
      buildJsonObject {
        put("brief", brief)
        if (model != null) put("model", model)
        if (characters != null) put("characters", characters)
      },
    )

  fun refine(storyboard: JsonElement, instruction: String, model: String? = null): PlanResponse =
    send(
      "POST",
      "/api/storyboard/refine",
      buildJsonObject {
        put("storyboard", storyboard)
        put("instruction", instruction)
        if (model != null) put("model", model)
      },
    )

  fun preflight(
    storyboard: JsonElement,
    castBindings: Map<String, String>? = null,
    quality: String? = null,
  ): PreflightResponse =
    send(
      "POST",
      "/api/storyboard/preflight",
      buildJsonObject {
        put("storyboard", storyboard)
        if (castBindings != null) {
          put(
            "castBindings",
            buildJsonObject { castBindings.forEach { (k, v) -> put(k, v) } },
          )
        }
        if (quality != null) put("quality", quality)
      },
    )

  fun bundle(
    storyboard: JsonElement,
    characterRefs: JsonElement,
    sceneStartImages: JsonElement? = null,
  ): BundleResponse =
    send(
      "POST",
      "/api/storyboard/bundle",
      buildJsonObject {
        put("storyboard", storyboard)
        put("characterRefs", characterRefs)
        if (sceneStartImages != null) put("sceneStartImages", sceneStartImages)
      },
    )

  fun storyboardYaml(storyboard: JsonElement): YamlResponse =
    send("POST", "/api/storyboard/yaml", buildJsonObject { put("storyboard", storyboard) })

  // Render
  fun submitStoryboardRender(body: JsonObject): RenderJobResponse =
    send("POST", "/api/storyboard/render", body)

  fun pollStoryboardRender(jobId: String): RenderJobResponse =
    get("/api/storyboard/render/${enc(jobId)}")

  fun submitScatter(body: JsonObject): RenderJobResponse =
    send("POST", "/api/storyboard/render/scatter", body)

  // History
  fun listRenders(projectId: Int? = null): List<RenderRow> {
    val q = if (projectId != null) mapOf("project_id" to projectId.toString()) else emptyMap()
    return get<RendersListResponse>("/api/storyboard/renders", q).renders
  }

  fun listRenderTags(): List<String> = get<TagsListResponse>("/api/storyboard/renders/tags").tags

  fun patchRender(
    id: Int,
    label: String? = null,
    tags: List<String>? = null,
    lockedShots: List<String>? = null,
  ): RenderRow =
    send(
      "PATCH",
      "/api/storyboard/renders/$id",
      buildJsonObject {
        if (label != null) put("label", label)
        if (tags != null) {
          put(
            "tags",
            kotlinx.serialization.json.buildJsonArray { tags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } },
          )
        }
        if (lockedShots != null) {
          put(
            "lockedShots",
            kotlinx.serialization.json.buildJsonArray {
              lockedShots.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            },
          )
        }
      },
    )

  fun deleteRender(id: Int) {
    http.sendJson("DELETE", "/api/storyboard/renders/$id", null, token())
  }

  fun addAudioToRender(id: Int, audioKey: String): JsonElement =
    send("POST", "/api/storyboard/renders/$id/add-audio", buildJsonObject { put("audioKey", audioKey) })

  fun addNarrationToRender(id: Int, text: String): JsonElement =
    send("POST", "/api/storyboard/renders/$id/add-narration", buildJsonObject { put("text", text) })

  fun finalizeRender(id: Int, audioKey: String? = null, castLoras: Map<String, String>? = null): JsonElement =
    send(
      "POST",
      "/api/storyboard/renders/$id/finalize",
      buildJsonObject {
        if (audioKey != null) put("audioKey", audioKey)
        if (castLoras != null) {
          put("castLoras", buildJsonObject { castLoras.forEach { (k, v) -> put(k, v) } })
        }
      },
    )

  fun animateCloud(id: Int, model: String? = null, perShot: Map<String, String>? = null): JsonElement =
    send(
      "POST",
      "/api/storyboard/renders/$id/animate-cloud",
      buildJsonObject {
        if (model != null) put("model", model)
        if (perShot != null && perShot.isNotEmpty()) {
          put("perShot", buildJsonObject { perShot.forEach { (k, v) -> put(k, v) } })
        }
      },
    )

  fun animateHybrid(
    id: Int,
    backends: JsonElement? = null,
    defaultBackend: String? = "gpu",
    defaultCloudModel: String? = null,
  ): JsonElement =
    send(
      "POST",
      "/api/storyboard/renders/$id/animate-hybrid",
      buildJsonObject {
        if (backends != null) put("backends", backends)
        if (defaultBackend != null) put("defaultBackend", defaultBackend)
        if (defaultCloudModel != null) put("defaultCloudModel", defaultCloudModel)
      },
    )

  fun regenShot(renderId: Int, shotId: String): RenderJobResponse =
    send("POST", "/api/storyboard/renders/$renderId/regen-shot", buildJsonObject { put("shotId", shotId) })

  // Upload / audio / artifacts
  fun uploadImage(data: ByteArray, mime: String): UploadResponse {
    val raw = http.sendBytes("POST", "/api/upload", data, mime, token())
    return decode(raw.toString(Charsets.UTF_8))
  }

  fun uploadCharacterRef(data: ByteArray, mime: String): UploadResponse {
    val raw = http.sendBytes("POST", "/api/storyboard/character-ref", data, mime, token())
    return decode(raw.toString(Charsets.UTF_8))
  }

  fun uploadAudio(data: ByteArray, mime: String): UploadResponse {
    val raw = http.sendBytes("POST", "/api/storyboard/audio-upload", data, mime, token())
    return decode(raw.toString(Charsets.UTF_8))
  }

  fun artifactUrl(key: String, expiresIn: Int = 300): ArtifactURLResponse {
    val pathKey =
      key.split("/").joinToString("/") {
        java.net.URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20")
      }
    return get("/api/artifact-url/$pathKey", mapOf("expires_in" to expiresIn.toString()))
  }

  fun scoreBed(body: JsonObject): JsonElement = send("POST", "/api/storyboard/score-bed", body)

  fun pollJob(id: String): JsonElement = get("/api/job/${enc(id)}")

  fun analyzeAudio(key: String): JsonElement =
    send("POST", "/api/audio/analyze", buildJsonObject { put("key", key) })

  fun chat(model: String, userInput: String): ChatResponse =
    send(
      "POST",
      "/api/chat",
      buildJsonObject {
        put("model", model)
        put("user_input", userInput)
      },
    )

  fun getPrefs(): JsonElement = get<PrefsResponse>("/api/prefs").prefs ?: buildJsonObject {}

  fun patchPrefs(prefs: JsonElement): JsonElement =
    send<PrefsResponse>("PATCH", "/api/prefs", prefs).prefs ?: buildJsonObject {}

  fun listInstalledModules(): List<JsonElement> =
    get<InstalledModulesResponse>("/api/modules/installed").modules.orEmpty()

  fun installModule(scriptName: String): JsonElement =
    send("POST", "/api/modules/install", buildJsonObject { put("script_name", scriptName) })

  fun uninstallModule(name: String) {
    http.sendJson("DELETE", "/api/modules/install/${enc(name)}", null, token())
  }

  fun setModuleEnabled(name: String, enabled: Boolean): JsonElement =
    send("PATCH", "/api/modules/install/${enc(name)}", buildJsonObject { put("enabled", enabled) })

  fun getModuleConfig(name: String): ModuleConfigResponse = get("/api/modules/${enc(name)}/config")

  fun patchModuleConfig(name: String, config: JsonElement): ModuleConfigResponse =
    send("PATCH", "/api/modules/${enc(name)}/config", config)

  fun storageUsage(): StorageUsageResponse = get("/api/storage/usage")

  fun storageReconcile(): JsonElement = send("POST", "/api/storage/reconcile", buildJsonObject {})

  fun demoMenu(): DemoMenuResponse = get("/api/demo/menu")

  fun demoRender(scene: String): DemoRenderResponse =
    send("POST", "/api/demo/render", buildJsonObject { put("scene", scene) })

  fun pollDemoRender(id: String): JsonElement = get("/api/demo/render/${enc(id)}")

  fun demoChat(message: String): ChatResponse =
    send("POST", "/api/demo/chat", buildJsonObject { put("message", message) })
}
