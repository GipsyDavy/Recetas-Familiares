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
| `recetas-postgres-logical-backup` | `recetas-postgres-logical-backup.timer` | diaria ~03:20 UTC | 14 dias |
| `recetas-postgres-basebackup` | `recetas-postgres-basebackup.timer` | semanal, domingo | 21 dias |
| `recetas-postgres-offsite-backup` | `recetas-postgres-offsite-backup.timer` | diaria ~05:20 UTC | keep-daily 14 + keep-weekly 5 |

Destino de los backups locales: `/var/backups/recetas-postgres/{logical,base,wal}`.
Destino offsite: repositorio restic cifrado en un Hetzner Storage Box por SFTP.

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
