# Diseño: red de seguridad para la lógica de pantalla (COD-8)

Fecha: 2026-08-05
Agente: Claude Code (sin apoyo de Codex ni Gemini, por decisión del usuario)

## Problema

Los cierres de los sprints del 01/08 y del 02/08 declaran el mismo riesgo residual
número uno: no existe ningún test que cubra la lógica de las pantallas. Desktop
tenía 60 tests y Android 82, todos de repositorios, caché de imágenes,
sincronización y temas.

Ese hueco ya se cobró una pieza. El defecto más grave del sprint del 02/08
—`WeeklyMenuView` repoblaba la caché compartida de recetas solo si estaba vacía,
de modo que el camino normal (abrir la app → Recetas → Menú) dejaba media agenda
semanal sin portada— lo encontró una revisión humana en la última fase. La
validación visual manual pasó por encima, porque probó justamente el único camino
donde la guarda funcionaba.

## Objetivo

Poner bajo test la clase concreta de lógica que ya ha fallado: **estado compartido
entre pantallas, filtrado y paginación**. En JVM pura, sin toolkit gráfico ni
emulador, dentro de los comandos de test que ya se ejecutan en cada cierre.

No es un sprint de cobertura amplia ni de tests de renderizado.

## Decisiones

| Decisión | Elección | Motivo |
|---|---|---|
| Qué se caza | Lógica sin renderizar | Es la clase del bug real; el renderizado es caro y frágil |
| Dónde corre | `mvn test` y `gradlew testDebugUnitTest` | No existe CI de clientes; montarla es un sprint propio |
| Desktop | Extraer a clases planas y testear | No hay seam: `AppContext` es singleton de constructor privado |
| Android | Testear el ViewModel directamente | Ya hay seam y las dependencias de test ya estaban |
| Alcance | Pantallas con bugs previos | YAGNI: cubrir todo sería trabajo de varias sesiones |

Se descartó introducir una capa de presenters en Desktop: es un refactor
arquitectónico sobre 14.655 líneas de UI estable, contrario a YAGNI y a la regla
de no refactorizar código estable de `CLAUDE.md`.

## Arquitectura

### Desktop: crear el seam

`ui/state/`, clases planas sin ningún import de `javafx.scene`, siguiendo el
precedente de `RecipeRepository.coverUrlFor()` del sprint del 02/08.

- **`RecipeListState`** — paginación (`onFirstPageLoaded`, `onNextPageLoaded`,
  `nextPage`, `reset`), contenido de página (`firstPage`, `appendPage`), filtro
  local (`matchesQuery`) y textos (`statusText`, `shouldShowLoadMore`).
  `RecipeListView` conserva hilos, `Platform.runLater` y nodos, y delega el resto.
- **`GlobalSearchResults`** — record con las tres listas filtradas, más
  `notePreview`. `GlobalSearchView` solo construye nodos.

`SimpleCache` ya era plano y testeable: se cubre sin tocarlo.

### Android: usar el seam existente

`RecetasViewModel(container: AppContainer)` expone su estado como `StateFlow`.
Se testea con `mockk<AppContainer>`, que no invoca el constructor y por tanto no
construye Room ni necesita `Context`. `MainDispatcherRule` instala un dispatcher
de test como `Dispatchers.Main`.

Los `StateFlow` usan `SharingStarted.WhileSubscribed`, así que cada test debe
suscribirse desde `backgroundScope` antes de leer `.value`.

## Defectos encontrados y corregidos

1. **`GlobalSearchView.notePreview` lanzaba `StringIndexOutOfBoundsException`.**
   El límite del `substring` se calculaba sobre el cuerpo original y se aplicaba a
   la cadena ya colapsada por `replaceAll("\\s+", " ")`, que es más corta. Una nota
   de 80 caracteres o más con párrafos tumbaba la búsqueda global entera.
   Reproducido antes de corregir: `Range [0, 80) out of bounds for length 36`.

2. **`RecipeListView` podía duplicar filas al cargar más páginas.** El append no
   deduplicaba, y la caché es compartida con el menú semanal, que trae su propia
   página. `RecipeListState.appendPage` deduplica por id.

## Verificación

Además de que la suite pase, se comprobó que **la red detecta lo que dice
detectar**: mutando `SimpleCache.mergeById` para que se comporte como `replaceAll`,
3 de los 7 tests de `SimpleCacheSharingTest` fallan, incluido el que cubre que el
menú no recorte lo que el listado había paginado.

Corrección al plan: el plan proponía revertir la corrección en `WeeklyMenuView`,
pero esos tests no pasan por esa vista. El objetivo correcto de la mutación es
`SimpleCache.mergeById`, que es donde vive la lógica.

## Fuera de alcance

CI para clientes; TestFX, Monocle, Robolectric y Compose UI Test; capa de
presenters en Desktop; el resto de vistas; iOS (bloqueado sin macOS);
`AppSession.familyId` sin `volatile` (preexistente, sprint propio).
