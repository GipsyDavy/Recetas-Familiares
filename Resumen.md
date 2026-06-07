# Resumen.md - Vision y Estado Consolidado Recetas Familiares

## 1. Vision

Recetas Familiares es una aplicacion premium multiplataforma para ayudar a familias a conservar, compartir y usar su memoria culinaria diaria.

El producto combina:
- recetario familiar,
- gestion de stock,
- planificacion de menus,
- lista de compra,
- notas familiares,
- fotos,
- miembros y roles,
- sincronizacion offline-first,
- experiencia calida y premium.

No debe sentirse como un ERP ni una base de datos de recetas. Debe sentirse como un espacio familiar moderno y confiable.

---

## 2. Diferenciadores

- Enfoque familiar real, no solo individual.
- Memoria emocional: fotos, notas, recetas heredadas, favoritos.
- Cocina practica: modo cocina, temporizadores, stock y menus.
- Sincronizacion entre Android, Desktop e iOS.
- Privacidad y ownership por familia.
- UX cuidada: temas, animaciones, skeletons, dark/light mode y accesibilidad.

---

## 3. Arquitectura General

- Backend Spring Boot como fuente de verdad.
- MySQL con migraciones versionadas.
- Android nativo Kotlin + Compose.
- Desktop JavaFX independiente.
- iOS con KMP + Compose Multiplatform.
- Logica compartida KMP como objetivo incremental.
- Clientes con cache local y sincronizacion cuando aplique.

---

## 4. Estado Por Modulo

### Backend

Implementado/documentado:
- Autenticacion JWT + refresh tokens.
- Familias, miembros, roles e invitaciones.
- Recetas, ingredientes y pasos.
- Stock familiar y caducidades.
- Menus semanales.
- Listas de compra.
- Favoritos, valoraciones, notas y fotos.
- Sync pull/push con soft delete, tombstones y `syncVersion`.
- Stats familiares: recetas, miembros, stock y ultima actividad.
- Flyway + MySQL.
- Ultima validacion documentada: 76 tests, 0 fallos.

Riesgos importantes:
- Hardening de JWT/configuracion.
- Proteccion de imagenes subidas.
- Rate limiting detras de proxy.
- Paginacion y robustez de sync.

### Android

Implementado/documentado:
- Login y sesion segura.
- Recetas, detalle, formulario, favoritos, fotos y modo cocina.
- Stock CRUD, caducidades y notificaciones.
- Menus, shopping list, notas y perfil.
- Widgets.
- Offline-first con Room + WorkManager.
- Sistema visual de 10 temas con claro/oscuro/sistema.
- Animaciones, hapticos y skeletons en varias pantallas.

Riesgos importantes:
- `baseSyncVersion` en push offline.
- Timeouts y cancelacion en refresh/login.
- Integracion completa de stats familiares.
- Fuentes premium reales si se exige identidad visual completa.

### Desktop

Implementado/documentado:
- Login, dashboard, recetas, stock, menus, shopping, notas y busqueda global.
- Modo cocina, exportaciones y notificaciones.
- Temas y ajustes como vista central.
- Diagnostico integrado.
- Gestion de miembros y avatar upload.
- Instalador Windows v1.1 documentado.

Riesgos importantes:
- Almacenamiento seguro de tokens.
- Perfil completo.
- Onboarding.
- Recompilacion con JDK 21 LTS antes de produccion.

### iOS

Implementado/documentado:
- Login, recetas, detalle, stock, notas, shopping y menu.
- Keychain para tokens.
- Ktor + SQLDelight.
- Pull incremental.
- Temas, ajustes, perfil, onboarding y hapticos.

Riesgos importantes:
- Build en Windows.
- Push sync completo.
- Refresh automatico 401.
- Paridad visual y funcional con Android.

---

## 5. Experiencia Visual

Direccion:
- calida,
- moderna,
- premium,
- familiar,
- accesible,
- emocional.

Sistema visual:
- 10 temas.
- Modo claro/oscuro/sistema.
- Cards visuales.
- Skeletons en cargas estructuradas.
- Microanimaciones suaves.
- Feedback visual siempre presente.
- Hapticos y sonidos configurables.

La especificacion detallada vive en `Interfaz.md`.

---

## 6. Seguridad y Privacidad

El producto maneja informacion familiar sensible:
- recetas privadas,
- fotos,
- notas,
- miembros,
- emails,
- tokens,
- cache offline.

Principios:
- Ownership por familia en backend.
- No confiar en el cliente.
- No exponer entidades JPA directamente.
- No hardcodear secretos.
- Proteger tokens con mecanismos del sistema.
- Minimizar datos en logs y errores.
- Consentimiento explicito para IA o servicios externos.

La auditoria historica vive en `auditoria.md`. Las reglas operativas viven en `CLAUDE.md`.

---

## 7. Proximo Trabajo Recomendado

Prioridad tecnica:
1. Eliminar fallback JWT secret hardcodeado.
2. Proteger `/uploads/**`.
3. Migrar tokens Desktop a almacenamiento seguro.
4. Corregir rate limiter proxy-aware.
5. Corregir timeouts/cancelacion Android.
6. Corregir `baseSyncVersion` offline.
7. Implementar refresh 401 iOS.
8. Paginar sync pull.

Prioridad UX/producto:
1. Integrar stats familiares en perfil/dashboard.
2. Completar paridad iOS en listas, filtros y skeletons.
3. Perfil Desktop completo.
4. Onboarding Desktop.
5. Ayuda contextual MVP.

---

## 8. Documentos De Referencia

- `CLAUDE.md`: reglas obligatorias de trabajo.
- `CONTINUAR.md`: estado operativo y siguiente sprint.
- `Interfaz.md`: sistema visual y UX.
- `README.md`: entrada rapida del proyecto.
- `auditoria.md`: informe historico de auditoria.
- `MACRO-PROMPT-RECETAS-FAMILIA.md`: plantilla para otros agentes.
