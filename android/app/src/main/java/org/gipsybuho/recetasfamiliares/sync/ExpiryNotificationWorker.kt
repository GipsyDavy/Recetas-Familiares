package org.gipsybuho.recetasfamiliares.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.gipsybuho.recetasfamiliares.MainActivity
import org.gipsybuho.recetasfamiliares.RecetasApplication
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ExpiryNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "expiry_alerts"
        const val NOTIFICATION_ID = 1001
        private const val EXPIRY_DAYS_AHEAD = 3L
    }

    override suspend fun doWork(): Result {
        ensureChannel()
        if (!hasNotificationPermission()) return Result.success()

        val database = (applicationContext as RecetasApplication).container.database
        val today = LocalDate.now()

        val expiring = database.stockDao().findExpiringItems()

        val items = expiring.filter { item ->
            item.expiresAt?.let { dateStr ->
                runCatching {
                    val expiryDate = LocalDate.parse(dateStr.substring(0, 10))
                    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate)
                    daysLeft in 0..EXPIRY_DAYS_AHEAD
                }.getOrDefault(false)
            } ?: false
        }

        if (items.isEmpty()) return Result.success()

        val title = when (items.size) {
            1 -> "1 artículo próximo a caducar"
            else -> "${items.size} artículos próximos a caducar"
        }

        val body = items.joinToString("\n") { item ->
            val daysLeft = item.expiresAt?.let { dateStr ->
                runCatching {
                    ChronoUnit.DAYS.between(today, LocalDate.parse(dateStr.substring(0, 10)))
                }.getOrDefault(-1L)
            } ?: -1L
            "• ${item.name}: " + when (daysLeft) {
                0L -> "caduca hoy"
                1L -> "caduca mañana"
                else -> "caduca en $daysLeft días"
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body.lines().firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas de caducidad",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos de productos del stock próximos a caducar"
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
