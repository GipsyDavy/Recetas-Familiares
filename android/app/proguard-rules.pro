# Reglas de R8 para el build de release.
#
# Regla de oro de este fichero: todo lo que se resuelva por reflexion en tiempo
# de ejecucion tiene que sobrevivir a la ofuscacion. Un fallo aqui NO rompe la
# compilacion: rompe la aplicacion cuando ya esta instalada.

# ── Gson + DTOs de red ────────────────────────────────────────────────────────
# data/remote/dto/ApiDtos.kt tiene 78 data classes SIN un solo @SerializedName,
# asi que Gson mapea por el nombre del campo. Si R8 renombra esos campos, el
# JSON del backend deja de mapear y la aplicacion falla en el primer login
# aunque haya compilado sin un warning. Esta es la regla que sostiene la app.
-keep class org.gipsybuho.recetasfamiliares.data.remote.dto.** { *; }

# Gson necesita la firma generica intacta para resolver List<RecipeDto> y
# similares; sin Signature devuelve List<LinkedTreeMap> y revienta al castear.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ── Entidades Room ────────────────────────────────────────────────────────────
# Room genera el mapeo en tiempo de compilacion, pero las entidades viajan a
# capas que las serializan. Conservarlas cuesta poco y evita un fallo mudo.
-keep class org.gipsybuho.recetasfamiliares.data.local.** { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
# Los workers se instancian por reflexion a partir del nombre de la clase, que
# viaja persistido en la base de datos de WorkManager.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Widgets ───────────────────────────────────────────────────────────────────
# Declarados en el manifiesto e instanciados por el sistema.
-keep class org.gipsybuho.recetasfamiliares.widget.** { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keepclassmembers class **$WhenMappings { <fields>; }
-keep class kotlin.Metadata { *; }
