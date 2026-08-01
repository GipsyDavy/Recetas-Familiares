# Portada de receta en los listados (Android + Desktop) — Diseño

**Item de backlog:** punto (7) de `paraImplementar.txt`, mitad pendiente. La foto y la portada en
la vista de DETALLE ya funcionan y se verificaron el 2026-07-12; lo que sigue abierto, y cierra
este documento, es que **las cards de LISTADO no muestran portada ni en Android ni en Desktop**
(`CONTINUAR.md` §8, "PARCIALES", y cierre de sesión del 2026-08-01).

Este sprint incluye además una deuda técnica arrastrada, independiente pero acordada en el mismo
alcance: **`UploadControllerTest` en rojo en local** — 5 tests, atribuidos a contaminación de la
base de datos de test compartida, según `CONTINUAR.md`; ninguna de las dos cosas está confirmada en
esta sesión (ver "Deuda de tests"). Se agrupa aquí porque toca precisamente el área de uploads que
este sprint roza, y dejarla en rojo enmascararía regresiones nuevas.

## Objetivo

Que un miembro reconozca una receta de un vistazo por su foto, sin abrir el detalle, tanto en el
listado de Android como en el panel de recetas de Desktop.

## Decisiones de alcance (confirmadas con el usuario, 2026-08-01)

1. **Origen del dato: campo nuevo en el contrato**, no derivación por cliente. Se descartó que cada
   cliente lo resolviera por su cuenta porque Desktop no mantiene caché local de fotos —
   `RecipeRepository.updatePhotosFromSync` es un no-op deliberado (`RecipeRepository.java:223`) — y
   habría necesitado una petición por receta visible, 30 por página. También se descartó un
   endpoint nuevo de portadas: añade round-trip y un sitio más donde validar ownership.
2. **Layout de Desktop: miniatura lateral de 56×56**, no card con hero 16:9. El panel izquierdo mide
   280-340px y es un índice de navegación, no un escaparate: con hero cada fila pasaría de ~48px a
   ~150px y se verían 3 recetas donde hoy se ven 8.
3. **iOS fuera de alcance.** Se verifica y documenta que el campo nuevo no lo rompe, pero no se
   escribe UI iOS. Motivo: sin macOS no puedo ejecutar ni ver nada de lo que escriba, y entregar UI
   sin validar contradice el protocolo de cierre de `CLAUDE.md`.
4. **Deuda de tests: diagnosticar y aislar por test**, no rediseñar la infraestructura de test. Se
   descartó montar una BD efímera por ejecución: arreglaría la clase entera de problemas pero
   excede el alcance autorizado y arriesga los ~111 tests que hoy pasan.

## Contrato backend

`RecipeResponse` gana un campo aditivo y anulable:

```java
String coverThumbnailUrl   // null si la receta no tiene fotos activas
```

**Regla única, válida en todos los endpoints que devuelven `RecipeResponse`** — listado, detalle,
creación, edición, copia y `sync/pull`:

> `coverThumbnailUrl` = `thumbnailUrl` de la foto activa (`deleted = false`) de menor `position`.
> Si esa foto no tiene `thumbnailUrl`, se usa su `url`. Si no hay fotos activas, `null`.

No se rellena en unos endpoints y en otros no. Un campo poblado a medias haría imposible distinguir
*"esta receta no tiene foto"* de *"aquí no se calculó"*, y el cliente no tiene forma de saber cuál
de las dos es.

El cambio es **aditivo**: los clientes ya publicados ignoran el campo. iOS lo ignora sin error
porque su `Json` tiene `ignoreUnknownKeys = true` (`ios/composeApp/.../network/ApiClient.kt:39`).

## Backend, implementación

Query nueva en `RecipePhotoRepository`, proyección ligera (no entidades completas), filtrada **por
familia además de por ids**:

```sql
WHERE p.recipe.family.id = :familyId
  AND p.recipe.id IN :recipeIds
  AND p.deleted = false
ORDER BY p.recipe.id, p.position
```

El filtro por `familyId` no es redundante con el `IN`: impide que un id de receta de otra familia
colado en la lista devuelva la URL de una portada ajena. Es la misma disciplina de ownership que
aplica el resto del backend, aplicada también a la consulta de portadas.

Va sobre el índice ya existente `ix_recipe_photos_recipe_active (recipe_id, deleted, position)`
(`V9__create_recipe_photos_schema.sql:19`). **No hace falta migración nueva.**

**Cero N+1.** Una query extra por página, nunca por receta:

- `RecipeService.listRecipes`: batch sobre los ids de la página (30 por defecto).
- `SyncService`, pull paginado: batch sobre los ids del slice. Es el caso crítico — hasta 200
  recetas por página; una query por receta sería el problema real.
- Caminos de una sola receta (`getRecipe`, `createRecipe`, `updateRecipe`, `copyRecipe`, y el bucle
  de push de sync): reutilizan `findByRecipe_IdAndDeletedFalseOrderByPositionAsc`, que ya existe.

## Android

`RecipeCard` (`android/.../ui/RecipeScreens.kt:339`) ya tiene el hueco hecho: un `Box` de 152dp con
icono `Restaurant` de placeholder y degradado vertical para que el texto se lea sobre la imagen. Se
añade `AsyncImage` (Coil 3, ya en uso) detrás del degradado, con `Crossfade` al llegar la imagen y
el icono actual como estado de carga y de fallo.

**Android no consume el campo nuevo del contrato, y es deliberado.** Room ya recibe *todas* las
fotos de la familia por sync (`Repositories.kt:554`), así que un DAO de portadas por familia da el
dato:

- sin subir versión de esquema Room para un dato que ya está en el dispositivo,
- funcionando **offline**, que es el criterio que decide,
- y sin esperar al despliegue del backend.

El campo del contrato existe para Desktop hoy y para iOS cuando se desbloquee.

## Desktop

Dos piezas, y la primera es la que concentra el coste del sprint.

### a) Cargador de imagen autenticado (componente nuevo)

`/uploads/**` exige JWT y `javafx.scene.image.Image(url)` no permite enviar cabeceras. Hace falta:
OkHttp con `Authorization: Bearer` → bytes → `Image`, con caché LRU acotada en memoria,
decodificación **fuera del JavaFX Application Thread** y entrega vía `Platform.runLater`. Reutiliza
el patrón del `photoClient` que ya existe en `RecipeRepository.java:30`.

### b) `RecipeCell`

`RecipeListView.java:254` pasa de texto plano a
`HBox[ ImageView 56×56 con esquinas redondeadas | VBox(título, meta) ]`, con placeholder cuando no
hay portada o la carga falla, y `FadeTransition` de 150ms al aparecer la imagen (regla §17 de
`CLAUDE.md`). `RecipeDtos.RecipeDto` gana `coverThumbnailUrl`.

**Trampa a cubrir explícitamente:** `ListCell` recicla sus instancias. Si no se invalida o cancela
la carga en curso al reciclarse la celda, acaba pintándose la foto de la receta anterior en la fila
nueva. Es el fallo clásico de este patrón y va como caso de prueba, no como cuidado mental.

## Deuda de tests

`superpowers:systematic-debugging`: **reproducir antes de tocar nada.** Hipótesis a verificar, no a
asumir:

- filas residuales en `recetas_familiares_test` de ejecuciones anteriores,
- ficheros viejos en `target/test-uploads`,
- dependencia del orden de ejecución entre tests.

El arreglo va **en el test**, no en producción. Si resultara que el código de uploads necesita
cambiar, eso es un hallazgo distinto, se documenta y se decide aparte — no se cuela en este sprint.

**Prerrequisito bloqueante:** `DB_TEST_PASSWORD` para `recetas_familiares_test` en
`10.10.0.1:5432`. El túnel WireGuard está operativo y el puerto 5432 acepta conexión (verificado en
sesión con `TcpClient`). El Postgres de `localhost:5432` pertenece a `PostgreSQL_For_Odoo` y no
sirve para esto.

**Estado real de la reproducción al escribir este spec: NO reproducido.** Se intentó
`mvn -f backend/pom.xml test -Dtest=UploadControllerTest` con la contraseña `recetas_app` que
guardaba la memoria del entorno, y falló en el arranque del contexto de Spring:

```
FATAL: password authentication failed for user "recetas_app"
```

Esa contraseña es de la época de MySQL y no es válida contra el PostgreSQL migrado. Los 7 tests de
la clase dieron error por fallo de `ApplicationContext` (Flyway no obtiene conexión), **no por el
problema de aislamiento que este sprint quiere arreglar**. No se escribió nada en la base de datos.

Consecuencia para el plan: hasta tener la contraseña correcta no se sabe si los fallos son 5 (lo
que documenta `CONTINUAR.md` de una sesión anterior) ni cuál es su causa. La fase de diagnóstico
empieza reproduciendo de verdad, y el número y la causa se registran a partir de esa ejecución, no
de la documentación previa.

## Seguridad

El campo nuevo **no abre superficie**: expone una URL que el miembro ya podía pedir, y
`UploadController` sigue siendo quien decide el acceso al fichero. El riesgo real está en la
consulta de portadas, y por eso lleva filtro por familia (ver arriba).

Por tocar imágenes, tokens y un endpoint con datos familiares, este sprint exige antes del commit
de cierre:

- `/VibeSec`,
- `/security-review`,
- `pwsh -NoProfile -File scripts/security/run-security-scan.ps1` en modo sprint.

## Validación esperada

| Área | Comando / acción |
|---|---|
| Backend | `mvn -f backend/pom.xml test`, con `UploadControllerTest` en verde |
| Desktop | `mvn test` y `mvn -DskipTests compile` desde `desktop/` |
| Android | `gradle test` y `gradle assembleDebug` desde `android/` |
| Desktop GUI | `mvn javafx:run` contra backend local: ver miniaturas, placeholder y scroll |
| Android GUI | AVD `Pixel_9_Pro`: ver portada, receta sin foto y scroll de lista larga |
| Diseño | `/impeccable critique` sobre las dos cards |

Ninguna de estas casillas se marca sin haberse ejecutado en la sesión (regla de honestidad
operativa de `CLAUDE.md`).

## Fuera de alcance

- **iOS**: ni DTO ni UI. Solo se verifica que el campo nuevo no lo rompe.
- **Otros listados**: favoritos, menús semanales y búsqueda global siguen sin portada.
- **Elegir la portada**: sigue siendo la foto de `position` menor. No se añade UI para marcar otra
  como portada.
- **Infraestructura de test**: no se monta BD efímera por ejecución.

## Riesgos

- El cargador de imagen autenticado de Desktop es componente nuevo en un cliente sin tests de UI
  automatizados: se valida por prueba manual, y eso queda como riesgo residual explícito.
- El reciclado de `ListCell` puede producir imágenes cruzadas entre filas si la cancelación no es
  correcta. Cubierto por caso de prueba, pero es el punto frágil.
- La causa de los tests en rojo aún no está diagnosticada, y el intento de reproducción de esta
  sesión no llegó a ejecutarlos (ver arriba). Si al reproducir resultara ser un fallo de producción
  y no de aislamiento, el alcance de esa mitad cambia y hay que reconsiderarlo con el usuario.
- La contraseña de `recetas_app` para PostgreSQL no está en ningún sitio al que yo llegue. Mientras
  no la aporte el usuario, la mitad de deuda técnica del sprint está bloqueada; la mitad de UX (7)
  no depende de ella y puede ejecutarse igual.
