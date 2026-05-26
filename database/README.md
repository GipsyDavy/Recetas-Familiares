# Database

Modulo reservado para migraciones, semillas y documentacion de base de datos.

Reglas:

- MySQL como base principal.
- Migraciones versionadas con Flyway o Liquibase.
- Nada de `ddl-auto=update` en produccion.
- Entidades sincronizables con `createdAt`, `updatedAt`, `syncVersion` y `deleted`.
- Soft delete para datos sincronizados.
