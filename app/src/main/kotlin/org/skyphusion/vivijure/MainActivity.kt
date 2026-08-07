package org.skyphusion.vivijure

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Scaffold launcher. Full Compose panel comes after kit CONTRACT coverage. */
class MainActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(
      TextView(this).apply {
        text = "Vivijure for Android (skeleton)"
        textSize = 18f
        setPadding(48, 48, 48, 48)
      },
    )
  }
}
