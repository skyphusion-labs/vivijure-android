package org.skyphusion.vivijure.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Host CONTRACT 2.18: POST /api/storyboard/render takes `scenes: FilmScene[]` where a FilmScene is
 * `{ shot_id, prompt, seconds }`. The web panel builds it with buildFilmScenes (planner-bundle.js).
 */
class FilmScenesTest {
  @Test
  fun buildsFilmScenesLikeThePanel() {
    val sb =
      buildJsonObject {
        put("clip_seconds", 6)
        put(
          "scenes",
          buildJsonArray {
            add(buildJsonObject { put("id", "intro"); put("prompt", "  wide shot  "); put("target_seconds", 2.5) })
            add(buildJsonObject { put("prompt", "second") })
            add(buildJsonObject { put("id", "blank"); put("prompt", "   ") })
            add(buildJsonObject { put("prompt", "fourth"); put("target_seconds", 0) })
          },
        )
      }
    val out = StoryboardHelpers.filmScenes(sb)
    assertEquals(
      """[{"shot_id":"intro","prompt":"wide shot","seconds":2.5},""" +
        """{"shot_id":"shot_02","prompt":"second","seconds":6.0},""" +
        """{"shot_id":"shot_04","prompt":"fourth","seconds":6.0}]""",
      out.toString(),
    )
  }

  @Test
  fun defaultsToFourSecondsWithoutClipSeconds() {
    val sb =
      buildJsonObject {
        put("scenes", buildJsonArray { add(buildJsonObject { put("prompt", "x") }) })
      }
    assertEquals("""[{"shot_id":"shot_01","prompt":"x","seconds":4.0}]""", StoryboardHelpers.filmScenes(sb).toString())
  }
}
