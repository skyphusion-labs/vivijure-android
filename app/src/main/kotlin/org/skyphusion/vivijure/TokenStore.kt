package org.skyphusion.vivijure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted prefs for studio URL + Bearer token. */
class TokenStore(context: Context) {
  private val prefs =
    EncryptedSharedPreferences.create(
      context,
      "vivijure_secure",
      MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

  var studioUrl: String
    get() = prefs.getString(KEY_URL, "").orEmpty()
    set(v) = prefs.edit().putString(KEY_URL, v).apply()

  var token: String
    get() = prefs.getString(KEY_TOKEN, "").orEmpty()
    set(v) = prefs.edit().putString(KEY_TOKEN, v).apply()

  val isConfigured: Boolean
    get() = studioUrl.isNotBlank() && token.isNotBlank()

  fun clearToken() {
    prefs.edit().remove(KEY_TOKEN).apply()
  }

  companion object {
    private const val KEY_URL = "studio_url"
    private const val KEY_TOKEN = "studio_token"
  }
}
