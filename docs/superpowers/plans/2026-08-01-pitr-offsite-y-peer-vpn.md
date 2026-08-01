# PITR desde offsite + retirada del peer VPN obsoleto — Plan de implementacion (v3)

> **Para trabajadores agenticos:** SUB-SKILL REQUERIDA: usar `superpowers:executing-plans` para
> ejecutar este plan tarea a tarea. Los pasos usan sintaxis de checkbox (`- [ ]`).
> **No usar subagentes**: todas las tareas comparten una unica sesion SSH contra produccion y el
> estado no es serializable entre agentes.

**Objetivo:** demostrar que el repositorio offsite cifrado basta por si solo para recuperar la base
de datos a un instante concreto (copia base + reproduccion de WAL), y retirar el peer WireGuard
`10.10.0.2`, que lleva 11+ dias sin handshake.

**Arquitectura:** se escriben dos marcadores temporales en una base de datos desechable del cluster
de produccion (para generar WAL con dos instantes distinguibles sin tocar `recetas_familiares`), se
fuerza el archivado y una copia offsite, y despues se levanta un cluster aislado en el puerto 5433
alimentado **exclusivamente** con ficheros restaurados desde restic. El `recovery_target_time` se
fija entre el **commit** del primer marcador y el del segundo: el exito consiste en que el cluster
recuperado contenga el primero y **no** el segundo.

**Stack:** PostgreSQL 18 (layout Debian), restic sobre SFTP a Hetzner Storage Box, systemd,
WireGuard. Sin codigo de aplicacion.

## Revision externa incorporada

Dos rondas de revision por Codex (solo lectura, sin conexion al VPS), ambas antes de ejecutar nada.

- **Ronda 1 sobre la v1:** 4 bloqueantes, 9 importantes, 4 menores. Incorporados 4/4, 8/9 y 4/4.
- **Ronda 2 sobre la v2:** 5 bloqueantes **nuevos, introducidos por la reescritura**, 9 importantes
  y 3 menores. Incorporados en su totalidad en la v3.
- **Ronda 3 sobre la v3**, acotada a las tres superficies donde un fallo silencioso hace daño real
  (script de limpieza, flujo WireGuard, aserciones de aislamiento): 8 bloqueantes, 3 importantes,
  1 menor. Incorporados 6 de 8, los 3 importantes y el menor. Los dos no incorporados estan
  justificados al final, en `Fuera de alcance`.

Tres de los cinco bloqueantes de la ronda 2 eran la misma clase de error: `grep`, `grep -c` y
`diff` devuelven codigo distinto de cero en situaciones normales, y bajo `set -o pipefail` eso
abortaba el script **justo cuando la comprobacion pasaba**. La ronda 3 encontro una cuarta
ocurrencia de la misma clase — `if ss -ltn | grep -q ':5433'`, que puede devolver el 141 de `ss`
por SIGPIPE y entrar en la rama "sin listener": un verde falso en la comprobacion de aislamiento.
La seccion `Convenciones de shell` existe para cerrar la clase entera; las reglas 8 y 9 se añaden
tras esa ronda.

Gemini no estaba disponible (sin cuota); la revision de coherencia documental la asume Claude Code
en la Tarea 9.

## Convenciones de shell (obligatorias en todo el plan)

Estas reglas no son estilo. Cada una corresponde a un fallo real detectado en revision.

1. **Todo bloque remoto** se envia con `ssh root@167.233.213.242 'bash -s' <<'REMOTE'` y empieza por
   `set -euo pipefail` y `. /root/pitr-drill-lib.sh`.
2. **Prohibido** `VAR=$(algo | grep ... | wc -l)` seguido de `test`. `grep` devuelve 1 sin
   coincidencias y `pipefail` propaga ese 1 a la asignacion, que con `set -e` mata el script.
   Usar siempre la forma explicita:
   ```bash
   if grep -q PATRON fichero; then echo "ABORTAR: ..."; exit 1; fi
   ```
3. **Prohibido** `ls ... | head -n`. Si el productor recibe SIGPIPE, `pipefail` devuelve 141.
   Usar consumidores que lean toda la entrada: `sed -n '1p'`, o `find ... | sort | sed -n '1p'`.
4. **`diff` devuelve 0, 1 o >1.** Nunca `diff ... || true`: eso oculta el 2 (error real) igual que
   el 1 (hay diferencias). Capturar el codigo y distinguirlo.
5. **`systemctl show` siempre devuelve 0.** Leer la propiedad con `--value` y afirmarla con `test`.
6. **Ningun paso termina en un comando informativo** (`tail`, `uptime`, `ls`) si antes hay algo que
   pueda fallar: el codigo de salida del bloque seria el del informativo.
7. **Todo estado compartido entre tareas** se persiste con `save_state` en `/root/pitr-drill.env`.
   Ninguna tarea puede depender de un valor "anotado a mano" de una tarea anterior: bajo `set -u`,
   una variable no definida aborta el bloque.
8. **Prohibido `if cmd | grep -q ...`.** Bajo `pipefail`, si `grep -q` sale antes de consumir toda
   la entrada, el productor recibe SIGPIPE y la tuberia devuelve 141: la condicion entra en la rama
   equivocada. Capturar la salida en una variable y comprobarla despues:
   ```bash
   OUT=$(cmd || true)
   case "$OUT" in *patron*) echo "ABORTAR: ..."; exit 1 ;; esac
   ```
9. **Toda ruta que vaya a un `rm -rf` o a un `pg_ctl`** se valida por forma canonica
   (`realpath`), no por prefijo textual: `/var/tmp/pitr-drill-X/../../lib/postgresql` supera
   cualquier `case` de prefijo.

## Restricciones globales

- **Produccion no se modifica** salvo: (a) crear y borrar la base desechable `recetas_pitr_drill`,
  (b) escrituras en esa base y un `pg_switch_wal()`, (c) la retirada del peer VPN de la Tarea 8.
- El cluster de ensayo **debe** quedar aislado: `archive_mode = off`, `listen_addresses = ''`,
  socket propio, puerto 5433. Verificado con `postgres -C` **antes** de arrancar, por igualdad
  literal, y con `SHOW` + `ss` despues. `postgresql.auto.conf` viene dentro del base backup y tiene
  mayor precedencia que `postgresql.conf`: se vacia.
- El `restore_command` del ensayo debe ser **exactamente** `cp $OFFSITE_ROOT/wal/%f %p`. Ademas, si
  `restore_command` no encuentra un segmento, PostgreSQL **cae de vuelta a `pg_wal/`**: por eso el
  `pg_wal` inicial solo puede proceder del `pg_wal.tar.gz` del propio base backup offsite, en un
  directorio recien creado, vacio y que no sea un symlink.
- **Fail-closed por disco, en bytes y por comando**, evaluado **dos veces**: antes de restaurar y
  antes de extraer.
- Ningun paso se da por bueno por lectura visual: cada verificacion termina en un `test` que falla
  si el valor no es el esperado.
- Nunca imprimir el contenido de `/etc/recetas-familiares/*.env`, la passphrase de restic,
  `PrivateKey`, `PresharedKey` ni los **valores** de `postgresql.auto.conf`.
- Si el plan aborta en cualquier punto entre la Tarea 2 y la Tarea 6, **ejecutar el bloque de
  limpieza** (Tarea 1) antes de reportar.

---

### Task 1: Preflight — biblioteca de estado, limpieza segura y comprobaciones bloqueantes

**Ficheros:** crea `/root/pitr-drill.env`, `/root/pitr-drill-lib.sh` y `/root/pitr-drill-cleanup.sh`
en el VPS. Los tres se borran en la Tarea 7.

**Interfaces:**
- Produce en `/root/pitr-drill.env`: `OFFSITE_DIR`, `DRILL_DIR`, `BASE_DIR_NAME`, `PGBIN`, y los
  cinco parametros `PRIMARY_MAX_*`. Las tareas 2-8 leen de ahi; ninguna depende de valores
  transcritos a mano.

- [ ] **Paso 1: Confirmar que el tunel WireGuard del PC esta activo**

En PowerShell local:

```powershell
Get-NetIPAddress -InterfaceAlias RecetasHetzner -AddressFamily IPv4 | Select-Object IPAddress
Test-NetConnection 10.10.0.1 -Port 5432 -InformationLevel Quiet
```

Esperado: `10.10.0.3` y `True`. Si el tunel esta desactivado, activarlo desde la GUI de WireGuard.
(Recordatorio del 31/07: desactivar un tunel en Windows **borra** su servicio; que no aparezca el
servicio no significa que el tunel no exista.)

- [ ] **Paso 2: Acceso SSH, servicios activos y herramientas disponibles**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
hostname
systemctl is-active postgresql@18-main
systemctl is-active recetas-backend.service
command -v restic >/dev/null || { echo "ABORTAR: falta restic"; exit 1; }
command -v findmnt >/dev/null || { echo "ABORTAR: falta findmnt"; exit 1; }
if command -v python3 >/dev/null; then echo "parser=python3"; elif command -v jq >/dev/null; then echo "parser=jq"; else echo "ABORTAR: no hay python3 ni jq"; exit 1; fi
test -x /usr/lib/postgresql/18/bin/pg_ctl || { echo "ABORTAR: no esta pg_ctl de la 18"; exit 1; }
echo "preflight de herramientas OK"
REMOTE
```

`is-active` devuelve codigo distinto de cero si el servicio no esta activo y `set -e` corta ahi.

**Si el parser es `jq`**, sustituir en las tareas 3 y 4 los tres bloques `python3 -c` por su
equivalente. Los tres se usan para: (a) obtener el ID del snapshot mas reciente, (b) afirmar sus
metadatos, (c) leer `total_size` de `restic stats`. Equivalencias:

```bash
# (a) id del ultimo snapshot         python3: json.load(sys.stdin)[-1]["id"]
jq -er '.[-1].id'
# (b) asercion de metadatos (falla con exit 1 si algo no cuadra)
jq -er --arg h "$(hostname)" --arg tsb "$TS_B" '
  .[0] | select(.hostname == $h)
       | select(.tags | index("scheduled"))
       | select(.paths | index("/var/backups/recetas-postgres"))
       | select(.time > $tsb) | .id'
# (c) total_size                     python3: json.load(sys.stdin)["total_size"]
jq -er '.total_size'
```

- [ ] **Paso 3: Descartar tablespaces de usuario y suscripciones logicas (bloqueante si existen)**

Con `pg_basebackup -Ft`, cada tablespace adicional viaja en un tar aparte y `tablespace_map`
conserva sus rutas **de produccion**. Un cluster de ensayo que las recree escribiria sobre datos
reales.

Las suscripciones logicas son el otro camino de salida: `listen_addresses=''` impide conexiones
**entrantes**, pero una suscripcion habilitada arranca workers que abren conexiones **salientes** en
cuanto el cluster se promueve. El ensayo se conectaria a un publisher real desde una copia.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
N=$(sudo -u postgres psql -At -c "SELECT COUNT(*) FROM pg_tablespace WHERE spcname NOT IN ('pg_default','pg_global');")
echo "tablespaces de usuario: $N"
test "$N" -eq 0 || { echo "ABORTAR: existen tablespaces de usuario; este procedimiento no los contempla"; exit 1; }
S=$(sudo -u postgres psql -At -c "SELECT COUNT(*) FROM pg_subscription;")
echo "suscripciones logicas: $S"
test "$S" -eq 0 || { echo "ABORTAR: hay suscripciones logicas; el cluster de ensayo podria abrir conexiones salientes"; exit 1; }
SPL=$(sudo -u postgres psql -At -c "SELECT setting FROM pg_settings WHERE name='shared_preload_libraries';")
echo "shared_preload_libraries en produccion: '${SPL}'"
REMOTE
```

Si cualquiera de los dos recuentos es distinto de 0, **detener el plan**: remapear tablespaces o
aislar suscripciones son procedimientos que este plan no cubre.

- [ ] **Paso 4: Crear temporales, biblioteca de estado y fichero de estado**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
umask 077
OFFSITE_DIR=$(mktemp -d /var/tmp/pitr-offsite-XXXXXXXX)
DRILL_DIR=$(mktemp -d /var/tmp/pitr-drill-XXXXXXXX)
for d in "$OFFSITE_DIR" "$DRILL_DIR"; do
  test "$(realpath "$d")" = "$d" || { echo "ABORTAR: $d no es una ruta real (symlink?)"; exit 1; }
  test -z "$(ls -A "$d")"        || { echo "ABORTAR: $d no esta vacio"; exit 1; }
  chmod 700 "$d"
done

: > /root/pitr-drill.env
chmod 600 /root/pitr-drill.env

cat > /root/pitr-drill-lib.sh <<'LIB'
# Biblioteca comun del ensayo PITR. Se sourcea al inicio de cada bloque remoto.
STATE_FILE=/root/pitr-drill.env
PGBIN=/usr/lib/postgresql/18/bin

# Escritura atomica: construye el fichero completo en un temporal y lo renombra. Si el proceso
# muere a mitad, el .env anterior queda intacto — nunca a medias, nunca con la clave perdida.
# El valor va ENTRECOMILLADO y se rechaza lo que no pueda representarse sin escapes (fail-closed).
save_state() {
  local k="$1" v="$2" tmp
  case "$k" in [A-Za-z_][A-Za-z0-9_]*) : ;; *) echo "save_state: clave invalida '$k'"; return 1 ;; esac
  case "$v" in *"'"*) echo "save_state: valor con comilla simple, no soportado"; return 1 ;; esac
  case "$v" in *$'\n'*) echo "save_state: valor multilinea, no soportado"; return 1 ;; esac
  touch "$STATE_FILE"
  tmp=$(mktemp /root/.pitr-state-XXXXXX)
  grep -v "^${k}=" "$STATE_FILE" > "$tmp" || true
  printf "%s='%s'\n" "$k" "$v" >> "$tmp"
  chmod 600 "$tmp"
  mv -f "$tmp" "$STATE_FILE"
}

# Parser explicito: NO usa `source`. Al ejecutar el plan el 2026-08-01, guardar
# TS_TARGET='2026-07-31 23:41:34.497764+00' sin comillas hizo que `. "$STATE_FILE"` asignara
# TS_TARGET=2026-07-31 y ejecutara `23:41:34.497764+00` como comando. No hace falta contenido
# hostil para romper `source`: basta un valor con un espacio. `printf -v` asigna sin eval.
load_state() {
  local line k v
  test -f "$STATE_FILE" || { echo "ABORTAR: no existe $STATE_FILE"; return 1; }
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|\#*) continue ;; esac
    case "$line" in *=*) : ;; *) echo "ABORTAR: linea sin '=' en $STATE_FILE: $line"; return 1 ;; esac
    k="${line%%=*}"; v="${line#*=}"
    case "$k" in [A-Za-z_][A-Za-z0-9_]*) : ;; *) echo "ABORTAR: clave invalida '$k'"; return 1 ;; esac
    case "$v" in "'"*"'") v="${v#\'}"; v="${v%\'}" ;; esac
    printf -v "$k" '%s' "$v"
  done < "$STATE_FILE"
}

# Devuelve 0 si $1 es un directorio real, sin symlinks, hijo directo de /var/tmp y con el prefijo
# de basename indicado en $2. Un prefijo textual no basta: /var/tmp/pitr-drill-X/../../lib lo pasa.
ruta_temporal_valida() {
  local d="$1" prefijo="$2" real base padre
  test -n "$d" || return 1
  test -d "$d" || return 1
  real=$(realpath "$d") || return 1
  test "$real" = "$d" || return 1
  padre=$(dirname "$real"); base=$(basename "$real")
  test "$padre" = "/var/tmp" || return 1
  case "$base" in "$prefijo"*) return 0 ;; *) return 1 ;; esac
}

# Ejecuta psql contra el cluster de ensayo (socket propio, puerto 5433).
drillsql() { sudo -u postgres psql -h "$DRILL_DIR" -p 5433 -At "$@"; }

# Ejecuta psql contra produccion.
prodsql()  { sudo -u postgres psql -At "$@"; }
LIB
chmod 600 /root/pitr-drill-lib.sh
bash -n /root/pitr-drill-lib.sh

. /root/pitr-drill-lib.sh
save_state OFFSITE_DIR "$OFFSITE_DIR"
save_state DRILL_DIR   "$DRILL_DIR"
save_state PGBIN       "$PGBIN"
cat /root/pitr-drill.env
REMOTE
```

- [ ] **Paso 5: Localizar la copia base y comprobar que no trae tars extra**

Sin `ls | head` y sin `grep | wc -l`: ambos rompen bajo `pipefail` (convenciones 2 y 3).

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
B=$(find /var/backups/recetas-postgres/base -mindepth 1 -maxdepth 1 -type d -name 'base_*' -printf '%f\n' | sort | sed -n '$p')
test -n "$B" || { echo "ABORTAR: no hay ninguna copia base"; exit 1; }
echo "BASE_DIR_NAME=$B"
D="/var/backups/recetas-postgres/base/$B"
ls -1 "$D"
for f in base.tar.gz pg_wal.tar.gz; do
  test -f "$D/$f" || { echo "ABORTAR: falta $f en $D"; exit 1; }
done
EXTRA=$(find "$D" -mindepth 1 -maxdepth 1 ! -name 'base.tar.gz' ! -name 'pg_wal.tar.gz' ! -name 'backup_manifest' -printf 'x\n' | wc -l)
test "$EXTRA" -eq 0 || { echo "ABORTAR: el base backup trae ficheros no previstos (posible tablespace)"; exit 1; }
save_state BASE_DIR_NAME "$B"
REMOTE
```

`find -printf 'x\n' | wc -l` siempre devuelve 0 aunque no encuentre nada, a diferencia de `grep`.
Los nombres de directorio `base_YYYYMMDDTHHMMSSZ` ordenan igual lexicografica que cronologicamente,
asi que `sort | sed -n '$p'` da el mas reciente sin depender de `-t`.

- [ ] **Paso 6: Leer los cinco parametros que la recovery exige >= primario, y persistirlos**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
for p in max_connections max_prepared_transactions max_locks_per_transaction max_wal_senders max_worker_processes; do
  V=$(prodsql -c "SELECT setting FROM pg_settings WHERE name = '$p';")
  test -n "$V" || { echo "ABORTAR: no se pudo leer $p"; exit 1; }
  echo "$p = $V"
  save_state "PRIMARY_$(echo "$p" | tr '[:lower:]' '[:upper:]')" "$V"
done
REMOTE
```

- [ ] **Paso 7: Timers realmente vivos, afirmado y no leido**

**Dos trampas encontradas al ejecutar este paso el 2026-08-01, ambas del propio chequeo:**

1. **`Result=success` no significa que la unidad haya funcionado.** Una unidad que nunca ha corrido
   *en este arranque* devuelve tambien `success`, porque es el valor por defecto. Afirmarlo a solas
   es un verde falso.
2. **`ExecMainExitTimestamp` se pierde en cada reinicio.** Es estado de runtime de systemd. Para una
   unidad **semanal**, tras un reinicio reciente esta vacio aunque todo funcione. Exigirlo no
   vacio aborta el plan sin motivo.

3. **`LastTriggerUSecRealtime` tampoco sirve:** esta vacio o a `0`. La propiedad con el dato es
   `LastTriggerUSec`, y aun asi es estado de runtime.

La evidencia que **si** sobrevive a un reinicio son los stamp files de `/var/lib/systemd/timers/`,
que systemd escribe porque estos timers llevan `Persistent=yes`. Su `mtime` es el ultimo disparo
real. Se usa esa fuente, con `LastTriggerUSec` como respaldo y un umbral de recencia por cadencia.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
AHORA=$(date +%s)
# unidad:dias_maximos_desde_el_ultimo_disparo
for entrada in recetas-postgres-logical-backup:2 recetas-postgres-basebackup:8 recetas-postgres-offsite-backup:2; do
  u="${entrada%:*}"; maxdias="${entrada#*:}"
  echo "== $u (max $maxdias dias)"
  systemctl is-enabled "$u.timer" > /dev/null || { echo "ABORTAR: $u.timer no esta enabled"; exit 1; }
  systemctl is-active  "$u.timer" > /dev/null || { echo "ABORTAR: $u.timer no esta active"; exit 1; }

  # Proxima ejecucion programada: si no hay, el timer esta muerto aunque figure activo.
  NEXT=$(systemctl show "$u.timer" -p NextElapseUSecRealtime --value)
  test -n "$NEXT" -a "$NEXT" != "0" || { echo "ABORTAR: $u.timer no tiene proxima ejecucion"; exit 1; }

  # Ultimo disparo real. Fuente primaria: el stamp file, que sobrevive a los reinicios porque el
  # timer lleva Persistent=yes. Respaldo: LastTriggerUSec (runtime, se pierde al reiniciar).
  STAMP="/var/lib/systemd/timers/stamp-$u.timer"
  if [ -f "$STAMP" ]; then
    LAST_S=$(date -r "$STAMP" +%s); FUENTE="stamp"
  else
    LT=$(systemctl show "$u.timer" -p LastTriggerUSec --value)
    test -n "$LT" || { echo "ABORTAR: $u.timer no registra ningun disparo previo"; exit 1; }
    LAST_S=$(date -d "$LT" +%s); FUENTE="LastTriggerUSec"
  fi
  DIAS=$(( (AHORA - LAST_S) / 86400 ))
  echo "   ultimo disparo ($FUENTE): $(date -u -d "@$LAST_S") (hace $DIAS dias); proximo: $NEXT"
  test "$DIAS" -le "$maxdias" || { echo "ABORTAR: $u lleva $DIAS dias sin dispararse (max $maxdias)"; exit 1; }

  # Result/ExecMainStatus solo son concluyentes si la unidad SI corrio en este arranque.
  T=$(systemctl show "$u.service" -p ExecMainExitTimestamp --value)
  if [ -n "$T" ]; then
    R=$(systemctl show "$u.service" -p Result --value)
    S=$(systemctl show "$u.service" -p ExecMainStatus --value)
    echo "   corrio en este arranque: Result=$R ExecMainStatus=$S"
    test "$R" = "success" || { echo "ABORTAR: $u termino con Result=$R"; exit 1; }
    test "$S" = "0"       || { echo "ABORTAR: $u termino con status $S"; exit 1; }
  else
    echo "   no ha corrido en este arranque (reinicio reciente); se valida por journal"
    FALLOS=$(journalctl -u "$u.service" --no-pager --since "-${maxdias} days" 2>/dev/null | grep -c 'Failed\|failed with' || true)
    test "$FALLOS" = "0" || { echo "ABORTAR: el journal registra $FALLOS fallos de $u"; exit 1; }
  fi
done
echo "=== timers ==="
systemctl list-timers --all --no-pager | sed -n '1p;/recetas/p'
REMOTE
```

- [ ] **Paso 8: Bloque de limpieza — escrito ANTES de tocar nada**

Es la red de seguridad de todo el plan y se ejecuta en los peores momentos. Cinco guardas, cada una
correspondiente a un camino de destruccion silenciosa identificado en revision:

1. **Rutas validadas por forma canonica**, no por prefijo textual (`ruta_temporal_valida`).
2. **Identidad del proceso comprobada contra `/proc/PID/cmdline`** antes de mandarle ninguna señal.
   `pg_ctl status` se apoya en `postmaster.pid` y comprueba vida con `kill(pid,0)`: **no verifica
   que ese PID administre ese PGDATA**. Con un `postmaster.pid` huerfano cuyo PID haya sido
   reutilizado por el postmaster de **produccion**, un `pg_ctl stop` se lo mandaria a produccion.
3. **Estado ausente no significa "nada que limpiar"**: se buscan huerfanos igualmente.
4. **La parada debe demostrarse**, no suponerse, antes de cualquier `rm -rf`.
5. **`DB_CREATED` se limpia tras el `dropdb`**, para que una segunda ejecucion no borre una base
   recreada despues.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
cat > /root/pitr-drill-cleanup.sh <<'SCRIPT'
#!/usr/bin/env bash
# Limpieza idempotente del ensayo PITR. Segura de ejecutar en cualquier momento.
set -uo pipefail
RC=0
STATE_FILE=/root/pitr-drill.env
PGBIN=/usr/lib/postgresql/18/bin

ruta_temporal_valida() {
  local d="$1" prefijo="$2" real base padre
  test -n "$d" || return 1
  test -d "$d" || return 1
  real=$(realpath "$d") || return 1
  test "$real" = "$d" || return 1
  padre=$(dirname "$real"); base=$(basename "$real")
  test "$padre" = "/var/tmp" || return 1
  case "$base" in "$prefijo"*) return 0 ;; *) return 1 ;; esac
}

# Devuelve el PID de un proceso del cluster que administre EXACTAMENTE $1, o vacio.
# Dos vias: (a) token `-D <pgdata>` exacto en el cmdline del postmaster, (b) cwd, que apunta al
# PGDATA en el postmaster Y en todos sus workers — asi detecta tambien workers huerfanos.
#
# La version anterior unia el cmdline con `paste -sd'\037'` y comparaba con `case`. Era un bug:
# `paste -d` no interpreta `\037` como octal (solo entiende \n, \t, \\, \0), asi que unia con los
# caracteres literales `\`, `0`, `3`, `7` en rotacion y el `case` NUNCA podia casar. La funcion
# devolvia vacio siempre: la limpieza habria concluido "sin cluster vivo" y borrado un PGDATA en
# uso. Detectado el 2026-08-01 probando la funcion contra el postmaster de produccion.
pid_de_pgdata() {
  local d="$1" p i args cwd
  for p in $(pgrep -x postgres 2>/dev/null || true); do
    if [ -r "/proc/$p/cmdline" ]; then
      mapfile -d '' -t args < "/proc/$p/cmdline" 2>/dev/null || args=()
      for ((i = 0; i < ${#args[@]}; i++)); do
        if [ "${args[i]}" = "-D" ] && [ "${args[i+1]:-}" = "$d" ]; then echo "$p"; return 0; fi
      done
    fi
    cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
    if [ -n "$cwd" ] && [ "$cwd" = "$d" ]; then echo "$p"; return 0; fi
  done
  return 0
}

# --- Estado ---
if [ -f "$STATE_FILE" ]; then
  # shellcheck disable=SC1090
  . "$STATE_FILE" || { echo "ERROR: $STATE_FILE ilegible o corrupto"; RC=1; }
else
  echo "AVISO: no hay $STATE_FILE; busco huerfanos de todos modos"
fi

# --- 1. Cluster de ensayo ---
DRILL_OK=0
if ruta_temporal_valida "${DRILL_DIR:-}" "pitr-drill-"; then
  DRILL_OK=1
elif [ -n "${DRILL_DIR:-}" ]; then
  echo "ERROR: DRILL_DIR '$DRILL_DIR' no supera la validacion canonica; NO lo toco"; RC=1
fi

if [ "$DRILL_OK" = "1" ] && [ -d "$DRILL_DIR/data" ]; then
  D="$DRILL_DIR/data"
  if [ "$(realpath "$D")" != "$D" ]; then
    echo "ERROR: $D no es ruta real; NO lo toco"; DRILL_OK=0; RC=1
  else
    PID=$(pid_de_pgdata "$D")
    if [ -n "$PID" ]; then
      echo "postmaster $PID administra $D: parando"
      if sudo -u postgres "$PGBIN/pg_ctl" -D "$D" -w -t 120 stop; then
        sleep 1
        if [ -n "$(pid_de_pgdata "$D")" ]; then
          echo "ERROR: sigue habiendo un postmaster en $D; NO borro el PGDATA"; DRILL_OK=0; RC=1
        fi
      else
        echo "ERROR: pg_ctl stop fallo; NO borro el PGDATA"; DRILL_OK=0; RC=1
      fi
    else
      # Sin proceso que administre $D. Si aun asi hay postmaster.pid, es huerfano: informar.
      test -f "$D/postmaster.pid" && echo "AVISO: postmaster.pid huerfano en $D (ningun proceso lo administra)"
      echo "sin cluster de ensayo vivo"
    fi
  fi
fi

# --- 2. Temporales ---
if [ "$DRILL_OK" = "1" ]; then
  if ruta_temporal_valida "$DRILL_DIR" "pitr-drill-"; then
    echo "borrando $DRILL_DIR"; rm -rf "$DRILL_DIR" || RC=1
  else
    echo "ERROR: DRILL_DIR dejo de ser valido antes del borrado"; RC=1
  fi
fi
if ruta_temporal_valida "${OFFSITE_DIR:-}" "pitr-offsite-"; then
  echo "borrando $OFFSITE_DIR"; rm -rf "$OFFSITE_DIR" || RC=1
elif [ -n "${OFFSITE_DIR:-}" ]; then
  echo "ERROR: OFFSITE_DIR '$OFFSITE_DIR' no supera la validacion canonica; NO lo toco"; RC=1
fi

# --- 3. Base desechable: solo si consta creada por este ensayo ---
if [ "${DB_CREATED:-0}" = "1" ]; then
  echo "eliminando la base desechable creada por este ensayo"
  if sudo -u postgres dropdb --if-exists recetas_pitr_drill; then
    tmp=$(mktemp /root/.pitr-state-XXXXXX)
    grep -v '^DB_CREATED=' "$STATE_FILE" > "$tmp" 2>/dev/null || true
    chmod 600 "$tmp"; mv -f "$tmp" "$STATE_FILE"
  else
    RC=1
  fi
else
  echo "DB_CREATED != 1: no toco recetas_pitr_drill (no consta creada por este ensayo)"
fi

# --- 4. Huerfanos, se haya podido leer el estado o no ---
HUERFANOS=$(find /var/tmp -maxdepth 1 -name 'pitr-*' 2>/dev/null || true)
if [ -n "$HUERFANOS" ]; then echo "AVISO: quedan temporales sin limpiar:"; echo "$HUERFANOS"; RC=1; fi
EXISTE_DB=$(sudo -u postgres psql -At -c "SELECT COUNT(*) FROM pg_database WHERE datname='recetas_pitr_drill';" 2>/dev/null || echo "?")
test "$EXISTE_DB" = "0" || { echo "AVISO: recetas_pitr_drill sigue existiendo (count=$EXISTE_DB)"; RC=1; }
PUERTO=$(ss -ltn 2>/dev/null || true)
case "$PUERTO" in *:5433*) echo "AVISO: algo escucha en 5433"; RC=1 ;; esac

echo "--- estado de produccion ---"
systemctl is-active postgresql@18-main || RC=1
systemctl is-active recetas-backend.service || RC=1
df -h --output=avail,pcent / | sed -n '$p'
exit $RC
SCRIPT
chmod 700 /root/pitr-drill-cleanup.sh
bash -n /root/pitr-drill-cleanup.sh
echo "script de limpieza sintacticamente valido"
REMOTE
```

`pid_de_pgdata` compara el `-D` del `cmdline` con el PGDATA esperado usando `\037` como separador,
para que una coincidencia parcial de prefijo no cuente. El estado ausente ya no produce `exit 0`
optimista: la seccion 4 busca huerfanos igualmente y devuelve codigo distinto de cero si los hay.

- [ ] **Paso 9: Medir espacio libre en el filesystem correcto**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
findmnt -nT "$OFFSITE_DIR" -o SOURCE,TARGET,AVAIL,USE%
findmnt -nT /var/cache/restic -o TARGET,AVAIL 2>/dev/null || echo "(cache de restic en el mismo fs que /)"
REMOTE
```

La puerta de disco se evalua en la Tarea 4, cuando ya se conoce el tamaño del snapshot.

---

### Task 2: Marcadores temporales en una base desechable

**Ficheros:** ninguno en el repo. Crea y destruye la base `recetas_pitr_drill` en produccion.

**Interfaces:**
- Consume: Task 1 completa.
- Produce en el estado: `DB_CREATED`, `TS_TARGET`, `TS_B`, `COUNTS_PROD_FILE`.

**Por que una base aparte:** el WAL es del cluster entero. Escribir en una base desechable genera
exactamente el WAL necesario sin meter una tabla ajena al esquema gestionado por Flyway.

**Por que `TS_TARGET` se captura en una transaccion aparte, y no como `ts_a + 30s`:**
`recovery_target_time` se compara contra el **timestamp de commit** registrado en el WAL, no contra
el valor de la columna. `clock_timestamp()` se evalua *durante* el INSERT, antes del commit. Si ese
commit se retrasara mas que el margen elegido — un bloqueo, una pausa de I/O — el objetivo caeria
**antes** del commit de A y el marcador A no apareceria: un fallo aparente del ensayo por un
artefacto del propio ensayo. Capturando el reloj en una transaccion posterior, el orden
`commit(A) < TS_TARGET < commit(B)` queda garantizado por construccion, no por margen.

- [ ] **Paso 1: Crear la base desechable y registrar que la creamos nosotros**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
YA=$(prodsql -c "SELECT COUNT(*) FROM pg_database WHERE datname='recetas_pitr_drill';")
test "$YA" = "0" || { echo "ABORTAR: recetas_pitr_drill ya existe; este plan no la reutiliza"; exit 1; }
sudo -u postgres createdb recetas_pitr_drill
save_state DB_CREATED 1
sudo -u postgres psql -v ON_ERROR_STOP=1 -d recetas_pitr_drill <<'SQL'
CREATE TABLE pitr_marker (
  id    int PRIMARY KEY,
  label text NOT NULL,
  ts    timestamptz NOT NULL DEFAULT clock_timestamp()
);
SQL
echo "base desechable creada"
REMOTE
```

`save_state DB_CREATED 1` va inmediatamente despues del `createdb`: es lo que autoriza a la limpieza
a borrarla. Si la base ya existiera de antes, el plan aborta en vez de reutilizarla — y la limpieza
no la tocara, porque `DB_CREATED` no estara puesto.

- [ ] **Paso 2: Insertar A, y solo despues capturar `TS_TARGET`**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
# Transaccion 1: el INSERT de A. Al retornar psql, su commit ya esta en el WAL.
sudo -u postgres psql -v ON_ERROR_STOP=1 -d recetas_pitr_drill -c "INSERT INTO pitr_marker (id, label) VALUES (1, 'marker-a');"
# Transaccion 2, posterior al commit de A: este es el objetivo de recuperacion.
TS_TARGET=$(sudo -u postgres psql -v ON_ERROR_STOP=1 -At -d recetas_pitr_drill -c "SELECT to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS.US')||'+00';")
test -n "$TS_TARGET" || { echo "ABORTAR: no se pudo capturar TS_TARGET"; exit 1; }
save_state TS_TARGET "$TS_TARGET"
echo "TS_TARGET=$TS_TARGET"
REMOTE
```

- [ ] **Paso 3: Esperar 90 segundos e insertar B**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
sleep 90
sudo -u postgres psql -v ON_ERROR_STOP=1 -d recetas_pitr_drill -c "INSERT INTO pitr_marker (id, label) VALUES (2, 'marker-b');"
TS_B=$(sudo -u postgres psql -v ON_ERROR_STOP=1 -At -d recetas_pitr_drill -c "SELECT to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS.US')||'+00';")
save_state TS_B "$TS_B"
echo "TS_B=$TS_B"
sudo -u postgres psql -v ON_ERROR_STOP=1 -At -F'|' -d recetas_pitr_drill -c "SELECT id, label, to_char(ts AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI:SS.US')||'+00' FROM pitr_marker ORDER BY id;"
REMOTE
```

Verificar que `TS_TARGET` cae entre los dos `ts` impresos. Es una comprobacion de cordura: la
garantia fuerte la da el orden de ejecucion, no esta lectura.

- [ ] **Paso 4: Registrar los recuentos de produccion**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
prodsql -d recetas_familiares <<'SQL' > /root/counts_prod.txt
SELECT 'users='||COUNT(*) FROM users
UNION ALL SELECT 'families='||COUNT(*) FROM families
UNION ALL SELECT 'recipes='||COUNT(*) FROM recipes
ORDER BY 1;
SQL
save_state COUNTS_PROD_FILE /root/counts_prod.txt
cat /root/counts_prod.txt
REMOTE
```

**Es un sanity check, no una prueba de integridad:** se toma despues de B, mientras el objetivo de
recuperacion es anterior, y la app puede escribir durante el ensayo. Comparar cardinalidades detecta
un desastre evidente; no demuestra que los datos sean correctos.

---

### Task 3: Barrera de WAL archivada y snapshot offsite fijado

**Ficheros:** ninguno.

**Interfaces:**
- Consume: `TS_B`.
- Produce: `WAL_BARRIER`, `ARCHIVER_FAILED_BEFORE`, `SNAPSHOT_ID`.

**Por que "barrera" y no "el segmento de B":** entre el commit de B y la consulta puede haber
ocurrido una rotacion por `archive_timeout` o por trafico de la aplicacion. Lo que se necesita
demostrar no es *cual* segmento contiene B, sino que **todo el WAL hasta despues de B esta
archivado y ha viajado al offsite**. Como el archivado es secuencial, que la barrera este archivada
implica que lo anterior tambien.

- [ ] **Paso 1: Generar escritura, rotar y esperar el archivado confirmado**

`pg_switch_wal()` **no rota nada** si no ha habido escrituras desde el ultimo cambio de segmento; por
eso se fuerza una escritura antes. Y la existencia del fichero no basta: `cp` lo crea antes de que
termine el `sync` del `archive_command`. La señal fiable es `pg_stat_archiver.last_archived_wal`.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state

FAILED_BEFORE=$(prodsql -c "SELECT failed_count FROM pg_stat_archiver;")
save_state ARCHIVER_FAILED_BEFORE "$FAILED_BEFORE"
echo "failed_count inicial = $FAILED_BEFORE"

# Escritura posterior a B, para garantizar que el segmento actual tiene contenido nuevo.
sudo -u postgres psql -v ON_ERROR_STOP=1 -d recetas_pitr_drill -c "INSERT INTO pitr_marker (id, label) VALUES (3, 'post-b-filler');"

WAL_BARRIER=$(prodsql -c "SELECT pg_walfile_name(pg_current_wal_insert_lsn());")
test -n "$WAL_BARRIER" || { echo "ABORTAR: no se pudo obtener el segmento actual"; exit 1; }
echo "WAL_BARRIER=$WAL_BARRIER"
prodsql -c "SELECT pg_switch_wal();" > /dev/null

OK=0
for i in $(seq 1 30); do
  LAST=$(prodsql -c "SELECT COALESCE(last_archived_wal,'') FROM pg_stat_archiver;")
  if [ -n "$LAST" ] && [ ! "$LAST" \< "$WAL_BARRIER" ]; then OK=1; echo "archivado confirmado: last_archived_wal=$LAST"; break; fi
  sleep 10
done
test "$OK" -eq 1 || { echo "ABORTAR: $WAL_BARRIER no consta archivado tras 300 s"; exit 1; }

FAILED_NOW=$(prodsql -c "SELECT failed_count FROM pg_stat_archiver;")
test "$FAILED_NOW" = "$FAILED_BEFORE" || { echo "ABORTAR: failed_count subio de $FAILED_BEFORE a $FAILED_NOW"; exit 1; }
test -f "/var/backups/recetas-postgres/wal/$WAL_BARRIER" || { echo "ABORTAR: la barrera no esta en el archivo local"; exit 1; }
save_state WAL_BARRIER "$WAL_BARRIER"
REMOTE
```

La comparacion `[ ! "$LAST" \< "$WAL_BARRIER" ]` es lexicografica, valida porque los nombres de
segmento WAL son hexadecimal de ancho fijo (24 caracteres) y ordenan igual que cronologicamente.

- [ ] **Paso 2: Lanzar la copia offsite y afirmar el resultado**

El script hace `backup`, `forget --prune` y `check --read-data-subset`; puede tardar varios minutos.

```bash
ssh -o ServerAliveInterval=30 root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
systemctl start recetas-postgres-offsite-backup.service
R=$(systemctl show recetas-postgres-offsite-backup.service -p Result --value)
S=$(systemctl show recetas-postgres-offsite-backup.service -p ExecMainStatus --value)
echo "Result=$R ExecMainStatus=$S"
test "$R" = "success" || { echo "ABORTAR: Result=$R"; exit 1; }
test "$S" = "0"       || { echo "ABORTAR: ExecMainStatus=$S"; exit 1; }
REMOTE
```

Sin snapshot fresco no hay nada que ensayar. Si falla: limpieza y reportar.

- [ ] **Paso 3: Fijar el snapshot por ID y afirmar sus metadatos**

`restic restore latest` se resuelve en el momento de ejecutarlo: si el timer diario dispara entre
esta tarea y la siguiente, restauraria otro backup. Se fija el ID, y el parser **falla** si los
metadatos no cuadran, en vez de limitarse a imprimirlos.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
set -a; . /etc/recetas-familiares/offsite-backup.env; set +a

SNAPSHOT_ID=$(restic snapshots --tag scheduled --host "$(hostname)" --path /var/backups/recetas-postgres --latest 1 --json \
  | TS_B="$TS_B" HOSTN="$(hostname)" python3 -c '
import sys, json, os
snaps = json.load(sys.stdin)
if not snaps:
    sys.exit("ABORTAR: sin snapshots que cumplan el filtro")
s = snaps[-1]
assert s["hostname"] == os.environ["HOSTN"], "host inesperado: %s" % s["hostname"]
assert "scheduled" in (s.get("tags") or []), "falta el tag scheduled"
assert "/var/backups/recetas-postgres" in s["paths"], "paths inesperado: %s" % s["paths"]
# s["time"] es ISO-8601 con zona; TS_B es UTC "YYYY-MM-DD HH:MM:SS.ffffff+00"
from datetime import datetime, timezone
st = datetime.fromisoformat(s["time"]).astimezone(timezone.utc)
tb = datetime.fromisoformat(os.environ["TS_B"].replace("+00", "+00:00")).astimezone(timezone.utc)
assert st > tb, "el snapshot (%s) no es posterior a TS_B (%s)" % (st, tb)
print(s["id"])
')
test -n "$SNAPSHOT_ID" || { echo "ABORTAR: no se obtuvo SNAPSHOT_ID"; exit 1; }
echo "SNAPSHOT_ID=$SNAPSHOT_ID"

# `restic ls` emite miles de lineas. Con `| grep -qF`, grep sale al primer acierto, restic recibe
# SIGPIPE y la tuberia devuelve 141 bajo pipefail: el `if !` abortaria JUSTO cuando la barrera si
# esta. Se vuelca a fichero, que consume toda la salida, y se busca despues (convencion 8).
restic ls "$SNAPSHOT_ID" /var/backups/recetas-postgres/wal > /tmp/pitr-snapshot-wal.txt
ENCONTRADA=$(grep -cF "$WAL_BARRIER" /tmp/pitr-snapshot-wal.txt || true)
rm -f /tmp/pitr-snapshot-wal.txt
test "$ENCONTRADA" -ge 1 || { echo "ABORTAR: el snapshot no contiene la barrera $WAL_BARRIER"; exit 1; }
echo "la barrera viajo al offsite: OK"
save_state SNAPSHOT_ID "$SNAPSHOT_ID"
REMOTE
```

Esta comprobacion es la prueba de que **el WAL posterior a los marcadores llego al repositorio
remoto**; sin ella, el ensayo posterior no demostraria procedencia.

---

### Task 4: Restaurar desde el repositorio offsite, y solo desde ahi

**Ficheros:** puebla `$OFFSITE_DIR`.

**Interfaces:**
- Consume: `SNAPSHOT_ID`, `WAL_BARRIER`, `BASE_DIR_NAME`, `OFFSITE_DIR`.
- Produce: `OFFSITE_ROOT`. La Tarea 5 no puede leer ninguna otra ruta.

- [ ] **Paso 1: Primera puerta de disco, en bytes**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
set -a; . /etc/recetas-familiares/offsite-backup.env; set +a
RESTORE_BYTES=$(restic stats "$SNAPSHOT_ID" --mode restore-size --json | python3 -c 'import sys,json; print(json.load(sys.stdin)["total_size"])')
AVAIL=$(findmnt -bnT "$OFFSITE_DIR" -o AVAIL)
RESERVA=$((5 * 1024 * 1024 * 1024))
NECESARIO=$((RESTORE_BYTES * 2 + RESERVA))
echo "restore=$RESTORE_BYTES avail=$AVAIL necesario=$NECESARIO"
test "$AVAIL" -ge "$NECESARIO" || { echo "ABORTAR: espacio insuficiente"; exit 1; }
REMOTE
```

`restore-size` cuenta los ficheros tal como quedan restaurados; `base.tar.gz` cuenta **comprimido**,
no por lo que ocupara extraido. Por eso el factor 2 es una heuristica y **la proteccion real es la
segunda puerta del paso 4**, ya con tamaños medidos.

**Si esta puerta falla, detener el plan y reportar.** No liberar espacio por iniciativa propia:
borrar backups locales para hacer sitio a un ensayo no es decision de un agente.

- [ ] **Paso 2: Restaurar solo `base/` y `wal/`, por ID de snapshot**

`logical/` no se restaura porque es irrelevante para un PITR — no por ahorro: los dumps logicos son
de cientos de KiB frente a varios GiB de WAL.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
set -a; . /etc/recetas-familiares/offsite-backup.env; set +a
restic restore "$SNAPSHOT_ID" --target "$OFFSITE_DIR" \
  --include /var/backups/recetas-postgres/base \
  --include /var/backups/recetas-postgres/wal
REMOTE
```

- [ ] **Paso 3: Verificar procedencia y completitud**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
O="$OFFSITE_DIR/var/backups/recetas-postgres"
# Procedencia canonica: todo debe residir DENTRO del temporal registrado. Un '..' en el estado
# apuntaria al archivo local y el ensayo leeria WAL de produccion dando un verde falso.
REAL_O=$(realpath "$O") || { echo "ABORTAR: $O no resuelve"; exit 1; }
case "$REAL_O/" in "$OFFSITE_DIR"/*) : ;; *) echo "ABORTAR: $REAL_O cae fuera de $OFFSITE_DIR"; exit 1 ;; esac

test -d "$O/base/$BASE_DIR_NAME"               || { echo "ABORTAR: el snapshot no trae $BASE_DIR_NAME"; exit 1; }
test -f "$O/base/$BASE_DIR_NAME/base.tar.gz"   || { echo "ABORTAR: falta base.tar.gz"; exit 1; }
test -f "$O/base/$BASE_DIR_NAME/pg_wal.tar.gz" || { echo "ABORTAR: falta pg_wal.tar.gz"; exit 1; }
test -f "$O/wal/$WAL_BARRIER"                  || { echo "ABORTAR: la barrera no esta en lo restaurado"; exit 1; }

# restic preserva symlinks. Un enlace en wal/ hacia /var/backups/... convertiria el ensayo en una
# lectura del archivo local: exactamente el verde falso que hay que impedir. `test -f` los sigue.
ENLACES=$(find "$O" \( -type l -o ! -type f -a ! -type d \) -printf '%p -> %l\n' | head -20)
test -z "$ENLACES" || { echo "ABORTAR: hay symlinks o ficheros no regulares en lo restaurado:"; echo "$ENLACES"; exit 1; }

N=$(find "$O/wal" -type f -printf 'x\n' | wc -l)
echo "segmentos WAL restaurados: $N (sin symlinks)"
save_state OFFSITE_ROOT "$O"
REMOTE
```

Se usa `BASE_DIR_NAME` del estado, no `el mas reciente`: si el timer semanal creo un base backup
**despues** de los marcadores, elegir el mas reciente daria una copia base posterior a `TS_TARGET` y
la recovery fallaria con el objetivo por detras del punto consistente.

- [ ] **Paso 4: Segunda puerta de disco, ya con tamaños reales**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
RESTAURADO=$(du -sb "$OFFSITE_ROOT" | cut -f1)
TARS=$(du -cb "$OFFSITE_ROOT/base/$BASE_DIR_NAME/base.tar.gz" "$OFFSITE_ROOT/base/$BASE_DIR_NAME/pg_wal.tar.gz" | sed -n '$p' | cut -f1)
CACHE=$(du -sb /var/cache/restic 2>/dev/null | cut -f1 || echo 0)
AVAIL=$(findmnt -bnT "$DRILL_DIR" -o AVAIL)
RESERVA=$((5 * 1024 * 1024 * 1024))
# x6 sobre los tar comprimidos cubre la extraccion del PGDATA mas el WAL que genere la recovery.
NECESARIO=$((TARS * 6 + RESERVA))
echo "restaurado=$RESTAURADO tars=$TARS cache_restic=$CACHE avail=$AVAIL necesario=$NECESARIO"
test "$AVAIL" -ge "$NECESARIO" || { echo "ABORTAR: espacio insuficiente para extraer y arrancar"; exit 1; }
REMOTE
```

El multiplicador 6 es heuristico y esta declarado como tal; lo que protege de verdad a produccion es
la reserva de 5 GiB, que ninguna de las dos puertas permite invadir.

- [ ] **Paso 5: Permisos de lectura para `postgres`**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
chown -R postgres:postgres "$OFFSITE_DIR"
chmod 750 "$OFFSITE_DIR"
REMOTE
```

(Trampa del runbook: `postgres` no puede leer bajo `/root`; por eso los temporales van a `/var/tmp`.)

---

### Task 5: Levantar el cluster de ensayo con el aislamiento verificado antes de arrancar

**Ficheros:** puebla `$DRILL_DIR`.

- [ ] **Paso 1: Extraer la copia base en un data dir nuevo, vacio y sin symlinks**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
B="$OFFSITE_ROOT/base/$BASE_DIR_NAME"
case "$B" in /var/tmp/pitr-offsite-*) : ;; *) echo "ABORTAR: la base no procede del offsite restaurado"; exit 1 ;; esac
D="$DRILL_DIR/data"
mkdir -p "$D"
test -z "$(ls -A "$D")" || { echo "ABORTAR: el data dir no esta vacio"; exit 1; }
tar xzf "$B/base.tar.gz" -C "$D"

test ! -L "$D/pg_wal" || { echo "ABORTAR: pg_wal es un symlink"; exit 1; }
mkdir -p "$D/pg_wal"
test ! -L "$D/pg_wal" || { echo "ABORTAR: pg_wal es un symlink"; exit 1; }
test "$(realpath "$D/pg_wal")" = "$D/pg_wal" || { echo "ABORTAR: pg_wal no reside dentro del PGDATA"; exit 1; }

# Lo que invalida el ensayo NO es que pg_wal tenga contenido, sino que tenga SEGMENTOS: cuando
# restore_command falla, PostgreSQL busca el segmento en pg_wal, asi que un residuo podria suplir
# WAL que el offsite no tuviera. `base.tar.gz` trae siempre `archive_status/` y `summaries/`
# vacios — son parte estandar del PGDATA. Exigir el directorio vacio aborta el plan sin motivo
# (comprobado el 2026-08-01); lo correcto es exigir cero segmentos.
RESIDUAL=$(find "$D/pg_wal" -maxdepth 1 -type f -regextype posix-extended -regex '.*/[0-9A-F]{24}$' -printf 'x\n' | wc -l)
test "$RESIDUAL" -eq 0 || { echo "ABORTAR: hay $RESIDUAL segmentos WAL residuales en pg_wal"; exit 1; }
INESPERADO=$(find "$D/pg_wal" -mindepth 1 -maxdepth 1 ! -name archive_status ! -name summaries -printf '%p\n')
test -z "$INESPERADO" || { echo "ABORTAR: contenido inesperado en pg_wal:"; echo "$INESPERADO"; exit 1; }

tar xzf "$B/pg_wal.tar.gz" -C "$D/pg_wal"

# tablespace_map redirige pg_tblspc a las rutas ORIGINALES de produccion. La Tarea 1 ya descarto
# tablespaces de usuario, pero un base backup incompleto podria traer el mapa sin su tar: entonces
# el cluster de ensayo recrearia enlaces hacia datos reales y escribiria sobre ellos.
if [ -s "$D/tablespace_map" ]; then
  echo "ABORTAR: tablespace_map no esta vacio:"; cat "$D/tablespace_map"; exit 1
fi
test -z "$(ls -A "$D/pg_tblspc" 2>/dev/null)" || { echo "ABORTAR: pg_tblspc no esta vacio"; exit 1; }
ENLACES=$(find "$D" -type l -printf '%p -> %l\n' | head -20)
test -z "$ENLACES" || { echo "ABORTAR: hay symlinks dentro del PGDATA extraido:"; echo "$ENLACES"; exit 1; }

echo "PGDATA extraido; segmentos en pg_wal inicial: $(find "$D/pg_wal" -maxdepth 1 -type f -printf 'x\n' | wc -l)"
REMOTE
```

Doble comprobacion de symlink, antes y despues de `mkdir -p`, porque `mkdir -p` **sigue** enlaces
existentes. **Si `pg_wal` no estuviera vacio, el ensayo quedaria invalidado:** cuando
`restore_command` falla, PostgreSQL busca el segmento en `pg_wal/`, asi que un residuo podria suplir
WAL que el offsite no tuviera.

- [ ] **Paso 2: Neutralizar `postgresql.auto.conf` y las señales heredadas**

`postgresql.auto.conf` viaja dentro del base backup y **tiene mayor precedencia que
`postgresql.conf`**. Se listan solo los **nombres** de parametro: sus valores pueden ser sensibles
(cadenas de conexion, credenciales) y este es un terminal compartido.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
D="$DRILL_DIR/data"
echo "=== parametros heredados en postgresql.auto.conf (solo nombres) ==="
if [ -s "$D/postgresql.auto.conf" ]; then
  sed -n 's/^[[:space:]]*\([a-zA-Z_][a-zA-Z0-9_]*\)[[:space:]]*=.*/\1/p' "$D/postgresql.auto.conf"
else
  echo "(vacio o inexistente)"
fi
echo "=== señales heredadas ==="
find "$D" -maxdepth 1 -name '*.signal' -printf '%f\n'
: > "$D/postgresql.auto.conf"
rm -f "$D/standby.signal" "$D/recovery.signal" "$D/postmaster.pid"
echo "=== tras neutralizar: bytes en auto.conf y señales restantes ==="
wc -c < "$D/postgresql.auto.conf"
find "$D" -maxdepth 1 -name '*.signal' -printf '%f\n'
REMOTE
```

Si aparecieron parametros heredados, anotar sus **nombres** para el cierre del sprint: es
informacion util sobre el estado real del servidor.

- [ ] **Paso 3: Escribir la configuracion del ensayo**

Todos los valores salen del estado; no hay nada que transcribir a mano. El heredoc exterior va
entrecomillado (`<<'REMOTE'`) para que el shell **local** no expanda nada; el interior (`CONF`) sin
comillas para que las variables si se expandan en el **VPS**.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
D="$DRILL_DIR/data"
cat > "$D/postgresql.conf" <<CONF
listen_addresses = ''
unix_socket_directories = '$DRILL_DIR'
port = 5433
max_connections = $PRIMARY_MAX_CONNECTIONS
max_prepared_transactions = $PRIMARY_MAX_PREPARED_TRANSACTIONS
max_locks_per_transaction = $PRIMARY_MAX_LOCKS_PER_TRANSACTION
max_wal_senders = $PRIMARY_MAX_WAL_SENDERS
max_worker_processes = $PRIMARY_MAX_WORKER_PROCESSES
shared_buffers = 128MB
archive_mode = off
max_logical_replication_workers = 0
shared_preload_libraries = ''
hba_file = '$D/pg_hba.conf'
ident_file = '$D/pg_ident.conf'
restore_command = '/usr/bin/cp -- $OFFSITE_ROOT/wal/%f %p'
recovery_target_time = '$TS_TARGET'
recovery_target_action = 'promote'
CONF
echo "local all postgres peer" > "$D/pg_hba.conf"
: > "$D/pg_ident.conf"
touch "$D/recovery.signal"
chown -R postgres:postgres "$DRILL_DIR"
chmod 700 "$D"
cat "$D/postgresql.conf"
REMOTE
```

- [ ] **Paso 4: Afirmar la configuracion EFECTIVA por igualdad literal**

`cat` muestra lo escrito, no lo que PostgreSQL aplicara. `postgres -C` resuelve el valor efectivo
sin arrancar. Aqui no vale un `case` con comodines: un `restore_command` que contuviera **ademas**
un fallback al WAL local pasaria el filtro. Se exige igualdad exacta.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
D="$DRILL_DIR/data"
get() { sudo -u postgres "$PGBIN/postgres" -D "$D" -C "$1"; }
assert_eq() {
  local nombre="$1" esperado="$2" real; real="$(get "$1")"
  printf '%-28s = %s\n' "$nombre" "$real"
  test "$real" = "$esperado" || { echo "ABORTAR: $nombre='$real', esperado '$esperado'"; exit 1; }
}
assert_eq data_directory           "$D"
assert_eq hba_file                 "$D/pg_hba.conf"
assert_eq ident_file               "$D/pg_ident.conf"
assert_eq unix_socket_directories  "$DRILL_DIR"
assert_eq archive_mode             "off"
assert_eq port                     "5433"
assert_eq listen_addresses         ""
assert_eq primary_conninfo         ""
assert_eq archive_command          ""
assert_eq restore_command          "/usr/bin/cp -- $OFFSITE_ROOT/wal/%f %p"
assert_eq max_logical_replication_workers "0"
assert_eq shared_preload_libraries  ""
assert_eq recovery_target_action   "promote"
assert_eq recovery_target_time     "$TS_TARGET"
assert_eq max_connections          "$PRIMARY_MAX_CONNECTIONS"
assert_eq max_prepared_transactions "$PRIMARY_MAX_PREPARED_TRANSACTIONS"
assert_eq max_locks_per_transaction "$PRIMARY_MAX_LOCKS_PER_TRANSACTION"
assert_eq max_wal_senders          "$PRIMARY_MAX_WAL_SENDERS"
assert_eq max_worker_processes     "$PRIMARY_MAX_WORKER_PROCESSES"
echo "archive_library = $(get archive_library)"
echo "aislamiento verificado antes de arrancar"
REMOTE
```

Si `archive_command` o `archive_library` efectivos no estuvieran vacios pese a `archive_mode = off`,
detener y revisar: significaria que algo mas los esta fijando.

- [ ] **Paso 5: Arrancar y esperar la promocion**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
sudo -u postgres "$PGBIN/pg_ctl" -D "$DRILL_DIR/data" -l "$DRILL_DIR/pitr.log" -w -t 300 start
sed -n '$!p;$p' "$DRILL_DIR/pitr.log" | tail -40
REMOTE
```

`pg_ctl -w` devuelve codigo distinto de cero si el arranque falla y `set -e` corta ahi: el log solo
se imprime si arranco bien.

Esperado en el log: `starting point-in-time recovery`, `recovery stopping before ...`,
`archive recovery complete`, `database system is ready to accept connections`.

Es **normal** ver `cp: cannot stat '.../00000002.history'`: PostgreSQL sondea la siguiente timeline.

- [ ] **Paso 6: Confirmar el aislamiento tambien en caliente**

La comprobacion de sockets **no** usa `cmd | grep -q`: bajo `pipefail`, si `grep -q` sale antes de
consumir toda la salida de `ss`, la tuberia devuelve 141 y la condicion entra en la rama "sin
listener" — un verde falso justo en la comprobacion de aislamiento (convencion 8).

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
# El SQL devuelve las filas que NO cumplen lo esperado: cero filas = aislamiento correcto.
DISCREPANCIAS=$(sudo -u postgres psql -X -v ON_ERROR_STOP=1 -h "$DRILL_DIR" -p 5433 -At -c "
SELECT name||' = '||setting FROM pg_settings WHERE
     (name = 'archive_mode'                    AND setting <> 'off')
  OR (name = 'port'                            AND setting <> '5433')
  OR (name = 'listen_addresses'                AND setting <> '')
  OR (name = 'max_logical_replication_workers' AND setting <> '0')
  OR (name = 'restore_command'                 AND setting NOT LIKE '/usr/bin/cp -- /var/tmp/pitr-offsite-%');")
test -z "$DISCREPANCIAS" || { echo "ABORTAR: aislamiento incorrecto en caliente:"; echo "$DISCREPANCIAS"; exit 1; }
echo "GUCs de aislamiento verificados en caliente"

SOCKETS=$(ss -ltnH 2>/dev/null || true)
case "$SOCKETS" in *:5433*) echo "ABORTAR: el cluster de ensayo ha abierto TCP"; exit 1 ;; esac
echo "sin listeners TCP en 5433: OK"

# Sin aislamiento de red a nivel de SO, comprobar tambien que no hay conexiones salientes suyas.
SALIENTES=$(ss -tnp 2>/dev/null | sed -n "/pid=$(cat "$DRILL_DIR/data/postmaster.pid" | sed -n '1p')/p" || true)
test -z "$SALIENTES" || { echo "ABORTAR: el cluster de ensayo tiene conexiones salientes:"; echo "$SALIENTES"; exit 1; }
echo "sin conexiones salientes: OK"
REMOTE
```

---

### Task 6: Validar la precision del PITR

**Interfaces:**
- Consume: el cluster de la Tarea 5, `COUNTS_PROD_FILE`, `ARCHIVER_FAILED_BEFORE`.
- Produce: el veredicto. Es la tarea que decide si el riesgo residual se cierra o no.

- [ ] **Paso 1: Confirmar que salio de recovery**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
R=$(drillsql -c "SELECT pg_is_in_recovery();")
echo "pg_is_in_recovery=$R"
test "$R" = "f" || { echo "ABORTAR: el cluster sigue en recovery"; exit 1; }
REMOTE
```

- [ ] **Paso 2: La prueba de fuego — marcador A si, marcador B no**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
echo "=== filas recuperadas ==="
drillsql -F'|' -d recetas_pitr_drill -c "SELECT id, label, to_char(ts AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI:SS.US') FROM pitr_marker ORDER BY id;"
A=$(drillsql -d recetas_pitr_drill -c "SELECT COUNT(*) FROM pitr_marker WHERE label = 'marker-a';")
B=$(drillsql -d recetas_pitr_drill -c "SELECT COUNT(*) FROM pitr_marker WHERE label = 'marker-b';")
echo "marker-a=$A marker-b=$B"
test "$A" = "1" || { echo "FALLO DEL ENSAYO: falta el marcador A"; exit 1; }
test "$B" = "0" || { echo "FALLO DEL ENSAYO: el marcador B no deberia existir"; exit 1; }
echo "PITR CON PRECISION DE TRANSACCION: OK"
REMOTE
```

Interpretacion si falla:

- **Aparecen A y B:** `recovery_target_time` quedo **posterior** al commit de B, o no se aplico.
  Revisar en el log la linea `recovery stopping before ...`.
- **No aparece ninguno:** o el WAL restaurado no alcanzaba el commit de A, o el objetivo quedo por
  delante de ese commit. Ambas causas son posibles; distinguirlas por el log y por `TS_TARGET`.
- **La base `recetas_pitr_drill` no existe en el cluster recuperado:** la copia base es anterior a
  su creacion **y** el WAL no se reprodujo. Fallo real del ensayo.

En los tres casos: documentar, ejecutar la limpieza y **no** cerrar el riesgo residual.

- [ ] **Paso 3: Sanity check de los datos de la aplicacion**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
echo "=== recuperado ==="
drillsql -d recetas_familiares <<'SQL'
SELECT 'users='||COUNT(*) FROM users
UNION ALL SELECT 'families='||COUNT(*) FROM families
UNION ALL SELECT 'recipes='||COUNT(*) FROM recipes
ORDER BY 1;
SQL
echo "=== produccion (capturado en Tarea 2) ==="
cat "$COUNTS_PROD_FILE"
REMOTE
```

**Diferencias no implican fallo**: el objetivo de recuperacion es anterior a la captura, y la app
puede haber escrito durante el ensayo. Detecta un desastre evidente (cero filas, tablas ausentes),
no afirma integridad.

- [ ] **Paso 4: Confirmar que produccion no se ha visto afectada, comparando de verdad**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/pitr-drill-lib.sh
load_state
NOW=$(prodsql -c "SELECT failed_count FROM pg_stat_archiver;")
echo "failed_count antes=$ARCHIVER_FAILED_BEFORE ahora=$NOW"
test "$NOW" = "$ARCHIVER_FAILED_BEFORE" || { echo "ABORTAR: el archiver de produccion ha fallado durante el ensayo"; exit 1; }
systemctl is-active postgresql@18-main
systemctl is-active recetas-backend.service
REMOTE
```

Y desde PowerShell local (el `curl` de git-bash falla por el MITM TLS de Avast):

```powershell
Invoke-RestMethod https://recetas.167.233.213.242.sslip.io/api/v1/health
```

Esperado: `status = UP`.

---

### Task 7: Limpieza total

- [ ] **Paso 1: Ejecutar el bloque de limpieza de la Tarea 1**

```bash
ssh root@167.233.213.242 '/root/pitr-drill-cleanup.sh'
```

Devuelve codigo distinto de cero si algo no pudo limpiarse. **No ignorar ese codigo.**

- [ ] **Paso 2: Verificar que no queda nada**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
TEMPORALES=$(find /var/tmp -maxdepth 1 -name 'pitr-*' 2>/dev/null || true)
test -z "$TEMPORALES" || { echo "ABORTAR: quedan temporales:"; echo "$TEMPORALES"; exit 1; }
echo "sin temporales"
sudo -u postgres psql -At -c "SELECT datname FROM pg_database ORDER BY 1;"
N=$(sudo -u postgres psql -At -c "SELECT COUNT(*) FROM pg_database WHERE datname='recetas_pitr_drill';")
test "$N" = "0" || { echo "ABORTAR: la base desechable sigue ahi"; exit 1; }
SOCKETS=$(ss -ltnH 2>/dev/null || true)
case "$SOCKETS" in *:5433*) echo "ABORTAR: sigue habiendo algo en 5433"; exit 1 ;; esac
echo "5433 libre"
df -h --output=avail,pcent / | sed -n '$p'
rm -f /root/pitr-drill.env /root/pitr-drill-lib.sh /root/pitr-drill-cleanup.sh /root/counts_prod.txt
echo "limpieza verificada"
REMOTE
```

- [ ] **Paso 3: Verificar que el backend sigue sano**

```powershell
Invoke-RestMethod https://recetas.167.233.213.242.sslip.io/api/v1/health
```

---

### Task 8: Retirar el peer WireGuard `10.10.0.2`

**Ficheros:** modifica `/etc/wireguard/wg0.conf` en el VPS.

**Interfaces:** independiente de las tareas 1-7. **No ejecutar en paralelo** con el ensayo.

**Contexto:** el peer `10.10.0.2` figura en la documentacion antigua como "peer PC", pero el PC del
usuario usa `10.10.0.3`. `10.10.0.2` lleva 11+ dias con `latest-handshake = 0` y sin endpoint. En la
sesion del 31/07 el clasificador de permisos del agente bloqueo esta modificacion dos veces; si
vuelve a ocurrir, el usuario ejecuta el comando con el prefijo `!` en el prompt.

**Por que nunca `restart` ni `reload`:** un restart de `wg-quick@wg0` **derriba la interfaz** y corta
el tunel del PC. Un reload no aporta nada una vez que el cambio ya se aplico en vivo y el fichero se
instalo. El cambio en caliente se hace con **`wg set wg0 peer <pubkey> remove`**, que toca
exactamente un peer — `wg syncconf` reconcilia la interfaz entera y aplicaria tambien cualquier otra
diferencia del fichero.

**Donde vive el candidato — y por que NO en `/run`:** `wg-quick` exige que el fichero que recibe
tenga por basename un nombre de interfaz valido (<=15 caracteres) seguido de `.conf`, asi que
`wg0.conf.candidate` queda descartado. Pero ademas, **este VPS tiene un perfil AppArmor `wg-quick`
en modo enforce** que le impide abrir configuraciones fuera de `/etc/wireguard/`: colocar el
candidato en `/run` o en `/root` hace que `wg-quick strip` falle con
`line 40: ...: Permission denied` aunque se ejecute como root y el fichero sea legible.
Comprobado el 2026-08-01 reproduciendolo en ambas rutas con el propio `wg0.conf` copiado.

Por eso el candidato es `/etc/wireguard/wgcand.conf`. Ventaja adicional: esta en el **mismo
filesystem** que el destino, asi que el `mv -T` final es realmente atomico.

- [ ] **Paso 1: Copia de seguridad ANTES de cualquier inspeccion o edicion**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
umask 077
TS=$(date +%Y%m%d-%H%M%S)
cp -a /etc/wireguard/wg0.conf "/etc/wireguard/wg0.conf.bak-$TS"
wg showconf wg0 > "/etc/wireguard/wg0.live.bak-$TS"
chmod 600 "/etc/wireguard/wg0.conf.bak-$TS" "/etc/wireguard/wg0.live.bak-$TS"
test -s "/etc/wireguard/wg0.conf.bak-$TS" || { echo "ABORTAR: backup del fichero vacio"; exit 1; }
test -s "/etc/wireguard/wg0.live.bak-$TS" || { echo "ABORTAR: backup del estado vivo vacio"; exit 1; }
printf 'BACKUP_TS=%s\n' "$TS" > /root/wg-change.env
chmod 600 /root/wg-change.env
ls -l /etc/wireguard/wg0.conf.bak-$TS /etc/wireguard/wg0.live.bak-$TS
echo "BACKUP_TS=$TS"
REMOTE
```

Se guardan **las dos cosas**: el fichero y la configuracion viva (`wg showconf`, formato nativo de
`wg setconf`). El rollback restaura primero el estado vivo y despues el fichero. `BACKUP_TS` se
persiste en `/root/wg-change.env`: los pasos siguientes lo leen de ahi, no de una transcripcion
manual (convencion 7).

- [ ] **Paso 2: Comprobar `SaveConfig`, la unidad y la identidad real de los peers**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
echo "=== SaveConfig ==="
if grep -qiE '^[[:space:]]*SaveConfig[[:space:]]*=[[:space:]]*true' /etc/wireguard/wg0.conf; then
  echo "SaveConfig=true: el servicio reescribe wg0.conf al pararse. NO reiniciar la unidad."
else
  echo "SaveConfig no activo"
fi
echo "=== unidad ==="
systemctl is-active wg-quick@wg0
systemctl show wg-quick@wg0 -p CanReload --value
echo "=== estado vivo (sin claves privadas) ==="
wg show wg0 peers
wg show wg0 latest-handshakes
wg show wg0 endpoints
wg show wg0 allowed-ips
echo "=== fichero, claves redactadas ==="
sed -E 's/^([[:space:]]*(PrivateKey|PresharedKey)[[:space:]]*=).*/\1 <redacted>/I' /etc/wireguard/wg0.conf | grep -n '' || true
REMOTE
```

Confirmar, **sin fiarse de la documentacion**:

- Un peer con `10.10.0.3/32` **con** handshake reciente: es el PC. No tocar.
- Un peer con `10.10.0.2/32` **sin** endpoint y con handshake `0` o muy antiguo: es el que se
  retira. Anotar su clave publica exacta como `PEER_PUBKEY`. **Ya no hacen falta numeros de linea:**
  el paso 3 localiza el bloque por esa clave.
- `CanReload` es informativo: el plan no hace reload ni restart. Se registra solo para documentar el
  estado de la unidad.

Si `SaveConfig` fuese `true`, **no se cambia**: con `syncconf` y rollback del estado vivo no hace
falta tocarlo, y modificarlo seria un cambio de configuracion no solicitado. Solo condiciona que la
unidad no se pare.

- [ ] **Paso 3: Eliminar el bloque por clave publica, no por numero de linea**

Un rango `sed '<N1>,<N2>d'` leido a ojo puede llevarse de paso el `PresharedKey` del peer valido o
una linea de `[Interface]` (`Address`, `ListenPort`, `PostUp`). El fichero seguiria siendo
sintacticamente valido, `wg-quick strip` lo aceptaria, la interfaz viva seguiria funcionando — y el
daño aparecerian en el **siguiente reinicio del VPS**, cuando se levante una configuracion
incompleta. `wg-quick strip` prueba que el fichero parsea, no que sea equivalente.

Por eso el bloque se elimina con un parser de secciones anclado en la `PublicKey`, y despues se
afirma que **todas las lineas ajenas al bloque objetivo siguen identicas**.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
umask 077
PEER_PUBKEY='<PEER_PUBKEY>'
CDIR=/run/recetas-wg-candidate
trap 'rm -rf "$CDIR"' EXIT          # las PSK y la clave privada no sobreviven a un aborto
rm -rf "$CDIR"; mkdir -p "$CDIR"; chmod 700 "$CDIR"
CAND="$CDIR/wg0.conf"

# Elimina el bloque [Peer] cuya PublicKey coincide exactamente. Acumula cada seccion y la vuelca
# solo si no es la buscada; asi nunca corta una seccion a medias.
awk -v target="$PEER_PUBKEY" '
  function flush() {
    if (n > 0 && !(sec == "peer" && found)) { for (i = 1; i <= n; i++) print buf[i] }
    n = 0; found = 0
  }
  /^[[:space:]]*\[/ { flush(); sec = (tolower($0) ~ /\[peer\]/) ? "peer" : "other" }
  {
    buf[++n] = $0
    if (sec == "peer") {
      line = $0; sub(/#.*/, "", line)
      if (line ~ /^[[:space:]]*PublicKey[[:space:]]*=/) {
        val = line; sub(/^[^=]*=[[:space:]]*/, "", val); gsub(/[[:space:]]+$/, "", val)
        if (val == target) found = 1
      }
    }
  }
  END { flush() }
' /etc/wireguard/wg0.conf > "$CAND"
chmod 600 "$CAND"

echo "=== el diff debe ser SOLO eliminaciones, todas del bloque objetivo ==="
RED='s/^([[:space:]]*(PrivateKey|PresharedKey)[[:space:]]*=).*/\1 <redacted-\2>/I'
set +e
diff <(sed -E "$RED" /etc/wireguard/wg0.conf) <(sed -E "$RED" "$CAND") > "$CDIR/diff.txt"
DRC=$?
set -e
case "$DRC" in
  1) cat "$CDIR/diff.txt" ;;
  0) echo "ABORTAR: el candidato es identico: no se elimino ningun peer"; exit 1 ;;
  *) echo "ABORTAR: diff fallo con codigo $DRC"; exit 1 ;;
esac

# Ninguna linea puede APARECER (>) ni cambiar: solo pueden desaparecer (<).
LINEAS_NUEVAS=$(sed -n '/^> /p' "$CDIR/diff.txt" || true)
test -z "$LINEAS_NUEVAS" || { echo "ABORTAR: el candidato añade o modifica lineas:"; echo "$LINEAS_NUEVAS"; exit 1; }

echo "=== comprobaciones sobre el candidato ==="
CONT=$(cat "$CAND")
case "$CONT" in *10.10.0.2/32*) echo "ABORTAR: sigue habiendo 10.10.0.2"; exit 1 ;; esac
case "$CONT" in *"$PEER_PUBKEY"*) echo "ABORTAR: sigue estando la clave publica del peer a retirar"; exit 1 ;; esac
case "$CONT" in *10.10.0.3/32*) : ;; *) echo "ABORTAR: el candidato ha perdido el peer 10.10.0.3"; exit 1 ;; esac
A=$(grep -c '^\[Peer\]' /etc/wireguard/wg0.conf || true)
B=$(grep -c '^\[Peer\]' "$CAND" || true)
echo "peers antes=$A despues=$B"
test "$B" -eq "$((A - 1))" || { echo "ABORTAR: se ha eliminado un numero de peers distinto de 1"; exit 1; }
I1=$(grep -c '^\[Interface\]' /etc/wireguard/wg0.conf || true)
I2=$(grep -c '^\[Interface\]' "$CAND" || true)
test "$I1" = "$I2" || { echo "ABORTAR: la seccion [Interface] ha cambiado"; exit 1; }

wg-quick strip "$CAND" > /dev/null
echo "candidato valido; diff guardado en $CDIR/diff.txt"
trap - EXIT                          # el candidato debe sobrevivir al paso 4
REMOTE
```

La comprobacion decisiva es `LINEAS_NUEVAS`: si el diff solo contiene lineas `<`, ninguna linea
ajena puede haberse modificado. Se comprueban ademas la IP **y** la clave publica exacta — dos peers
podrian compartir `AllowedIPs` — y que `[Interface]` sigue intacta.

`grep -c` lleva `|| true` porque devuelve 1 cuando el recuento es 0 (convencion 2), y las busquedas
de contenido usan `case` sobre una variable en vez de `if ... | grep -q` (convencion 8).

- [ ] **Paso 4: Retirar el peer en vivo e instalar el fichero de forma atomica**

Dos cambios respecto a la version anterior:

- **`wg set ... remove` en vez de `wg syncconf`.** `syncconf` reconcilia la interfaz entera: si el
  fichero difiere en algo mas del peer objetivo, aplica tambien esa diferencia. `wg set peer remove`
  toca exactamente un peer, y el peer valido ni se entera.
- **`mv -T` en vez de `install`.** `install` copia sobre el destino: una interrupcion a mitad deja
  `wg0.conf` truncado, con la interfaz viva correcta y el fichero roto — un fallo que no aparece
  hasta el siguiente arranque. Con temporal en el **mismo filesystem** + `mv`, o esta el fichero
  entero antiguo o el entero nuevo.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/wg-change.env
PEER_PUBKEY='<PEER_PUBKEY>'
CDIR=/run/recetas-wg-candidate
test -f "$CDIR/wg0.conf" || { echo "ABORTAR: no existe el candidato del paso 3"; exit 1; }

# Si algo falla despues de mutar la interfaz, restaurar estado vivo y fichero antes de salir.
rollback() {
  echo "ROLLBACK: restaurando estado vivo y fichero"
  wg syncconf wg0 "/etc/wireguard/wg0.live.bak-$BACKUP_TS" || echo "ERROR: fallo el syncconf de rollback"
  cp -a "/etc/wireguard/wg0.conf.bak-$BACKUP_TS" /etc/wireguard/wg0.conf || echo "ERROR: fallo el cp de rollback"
  wg show wg0 allowed-ips
}
trap rollback ERR

wg set wg0 peer "$PEER_PUBKEY" remove

TMP=$(mktemp /etc/wireguard/.wg0.conf.new-XXXXXX)   # mismo filesystem que el destino
cat "$CDIR/wg0.conf" > "$TMP"
chmod 600 "$TMP"; chown root:root "$TMP"
sync "$TMP"
mv -T "$TMP" /etc/wireguard/wg0.conf
sync /etc/wireguard 2>/dev/null || true

trap - ERR
rm -rf "$CDIR"
wg show wg0 allowed-ips
REMOTE
```

- [ ] **Paso 5: Verificar desde el PC que el tunel sigue vivo**

```powershell
Test-NetConnection 10.10.0.1 -Port 5432 -InformationLevel Quiet
```

Esperado: `True`. Si sale `False`, rollback inmediato — **primero el estado vivo, despues el
fichero**, sin reiniciar el servicio. `wg0.live.bak-*` viene de `wg showconf`, que ya esta en formato
nativo: **no** pasa por `wg-quick strip`.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
. /root/wg-change.env
wg syncconf wg0 "/etc/wireguard/wg0.live.bak-$BACKUP_TS"
cp -a "/etc/wireguard/wg0.conf.bak-$BACKUP_TS" /etc/wireguard/wg0.conf
wg show wg0 allowed-ips
REMOTE
```

- [ ] **Paso 6: Afirmar el estado final, en la interfaz Y en el fichero, sin reload ni restart**

El paso 4 ya aplico el cambio a la interfaz viva y dejo el fichero instalado atomicamente. Un
`reload` no aportaria nada y un `restart` derribaria el tunel: **no se hace ninguno de los dos**. Lo
que si hay que demostrar es que **ambos** estan correctos — la version anterior imprimia
`wg show` pero solo comprobaba el fichero, y luego afirmaba "fuera del fichero y de la interfaz".

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
PEER_PUBKEY='<PEER_PUBKEY>'

# --- interfaz viva ---
VIVO=$(wg show wg0 allowed-ips)
echo "$VIVO"
case "$VIVO" in *"$PEER_PUBKEY"*) echo "ABORTAR: la clave del peer retirado sigue en la interfaz"; exit 1 ;; esac
case "$VIVO" in *10.10.0.2/32*)   echo "ABORTAR: 10.10.0.2 sigue en la interfaz"; exit 1 ;; esac
case "$VIVO" in *10.10.0.3/32*) : ;; *) echo "ABORTAR: el peer 10.10.0.3 ha desaparecido de la interfaz"; exit 1 ;; esac

# --- fichero persistente ---
FICH=$(cat /etc/wireguard/wg0.conf)
case "$FICH" in *"$PEER_PUBKEY"*) echo "ABORTAR: la clave del peer retirado sigue en el fichero"; exit 1 ;; esac
case "$FICH" in *10.10.0.2/32*)   echo "ABORTAR: 10.10.0.2 sigue en el fichero"; exit 1 ;; esac
case "$FICH" in *10.10.0.3/32*) : ;; *) echo "ABORTAR: el peer 10.10.0.3 falta en el fichero"; exit 1 ;; esac
wg-quick strip /etc/wireguard/wg0.conf > /dev/null

# --- el fichero instalado debe coincidir con lo que ve la interfaz ---
HANDSHAKE=$(wg show wg0 latest-handshakes)
echo "$HANDSHAKE"
systemctl is-active wg-quick@wg0
echo "10.10.0.2 retirado de la interfaz Y del fichero; 10.10.0.3 intacto en ambos"
REMOTE
```

`wg-quick strip` sobre el fichero ya instalado confirma que un arranque futuro lo parseara. Repetir
el `Test-NetConnection` del paso 5 despues de este paso.

- [ ] **Paso 7: Limpiar el rastro del cambio**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
rm -rf /run/recetas-wg-candidate
rm -f /root/wg-change.env
ls -l /etc/wireguard/
REMOTE
```

Los backups `wg0.conf.bak-*` y `wg0.live.bak-*` **se conservan**: son la unica via de vuelta si el
peer retirado resulta ser necesario.

> El acceso SSH publico al VPS es independiente del tunel WireGuard, asi que un error en la VPN no
> deja el servidor incomunicado. Aun asi, mantener la sesion SSH **ya abierta** durante los pasos
> 4-6.

---

### Task 9: Documentacion, seguridad y cierre

**Ficheros:**
- Modificar: `docs/postgres-operacion-runbook.md` (secciones `PITR` y `Riesgos Residuales`)
- Modificar: `CONTINUAR.md` (nueva entrada de trazabilidad)
- Añadir: este plan

- [ ] **Paso 1: Actualizar el runbook**

- En `PITR`, añadir el procedimiento **partiendo del offsite** con los comandos ejecutados y las
  trampas nuevas: neutralizar `postgresql.auto.conf`, verificar la configuracion efectiva con
  `postgres -C` por igualdad literal, exigir `pg_wal` vacio y no-symlink, y capturar el objetivo de
  recuperacion en una transaccion posterior al commit del marcador.
- En `Riesgos Residuales`, sustituir el primer punto por el resultado real. **Si el ensayo fallo,
  describir el fallo; no borrar el riesgo.**
- Documentar el patron de la base desechable `recetas_pitr_drill`.
- Si `postgresql.auto.conf` de produccion tenia parametros, anotar sus nombres.

- [ ] **Paso 2: Revision de coherencia documental (suple a Gemini, no disponible)**

Releer el runbook contra `infra/postgres/README.md` y la seccion 8 de `CONTINUAR.md` buscando
contradicciones de rutas, permisos, horarios y nombres de unidades. Dejar constancia de que la hizo
Claude Code y no Gemini.

- [ ] **Paso 3: Escaneo de seguridad obligatorio de sprint**

```powershell
pwsh -NoProfile -File scripts/security/run-security-scan.ps1
```

Documentar modo, hallazgos por severidad, secretos verificados y codigo de salida.
Exit 0 = limpio; 1 = bloqueante; 2 = herramienta no disponible.

**`/VibeSec` y `/security-review`:** este sprint no toca codigo de aplicacion, auth, ownership ni
endpoints — mismo criterio documentado en el sprint del 31/07. Si se confirma que no aplican,
justificarlo por escrito; no marcarlo como PASS.

- [ ] **Paso 4: Entrada de trazabilidad en `CONTINUAR.md`**

Sin inventar nada: agente lider, skills usadas, las **dos rondas** de revision de Codex y que se
acepto o rechazo de cada una, indisponibilidad de Gemini, resultado literal del ensayo, estado del
peer VPN, comandos ejecutados, hallazgos del escaneo, riesgo residual abierto.

- [ ] **Paso 5: Commit**

```bash
git add docs/postgres-operacion-runbook.md CONTINUAR.md docs/superpowers/plans/2026-08-01-pitr-offsite-y-peer-vpn.md
git commit -m "docs(infra): ensaya PITR desde el repositorio offsite y retira el peer VPN obsoleto"
```

- [ ] **Paso 6: Checklist de cierre de `CLAUDE.md`**

Recorrer el protocolo punto por punto. Lo no ejecutado se marca `no aplica` con motivo, o
`bloqueado` con riesgo residual. Nada se marca `PASS` sin ejecucion real en la sesion.

---

## Fuera de alcance

### Hallazgos de la ronda 3 no incorporados

**1. Parser con allowlist, `flock` y comprobacion de dueño/modo para el fichero de estado.**
Codex señala que `. "$STATE_FILE"` ejecuta contenido arbitrario y que sin `flock` puede haber
carreras. Se incorpora la parte barata y con impacto real — escritura atomica por temporal +
`mv`, validacion canonica de rutas, y que un estado ausente ya no produzca `exit 0` optimista — pero
**no** el parser con allowlist ni el `flock`.

Motivo: el fichero lo crea root con modo 600 en un VPS de un solo operador, y el plan se ejecuta
paso a paso de forma interactiva, sin concurrencia. Para que el contenido fuese hostil haria falta
un atacante que ya escribe en `/root` como root, es decir, que ya controla la maquina — momento en
el que el parser no protege de nada. `CLAUDE.md` exige YAGNI y minima complejidad: añadir un
analizador sintactico y bloqueo de ficheros a un script de limpieza de un ensayo desechable es
complejidad que habria que mantener sin reducir riesgo real. **Riesgo residual aceptado y anotado.**

**2. Sandbox de red a nivel de sistema operativo (`systemd-run --property=PrivateNetwork=yes`).**
Se incorporan las guardas de configuracion — descartar suscripciones logicas en el preflight,
`max_logical_replication_workers = 0` y `shared_preload_libraries = ''` afirmados por igualdad
literal, y comprobacion de conexiones salientes del proceso — pero no el aislamiento de red por
namespace.

Motivo: meter el cluster de ensayo en un namespace transitorio complica el arranque, el acceso por
socket, la limpieza y el propio diagnostico si algo falla, y aporta sobre las guardas ya presentes
solo en el escenario de un GUC no contemplado. Codex tiene razon en que "no puede abrir red" pasa a
ser convencion de GUCs y no garantia del SO: **queda como riesgo residual explicito**, no como
problema resuelto.

### Rediseño del `archive_command` de produccion

El comando actual es:

```
test ! -f /var/backups/recetas-postgres/wal/%f && cp %p /var/backups/recetas-postgres/wal/%f && sync /var/backups/recetas-postgres/wal/%f
```

Codex sostiene, citando la documentacion de PostgreSQL 18, que ante un rearchivado el comando debe
devolver 0 si el destino preexistente es identico y durable, y fallo solo si difiere. Escenario que
describe: el comando copia y sincroniza; PostgreSQL cae antes de registrar duraderamente el exito;
al reiniciar reintenta el mismo segmento; `test ! -f` devuelve 1 para siempre, el archiver se
atasca y `pg_wal` empieza a acumularse.

**No se ha verificado esa cita contra la documentacion en esta sesion**, asi que aqui queda
registrada como afirmacion de Codex, no como hecho comprobado. Lo que si consta: el archivado
funciona hoy (`failed_count = 0`, 365 segmentos archivados en la verificacion del 31/07).

Queda fuera de este sprint por separacion de riesgos — es un cambio en el camino de archivado de
produccion, ajeno al objetivo del ensayo, y tocarlo aqui mezclaria dos diagnosticos. Se anota como
**defecto latente candidato a sprint propio**, con la verificacion documental como primer paso.
