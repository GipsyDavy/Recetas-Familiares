# AUDITORIA.md - Auditoria Consolidada Recetas Familiares

**Fecha de auditoria:** 2026-05-31
**Alcance:** estado del proyecto hasta Sprint 41
**Naturaleza del documento:** informe historico de auditoria, no estado operativo actual.

Para continuar trabajo diario usar `CONTINUAR.md`. Para reglas de trabajo usar `CLAUDE.md`. Para vision consolidada usar `Resumen.md`.
Sprint 42 resolvio varios hallazgos historicos de este informe (`SEC-1`, `SEC-2`, `SEC-3` parcial, `SEC-4`, `SEC-6`, `SEC-7`, `COD-3`, `COD-7`). Sprint 43 resolvio `SEC-3` completo, `COD-4`, `COD-5` y `UX-6`; la revision post-Sprint 43 endurecio uploads, sync paginado, fallback Desktop, documentacion UX y redujo `COD-2`: los targets Kotlin/Native iOS compilan en Windows, pendiente validar runtime en macOS/dispositivo. Sprint 44 (2026-07-05) resolvio `UX-1` (fuentes Nunito/Lato empaquetadas en `res/font`, sin red), `UX-8`/`UX-13` (onboarding primer arranque Desktop), `UX-11` (shortcuts completos en modo cocina Desktop), redujo `COD-8` (primeros tests unitarios Android y Desktop de sync/repositorios) y regenero el instalador Windows con JDK 21 LTS. Revisar `CONTINUAR.md` y el codigo actual antes de tratarlos como pendientes.

---

## 1. Proposito

Este documento conserva la sintesis de una auditoria comparativa realizada por Claude Code, Codex y Gemini sobre seguridad, arquitectura, calidad de codigo y UX.

No debe usarse como lista viva de tareas sin contrastar antes con el codigo actual y `CONTINUAR.md`, porque algunos hallazgos pueden haber sido resueltos en sprints posteriores.

Protocolo para nuevas auditorias:
- Claude Code actua siempre como agente principal.
- Antes de iniciar una auditoria completa, Claude Code debe proponer apoyo paralelo de Codex y Gemini.
- Si no puede invocarlos directamente, debe preparar bloques listos para copiar y pegar en cada agente.
- Codex debe enfocarse en codigo, arquitectura, seguridad tecnica, tests, build, dependencias y consistencia entre plataformas.
- Gemini debe enfocarse en producto, interfaz, UX, documentacion, duplicidades, ruido e inconsistencias funcionales.
- Si la auditoria es solo lectura, todos los bloques deben indicar expresamente `solo lectura, no modificar archivos`.
- Si el usuario no autoriza Codex o Gemini, Claude Code continuara solo y dejara constancia de esa limitacion en la conclusion.
- Al finalizar, si se recomiendan cambios, Claude Code debe pedir autorizacion explicita antes de modificar archivos y ofrecer opciones de alcance: solo criticos, criticos mas limpieza documental, todos los recomendados, o solo informe sin cambios.

Ejemplo de solicitud:

```text
Autoriza una auditoria paralela con agentes IA sobre seguridad, interfaz, build/dependencias y documentacion, sin hacer cambios.
```

Leyenda de convergencia:
- `[3/3]`: los tres agentes coincidieron.
- `[2/3]`: dos agentes coincidieron.
- `[1/3]`: hallazgo exclusivo de un agente, validado en la sintesis.

---

## 2. Estado General Auditado

Valoracion en la fecha de auditoria:

- MVP avanzado / beta tecnica solida.
- Backend era el modulo mas maduro: 76 tests, 0 fallos.
- Android estaba avanzado y funcional.
- Desktop era funcional, con deuda conocida en almacenamiento seguro de tokens.
- iOS estaba por detras en paridad offline, build Windows y algunas pantallas.
- Seguridad y privacidad estaban tratadas de forma explicita, pero quedaban bloqueantes antes de produccion publica.

Puntos fuertes detectados:
- Backend por capas con DTOs explicitos, Flyway y `ddl-auto=validate`.
- JWT + refresh tokens opacos, rotacion y BCrypt.
- Validacion de ownership familiar en endpoints sensibles.
- Anti-enumeracion en invitaciones.
- Validacion de magic bytes para imagenes JPEG/PNG/WebP.
- EncryptedSharedPreferences en Android y Keychain en iOS.
- Soft delete + `syncVersion` en entidades sincronizables.
- Sistema visual premium definido en tres plataformas.

---

## 3. Hallazgos Bloqueantes Antes de Produccion

Estos hallazgos deben revisarse contra el codigo actual antes de cualquier despliegue publico.

| ID | Severidad | Hallazgo | Accion esperada |
|---|---|---|---|
| SEC-1 | Critica | Fallback de `JWT_SECRET` en `application-dev.yml` | El arranque debe fallar si falta secreto; no usar fallback conocido. |
| SEC-2 | Critica | Desktop guardaba tokens en `java.util.prefs` / Windows Registry | Migrar a Windows Credential Manager, Keychain o libsecret segun plataforma. |
| SEC-3 | Critica | `/uploads/**` publico sin autenticacion | Proteger imagenes con JWT + ownership o URLs prefirmadas de corta duracion. |
| SEC-4 | Alta | Rate limiter basado en `getRemoteAddr()` | Hacerlo proxy-aware solo con proxies de confianza. |
| SEC-5 | Alta | Clientes con HTTP por defecto | Separar dev/prod y bloquear cleartext fuera de localhost/emulador/debug. |
| SEC-6 | Alta | iOS sin refresh automatico ante 401 | Interceptor Ktor que refresque token y reintente una vez. |
| SEC-7 | Alta | Android refresh sin timeouts | Definir `connectTimeout` y `readTimeout`. |

---

## 4. Deuda Tecnica Relevante

| ID | Area | Hallazgo | Prioridad |
|---|---|---|---|
| COD-1 | iOS | Push sync no implementado o incompleto | Alta |
| COD-2 | iOS | Build roto en Windows por SQLDelight/Gradle | Alta |
| COD-3 | Android | `baseSyncVersion` enviado como `null` en push offline | Alta |
| COD-4 | Backend | `lastActivityAt` solo contemplaba recetas | Media |
| COD-5 | Backend | `sync/pull` sin paginacion/lotes | Media |
| COD-6 | Backend | Login con familia inicial no determinista | Media |
| COD-7 | Android | `CancellationException` capturada silenciosamente | Media |
| COD-8 | Clientes | Falta de tests unitarios en Android/Desktop/iOS | Media |
| COD-9 | Backend | `SyncService` con exceso de dependencias | Media |
| COD-10 | Backend | Error de rol reflejaba input del usuario | Baja |
| COD-11 | Docs | README desactualizado respecto a tests | Baja |
| COD-12 | Desktop | Instalador generado con JDK no LTS | Media |

---

## 5. Hallazgos UX Relevantes

| ID | Area | Hallazgo | Prioridad |
|---|---|---|---|
| UX-1 | Android | Fuentes premium declaradas pero no empaquetadas como TTF | Alta |
| UX-2 | Android | NavigationBar no diferenciaba icono seleccionado/inactivo | Media |
| UX-3 | iOS | Falta de paridad: busqueda, filtros, skeletons y pistas visuales | Alta |
| UX-4 | Clientes | Iniciales de avatar calculadas de forma pobre | Baja |
| UX-5 | Desktop | Falta de vista de perfil completa | Media |
| UX-6 | Android | ProfileScreen no consumia completamente `/stats` | Media |
| UX-7 | Desktop | Sidebar con informacion de usuario insuficiente | Baja |
| UX-8 | Onboarding | Primera impresion visual mejorable | Baja |
| UX-9 | Plataformas | Hapticos sin toggle global | Media |
| UX-10 | iOS | Fechas de caducidad sin lenguaje contextual | Baja |
| UX-11 | Desktop | CookingScreen sin shortcuts completos | Baja |
| UX-12 | Desktop | Duraciones de transicion inconsistentes | Baja |
| UX-13 | Desktop | Sin onboarding de primer arranque | Baja |

---

## 6. Plan de Accion Derivado

### Prioridad 1 - Seguridad y produccion
1. Eliminar fallback de secreto JWT.
2. Proteger `/uploads/**`.
3. Migrar tokens Desktop a almacenamiento seguro del sistema.
4. Corregir rate limiter proxy-aware.
5. Anadir timeouts al refresh Android.
6. Revisar uso de HTTP en configuraciones no debug.

### Prioridad 2 - Sincronizacion y paridad
1. Corregir `baseSyncVersion` Android.
2. Implementar push sync iOS.
3. Anadir refresh automatico iOS.
4. Paginar `sync/pull`.
5. Expandir `lastActivityAt` a entidades familiares relevantes.

### Prioridad 3 - UX premium
1. Empaquetar fuentes reales.
2. Completar paridad iOS con Android en busqueda, filtros y skeletons.
3. Integrar stats familiares en perfil/dashboard.
4. Mejorar Perfil Desktop y onboarding Desktop.
5. Anadir toggle global para hapticos.

---

## 7. Uso Correcto de Esta Auditoria

- Antes de implementar un hallazgo, verificar si sigue vigente en el codigo actual.
- Si se resuelve un hallazgo, documentarlo en `CONTINUAR.md` solo si afecta al estado operativo o al proximo sprint.
- No duplicar este informe completo en otros documentos.
- Mantener IDs `SEC-*`, `COD-*`, `UX-*` para trazabilidad.

---

## 8. Conclusion Historica

La auditoria concluyo que el proyecto tenia una base solida, pero no estaba listo para produccion publica sin resolver los bloqueantes de seguridad. Las areas principales eran: proteccion de imagenes, almacenamiento seguro de tokens, hardening JWT/rate-limit, paridad iOS, robustez offline y pulido visual premium.
