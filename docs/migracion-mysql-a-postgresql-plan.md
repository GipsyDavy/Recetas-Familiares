# Plan de migración MySQL → PostgreSQL (backend en Hetzner)

Documento de planificación para un sprint **no autorizado todavía**. Sirve para arrancar en frío: contiene la decisión, la evidencia de viabilidad, el alcance, el plan paso a paso, las decisiones pendientes del usuario, la validación esperada y los riesgos.

Fecha de análisis: 2026-07-08. Autor del análisis: Claude Code (solo lectura, sin cambios).

Actualizacion 2026-07-09: la decision 2 quedo corregida durante la ejecucion. En PostgreSQL se usa `varchar(36)`, no `CHAR(36)`, porque Hibernate `ddl-auto=validate` reporta `CHAR` como `bpchar`/`Types#CHAR` y no lo acepta para los ids Java `String`.

---

## 1. Decisión y objetivo

**Camino elegido (a confirmar por el usuario): Spring Boot + PostgreSQL en Hetzner.**

- Se mantiene el backend Spring Boot actual y toda su seguridad ya auditada (Spring Security, JWT, refresh tokens, ownership por familia, rate limit, chat STOMP).
- Se cambia únicamente el motor de base de datos: **MySQL 8.0 → PostgreSQL**.
- La DB se aloja en **Hetzner** (VPS autogestionado con Postgres, salvo que se opte por un Postgres gestionado de terceros).
- **NO** se adopta Supabase completo (Camino 2 descartado por ser reescritura de backend + 3 clientes + operar stack Supabase self-hosted). Aclaración clave: "Supabase" no es una base de datos; su motor es Postgres. La ventaja real sobre MySQL es **Postgres**, y se obtiene sin reescritura.

Motivación: Postgres es generalmente superior a MySQL (tipos, integridad, extensiones, JSONB, índices parciales, CTEs, etc.) y saca la DB del equipo local a un servidor controlado.

---

## 2. Evidencia de viabilidad (por qué el cambio es acotado)

Recogida por inspección del código el 2026-07-08:

| Aspecto | Hallazgo | Consecuencia |
|---|---|---|
| Queries `@Query` | **Todas JPQL, 0 `nativeQuery=true`** (verificado en repositorios de notes, users, chat, auth, favorites, families, shopping, menus, recipes, stock, photos) | Hibernate genera el SQL según dialecto → **cero reescrituras de consultas** |
| Timestamps | Entidades fijan `created_at`/`updated_at` con `@PrePersist`/`@PreUpdate` (JPA lifecycle) | El `ON UPDATE CURRENT_TIMESTAMP(6)` del DDL MySQL es redundante → se elimina sin cambio de comportamiento |
| Tipos SQL | UUID textuales en `varchar(36)`, VARCHAR, BIGINT, BOOLEAN, TIMESTAMP(6); sin `AUTO_INCREMENT`, `ENGINE=`, `ENUM`, `JSON`, `TINYINT` explícito | Todo mapea a Postgres con traducción mínima |
| Migraciones | 15 Flyway en `backend/src/main/resources/db/migration/V1..V15` | Traducción mecánica, no reescritura conceptual |
| Tests | Ya corren en **H2** (`com.h2database:h2` scope test), no MySQL | La suite no depende de MySQL; se puede subir a Testcontainers-Postgres para fidelidad |
| Config DB | `spring.datasource.url/username/password` ya vía env (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) | Solo apuntar env a Hetzner; sin hardcode |
| Dialecto Hibernate | No fijado en `application.yml`; autodetección por driver | Cambiar driver basta |
| Clientes | Android/Desktop/iOS hablan con Spring vía HTTP, **no con la DB** | **Cero cambios de cliente**; contrato multiplataforma intacto |

Único idiom MySQL real: `ON UPDATE CURRENT_TIMESTAMP(6)` (17 usos en las migraciones). Inofensivo porque la app ya fija los timestamps.

---

## 3. Alcance

### Cambia (solo backend)
- `backend/pom.xml`: driver y plugin Flyway.
- `backend/src/main/resources/db/migration/V1..V15`: sintaxis Postgres.
- `backend/src/main/resources/application*.yml`: dialecto/validate si hace falta.
- Entidades: revisar `columnDefinition` para que `ddl-auto=validate` cuadre en Postgres.
- Tests: opcional H2 → Testcontainers-Postgres.
- Infra: Postgres en Hetzner + variables de entorno de despliegue.

### NO cambia
- Lógica de negocio, services, controllers, DTOs.
- Seguridad (Spring Security, JWT, ownership, rate limit).
- Chat (REST + WebSocket/STOMP).
- Contrato API `/api/v1/**`.
- Clientes Android, Desktop, iOS y `shared/`.
- Storage de imágenes (sigue en filesystem `./uploads`; mover a otro storage es decisión aparte).

---

## 4. Decisiones cerradas

Estado actualizado el 2026-07-09 tras la ejecucion del sprint:

1. **Hosting exacto:** Postgres autogestionado en VPS Hetzner, instalado como paquete del sistema (`18/main`) y accesible por WireGuard. Autogestionado implica que backups, PITR, parches y hardening son responsabilidad propia.
2. **Tipo de UUID:** resuelto el 2026-07-09: usar `varchar(36)` para los ids textuales. No usar `CHAR(36)` en PostgreSQL; Hibernate validate lo ve como `bpchar`/`Types#CHAR`.
3. **Datos:** se migro `FamilyDemo` desde MySQL local a PostgreSQL Hetzner por copia JDBC controlada, con recuentos antes/despues. MySQL local queda intacto como rollback operativo.
4. **Tests:** se eliminó H2 del backend y la suite corre contra PostgreSQL real por WireGuard mediante `DB_TEST_URL`, `DB_TEST_USERNAME` y `DB_TEST_PASSWORD`. No se usa Testcontainers porque no hay Docker disponible.
5. **Pooler:** no se usa pgbouncer/pooler. La app y Flyway conectan directamente a PostgreSQL por WireGuard.

Operacion posterior ya aplicada: backups logicos diarios, base backups semanales, WAL archiving local, restore logico probado y rotacion de `recetas_app`. Quedan vivos copia offsite cifrada, ensayo PITR completo y despliegue backend/API publica.

---

## 5. Plan paso a paso

### Paso 1 — Dependencias (`backend/pom.xml`)
- Quitar `com.mysql:mysql-connector-j`; añadir `org.postgresql:postgresql` (scope runtime).
- Quitar `org.flywaydb:flyway-mysql`; añadir `org.flywaydb:flyway-database-postgresql` (o dejar solo `flyway-core`, que soporta Postgres).
- Hibernate autodetecta `PostgreSQLDialect`. Si se quiere explícito: `spring.jpa.properties.hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect`.

### Paso 2 — Traducir las 15 migraciones Flyway
Cambios mecánicos por archivo:
- Eliminar `ON UPDATE CURRENT_TIMESTAMP(6)` (la app fija `updated_at`).
- `DEFAULT CURRENT_TIMESTAMP(6)` → `DEFAULT now()` (o dejar que la app lo fije y quitar el default).
- `TIMESTAMP(6)` → `timestamp(6)` (o `timestamptz` si se decide UTC explícito; la app ya trabaja en UTC, valorar `timestamptz`).
- `BOOLEAN NOT NULL DEFAULT FALSE` → válido en Postgres tal cual.
- `CHAR(36)` → `varchar(36)` según decisión 2 corregida.
- Índices, PK, FK, UNIQUE: sintaxis idéntica, sin cambios.
- Revisar V13 (`ensure_family_owner_members`) por si tiene UPDATE/subconsultas con sintaxis específica; traducir a Postgres si hace falta.
- **Estrategia recomendada:** como aún no hay Postgres productivo con estas migraciones aplicadas, se pueden **editar las V1..V15 in situ** (no crear V16 de conversión) porque el esquema Postgres se crea desde cero. Si ya existiera un Postgres con estas migraciones aplicadas, NO editar históricas y usar nuevas versiones.

### Paso 3 — Entidades y `ddl-auto=validate`
- Revisar cada `@Column(columnDefinition = "CHAR(36)")`: en Postgres debe quedar como `varchar(36)` para validar contra los ids Java `String`.
- Confirmar que `TIMESTAMP(6)` de entidades valida contra el tipo elegido en las migraciones.
- Ejecutar el arranque con `validate` para detectar desajustes.

### Paso 4 — Configuración
- `application.yml`: sin cambios de estructura; los env `DB_URL/USERNAME/PASSWORD` apuntan a Postgres Hetzner con SSL (`?sslmode=require`).
- `application-dev.yml`: defaults locales para un Postgres de desarrollo.
- Documentar en `CONTINUAR.md` §5 el nuevo arranque dev (Postgres local o contra Hetzner).

### Paso 5 — Tests
- Si se sube a Testcontainers-Postgres: añadir dependencia test, base class con `@Container PostgreSQLContainer`, `@DynamicPropertySource` para inyectar URL/credenciales.
- Correr `mvn test` (107+ tests) contra Postgres para validar que las migraciones y el mapeo funcionan en el motor real.

### Paso 6 — Migración de datos (si decisión 3 = migrar)
- Dataset pequeño (app familiar). Opciones:
  - `pgloader mysql://... postgresql://...` (automático, mapea tipos).
  - Export CSV por tabla en MySQL + `COPY` en Postgres.
- IDs son UUID en texto → mapeo casi 1:1. Verificar recuentos por tabla antes/después.
- Respetar orden de FKs o cargar con FK checks diferidas.

### Paso 7 — Infra Hetzner
- Provisionar Postgres (Docker `postgres:16` o paquete del SO) en el VPS.
- Configurar: usuario de aplicación con **mínimo privilegio** (no superuser), base `recetas_familiares`, SSL/TLS, firewall (solo el backend accede), backups automáticos (pg_dump/PITR con WAL), y actualizaciones.
- Nunca versionar credenciales; usar env/secretos del despliegue.

---

## 6. Validación esperada (ejecutar en la sesión del sprint)

- `mvn -f backend/pom.xml test` verde contra Postgres (Testcontainers o Postgres real).
- `mvn -f backend/pom.xml -DskipTests package` OK.
- Arranque real contra Postgres Hetzner: `GET /api/v1/health` = UP, Flyway aplica V1..V15 sin error.
- Smoke E2E de contrato (mismo método usado en el chat): registro/login, CRUD de una entidad, sync pull/push, y chat REST+WS. Reutilizar el enfoque de `docs` de la sesión de chat (curl + cliente WS Node).
- Verificar recuentos de datos migrados si aplica.
- `git diff --check` OK.
- Seguridad: como no cambian endpoints ni auth, `/security-review` no aplica; sí revisar con criterio VibeSec la config de conexión (SSL, credenciales por env, mínimo privilegio del usuario DB).

---

## 7. Riesgos y rollback

- **Riesgo ops (principal):** Postgres autogestionado en Hetzner = backups/PITR/hardening/parches son responsabilidad propia. Mitigación: automatizar backups y documentar restore antes de producción; o usar Postgres gestionado.
- **`ddl-auto=validate`:** desajustes de tipo entre entidad y columna Postgres. Mitigación: iterar con `validate` en local hasta 0 errores antes de tocar Hetzner.
- **varchar vs uuid:** se eligio `varchar(36)` para minimizar cambios en Java/clientes y evitar el padding/tipo `bpchar` de `CHAR(36)`. Migrar a `uuid` nativo queda como mejora futura con validacion multiplataforma.
- **Zona horaria:** decidir `timestamp` vs `timestamptz`. La app ya usa UTC (`hibernate.jdbc.time_zone: UTC`); `timestamptz` es más robusto.
- **Rollback:** el backend MySQL actual sigue funcionando; la migración se hace en rama. Si falla, no se despliega y se conserva MySQL. Reversible mientras no se apaguen los datos MySQL de origen.

---

## 8. Punto de arranque del sprint

- Partir de `main` limpio y alineado con `origin/main`.
- Crear rama `feat/migracion-postgresql`.
- Resolver las 5 decisiones de §4 con el usuario.
- Ejecutar §5 en orden; validar §6; documentar en `CONTINUAR.md`.
- Estado actual del repo al documentar este plan: chat Desktop (fase 2) ya integrado y publicado en `origin/main`; DB local MySQL con familia real `FamilyDemo` intacta.
