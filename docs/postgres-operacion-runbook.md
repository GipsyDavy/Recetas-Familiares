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

## PITR (ensayado 2026-07-11 en cluster aislado)

Recuperacion a punto en el tiempo validada de extremo a extremo: base backup + WAL archivados permiten recuperar a un instante concreto con precision de transaccion (probado con dos marcadores y `recovery_target_time` entre ambos: el cluster recuperado contenia solo el primero).

No ejecutar un PITR sobre el cluster activo sin ventana de mantenimiento y copia previa. Procedimiento probado (cluster aislado en el propio VPS, solo socket local):

```bash
PITR=/var/tmp/pitr-test
mkdir -p "$PITR/data"
tar xzf /var/backups/recetas-postgres/base/base_<FECHA>/base.tar.gz -C "$PITR/data"
mkdir -p "$PITR/data/pg_wal"
tar xzf /var/backups/recetas-postgres/base/base_<FECHA>/pg_wal.tar.gz -C "$PITR/data/pg_wal"
cat > "$PITR/data/postgresql.conf" <<EOF
listen_addresses = ''
unix_socket_directories = '$PITR'
port = 5433
max_connections = 100
shared_buffers = 128MB
archive_mode = off
restore_command = 'cp /var/backups/recetas-postgres/wal/%f %p'
recovery_target_time = '<YYYY-MM-DD HH:MM:SS.ffffff+00>'
recovery_target_action = 'promote'
EOF
echo "local all postgres peer" > "$PITR/data/pg_hba.conf"
touch "$PITR/data/pg_ident.conf" "$PITR/data/recovery.signal"
chown -R postgres:postgres "$PITR" && chmod 700 "$PITR/data"
sudo -u postgres /usr/lib/postgresql/18/bin/pg_ctl -D "$PITR/data" -l "$PITR/pitr.log" -w -t 180 start
# validar
sudo -u postgres psql -h "$PITR" -p 5433 -At -c "SELECT pg_is_in_recovery();"   # f tras promote
sudo -u postgres psql -h "$PITR" -p 5433 -d recetas_familiares -At -c "SELECT COUNT(*) FROM users;"
# limpieza
sudo -u postgres /usr/lib/postgresql/18/bin/pg_ctl -D "$PITR/data" -w stop
rm -rf "$PITR"
```

Trampas comprobadas en el ensayo:

- El base backup NO incluye `postgresql.conf`/`pg_hba.conf`/`pg_ident.conf` (layout Debian: viven en `/etc/postgresql/18/main`); hay que crear una config minima en el data dir restaurado.
- `max_connections` del cluster de restore debe ser >= al del primario (100); con menos, la recovery aborta con `insufficient parameter settings`.
- `archive_mode = off` obligatorio en el cluster de ensayo para no contaminar el archivo WAL de produccion.
- `listen_addresses = ''` + solo socket local: el cluster de ensayo no abre TCP.
- Es normal ver `cp: cannot stat .../00000002.history` al inicio: PostgreSQL sondea timelines siguientes.
- Para recuperar TODO el WAL disponible (no un punto concreto), omitir `recovery_target_time`.
- El cluster promovido queda en timeline 2; es desechable, no reutilizarlo como primario.

## Backups Offsite Cifrados (implementado 2026-07-11)

Estado: operativo. Copia diaria cifrada de `/var/backups/recetas-postgres` (logical + base + wal) a una Hetzner Storage Box mediante `restic` sobre SFTP.

Arquitectura:

- Destino: Hetzner Storage Box (`STORAGEBOX_HOST` en `/etc/recetas-familiares/storagebox.env`), acceso SFTP puerto 22 con clave SSH dedicada `/root/.ssh/storagebox_ed25519` (auth por password ya no se usa; el puerto 23/SSH interactivo esta desactivado en la Storage Box).
- Repositorio: `restic` (`sftp:storagebox:recetas-postgres-restic`), cifrado extremo a extremo antes de salir del VPS. Alias `storagebox` definido en `/root/.ssh/config`.
- Secretos (solo en VPS, `0600 root:root`): `/etc/recetas-familiares/storagebox.env` y `/etc/recetas-familiares/offsite-backup.env` (`RESTIC_REPOSITORY`, `RESTIC_PASSWORD`, `RESTIC_CACHE_DIR=/var/cache/restic`).
- Copia de emergencia de la passphrase restic: fuera del VPS, en carpeta local no versionada del usuario (`herztner/restic-offsite-passphrase.env`). Sin passphrase el repositorio offsite es irrecuperable.

Unidades:

```bash
/usr/local/sbin/recetas-postgres-offsite-backup        # 0700 root
/etc/systemd/system/recetas-postgres-offsite-backup.service
/etc/systemd/system/recetas-postgres-offsite-backup.timer   # diario 05:15 UTC (tras logico 03:15 y basebackup dominical 04:31)
```

El script hace `restic backup` (tag `scheduled`), `restic forget --keep-daily 14 --keep-weekly 5 --prune` y `restic check`. Falla cerrado: si el destino no responde, los backups locales siguen intactos.

Comprobaciones:

```bash
systemctl list-timers --no-pager | grep offsite
systemctl status recetas-postgres-offsite-backup.service --no-pager
set -a; . /etc/recetas-familiares/offsite-backup.env; set +a
restic snapshots
restic check
```

Restore offsite (probado 2026-07-11):

```bash
set -a; . /etc/recetas-familiares/offsite-backup.env; set +a
RESTORE_DIR=$(mktemp -d /root/offsite-restore-check-XXXX)
restic restore latest --target "$RESTORE_DIR"
# aparecen logical/, base/ y wal/ bajo $RESTORE_DIR/var/backups/recetas-postgres
LATEST=$(find "$RESTORE_DIR/var/backups/recetas-postgres/logical" -name '*.dump' | sort | tail -1)
cp "$LATEST" /tmp/offsite_check.dump && chown postgres:postgres /tmp/offsite_check.dump
sudo -u postgres createdb -O recetas_app recetas_familiares_offsite_check
sudo -u postgres pg_restore --dbname=recetas_familiares_offsite_check --no-owner --no-acl /tmp/offsite_check.dump
# comparar recuentos con prod (flyway_schema_history, users, families, family_members, chat_messages)
sudo -u postgres dropdb recetas_familiares_offsite_check
rm -rf "$RESTORE_DIR" /tmp/offsite_check.dump
```

Nota: `pg_restore` no puede leer dumps dentro de `/root` (permisos); copiar antes a `/tmp` con owner `postgres`.

Rotacion de credenciales offsite:

- Clave SSH: generar nueva con `ssh-keygen -t ed25519`, subir `authorized_keys` actualizado por SFTP (puerto 22) y borrar la anterior.
- Passphrase restic: no rotar a la ligera; requiere `restic key add`/`remove` y actualizar `/etc/recetas-familiares/offsite-backup.env` y la copia local del usuario.

Reglas de seguridad:

- No imprimir secretos en terminal compartida, logs ni documentacion.
- No subir secretos a Git (`herztner/` sigue fuera del repo).
- No abrir `5432/tcp` a internet para resolver backups.
- Fallar cerrado si el destino offsite no esta disponible; mantener backups locales funcionando.

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

- El ensayo PITR se hizo restaurando desde los backups locales del VPS; un PITR usando exclusivamente el repositorio offsite (restic restore + mismo procedimiento) no se ha ensayado por separado, aunque el restore offsite ya esta validado.
- La copia offsite depende de una unica Storage Box; si Hetzner pierde VPS y Storage Box a la vez (misma cuenta/proveedor), no hay tercera copia.
- La passphrase restic tiene una unica copia fuera del VPS (carpeta local del usuario); si se pierden ambas, el repositorio offsite es irrecuperable.
- El backend ya esta desplegado en VPS/API publica HTTPS temporal; falta dominio propio estable y estrategia de rollback/CI-CD.
- Flyway 11.7.2 avisa que PostgreSQL 18.4 es mas nuevo que su soporte probado actual.
