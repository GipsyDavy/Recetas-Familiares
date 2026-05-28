package org.gipsybuho.recetasfamiliares.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.MainActivity
import org.gipsybuho.recetasfamiliares.R
import org.gipsybuho.recetasfamiliares.core.AppContainer
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAll(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val db = Room.databaseBuilder(
            context.applicationContext,
            RecetasDatabase::class.java,
            "recetas-familiares.db"
        ).addMigrations(AppContainer.MIGRATION_1_2).build()
        try {
            val threshold = LocalDate.now().plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val criticalCount = db.stockDao().findCriticalItems(threshold).size

            for (widgetId in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_stock)
                views.setTextViewText(R.id.widget_stock_count, criticalCount.toString())
                views.setTextViewText(
                    R.id.widget_stock_label,
                    if (criticalCount == 1) "crítico" else "críticos"
                )

                val pi = PendingIntent.getActivity(
                    context, 1,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_stock_root, pi)
                manager.updateAppWidget(widgetId, views)
            }
        } finally {
            db.close()
        }
    }
}
