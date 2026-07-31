# Correcciones operativas del VPS — Plan de implementacion

> **EJECUTADO Y CERRADO el 2026-07-31.** Las 9 tareas se completaron en linea con
> `superpowers:executing-plans`. Las casillas se dejan sin marcar a proposito: este fichero queda
> como el plan tal cual se aprobo, y el resultado real —incluidas las desviaciones respecto a el—
> esta en `CONTINUAR.md`, seccion "Sprint de correcciones operativas del VPS".
>
> Desviaciones que conviene conocer si alguien reutiliza este plan:
> - **Task 4/5, permisos:** el plan dice `chmod 0700`. Es **incorrecto** para
>   `recetas-postgres-logical-backup` y `recetas-postgres-basebackup`, que corren con
>   `User=postgres` y necesitan `0750`. Con `0700` fallan con `status=203/EXEC`.
> - **Task 6, ruta:** el `archive_command` no esta en `postgresql.conf` sino en
>   `conf.d/recetas-archive.conf`.
> - **Task 6, verificacion:** `pg_switch_wal()` no rota nada sin escrituras previas; hay que
>   generar WAL en una base desechable o la prueba sale en falso.
> - **Task 8:** el tunel no estaba roto. El peer obsoleto es `10.10.0.2`, no `10.10.0.3`.
> - Se añadio `.gitattributes`, no previsto: sin el, un clon nuevo sacaba los scripts con CRLF.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** cerrar los siete hallazgos de la auditoria operativa del 2026-07-31 dejando el VPS parcheado, la purga de WAL desacoplada del basebackup semanal, la integridad del repo offsite verificada de verdad y los scripts de backup versionados en el repositorio.

**Architecture:** todos los cambios son de infraestructura sobre el VPS Hetzner (`167.233.213.242`), no de codigo de aplicacion. Ningun cambio toca `backend/`, `android/`, `desktop/` ni `ios/`, asi que el CI/CD no se dispara por ellos. La secuencia es deliberada: primero una red de seguridad (backup fresco verificado), luego lo irreversible (reinicio), despues los cambios de configuracion reversibles, y al final el ensayo de restauracion que valida todo lo anterior.

**Tech Stack:** Ubuntu 26.04 (resolute), PostgreSQL 18, systemd (timers + oneshot services), restic 0.18.1 sobre SFTP a Hetzner Storage Box, WireGuard, ufw, Caddy.

## Global Constraints

- **Ninguna operacion destructiva sin backup fresco verificado en la misma sesion.** La Task 1 es requisito de todas las demas.
- **Fail-closed en purga de datos:** si no existe una copia base valida, no se borra ni un solo segmento WAL.
- **Secretos nunca impresos.** Los `.env` de `/etc/recetas-familiares/` se cargan en subshell con `set -a; source ...; set +a`. Nunca `cat`, nunca `echo` de sus valores. En documentacion, siempre `Redacted`.
- **Todo cambio en `/usr/local/sbin/` del VPS se copia a `infra/postgres/` del repo** en la misma tarea que lo introduce. La divergencia entre servidor y repo es exactamente lo que provoco que el item 18 fuera "no verificable".
- **Antes de sobrescribir cualquier script del VPS, guardar copia** `<script>.bak-20260731`.
- **Rutas fijas:** `BACKUP_ROOT=/var/backups/recetas-postgres` con subdirectorios `logical/`, `base/`, `wal/`. Scripts en `/usr/local/sbin/`. Units en `/etc/systemd/system/`.
- **Valores de retencion actuales que NO se tocan:** logico 14 dias, basebackup 21 dias, restic `keep-daily 14 --keep-weekly 5`.
- **Estado de partida verificado el 2026-07-31:** kernel corriendo `7.0.0-27-generic`, instalado `7.0.0-28-generic`; 12 paquetes actualizables incluido OpenSSL `3.5.5-1ubuntu3.2 -> 3.5.5-1ubuntu3.3` (`resolute-security`); BD `recetas_familiares` = 10 MB, 14 usuarios / 10 familias / 58 recetas; repo restic = 16 snapshots, 55.17 MiB reales.

---

### Task 1: Red de seguridad previa al reinicio

Antes de tocar nada se genera y se valida una copia fresca. No se reutiliza la de las 03:16 UTC: si el reinicio sale mal, la ventana de perdida debe ser de minutos, no de catorce horas.

**Files:**
- Crear en VPS: `/var/backups/recetas-postgres/logical/recetas_familiares_<TS>.dump` (via el script existente)
- Crear local: `docs/superpowers/plans/estado-previo-20260731.txt` (evidencia del estado de partida)

**Interfaces:**
- Produces: dump fresco validado con `pg_restore --list`, y un fichero de estado previo que las Tasks 2 y 8 usan para comparar antes/despues.

- [ ] **Step 1: Capturar el estado de partida en un fichero**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF' > docs/superpowers/plans/estado-previo-20260731.txt 2>&1
echo "kernel=$(uname -r)"
echo "uptime=$(uptime -p)"
sudo -u postgres psql -d recetas_familiares -tAc "select 'users='||(select count(*) from users)||' families='||(select count(*) from families)||' recipes='||(select count(*) from recipes)"
echo "wal_segments=$(ls -1 /var/backups/recetas-postgres/wal | wc -l)"
echo "disco=$(df -h / | tail -1)"
systemctl is-active recetas-backend postgresql caddy
EOF
```

- [ ] **Step 2: Lanzar un backup logico fresco**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'systemctl start recetas-postgres-logical-backup && systemctl show recetas-postgres-logical-backup -p Result -p ExecMainStatus'
```

Esperado: `Result=success` y `ExecMainStatus=0`.

- [ ] **Step 3: Verificar que el dump fresco es restaurable**

No basta con que exista. Se comprueba el TOC y que las tablas clave llevan datos.

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
D=$(ls -t /var/backups/recetas-postgres/logical/*.dump | head -1)
echo "dump: $D"
sudo -u postgres pg_restore --list "$D" > /tmp/toc.txt && echo "TOC OK"
echo "tablas con datos: $(grep -c 'TABLE DATA' /tmp/toc.txt)"
grep -cE "TABLE DATA .* (users|families|recipes)" /tmp/toc.txt
rm -f /tmp/toc.txt
EOF
```

Esperado: `TOC OK`, 26 tablas con datos, y 3 en el segundo `grep`. Si sale menos, **DETENERSE**: no se reinicia nada.

- [ ] **Step 4: Empujar ese dump al repositorio offsite**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'systemctl start recetas-postgres-offsite-backup && systemctl show recetas-postgres-offsite-backup -p Result -p ExecMainStatus'
```

Esperado: `Result=success`, `ExecMainStatus=0`. Ahora existe copia local **y** offsite del estado inmediatamente anterior al reinicio.

- [ ] **Step 5: Commit de la evidencia**

```bash
git add docs/superpowers/plans/estado-previo-20260731.txt
git commit -m "docs: registra el estado del VPS previo a las correcciones operativas"
```

---

### Task 2: Actualizar paquetes y reiniciar el VPS

Aplica el kernel `7.0.0-28`, `libc6`, `linux-base` y el parche de seguridad de OpenSSL. Corta produccion entre uno y dos minutos.

Ya verificado el 2026-07-31, no hace falta repetirlo: `postgresql.service` esta `enabled` con symlink en `multi-user.target.wants/`, `/etc/postgresql/18/main/start.conf` dice `auto`, y `caddy`, `wg-quick@wg0` y `recetas-backend` tienen su symlink de arranque. El backend declara `After`/`Wants` sobre `postgresql@18-main.service` y `network-online.target`, asi que no arranca antes que la base de datos.

**Files:**
- Modificar en VPS: paquetes del sistema (`apt-get upgrade`)
- Modificar en VPS: `/etc/apt/apt.conf.d/50unattended-upgrades` (reinicio automatico)

**Interfaces:**
- Consumes: dump fresco verificado de la Task 1.
- Produces: VPS en kernel `7.0.0-28-generic` con todos los servicios arriba.

- [ ] **Step 1: Actualizar paquetes sin reiniciar todavia**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'DEBIAN_FRONTEND=noninteractive apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y 2>&1 | tail -20'
```

- [ ] **Step 2: Confirmar que ya no quedan paquetes pendientes**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'apt list --upgradable 2>/dev/null | tail -n +2 | wc -l'
```

Esperado: `0`. Si queda alguno, leer cual y por que antes de seguir.

- [ ] **Step 3: Reiniciar**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'systemctl reboot' || true
```

El `|| true` es intencionado: la conexion SSH muere con el reinicio y `ssh` devuelve error. No es un fallo.

- [ ] **Step 4: Esperar a que vuelva y verificar el kernel**

Reintentar hasta que responda, sin bucle de espera ciego:

```bash
until ssh -o BatchMode=yes -o ConnectTimeout=5 root@167.233.213.242 'uname -r' 2>/dev/null; do :; done
```

Esperado: `7.0.0-28-generic`. Si sigue diciendo `-27`, el reinicio no aplico el kernel nuevo: **DETENERSE** e investigar antes de continuar.

- [ ] **Step 5: Verificar que TODO volvio solo**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
echo "kernel=$(uname -r)"
systemctl is-active postgresql recetas-backend caddy wg-quick@wg0
echo "--- timers ---"
systemctl list-timers 'recetas-*' --no-pager | head -5
echo "--- reinicio pendiente? ---"
[ -f /var/run/reboot-required ] && echo "SIGUE PENDIENTE" || echo "limpio"
echo "--- datos intactos ---"
sudo -u postgres psql -d recetas_familiares -tAc "select 'users='||(select count(*) from users)||' families='||(select count(*) from families)||' recipes='||(select count(*) from recipes)"
EOF
```

Esperado: los cuatro servicios `active`, los tres timers programados, `limpio`, y **exactamente** `users=14 families=10 recipes=58` (contrastar contra `estado-previo-20260731.txt`).

- [ ] **Step 6: Verificar produccion desde fuera**

Desde Windows, porque `curl` de git-bash esta roto por el MITM TLS de Avast:

```powershell
Invoke-RestMethod -Uri "https://recetas.167.233.213.242.sslip.io/api/v1/health" -TimeoutSec 20
```

Esperado: `status = UP`. Esto prueba la cadena completa: Caddy, TLS, backend y PostgreSQL.

- [ ] **Step 7: Evitar que la deriva se repita**

La causa de acumular 22 dias de parches es que `unattended-upgrades` instala pero nunca reinicia. Se fija una ventana a las 05:45 UTC, despues de que terminen los tres backups (el ultimo, el offsite, arranca a las 05:15 y tarda 8 segundos).

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
cp /etc/apt/apt.conf.d/50unattended-upgrades /etc/apt/apt.conf.d/50unattended-upgrades.bak-20260731
cat >> /etc/apt/apt.conf.d/50unattended-upgrades <<'CONF'

// Añadido 2026-07-31: reinicio automatico tras backups (offsite termina ~05:16 UTC).
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-WithUsers "true";
Unattended-Upgrade::Automatic-Reboot-Time "05:45";
CONF
unattended-upgrades --dry-run --debug 2>&1 | tail -5
EOF
```

Esperado: el `--dry-run` termina sin error de sintaxis.

---

### Task 3: Versionar los scripts de backup en el repositorio

Causa raiz del item 18: los tres scripts viven solo en `/usr/local/sbin/` del VPS. Ninguna auditoria de codigo podia verlos. El repo ya tiene el patron establecido en `infra/backend/`, asi que se replica.

**Files:**
- Crear: `infra/postgres/recetas-postgres-logical-backup`
- Crear: `infra/postgres/recetas-postgres-basebackup`
- Crear: `infra/postgres/recetas-postgres-offsite-backup`
- Crear: `infra/postgres/README.md`

**Interfaces:**
- Produces: copia fiel en el repo de los tres scripts, que las Tasks 4, 5 y 6 modifican **primero en el repo** y luego despliegan al VPS.

- [ ] **Step 1: Traer los tres scripts tal cual estan hoy**

```bash
mkdir -p infra/postgres
for s in recetas-postgres-logical-backup recetas-postgres-basebackup recetas-postgres-offsite-backup; do
  ssh -o BatchMode=yes root@167.233.213.242 "cat /usr/local/sbin/$s" > "infra/postgres/$s"
done
```

- [ ] **Step 2: Confirmar que no arrastran secretos**

Los scripts hacen `source` de los `.env`, no llevan credenciales dentro. Verificarlo, no suponerlo:

```bash
grep -nE "PASSWORD|PASSPHRASE|RESTIC_PASSWORD|[A-Za-z0-9+/]{32,}" infra/postgres/* || echo "sin secretos embebidos"
```

Esperado: `sin secretos embebidos`. Si aparece algo, **no commitear** y avisar.

- [ ] **Step 3: Escribir el README**

```bash
cat > infra/postgres/README.md <<'EOF'
# Backups de PostgreSQL (VPS Hetzner)

Copia versionada de los scripts que viven en `/usr/local/sbin/` del VPS. El
servidor es la fuente de ejecucion; este directorio es la fuente de revision.
Cualquier cambio se edita aqui primero y se despliega despues.

| Script | Unit | Cadencia | Retencion |
|---|---|---|---|
| `recetas-postgres-logical-backup` | `recetas-postgres-logical-backup.timer` | diaria ~03:20 UTC | 14 dias |
| `recetas-postgres-basebackup` | `recetas-postgres-basebackup.timer` | semanal domingo | 21 dias |
| `recetas-postgres-offsite-backup` | `recetas-postgres-offsite-backup.timer` | diaria ~05:20 UTC | keep-daily 14 + keep-weekly 5 |

Los secretos NO estan aqui: viven en `/etc/recetas-familiares/*.env` del VPS,
modo 0600 root:root. Los scripts los cargan con `source`.

## Desplegar un cambio

```bash
scp infra/postgres/<script> root@167.233.213.242:/usr/local/sbin/<script>
ssh root@167.233.213.242 'chmod 0700 /usr/local/sbin/<script>'
```

## Verificar

```bash
ssh root@167.233.213.242 'systemctl start <unit> && systemctl show <unit> -p Result -p ExecMainStatus'
```
EOF
```

- [ ] **Step 4: Commit**

```bash
git add infra/postgres/
git commit -m "chore(infra): versiona los scripts de backup de PostgreSQL del VPS"
```

---

### Task 4: Desacoplar la purga de WAL del basebackup semanal

Hoy la purga es la linea 18 de `recetas-postgres-basebackup`: `find "$WAL_DIR" -type f -mtime +35 -delete`. Si el basebackup falla varias semanas seguidas, el WAL deja de purgarse en silencio.

Se mueve al script diario y se cambia la regla por una **semanticamente correcta y fail-closed**: borrar solo los segmentos anteriores a la copia base mas antigua que se conserva. Si no hay ninguna copia base, no se borra nada. Eso es exactamente lo que el PITR necesita, y no depende de una ventana de dias elegida a ojo.

**Files:**
- Modificar: `infra/postgres/recetas-postgres-basebackup` (quitar la linea 18)
- Modificar: `infra/postgres/recetas-postgres-logical-backup` (añadir la purga)
- Desplegar ambos al VPS

**Interfaces:**
- Consumes: `infra/postgres/` de la Task 3.
- Produces: purga de WAL diaria, acotada por la copia base mas antigua.

- [ ] **Step 1: Probar la regla nueva en seco, sin borrar nada**

Este es el paso que demuestra que la regla es correcta antes de aplicarla. `-print` en vez de `-delete`.

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
BASE_DIR=/var/backups/recetas-postgres/base
WAL_DIR=/var/backups/recetas-postgres/wal
OLDEST_BASE=$(ls -1d "$BASE_DIR"/base_* 2>/dev/null | sort | head -1)
echo "copia base mas antigua: ${OLDEST_BASE:-NINGUNA}"
if [ -n "$OLDEST_BASE" ]; then
  echo "WAL que se borraria: $(find "$WAL_DIR" -type f ! -newer "$OLDEST_BASE" | wc -l) de $(ls -1 "$WAL_DIR" | wc -l)"
else
  echo "sin copia base: no se borraria nada (fail-closed)"
fi
EOF
```

Esperado hoy: la copia base mas antigua es `base_20260709T212950Z` y el numero de WAL a borrar es **muy bajo o cero**, porque el archivado empezo ese mismo dia. Si dijera que borraria cientos, **DETENERSE**: la regla estaria mal.

- [ ] **Step 2: Quitar la purga del script de basebackup**

En `infra/postgres/recetas-postgres-basebackup`, borrar la linea:

```bash
find "$WAL_DIR" -type f -mtime +"$WAL_RETENTION_DAYS" -delete
```

Y tambien la definicion ya inservible de la linea 8:

```bash
WAL_RETENTION_DAYS="${WAL_RETENTION_DAYS:-35}"
```

- [ ] **Step 3: Añadir la purga al script diario**

En `infra/postgres/recetas-postgres-logical-backup`, despues de la linea de retencion de dumps (`find "$LOGICAL_DIR" ... -delete`), añadir:

```bash
# Purga de WAL: se conservan solo los segmentos necesarios para restaurar desde
# la copia base mas antigua que sigue en disco. Fail-closed: sin copia base, no
# se borra nada. Vivia en el script de basebackup (semanal); si aquel fallaba,
# el WAL dejaba de purgarse en silencio.
BASE_DIR="$BACKUP_ROOT/base"
WAL_DIR="$BACKUP_ROOT/wal"
OLDEST_BASE="$(ls -1d "$BASE_DIR"/base_* 2>/dev/null | sort | head -1)"
if [ -n "$OLDEST_BASE" ]; then
  find "$WAL_DIR" -type f ! -newer "$OLDEST_BASE" -delete
fi
```

Comprobar que `BACKUP_ROOT` ya esta definido arriba en ese script; si no lo estuviera, añadir `BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/recetas-postgres}"`.

- [ ] **Step 4: Validar la sintaxis antes de desplegar**

```bash
bash -n infra/postgres/recetas-postgres-logical-backup && bash -n infra/postgres/recetas-postgres-basebackup && echo "sintaxis OK"
```

- [ ] **Step 5: Desplegar con copia de seguridad previa**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'cp /usr/local/sbin/recetas-postgres-logical-backup /usr/local/sbin/recetas-postgres-logical-backup.bak-20260731; cp /usr/local/sbin/recetas-postgres-basebackup /usr/local/sbin/recetas-postgres-basebackup.bak-20260731'
scp infra/postgres/recetas-postgres-logical-backup root@167.233.213.242:/usr/local/sbin/recetas-postgres-logical-backup
scp infra/postgres/recetas-postgres-basebackup root@167.233.213.242:/usr/local/sbin/recetas-postgres-basebackup
ssh -o BatchMode=yes root@167.233.213.242 'chmod 0700 /usr/local/sbin/recetas-postgres-logical-backup /usr/local/sbin/recetas-postgres-basebackup'
```

- [ ] **Step 6: Ejecutar y verificar que no destruyo nada**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
ANTES=$(ls -1 /var/backups/recetas-postgres/wal | wc -l)
systemctl start recetas-postgres-logical-backup
systemctl show recetas-postgres-logical-backup -p Result -p ExecMainStatus
DESPUES=$(ls -1 /var/backups/recetas-postgres/wal | wc -l)
echo "WAL antes=$ANTES despues=$DESPUES"
echo "dumps: $(ls -1 /var/backups/recetas-postgres/logical/*.dump | wc -l)"
EOF
```

Esperado: `Result=success`, `ExecMainStatus=0`, y el conteo de WAL practicamente igual (solo caerian los anteriores al 09/07, que no existen). Una caida brusca significa que la regla borro de mas: **restaurar el `.bak-20260731` de inmediato**.

- [ ] **Step 7: Commit**

```bash
git add infra/postgres/
git commit -m "fix(infra): desacopla la purga de WAL del basebackup semanal"
```

---

### Task 5: Verificar de verdad la integridad del repositorio offsite

`restic check` a secas valida estructura e indices, no los datos cifrados. Se añade `--read-data-subset=1/7`: cada dia verifica una septima parte de los blobs, asi que en una semana se cubre el repo entero. Con 55 MiB reales son unos 8 MiB diarios, coste despreciable.

**Files:**
- Modificar: `infra/postgres/recetas-postgres-offsite-backup`

**Interfaces:**
- Consumes: `infra/postgres/` de la Task 3.
- Produces: verificacion de blobs rotatoria, cobertura total semanal.

- [ ] **Step 1: Cambiar la ultima linea del script**

En `infra/postgres/recetas-postgres-offsite-backup`, sustituir:

```bash
restic check --quiet
```

por:

```bash
# Verifica estructura e indices siempre, y ademas una septima parte de los blobs
# cifrados cada dia: en una semana se comprueba el repositorio entero. Sin esto,
# `check` a secas nunca llega a leer los datos.
restic check --read-data-subset="$(( ($(date -u +%j) % 7) + 1 ))/7" --quiet
```

- [ ] **Step 2: Validar sintaxis**

```bash
bash -n infra/postgres/recetas-postgres-offsite-backup && echo "sintaxis OK"
```

- [ ] **Step 3: Desplegar con copia previa**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'cp /usr/local/sbin/recetas-postgres-offsite-backup /usr/local/sbin/recetas-postgres-offsite-backup.bak-20260731'
scp infra/postgres/recetas-postgres-offsite-backup root@167.233.213.242:/usr/local/sbin/recetas-postgres-offsite-backup
ssh -o BatchMode=yes root@167.233.213.242 'chmod 0700 /usr/local/sbin/recetas-postgres-offsite-backup'
```

- [ ] **Step 4: Ejecutar y confirmar que la verificacion de datos corre**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'systemctl start recetas-postgres-offsite-backup; systemctl show recetas-postgres-offsite-backup -p Result -p ExecMainStatus; journalctl -u recetas-postgres-offsite-backup -n 15 --no-pager | grep -viE "post-quantum|store now|openssh.com|upgraded"'
```

Esperado: `Result=success`, `ExecMainStatus=0`, sin lineas de error de restic.

- [ ] **Step 5: Commit**

```bash
git add infra/postgres/
git commit -m "fix(infra): restic verifica los blobs cifrados, no solo los indices"
```

---

### Task 6: Hacer duradero el archivado de WAL

`archive_command` usa `cp` plano. La documentacion de PostgreSQL advierte que sin `fsync` el ultimo segmento puede perderse ante un corte abrupto: `cp` devuelve exito con los datos aun en cache de pagina. Se añade `sync` sobre el fichero destino. Es un parametro `sighup`, asi que basta recargar; no hace falta reiniciar PostgreSQL.

**Files:**
- Modificar en VPS: `/etc/postgresql/18/main/postgresql.conf` (o el `conf.d` que corresponda)

- [ ] **Step 1: Localizar donde esta definido hoy**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'grep -rn "archive_command" /etc/postgresql/18/main/ | grep -v "^.*#"'
```

- [ ] **Step 2: Cambiarlo, guardando copia**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
F=/etc/postgresql/18/main/postgresql.conf
cp "$F" "$F.bak-20260731"
sed -i "s|^archive_command = .*|archive_command = 'test ! -f /var/backups/recetas-postgres/wal/%f \&\& cp %p /var/backups/recetas-postgres/wal/%f \&\& sync /var/backups/recetas-postgres/wal/%f'|" "$F"
grep -n "^archive_command" "$F"
EOF
```

- [ ] **Step 3: Recargar sin reiniciar**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'sudo -u postgres psql -tAc "select pg_reload_conf()" && sudo -u postgres psql -tAc "show archive_command"'
```

Esperado: `t`, y el `archive_command` nuevo con `sync` al final.

- [ ] **Step 4: Forzar un cambio de segmento y comprobar que se archiva**

Este es el paso que prueba que no se rompio el archivado. Si falla, PostgreSQL empezaria a acumular WAL en `pg_wal` y acabaria parando.

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
ANTES=$(ls -1 /var/backups/recetas-postgres/wal | wc -l)
sudo -u postgres psql -tAc "select pg_switch_wal()"
sleep 5
DESPUES=$(ls -1 /var/backups/recetas-postgres/wal | wc -l)
echo "segmentos antes=$ANTES despues=$DESPUES"
sudo -u postgres psql -tAc "select archived_count, last_archived_wal, failed_count, last_failed_wal from pg_stat_archiver"
EOF
```

Esperado: `despues > antes`, y en `pg_stat_archiver` el `failed_count` **no** debe haber subido. Si `last_failed_wal` es reciente, **restaurar el `.bak-20260731` y recargar**.

---

### Task 7: Repetir el ensayo de restauracion, esta vez desde el repositorio offsite

El ensayo del 11/07 restauro desde las copias locales. La pata nunca probada, y anotada como riesgo residual desde entonces, es restaurar **partiendo solo del repositorio offsite**. Si el VPS se perdiera entero, esa es la unica via.

Se restaura un dump desde restic a un directorio temporal, se carga en una base de datos desechable y se comparan los recuentos con produccion. Produccion no se toca en ningun momento.

**Files:**
- Temporal en VPS: `/var/tmp/restore-drill-20260731/` (se borra al final)
- Temporal en VPS: base de datos `restore_drill_20260731` (se borra al final)

**Interfaces:**
- Consumes: repositorio restic verificado en la Task 5.
- Produces: evidencia de que la copia offsite es restaurable de punta a punta.

- [ ] **Step 1: Restaurar el ultimo snapshot desde el Storage Box**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
set -a; source /etc/recetas-familiares/offsite-backup.env; set +a
mkdir -p /var/tmp/restore-drill-20260731
restic restore latest --target /var/tmp/restore-drill-20260731 \
  --include /var/backups/recetas-postgres/logical 2>&1 | grep -viE "post-quantum|store now|openssh.com|upgraded"
ls -lh /var/tmp/restore-drill-20260731/var/backups/recetas-postgres/logical/ | tail -3
EOF
```

Esperado: restic reporta la restauracion y aparecen los dumps.

- [ ] **Step 2: Cargar el dump restaurado en una base de datos desechable**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
D=$(ls -t /var/tmp/restore-drill-20260731/var/backups/recetas-postgres/logical/*.dump | head -1)
echo "restaurando: $D"
sudo -u postgres psql -tAc "drop database if exists restore_drill_20260731"
sudo -u postgres psql -tAc "create database restore_drill_20260731"
sudo -u postgres pg_restore --no-owner --no-acl -d restore_drill_20260731 "$D" 2>&1 | tail -5
echo "carga terminada"
EOF
```

- [ ] **Step 3: Comparar recuentos contra produccion**

Esta es la prueba real: no que el fichero se lea, sino que los datos esten completos.

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
Q="select 'users='||(select count(*) from users)||' families='||(select count(*) from families)||' recipes='||(select count(*) from recipes)||' notes='||(select count(*) from family_notes)"
echo "PRODUCCION : $(sudo -u postgres psql -d recetas_familiares -tAc "$Q")"
echo "RESTAURADA : $(sudo -u postgres psql -d restore_drill_20260731 -tAc "$Q")"
EOF
```

Esperado: **las dos lineas identicas**. Si difieren, el backup offsite no es fiable: parar todo y avisar.

- [ ] **Step 4: Limpiar la base desechable y los temporales**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'sudo -u postgres psql -tAc "drop database if exists restore_drill_20260731"; rm -rf /var/tmp/restore-drill-20260731; echo limpiado'
```

- [ ] **Step 5: Confirmar que produccion quedo intacta**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
echo "bases drill restantes: $(sudo -u postgres psql -tAc "select count(*) from pg_database where datname like 'restore_drill%'")"
echo "recetas en produccion: $(sudo -u postgres psql -d recetas_familiares -tAc 'select count(*) from recipes')"
EOF
```

Esperado: `0` bases drill y `58` recetas.

---

### Task 8: Restaurar el tunel WireGuard del PC

El peer `10.10.0.2` lleva 11 dias sin handshake y el servicio Windows `WireGuardTunnel$RecetasHetzner` ya no existe: solo aparece "ProtonVPN WireGuard", parado. WireGuard **si** sigue instalado (`C:\Program Files\WireGuard\wireguard.exe`) y la configuracion esta en `herztner/wireguard_config_herztner.txt`. Sin este tunel no hay acceso a PostgreSQL desde el PC ni backend dev local contra `recetas_familiares_test`.

No afecta a produccion. El lado servidor esta bien: `wg-quick@wg0` activo, el peer sigue declarado y ufw permite 51820/udp.

**Files:**
- Crear local: `%USERPROFILE%\AppData\Local\Temp\claude\...\RecetasHetzner.conf` (temporal, se borra tras importar)
- Modificar: servicios de Windows (alta del tunel)

**Interfaces:**
- Produces: tunel activo con handshake reciente contra `10.10.0.1`, y acceso a PostgreSQL desde el PC.

- [ ] **Step 1: Confirmar el estado de partida**

```powershell
Get-Service | Where-Object { $_.Name -like '*WireGuard*' } | Select-Object Name,Status,StartType
Test-Path "C:\Program Files\WireGuard\wireguard.exe"
```

Esperado: solo "ProtonVPN WireGuard" (Stopped) y `True`.

- [ ] **Step 2: Preparar el fichero de configuracion**

`herztner/wireguard_config_herztner.txt` contiene la clave privada del PC. **No imprimir su contenido en la conversacion.** Se copia al scratchpad con extension `.conf`, que es lo que exige WireGuard:

```powershell
Copy-Item "herztner\wireguard_config_herztner.txt" "$env:TEMP\RecetasHetzner.conf"
(Get-Content "$env:TEMP\RecetasHetzner.conf" | Measure-Object -Line).Lines
```

Esperado: un numero de lineas mayor que cero. Solo se comprueba que no esta vacio, nada mas.

- [ ] **Step 3: Dar de alta el tunel como servicio**

Requiere privilegios de administrador. Si Windows lo rechaza, el usuario puede hacerlo desde la GUI de WireGuard con "Importar tunel desde archivo".

```powershell
& "C:\Program Files\WireGuard\wireguard.exe" /installtunnelservice "$env:TEMP\RecetasHetzner.conf"
```

- [ ] **Step 4: Verificar que el servicio existe y arranca**

```powershell
Start-Sleep -Seconds 3
Get-Service | Where-Object { $_.Name -like '*RecetasHetzner*' } | Select-Object Name,Status,StartType
```

Esperado: el servicio presente y `Running`.

- [ ] **Step 5: Probar el tunel de verdad, no solo el servicio**

Que el servicio este arriba no prueba que el tunel funcione. Lo que lo prueba es el handshake desde el lado servidor:

```bash
ssh -o BatchMode=yes root@167.233.213.242 'wg show wg0 | grep -A3 "10.10.0.2"'
```

Esperado: `latest handshake` de hace segundos, no de hace 11 dias.

- [ ] **Step 6: Probar el acceso real a PostgreSQL**

```powershell
Test-NetConnection -ComputerName 10.10.0.1 -Port 5432 -InformationLevel Quiet
```

Esperado: `True`. Esto cierra el hallazgo: el PC vuelve a poder correr backend dev contra `recetas_familiares_test`.

- [ ] **Step 7: Borrar la copia temporal de la configuracion**

Contiene una clave privada. No debe quedar en el scratchpad.

```powershell
Remove-Item "$env:TEMP\RecetasHetzner.conf" -Force
Test-Path "$env:TEMP\RecetasHetzner.conf"
```

Esperado: `False`.

---

### Task 9: Documentacion, seguridad y cierre

**Files:**
- Modificar: `CONTINUAR.md`
- Modificar: `docs/postgres-operacion-runbook.md`
- Modificar: memoria del proyecto (`project_state.md`)

- [ ] **Step 1: Documentar el peer WireGuard 10.10.0.3**

El usuario confirmo el 2026-07-31 que es un dispositivo suyo. Se registra en el runbook para que no vuelva a aparecer como desconocido en la proxima auditoria: clave publica `i6Y0xNuiCI7p5hCoBJrbXMq6oJxJKkxjTU5iuOgfSCg=`, IP `10.10.0.3/32`, mismo endpoint publico que el PC.

- [ ] **Step 2: Actualizar el runbook con los cambios de esta sesion**

En `docs/postgres-operacion-runbook.md`: purga de WAL movida al script diario y acotada por la copia base mas antigua; `restic check --read-data-subset=1/7`; `archive_command` con `sync`; scripts versionados en `infra/postgres/`; reinicio automatico de `unattended-upgrades` a las 05:45 UTC.

- [ ] **Step 3: Escribir la seccion de cierre en `CONTINUAR.md`**

Con lo de siempre: agentes usados, hallazgos cerrados uno a uno, comandos ejecutados con su salida real, y riesgo residual explicito.

- [ ] **Step 4: Escaneo de seguridad obligatorio**

```powershell
pwsh -NoProfile -File scripts/security/run-security-scan.ps1 -Mode sprint
```

Esperado: exit 0. Los dos falsos positivos permanentes de `ServerUrlConfigTest.kt:42` y `ServerConfigTest.java:75` son conocidos y estan documentados. **Atencion:** esta tarea añade `infra/postgres/` al repo; si TruffleHog marcara algo ahi, no es falso positivo — hay que mirarlo.

- [ ] **Step 5: Commit y push**

```bash
git add CONTINUAR.md docs/postgres-operacion-runbook.md
git commit -m "docs: cierra el sprint de correcciones operativas del VPS"
git push origin main
```

Ningun fichero toca `backend/**` ni `.github/workflows/**`, asi que no se dispara el CI/CD ni un redespliegue.

- [ ] **Step 6: Verificacion final del conjunto**

```bash
ssh -o BatchMode=yes root@167.233.213.242 'bash -s' <<'EOF'
echo "kernel=$(uname -r)"
systemctl is-active postgresql recetas-backend caddy wg-quick@wg0
systemctl list-timers 'recetas-*' --no-pager | head -5
sudo -u postgres psql -d recetas_familiares -tAc "select 'recipes='||count(*) from recipes"
df -h / | tail -1
EOF
```

Y desde Windows:

```powershell
Invoke-RestMethod -Uri "https://recetas.167.233.213.242.sslip.io/api/v1/health" -TimeoutSec 20
```

---

## Riesgos y reversion

| Cambio | Reversible | Como |
|---|---|---|
| `apt upgrade` | Si | `apt-get install <pkg>=<version-anterior>` |
| Reinicio | No, pero repetible | Kernel anterior `7.0.0-27` sigue instalado; seleccionable desde GRUB |
| Reinicio automatico de unattended-upgrades | Si | `50unattended-upgrades.bak-20260731` |
| Purga de WAL | Si | `.bak-20260731` de los dos scripts |
| `restic --read-data-subset` | Si | `.bak-20260731` del script offsite |
| `archive_command` | Si | `postgresql.conf.bak-20260731` + `pg_reload_conf()` |
| Ensayo de restauracion | N/A | Solo lectura sobre produccion; usa base desechable |
| Tunel WireGuard del PC | Si | `wireguard.exe /uninstalltunnelservice RecetasHetzner` |

**Riesgo mayor:** que PostgreSQL no arranque tras el reinicio. Mitigado con la verificacion previa de symlinks y `start.conf=auto` ya hecha el 2026-07-31, y con el backup fresco local **y** offsite de la Task 1.

**Riesgo silencioso:** que la regla nueva de purga borre WAL de mas. Mitigado con la ejecucion en seco de la Task 4 Step 1, que debe correrse y leerse **antes** de desplegar nada.
