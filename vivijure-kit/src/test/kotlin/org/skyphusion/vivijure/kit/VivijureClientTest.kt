package org.skyphusion.vivijure.kit

import kotlin.test.Test
import kotlin.test.assertEquals

class VivijureClientTest {
  @Test
  fun holdsBaseUrl() {
    val c = VivijureClient(baseUrl = "https://studio.example.com")
    assertEquals("https://studio.example.com", c.baseUrl)
  }
}
