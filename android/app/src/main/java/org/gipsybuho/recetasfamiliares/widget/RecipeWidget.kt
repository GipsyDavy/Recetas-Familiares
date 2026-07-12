package org.gipsybuho.recetasfamiliares.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.gipsybuho.recetasfamiliares.MainActivity
import org.gipsybuho.recetasfamiliares.R
import org.gipsybuho.recetasfamiliares.core.AppContainer
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import java.net.HttpURLConnection
import java.net.URL

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
            val familyId = SessionStore(context.applicationContext).familyId
            val recipes = familyId?.let { db.recipeDao().findAll(it) }.orEmpty()
            val recipe = if (recipes.isNotEmpty()) {
                val dayIndex = (System.currentTimeMillis() / 86_400_000L % recipes.size).toInt()
                recipes[dayIndex]
            } else null

            val photoBitmap: Bitmap? = recipe?.let { r ->
                db.recipePhotoDao().findFirstByRecipeId(r.id, r.familyId)?.url?.let { url ->
                    downloadBitmap(url)
                }
            }

            for (widgetId in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_recipe)
                bindContent(context, views, recipe, photoBitmap)
                manager.updateAppWidget(widgetId, views)
            }
        } finally {
            db.close()
        }
    }

    private fun bindContent(
        context: Context,
        views: RemoteViews,
        recipe: RecipeEntity?,
        photoBitmap: Bitmap?
    ) {
        views.setTextViewText(R.id.widget_recipe_title, recipe?.title ?: "Sin recetas")
        views.setTextViewText(R.id.widget_recipe_subtitle, buildSubtitle(recipe))

        if (photoBitmap != null) {
            views.setImageViewBitmap(R.id.widget_recipe_image, photoBitmap)
            views.setViewVisibility(R.id.widget_recipe_image, View.VISIBLE)
            views.setViewVisibility(R.id.widget_recipe_overlay, View.VISIBLE)
            // Texto blanco sobre foto oscura
            views.setTextColor(R.id.widget_recipe_label, 0xCCFFFFFF.toInt())
            views.setTextColor(R.id.widget_recipe_title, 0xFFFFFFFF.toInt())
            views.setTextColor(R.id.widget_recipe_subtitle, 0xCCFFFFFF.toInt())
        } else {
            views.setViewVisibility(R.id.widget_recipe_image, View.GONE)
            views.setViewVisibility(R.id.widget_recipe_overlay, View.GONE)
            // Texto cálido sobre fondo beige
            views.setTextColor(R.id.widget_recipe_label, 0xFF8B6F5E.toInt())
            views.setTextColor(R.id.widget_recipe_title, 0xFF3D2B1F.toInt())
            views.setTextColor(R.id.widget_recipe_subtitle, 0xFF8B6F5E.toInt())
        }

        // Tap en raíz → abre la app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_recipe_root, openPi)

        // Tap en "▶ Cocinar" → abre la app navegando directamente a esa receta
        val cookPi = if (recipe != null) {
            val cookIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(MainActivity.EXTRA_RECIPE_ID, recipe.id)
            }
            PendingIntent.getActivity(
                context, 1, cookIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else openPi
        views.setOnClickPendingIntent(R.id.widget_recipe_cook_btn, cookPi)
    }

    private fun downloadBitmap(url: String): Bitmap? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout    = 10_000
        connection.connect()
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
            .also { connection.disconnect() }
    }.getOrNull()

    private fun buildSubtitle(recipe: RecipeEntity?): String {
        if (recipe == null) return "Abre la app para sincronizar"
        return when {
            recipe.prepMinutes != null && recipe.difficulty != null ->
                "${recipe.prepMinutes} min · ${recipe.difficulty.replaceFirstChar { it.uppercase() }}"
            recipe.prepMinutes != null -> "${recipe.prepMinutes} min"
            recipe.difficulty  != null -> recipe.difficulty.replaceFirstChar { it.uppercase() }
            else -> ""
        }
    }
}
