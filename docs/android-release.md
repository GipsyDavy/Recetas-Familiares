# Publicar una release de Android

Cómo generar el APK de distribución de Recetas Familiares. Hasta el 2026-08-06 el único
artefacto posible era el APK **debug**, que es depurable y no debe entregarse a nadie.

---

## 1. Crear el keystore (una sola vez, lo hace el propietario)

El keystore es la identidad de la aplicación. **Si se pierde el fichero o su contraseña, no hay
forma de volver a actualizar la aplicación en ningún dispositivo, ni siquiera reinstalándola.** No
existe procedimiento de recuperación: habría que publicar una aplicación nueva y pedir a cada
usuario que reinstale desde cero.

```bash
keytool -genkeypair -v -keystore recetas-release.jks -alias recetas \
  -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` viene con el JDK. La orden pregunta la contraseña y los datos del certificado.

Después:

- Guarda `recetas-release.jks` **fuera del repositorio**.
- Haz copia de seguridad del fichero y de la contraseña en sitios distintos.
- No lo subas a git: `.gitignore` ya bloquea `*.jks`, `*.keystore` y `keystore.properties`, pero
  la protección es la costumbre, no el fichero.

`-validity 10000` son unos 27 años. Un certificado caducado impide publicar actualizaciones, así
que conviene que sobreviva a la aplicación.

## 2. Declarar las credenciales

Crea `android/keystore.properties`, que git ignora:

```properties
storeFile=C:/ruta/fuera/del/repo/recetas-release.jks
storePassword=<la contraseña del almacén>
keyAlias=recetas
keyPassword=<la contraseña de la clave>
```

`storeFile` admite ruta absoluta o relativa a `android/`.

Si este fichero no existe el build **no falla**: produce un APK sin firmar. Ese es el caso de la
CI y de cualquiera que clone el repositorio, que no deben necesitar la clave de firma.

## 3. Generar el APK

```bash
cd android
gradle :app:assembleRelease
```

Sale en `app/build/outputs/apk/release/app-release.apk`. Si el nombre incluye `unsigned`, el
paso 2 no está bien: Android no instala un APK sin firmar.

Comprobaciones que merece la pena hacer antes de repartirlo:

```bash
# ¿Está firmado, y con el certificado que toca?
"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk

# No debe imprimir nada: si imprime, el APK es depurable y no debe distribuirse.
"$ANDROID_HOME/build-tools/36.1.0/aapt2" dump badging app/build/outputs/apk/release/app-release.apk | grep application-debuggable
```

## 4. Subir la versión en cada entrega

En `android/app/build.gradle.kts`:

- `versionCode`: entero que **debe crecer** en cada publicación. Android rechaza instalar encima
  una versión con `versionCode` igual o menor.
- `versionName`: lo que ve la persona (`1.0.0`, `1.1.0`, …).

## 5. Guardar el `mapping.txt`

R8 ofusca el código, así que cualquier informe de fallo llegará con la traza ilegible. El fichero
que la traduce es:

```
android/app/build/outputs/mapping/release/mapping.txt
```

**Guárdalo junto a cada APK que distribuyas.** Se regenera en cada build y sólo sirve para el APK
con el que se generó; sin él, una traza de esa versión es irrecuperable.

---

## Por qué R8 puede romper la aplicación sin romper el build

`data/remote/dto/ApiDtos.kt` tiene 78 `data class` y **ni un solo `@SerializedName`**: Gson mapea
por el nombre del campo. Si R8 renombrara esos campos a `a`, `b`, `c`, la aplicación compilaría,
instalaría y arrancaría — y fallaría al primer contacto con el servidor.

Lo evita esta regla de `app/proguard-rules.pro`:

```proguard
-keep class org.gipsybuho.recetasfamiliares.data.remote.dto.** { *; }
-keepattributes Signature
```

`Signature` es igual de necesaria: sin ella Gson pierde los tipos genéricos y `List<RecipeDto>` se
deserializa como `List<LinkedTreeMap>`, que revienta al castear.

**Si algún día se añade un DTO fuera de ese paquete, hay que ampliar la regla.** Y toda validación
de un build de release tiene que incluir una petición real contra el servidor: compilar no prueba
nada aquí.

Comprobación rápida de que las reglas siguen aplicando, tras cualquier cambio de dependencias:

```bash
grep "data.remote.dto.AuthResponseDto ->" android/app/build/outputs/mapping/release/mapping.txt
```

Debe mapearse a sí mismo. Si aparece renombrado a algo corto, la regla dejó de aplicar.
