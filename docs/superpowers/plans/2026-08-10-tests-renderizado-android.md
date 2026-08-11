# Tests de renderizado en Android con Robolectric — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar en verde, en local y en la CI, los primeros tests que componen una pantalla real de Android y comprueban lo que muestra.

**Architecture:** Compose UI Test sobre Robolectric, que corre en la JVM sin emulador y entra en `testDebugUnitTest`, la tarea que la CI ya ejecuta. La pantalla elegida es `HelpSheet`: no depende del `ViewModel`, ni de red, ni de base de datos, así que aísla el problema de arranque de Robolectric del de las dependencias de la aplicación.

**Tech Stack:** Kotlin 2.3.20, Compose BOM 2026.05.00, Material 3, Robolectric 4.14.1, JUnit 4, Gradle 9.5.1, JDK 21.

---

## Por qué falló el intento del 2026-08-09 (diagnóstico corregido)

`CONTINUAR.md` concluyó que era «una incompatibilidad del propio Robolectric en este entorno (JDK 21 con SDK 34)». **Eso es falso.** La evidencia:

El log de la CI (run `31335299900`, Ubuntu limpio, sin Avast) muestra en los cuatro tests:

```
java.security.KeyStoreException at KeyStore.java:879
    Caused by: java.security.NoSuchAlgorithmException at GetInstance.java:159
```

`KeyStore.java:879` es `KeyStore.getInstance(String type)`, que envuelve `NoSuchAlgorithmException` en `KeyStoreException` cuando el tipo de almacén no existe. La cadena real es:

1. `AndroidManifest.xml:10` declara `android:name=".RecetasApplication"`.
2. Robolectric instancia la `Application` del manifiesto **antes de cada test**.
3. `RecetasApplication.onCreate()` construye `AppContainer(this)`.
4. El primer campo de `AppContainer` es `val sessionStore = SessionStore(context)`.
5. `SessionStore` construye un `MasterKey` de `androidx.security.crypto`, que llama a `KeyStore.getInstance("AndroidKeyStore")`.
6. Robolectric no registra el proveedor `AndroidKeyStore`. Excepción.

Es decir: **Robolectric arrancó bien en la CI**. Descargó sus jars, aceptó SDK 34 y JDK 21, instrumentó y ejecutó. Lo que reventó fue el arranque de la propia aplicación. Ni el TLS de Avast ni la versión del JDK tienen nada que ver con el fallo de la CI.

Sobre el fallo *local* (`SunCertPathBuilderException`): ya no aplica. Los jars de Robolectric están descargados y completos en el disco desde el 2026-08-09 22:49:

```
~/.m2/repository/org/robolectric/android-all-instrumented/14-robolectric-10818077-i7/
    android-all-instrumented-14-robolectric-10818077-i7.jar   151.142.227 bytes, 65.305 entradas
```

Robolectric resuelve desde ahí sin volver a la red.

**La corrección es una línea de configuración**: decirle a Robolectric que use una `Application` vacía en vez de `RecetasApplication`.

## Global Constraints

- Robolectric **4.14.1**. Soporta como máximo SDK 35; `compileSdk`/`targetSdk` del proyecto son **36**, así que la configuración **debe** fijar `sdk=34` o Robolectric aborta.
- **No se toca código de producción** salvo el plan de contingencia de la Tarea 2, que está explícitamente acotado y requiere aviso al usuario antes de aplicarse.
- `./gradlew` **no sirve**: su `distributionUrl` apunta a `file:///C:/tmp/tools/gradle-9.5.1-bin.zip`. Usar el `gradle` del PATH (`/c/tmp/tools/gradle-9.5.1/bin/gradle`, versión 9.5.1), que es lo mismo que hace la CI.
- `JAVA_HOME` debe apuntar a `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`.
- Los tests van en `src/test/`, **nunca** en `androidTest/`: el objetivo es que corran sin emulador en la tarea que la CI ya ejecuta.
- Textos de aserto: sacarlos de `HelpContent` por código, no copiarlos a mano. Si alguien reescribe la ayuda, el test debe seguir valiendo.
- Nada de esto toca autenticación, ownership, red ni datos familiares: **no procede `/VibeSec` ni `/security-review`**. Sí procede `run-security-scan.ps1 -Mode sprint` en el cierre, por protocolo.

---

## File Structure

| Fichero | Responsabilidad |
|---|---|
| `android/app/build.gradle.kts` (modificar) | Dependencias de test de Compose + Robolectric, y `testOptions.unitTests.isIncludeAndroidResources = true`, sin lo cual Compose no encuentra los recursos. |
| `android/app/src/test/resources/robolectric.properties` (crear) | Configuración común a **todos** los tests de renderizado presentes y futuros: SDK 34 y `Application` vacía. Evita repetir `@Config` en cada clase. |
| `android/app/src/test/java/org/gipsybuho/recetasfamiliares/ui/HelpSheetRenderTest.kt` (crear) | Los tests de renderizado de la ayuda. |
| `CONTINUAR.md` (modificar) | Corregir el diagnóstico erróneo del intento anterior. |

---

### Task 1: Arrancar Robolectric y componer la primera pantalla

**Files:**
- Modify: `android/app/build.gradle.kts` (bloque `android { ... }` y bloque `dependencies { ... }`)
- Create: `android/app/src/test/resources/robolectric.properties`
- Test: `android/app/src/test/java/org/gipsybuho/recetasfamiliares/ui/HelpSheetRenderTest.kt`

**Interfaces:**
- Consumes: `org.gipsybuho.recetasfamiliares.ui.HelpSheet(screenKey: String?, onDismiss: () -> Unit)` — `internal`, accesible desde `src/test` porque es el mismo módulo Gradle. `org.gipsybuho.recetasfamiliares.core.HelpContent.sections(): List<Section>`, con `Section(emoji: String, title: String, blocks: List<String>)`.
- Produces: la clase `HelpSheetRenderTest` y el fichero `robolectric.properties`, del que dependen todos los tests de renderizado posteriores.

- [ ] **Step 1: Añadir las dependencias de test**

En `android/app/build.gradle.kts`, dentro de `dependencies { ... }`, justo encima de la línea `testImplementation("junit:junit:4.13.2")`:

```kotlin
    // Renderizado en la JVM: Compose UI Test sobre Robolectric, sin emulador,
    // en la misma tarea que el resto de tests. Ver robolectric.properties.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
```

- [ ] **Step 2: Activar los recursos de Android en los tests de JVM**

En el mismo fichero, dentro del bloque `android { ... }`, después del bloque `buildFeatures { ... }` que termina en la línea 69:

```kotlin
    // Compose no puede pintar sin los recursos de Android, y por defecto la
    // tarea de tests de JVM no los incluye.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

- [ ] **Step 3: Escribir el test que falla**

Crear `android/app/src/test/java/org/gipsybuho/recetasfamiliares/ui/HelpSheetRenderTest.kt`:

```kotlin
package org.gipsybuho.recetasfamiliares.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests de renderizado de la ayuda, en la JVM con Robolectric: sin emulador, asi
 * que corren en la misma tarea que el resto y la CI los ejecuta sin cambios.
 *
 * Que cubren y que no: comprueban que la pantalla se compone y muestra lo que
 * debe. NO comprueban pixeles, contraste ni texto recortado, que es donde
 * estuvieron varios de los fallos de la jornada del 2026-08-09. Abrir la
 * aplicacion sigue siendo necesario.
 *
 * La configuracion comun (SDK y Application vacia) vive en
 * src/test/resources/robolectric.properties.
 */
@RunWith(RobolectricTestRunner::class)
class HelpSheetRenderTest {

    @get:Rule
    val compose = createComposeRule()

    /** Con clave nula se abre directamente el indice, que es como entra desde Perfil. */
    @Test
    fun sinPantallaSeAbreDirectamenteElIndice() {
        compose.setContent { HelpSheet(screenKey = null, onDismiss = {}) }

        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
    }
}
```

- [ ] **Step 4: Ejecutarlo y ver que falla por el motivo esperado**

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
gradle -p "C:/Users/GipsyDavy/MAVEN/Recetas Familiares/android" :app:testDebugUnitTest --tests "org.gipsybuho.recetasfamiliares.ui.HelpSheetRenderTest"
```

Esperado: **FALLA** con `java.security.KeyStoreException ... Caused by: java.security.NoSuchAlgorithmException`. Ese es el fallo documentado arriba y confirma el diagnóstico antes de arreglarlo.

Si en su lugar aparece `Robolectric does not support SDK 36`, también vale como fallo esperado: lo arregla el mismo fichero del paso siguiente.

- [ ] **Step 5: Crear la configuración que lo arregla**

Crear `android/app/src/test/resources/robolectric.properties`:

```properties
# Robolectric 4.14.1 llega hasta SDK 35 y el proyecto compila contra 36: sin
# fijar el nivel aqui, aborta.
sdk=34

# Robolectric instancia la Application del manifiesto antes de cada test.
# RecetasApplication construye AppContainer, cuyo primer campo es un
# SessionStore que abre EncryptedSharedPreferences y pide un
# KeyStore.getInstance("AndroidKeyStore"), proveedor que Robolectric no tiene:
# reventaba con KeyStoreException / NoSuchAlgorithmException. Estos tests son de
# pintado y no necesitan el contenedor, asi que arrancan con una Application
# vacia.
application=android.app.Application
```

- [ ] **Step 6: Ejecutar y ver que pasa**

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
gradle -p "C:/Users/GipsyDavy/MAVEN/Recetas Familiares/android" :app:testDebugUnitTest --tests "org.gipsybuho.recetasfamiliares.ui.HelpSheetRenderTest"
```

Esperado: **PASS**, 1 test.

La primera ejecución tarda: Robolectric instrumenta el `android-all` de 151 MB. **No hay descarga**: el jar ya está en `~/.m2/repository/org/robolectric/android-all-instrumented/14-robolectric-10818077-i7/`. Si aun así intenta bajarlo y falla por TLS, ver «Contingencia A» al final.

Si el test falla con `assertIsDisplayed` por no encontrar el nodo, o se queda colgado, ver «Contingencia B».

- [ ] **Step 7: Comprobar que no se ha roto nada de lo anterior**

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
gradle -p "C:/Users/GipsyDavy/MAVEN/Recetas Familiares/android" :app:testDebugUnitTest
```

Esperado: **122 tests, 0 fallos** (los 121 de antes más el nuevo).

- [ ] **Step 8: Commit**

```bash
git checkout -b test/renderizado-android-robolectric
git add android/app/build.gradle.kts android/app/src/test/resources/robolectric.properties android/app/src/test/java/org/gipsybuho/recetasfamiliares/ui/HelpSheetRenderTest.kt
git commit -m "test: primer test de renderizado, con la Application que Robolectric necesita"
```

---

### Task 2: Los tres tests que quedan de la ayuda

**Files:**
- Test: `android/app/src/test/java/org/gipsybuho/recetasfamiliares/ui/HelpSheetRenderTest.kt` (modificar: añadir tres métodos)

**Interfaces:**
- Consumes: de la Tarea 1, la clase `HelpSheetRenderTest` con su `@get:Rule val compose` y `robolectric.properties`. De producción: `HelpContent.topic(screenKey: String): Topic?` con `Topic(emoji: String, title: String, tips: List<String>)`, y `HelpContent.sections(): List<Section>`.
- Produces: cobertura de los tres saltos de navegación de la hoja de ayuda. Nada depende de esta tarea.

**Aviso sobre `LazyColumn`, importante:** el índice pinta las 13 secciones en un `LazyColumn`. Robolectric usa una pantalla pequeña, así que **solo se componen las primeras**. Por eso estos tests usan `HelpContent.sections().first()` («Primeros pasos») y **no** `.last()` («Problemas frecuentes»), que fue lo que hizo el intento anterior. Un aserto sobre una sección de abajo fallaría con «no node found» sin que la pantalla tenga nada malo.

- [ ] **Step 1: Escribir los tres tests que fallan**

Añadir dentro de la clase `HelpSheetRenderTest`, después del test existente, y añadir los imports `androidx.compose.ui.test.performClick` y `org.gipsybuho.recetasfamiliares.core.HelpContent`:

```kotlin
    @Test
    fun laAyudaDeUnaPantallaMuestraSuTituloYSusConsejos() {
        compose.setContent { HelpSheet(screenKey = "recipes", onDismiss = {}) }

        val topic = HelpContent.topic("recipes")!!
        compose.onNodeWithText(topic.title, substring = true).assertIsDisplayed()
        compose.onNodeWithText(topic.tips.first(), substring = true).assertIsDisplayed()
    }

    /** El paso de la ayuda de la pantalla al indice completo. */
    @Test
    fun desdeLaAyudaSeLlegaAlIndiceCompleto() {
        compose.setContent { HelpSheet(screenKey = "recipes", onDismiss = {}) }

        compose.onNodeWithText("Ver todos los temas").performClick()

        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
        // Solo la primera seccion: el resto del indice es un LazyColumn y en la
        // pantalla pequena de Robolectric no llega a componerse.
        compose.onNodeWithText(HelpContent.sections().first().title, substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun alTocarUnaSeccionSeVeSuContenidoYSePuedeVolver() {
        compose.setContent { HelpSheet(screenKey = null, onDismiss = {}) }

        val seccion = HelpContent.sections().first()
        compose.onNodeWithText(seccion.title, substring = true).performClick()

        compose.onNodeWithText(seccion.blocks.first(), substring = true).assertIsDisplayed()

        compose.onNodeWithText("Volver al índice").performClick()
        compose.onNodeWithText("Centro de ayuda").assertIsDisplayed()
    }
```

- [ ] **Step 2: Ejecutarlos**

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
gradle -p "C:/Users/GipsyDavy/MAVEN/Recetas Familiares/android" :app:testDebugUnitTest --tests "org.gipsybuho.recetasfamiliares.ui.HelpSheetRenderTest"
```

Esperado: **PASS, 4 tests**. Si alguno falla, leer el mensaje antes de tocar nada: `onNodeWithText` imprime el árbol de semántica completo, que dice exactamente qué se pintó.

- [ ] **Step 3: Comprobar que un test detecta de verdad un fallo**

No basta con que estén en verde: hay que ver el rojo. Cambiar temporalmente, en `HelpSheet.kt`, el texto `"Ver todos los temas"` por `"Ver todo"`, ejecutar y confirmar que **`desdeLaAyudaSeLlegaAlIndiceCompleto` falla**. Deshacer el cambio inmediatamente:

```bash
git checkout -- android/app/src/main/java/org/gipsybuho/recetasfamiliares/ui/HelpSheet.kt
```

Sin este paso los tests no valen nada: un test que pasa siempre no es un test.

- [ ] **Step 4: Suite completa**

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
gradle -p "C:/Users/GipsyDavy/MAVEN/Recetas Familiares/android" :app:testDebugUnitTest
```

Esperado: **125 tests, 0 fallos**.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/test/java/org/gipsybuho/recetasfamiliares/ui/HelpSheetRenderTest.kt
git commit -m "test: la navegacion de la ayuda, comprobada pintando la pantalla"
```

---

### Task 3: CI en verde y corregir el diagnóstico escrito

**Files:**
- Modify: `CONTINUAR.md` (sección «Intento fallido de tests de renderizado — 2026-08-09», líneas 6924-6967)

**Interfaces:**
- Consumes: las Tareas 1 y 2 en verde en local.
- Produces: el registro corregido. Nada depende de esta tarea.

- [ ] **Step 1: Abrir la PR y esperar a la CI**

```bash
git push -u origin test/renderizado-android-robolectric
gh pr create --title "test: los primeros tests de renderizado, en Android" --body "Cuatro tests de \`HelpSheet\` con Compose UI Test sobre Robolectric, en la JVM y sin emulador, dentro de \`testDebugUnitTest\`.

El intento del 2026-08-09 se cerró concluyendo que Robolectric era incompatible con JDK 21 y SDK 34. **No era eso.** El log de la CI mostraba \`KeyStoreException / NoSuchAlgorithmException\`: Robolectric instancia la \`Application\` del manifiesto antes de cada test, \`RecetasApplication\` construye \`AppContainer\`, y su primer campo abre \`EncryptedSharedPreferences\`, que pide un \`KeyStore\` \`AndroidKeyStore\` que Robolectric no tiene. Robolectric arrancaba perfectamente; lo que reventaba era la aplicación.

Se arregla con \`robolectric.properties\`: SDK 34 (4.14.1 no llega a 36) y \`Application\` vacía.

Qué cubren y qué no: que la pantalla se compone y muestra lo que debe. **No** píxeles, contraste ni texto recortado. Está escrito en el propio test.

No despliega."
```

- [ ] **Step 2: Verificar que los cuatro checks pasan**

```bash
gh pr checks --watch
```

Esperado: `Android CI`, `Desktop CI`, `Backend CI/CD` y `Dependency Audit` en verde. Si `Android CI` falla, leer el log real antes de teorizar:

```bash
gh run view <run-id> --log-failed | grep -iE "error|exception|caused by|FAILED"
```

- [ ] **Step 3: Corregir el diagnóstico en `CONTINUAR.md`**

Sustituir el apartado «### Por que fallo» de la sección «Intento fallido de tests de renderizado — 2026-08-09» (líneas 6939-6948) por:

```markdown
### Por que fallo — DIAGNOSTICO CORREGIDO EL 2026-08-10

La conclusion que se escribio aqui («incompatibilidad del propio Robolectric con
JDK 21 y SDK 34») **era falsa**. El log de la CI decia:

    java.security.KeyStoreException at KeyStore.java:879
        Caused by: java.security.NoSuchAlgorithmException at GetInstance.java:159

`KeyStore.java:879` es `KeyStore.getInstance(type)`. La cadena real: Robolectric
instancia la `Application` del manifiesto antes de cada test -> `RecetasApplication`
construye `AppContainer` -> su primer campo es `SessionStore` -> `MasterKey` pide
`KeyStore.getInstance("AndroidKeyStore")`, proveedor que Robolectric no registra.

**Robolectric arrancaba bien**: descargo sus jars, acepto SDK 34 y JDK 21,
instrumento y ejecuto. Lo que reventaba era el arranque de la aplicacion. Ni el
TLS de Avast ni la version del JDK tenian nada que ver con el fallo de la CI.

Se arregla en `android/app/src/test/resources/robolectric.properties`: `sdk=34`
(4.14.1 no llega al 36 del proyecto) y `application=android.app.Application`.

**La leccion**: se dio por buena una hipotesis plausible sin leer el traza de la
excepcion hasta el final. Media hora de log habria ahorrado el cierre en falso.
```

- [ ] **Step 4: Escaneo de seguridad de cierre**

```powershell
pwsh -NoProfile -File scripts/security/run-security-scan.ps1 -Mode sprint
```

Documentar en el cierre: modo, hallazgos por severidad, secretos verificados y código de salida. Esperado: exit 0 (el cambio no toca código de producción).

- [ ] **Step 5: Commit y fusionar**

```bash
git add CONTINUAR.md
git commit -m "docs: el intento de Robolectric no fallo por lo que se escribio"
git push
```

**No fusionar por iniciativa propia**: pedir autorización explícita al usuario antes de `gh pr merge`.

---

## Contingencias

**Contingencia A — Robolectric intenta descargar y falla con `SunCertPathBuilderException`.**
No debería: el jar está en disco. Si ocurre, forzar el modo sin red añadiendo a `android/gradle.properties`:

```properties
# Robolectric resuelve su android-all desde el disco: el TLS de esta maquina
# lo intercepta Avast y el JDK rechaza la raiz, el mismo motivo por el que las
# reglas de Semgrep son snapshots locales.
android.testInstrumentationRunnerArguments.robolectric.offline=true
```

Si eso no basta, pasar las propiedades al ejecutor de tests en `android/app/build.gradle.kts`, dentro de `testOptions`:

```kotlin
        unitTests.all {
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty(
                "robolectric.dependency.dir",
                "${System.getProperty("user.home")}/.m2/repository/org/robolectric/android-all-instrumented/14-robolectric-10818077-i7"
            )
        }
```

**Contingencia B — el test se cuelga o no encuentra los nodos por culpa de `ModalBottomSheet`.**
`ModalBottomSheet` pinta en una ventana aparte y anima su entrada; bajo Robolectric eso puede dejar el reloj de Compose sin llegar nunca a reposo. Si pasa, **avisar al usuario antes de tocar producción** y aplicar este cambio quirúrgico en `HelpSheet.kt`: extraer el `Column` interior a un `@Composable internal fun HelpSheetBody(screenKey: String?)` y dejar `HelpSheet` como envoltorio de tres líneas que solo pone el `ModalBottomSheet` alrededor. Los tests pasan a componer `HelpSheetBody`, que es donde está toda la lógica que interesa. Es más testable y no cambia lo que ve el usuario, pero es código de producción y no entra sin permiso.

**Contingencia C — la caja de tiempo se agota.**
Si al terminar la Tarea 1 Robolectric sigue sin arrancar por un motivo distinto al diagnosticado, **parar**. No encadenar intentos. El camino alternativo (TestFX + Monocle en Desktop JavaFX) es independiente de todo esto y merece su propio plan.

---

## Fuera de alcance, a propósito

- **Desktop con TestFX + Monocle.** Es otro subsistema y otro plan.
- **Tests instrumentados con emulador en la CI.** Minutos por ejecución y un workflow más frágil, a cambio de poco más que esto.
- **Más pantallas.** Primero que el mecanismo funcione en una; extenderlo después es barato.
- **Comparación de píxeles o capturas de referencia.** Ni Robolectric ni este plan lo cubren, y conviene que quede dicho: **abrir la aplicación sigue siendo obligatorio** antes de dar por cerrado un sprint de interfaz.
