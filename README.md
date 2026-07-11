# Recetas Familiares

Aplicacion premium multiplataforma para gestion familiar de recetas, ingredientes, stock, menus, listas de compra, notas, fotos, chat familiar y memoria culinaria compartida.

## Modulos

- `backend/`: API Spring Boot + Java 21 + PostgreSQL + Flyway + JWT.
- `android/`: Android nativo Kotlin + Compose, Room, WorkManager y offline-first.
- `desktop/`: JavaFX + Maven, cliente HTTP, dashboard, modo cocina e instalador Windows. Solo Windows: el almacenamiento seguro de tokens usa Windows DPAPI (`TokenVault`); portar a otro SO requiere sustituirlo por Keychain/libsecret.
- `ios/`: Kotlin Multiplatform + Compose Multiplatform, Ktor, SQLDelight y Keychain.
- `database/`: migraciones y scripts de base de datos.
- `docs/`: documentacion tecnica, roadmap y decisiones.

## Estado Resumido

Estado documentado mas reciente (ver `CONTINUAR.md` para el detalle vivo):
- Backend: 116 tests, 0 fallos en la ultima validacion registrada. Desplegado en Hetzner (systemd + Caddy + PostgreSQL via WireGuard) con CI/CD y backups offsite.
- Android: funcional con recetas, stock, menus, notas, perfil, widgets, temas y sincronizacion offline.
- Desktop: funcional con dashboard, CRUD principal, busqueda, temas, ajustes, diagnostico e instalador v1.1.
- iOS: en desarrollo avanzado; targets Kotlin/Native compilan en Windows, con deuda de validacion runtime/paridad/sincronizacion.
- Chat familiar: texto, imagenes y edicion/borrado propio integrados en backend, Android y Desktop (REST + WebSocket/STOMP). Fases pendientes: video, push e iOS.

Antes de publicar o cerrar sprint, ejecutar validaciones reales y seguir `CLAUDE.md`.

## Arranque Rapido

Backend:

```bash
java -jar backend/target/recetas-familiares-backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  "--spring.datasource.password=<DB_PASSWORD>" \
  "--app.security.jwt.secret=<JWT_SECRET_32_BYTES_MINIMO>"
```

Android:

```powershell
cd android
.\gradlew assembleDebug
```

Desktop:

```powershell
cd desktop
mvn javafx:run -Dapi.base.url=http://localhost:8080/
```

## Documentacion Principal

- `CLAUDE.md`: reglas de trabajo, seguridad, cierre y trazabilidad.
- `CONTINUAR.md`: estado operativo para retomar el proyecto.
- `Resumen.md`: vision de producto y estado funcional consolidado.
- `Interfaz.md`: sistema visual, UX, animaciones y ayuda contextual.
- `auditoria.md`: auditoria historica con hallazgos `SEC-*`, `COD-*`, `UX-*`.
- `MACRO-PROMPT-RECETAS-FAMILIA.md`: plantilla para consultar otros agentes.

## Seguridad

Este proyecto maneja datos familiares, fotos, notas, mensajes de chat, videos, tokens y sincronizacion offline. Cualquier cambio en auth, ownership, imagenes, videos, almacenamiento, API, WebSocket o sincronizacion debe revisarse segun `CLAUDE.md`.

No documentar secretos reales en archivos versionables.
