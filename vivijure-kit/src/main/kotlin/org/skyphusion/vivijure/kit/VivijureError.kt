package org.skyphusion.vivijure.kit

sealed class VivijureError(message: String, cause: Throwable? = null) : Exception(message, cause) {
  class InvalidUrl(url: String) : VivijureError("Invalid URL: $url")
  class MissingToken : VivijureError("Bearer token required")
  class Transport(msg: String, cause: Throwable? = null) : VivijureError(msg, cause)
  class Http(val status: Int, val body: String) : VivijureError("HTTP $status: ${body.take(400)}")
  class Decoding(msg: String, cause: Throwable? = null) : VivijureError(msg, cause)
  class ExpertJson(msg: String) : VivijureError("expert JSON: $msg")
}
