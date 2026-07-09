# PostgreSQL Operation Runbook

Runbook operativo para el PostgreSQL autogestionado de Recetas Familiares en Hetzner.

## Estado

- Host: VPS Hetzner accesible por `ssh root@167.233.213.242`.
- PostgreSQL: cluster `18/main`.
- Bases principales: `recetas_familiares` y `recetas_familiares_test`.
- Rol de aplicacion: `recetas_app`, sin privilegios de superusuario ni administracion.
- Red DB: Postgres escucha en `10.10.0.1:5432`, `127.0.0.1:5432` y `::1:5432`; no debe escuchar en la IP publica.
- Conexion normal de la app: `jdbc:postgresql://10.10.0.1:5432/recetas_familiares` via WireGuard.
- Secretos locales: `herztner/recetas_app.env` queda fuera de Git.
- Backend desplegado en el mismo VPS como `recetas-backend.service`, detras de Caddy/TLS. Ver `docs/backend-vps-deploy-runbook.md`.

## Backup Configurado

Rutas en el VPS:

```bash
/var/backups/recetas-postgres/logical
/var/backups/recetas-postgres/base
/var/backups/recetas-postgres/wal
```

Servicios systemd:

```bash
recetas-postgres-logical-backup.service
recetas-postgres-logical-backup.timer
recetas-postgres-basebackup.service
recetas-postgres-basebackup.timer
```

Politica actual:

- Backup logico diario a las 03:15 UTC con `pg_dump --format=custom`, retencion 14 dias.
- Base backup fisico semanal los domingos a las 04:15 UTC con `pg_basebackup`, retencion 21 dias.
- Archivado WAL activo con `archive_mode=on`, `archive_timeout=15min`, retencion local 35 dias.
- Permisos de backups restringidos a `postgres:postgres`.

Comprobaciones:

```bash
systemctl list-timers --all --no-pager | grep recetas-postgres
systemctl status recetas-postgres-logical-backup.service --no-pager
systemctl status recetas-postgres-basebackup.service --no-pager
sudo -u postgres psql -At -c "SHOW archive_mode; SHOW archive_command; SHOW archive_timeout;"
sudo -u postgres psql -At -c "SELECT archived_count, failed_count, last_archived_wal, last_failed_wal FROM pg_stat_archiver;"
find /var/backups/recetas-postgres -maxdepth 3 -type f -printf '%M %u %g %s %TY-%Tm-%Td %TH:%TM %p\n' | sort
```

Ejecucion manual:

```bash
systemctl start recetas-postgres-logical-backup.service
systemctl start recetas-postgres-basebackup.service
sudo -u postgres psql -At -c "SELECT pg_switch_wal();"
```

## Restore Logico De Prueba

Usar una base aislada. No restaurar encima de `recetas_familiares` salvo operacion de desastre planificada.

```bash
RESTORE_DB=recetas_familiares_restore_check
LATEST="$(find /var/backups/recetas-postgres/logical -maxdepth 1 -type f -name 'recetas_familiares_*.dump' | sort | tail -n 1)"

sudo -u postgres dropdb --if-exists "$RESTORE_DB"
sudo -u postgres createdb -O recetas_app "$RESTORE_DB"
sudo -u postgres pg_restore --dbname="$RESTORE_DB" --no-owner --no-acl "$LATEST"

sudo -u postgres psql -At -d "$RESTORE_DB" -c "SELECT 'flyway='||COUNT(*) FROM flyway_schema_history WHERE success UNION ALL SELECT 'users='||COUNT(*) FROM users UNION ALL SELECT 'families='||COUNT(*) FROM families UNION ALL SELECT 'family_members='||COUNT(*) FROM family_members UNION ALL SELECT 'refresh_tokens='||COUNT(*) FROM refresh_tokens UNION ALL SELECT 'chat_messages='||COUNT(*) FROM chat_messages UNION ALL SELECT 'chat_message_clears='||COUNT(*) FROM chat_message_clears ORDER BY 1;"

sudo -u postgres dropdb "$RESTORE_DB"
```

## PITR

Hay base backups y WAL locales suficientes para una recuperacion a punto en el tiempo dentro de la ventana de retencion, pero el restore PITR debe ensayarse en un directorio/servidor aislado antes de usarlo en produccion.

No ejecutar un PITR sobre el cluster activo sin ventana de mantenimiento y copia previa. La secuencia segura es:

1. Parar un cluster de restore aislado.
2. Restaurar el ultimo `base.tar.gz` del directorio `base_*`.
3. Configurar `restore_command` apuntando a `/var/backups/recetas-postgres/wal/%f`.
4. Definir `recovery_target_time` si se busca un punto concreto.
5. Arrancar el cluster aislado y validar recuentos/datos.

## Rotacion De Credenciales

Generar una clave aleatoria nueva, aplicar:

```sql
ALTER ROLE recetas_app WITH PASSWORD '<nueva-clave>';
```

Actualizar solo secretos fuera de Git, por ejemplo `herztner/recetas_app.env` o variables del despliegue. Validar:

```bash
PGPASSWORD='<nueva-clave>' psql -h 10.10.0.1 -U recetas_app -d recetas_familiares -At -c "SELECT current_user, current_database();"
PGPASSWORD='<nueva-clave>' psql -h 10.10.0.1 -U recetas_app -d recetas_familiares_test -At -c "SELECT current_user, current_database();"
```

## Riesgos Residuales

- Los backups estan en el mismo VPS/disco. Falta copia offsite cifrada para cubrir perdida total del servidor.
- El PITR esta configurado a nivel de base backup + WAL, pero falta ensayo completo en cluster aislado.
- El backend ya esta desplegado en VPS/API publica HTTPS temporal; falta dominio propio estable y estrategia de rollback/CI-CD.
- Flyway 11.7.2 avisa que PostgreSQL 18.4 es mas nuevo que su soporte probado actual.
