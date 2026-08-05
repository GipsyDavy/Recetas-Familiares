# Diseño: CI de clientes (Desktop y Android)

Fecha: 2026-08-05
Agente: Claude Code (sin apoyo de Codex ni Gemini)

## Problema

`backend-ci-cd.yml` filtra por `paths: backend/**`. Desktop y Android **nunca** se compilaban ni
se testeaban en CI. Los 96 tests de Desktop y los 93 de Android que dejó el sprint COD-8 solo
corrían si alguien los lanzaba a mano en la máquina de desarrollo.

El sprint COD-8 cerró declarando esto como el candidato natural siguiente, y con razón: una red de
seguridad que depende de que un humano se acuerde de tirar de ella no es una red de seguridad.

## Blocker encontrado antes de diseñar

`android/gradle/wrapper/gradle-wrapper.properties` apunta a
`file:///C:/tmp/tools/gradle-9.5.1-bin.zip`, una ruta local de la máquina de desarrollo. En un
runner de GitHub ese archivo no existe: `./gradlew` falla en el primer segundo.

Se descartó arreglar el wrapper a la URL oficial. Motivo: la caché de `dists` está vacía, así que
el cambio forzaría una descarga de Gradle en la máquina local, y en ese equipo Avast intercepta
TLS y ya rompió el registry de Semgrep (documentado en `CLAUDE.md`). Arreglar el wrapper es
correcto en abstracto, pero arriesga el entorno de desarrollo a cambio de nada que este sprint
necesite. Queda como deuda anotada.

**Solución adoptada:** CI instala Gradle 9.5.1 con `gradle/actions/setup-gradle` e invoca
`gradle`, no `./gradlew`. El wrapper no se toca.

## Decisiones

| Decisión | Elección | Motivo |
|---|---|---|
| Estructura | Dos workflows separados | Los filtros `paths` son por workflow; separarlos evita que un cambio en Android dispare el build de Desktop |
| Runner Desktop | Matriz `ubuntu` + `windows` | El repositorio es público: los runners no consumen cuota. Desktop se distribuye en Windows |
| Runner Android | Solo `ubuntu` | Sin comportamiento dependiente del sistema operativo; el SDK arranca antes en Linux |
| Alcance | Tests y compilación | Es lo que hoy se valida a mano en cada cierre de sprint |
| SDK de Android | `sdkmanager` de la imagen | Una acción de terceros menos en la cadena de suministro |
| `cancel-in-progress` | `true` | Al revés que backend, que despliega y no puede cancelarse a medias. Esto solo valida |

Verificado antes de elegir el runner Linux: ningún test de Desktop importa JavaFX ni necesita
display, y `TokenVault` se guarda tras `os.name` (`TokenVault.java:17`), así que en Linux cae a
texto plano sin cargar JNA.

## Arquitectura

**`desktop-ci.yml`** — matriz `ubuntu-latest` y `windows-latest`, `fail-fast: false` para que un
fallo solo en Windows no oculte el resultado de Linux. JDK 21 temurin con caché de Maven.
`mvn -B -f desktop/pom.xml test`, luego `compile`.

**`android-ci.yml`** — `ubuntu-latest`, `working-directory: android`. JDK 21 temurin,
`setup-gradle` fijando 9.5.1, `sdkmanager --install "platforms;android-36" "build-tools;36.0.0"`
(idempotente), `gradle testDebugUnitTest`, `gradle assembleDebug`.

Ambos: `permissions: contents: read`, acciones pinadas por SHA como el workflow de backend,
disparadores `push` a main, `pull_request` y `workflow_dispatch`, todos filtrados por paths.

## Validación

Los workflows se validaron **en una PR antes de fusionar**, que es la única forma de comprobar
que una CI funciona sin haberla metido ya en `main`. Ambos ficheros están dentro de sus propios
filtros `paths`, así que la PR que los introduce se valida a sí misma.

## Fuera de alcance

Tests instrumentados con emulador; subida del APK como artefacto; empaquetado del instalador
Windows con `jpackage`; escaneo de seguridad en CI (depende de snapshots locales de reglas de
Semgrep, es un sprint propio); protección de rama exigiendo que la CI pase.

## Deuda detectada, no abordada

- **`TokenVault` no tiene ni un test.** Cifra los tokens de sesión en disco con DPAPI (SEC-2).
  Correr en Windows no lo cubre: ningún test llega a esa clase. Un test de ida y vuelta
  (`protect` y luego `unprotect`) haría que el runner Windows ejercitara DPAPI de verdad.
- **El wrapper de Gradle sigue apuntando a un zip local**, así que el repositorio no es
  reproducible para otro clon. Arreglarlo exige verificar antes que Avast no rompe la descarga.
