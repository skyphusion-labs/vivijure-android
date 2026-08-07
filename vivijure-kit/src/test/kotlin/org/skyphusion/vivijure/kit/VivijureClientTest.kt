package org.skyphusion.vivijure.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VivijureClientTest {
  @Test
  fun holdsBaseUrl() {
    val c = VivijureClient(baseUrl = "https://studio.example.com")
    assertEquals("https://studio.example.com", c.baseUrl)
  }

  @Test
  fun rejectsBlankBaseUrl() {
    assertFailsWith<IllegalArgumentException> { VivijureClient(baseUrl = "  ") }
  }

  @Test
  fun sceneIdsPreferId() {
    val sb =
      buildJsonObject {
        put(
          "scenes",
          buildJsonArray {
            add(buildJsonObject { put("id", "intro") })
            add(buildJsonObject { put("prompt", "x") })
          },
        )
      }
    assertEquals(listOf("intro", "shot_02"), StoryboardHelpers.sceneIds(sb))
  }

  @Test
  fun snapToBeats() {
    val sb =
      buildJsonObject {
        put(
          "scenes",
          buildJsonArray {
            add(buildJsonObject { put("target_seconds", 1.0) })
          },
        )
      }
    val next = StoryboardHelpers.snapToBeats(sb, bpm = 120.0, beatsPerShot = 4.0)
    val sec =
      next["scenes"]!!
        .let { it as kotlinx.serialization.json.JsonArray }[0]
        .let { it as kotlinx.serialization.json.JsonObject }["target_seconds"]
        ?.let { (it as JsonPrimitive).content.toDouble() }
    assertEquals(2.0, sec)
  }

  @Test
  fun characterRefsFromCast() {
    val cast =
      listOf(
        CastMember(
          id = "c1",
          name = "Elena",
          bible = "red coat",
          portraitKey = "cast/1/p.png",
          refKeys = listOf(CastImageKey("cast/1/r.png")),
        ),
      )
    val refs =
      StoryboardHelpers.characterRefs(
        useCharacters = listOf("A"),
        castBindings = mapOf("A" to "c1"),
        cast = cast,
      )
    assertEquals("Elena", refs["A"]!!.let { (it as kotlinx.serialization.json.JsonObject)["name"] }
      .let { (it as JsonPrimitive).content })
  }

  @Test
  fun mergeExpertDeepConfig() {
    val base =
      buildJsonObject {
        put("motion_backend", "own-gpu")
        put(
          "config",
          buildJsonObject {
            put("keyframe-sdxl", buildJsonObject { put("steps", 20) })
          },
        )
      }
    val expert =
      buildJsonObject {
        put("motion_backend", "seedance")
        put(
          "config",
          buildJsonObject {
            put("keyframe-sdxl", buildJsonObject { put("guidance", 7) })
          },
        )
      }
    val m = RenderConfigSchema.mergeExpert(base, expert)
    assertEquals("seedance", (m["motion_backend"] as JsonPrimitive).content)
    val steps =
      (m["config"] as kotlinx.serialization.json.JsonObject)["keyframe-sdxl"]
        .let { it as kotlinx.serialization.json.JsonObject }["steps"]
        .let { (it as JsonPrimitive).content }
    assertEquals("20", steps)
  }

  @Test
  fun qualityTiersFallback() {
    val empty = ModulesResponse()
    assertTrue(empty.qualityTiers.isNotEmpty())
  }

  @Test
  fun scoreScaffold() {
    val sb =
      buildJsonObject {
        put("style_prefix", "noir")
        put("duration_seconds", 12)
        put("scenes", buildJsonArray {})
      }
    val p = RenderConfigSchema.scorePromptScaffold(sb, "chase")
    assertTrue(p.contains("Instrumental"))
  }
}
