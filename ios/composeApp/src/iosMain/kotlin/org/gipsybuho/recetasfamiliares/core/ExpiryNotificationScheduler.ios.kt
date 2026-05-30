package org.gipsybuho.recetasfamiliares.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until
import org.gipsybuho.recetasfamiliares.network.StockItemDto
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

private const val ID_TODAY = "recetas_expiry_today"
private const val ID_WEEK  = "recetas_expiry_week"

actual class ExpiryNotificationScheduler actual constructor() {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    actual fun schedule(items: List<StockItemDto>) {
        val options = UNAuthorizationOptionAlert or
                UNAuthorizationOptionSound or
                UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            if (granted) performScheduling(items)
        }
    }

    private fun performScheduling(items: List<StockItemDto>) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        fun daysLeft(item: StockItemDto): Int? = item.expiresAt?.take(10)?.let { raw ->
            runCatching { today.until(LocalDate.parse(raw), DateTimeUnit.DAY) }.getOrNull()
        }

        val todayItems = items.filter { daysLeft(it) == 0 }
        val weekItems  = items.filter { (daysLeft(it) ?: -1) in 1..7 }

        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_TODAY, ID_WEEK))

        if (todayItems.isNotEmpty()) post(
            id    = ID_TODAY,
            title = "${todayItems.size} artículo${if (todayItems.size > 1) "s" else ""} caducan HOY",
            body  = todayItems.joinToString(", ") { it.name },
            delay = 1.0
        )

        if (weekItems.isNotEmpty()) post(
            id    = ID_WEEK,
            title = "${weekItems.size} artículo${if (weekItems.size > 1) "s" else ""} caducan esta semana",
            body  = weekItems.joinToString(", ") { it.name },
            delay = 2.0
        )
    }

    private fun post(id: String, title: String, body: String, delay: Double) {
        val content = UNMutableNotificationContent()
        content.title = title
        content.body  = body
        content.sound = UNNotificationSound.defaultSound()
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = delay,
            repeats      = false
        )
        val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger)
        center.addNotificationRequest(request) { _ -> }
    }
}
