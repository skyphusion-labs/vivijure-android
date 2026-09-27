package org.skyphusion.vivijure.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** Host CONTRACT 2.13: POST /api/storyboard/refine body is { storyboard, message, model }. */
class RefineRequestTest {
  @Test
  fun refineSendsMessageField() {
    MockWebServer().use { server ->
      server.enqueue(
        MockResponse()
          .setHeader("content-type", "application/json; charset=utf-8")
          .setBody("""{"ok":true}"""),
      )
      server.start()
      val c = VivijureClient(server.url("/").toString(), "tok")
      c.refine(buildJsonObject { put("scenes", JsonPrimitive(1)) }, "make it darker", "m1")
      val req = server.takeRequest()
      assertEquals("/api/storyboard/refine", req.path)
      val body: JsonObject =
        studioJson.parseToJsonElement(req.body.readUtf8()).jsonObject
      assertEquals("make it darker", body["message"]?.jsonPrimitive?.content)
      assertEquals(null, body["instruction"])
      assertEquals("m1", body["model"]?.jsonPrimitive?.content)
    }
  }
}
