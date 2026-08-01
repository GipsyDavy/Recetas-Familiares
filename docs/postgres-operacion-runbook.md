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

- Backup logico diario, `OnCalendar=*-*-* 03:15:00` + `RandomizedDelaySec=15m`, con
  `pg_dump --format=custom`, retencion 14 dias.
- Base backup fisico semanal, `OnCalendar=Sun *-*-* 04:15:00` + `RandomizedDelaySec=30m`, con
  `pg_basebackup`, retencion 21 dias.
- Los tres timers llevan `Persistent=true`: recuperan la ejecucion perdida tras un reinicio y
  escriben un stamp en `/var/lib/systemd/timers/`, que es la **unica** evidencia del ultimo disparo
  que sobrevive a un reboot (`ExecMainExitTimestamp` y `LastTriggerUSec` son estado de runtime y se
  pierden).
- Archivado WAL activo con `archive_mode=on`, `archive_timeout=15min`. **La purga de WAL vive
  en el script diario** y conserva solo los segmentos posteriores a la copia base mas antigua en
  disco (ver abajo).
- Permisos de backups restringidos a `postgres:postgres`.

**Los scripts estan versionados en `infra/postgres/` desde el 2026-07-31.** Antes vivian solo en
`/usr/local/sbin/` del VPS, invisibles para cualquier auditoria del repositorio. Editar alli
primero y desplegar despues; el README de ese directorio tiene el procedimiento y, sobre todo,
**los permisos correctos por script**: `0750 root:postgres` para los dos que corren con
`User=postgres`, `0700 root:root` solo para el offsite. Ponerles `0700` a los primeros los rompe
con `status=203/EXEC` y el backup deja de ejecutarse.

### Purga de WAL (cambiada el 2026-07-31)

Antes: `find "$WAL_DIR" -mtime +35 -delete` **dentro del script de basebackup**, que solo corre los
domingos. Si el basebackup fallaba varias semanas seguidas, el WAL dejaba de purgarse en silencio.

Ahora, en `recetas-postgres-logical-backup` (diario):

```bash
OLDEST_BASE="$(ls -1d "$BASE_DIR"/base_* 2>/dev/null | sort | head -1)"
if [ -n "$OLDEST_BASE" ]; then
  find "$WAL_DIR" -type f ! -newer "$OLDEST_BASE" -delete
fi
```

Conserva exactamente lo que el PITR necesita: los segmentos posteriores a la copia base mas antigua
que sigue en disco. Es fail-closed — sin ninguna copia base, no borra nada.

Antes de tocar esta regla, probarla siempre en seco cambiando `-delete` por `-print`.

### Durabilidad y contrato del archivado (reescrito el 2026-08-01)

`archive_command` es ahora un script versionado:

```
archive_command = '/usr/local/sbin/recetas-postgres-archive-wal %p %f'
```

Sustituye a `test ! -f DEST && cp %p DEST && sync DEST`, que tenia **dos defectos**, ambos
verificados el 2026-08-01. Detalle completo en `infra/postgres/README.md`.

**1. Incumplia el contrato de rearchivado.** §25.3.1 dice literalmente:

> "When an archive command or library encounters a pre-existing file, it should return a zero
> status [...] if the WAL file has identical contents to the pre-existing archive and the
> pre-existing archive is fully persisted to storage. If a pre-existing file contains different
> contents [...] the archive command or library _must_ return a nonzero status."

El comando anterior devolvia 1 **siempre** que el destino existiera. Tras una caida en la ventana
entre la copia y el registro durable del exito, PostgreSQL reintenta el mismo segmento: el archiver
quedaba atascado de forma permanente, `pg_wal` crecia y, si el disco se llenaba, PANIC.

**Detalle que conviene recordar:** el ejemplo canonico de la propia documentacion
(`test ! -f ... && cp ...`) era exactamente el que teniamos, y es **incompleto** respecto a lo que
el mismo apartado exige unas lineas mas abajo. Que un comando aparezca como ejemplo oficial no
significa que cumpla el contrato completo.

**2. El `sync` no hacia lo que su propio comentario afirmaba.** Ubuntu 26.04 sustituyo GNU coreutils
por **uutils** (`rust-coreutils 0.8.0`; el paquete `coreutils` es solo un meta-paquete). Su
`sync FICHERO` **no hace `fsync(2)`**: abre el fichero y llama a `sync()` global. Comprobado con
`strace`:

```
openat(AT_FDCWD, "...", O_RDONLY|O_NONBLOCK) = 3
sync()                                       = 0
```

Ademas devolvio codigo 0 sincronizando un fichero que el usuario ni siquiera podia abrir, asi que
su codigo de salida no sirve para afirmar durabilidad. Por eso el script esta en Python 3, que
expone `os.fsync` real sobre el fichero **y sobre el directorio** — el `fsync` de un fichero no
persiste la entrada de su directorio — y propaga los errores.

**Regla general que deja este hallazgo:** en este servidor, **no dar por hecho el comportamiento de
GNU coreutils**. `cp` y `cmp` siguen siendo GNU, pero `sync`, `ln`, `cat`, `mktemp` y `stat` son
uutils. Verificar con `<comando> --version` antes de apoyarse en semantica fina.

La configuracion vive en `/etc/postgresql/18/main/conf.d/recetas-archive.conf`, **no** en
`postgresql.conf`. Es `sighup`: aplicar con `select pg_reload_conf()`, sin reiniciar.

#### Verificar el archivado

**Trampa 1:** `pg_switch_wal()` no rota nada si no hubo escrituras desde el ultimo cambio de
segmento, asi que la prueba sale en falso. Usar `pg_create_restore_point()`, que escribe un marcador
en el WAL **sin DDL** y sin tocar el esquema (crear y borrar una tabla de sondeo puede eliminar una
homonima preexistente):

```bash
sudo -u postgres psql -tAc "select pg_create_restore_point('archive-check')"
SEG=$(sudo -u postgres psql -tAc "select pg_walfile_name(pg_current_wal_insert_lsn())")
sudo -u postgres psql -tAc "select pg_switch_wal()"
sleep 10
sudo -u postgres psql -tAc "select last_archived_wal, archived_count, failed_count from pg_stat_archiver"
```

**Trampa 2:** `pg_reload_conf()` solo confirma que se envio el SIGHUP; una configuracion invalida se
ignora y solo deja rastro en el log. Para afirmar que se aplico de verdad:

```bash
sudo -u postgres psql -X -At -c "SELECT applied, sourcefile FROM pg_file_settings WHERE name='archive_command' ORDER BY seqno DESC LIMIT 1;"
sudo -u postgres psql -X -At -c "SELECT pg_conf_load_time();"
```

**Trampa 3:** `failed_count` sin cambios no prueba ausencia de fallos — PostgreSQL no contabiliza
ahi los fallos por señal ni ciertos errores de shell. La comprobacion concluyente exige a la vez:
destino regular presente, `<segmento>.done` en `archive_status`, `.ready` ausente, contador
incrementado y **entrada en el syslog del script**:

```bash
journalctl -t recetas-archive-wal --no-pager -n 20
```

Esa ultima es la unica forma de demostrar que el archiver ejecuta **este** script y no otro comando.

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

## PITR partiendo SOLO del repositorio offsite (ensayado 2026-08-01)

Cierra el riesgo residual abierto desde el 11/07. Lo ensayado el 31/07 fue restauracion **logica**
desde offsite; esto es el PITR completo: copia base + reproduccion de WAL hasta un instante
concreto, **sin tocar en ningun momento `/var/backups/recetas-postgres`**.

Resultado del ensayo: `recovery stopping before commit of transaction 13685, time
2026-07-31 23:45:15.833301+00`. El cluster recuperado contenia el marcador anterior al objetivo y
**no** el posterior. Datos de la aplicacion identicos a produccion: `users=14 families=10
recipes=58`, 26 tablas. Produccion intacta (`failed_count=0` antes y despues).

Procedimiento (detalle completo en
`docs/superpowers/plans/2026-08-01-pitr-offsite-y-peer-vpn.md`):

1. **Preflight bloqueante.** Cero tablespaces de usuario (`pg_tablespace`) y cero suscripciones
   logicas (`pg_subscription`): las primeras harian que el ensayo escribiera sobre datos reales via
   `tablespace_map`, las segundas abririan conexiones salientes tras la promocion. Leer del primario
   `max_connections`, `max_prepared_transactions`, `max_locks_per_transaction`, `max_wal_senders` y
   `max_worker_processes`: la recovery aborta si el cluster de ensayo tiene alguno por debajo.
2. **Marcadores en una base desechable** (`recetas_pitr_drill`). El WAL es del cluster entero, asi
   que una base aparte genera el material necesario sin tocar el esquema de Flyway.
3. **El objetivo de recuperacion se captura en una transaccion POSTERIOR al commit del marcador A**,
   nunca como `ts_a + N segundos`. `recovery_target_time` se compara con el timestamp de **commit**
   del WAL, no con el valor de la columna; con un margen fijo, un commit lento dejaria el objetivo
   por delante del commit real y el marcador no apareceria.
4. **Barrera de WAL.** Forzar una escritura, capturar `pg_walfile_name(pg_current_wal_insert_lsn())`,
   `pg_switch_wal()` y esperar a que `pg_stat_archiver.last_archived_wal` alcance esa barrera. La
   existencia del fichero no basta: `cp` lo crea antes de que termine el `sync`.
5. **Fijar el snapshot restic por ID**, nunca `latest` (el timer diario puede disparar en medio), y
   exigir que la barrera este dentro de ese snapshot: esa es la prueba de procedencia.
6. **Restaurar solo `base/` y `wal/`** y rechazar cualquier symlink en lo restaurado — restic los
   preserva y `test -f` los sigue, asi que un enlace al archivo local daria un verde falso.
7. **Vaciar `postgresql.auto.conf`** del PGDATA extraido: viaja dentro de `base.tar.gz` y **tiene
   mas precedencia que `postgresql.conf`**. Borrar tambien `*.signal` heredadas y `postmaster.pid`.
   Exigir `tablespace_map` vacio y `pg_tblspc` vacio.
8. **`pg_wal` sin segmentos residuales.** Cuando `restore_command` falla, PostgreSQL busca el
   segmento en `pg_wal/`: un residuo podria suplir WAL que el offsite no tuviera. Ojo: `base.tar.gz`
   trae siempre `archive_status/` y `summaries/` vacios, asi que la comprobacion correcta es
   "cero ficheros `[0-9A-F]{24}`", no "directorio vacio".
9. **Afirmar la configuracion efectiva con `postgres -C` ANTES de arrancar**, por igualdad literal y
   no por comodines: un `restore_command` que ademas contuviera un fallback al WAL local pasaria un
   filtro laxo. Se comprueban 19 GUCs, incluidos `archive_mode=off`, `listen_addresses=''`,
   `primary_conninfo=''`, `max_logical_replication_workers=0` y `shared_preload_libraries=''`.
10. **Arrancar en el puerto 5433 con socket propio**, validar `pg_is_in_recovery()=f`, comprobar los
    marcadores y confirmar que no hay listener TCP en 5433.

Limitacion asumida: el aislamiento de red del cluster de ensayo es **convencion de GUCs**, no
garantia del sistema operativo. No se uso `PrivateNetwork` ni namespace propio.

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
/etc/systemd/system/recetas-postgres-offsite-backup.timer   # diario 05:15 UTC +10m (tras logico 03:15+15m y basebackup dominical 04:15+30m)
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

## Verificacion operativa 2026-07-31

Auditoria completa por SSH y sprint de correcciones. Detalle en `CONTINUAR.md`.

- Los tres backups verificados vivos, con `Result=success` y exit 0, y **restaurabilidad probada**:
  `pg_restore --list` sobre el dump del dia devuelve 184 entradas de TOC y 26 tablas con datos.
- **Restauracion desde el repositorio offsite ensayada por primera vez** (cerraba un riesgo
  residual abierto desde el 11/07): `restic restore latest` -> `pg_restore` en base desechable ->
  recuentos comparados con produccion. Identicos:
  `users=14 families=10 recipes=58 notes=2 stock=0 dm=1`, 26 tablas en ambas.
- Repositorio restic: 16 snapshots, **55 MiB reales** pese a 5.5 GiB logicos por snapshot
  (deduplicacion + compresion 5.53x sobre los segmentos WAL). Storage Box de 1 TB al 0%.
- `restic check` pasa a verificar tambien los blobs cifrados con `--read-data-subset=1/7`,
  rotando por dia del año: cobertura completa del repositorio cada semana. Antes solo validaba
  estructura e indices, asi que un blob corrupto habria pasado desapercibido.
- VPS actualizado y reiniciado: kernel `7.0.0-27` -> `7.0.0-28`, `libc6` y parche de seguridad de
  OpenSSL. Corte de produccion ~12 s. Todos los servicios volvieron solos.
- `unattended-upgrades` reinicia ahora automaticamente a las **05:45 UTC**, despues de los tres
  backups. Antes instalaba parches pero nunca reiniciaba, y por eso se acumularon 22 dias de
  kernel sin aplicar.

**Falsa alarma descartada, anotada para no repetirla:** el WAL local parece crecer sin control
(5.6 GB para una base de datos de 10 MB, sin `crontab` de root y sin scripts en `/usr/local/bin`).
No es cierto. La purga existe, vive en `/usr/local/sbin` y estaba embebida en el script de
basebackup. El estado estacionario es de ~6-7 GB sobre 38 GB de disco.

## Riesgos Residuales

- ~~Un PITR partiendo solo del offsite sigue sin ensayarse.~~ **CERRADO el 2026-08-01**: PITR
  completo desde el repositorio offsite validado con precision de transaccion. Ver la seccion
  `PITR partiendo SOLO del repositorio offsite`.
- El aislamiento del cluster de ensayo se apoya en GUCs (`listen_addresses=''`,
  `max_logical_replication_workers=0`, `shared_preload_libraries=''`) y no en un namespace de red
  del sistema operativo. Suficiente mientras el preflight siga confirmando cero suscripciones
  logicas; si algun dia existieran, hay que aislar por SO antes de repetir el ensayo.
- ~~El `archive_command` podria atascarse ante un rearchivado tras caida.~~ **CERRADO el
  2026-08-01.** La afirmacion de Codex se verifico contra la documentacion de PostgreSQL 18 §25.3.1
  y era correcta. Sustituido por `recetas-postgres-archive-wal`, validado con 11 casos y con el
  contrato comprobado en produccion: rearchivado identico devuelve 0, contenido distinto devuelve 2
  sin tocar el fichero. Al hacerlo aparecio un segundo defecto que nadie habia previsto: el `sync`
  del comando anterior no hacia `fsync(2)` porque este servidor usa uutils, no GNU coreutils.
- **`ENOSPC` y `EIO` reales no se han ensayado** sobre el filesystem de produccion. La bateria cubre
  escritura truncada con `RLIMIT_FSIZE` aplicado solo al proceso de prueba; los fallos de
  dispositivo quedan sin ensayar. El script es fail-closed ante ellos por diseño, pero no verificado.
- Si el proceso muere con `SIGKILL` entre `mkstemp` y `os.link`, queda un temporal `.<segmento>.*`
  en el directorio de WAL. No es corrupcion (antes del `link` no hay nada publicado; despues,
  temporal y destino son hard links al mismo inodo, asi que borrar el temporal no elimina el
  segmento), pero `restic` puede copiarlo en la ventana hasta la purga diaria.
- La copia offsite depende de una unica Storage Box; si Hetzner pierde VPS y Storage Box a la vez (misma cuenta/proveedor), no hay tercera copia.
- La passphrase restic tiene una unica copia fuera del VPS (carpeta local del usuario); si se pierden ambas, el repositorio offsite es irrecuperable.
- El backend ya esta desplegado en VPS/API publica HTTPS temporal; falta dominio propio estable y estrategia de rollback/CI-CD.
- Flyway 11.7.2 avisa que PostgreSQL 18.4 es mas nuevo que su soporte probado actual.
