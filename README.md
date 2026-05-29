# Recetas Familiares

Aplicacion premium multiplataforma para gestion familiar de recetas, ingredientes, menus, listas de compra y memoria culinaria compartida.

## Modulos

- `backend/`: API Spring Boot 3.5 + Java 21 + MySQL + Flyway + JWT. 62 tests, 0 fallos.
- `android/`: aplicacion Android nativa Kotlin + Compose Material3. Offline-first con Room + WorkManager.
- `desktop/`: aplicacion JavaFX 21. Dashboard, modo cocina, exportacion, sincronizacion.
- `ios/`: Kotlin Multiplatform + Compose Multiplatform. Cache offline SQLDelight + Ktor.
- `database/`: migraciones Flyway V1-V9. 14 tablas con soft delete y syncVersion.
- `docs/`: arquitectura, UX, contratos API, seguridad y roadmap.

## Estado (Sprint 25A — 2026-05-29)

Sistema de 10 temas con modo Claro/Oscuro/Sistema implementado en las 3 plataformas cliente:

| Plataforma | Estado | Persistencia |
|------------|--------|--------------|
| Android | Compilado ✅ | DataStore Preferences |
| Desktop | Compilado ✅ | Java Preferences API |
| iOS | Codigo listo ✅ | NSUserDefaults (compilacion requiere macOS) |

Temas disponibles: Bosque, Terracota, Ocaso, Mediterraneo, Lavanda, Oliva, Canela, Menta, Frambuesa, Noche de Verano.

## Arranque rapido

```bash
# Backend
java -jar backend/target/recetas-familiares-backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev "--spring.datasource.password=Recetas2024!"

# Desktop
cd desktop && mvn javafx:run -Dapi.base.url=http://localhost:8080/

# Android
cd android && ./gradlew assembleDebug
```

Credenciales demo: `demo@recetas.local` / `Demo1234!Familia`

Ver `CONTINUAR.md` para el estado completo del proyecto y `Interfaz.md` para el plan visual.
