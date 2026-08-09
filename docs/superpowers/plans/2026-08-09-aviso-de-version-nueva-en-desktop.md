# Aviso de versión nueva en Desktop — Plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar `superpowers:executing-plans` para ejecutar tarea a tarea. Los pasos usan `- [ ]` para seguimiento.

**Objetivo:** que Desktop avise, al arrancar, de que hay una versión más nueva publicada, con el enlace de descarga — sin descargar ni ejecutar nada por su cuenta.

**Arquitectura:** el backend expone `GET /api/v1/app-version`, público y sin datos personales, que devuelve la versión recomendada y la URL de descarga de cada plataforma, configuradas por variables de entorno. Desktop la consulta al arrancar en un hilo virtual, compara con su propia versión (`Implementation-Version` del manifiesto del JAR) y, si la del servidor es mayor, muestra un aviso no modal abajo a la derecha con el enlace. Si el servidor no responde o no hay versión configurada, silencio absoluto.

**Stack:** Spring Boot 3.5 / Java 21 en backend. JavaFX 21 + OkHttp + Gson en Desktop. Sin dependencias nuevas.

## Restricciones globales

- **Sin dependencias nuevas** en ningún módulo.
- **Nunca bloquear el hilo de JavaFX**: todo el trabajo de red va en `Thread.ofVirtual().start(...)` y la vuelta a la interfaz en `Platform.runLater(...)`, siguiendo el patrón de `MainWindow.loadInitialActivity()`.
- **Fallar en silencio**: cualquier error de red, JSON mal formado o configuración ausente no debe producir ningún aviso ni ningún diálogo de error. Enterarse de una actualización es opcional; molestar por un fallo de red no lo es.
- **El aviso no descarga ni ejecuta nada.** Solo abre el navegador del sistema en la URL.
- **Solo se aceptan URL `https`.** Si la URL que llega no empieza por `https://`, se ignora la actualización entera. Protege de que un backend comprometido envíe `file://` o `http://`.
- **Ningún dato personal sale en la consulta.** El endpoint es un GET sin parámetros ni cabecera de autorización.
- **Alojamiento: GitHub Releases.** Las URL apuntan a `https://github.com/GipsyDavy/Recetas-Familiares/releases/...`.
- Los tests `@SpringBootTest` no se pueden ejecutar en local (exigen PostgreSQL real, no hay Docker). Los tests de este plan son todos unitarios puros.

---

## Estructura de ficheros

**Backend**
- Crear `backend/src/main/java/org/gipsybuho/recetasfamiliares/common/api/AppVersionController.java` — el endpoint y su DTO. Un único fichero: es un controlador sin lógica de negocio, no merece un service.
- Modificar `backend/src/main/java/org/gipsybuho/recetasfamiliares/security/SecurityConfig.java` — añadir la ruta a la lista `permitAll`.
- Modificar `backend/src/main/resources/application.yml` — declarar las cuatro propiedades con valor por defecto vacío.
- Crear `backend/src/test/java/org/gipsybuho/recetasfamiliares/common/api/AppVersionControllerTest.java`.

**Desktop**
- Crear `desktop/src/main/java/org/gipsybuho/recetasfamiliares/core/AppVersion.java` — la comparación de versiones y la lectura de la versión propia. Lógica pura, sin JavaFX, para poder testearla.
- Crear `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/UpdateNotificationService.java` — el aviso visual, hermano de `ExpiryNotificationService`.
- Modificar `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ApiClient.java` — añadir `getPublic`, un GET sin cabecera de autorización.
- Modificar `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/` — DTO de la respuesta.
- Modificar `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java` — llamada al arrancar.
- Crear `desktop/src/test/java/org/gipsybuho/recetasfamiliares/core/AppVersionTest.java`.

**Documentación**
- Crear `docs/publicar-una-version.md` — el procedimiento manual de publicar una versión nueva.

---

### Tarea 1: Comparación de versiones en Desktop

Se hace primero porque es lógica pura, no depende de nada y es donde están todos los casos raros.

**Ficheros:**
- Crear: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/core/AppVersion.java`
- Test: `desktop/src/test/java/org/gipsybuho/recetasfamiliares/core/AppVersionTest.java`

**Interfaces:**
- Produce: `AppVersion.isNewer(String candidate, String current) -> boolean` y `AppVersion.current() -> String`.

- [ ] **Paso 1: Escribir el test que falla**

```java
package org.gipsybuho.recetasfamiliares.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppVersionTest {

    @Test
    void unaVersionMayorEsMasNueva() {
        assertTrue(AppVersion.isNewer("1.3", "1.2"));
    }

    /** El caso que rompe la comparacion alfabetica: "1.10" < "1.9" como texto. */
    @Test
    void compararNumeroANumeroNoTextoATexto() {
        assertTrue(AppVersion.isNewer("1.10", "1.9"));
        assertFalse(AppVersion.isNewer("1.9", "1.10"));
    }

    @Test
    void laMismaVersionNoEsMasNueva() {
        assertFalse(AppVersion.isNewer("1.2", "1.2"));
    }

    @Test
    void unaVersionMasAntiguaNoEsMasNueva() {
        assertFalse(AppVersion.isNewer("1.1", "1.2"));
    }

    /** "1.2.1" tiene mas segmentos que "1.2": los que faltan valen cero. */
    @Test
    void losSegmentosQueFaltanCuentanComoCero() {
        assertTrue(AppVersion.isNewer("1.2.1", "1.2"));
        assertFalse(AppVersion.isNewer("1.2", "1.2.0"));
    }

    /** Ante basura, no avisar: es preferible callar que dar un aviso falso. */
    @Test
    void anteUnaVersionIlegibleNoSeAvisa() {
        assertFalse(AppVersion.isNewer("no-es-una-version", "1.2"));
        assertFalse(AppVersion.isNewer("1.3", "tampoco"));
        assertFalse(AppVersion.isNewer(null, "1.2"));
        assertFalse(AppVersion.isNewer("1.3", null));
        assertFalse(AppVersion.isNewer("", "1.2"));
    }
}
```

- [ ] **Paso 2: Verlo fallar**

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
mvn -o -f desktop/pom.xml test "-Dtest=AppVersionTest"
```

Esperado: error de compilación, `AppVersion` no existe. Corregir creando el fichero con el método vacío devolviendo `false`, y volver a ejecutar hasta ver fallos de aserción de verdad, no de compilación.

- [ ] **Paso 3: Implementación mínima**

```java
package org.gipsybuho.recetasfamiliares.core;

/** Version propia de la aplicacion y comparacion de versiones "1.2.3". */
public final class AppVersion {

    private AppVersion() {}

    /**
     * Version de esta compilacion, leida del manifiesto del JAR. Al ejecutar
     * desde el IDE no hay manifiesto y devuelve "0.0", que nunca es menor que
     * nada publicado: en desarrollo no molesta con avisos.
     */
    public static String current() {
        String version = AppVersion.class.getPackage().getImplementationVersion();
        return (version == null || version.isBlank()) ? "0.0" : version;
    }

    /** true si candidate es estrictamente mayor que current. Ante cualquier duda, false. */
    public static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        if (a == null || b == null) {
            return false;
        }
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String[] parts = version.trim().split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException notANumber) {
                return null;
            }
            if (numbers[i] < 0) {
                return null;
            }
        }
        return numbers;
    }
}
```

- [ ] **Paso 4: Verlo pasar**

```powershell
mvn -o -f desktop/pom.xml test "-Dtest=AppVersionTest"
```

Esperado: 6 tests en verde.

- [ ] **Paso 5: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/core/AppVersion.java desktop/src/test/java/org/gipsybuho/recetasfamiliares/core/AppVersionTest.java
git commit -m "feat: comparacion de versiones para el aviso de actualizacion"
```

---

### Tarea 2: El endpoint del backend

**Ficheros:**
- Crear: `backend/src/main/java/org/gipsybuho/recetasfamiliares/common/api/AppVersionController.java`
- Modificar: `backend/src/main/java/org/gipsybuho/recetasfamiliares/security/SecurityConfig.java` (lista `permitAll`, junto a `/api/v1/health`)
- Modificar: `backend/src/main/resources/application.yml` (bloque `app:`)
- Test: `backend/src/test/java/org/gipsybuho/recetasfamiliares/common/api/AppVersionControllerTest.java`

**Interfaces:**
- Consume: nada.
- Produce: `GET /api/v1/app-version` devolviendo
  `{"desktop":{"latestVersion":"1.3","downloadUrl":"https://..."},"android":{...}}`.
  Cuando una plataforma no está configurada, su objeto es `null`.

- [ ] **Paso 1: Escribir el test que falla**

```java
package org.gipsybuho.recetasfamiliares.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppVersionControllerTest {

    @Test
    void devuelveLoConfiguradoParaCadaPlataforma() {
        AppVersionController controller = new AppVersionController(
                "1.3", "https://github.com/GipsyDavy/Recetas-Familiares/releases/latest",
                "1.1.0", "https://github.com/GipsyDavy/Recetas-Familiares/releases/latest");

        var response = controller.appVersion();

        assertThat(response.desktop().latestVersion()).isEqualTo("1.3");
        assertThat(response.android().latestVersion()).isEqualTo("1.1.0");
    }

    /** Sin configurar, el endpoint responde pero no propone nada. */
    @Test
    void sinConfiguracionCadaPlataformaViajaComoNula() {
        AppVersionController controller = new AppVersionController("", "", "", "");

        var response = controller.appVersion();

        assertThat(response.desktop()).isNull();
        assertThat(response.android()).isNull();
    }

    /** Media configuracion es configuracion rota: no se propone una descarga sin URL. */
    @Test
    void unaPlataformaSinUrlNoSePropone() {
        AppVersionController controller = new AppVersionController("1.3", "", "", "");

        assertThat(controller.appVersion().desktop()).isNull();
    }
}
```

- [ ] **Paso 2: Verlo fallar**

```powershell
mvn -o -f backend/pom.xml test "-Dtest=AppVersionControllerTest"
```

Esperado: error de compilación, `AppVersionController` no existe.

- [ ] **Paso 3: Implementación mínima**

```java
package org.gipsybuho.recetasfamiliares.common.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Version recomendada de cada aplicacion cliente y donde descargarla.
 *
 * Publico a proposito: el cliente lo consulta antes de iniciar sesion y la
 * respuesta no contiene ningun dato personal. Se configura por variables de
 * entorno; sin configurar, cada plataforma viaja nula y el cliente no avisa.
 */
@RestController
@RequestMapping("/api/v1/app-version")
public class AppVersionController {

    private final PlatformRelease desktop;
    private final PlatformRelease android;

    public AppVersionController(
            @Value("${app.update.desktop.version:}") String desktopVersion,
            @Value("${app.update.desktop.url:}") String desktopUrl,
            @Value("${app.update.android.version:}") String androidVersion,
            @Value("${app.update.android.url:}") String androidUrl
    ) {
        this.desktop = release(desktopVersion, desktopUrl);
        this.android = release(androidVersion, androidUrl);
    }

    @GetMapping
    public AppVersionResponse appVersion() {
        return new AppVersionResponse(desktop, android);
    }

    private static PlatformRelease release(String version, String url) {
        if (version == null || version.isBlank() || url == null || url.isBlank()) {
            return null;
        }
        return new PlatformRelease(version.trim(), url.trim());
    }

    public record PlatformRelease(String latestVersion, String downloadUrl) {}

    public record AppVersionResponse(PlatformRelease desktop, PlatformRelease android) {}
}
```

- [ ] **Paso 4: Hacer la ruta pública**

En `SecurityConfig.java`, dentro del `requestMatchers(...)` que termina en `.permitAll()`, añadir la línea justo debajo de `"/api/v1/health",`:

```java
                                "/api/v1/app-version",
```

- [ ] **Paso 5: Declarar las propiedades**

En `backend/src/main/resources/application.yml`, dentro del bloque `app:`, después de `mail:`, añadir:

```yaml
  update:
    desktop:
      version: ${APP_UPDATE_DESKTOP_VERSION:}
      url: ${APP_UPDATE_DESKTOP_URL:}
    android:
      version: ${APP_UPDATE_ANDROID_VERSION:}
      url: ${APP_UPDATE_ANDROID_URL:}
```

- [ ] **Paso 6: Verlo pasar**

```powershell
mvn -o -f backend/pom.xml test "-Dtest=AppVersionControllerTest"
```

Esperado: 3 tests en verde.

- [ ] **Paso 7: Commit**

```bash
git add backend/src/main/java/org/gipsybuho/recetasfamiliares/common/api/AppVersionController.java backend/src/test/java/org/gipsybuho/recetasfamiliares/common/api/AppVersionControllerTest.java backend/src/main/java/org/gipsybuho/recetasfamiliares/security/SecurityConfig.java backend/src/main/resources/application.yml
git commit -m "feat: endpoint publico con la version recomendada de cada cliente"
```

---

### Tarea 3: Desktop consulta el endpoint

**Ficheros:**
- Modificar: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ApiClient.java`
- Crear: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/AppVersionDtos.java`

**Interfaces:**
- Consume: `ApiClient.execute(...)` y `ApiClient.url(...)`, ya existentes y privados.
- Produce: `ApiClient.getPublic(String path, Class<T> responseType) throws ApiException` y
  `AppVersionDtos.AppVersionResponse(PlatformRelease desktop, PlatformRelease android)` con
  `PlatformRelease(String latestVersion, String downloadUrl)`.

- [ ] **Paso 1: Crear el DTO**

```java
package org.gipsybuho.recetasfamiliares.api.dto;

/** Respuesta de GET /api/v1/app-version. Campos nulos si no hay version publicada. */
public final class AppVersionDtos {

    private AppVersionDtos() {}

    public record PlatformRelease(String latestVersion, String downloadUrl) {}

    public record AppVersionResponse(PlatformRelease desktop, PlatformRelease android) {}
}
```

- [ ] **Paso 2: Añadir `getPublic` a `ApiClient`**

Justo debajo del método `get`, con el mismo estilo:

```java
    /**
     * GET a un endpoint publico: sin cabecera Authorization. Para llamadas que
     * tienen sentido antes de iniciar sesion, como consultar la version
     * recomendada de la aplicacion.
     */
    public <T> T getPublic(String path, Class<T> responseType) throws ApiException {
        Request request = new Request.Builder()
                .url(url(path))
                .get()
                .build();
        return execute(request, responseType);
    }
```

- [ ] **Paso 3: Compilar**

```powershell
mvn -o -f desktop/pom.xml -DskipTests compile
```

Esperado: BUILD SUCCESS.

- [ ] **Paso 4: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ApiClient.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/AppVersionDtos.java
git commit -m "feat: Desktop puede llamar a endpoints publicos sin token"
```

---

### Tarea 4: El aviso visual

**Ficheros:**
- Crear: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/UpdateNotificationService.java`
- Modificar: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java`

**Interfaces:**
- Consume: `AppVersion.isNewer`, `AppVersion.current`, `ApiClient.getPublic`, `AppVersionDtos`.
- Produce: `UpdateNotificationService.checkInBackground(AppContext context, Stage owner)`.

Se modela sobre `ExpiryNotificationService`, que ya resuelve el aviso no modal abajo a la derecha. Diferencias deliberadas: **no se cierra solo** (tiene un enlace que hay que poder pulsar) y recuerda la versión descartada en `Preferences`.

- [ ] **Paso 1: Escribir el servicio**

```java
package org.gipsybuho.recetasfamiliares.ui;

import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.gipsybuho.recetasfamiliares.api.dto.AppVersionDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.core.AppVersion;

/**
 * Avisa de que hay una version mas nueva publicada. No descarga ni ejecuta
 * nada: abre el navegador del sistema en la URL, que se muestra entera para
 * que se vea a donde lleva.
 */
public final class UpdateNotificationService {

    private static final String PREF_NODE = "org/gipsybuho/recetasfamiliares";
    private static final String PREF_KEY = "update.dismissed.version";

    private UpdateNotificationService() {}

    /** Consulta en segundo plano. Cualquier fallo se traga: el aviso es opcional. */
    public static void checkInBackground(AppContext context, Stage owner) {
        Thread.ofVirtual().start(() -> {
            try {
                AppVersionDtos.AppVersionResponse response = context.getApiClient()
                        .getPublic("/app-version", AppVersionDtos.AppVersionResponse.class);
                AppVersionDtos.PlatformRelease desktop = response == null ? null : response.desktop();
                if (!shouldNotify(desktop)) {
                    return;
                }
                Platform.runLater(() -> show(desktop, owner));
            } catch (Exception ignored) {
                // Sin red, sin servidor o con una respuesta rara: no se avisa de nada.
            }
        });
    }

    static boolean shouldNotify(AppVersionDtos.PlatformRelease release) {
        if (release == null || release.downloadUrl() == null) {
            return false;
        }
        // Solo https: un backend comprometido no debe poder mandarnos a file:// ni a http://
        if (!release.downloadUrl().startsWith("https://")) {
            return false;
        }
        if (!AppVersion.isNewer(release.latestVersion(), AppVersion.current())) {
            return false;
        }
        String dismissed = Preferences.userRoot().node(PREF_NODE).get(PREF_KEY, "");
        return !release.latestVersion().equals(dismissed);
    }

    private static void show(AppVersionDtos.PlatformRelease release, Stage owner) {
        if (!owner.isShowing()) {
            return;
        }

        Stage toast = new Stage();
        toast.initOwner(owner);
        toast.initStyle(StageStyle.TRANSPARENT);

        Label title = new Label("Hay una version nueva: " + release.latestVersion());
        title.getStyleClass().add("expiry-toast-title");

        Label detail = new Label("Tienes la " + AppVersion.current()
                + ". Al instalarla encima se conservan tus datos y tu sesion.");
        detail.setWrapText(true);

        Hyperlink download = new Hyperlink("Descargar desde " + host(release.downloadUrl()));
        download.setOnAction(e -> {
            openInBrowser(release.downloadUrl());
            toast.close();
        });

        Hyperlink dismiss = new Hyperlink("No volver a avisar de esta version");
        dismiss.setOnAction(e -> {
            Preferences.userRoot().node(PREF_NODE).put(PREF_KEY, release.latestVersion());
            toast.close();
        });

        Hyperlink later = new Hyperlink("Mas tarde");
        later.setOnAction(e -> toast.close());

        HBox actions = new HBox(12, download, dismiss, later);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(6, title, detail, actions);
        content.getStyleClass().add("expiry-toast");
        content.setPadding(new Insets(12, 16, 12, 16));
        content.setMaxWidth(380);

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        owner.getScene().getStylesheets().forEach(scene.getStylesheets()::add);
        toast.setScene(scene);

        var bounds = Screen.getPrimary().getVisualBounds();
        toast.setX(bounds.getMaxX() - 400);
        toast.setY(bounds.getMaxY() - 160);
        toast.show();
    }

    private static String host(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (IllegalArgumentException malformed) {
            return "el sitio de descargas";
        }
    }

    private static void openInBrowser(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ignored) {
            // Sin navegador disponible: el usuario siempre puede ir a mano.
        }
    }
}
```

**Nota para quien implemente:** verificar el nombre real del getter del cliente HTTP en `AppContext` (en `MainWindow` se usa `context.getApiClient()`). Si no existe con ese nombre, usar el que haya; no añadir uno nuevo.

- [ ] **Paso 2: Llamarlo al arrancar**

En `MainWindow.java`, **al final del constructor**, justo después del bloque
`if (context.getSession().isLoggedIn()) { showMain(); refreshPersistedRole(); } else { showLogin(); }`:

```java
        UpdateNotificationService.checkInBackground(context, stage);
```

Ese punto y no otro: se ejecuta **una sola vez por arranque**. `showMain()` se llama también al
iniciar sesión y al cambiar de familia, así que colgarlo de ahí repetiría el aviso. Y el endpoint es
público, así que funciona igual con la pantalla de login delante.

- [ ] **Paso 3: Compilar y ejecutar toda la batería de Desktop**

```powershell
mvn -o -f desktop/pom.xml test
```

Esperado: los 109 tests existentes más los 6 de `AppVersionTest`, todos en verde.

- [ ] **Paso 4: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/UpdateNotificationService.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java
git commit -m "feat: Desktop avisa de que hay una version nueva publicada"
```

---

### Tarea 5: El procedimiento de publicar una versión

Sin esto la funcionalidad no sirve: alguien tiene que subir el instalador y decirle al servidor que existe.

**Ficheros:**
- Crear: `docs/publicar-una-version.md`

- [ ] **Paso 1: Escribir el documento**

Debe contener, en este orden y con los comandos exactos:

1. Subir `<version>` en `desktop/pom.xml` y `$AppVersion` en `desktop/build-installer.ps1` (los dos, y deben coincidir).
2. Para Android, subir `versionCode` **y** `versionName` en `android/app/build.gradle.kts`. Recordar que `versionCode` está hoy en `1` y que sin incrementarlo Android no trata el APK como actualización.
3. Generar el instalador:
   `pwsh -File desktop/build-installer.ps1 -JdkPath "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"`
4. Crear la release y subir los binarios:
   `gh release create v<version> desktop/output/RecetasFamiliares-Instalador-v<version>.exe android/app/build/outputs/apk/release/app-release.apk --title "v<version>" --notes "..."`
5. Guardar el `mapping.txt` de Android junto al APK repartido, fuera del repositorio.
6. Actualizar en el VPS `/etc/recetas-familiares/backend.env` con `APP_UPDATE_DESKTOP_VERSION`, `APP_UPDATE_DESKTOP_URL`, `APP_UPDATE_ANDROID_VERSION` y `APP_UPDATE_ANDROID_URL`, y reiniciar: `systemctl restart recetas-backend`.
7. Comprobar: `curl -s https://recetas.167.233.213.242.sslip.io/api/v1/app-version`.

Debe advertir además de que **la URL tiene que ser `https`** o el cliente la ignora en silencio, y de que **el instalador actualiza en sitio** gracias al `AppId` fijo de `installer.iss`: no hay que desinstalar nada.

- [ ] **Paso 2: Commit**

```bash
git add docs/publicar-una-version.md
git commit -m "docs: como publicar una version nueva"
```

---

## Verificación final, antes de dar el sprint por cerrado

- [ ] `mvn -o -f desktop/pom.xml test` en verde.
- [ ] Backend: `mvn -o -f backend/pom.xml test "-Dtest=!*ControllerTest,!BackendApplicationTests,!DevDataSeederTest,!OpenApiConfigTest,!AuthRateLimitFilterTest,!SecurityHardeningTest"` en verde. La suite completa la corre la CI.
- [ ] `pwsh -NoProfile -File scripts/security/run-security-scan.ps1 -Mode sprint` con exit 0.
- [ ] PR con los cuatro checks en verde.
- [ ] **Fusionar despliega a producción**: pedir autorización explícita antes.
- [ ] Tras desplegar, comprobar que el endpoint responde sin token:
      `curl -s https://recetas.167.233.213.242.sslip.io/api/v1/app-version`
      Esperado con la configuración vacía: `{"desktop":null,"android":null}`.
- [ ] Actualizar `CONTINUAR.md`.

## Riesgo residual conocido de antemano

- **El aviso no se puede probar de verdad hasta que exista una release publicada** y las variables configuradas en el VPS. Hasta entonces el endpoint devuelve nulos y el cliente calla, que es el comportamiento correcto pero no demuestra que el aviso se vea bien.
- **Android queda fuera.** El endpoint ya sirve su versión, pero ninguna pantalla la consulta todavía.
- **Publicar sigue siendo manual.** Automatizarlo en la CI es otro sprint.
- **El aviso depende de que el servidor diga la verdad.** Si alguien comprometiera el backend podría anunciar una versión falsa; el daño está acotado a que el usuario vea un enlace, porque la aplicación no descarga ni ejecuta nada y solo acepta `https`. La URL se muestra con su dominio a la vista.
