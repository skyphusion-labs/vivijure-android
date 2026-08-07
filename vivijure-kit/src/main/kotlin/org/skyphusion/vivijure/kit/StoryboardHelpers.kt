package org.skyphusion.vivijure.kit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.math.round

val plannerSlotIds = listOf("A", "B", "C", "D")

object StoryboardHelpers {
  fun sceneId(index: Int, scene: JsonElement): String {
    val id = scene.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (id.isNotEmpty()) return id
    return "shot_%02d".format(index + 1)
  }

  fun sceneIds(storyboard: JsonElement): List<String> {
    val scenes = storyboard.jsonObject["scenes"]?.jsonArray ?: return emptyList()
    return scenes.mapIndexed { i, s -> sceneId(i, s) }
  }

  fun useCharacters(storyboard: JsonElement): List<String> {
    val arr = storyboard.jsonObject["use_characters"]?.jsonArray ?: return emptyList()
    return arr.mapNotNull { it.jsonPrimitive.contentOrNull }
  }

  fun scenesFrom(storyboard: JsonElement): List<SceneEdit> {
    val scenes = storyboard.jsonObject["scenes"]?.jsonArray ?: return emptyList()
    return scenes.mapIndexed { idx, scene ->
      val o = scene.jsonObject
      val dlg = o["dialogue"]?.jsonObject
      val slots =
        o["character_slots"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
      SceneEdit(
        index = idx,
        id = o["id"]?.jsonPrimitive?.contentOrNull ?: "scene ${idx + 1}",
        prompt = o["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        targetSeconds =
          o["target_seconds"]?.jsonPrimitive?.doubleOrNull
            ?: o["clip_seconds"]?.jsonPrimitive?.doubleOrNull,
        act = o["act"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        characterSlots = slots.toMutableList(),
        dialogueSlot = dlg?.get("slot")?.jsonPrimitive?.contentOrNull.orEmpty(),
        dialogueText = dlg?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty(),
      )
    }
  }

  fun applyScenes(edits: List<SceneEdit>, storyboard: JsonElement): JsonObject {
    val root = storyboard.jsonObject.toMutableMap()
    val scenes = (root["scenes"] as? JsonArray)?.toMutableList() ?: mutableListOf()
    for (edit in edits) {
      if (edit.index !in scenes.indices) continue
      val o = scenes[edit.index].jsonObject.toMutableMap()
      o["prompt"] = JsonPrimitive(edit.prompt)
      if (edit.targetSeconds != null) {
        o["target_seconds"] = JsonPrimitive(edit.targetSeconds!!)
      } else {
        o.remove("target_seconds")
      }
      val act = edit.act.trim()
      if (act.isEmpty()) o.remove("act") else o["act"] = JsonPrimitive(act)
      if (edit.characterSlots.isEmpty()) {
        o.remove("character_slots")
      } else {
        o["character_slots"] =
          buildJsonArray { edit.characterSlots.forEach { add(JsonPrimitive(it)) } }
      }
      val text = edit.dialogueText.trim()
      val slot = edit.dialogueSlot.trim()
      if (text.isEmpty() || slot.isEmpty()) {
        o.remove("dialogue")
      } else {
        o["dialogue"] =
          buildJsonObject {
            put("slot", JsonPrimitive(slot))
            put("text", JsonPrimitive(text))
          }
      }
      scenes[edit.index] = JsonObject(o)
    }
    root["scenes"] = JsonArray(scenes)
    return JsonObject(root)
  }

  fun deleteScene(index: Int, storyboard: JsonElement): JsonObject {
    val root = storyboard.jsonObject.toMutableMap()
    val scenes = (root["scenes"] as? JsonArray)?.toMutableList() ?: return JsonObject(root)
    if (index !in scenes.indices) return JsonObject(root)
    scenes.removeAt(index)
    root["scenes"] = JsonArray(scenes)
    return JsonObject(root)
  }

  fun snapToBeats(storyboard: JsonElement, bpm: Double, beatsPerShot: Double = 4.0): JsonObject {
    if (bpm <= 0 || beatsPerShot <= 0) return storyboard.jsonObject
    val phrase = (60.0 / bpm) * beatsPerShot
    val root = storyboard.jsonObject.toMutableMap()
    val scenes = (root["scenes"] as? JsonArray)?.toMutableList() ?: return JsonObject(root)
    for (i in scenes.indices) {
      val o = scenes[i].jsonObject.toMutableMap()
      val before =
        o["target_seconds"]?.jsonPrimitive?.doubleOrNull
          ?: o["clip_seconds"]?.jsonPrimitive?.doubleOrNull
          ?: phrase
      val snapped = max(phrase, round(before / phrase) * phrase)
      val fixed = round(snapped * 1000) / 1000.0
      o["target_seconds"] = JsonPrimitive(fixed)
      scenes[i] = JsonObject(o)
    }
    root["scenes"] = JsonArray(scenes)
    return JsonObject(root)
  }

  fun characterRefs(
    useCharacters: List<String>,
    castBindings: Map<String, String>,
    cast: List<CastMember>,
  ): JsonObject {
    val byId = cast.associateBy { it.id }
    return buildJsonObject {
      for (slot in useCharacters) {
        val id = castBindings[slot] ?: continue
        val m = byId[id] ?: continue
        val keys = mutableListOf<String>()
        m.portraitKey?.let { keys += it }
        keys += m.refKeyList
        if (keys.isEmpty()) keys += m.sourceKeyList
        val training =
          buildJsonArray {
            keys.forEach { add(buildJsonObject { put("key", JsonPrimitive(it)) }) }
          }
        put(
          slot,
          buildJsonObject {
            put("name", JsonPrimitive(m.name))
            put("prompt", JsonPrimitive(m.bible.orEmpty()))
            put("trainingImages", training)
          },
        )
      }
    }
  }
}

object RenderConfigSchema {
  private val panelSkip =
    setOf("plan.enhance", "score", "dialogue", "cast.image", "notify")

  fun modules(from: ModulesResponse?): List<RenderConfigModule> {
    val mods = from?.modules ?: return emptyList()
    val out = mutableListOf<RenderConfigModule>()
    for (mod in mods) {
      val o = mod.jsonObject
      val name = o["name"]?.jsonPrimitive?.contentOrNull ?: continue
      val schema = o["config_schema"]?.jsonObject ?: continue
      val fields = mutableListOf<RenderConfigField>()
      for ((key, fieldVal) in schema) {
        val f = fieldVal.jsonObject
        val scope = f["scope"]?.jsonPrimitive?.contentOrNull ?: "render"
        if (scope == "install") continue
        if (key == "quality_tier" || key == "quality") continue
        val type = f["type"]?.jsonPrimitive?.contentOrNull ?: "string"
        val label = f["label"]?.jsonPrimitive?.contentOrNull ?: key
        val enumValues =
          f["values"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        fields +=
          RenderConfigField(
            moduleName = name,
            key = key,
            type = type,
            label = label,
            defaultValue = f["default"],
            min = f["min"]?.jsonPrimitive?.doubleOrNull,
            max = f["max"]?.jsonPrimitive?.doubleOrNull,
            enumValues = enumValues,
          )
      }
      if (fields.isEmpty()) continue
      val label =
        o["provides"]?.jsonArray?.firstOrNull()?.jsonObject?.get("label")
          ?.jsonPrimitive?.contentOrNull
          ?: name
      out += RenderConfigModule(name = name, label = label, fields = fields)
    }
    return out.sortedBy { it.name }
  }

  fun buildOverrides(
    motionBackend: String?,
    fieldValues: Map<String, JsonElement>,
  ): JsonObject {
    val config = mutableMapOf<String, MutableMap<String, JsonElement>>()
    for ((composite, value) in fieldValues) {
      val dot = composite.indexOf('.')
      if (dot <= 0) continue
      val mod = composite.substring(0, dot)
      val key = composite.substring(dot + 1)
      config.getOrPut(mod) { mutableMapOf() }[key] = value
    }
    return buildJsonObject {
      if (config.isNotEmpty()) {
        put(
          "config",
          buildJsonObject {
            for ((mod, fields) in config) {
              put(mod, buildJsonObject { fields.forEach { (k, v) -> put(k, v) } })
            }
          },
        )
      }
      if (!motionBackend.isNullOrBlank()) put("motion_backend", JsonPrimitive(motionBackend))
    }
  }

  fun mergeExpert(base: JsonObject, expert: JsonObject): JsonObject {
    val out = base.toMutableMap()
    for ((k, v) in expert) {
      if (k == "config") continue
      out[k] = v
    }
    if (expert["config"] != null || out["config"] != null) {
      val cfg = (out["config"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
      val expCfg = expert["config"] as? JsonObject
      if (expCfg != null) {
        for ((name, fields) in expCfg) {
          val merged = (cfg[name] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
          val fieldObj = fields as? JsonObject ?: continue
          for ((fk, fv) in fieldObj) merged[fk] = fv
          cfg[name] = JsonObject(merged)
        }
      }
      out["config"] = JsonObject(cfg)
    }
    return JsonObject(out)
  }

  fun parseExpertJson(raw: String): JsonObject? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return try {
      studioJson.parseToJsonElement(t).jsonObject
    } catch (e: Exception) {
      throw VivijureError.ExpertJson(e.message ?: "invalid")
    }
  }

  fun scorePromptScaffold(storyboard: JsonElement, brief: String): String {
    val o = storyboard.jsonObject
    val scenes = o["scenes"]?.jsonArray.orEmpty()
    val style = o["style_prefix"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val concept = o["full_prompt"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val dur =
      o["duration_seconds"]?.jsonPrimitive?.doubleOrNull
        ?: scenes.size * (o["clip_seconds"]?.jsonPrimitive?.doubleOrNull ?: 4.0)
    val parts =
      mutableListOf("Instrumental cinematic underscore, roughly ${dur.toInt()} seconds.")
    if (style.isNotEmpty()) parts += "Style mood: $style."
    if (concept.isNotEmpty()) parts += "Concept energy: ${concept.take(120)}."
    if (brief.isNotEmpty()) parts += "Brief: ${brief.take(120)}."
    parts += "Build energy with the shot progression; no vocals."
    return parts.joinToString(" ")
  }
}
