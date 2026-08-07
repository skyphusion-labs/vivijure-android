package org.skyphusion.vivijure

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
  private const val CHANNEL = "vivijure_renders"

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val ch =
      NotificationChannel(CHANNEL, "Render status", NotificationManager.IMPORTANCE_DEFAULT)
    mgr.createNotificationChannel(ch)
  }

  fun notifyRenderDone(context: Context, jobId: String, status: String) {
    ensureChannel(context)
    val n =
      NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_upload)
        .setContentTitle("Vivijure render")
        .setContentText("$jobId: $status")
        .setAutoCancel(true)
        .build()
    try {
      NotificationManagerCompat.from(context).notify(jobId.hashCode(), n)
    } catch (_: SecurityException) {
      // POST_NOTIFICATIONS not granted on API 33+
    }
  }
}
