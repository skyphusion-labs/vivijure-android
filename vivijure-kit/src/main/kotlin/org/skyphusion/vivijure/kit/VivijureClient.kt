package org.skyphusion.vivijure.kit

/**
 * HTTP client for a Vivijure Studio host (`vivijure-cf` or `vivijure-local`).
 *
 * Skeleton: wire base URL + Bearer token to CONTRACT routes (`/api/modules`, projects,
 * cast, film submit/poll, artifacts). Expand in lockstep with host `docs/CONTRACT.md`.
 */
class VivijureClient(
  val baseUrl: String,
  val bearerToken: String? = null,
) {
  init {
    require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
  }
}
