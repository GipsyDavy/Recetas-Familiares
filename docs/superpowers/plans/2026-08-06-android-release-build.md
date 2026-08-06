# Build de release de Android — Plan de Implementación

> **Para trabajadores agénticos:** SUB-SKILL REQUERIDA: usar `superpowers:executing-plans` para
> implementar tarea a tarea. Los pasos usan sintaxis de checkbox (`- [ ]`).

**Objetivo:** que el módulo Android produzca un APK de release firmado, no depurable y con R8
activado, verificado contra el backend de producción.

**Arquitectura:** se añade el bloque `buildTypes` que hoy no existe en `android/app/build.gradle.kts`.
La firma se lee de un `keystore.properties` fuera del control de versiones, y el build degrada a
APK sin firmar si ese fichero no está, para que la CI y cualquier clon sigan compilando. Las reglas
de R8 viven en un `proguard-rules.pro` nuevo.

**Stack:** Gradle KTS, AGP, R8, Room 2.8.4, Retrofit 3.0.0 + Gson, WorkManager 2.11.2, Compose.

## Restricciones globales

- **`applicationId` no cambia**: `org.gipsybuho.recetasfamiliares`. Cambiarlo sería otra app.
- **Ningún keystore, contraseña ni `keystore.properties` entra en git.** Nunca.
- **El keystore de distribución lo crea el usuario**, no el agente. El agente sólo usa un keystore
  desechable de validación, que jamás debe usarse para distribuir.
- **`minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`** no se tocan.
- El build debe seguir funcionando **sin** `keystore.properties` presente (caso CI).
- Riesgo eje del sprint: `data/remote/dto/ApiDtos.kt` tiene **78 data classes sin un solo
  `@SerializedName`**. Gson mapea por nombre de campo; si R8 los renombra, el JSON deja de mapear y
  la app falla en runtime pese a compilar. Toda validación debe ejercitar red real.

---

### Task 1: Blindar el repositorio contra secretos de firma

**Archivos:**
- Modificar: `.gitignore`

Va primero y en solitario a propósito: si cualquier paso posterior crea un keystore antes de que
git lo ignore, el secreto queda en el árbol y basta un `git add -A` para publicarlo en un
repositorio **público**.

- [ ] **Paso 1: Añadir los patrones al `.gitignore`, bajo la sección `# Android`**

```gitignore
# Firma de release: NUNCA versionar keystores ni sus credenciales
*.jks
*.keystore
keystore.properties
```

- [ ] **Paso 2: Verificar que git ignora los tres patrones**

Ejecutar:
```bash
git check-ignore -v android/app/release.jks android/keystore.properties android/x.keystore
```
Esperado: tres líneas, cada una citando `.gitignore` y el patrón que aplica. Sin salida = fallo.

- [ ] **Paso 3: Commit**

```bash
git add .gitignore
git commit -m "chore: ignora keystores y keystore.properties antes de crear ninguno"
```

---

### Task 2: `buildTypes.release` sin minify, con versionado real

**Archivos:**
- Modificar: `android/app/build.gradle.kts`

**Interfaces:**
- Produce: un `buildTypes.release` que la Task 4 amplía con `isMinifyEnabled` y el fichero de
  reglas, y un `signingConfigs.release` que la Task 3 rellena.

Separado de R8 deliberadamente: entrega un release funcional aunque la Task 4 se tuerza. `release`
ya implica `debuggable=false`, que es lo que de verdad cierra el agujero de seguridad del APK debug.

- [ ] **Paso 1: Subir la versión en `defaultConfig`**

En `android/app/build.gradle.kts`, sustituir:
```kotlin
        versionCode = 1
        versionName = "0.1.0"
```
por:
```kotlin
        versionCode = 1
        versionName = "1.0.0"
```
`versionCode` se queda en 1: la app nunca se ha distribuido, así que 1 es el primer número
legítimo. Sube a 2 en la siguiente entrega.

- [ ] **Paso 2: Añadir el bloque `buildTypes` tras `compileOptions`**

```kotlin
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
```
El `applicationIdSuffix` en debug permite tener instaladas a la vez la de desarrollo y la real sin
que una pise la otra.

- [ ] **Paso 3: Compilar el release**

Ejecutar desde `android/`:
```bash
gradle :app:assembleRelease
```
Esperado: BUILD SUCCESSFUL y un APK en `app/build/outputs/apk/release/`, con `unsigned` en el
nombre porque todavía no hay firma.

- [ ] **Paso 4: Verificar que el APK NO es depurable**

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/release/app-release-unsigned.apk | grep -i "application-debuggable"
```
Esperado: **sin salida**. Cualquier línea `application-debuggable` significa que el agujero sigue
abierto y la tarea no está hecha.

- [ ] **Paso 5: Commit**

```bash
git add android/app/build.gradle.kts
git commit -m "build: anade buildTypes.release no depurable y sube a 1.0.0"
```

---

### Task 3: Firma leída de `keystore.properties`, opcional

**Archivos:**
- Modificar: `android/app/build.gradle.kts`
- Crear (fuera de git): `android/keystore.properties`

**Interfaces:**
- Consume: el `buildTypes.release` de la Task 2.
- Produce: `signingConfigs.release`, aplicado a `release` sólo si el fichero existe.

- [ ] **Paso 1: Leer el fichero al principio de `android/app/build.gradle.kts`**

Antes del bloque `plugins`, no dentro:
```kotlin
import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasSigningConfig = keystoreProperties.getProperty("storeFile") != null
```

- [ ] **Paso 2: Declarar `signingConfigs` antes de `buildTypes`**

```kotlin
    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
```

- [ ] **Paso 3: Aplicarlo al build type `release`**

Dentro de `release { ... }`, como primera línea:
```kotlin
            if (hasSigningConfig) signingConfig = signingConfigs.getByName("release")
```

- [ ] **Paso 4: Verificar que SIN el fichero sigue compilando**

Con `android/keystore.properties` inexistente:
```bash
gradle :app:assembleRelease
```
Esperado: BUILD SUCCESSFUL, APK `-unsigned`. Este es el caso de la CI y de cualquier clon; si
falla, el repositorio queda roto para todos menos para esta máquina.

- [ ] **Paso 5: Crear el keystore DESECHABLE de validación**

Sólo para probar en el emulador. **No sirve para distribuir.**
```bash
keytool -genkeypair -v -keystore /c/Users/GIPSYD~1/AppData/Local/Temp/claude/validacion.jks \
  -alias validacion -keyalg RSA -keysize 2048 -validity 30 \
  -storepass validacion123 -keypass validacion123 \
  -dname "CN=Validacion Sprint, OU=Dev, O=Recetas, L=NA, S=NA, C=ES"
```
Fuera del repositorio a propósito, y con 30 días de validez para que no acabe usándose por error.

- [ ] **Paso 6: Apuntar `keystore.properties` al keystore desechable**

Crear `android/keystore.properties` (ya ignorado por la Task 1):
```properties
storeFile=C:/Users/GIPSYD~1/AppData/Local/Temp/claude/validacion.jks
storePassword=validacion123
keyAlias=validacion
keyPassword=validacion123
```

- [ ] **Paso 7: Verificar que ahora firma**

```bash
gradle :app:assembleRelease
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
```
Esperado: el APK ya no lleva `unsigned` en el nombre y `apksigner` imprime el certificado
`CN=Validacion Sprint`.

- [ ] **Paso 8: Confirmar que git no ve ningún secreto**

```bash
git status --short
```
Esperado: `keystore.properties` **no aparece**. Si aparece, parar y revisar la Task 1.

- [ ] **Paso 9: Commit**

```bash
git add android/app/build.gradle.kts
git commit -m "build: firma de release desde keystore.properties fuera del repositorio"
```

---

### Task 4: Reglas de R8 y activación de minify

**Archivos:**
- Crear: `android/app/proguard-rules.pro`
- Modificar: `android/app/build.gradle.kts`

Aquí es donde el sprint puede romper la aplicación en runtime sin que la compilación se queje.

- [ ] **Paso 1: Escribir `android/app/proguard-rules.pro`**

```proguard
# ── Gson + DTOs de red ────────────────────────────────────────────────────────
# ApiDtos.kt tiene 78 data classes SIN @SerializedName: Gson mapea por el nombre
# del campo. Si R8 los renombra, el JSON del backend deja de mapear y la app
# falla en runtime aunque compile. Esta regla es la que sostiene la aplicacion.
-keep class org.gipsybuho.recetasfamiliares.data.remote.dto.** { *; }

# Gson necesita los tipos genericos intactos para resolver List<RecipeDto> y
# similares, y las anotaciones para el resto.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ── Entidades Room ────────────────────────────────────────────────────────────
# Room genera el mapeo en tiempo de compilacion, pero las entidades se comparten
# con capas que las serializan. Mantenerlas cuesta poco y evita un fallo mudo.
-keep class org.gipsybuho.recetasfamiliares.data.local.** { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
# Los workers se instancian por reflexion a partir del nombre de la clase.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Widgets ───────────────────────────────────────────────────────────────────
# Declarados en el manifiesto; R8 los conserva, pero el receiver debe mantener
# su constructor accesible.
-keep class org.gipsybuho.recetasfamiliares.widget.** { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keepclassmembers class **$WhenMappings { <fields>; }
-keep class kotlin.Metadata { *; }
```

- [ ] **Paso 2: Activar minify en `release`**

Sustituir en el bloque `release`:
```kotlin
            isMinifyEnabled = false
            isShrinkResources = false
```
por:
```kotlin
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
```

- [ ] **Paso 3: Compilar con R8**

```bash
gradle :app:assembleRelease
```
Esperado: BUILD SUCCESSFUL. Si R8 avisa de clases que faltan, leer el aviso: casi siempre es una
dependencia opcional y se resuelve con `-dontwarn` acotado a ese paquete, **nunca** con
`-dontwarn **`.

- [ ] **Paso 4: Comprobar que los DTOs sobrevivieron con su nombre**

```bash
grep -c "recetasfamiliares" app/build/outputs/mapping/release/mapping.txt
grep "data.remote.dto.LoginRequestDto" app/build/outputs/mapping/release/mapping.txt
```
Esperado: la segunda orden imprime `LoginRequestDto -> ...LoginRequestDto`, es decir, se mapea a sí
misma. Si aparece renombrada a algo corto, la regla no está aplicando y el login fallará.

- [ ] **Paso 5: Commit**

```bash
git add android/app/proguard-rules.pro android/app/build.gradle.kts
git commit -m "build: activa R8 con reglas que preservan los DTOs de Gson"
```

---

### Task 5: Validación en el emulador contra producción

**Archivos:** ninguno. Es la tarea que decide si las Tasks 2-4 valen algo.

Compilar no prueba nada aquí: el fallo de R8 con Gson sólo aparece hablando con el servidor.

- [ ] **Paso 1: Arrancar el AVD**

```bash
"$ANDROID_HOME/emulator/emulator" -avd Pixel_9_Pro -no-snapshot-load &
adb wait-for-device
```

- [ ] **Paso 2: Desinstalar cualquier versión previa**

```bash
adb uninstall org.gipsybuho.recetasfamiliares || true
```
Obligatorio: la firma de validación no coincide con la del APK debug y la instalación fallaría con
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

- [ ] **Paso 3: Instalar el APK de release firmado**

```bash
adb install app/build/outputs/apk/release/app-release.apk
```
Esperado: `Success`.

- [ ] **Paso 4: Lanzar y capturar los fallos de red**

```bash
adb logcat -c
adb shell monkey -p org.gipsybuho.recetasfamiliares -c android.intent.category.LAUNCHER 1
```

- [ ] **Paso 5: Hacer login real contra producción y observar**

La app apunta por defecto a `https://recetas.167.233.213.242.sslip.io/`. Introducir credenciales
reales en la pantalla de login y esperar a que entre.

```bash
adb logcat -d | grep -iE "JsonSyntaxException|IllegalStateException|ClassCastException|FATAL|AndroidRuntime"
```
Esperado: **sin salida**. Un `JsonSyntaxException` o un campo nulo donde no debería significa que
R8 renombró un DTO: volver a la Task 4 y ampliar la regla `-keep`.

- [ ] **Paso 6: Ejercitar las rutas que dependen de Gson y de Room**

Recorrer en la app: listado de recetas con portadas, detalle de una receta, stock, menú semanal y
perfil. Son las que cubren deserialización de listas anidadas, imágenes autenticadas de
`/uploads/**` y lectura de Room.

Capturar evidencia:
```bash
adb exec-out screencap -p > /c/Users/GIPSYD~1/AppData/Local/Temp/claude/release-recetas.png
```

- [ ] **Paso 7: Verificar que la sincronización en segundo plano no casca**

```bash
adb logcat -d | grep -iE "SyncWorker|WorkerFactory|ClassNotFoundException"
```
Esperado: sin `ClassNotFoundException`. Si aparece, la regla de `ListenableWorker` no está
cogiendo.

---

### Task 6: Documentar y cerrar

**Archivos:**
- Modificar: `CONTINUAR.md`
- Modificar: `android/README.md` si existe; si no, crear `docs/android-release.md`

- [ ] **Paso 1: Escribir el procedimiento del keystore de producción**

Debe incluir, literal, la orden que el usuario tiene que ejecutar él:
```bash
keytool -genkeypair -v -keystore recetas-release.jks -alias recetas \
  -keyalg RSA -keysize 2048 -validity 10000
```
Y la advertencia: si se pierde ese fichero o su contraseña, **no hay forma de volver a actualizar
la aplicación**, ni siquiera reinstalando. Guardarlo fuera del repositorio y con copia de
seguridad.

- [ ] **Paso 2: Cerrar el sprint en `CONTINUAR.md`**

Sección nueva con: qué se construyó, la trampa de Gson + R8 y cómo se detectó, la tabla de
validación con resultados reales, y el riesgo residual actualizado.

- [ ] **Paso 3: Ejecutar el escaneo de seguridad**

```bash
pwsh -NoProfile -File scripts/security/run-security-scan.ps1 -Mode sprint
```
Esperado: exit 0. Presta atención especial a que TruffleHog no encuentre el keystore ni sus
contraseñas.

- [ ] **Paso 4: Commit y PR**

```bash
git add CONTINUAR.md docs/
git commit -m "docs: cierra el sprint del build de release de Android"
git push -u origin build/android-release
gh pr create
```
