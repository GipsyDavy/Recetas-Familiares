# Publicar una release de Android

Cómo generar el APK de distribución de Recetas Familiares. Hasta el 2026-08-06 el único
artefacto posible era el APK **debug**, que es depurable y no debe entregarse a nadie.

---

## 1. Crear el keystore (una sola vez, lo hace el propietario)

El keystore es la identidad de la aplicación. **Si se pierde el fichero o su contraseña, no hay
forma de volver a actualizar la aplicación en ningún dispositivo, ni siquiera reinstalándola.** No
existe procedimiento de recuperación: habría que publicar una aplicación nueva y pedir a cada
usuario que reinstale desde cero.

**Hecho el 2026-08-08.** Esto queda como referencia por si algún día hay que repetirlo o
entenderlo; la clave de esta aplicación ya existe y **no debe regenerarse**.

```bash
keytool -genkeypair -v -keystore recetas-release.jks -alias recetas \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Recetas Familiares, O=Gipsybuho, C=ES"
```

`keytool` viene con el JDK, es gratuito y el certificado es autofirmado: Android no exige ninguna
autoridad certificadora. Con `-dname` en la orden, lo único que pregunta es la contraseña.

Tres cosas que no son evidentes y cuestan tiempo si se descubren sobre la marcha:

- **El `keytool` moderno crea PKCS12, no JKS**, aunque el fichero se llame `.jks`. En PKCS12 la
  contraseña del almacén y la de la clave **son la misma**: no se pueden separar.
- **Sobre un keystore que ya existe, `keytool` añade una clave nueva en vez de avisar.** Comprobar
  siempre que el destino está libre antes de lanzarlo.
- La contraseña **no se ve mientras se teclea**. No está colgado.

Después:

- Guarda `recetas-release.jks` **fuera del repositorio** (aquí vive en
  `%USERPROFILE%\claves\recetas-familiares\`; la ruta real la fija `keystore.properties`, que git
  ignora).
- Copia del fichero en **dos sitios distintos**, y la contraseña **separada del fichero**: juntos,
  una sola filtración se lo lleva todo.
- `.gitignore` bloquea `*.jks`, `*.keystore` y `keystore.properties`, pero la protección de verdad
  es la costumbre, no el fichero.

`-validity 10000` son unos 27 años: el certificado de esta aplicación vale hasta el **24 de
diciembre de 2053**. Uno caducado impide publicar actualizaciones para siempre.

### Identidad de la clave

Toda release legítima tiene que traer esta huella. Sirve para comprobar, años después, que un APK
salió de esta clave y no de otra:

```
SHA-256  CB:92:93:26:90:F3:22:45:00:B0:EB:E9:54:02:FC:16:47:59:D5:88:37:65:D7:9F:70:AE:3A:48:79:BD:3E:E7
SHA-1    C7:5B:26:1F:54:15:C0:4E:80:65:59:5F:CF:95:34:40:7C:0E:A7:A3
Titular  CN=Recetas Familiares, O=Gipsybuho, C=ES   (autofirmado)
Clave    RSA 4096, firma SHA384withRSA
```

### Si hace falta cambiar la contraseña

Se puede, y **no altera la identidad de la aplicación**: el certificado, la clave y las huellas de
arriba siguen siendo los mismos, así que las instalaciones existentes se siguen actualizando.

```bash
keytool -storepasswd -keystore recetas-release.jks
```

Después hay que actualizar `keystore.properties` a mano.

## 2. Declarar las credenciales

Crea `android/keystore.properties`, que git ignora:

```properties
storeFile=C:/ruta/fuera/del/repo/recetas-release.jks
storePassword=<la contraseña>
keyAlias=recetas
keyPassword=<la misma contraseña: el keystore es PKCS12>
```

`storeFile` admite ruta absoluta o relativa a `android/`. **Usa barras normales `/`**: en un fichero
`.properties` la barra invertida es un carácter de escape, así que `C:\Users\...` no se lee como
esperas.

Si este fichero no existe el build **no falla**: produce un APK sin firmar. Ese es el caso de la
CI y de cualquiera que clone el repositorio, que no deben necesitar la clave de firma.

## 3. Generar el APK

```bash
cd android
gradle :app:assembleRelease
```

Sale en `app/build/outputs/apk/release/app-release.apk`. Si el nombre incluye `unsigned`, el
paso 2 no está bien: Android no instala un APK sin firmar.

Comprobaciones antes de repartirlo. En Windows los binarios son `apksigner.bat` y `aapt2.exe`:

```bash
# ¿Está firmado, y con el certificado que toca? Compara la huella con la de arriba.
"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk

# No debe imprimir nada: si imprime, el APK es depurable y no debe distribuirse.
"$ANDROID_HOME/build-tools/36.1.0/aapt2" dump badging app/build/outputs/apk/release/app-release.apk | grep application-debuggable

# R8 no puede haber renombrado los DTO: debe mapearse a sí mismo (ver el apartado final).
grep "data.remote.dto.AuthResponseDto ->" app/build/outputs/mapping/release/mapping.txt
```

Las cuatro pasaron el 2026-08-08 sobre el primer APK firmado: 3,04 MB, `versionCode=1`,
`versionName=1.0.0`, no depurable, y huella idéntica a la del keystore.

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
