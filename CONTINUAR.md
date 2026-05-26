# Continuidad del Proyecto

Este documento resume el estado actual del proyecto para continuar en una nueva sesion sin perder contexto.

## Carpeta que debe abrirse

Abrir siempre la carpeta raiz del monorepo:

```text
C:\Users\GipsyDavy\MAVEN\Recetas Familiares
```

No abrir como proyecto principal:

- `desktop/`
- `Recetas Familiares - Desktop/`
- `Recetas Familiares - Android/`

La carpeta valida del modulo Desktop es:

```text
C:\Users\GipsyDavy\MAVEN\Recetas Familiares\desktop
```

## Repositorio Git

El repositorio Git local esta inicializado en la carpeta raiz:

```text
C:\Users\GipsyDavy\MAVEN\Recetas Familiares\.git
```

Rama principal:

```text
main
```

Remoto GitHub:

```text
origin https://github.com/GipsyDavy/Recetas-Familiares.git
```

Ultimos commits:

```text
988d832 Organize monorepo structure
a97b8bd Initial project baseline
```

Estado esperado al retomar:

```text
git status --short --branch
```

Debe mostrar algo equivalente a:

```text
## main...origin/main
```

## Estructura versionada actual

Archivos y carpetas que estan en Git:

```text
.gitignore
CLAUDE.md
MACRO-PROMPT-RECETAS-FAMILIA.md
README.md
Resumen.md
android/README.md
backend/README.md
database/README.md
desktop/.gitignore
desktop/src/main/java/org/gipsybuho/Main.java
docs/README.md
docs/roadmap.md
CONTINUAR.md
```

## Que se hizo

1. Se revisaron los documentos raiz:
   - `CLAUDE.md`
   - `Resumen.md`
   - `MACRO-PROMPT-RECETAS-FAMILIA.md`

2. Se detecto que el proyecto estaba casi vacio:
   - no habia repo Git;
   - no habia build system;
   - no habia backend Spring Boot;
   - no habia proyecto Android real;
   - solo habia un `Main.java` de ejemplo en Desktop.

3. Se inicializo Git en la carpeta raiz `Recetas Familiares`.

4. Se creo `.gitignore` global para:
   - Java;
   - Maven;
   - Gradle;
   - Android;
   - IntelliJ;
   - logs;
   - builds;
   - secretos locales.

5. Se hizo el primer commit:

```text
a97b8bd Initial project baseline
```

6. Se conecto el remoto GitHub:

```text
https://github.com/GipsyDavy/Recetas-Familiares.git
```

7. Se hizo push inicial a GitHub.

8. Se normalizo la estructura del monorepo:

```text
backend/
android/
desktop/
database/
docs/
```

9. Se movio el contenido versionado de `Recetas Familiares - Desktop/` a:

```text
desktop/
```

10. Se crearon README iniciales:

```text
README.md
backend/README.md
android/README.md
database/README.md
docs/README.md
docs/roadmap.md
```

11. Se hizo commit y push:

```text
988d832 Organize monorepo structure
```

## Carpetas antiguas locales

Pueden seguir existiendo localmente:

```text
Recetas Familiares - Desktop/
Recetas Familiares - Android/
```

Esas carpetas contienen restos/metadatos ignorados de IntelliJ y no forman parte del estado versionado importante.

No usarlas como raiz del proyecto.

Antes de borrarlas, verificar que no contienen ningun archivo util no versionado:

```text
git status --short --ignored
```

No borrar nada automaticamente sin confirmacion del usuario.

## Objetivo del producto

Segun los documentos raiz, el proyecto debe ser una aplicacion premium multiplataforma para gestion familiar de recetas:

- Backend Spring Boot + MySQL;
- Android nativo;
- Desktop JavaFX;
- sincronizacion multiplataforma;
- experiencia calida, moderna, emocional y premium;
- gestion de recetas, ingredientes, stock, menus, listas de compra y memoria culinaria familiar.

## Reglas importantes del proyecto

- Mantener arquitectura cliente-servidor.
- MySQL es la fuente principal.
- Android y Desktop se comunican mediante API HTTP.
- No exponer entidades JPA directamente.
- Usar DTOs explicitos.
- API versionada bajo `/api/v1/`.
- Validar ownership familiar en todos los endpoints.
- Usar JWT y refresh tokens.
- No hardcodear secretos.
- Usar migraciones versionadas con Flyway o Liquibase.
- Entidades sincronizables con:
  - `id`
  - `createdAt`
  - `updatedAt`
  - `syncVersion`
  - `deleted`
- Soft delete obligatorio para datos sincronizados.
- Evitar bloquear UI thread en Android y JavaFX.

## Siguiente paso recomendado

El siguiente paso tecnico recomendado es crear el backend real dentro de:

```text
backend/
```

Recomendacion:

- Java 21 LTS;
- Spring Boot 3.x;
- Maven o Gradle, preferiblemente escoger uno y mantenerlo;
- Spring Web;
- Spring Security;
- Spring Data JPA;
- MySQL Driver;
- Flyway;
- Validation;
- OpenAPI/Swagger;
- tests iniciales.

Primer backend minimo:

```text
backend/
├─ pom.xml o build.gradle.kts
├─ src/main/java/...
├─ src/main/resources/application.yml
└─ src/test/java/...
```

Primeras capacidades backend:

1. arrancar Spring Boot;
2. health endpoint;
3. configuracion por variables de entorno;
4. conexion MySQL preparada;
5. Flyway preparado;
6. estructura de paquetes:
   - `auth`
   - `users`
   - `families`
   - `recipes`
   - `common`
   - `security`

## Procedimiento al retomar

1. Abrir:

```text
C:\Users\GipsyDavy\MAVEN\Recetas Familiares
```

2. Comprobar estado:

```text
git status --short --branch
```

3. Leer:

```text
CLAUDE.md
Resumen.md
MACRO-PROMPT-RECETAS-FAMILIA.md
CONTINUAR.md
docs/roadmap.md
```

4. Continuar con el backend Spring Boot en `backend/`.

5. Antes de modificar, explicar plan breve.

6. Despues de modificar:

```text
git status --short --branch
git diff --stat
```

7. Commit y push cuando el cambio este verificado.

## Nota para la siguiente sesion

No empezar desde `desktop/`. El proyecto ahora es un monorepo y la raiz correcta es `Recetas Familiares`.

El siguiente trabajo no debe ser UI todavia. Primero conviene crear el backend base, porque Android y Desktop dependeran de sus contratos API.
