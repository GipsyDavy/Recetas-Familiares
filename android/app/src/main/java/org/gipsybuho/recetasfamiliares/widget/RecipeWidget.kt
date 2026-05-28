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

class RecipeWidget : AppWidgetProvider() {

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
            val recipes = db.recipeDao().findAll()
            val recipe = if (recipes.isNotEmpty()) {
                val dayIndex = (System.currentTimeMillis() / 86_400_000L % recipes.size).toInt()
                recipes[dayIndex]
            } else null

            for (widgetId in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_recipe)
                views.setTextViewText(R.id.widget_recipe_title, recipe?.title ?: "Sin recetas")
                val subtitle = when {
                    recipe == null -> "Abre la app para sincronizar"
                    recipe.prepMinutes != null && recipe.difficulty != null ->
                        "${recipe.prepMinutes} min · ${recipe.difficulty.replaceFirstChar { it.uppercase() }}"
                    recipe.prepMinutes != null -> "${recipe.prepMinutes} min"
                    recipe.difficulty != null -> recipe.difficulty.replaceFirstChar { it.uppercase() }
                    else -> ""
                }
                views.setTextViewText(R.id.widget_recipe_subtitle, subtitle)

                val pi = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_recipe_root, pi)
                manager.updateAppWidget(widgetId, views)
            }
        } finally {
            db.close()
        }
    }
}
