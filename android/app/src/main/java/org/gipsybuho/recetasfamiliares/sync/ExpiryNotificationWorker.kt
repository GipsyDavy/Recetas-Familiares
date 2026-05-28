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
        const val NOTIFICATION_ID_TODAY = 1001
        const val NOTIFICATION_ID_WEEK = 1002
        private const val EXPIRY_DAYS_AHEAD = 7L
    }

    override suspend fun doWork(): Result {
        ensureChannel()
        if (!hasNotificationPermission()) return Result.success()

        val database = (applicationContext as RecetasApplication).container.database
        val today = LocalDate.now()

        val expiring = database.stockDao().findExpiringItems()

        fun daysLeft(item: org.gipsybuho.recetasfamiliares.data.local.StockItemEntity): Long =
            item.expiresAt?.let { dateStr ->
                runCatching {
                    ChronoUnit.DAYS.between(today, LocalDate.parse(dateStr.substring(0, 10)))
                }.getOrDefault(-1L)
            } ?: -1L

        val todayItems = expiring.filter { daysLeft(it) == 0L }
        val weekItems  = expiring.filter { daysLeft(it) in 1L..EXPIRY_DAYS_AHEAD }

        if (todayItems.isEmpty() && weekItems.isEmpty()) return Result.success()

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = NotificationManagerCompat.from(applicationContext)

        if (todayItems.isNotEmpty()) {
            val body = todayItems.joinToString("\n") { "‼️ ${it.name}: caduca HOY" }
            nm.notify(
                NOTIFICATION_ID_TODAY,
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("${todayItems.size} artículo${if (todayItems.size > 1) "s" else ""} caducan HOY")
                    .setContentText(todayItems.joinToString(", ") { it.name })
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
            )
        }

        if (weekItems.isNotEmpty()) {
            val body = weekItems.joinToString("\n") { item ->
                val d = daysLeft(item)
                "• ${item.name}: caduca " + if (d == 1L) "mañana" else "en $d días"
            }
            nm.notify(
                NOTIFICATION_ID_WEEK,
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("${weekItems.size} artículo${if (weekItems.size > 1) "s" else ""} caducan esta semana")
                    .setContentText(weekItems.joinToString(", ") { it.name })
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
            )
        }

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
