# Android

Modulo reservado para la aplicacion Android nativa.

Stack objetivo:

- Kotlin
- Material You 3
- MVVM
- Retrofit
- Room
- WorkManager
- offline-first

Responsabilidades iniciales:

- login;
- recetas;
- detalle de receta;
- lista de compra;
- menu semanal;
- cache local;
- sincronizacion con backend.

## Estado actual

Primer scaffold Android nativo creado con:

- Gradle Kotlin DSL;
- Kotlin + Jetpack Compose + Material 3;
- Retrofit apuntando por defecto a `http://10.0.2.2:8080/`;
- Room para cache local inicial de recetas y stock;
- WorkManager para sincronizacion pull periodica;
- MVVM ligero con repositorios;
- pantallas iniciales de login, recetas, detalle y stock.

Nota: en esta maquina no se detecto Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT` vacios). Para compilar hay que abrir `android/` en Android Studio o instalar/configurar el SDK y crear `local.properties`:

```properties
sdk.dir=C\:\\Users\\GipsyDavy\\AppData\\Local\\Android\\Sdk
```

Despues:

```powershell
gradle :app:compileDebugKotlin
gradle :app:assembleDebug
```
