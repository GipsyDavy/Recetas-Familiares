# Publicar una versión nueva

Procedimiento manual para repartir una versión de Desktop o de Android a la familia. Los binarios se
alojan en **GitHub Releases**: no consumen disco ni tráfico del VPS y el enlace es estable.

Al terminar, quien tenga la aplicación abierta verá al arrancar un aviso con la versión nueva y el
enlace de descarga. El aviso lo dispara `GET /api/v1/app-version`, que lee las variables de entorno
del paso 6: **mientras no las actualices, nadie se entera de nada.**

---

## 1. Subir el número de versión

**Desktop** — los dos ficheros, y tienen que coincidir:

- `desktop/pom.xml` → `<version>`
- `desktop/build-installer.ps1` → `$AppVersion`

La aplicación lee su propia versión del manifiesto del JAR (`Implementation-Version`, que Maven
rellena desde el `pom.xml`). Si los dos números no coinciden, el instalador se llamará de una forma y
la aplicación se creerá otra cosa.

**Android** — en `android/app/build.gradle.kts`:

- `versionCode` → **incrementar siempre en 1**. Es un entero, no se ve en ningún sitio y es lo único
  que Android mira para saber que es una actualización. Sin incrementarlo, el APK nuevo no se instala
  encima del viejo.
- `versionName` → el que verá la gente, por ejemplo `"1.1.0"`.

## 2. Generar el instalador de Desktop

```powershell
pwsh -File desktop/build-installer.ps1 -JdkPath "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
```

Sale en `desktop/output/RecetasFamiliares-Instalador-v<version>.exe`. El `-JdkPath` es obligatorio:
`jpackage` necesita el JDK 21 concreto, y en esta máquina hay varios JDK en el `PATH`.

## 3. Generar el APK de Android

```powershell
cd android
./gradlew assembleRelease
```

Sale en `android/app/build/outputs/apk/release/app-release.apk`, firmado con el keystore de
`android/keystore.properties`.

**Guarda el `mapping.txt` de esa compilación junto al APK que repartas**, fuera del repositorio. Se
regenera en cada build y solo sirve para el APK con el que salió: sin él, una traza de error de esa
versión es ilegible.

## 4. Crear la release

```bash
gh release create v<version> \
  "desktop/output/RecetasFamiliares-Instalador-v<version>.exe" \
  "android/app/build/outputs/apk/release/app-release.apk" \
  --title "v<version>" \
  --notes "Qué cambia, en lenguaje de persona."
```

Copia después la URL de cada fichero desde la página de la release: son las que van en el paso 6.

## 5. Comprobar antes de anunciarlo

Descarga el instalador **desde la release**, no desde tu disco, y ejecútalo encima de tu instalación
actual. Debe:

- actualizar sin pedirte desinstalar nada,
- conservar tu sesión iniciada y tus preferencias,
- arrancar y mostrar la versión nueva en Ajustes → Acerca de.

## 6. Decirle al servidor que existe

En el VPS, editar `/etc/recetas-familiares/backend.env`:

```bash
ssh root@167.233.213.242
nano /etc/recetas-familiares/backend.env
```

```
APP_UPDATE_DESKTOP_VERSION=1.3
APP_UPDATE_DESKTOP_URL=https://github.com/GipsyDavy/Recetas-Familiares/releases/download/v1.3/RecetasFamiliares-Instalador-v1.3.exe
APP_UPDATE_ANDROID_VERSION=1.1.0
APP_UPDATE_ANDROID_URL=https://github.com/GipsyDavy/Recetas-Familiares/releases/download/v1.1.0/app-release.apk
```

```bash
systemctl restart recetas-backend
```

**Las URL tienen que ser `https`.** Desktop ignora en silencio cualquier otra cosa: es la defensa
para que un backend comprometido no pueda mandar a nadie a un `file://` ni a un `http://`.

## 7. Verificar

```bash
curl -s https://recetas.167.233.213.242.sslip.io/api/v1/app-version
```

Debe devolver las versiones que acabas de poner. Si devuelve `{"desktop":null,"android":null}`, falta
alguna de las cuatro variables o el servicio no se ha reiniciado.

---

## Cómo se instala una actualización, para explicárselo a la familia

**No hay que desinstalar nada, ni en Windows ni en Android.** Se instala encima y se conserva todo:
sesión, preferencias y datos.

- **Windows**: ejecutar el instalador nuevo. Inno Setup reconoce la instalación anterior por el
  `AppId` fijo de `installer.iss` y actualiza en la misma carpeta. Instala por usuario, no pide
  administrador. **SmartScreen avisará** porque el instalador no está firmado: hay que pulsar
  *Más información → Ejecutar de todos modos*.
- **Android**: instalar el APK nuevo encima. Funciona porque va firmado con el mismo keystore y con
  un `versionCode` mayor. Android pedirá permiso para instalar desde esa fuente la primera vez.

Desinstalar antes solo haría falta si cambiara el `AppId` de Windows o la firma de Android. No es el
caso, y hacerlo perdería la sesión.

## Lo que este procedimiento todavía no hace

- **No es automático.** Generar, subir y actualizar las variables es manual. Automatizarlo en la CI es
  un sprint pendiente.
- **Android no avisa todavía.** El endpoint ya sirve su versión, pero ninguna pantalla de Android la
  consulta. Solo avisa Desktop.
- **Nadie descarga por ti.** El aviso abre el navegador en la página de descarga; instalar lo hace la
  persona. Es deliberado: que la aplicación descargue y ejecute binarios sería una superficie de
  ataque nueva a cambio de ahorrar un clic.
