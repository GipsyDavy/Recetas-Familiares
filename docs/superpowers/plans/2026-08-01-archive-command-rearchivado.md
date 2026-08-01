# `archive_command` correcto ante rearchivado — Plan de implementacion (v2)

> **Para trabajadores agenticos:** SUB-SKILL REQUERIDA: `superpowers:executing-plans`. Los pasos
> usan checkbox (`- [ ]`). Sin subagentes: sesion SSH unica contra produccion.

**Goal:** sustituir el `archive_command` de produccion por uno que cumpla el contrato de PostgreSQL
ante un fichero preexistente — cero si el contenido es identico **y persistido**, distinto de cero
si difiere — sin dejar de ser fail-closed.

**Architecture:** script versionado en `infra/postgres/`, desplegado en `/usr/local/sbin/`, que
copia a un temporal en el mismo directorio, hace `fsync` del fichero, publica con `link(2)` (falla
si el destino existe, nunca sobrescribe) y hace `fsync` del directorio. Ante un destino preexistente
compara contenidos y decide el codigo de salida. Codigos de salida distintos por causa, para que las
pruebas puedan afirmar el motivo exacto y no solo "fallo".

**Tech Stack:** PostgreSQL 18.4 (Debian layout), **Python 3**, systemd. Sin codigo de aplicacion.

## Cambio de lenguaje decidido durante la ejecucion (2026-08-01)

El script se escribio primero en bash. El preflight revelo que **este servidor no tiene GNU
coreutils**: Ubuntu 26.04 usa `rust-coreutils 0.8.0` (uutils), y el paquete `coreutils` es solo un
meta-paquete. Comprobado con `strace`, su `sync FICHERO` **no hace `fsync(2)`** — abre el fichero y
llama a `sync()` global — y devolvio codigo 0 sincronizando un fichero que el usuario ni siquiera
podia abrir. Todo el diseño de durabilidad se apoyaba en que `sync FICHERO` hiciera `fsync`, tanto
en mi plan como en la revision de Codex, que citaba documentacion de **GNU**.

No hay forma de cumplir el contrato de durabilidad de manera verificable con bash + uutils, y
cambiar el userland del servidor por esto seria desproporcionado. Python 3 expone `os.fsync(2)` real
sobre fichero y sobre directorio y propaga los errores (`OSError [Errno 13]` verificado). Se
reescribio el script en Python 3, conservando intactos el contrato, los codigos de salida y la
bateria de pruebas.

`os.link()` sustituye a `ln -T`: misma garantia (`FileExistsError` ante destino existente de
cualquier tipo) sin depender de una opcion de `ln`. Nota: se comprobo en este VPS que `ln` **sin**
`-T` sobre un destino que sea un directorio devuelve **exit 0** publicando dentro — el bloqueante
que Codex habia señalado, confirmado empiricamente.

Riesgo asumido: dependencia de `python3` en el camino de archivado. Es parte de Ubuntu base y el
fallo seria fail-closed (PostgreSQL reintenta, no se pierde WAL).

## Revision externa incorporada

Codex reviso la v1 (solo lectura, sin acceso al VPS): **6 bloqueantes, 5 importantes, 2 menores.
Incorporados todos.** Ninguno rechazado.

Los dos de mayor impacto:

1. **`ln` sin `-T` podia devolver 0 sin publicar el segmento.** Si durante la carrera aparece un
   directorio (o symlink a directorio) con el nombre del destino, `ln SRC DEST` crea el enlace
   **dentro** de ese directorio y sale 0. PostgreSQL marcaria el segmento como archivado sin estarlo.
2. **La verificacion de la v1 pasaba igual con el comando antiguo.** Archivar un destino nuevo es
   algo que el comando viejo tambien hace, y el "rearchivado" se probaba invocando el script a mano,
   no a traves del archiver. No distinguia activado de no activado.

Y uno que no se habia considerado: **el rollback al comando antiguo puede atascar el archiver por el
mismo defecto que este sprint corrige**, si el script nuevo llego a publicar un destino y devolvio
error despues. La Tarea 7 inspecciona los `.ready` antes de revertir.

Confirmaciones utiles de la revision, que evitan trabajo innecesario: comparar contra `%p` es
correcto (`%p` es el segmento completado que PostgreSQL pide archivar); `cat > TMP` si detecta
fallos de escritura y el `fsync` posterior cubre los diferidos, de modo que un checksum adicional no
aporta durabilidad; el temporal huerfano por `SIGKILL` es ruido y no corrupcion — tras `ln` el
temporal y el destino son hard links al mismo inodo, asi que borrar el nombre temporal no elimina el
segmento. **No** se añade barrido al `archive_command`.

## Por que este cambio

Comando actual (`/etc/postgresql/18/main/conf.d/recetas-archive.conf`):

```
archive_command = 'test ! -f /var/backups/recetas-postgres/wal/%f && cp %p /var/backups/recetas-postgres/wal/%f && sync /var/backups/recetas-postgres/wal/%f'
```

Si el destino existe, `test ! -f` devuelve 1 y el comando falla **siempre**, aunque el contenido sea
identico.

Documentacion de PostgreSQL 18, §25.3.1, verificada el 2026-08-01:

> "In rare cases, PostgreSQL may attempt to re-archive a WAL file that was previously archived. For
> example, if the system crashes before the server makes a durable record of archival success, the
> server will attempt to archive the file again after restarting (provided archiving is still
> enabled). When an archive command or library encounters a pre-existing file, it should return a
> zero status or `true`, respectively, if the WAL file has identical contents to the pre-existing
> archive and the pre-existing archive is fully persisted to storage. If a pre-existing file
> contains different contents than the WAL file being archived, the archive command or library
> _must_ return a nonzero status or `false`, respectively."

> "The `pg_wal/` directory will continue to fill with WAL segment files until the situation is
> resolved. (If the file system containing `pg_wal/` fills up, PostgreSQL will do a PANIC shutdown.)"

**Nota para el futuro:** el ejemplo canonico de la propia documentacion (`test ! -f ... && cp ...`)
es exactamente el que teniamos, y es **incompleto** respecto a lo que el mismo apartado exige unas
lineas mas abajo. Que un comando aparezca como ejemplo oficial no significa que cumpla el contrato
completo.

**Severidad:** probabilidad baja (requiere una caida entre la copia y el registro durable del exito),
impacto alto (archiver bloqueado indefinidamente, `pg_wal` creciendo, PANIC si se llena el disco).
Hoy no se ha materializado: `failed_count=0`, 367 segmentos archivados.

## Restricciones globales

- **Fail-closed.** Un falso fallo solo provoca reintentos; un falso exito **pierde un segmento de
  WAL y rompe el PITR en silencio**. Ante cualquier duda, salir distinto de cero.
- **Ningun retorno 0 sin durabilidad demostrada.** Todos los caminos de exito pasan por la misma
  funcion, que hace `fsync` del fichero **y del directorio**, sin fallbacks que enmascaren un error.
  `fsync` de un fichero no persiste la entrada de su directorio: hace falta el segundo.
- **Nunca sobrescribir un destino existente.** Publicacion con `ln -T`, que falla con `EEXIST`.
- **`ARCHIVE_DIR` es constante en el script de produccion.** No se lee del entorno: una variable
  heredada accidentalmente por PostgreSQL podria redirigir el archivo en silencio. Las pruebas usan
  una **copia** del script con la constante sustituida.
- El script corre como `postgres` (el usuario del archiver), no como root.
- `archive_command` es `sighup`: `SELECT pg_reload_conf()`. **No reiniciar PostgreSQL.**
- El script se prueba **fuera** del `archive_command` y la bateria **devuelve codigo de salida
  distinto de cero si algun caso falla** — no basta con imprimir `[MAL]`.
- Convenciones de shell del plan anterior vigentes: `set -euo pipefail`, nada de `if cmd | grep -q`,
  nada de `VAR=$(... | grep | wc -l)`.

## Codigos de salida del script

Distintos por causa, para que las pruebas afirmen el motivo exacto y no aprueben por un fallo ajeno
(script inexistente, AppArmor, `mktemp` roto).

| Codigo | Significado |
|---|---|
| 0 | archivado (o rearchivado identico) y persistido |
| 1 | uso incorrecto: argumentos o nombre de destino invalido |
| 2 | **conflicto**: el destino existe con contenido distinto |
| 3 | error copiando el origen al temporal |
| 4 | error de durabilidad: `fsync` de fichero o directorio fallo |
| 5 | error de entorno: directorio ausente, origen ausente, destino no regular |

---

### Task 1: Preflight

- [ ] **Paso 1: Estado actual del archivado y del entorno**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
echo "=== configuracion efectiva ==="
sudo -u postgres psql -At -c "SELECT name||' = '||setting FROM pg_settings WHERE name IN ('archive_mode','archive_command','archive_timeout','archive_library') ORDER BY name;"
echo "=== estado del archiver ==="
sudo -u postgres psql -At -c "SELECT 'archived='||archived_count||' failed='||failed_count||' last='||COALESCE(last_archived_wal,'(ninguno)')||' last_failed='||COALESCE(last_failed_wal,'(ninguno)') FROM pg_stat_archiver;"
echo "=== usuario del archiver ==="
ps -o user= -p "$(head -1 /var/lib/postgresql/18/main/postmaster.pid)"
echo "=== destino y filesystem ==="
ls -ld /var/backups/recetas-postgres/wal
findmnt -no SOURCE,TARGET,FSTYPE -T /var/backups/recetas-postgres/wal
findmnt -no SOURCE,TARGET,FSTYPE -T /var/tmp
echo "=== .ready pendientes (deberia ser 0 o muy pocos) ==="
find /var/lib/postgresql/18/main/pg_wal/archive_status -name '*.ready' -printf '%f\n' | head
echo "=== configuracion en disco ==="
cat /etc/postgresql/18/main/conf.d/recetas-archive.conf
REMOTE
```

Anotar `archived_count`, `failed_count` y el **fstype** del directorio de WAL. Si `/var/tmp` es de
otro tipo de filesystem, las pruebas de la Tarea 3 se ejecutan en un directorio dentro de
`/var/backups` para compartir semantica.

---

### Task 2: Escribir el script

**Ficheros:** Crear `infra/postgres/recetas-postgres-archive-wal`

- [ ] **Paso 1: Crear el script**

```bash
#!/usr/bin/env bash
# archive_command de Recetas Familiares.
#
# Contrato de PostgreSQL (§25.3.1, verificado contra la doc de la 18 el 2026-08-01):
#   - No sobrescribir nunca un fichero de archivo preexistente.
#   - Si existe y el contenido es IDENTICO y esta persistido -> salir 0. El rearchivado tras una
#     caida es legitimo y debe poder avanzar.
#   - Si existe y el contenido DIFIERE -> salir distinto de 0, obligatoriamente.
#   - Ante cualquier otro problema -> salir distinto de 0 (fail-closed): PostgreSQL reintentara.
#
# El comando anterior era 'test ! -f DEST && cp %p DEST && sync DEST', que devolvia 1 SIEMPRE que
# el destino existiera, aunque fuese identico: podia atascar el archiver de forma permanente tras
# una caida, con pg_wal creciendo hasta un PANIC por disco lleno.
#
# Codigos: 0 ok | 1 uso | 2 conflicto de contenido | 3 error de copia | 4 error de durabilidad
#          5 error de entorno
#
# Uso: recetas-postgres-archive-wal <ruta_origen (%p)> <nombre_destino (%f)>
set -euo pipefail

# Constante deliberada: NO se lee del entorno. Una variable heredada por el proceso de PostgreSQL
# podria redirigir el archivo en silencio. Las pruebas usan una copia con esta linea sustituida.
ARCHIVE_DIR=/var/backups/recetas-postgres/wal

err() { logger -t recetas-archive-wal -p daemon.err -- "$*" 2>/dev/null || true; echo "recetas-archive-wal: $*" >&2; }
info() { logger -t recetas-archive-wal -p daemon.info -- "$*" 2>/dev/null || true; }

test "$#" -eq 2 || { err "uso: $0 <origen> <nombre>"; exit 1; }
SRC="$1"
NAME="$2"

case "$NAME" in
  */*|.|..|"") err "nombre de destino invalido: '$NAME'"; exit 1 ;;
esac
test -d "$ARCHIVE_DIR" || { err "el directorio de archivo no existe: $ARCHIVE_DIR"; exit 5; }
test -f "$SRC" || { err "el origen no existe o no es un fichero regular: $SRC"; exit 5; }

DEST="$ARCHIVE_DIR/$NAME"

# Unico camino de exito. Sin fallbacks: `sync FICHERO` usa fsync(2); un `sync` global puede
# devolver exito ofreciendo garantias menores y enmascarar un EIO/ENOSPC del fsync real.
# El fsync del fichero NO persiste la entrada de su directorio: por eso el segundo.
persistir_y_salir() {
  local motivo="$1"
  sync -- "$DEST"         || { err "fsync del fichero fallo: $DEST"; exit 4; }
  sync -- "$ARCHIVE_DIR"  || { err "fsync del directorio fallo: $ARCHIVE_DIR"; exit 4; }
  info "$NAME: $motivo"
  exit 0
}

# --- Caso 1: el destino ya existe (rearchivado) ---
if [ -e "$DEST" ] || [ -L "$DEST" ]; then
  # Un symlink a fichero regular pasaria `-f`: el script compararia y sincronizaria su objetivo,
  # que puede estar fuera del repositorio de WAL. Se rechaza explicitamente.
  [ -L "$DEST" ] && { err "el destino es un symlink: $DEST"; exit 5; }
  [ -f "$DEST" ] || { err "el destino existe y no es un fichero regular: $DEST"; exit 5; }
  if cmp -s -- "$SRC" "$DEST"; then
    persistir_y_salir "rearchivado con contenido identico"
  fi
  err "CONFLICTO: $DEST ya existe con contenido DISTINTO; no se sobrescribe"
  exit 2
fi

# --- Caso 2: destino nuevo ---
TMP="$(mktemp "$ARCHIVE_DIR/.$NAME.XXXXXX")" || { err "no se pudo crear el temporal en $ARCHIVE_DIR"; exit 3; }
trap 'rm -f -- "$TMP"' EXIT

cat -- "$SRC" > "$TMP"  || { err "fallo copiando $SRC a $TMP"; exit 3; }
chmod 600 -- "$TMP"     || { err "fallo ajustando permisos de $TMP"; exit 3; }
sync -- "$TMP"          || { err "fsync del temporal fallo: $TMP"; exit 4; }

# `-T` es obligatorio: sin el, si $DEST apareciese como directorio durante la carrera, `ln` crearia
# el enlace DENTRO y devolveria 0 — el segmento no quedaria publicado y PostgreSQL lo daria por
# archivado. Con -T, si $DEST existe de cualquier forma, `ln` falla.
if ! ln -T -- "$TMP" "$DEST" 2>/dev/null; then
  # Carrera: alguien publico el destino entre la comprobacion inicial y ahora.
  if [ ! -L "$DEST" ] && [ -f "$DEST" ] && cmp -s -- "$SRC" "$DEST"; then
    persistir_y_salir "carrera: archivado en paralelo con contenido identico"
  fi
  err "no se pudo publicar $DEST (destino aparecido con otro contenido o no regular)"
  exit 2
fi

persistir_y_salir "archivado"
```

**Decisiones de diseño y su motivo:**

- **`ln -T` en vez de `mv`.** `rename(2)` **sobrescribe** en silencio si el destino aparece entre la
  comprobacion y la operacion; `ln` falla con `EEXIST`. Y `-T` evita que un directorio homonimo haga
  que `ln` publique dentro de el devolviendo 0.
- **Un unico camino de exito** (`persistir_y_salir`), con `fsync` de fichero y directorio y sin
  fallbacks. En la v1 habia dos retornos 0 sin durabilidad demostrada: la rama de "identico" con un
  `|| sync` que enmascaraba errores, y la de carrera, que devolvia 0 sin sincronizar nada.
- **Symlink rechazado explicitamente** en la rama inicial y en la de carrera.
- **Temporal dentro de `ARCHIVE_DIR`**: `ln` exige el mismo filesystem.
- **`cat > "$TMP"` en vez de `cp`**: solo interesa el contenido, y el destino final se construye
  entero antes de existir con su nombre definitivo.
- **`--` en todos los comandos** para que un `%f` inesperado no se interprete como opcion.
- **`info` en `daemon.info` en cada exito.** Cuesta una linea de syslog por segmento (maximo ~96/dia
  con `archive_timeout=15min`) y es lo que permite **demostrar que el archiver ejecuto este script**
  y no el antiguo — la Tarea 6 se apoya en ello.

- [ ] **Paso 2: Sintaxis y analisis estatico en local**

```bash
bash -n infra/postgres/recetas-postgres-archive-wal
```

---

### Task 3: Bateria de pruebas fuera del `archive_command`

Puerta del sprint. **La bateria termina con `exit` distinto de cero si algun caso falla**: en la v1
imprimia `[MAL]` y salia 0, un verde falso automatizable.

- [ ] **Paso 1: Generar la copia de pruebas del script**

La constante `ARCHIVE_DIR` se sustituye por `sed`; el script de produccion no admite override por
entorno.

```bash
scp infra/postgres/recetas-postgres-archive-wal root@167.233.213.242:/tmp/archive-wal-orig
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
bash -n /tmp/archive-wal-orig
echo "sintaxis OK"
grep -n '^ARCHIVE_DIR=' /tmp/archive-wal-orig
REMOTE
```

- [ ] **Paso 2: Bateria completa, con codigo de salida real**

Se ejecuta dentro de `/var/backups/recetas-postgres` para compartir el filesystem y la semantica del
destino real (`/var/tmp` puede ser de otro tipo).

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -uo pipefail
FAIL=0
BASE=$(mktemp -d /var/backups/recetas-postgres/.archtest-XXXXXX)
trap 'rm -rf "$BASE"; exit $FAIL' EXIT
ARCH="$BASE/arch"; mkdir -p "$ARCH"
SCRIPT="$BASE/archive-wal"
sed "s|^ARCHIVE_DIR=.*|ARCHIVE_DIR=$ARCH|" /tmp/archive-wal-orig > "$SCRIPT"
chmod 0755 "$SCRIPT"
grep -q "^ARCHIVE_DIR=$ARCH\$" "$SCRIPT" || { echo "[MAL] no se sustituyo ARCHIVE_DIR"; FAIL=1; exit 1; }
printf 'contenido-A' > "$BASE/src_a"
printf 'contenido-B' > "$BASE/src_b"
chown -R postgres:postgres "$BASE"
chmod 700 "$BASE" "$ARCH"

correr() { sudo -u postgres "$SCRIPT" "$1" "$2" >/dev/null 2>"$BASE/err.txt"; echo $?; }
comprobar() { # descripcion esperado obtenido
  if [ "$2" = "$3" ]; then echo "  [ok]  $1 (exit $3)"
  else echo "  [MAL] $1 -> exit $3, esperaba $2 | stderr: $(cat "$BASE/err.txt" 2>/dev/null | tail -1)"; FAIL=1; fi
}
afirmar() { # descripcion condicion_ya_evaluada(0/1)
  if [ "$2" = "0" ]; then echo "  [ok]  $1"; else echo "  [MAL] $1"; FAIL=1; fi
}

S=000000010000000000000001

echo "=== 1. destino nuevo -> 0 ==="
comprobar "archiva destino nuevo" 0 "$(correr "$BASE/src_a" "$S")"
test -f "$ARCH/$S"; afirmar "el fichero existe" $?
cmp -s "$BASE/src_a" "$ARCH/$S"; afirmar "contenido correcto" $?
INODE1=$(stat -c %i "$ARCH/$S" 2>/dev/null || echo x)

echo "=== 2. rearchivado identico -> 0 (lo que el comando antiguo NO hacia) ==="
comprobar "rearchivado identico" 0 "$(correr "$BASE/src_a" "$S")"
test "$(stat -c %i "$ARCH/$S")" = "$INODE1"; afirmar "no se recreo el fichero" $?

echo "=== 3. preexistente distinto -> 2 y sin sobrescribir ==="
comprobar "conflicto de contenido" 2 "$(correr "$BASE/src_b" "$S")"
cmp -s "$BASE/src_a" "$ARCH/$S"; afirmar "el destino NO se sobrescribio" $?
grep -q CONFLICTO "$BASE/err.txt"; afirmar "el diagnostico menciona CONFLICTO" $?

echo "=== 4. origen inexistente -> 5 ==="
comprobar "origen ausente" 5 "$(correr "$BASE/no_existe" 000000010000000000000002)"

echo "=== 5. directorio de archivo inexistente -> 5 ==="
sed "s|^ARCHIVE_DIR=.*|ARCHIVE_DIR=$BASE/no_such_dir|" /tmp/archive-wal-orig > "$BASE/s5"; chmod 755 "$BASE/s5"; chown postgres "$BASE/s5"
RC=$(sudo -u postgres "$BASE/s5" "$BASE/src_a" 000000010000000000000003 >/dev/null 2>&1; echo $?)
comprobar "directorio ausente" 5 "$RC"

echo "=== 6. nombre con barra -> 1 y sin escritura fuera ==="
comprobar "nombre invalido" 1 "$(correr "$BASE/src_a" "../evasion")"
test ! -e "$BASE/evasion"; afirmar "no se escribio fuera del directorio" $?

echo "=== 7. destino no escribible -> 3 ==="
chmod 500 "$ARCH"
RC=$(correr "$BASE/src_a" 000000010000000000000004)
chmod 700 "$ARCH"
comprobar "sin permiso de escritura" 3 "$RC"

echo "=== 8. destino es un symlink -> 5 ==="
S8=000000010000000000000005
ln -s "$BASE/src_b" "$ARCH/$S8"
comprobar "destino symlink" 5 "$(correr "$BASE/src_a" "$S8")"
test -L "$ARCH/$S8"; afirmar "el symlink sigue intacto" $?
rm -f "$ARCH/$S8"

echo "=== 9. destino es un directorio (la carrera que -T evita) -> != 0 ==="
S9=000000010000000000000006
mkdir "$ARCH/$S9"
RC=$(correr "$BASE/src_a" "$S9")
test "$RC" -ne 0; afirmar "no devuelve 0 con un directorio en el destino (exit $RC)" $?
test -z "$(ls -A "$ARCH/$S9")"; afirmar "no publico nada DENTRO del directorio" $?
rmdir "$ARCH/$S9"

echo "=== 10. escritura parcial via RLIMIT_FSIZE -> != 0 y sin publicar ==="
S10=000000010000000000000007
head -c 1048576 /dev/urandom > "$BASE/big"; chown postgres "$BASE/big"
RC=$(sudo -u postgres bash -c "ulimit -f 1; exec '$SCRIPT' '$BASE/big' '$S10'" >/dev/null 2>&1; echo $?)
test "$RC" -ne 0; afirmar "escritura truncada no devuelve 0 (exit $RC)" $?
test ! -e "$ARCH/$S10"; afirmar "no se publico un segmento truncado" $?

echo "=== 11. sin temporales huerfanos ==="
HUERF=$(find "$ARCH" -maxdepth 1 -name '.*' -printf '%f\n')
test -z "$HUERF"; afirmar "sin temporales huerfanos ($HUERF)" $?

echo
test "$FAIL" -eq 0 && echo "BATERIA COMPLETA: OK" || echo "BATERIA COMPLETA: HAY FALLOS"
REMOTE
```

**Puerta:** el bloque devuelve distinto de cero si `FAIL=1`. Si falla, corregir el script en el
repositorio, volver al Paso 1 y repetir. **No continuar.**

**No cubierto y documentado como tal:** `ENOSPC` real y `EIO` en el filesystem de produccion. No se
provocan deliberadamente en el VPS. El caso 10 cubre la escritura truncada mediante `RLIMIT_FSIZE`
aplicado solo al proceso de prueba, que es seguro; los fallos de `fsync` por error de dispositivo
quedan como riesgo residual no ensayado.

---

### Task 4: Instalar el script

- [ ] **Paso 1: Copia de seguridad de la configuracion, con su ruta persistida**

`BACKUP_TS` no se transcribe a mano (convencion 7 del plan anterior).

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
TS=$(date +%Y%m%d-%H%M%S)
C=/etc/postgresql/18/main/conf.d/recetas-archive.conf
BAK="$C.bak-$TS"
cp -a "$C" "$BAK"
sync -- "$BAK"
sha256sum "$BAK" | cut -d' ' -f1 > /root/archive-rollback.sha
printf 'BACKUP_CONF=%s\n' "$BAK" > /root/archive-rollback.env
chmod 600 /root/archive-rollback.env /root/archive-rollback.sha
cat /root/archive-rollback.env
cat "$BAK"
REMOTE
```

- [ ] **Paso 2: Instalar el script de forma atomica y verificar su identidad**

`scp` directo sobre la ruta definitiva puede truncar un script ya activo en un despliegue posterior.
Se sube a un temporal en el mismo directorio y se publica con `mv -T`.

```bash
scp infra/postgres/recetas-postgres-archive-wal root@167.233.213.242:/usr/local/sbin/.recetas-archive-wal.new
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
NEW=/usr/local/sbin/.recetas-archive-wal.new
DST=/usr/local/sbin/recetas-postgres-archive-wal
bash -n "$NEW"
grep -q '^ARCHIVE_DIR=/var/backups/recetas-postgres/wal$' "$NEW" || { echo "ABORTAR: ARCHIVE_DIR no es la constante de produccion"; exit 1; }
chown root:root "$NEW"
chmod 0755 "$NEW"
sync -- "$NEW"
mv -T "$NEW" "$DST"
sync -- /usr/local/sbin
sudo -u postgres test -x "$DST" && echo "ejecutable por postgres: OK"
ls -l "$DST"
sha256sum "$DST"
REMOTE
```

Permisos `0755 root:root`: lo ejecuta `postgres`, que necesita el bit de ejecucion, pero no debe
poder modificarlo. Distinto de los scripts de backup (`0750 root:postgres`), que los lanza systemd
con `User=postgres`.

---

### Task 5: Activar y demostrar que PostgreSQL lo aplico

**`pg_reload_conf()` solo confirma que se envio el SIGHUP.** Una configuracion invalida se ignora y
solo deja rastro en el log. Hay que afirmar la aplicacion real.

- [ ] **Paso 1: Escribir la configuracion nueva de forma atomica**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
C=/etc/postgresql/18/main/conf.d/recetas-archive.conf
TMP=$(mktemp /etc/postgresql/18/main/conf.d/.recetas-archive.XXXXXX)
cat > "$TMP" <<'CONF'
# Managed for Recetas Familiares PostgreSQL operation.
# Enables local WAL archiving for point-in-time recovery with base backups.
archive_mode = on
# El comando vive en un script versionado (infra/postgres/recetas-postgres-archive-wal) porque
# tiene que cumplir el contrato completo de §25.3.1: ante un fichero preexistente devuelve 0 si el
# contenido es identico y esta persistido, y != 0 si difiere. El anterior
# ('test ! -f ... && cp ... && sync ...') fallaba SIEMPRE con el destino presente, y podia atascar
# el archiver de forma permanente tras una caida.
archive_command = '/usr/local/sbin/recetas-postgres-archive-wal %p %f'
archive_timeout = '15min'
CONF
chmod 644 "$TMP"; chown root:root "$TMP"
sync -- "$TMP"
mv -T "$TMP" "$C"
sync -- /etc/postgresql/18/main/conf.d
cat "$C"
REMOTE
```

- [ ] **Paso 2: Recargar y AFIRMAR que se aplico**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
Q() { sudo -u postgres psql -X -At -v ON_ERROR_STOP=1 -c "$1"; }
ANTES=$(Q "SELECT pg_conf_load_time();")

echo "=== errores de sintaxis ANTES de recargar ==="
ERRORES=$(Q "SELECT COALESCE(string_agg(sourcefile||':'||sourceline||' '||COALESCE(error,''), E'\n'), '') FROM pg_file_settings WHERE error IS NOT NULL;")
test -z "$ERRORES" || { echo "ABORTAR: hay errores en la configuracion:"; echo "$ERRORES"; exit 1; }

Q "SELECT pg_reload_conf();" > /dev/null
sleep 3

echo "=== la entrada del fichero esta APLICADA ==="
APPLIED=$(Q "SELECT applied FROM pg_file_settings WHERE name='archive_command' ORDER BY seqno DESC LIMIT 1;")
SRCFILE=$(Q "SELECT sourcefile FROM pg_file_settings WHERE name='archive_command' ORDER BY seqno DESC LIMIT 1;")
echo "applied=$APPLIED sourcefile=$SRCFILE"
test "$APPLIED" = "t" || { echo "ABORTAR: la entrada no consta aplicada"; exit 1; }
case "$SRCFILE" in *recetas-archive.conf) : ;; *) echo "ABORTAR: sourcefile inesperado: $SRCFILE"; exit 1 ;; esac

echo "=== valores efectivos ==="
ESPERADO='/usr/local/sbin/recetas-postgres-archive-wal %p %f'
REAL=$(Q "SELECT setting FROM pg_settings WHERE name='archive_command';")
echo "archive_command = $REAL"
test "$REAL" = "$ESPERADO" || { echo "ABORTAR: archive_command efectivo inesperado"; exit 1; }
test "$(Q "SELECT setting FROM pg_settings WHERE name='archive_mode';")" = "on" || { echo "ABORTAR: archive_mode != on"; exit 1; }
test "$(Q "SELECT setting FROM pg_settings WHERE name='archive_library';")" = "" || { echo "ABORTAR: archive_library no esta vacio"; exit 1; }

DESPUES=$(Q "SELECT pg_conf_load_time();")
echo "conf_load_time: $ANTES -> $DESPUES"
test "$DESPUES" != "$ANTES" || { echo "ABORTAR: pg_conf_load_time no avanzo: la recarga no ocurrio"; exit 1; }
echo "CONFIGURACION APLICADA Y VERIFICADA"
REMOTE
```

---

### Task 6: Verificar que el archivado real pasa por el script nuevo

La v1 no distinguia el comando nuevo del antiguo: archivar un destino nuevo lo hacen los dos. Aqui
la prueba es la entrada en syslog que **solo** el script nuevo escribe, para el segmento exacto.

- [ ] **Paso 1: Generar WAL sin DDL y archivar un segmento controlado**

`CREATE TABLE IF NOT EXISTS` + `DROP TABLE` podia borrar una tabla homonima preexistente.
`pg_create_restore_point()` escribe un marcador en el WAL sin tocar el esquema.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
Q() { sudo -u postgres psql -X -At -v ON_ERROR_STOP=1 -c "$1"; }
MARCA=$(date -u +%Y-%m-%dT%H:%M:%S)
ARCHIVED_ANTES=$(Q "SELECT archived_count FROM pg_stat_archiver;")
FAILED_ANTES=$(Q "SELECT failed_count FROM pg_stat_archiver;")
echo "antes: archived=$ARCHIVED_ANTES failed=$FAILED_ANTES"

Q "SELECT pg_create_restore_point('archive-cmd-check');" > /dev/null
SEG=$(Q "SELECT pg_walfile_name(pg_current_wal_insert_lsn());")
echo "segmento a archivar: $SEG"
Q "SELECT pg_switch_wal();" > /dev/null

OK=0
for i in $(seq 1 36); do
  LAST=$(Q "SELECT COALESCE(last_archived_wal,'') FROM pg_stat_archiver;")
  if [ -n "$LAST" ] && [ ! "$LAST" \< "$SEG" ]; then OK=1; echo "last_archived_wal=$LAST"; break; fi
  sleep 5
done
test "$OK" -eq 1 || { echo "ABORTAR: $SEG no se archivo"; exit 1; }

echo "=== el segmento existe como fichero regular ==="
DEST="/var/backups/recetas-postgres/wal/$SEG"
test -f "$DEST" && test ! -L "$DEST" || { echo "ABORTAR: destino ausente o no regular"; exit 1; }
ls -l "$DEST"

echo "=== el archiver marco .done y no dejo .ready ==="
AS=/var/lib/postgresql/18/main/pg_wal/archive_status
test -f "$AS/$SEG.done" || { echo "ABORTAR: no hay $SEG.done"; exit 1; }
test ! -e "$AS/$SEG.ready" || { echo "ABORTAR: sigue habiendo $SEG.ready"; exit 1; }

echo "=== PRUEBA DECISIVA: la entrada de syslog solo la escribe el script nuevo ==="
ENTRADA=$(journalctl -t recetas-archive-wal --no-pager --since "$MARCA" 2>/dev/null | grep -F "$SEG" || true)
echo "$ENTRADA"
test -n "$ENTRADA" || { echo "ABORTAR: el archiver NO ejecuto el script nuevo para $SEG"; exit 1; }

ARCHIVED_DESPUES=$(Q "SELECT archived_count FROM pg_stat_archiver;")
FAILED_DESPUES=$(Q "SELECT failed_count FROM pg_stat_archiver;")
echo "despues: archived=$ARCHIVED_DESPUES failed=$FAILED_DESPUES"
test "$ARCHIVED_DESPUES" -gt "$ARCHIVED_ANTES" || { echo "ABORTAR: archived_count no subio"; exit 1; }
test "$FAILED_DESPUES" = "$FAILED_ANTES" || { echo "ABORTAR: failed_count subio"; exit 1; }

echo "=== sin errores del archiver en el log de PostgreSQL desde la marca ==="
ERR=$(journalctl -u postgresql@18-main --no-pager --since "$MARCA" 2>/dev/null | grep -iE 'archive command|archiver' || true)
test -z "$ERR" && echo "(ninguno)" || echo "$ERR"
echo "ARCHIVADO REAL VERIFICADO A TRAVES DEL SCRIPT NUEVO"
REMOTE
```

Nota: `failed_count` por si solo no prueba ausencia de fallos — PostgreSQL no contabiliza ahi los
fallos por señal ni ciertos errores de shell. Por eso este paso exige simultaneamente destino
regular, `.done` presente, `.ready` ausente, contador incrementado, entrada de syslog del script y
ausencia de errores en el log.

- [ ] **Paso 2: Rearchivado real, con origen distinto del destino**

En la v1 se pasaba `"$DEST"` como origen: `cmp` puede reconocer el mismo inodo sin leer, asi que era
tautologico. Aqui el origen es una copia independiente.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
SEG=$(sudo -u postgres psql -X -At -c "SELECT last_archived_wal FROM pg_stat_archiver;")
DEST="/var/backups/recetas-postgres/wal/$SEG"
echo "segmento: $SEG"
SUM_ANTES=$(sha256sum "$DEST" | cut -d' ' -f1)
test -n "$SUM_ANTES" || { echo "ABORTAR: no se pudo calcular el hash inicial"; exit 1; }
INODE_ANTES=$(stat -c %i "$DEST")

COPIA=$(mktemp /var/backups/recetas-postgres/.rearch-XXXXXX)
cat -- "$DEST" > "$COPIA"
chown postgres:postgres "$COPIA"
test "$(stat -c %i "$COPIA")" != "$INODE_ANTES" || { echo "ABORTAR: la copia comparte inodo"; exit 1; }

set +e
sudo -u postgres /usr/local/sbin/recetas-postgres-archive-wal "$COPIA" "$SEG" 2>/tmp/rearch.err
RC=$?
set -e
echo "rearchivado identico (origen independiente) -> exit $RC (esperado 0)"
test "$RC" -eq 0 || { echo "ABORTAR: no permite rearchivar contenido identico"; cat /tmp/rearch.err; exit 1; }

DIST=$(mktemp /var/backups/recetas-postgres/.rearch-XXXXXX)
head -c 16777216 /dev/zero > "$DIST"
chown postgres:postgres "$DIST"
set +e
sudo -u postgres /usr/local/sbin/recetas-postgres-archive-wal "$DIST" "$SEG" 2>/tmp/rearch2.err
RC2=$?
set -e
echo "rearchivado con contenido DISTINTO -> exit $RC2 (esperado 2)"
test "$RC2" -eq 2 || { echo "ABORTAR: codigo inesperado"; cat /tmp/rearch2.err; exit 1; }
grep -q CONFLICTO /tmp/rearch2.err || { echo "ABORTAR: falta el diagnostico de conflicto"; exit 1; }

SUM_DESPUES=$(sha256sum "$DEST" | cut -d' ' -f1)
test -n "$SUM_DESPUES" || { echo "ABORTAR: no se pudo calcular el hash final"; exit 1; }
test "$SUM_ANTES" = "$SUM_DESPUES" || { echo "ABORTAR: el segmento archivado fue modificado"; exit 1; }
test "$(stat -c %i "$DEST")" = "$INODE_ANTES" || { echo "ABORTAR: el inodo del destino cambio"; exit 1; }
echo "el segmento archivado no fue modificado: OK"
rm -f "$COPIA" "$DIST" /tmp/rearch.err /tmp/rearch2.err
find /var/backups/recetas-postgres -maxdepth 1 -name '.rearch-*' -printf 'huerfano: %p\n'
REMOTE
```

- [ ] **Paso 3: Backups programados sanos**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
systemctl start recetas-postgres-logical-backup.service
R=$(systemctl show recetas-postgres-logical-backup.service -p Result --value)
S=$(systemctl show recetas-postgres-logical-backup.service -p ExecMainStatus --value)
echo "logical-backup: Result=$R ExecMainStatus=$S"
test "$R" = "success" -a "$S" = "0" || { echo "ABORTAR: el backup logico fallo"; exit 1; }
systemctl is-active postgresql@18-main
systemctl is-active recetas-backend.service
REMOTE
```

Y desde PowerShell: `Invoke-RestMethod https://recetas.167.233.213.242.sslip.io/api/v1/health`

- [ ] **Paso 4: Vigilancia diferida (>=20 min despues)**

Repite la barrera del Paso 1 con un marcador nuevo, en vez de limitarse a imprimir estadisticas.

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
Q() { sudo -u postgres psql -X -At -v ON_ERROR_STOP=1 -c "$1"; }
MARCA=$(date -u +%Y-%m-%dT%H:%M:%S)
Q "SELECT pg_create_restore_point('archive-cmd-recheck');" > /dev/null
SEG=$(Q "SELECT pg_walfile_name(pg_current_wal_insert_lsn());")
Q "SELECT pg_switch_wal();" > /dev/null
OK=0
for i in $(seq 1 36); do
  LAST=$(Q "SELECT COALESCE(last_archived_wal,'') FROM pg_stat_archiver;")
  if [ -n "$LAST" ] && [ ! "$LAST" \< "$SEG" ]; then OK=1; break; fi
  sleep 5
done
test "$OK" -eq 1 || { echo "ABORTAR: $SEG no se archivo en la revision diferida"; exit 1; }
test -n "$(journalctl -t recetas-archive-wal --no-pager --since "$MARCA" 2>/dev/null | grep -F "$SEG" || true)" \
  || { echo "ABORTAR: el archiver no ejecuto el script para $SEG"; exit 1; }
test -f "/var/lib/postgresql/18/main/pg_wal/archive_status/$SEG.done" || { echo "ABORTAR: falta $SEG.done"; exit 1; }
Q "SELECT 'archived='||archived_count||' failed='||failed_count FROM pg_stat_archiver;"
echo "VIGILANCIA DIFERIDA: OK"
REMOTE
```

---

### Task 7: Rollback (solo si hace falta)

**No es un `cp` y ya.** Volver al comando antiguo puede **atascar el archiver por el mismo defecto
que este sprint corrige**: si el script nuevo llego a publicar un destino y devolvio error despues,
el comando antiguo encontrara ese fichero y fallara indefinidamente.

- [ ] **Paso 1: Inspeccionar los `.ready` ANTES de revertir**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
AS=/var/lib/postgresql/18/main/pg_wal/archive_status
W=/var/backups/recetas-postgres/wal
PW=/var/lib/postgresql/18/main/pg_wal
BLOQUEANTES=0
READY=$(find "$AS" -name '*.ready' -printf '%f\n' | sed 's/\.ready$//')
test -n "$READY" || { echo "sin .ready pendientes: el rollback es seguro"; exit 0; }
for seg in $READY; do
  if [ ! -e "$W/$seg" ]; then echo "  $seg: destino ausente -> el comando antiguo lo archivara bien"
  elif cmp -s -- "$PW/$seg" "$W/$seg"; then
    echo "  $seg: destino IDENTICO ya presente -> el comando antiguo se ATASCARA aqui"; BLOQUEANTES=$((BLOQUEANTES+1))
  else
    echo "  $seg: destino DISTINTO -> requiere intervencion manual, no revertir"; BLOQUEANTES=$((BLOQUEANTES+1))
  fi
done
test "$BLOQUEANTES" -eq 0 || { echo "ABORTAR EL ROLLBACK: $BLOQUEANTES segmentos bloquearian el comando antiguo. Escalar."; exit 1; }
REMOTE
```

- [ ] **Paso 2: Restaurar de forma atomica y afirmar la aplicacion**

```bash
ssh root@167.233.213.242 'bash -s' <<'REMOTE'
set -euo pipefail
BACKUP_CONF=$(sed -n 's/^BACKUP_CONF=//p' /root/archive-rollback.env)
test -f "$BACKUP_CONF" || { echo "ABORTAR: no existe $BACKUP_CONF"; exit 1; }
SHA=$(cat /root/archive-rollback.sha)
test "$(sha256sum "$BACKUP_CONF" | cut -d' ' -f1)" = "$SHA" || { echo "ABORTAR: el backup no coincide con su hash"; exit 1; }
C=/etc/postgresql/18/main/conf.d/recetas-archive.conf
TMP=$(mktemp /etc/postgresql/18/main/conf.d/.recetas-archive.XXXXXX)
cat -- "$BACKUP_CONF" > "$TMP"
chmod 644 "$TMP"; chown root:root "$TMP"
sync -- "$TMP"
mv -T "$TMP" "$C"
sync -- /etc/postgresql/18/main/conf.d
sudo -u postgres psql -X -At -c "SELECT pg_reload_conf();" > /dev/null
sleep 3
APPLIED=$(sudo -u postgres psql -X -At -c "SELECT applied FROM pg_file_settings WHERE name='archive_command' ORDER BY seqno DESC LIMIT 1;")
test "$APPLIED" = "t" || { echo "ABORTAR: la reversion no consta aplicada"; exit 1; }
sudo -u postgres psql -X -At -c "SELECT setting FROM pg_settings WHERE name='archive_command';"
echo "ROLLBACK APLICADO Y VERIFICADO"
REMOTE
```

`archive_command` es `sighup`: revertir no reinicia PostgreSQL ni corta el servicio.

---

### Task 8: Documentacion y cierre

**Ficheros:**
- Modificar: `infra/postgres/recetas-archive.conf`, `infra/postgres/README.md`,
  `docs/postgres-operacion-runbook.md`, `CONTINUAR.md`

- [ ] **Paso 1** Sincronizar `infra/postgres/recetas-archive.conf` con lo desplegado. El repositorio
  es la fuente de revision; no puede describir el comando antiguo.
- [ ] **Paso 2** `infra/postgres/README.md`: añadir `recetas-postgres-archive-wal` a la tabla con
  modo `0755 root:root` y la nota de por que **no** es `0750 root:postgres` como los demas (no lo
  lanza systemd con `User=postgres`, lo ejecuta el proceso archiver). Documentar los codigos de
  salida y que `journalctl -t recetas-archive-wal` es la via para auditar el archivado.
- [ ] **Paso 3** Runbook: en `Durabilidad del archivado`, sustituir la descripcion del comando con la
  cita de §25.3.1 y el motivo. En `Riesgos Residuales`, cerrar el punto del `archive_command` con el
  resultado real, y añadir como no ensayados `ENOSPC`/`EIO` reales.
- [ ] **Paso 4** Escaneo de seguridad:
  ```powershell
  pwsh -NoProfile -File scripts/security/run-security-scan.ps1
  ```
  Documentar modo, hallazgos por severidad, secretos verificados y codigo de salida.
  `/VibeSec` y `/security-review`: evaluar; sin codigo de aplicacion probablemente no aplican, pero
  hay que justificarlo por escrito y no marcarlo como PASS.
- [ ] **Paso 5** Trazabilidad en `CONTINUAR.md` y commit:
  ```bash
  git commit -m "fix(infra): el archive_command cumple el contrato de rearchivado de PostgreSQL"
  ```
- [ ] **Paso 6** Checklist de cierre de `CLAUDE.md`, punto por punto.

---

## Riesgo residual previsto

- `ENOSPC` y `EIO` reales en el filesystem de produccion no se ensayan. El caso 10 de la bateria
  cubre escritura truncada con `RLIMIT_FSIZE`; los fallos de dispositivo quedan sin ensayar.
- El temporal huerfano por `SIGKILL` entre `mktemp` y `ln` no se limpia hasta la purga diaria de
  WAL. No es corrupcion (antes de `ln` no hay nada publicado; despues, temporal y destino son hard
  links al mismo inodo), pero `restic` puede copiarlo en la ventana intermedia.
