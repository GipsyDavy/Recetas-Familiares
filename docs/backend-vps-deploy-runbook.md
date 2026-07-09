# Backend VPS Deploy Runbook

Runbook operativo del backend Spring Boot de Recetas Familiares desplegado en el VPS Hetzner.

## Estado

- Host: `ssh root@167.233.213.242`.
- URL publica HTTPS temporal: `https://recetas.167.233.213.242.sslip.io`.
- Reverse proxy: Caddy en puertos publicos `80/tcp` y `443/tcp`.
- Backend: systemd `recetas-backend.service`, escuchando solo en `127.0.0.1:8080`.
- PostgreSQL: `10.10.0.1:5432` por WireGuard/local VPS; `5432` no esta expuesto a internet.
- Uploads persistentes: `/var/lib/recetas-familiares/uploads`.
- Artefacto jar: `/opt/recetas-familiares/backend/recetas-familiares-backend.jar`.
- Secretos runtime: `/etc/recetas-familiares/backend.env`, permisos `0640`, propietario `root:recetas-backend`.

## Arquitectura

```text
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

## Despliegue De Un Nuevo Jar

Desde la raiz del repo:

```powershell
mvn -f backend\pom.xml -DskipTests package
scp backend\target\recetas-familiares-backend-0.1.0-SNAPSHOT.jar root@167.233.213.242:/opt/recetas-familiares/backend/recetas-familiares-backend.jar
ssh root@167.233.213.242 "chown root:root /opt/recetas-familiares/backend/recetas-familiares-backend.jar; chmod 644 /opt/recetas-familiares/backend/recetas-familiares-backend.jar; systemctl restart recetas-backend.service"
```

Validar despues:

```powershell
curl.exe --ssl-no-revoke -i https://recetas.167.233.213.242.sslip.io/api/v1/health
ssh root@167.233.213.242 "systemctl is-active recetas-backend.service caddy.service postgresql@18-main.service"
```

## Operacion

Estado de servicios:

```bash
systemctl status recetas-backend.service --no-pager
systemctl status caddy.service --no-pager
journalctl -u recetas-backend.service -n 100 --no-pager
journalctl -u caddy.service -n 100 --no-pager
```

Puertos esperados:

```bash
ss -ltnp | grep -E ':(80|443|8080|5432) '
ufw status numbered
```

Esperado:

- Caddy escucha en `*:80` y `*:443`.
- Backend escucha en `127.0.0.1:8080`.
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

## Riesgos Residuales

- El hostname `sslip.io` es temporal y depende de un servicio DNS externo; sustituir por dominio propio antes de uso estable.
- Caddy instalado desde repo Ubuntu (`2.6.2`); vigilar actualizaciones de seguridad.
- No hay CI/CD ni rollback automatizado de jar; conservar artefactos/versiones si se publica a usuarios reales.
- Backups DB siguen sin copia offsite cifrada y PITR completo no se ha ensayado en cluster aislado.
