package org.skyphusion.vivijure.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * Host CONTRACT (docs/CONTRACT.md, Appendix A): project and render ids on the wire are opaque
 * public ids (UUID strings), and RenderRow.project_id is the project's public id.
 */
class PublicIdContractTest {
  private val projectId = "3f2b8c1e-5a4d-4e6f-9b7a-1c2d3e4f5a6b"
  private val renderId = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"

  private fun withServer(body: String, block: (VivijureClient, MockWebServer) -> Unit) {
    MockWebServer().use { server ->
      server.enqueue(
        MockResponse()
          .setHeader("content-type", "application/json; charset=utf-8")
          .setBody(body),
      )
      server.start()
      block(VivijureClient(server.url("/").toString(), "tok"), server)
    }
  }

  @Test
  fun listProjectsDecodesPublicIds() {
    withServer("""{"projects":[{"id":"$projectId","slug":"p","name":"P"}]}""") { c, _ ->
      assertEquals(projectId, c.listProjects().single().id.toString())
    }
  }

  @Test
  fun listRendersDecodesPublicIds() {
    withServer(
      """{"renders":[{"id":"$renderId","job_id":"film-1","project_id":"$projectId"}]}""",
    ) { c, _ ->
      val row = c.listRenders().single()
      assertEquals(renderId, row.id.toString())
      assertEquals(projectId, row.projectId.toString())
    }
  }
}

class PublicIdRequestTest {
  private val projectId = "3f2b8c1e-5a4d-4e6f-9b7a-1c2d3e4f5a6b"
  private val renderId = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"

  private fun recorded(block: (VivijureClient) -> Unit): String {
    MockWebServer().use { server ->
      server.enqueue(
        MockResponse()
          .setHeader("content-type", "application/json; charset=utf-8")
          .setBody("""{"renders":[],"ok":true}"""),
      )
      server.start()
      block(VivijureClient(server.url("/").toString(), "tok"))
      return server.takeRequest().path.orEmpty()
    }
  }

  @Test
  fun listRendersFiltersByPublicProjectId() {
    assertEquals(
      "/api/storyboard/renders?project_id=$projectId",
      recorded { it.listRenders(projectId) },
    )
  }

  @Test
  fun renderRoutesAddressPublicRenderId() {
    assertEquals("/api/storyboard/renders/$renderId", recorded { it.deleteRender(renderId) })
  }
}
