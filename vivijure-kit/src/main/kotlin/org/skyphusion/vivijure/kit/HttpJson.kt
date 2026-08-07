package org.skyphusion.vivijure.kit

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Thin JSON / raw HTTP helper for the studio Bearer API. */
class HttpJson(
  baseUrl: String,
  val client: OkHttpClient = defaultClient(),
  val json: Json = studioJson,
) {
  val root: String = baseUrl.trimEnd('/')

  init {
    require(baseUrl.isNotBlank()) { "baseUrl required" }
  }

  fun url(path: String, query: Map<String, String> = emptyMap()): String {
    val p = if (path.startsWith("/")) path else "/$path"
    val base = (root + p).toHttpUrl()
    if (query.isEmpty()) return base.toString()
    val b = base.newBuilder()
    for ((k, v) in query) b.addQueryParameter(k, v)
    return b.build().toString()
  }

  fun request(
    method: String,
    path: String,
    body: ByteArray? = null,
    contentType: String? = null,
    bearer: String? = null,
    query: Map<String, String> = emptyMap(),
  ): Request {
    val builder =
      Request.Builder()
        .url(url(path, query))
        .header("Accept", "application/json")
    if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
    val m = method.uppercase()
    when {
      m == "GET" || m == "HEAD" || m == "DELETE" && body == null -> {
        if (m == "DELETE") builder.delete() else builder.method(m, null)
      }
      body != null -> {
        val media = (contentType ?: "application/json; charset=utf-8").toMediaType()
        builder.method(m, body.toRequestBody(media))
        builder.header("Content-Type", media.toString())
      }
      else -> builder.method(m, ByteArray(0).toRequestBody(null))
    }
    return builder.build()
  }

  fun execute(req: Request): Response {
    return try {
      client.newCall(req).execute()
    } catch (e: IOException) {
      throw VivijureError.Transport("Transport failed: ${e.message}", e)
    }
  }

  fun sendJson(
    method: String,
    path: String,
    bodyJson: String? = null,
    bearer: String?,
    query: Map<String, String> = emptyMap(),
  ): String {
    val bytes = bodyJson?.toByteArray(Charsets.UTF_8)
    val req =
      request(
        method = method,
        path = path,
        body = bytes,
        contentType = if (bytes != null) "application/json; charset=utf-8" else null,
        bearer = bearer,
        query = query,
      )
    val res = execute(req)
    val raw = res.body?.string().orEmpty()
    res.close()
    if (res.code !in 200..299) throw VivijureError.Http(res.code, raw)
    return raw
  }

  fun sendBytes(
    method: String,
    path: String,
    body: ByteArray,
    contentType: String,
    bearer: String?,
  ): ByteArray {
    val req =
      request(
        method = method,
        path = path,
        body = body,
        contentType = contentType,
        bearer = bearer,
      )
    val res = execute(req)
    val raw = res.body?.bytes() ?: ByteArray(0)
    res.close()
    if (res.code !in 200..299) {
      throw VivijureError.Http(res.code, raw.toString(Charsets.UTF_8))
    }
    return raw
  }

  companion object {
    fun defaultClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
  }
}

val studioJson: Json =
  Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
  }
