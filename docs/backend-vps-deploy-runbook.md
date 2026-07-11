# Backend VPS Deploy Runbook

Runbook operativo del backend Spring Boot de Recetas Familiares desplegado en el VPS Hetzner.

## Estado

- Host: `ssh root@167.233.213.242`.
- URL publica HTTPS temporal: `https://recetas.167.233.213.242.sslip.io`.
- Reverse proxy: Caddy en puertos publicos `80/tcp` y `443/tcp`.
- Backend: systemd `recetas-backend.service`, escuchando solo en `127.0.0.1:8080`.
- PostgreSQL: `10.10.0.1:5432` por WireGuard/local VPS; `5432` no esta expuesto a internet.
- Uploads persistentes: `/var/lib/recetas-familiares/uploads`.
- Artefactos backend versionados: `/opt/recetas-familiares/backend/releases/<YYYYMMDDTHHMMSSZ-gitsha>.jar`.
- Symlink activo: `/opt/recetas-familiares/backend/current.jar`.
- Jar legado conservado: `/opt/recetas-familiares/backend/recetas-familiares-backend.jar`.
- Secretos runtime: `/etc/recetas-familiares/backend.env`, permisos `0640`, propietario `root:recetas-backend`.
- Deploy CI/CD: GitHub Actions con usuario SSH restringido `recetas-deploy`.

## Arquitectura

```text
GitHub Actions
  -> SSH recetas-deploy@167.233.213.242 (comando forzado)
  -> /usr/local/sbin/recetas-backend-deploy
  -> /opt/recetas-familiares/backend/releases/*.jar
  -> /opt/recetas-familiares/backend/current.jar

Internet
  -> Caddy :443 TLS
  -> 127.0.0.1:8080 recetas-backend.service
  -> PostgreSQL 10.10.0.1:5432
```

El backend no debe escuchar en la IP publica ni abrir `8080/tcp` en `ufw`.

## Variables Runtime

El archivo `/etc/recetas-familiares/backend.env` contiene valores reales fuera de Git:

```text
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
SERVER_FORWARD_HEADERS_STRATEGY=framework
DB_URL=jdbc:postgresql://10.10.0.1:5432/recetas_familiares
DB_USERNAME=recetas_app
DB_PASSWORD=<secret>
JWT_SECRET=<secret>
UPLOAD_DIR=/var/lib/recetas-familiares/uploads
UPLOAD_BASE_URL=https://recetas.167.233.213.242.sslip.io
CORS_ALLOWED_ORIGINS=https://recetas.167.233.213.242.sslip.io
APP_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS=https://recetas.167.233.213.242.sslip.io
APP_SECURITY_RATE_LIMIT_AUTH_TRUST_PROXY=true
```

No copiar secretos a documentacion, logs ni commits.

## CI/CD Backend

Workflow versionado: `.github/workflows/backend-ci-cd.yml`.

En `push` a `main`:

1. Levanta `postgres:18` como service container.
2. Ejecuta `mvn -B -f backend/pom.xml test` con:
   - `DB_TEST_URL=jdbc:postgresql://localhost:5432/recetas_familiares_test`
   - `DB_TEST_USERNAME=recetas_app`
   - `DB_TEST_PASSWORD=<ci-password>`
3. Ejecuta `mvn -B -f backend/pom.xml -DskipTests package`.
4. Publica el jar como artifact versionado.
5. Despliega por SSH al VPS usando `scripts/backend/deploy-backend-ci.sh`.

Secrets de GitHub Actions configurados:

```text
BACKEND_DEPLOY_HOST
BACKEND_DEPLOY_PORT
BACKEND_DEPLOY_USER
BACKEND_DEPLOY_KEY
BACKEND_DEPLOY_KNOWN_HOSTS
```

La clave privada de deploy tiene copia local fuera de Git en `herztner/recetas-backend-deploy-ed25519`. No moverla a una ruta versionada ni imprimirla.

## Deploy Manual De Emergencia

El flujo normal es GitHub Actions. Para probar el mismo canal desde una maquina con la clave de deploy:

```bash
export BACKEND_DEPLOY_HOST=167.233.213.242
export BACKEND_DEPLOY_USER=recetas-deploy
export BACKEND_DEPLOY_PORT=22
export BACKEND_DEPLOY_KEY_FILE=herztner/recetas-backend-deploy-ed25519
export BACKEND_DEPLOY_KNOWN_HOSTS="$(ssh root@167.233.213.242 "awk '{print \"167.233.213.242 \" \$1 \" \" \$2}' /etc/ssh/ssh_host_*.pub")"
export BACKEND_HEALTH_URL=https://recetas.167.233.213.242.sslip.io/api/v1/health
mvn -f backend/pom.xml -DskipTests package
bash scripts/backend/deploy-backend-ci.sh backend/target/recetas-familiares-backend-0.1.0-SNAPSHOT.jar
```

En Windows sin Bash, usar GitHub Actions para deploy normal. El usuario root sigue disponible solo para operaciones de emergencia.

## Rollback

Rollback en un comando desde la raiz del repo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backend\rollback-backend.ps1
```

El script ejecuta `rollback` por SSH como `recetas-deploy`; el comando forzado llama a `/usr/local/sbin/recetas-backend-rollback`, mueve `current.jar` a la release anterior, reinicia `recetas-backend.service` y valida health. Si el health falla, restaura la release original.

Rollback directo en el VPS:

```bash
sudo /usr/local/sbin/recetas-backend-rollback
```

## Operacion

Estado de servicios:

```bash
systemctl status recetas-backend.service --no-pager
systemctl status caddy.service --no-pager
journalctl -u recetas-backend.service -n 100 --no-pager
journalctl -u caddy.service -n 100 --no-pager
```

Releases:

```bash
readlink -f /opt/recetas-familiares/backend/current.jar
find /opt/recetas-familiares/backend/releases -maxdepth 1 -type f -name '*.jar' -printf '%TY-%Tm-%Td %TH:%TM %f\n' | sort
```

Puertos esperados:

```bash
ss -ltnp | grep -E ':(80|443|8080|5432) '
ufw status numbered
```

Esperado:

- Caddy escucha en `*:80` y `*:443`.
- Backend escucha en `127.0.0.1:8080` o equivalente IPv4-mapped loopback.
- PostgreSQL escucha en `10.10.0.1:5432`, `127.0.0.1:5432`, `::1:5432`.
- `ufw` permite `22/tcp`, `51820/udp`, `80/tcp`, `443/tcp` y `5432/tcp` solo `on wg0`.

## Validacion Smoke

Minimo tras cada despliegue:

- `GET /api/v1/health` por HTTPS -> `200`, `UP`.
- Flyway en `recetas_familiares` -> V1..V15 `success=true`.
- Registro/login temporal.
- CRUD de `stock-items`.
- `sync/push` + `sync/pull`.
- Chat REST `POST /chat/messages` + historial.
- WebSocket/STOMP `wss://.../ws`: `CONNECT` con JWT en frame STOMP, `SUBSCRIBE /topic/families/{familyId}/chat`, recibir `MESSAGE` tras envio REST.
- Limpieza de datos temporales de smoke por SQL acotado a prefijos temporales.

## Seguridad

- No exponer `8080/tcp` ni `5432/tcp` a internet.
- No usar HTTP plano para endpoints autenticados; Caddy redirige `80 -> 443`.
- Mantener `JWT_SECRET` y `DB_PASSWORD` solo en `/etc/recetas-familiares/backend.env` y secretos locales no versionados.
- `recetas-backend.service` corre con usuario sin login `recetas-backend`.
- `NoNewPrivileges=true`, `PrivateTmp=true`, `ProtectSystem=strict`, `ProtectHome=true`.
- Swagger/OpenAPI queda deshabilitado por perfil `prod` (`/swagger-ui.html` devuelve 404).
- `recetas-deploy` tiene password bloqueada, shell `/bin/bash` solo para que OpenSSH ejecute el comando forzado, y `authorized_keys` con `restrict,command="/usr/local/sbin/recetas-backend-ssh-dispatch"`.
- `sudoers` de deploy permite solo `/usr/local/sbin/recetas-backend-deploy *` y `/usr/local/sbin/recetas-backend-rollback`.
- El dispatcher solo acepta `deploy <release-id>`, `rollback` y `health`; no permite shell ni comandos arbitrarios.
- Un atacante con la clave de deploy podria desplegar un jar malicioso. Rotar `BACKEND_DEPLOY_KEY` y `authorized_keys` si hay sospecha de exposicion.

## Riesgos Residuales

- El hostname `sslip.io` es temporal y depende de un servicio DNS externo; sustituir por dominio propio antes de uso estable.
- Caddy instalado desde repo Ubuntu (`2.6.2`); vigilar actualizaciones de seguridad.
- La clave SSH de deploy esta en GitHub Secrets para habilitar CD; la mitigacion es usuario no-root, comando forzado, known_hosts fijado y sudoers limitado, pero la superficie de ataque existe.
- El workflow despliega automaticamente todo push a `main`; proteger `main` con revisiones/branch protection si el repositorio empieza a recibir contribuciones externas.
- Flyway 11.7.2 avisa que PostgreSQL 18.4 es mas nuevo que su soporte probado actual.
