# Backups de PostgreSQL (VPS Hetzner)

Copia versionada de los scripts que se ejecutan desde `/usr/local/sbin/` del VPS.
El servidor es la fuente de ejecucion; este directorio es la fuente de revision.
Cualquier cambio se edita aqui primero y se despliega despues.

**Por que existe este directorio:** hasta el 2026-07-31 estos scripts vivian solo en
el servidor. Ninguna auditoria del repositorio podia verlos, y por eso el punto 18 del
backlog (`backups del servidor Hetzner`) estuvo meses marcado como "no verificable
desde el repo". Mismo patron que `infra/backend/`.

| Script | Unit systemd | Cadencia | Retencion |
|---|---|---|---|
| `recetas-postgres-logical-backup` | `recetas-postgres-logical-backup.timer` | diaria 03:15 UTC +15m | 14 dias |
| `recetas-postgres-basebackup` | `recetas-postgres-basebackup.timer` | domingos 04:15 UTC +30m | 21 dias |
| `recetas-postgres-offsite-backup` | `recetas-postgres-offsite-backup.timer` | diaria 05:15 UTC +10m | keep-daily 14 + keep-weekly 5 |

El `+Nm` es `RandomizedDelaySec`. Los tres llevan `Persistent=true`. Para comprobar cuando se
disparo cada uno por ultima vez, usar el `mtime` de `/var/lib/systemd/timers/stamp-<unit>.timer`:
`ExecMainExitTimestamp` y `LastTriggerUSec` se pierden en cada reinicio, y `Result=success` es
tambien el valor por defecto de una unidad que nunca ha corrido en el arranque actual.

Destino de los backups locales: `/var/backups/recetas-postgres/{logical,base,wal}`.
Destino offsite: repositorio restic cifrado en un Hetzner Storage Box por SFTP.

`recetas-archive.conf` es la configuracion de archivado de WAL de PostgreSQL.
Vive en el servidor en `/etc/postgresql/18/main/conf.d/` — **no** en
`postgresql.conf`, que solo tiene la linea de ejemplo comentada. `archive_command`
es un parametro `sighup`: basta `select pg_reload_conf()`, no hace falta reiniciar.

## `recetas-postgres-archive-wal` (el `archive_command`)

| Script | Ejecutado por | Modo | Propietario |
|---|---|---|---|
| `recetas-postgres-archive-wal` | el proceso archiver de PostgreSQL | `0755` | `root:root` |

**Ojo: NO es `0750 root:postgres` como los tres de backup.** Aquellos los lanza systemd con
`User=postgres`, asi que basta el bit de grupo. Este lo ejecuta el archiver directamente, que corre
como `postgres`: necesita el bit de ejecucion de «otros», y no debe poder modificarlo.

Esta escrito en **Python 3**, no en shell, por dos motivos verificados el 2026-08-01:

1. El contrato de §25.3.1 exige que, ante un fichero de archivo preexistente, el comando devuelva
   **0 si el contenido es identico y esta persistido** y distinto de 0 solo si difiere. El comando
   anterior (`test ! -f DEST && cp %p DEST && sync DEST`) fallaba **siempre** que el destino
   existiera. Tras una caida en la ventana entre la copia y el registro durable del exito,
   PostgreSQL reintenta el mismo segmento: con el comando antiguo el archiver quedaba atascado de
   forma permanente, `pg_wal` crecia y, si el disco se llenaba, PostgreSQL hacia PANIC.
2. **Ubuntu 26.04 sustituyo GNU coreutils por uutils** (`rust-coreutils 0.8.0`; el paquete
   `coreutils` es solo un meta-paquete). Su `sync FICHERO` **no hace `fsync(2)`**: abre el fichero y
   llama a `sync()` global. Comprobado con `strace`. Ademas devolvio codigo 0 sincronizando un
   fichero que el usuario ni siquiera podia abrir, asi que su codigo de salida no sirve para
   afirmar durabilidad. Python expone `os.fsync` real sobre fichero y sobre directorio, y propaga
   los errores.

Publicacion con `os.link()`, no con `rename()`: `rename` **sobrescribe** en silencio si el destino
aparece durante la carrera. Y `ln` de shell sin `-T` sobre un destino que fuese un directorio
publicaria **dentro** devolviendo 0 — comprobado en este VPS.

### Codigos de salida

| Codigo | Significado |
|---|---|
| 0 | archivado (o rearchivado identico) y persistido |
| 1 | uso incorrecto: argumentos o nombre de destino invalido |
| 2 | **conflicto**: el destino existe con contenido distinto. Requiere intervencion humana |
| 3 | error copiando el origen al temporal |
| 4 | error de durabilidad: `fsync` de fichero o de directorio fallo |
| 5 | error de entorno: directorio ausente, origen ausente, destino no regular |

### Auditar el archivado

El script registra cada exito en `daemon.info` y cada error en `daemon.err`:

```bash
journalctl -t recetas-archive-wal --no-pager -n 20
```

Es la via para **demostrar que el archiver ejecuta este script** y no otro comando, y el unico
sitio donde aparece el codigo 2 (conflicto), que no incrementa `failed_count` de forma
distinguible.

`ARCHIVE_DIR` es una constante dentro del script, deliberadamente **no** configurable por entorno:
una variable heredada por el proceso de PostgreSQL podria redirigir el archivo en silencio. Para
probarlo en un directorio desechable, generar una copia del script sustituyendo esa linea.

## Secretos

**No estan aqui y no deben estarlo.** Viven en el VPS, modo 0600 root:root:

- `/etc/recetas-familiares/offsite-backup.env` — `RESTIC_REPOSITORY`, `RESTIC_PASSWORD`, `RESTIC_CACHE_DIR`
- `/etc/recetas-familiares/storagebox.env` — `STORAGEBOX_USER`, `STORAGEBOX_HOST`, `STORAGEBOX_PASSWORD`

Los scripts los cargan con `set -a; source ...; set +a`. La passphrase de restic tiene
una unica copia fuera del VPS, en `herztner/` (directorio local, nunca versionado).
Sin ella el repositorio offsite es irrecuperable.

## Purga de WAL

Vive en el script **diario** (`recetas-postgres-logical-backup`), no en el semanal.
La regla es: borrar solo los segmentos anteriores a la copia base mas antigua que
sigue en disco. Es fail-closed — si no hay ninguna copia base, no borra nada.

Hasta el 2026-07-31 la purga estaba dentro del script de basebackup con una ventana
fija de 35 dias. Si el basebackup fallaba varias semanas seguidas, el WAL dejaba de
purgarse en silencio.

## Desplegar un cambio

**Ojo con los permisos: no son iguales para los tres scripts.** Los dos de
PostgreSQL corren con `User=postgres` en su unit, asi que necesitan bit de
ejecucion para el grupo. El offsite corre como root y no lo necesita.

| Script | Modo | Propietario |
|---|---|---|
| `recetas-postgres-logical-backup` | `0750` | `root:postgres` |
| `recetas-postgres-basebackup` | `0750` | `root:postgres` |
| `recetas-postgres-offsite-backup` | `0700` | `root:root` |

Poner `0700` en los dos primeros los rompe con `status=203/EXEC` y
`Permission denied`, y el backup no se ejecuta. Paso el 2026-07-31.

```bash
ssh root@167.233.213.242 'cp /usr/local/sbin/<script> /usr/local/sbin/<script>.bak-$(date +%Y%m%d)'
scp infra/postgres/<script> root@167.233.213.242:/usr/local/sbin/<script>
ssh root@167.233.213.242 'chmod 0750 /usr/local/sbin/<script>'   # 0700 para el offsite
```

## Verificar

`inactive dead` no significa que fuera bien. Hay que mirar el resultado real:

```bash
ssh root@167.233.213.242 'systemctl start <unit> && systemctl show <unit> -p Result -p ExecMainStatus'
```

Esperado: `Result=success` y `ExecMainStatus=0`.

Y que un dump sea restaurable de verdad, no solo que el fichero exista:

```bash
ssh root@167.233.213.242 'D=$(ls -t /var/backups/recetas-postgres/logical/*.dump | head -1); sudo -u postgres pg_restore --list "$D" | grep -c "TABLE DATA"'
```

Esperado: 26 tablas con datos (a fecha 2026-07-31).
