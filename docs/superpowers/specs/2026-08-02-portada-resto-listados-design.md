# Portada de receta en el resto de listados (Android + Desktop) — Diseño

**Continuación directa** del sprint cerrado el 2026-08-01 (`2026-08-01-portada-recetas-listado-design.md`,
merge `8baddf7`). Aquel llevó la portada al listado principal de recetas de Android y Desktop. Este
la lleva a las pantallas que quedaron fuera: búsqueda global, "Recetas recientes" del dashboard de
Desktop y el menú semanal de ambas plataformas.

## Corrección al backlog

`CONTINUAR.md` proponía como candidato "favoritos, menús semanales y búsqueda global". Verificado
contra el código en esta sesión: **no existe ninguna pantalla de favoritos**, ni en Android ni en
Desktop. Favoritos es un botón de alternar en el detalle de la receta (`RecipeDetailView.java:46`,
`RecipeScreens.kt:502`) y un repositorio contra `/favorite-recipes`, pero no hay listado que
mostrar. La entrada del backlog queda corregida.

A cambio aparecen dos listados que el candidato no mencionaba: **"Recetas recientes" del dashboard
de Desktop** (`DashboardView.renderRecipeCards`) y los **selectores de receta**
(`AssignMenuDialog` en Android, `showRecipePicker` en Desktop).

## Objetivo

Que la foto de la receta acompañe al usuario allí donde navega, no solo en el listado principal:
al buscar, al mirar el menú de la semana y al ver las recetas recientes.

## Estado de partida, verificado en esta sesión

| Plataforma | Pantalla | Portada hoy |
|---|---|---|
| Android | `RecipeList` / `RecipeCard` | Sí, 152 dp |
| Android | `GlobalSearchScreen`, sección "Recetas" | No |
| Android | `MenuScreen` → `MealRow` | No |
| Android | `MenuScreen` → `AssignMenuDialog` | No |
| Desktop | `RecipeListView` / `RecipeCell` | Sí, 56×56 |
| Desktop | `GlobalSearchView`, sección Recetas | No |
| Desktop | `DashboardView` → "Recetas recientes" | No |
| Desktop | `WeeklyMenuView` → `filledCell` y vista mensual | No |
| Desktop | `WeeklyMenuView` → `showRecipePicker` | No |

## Decisiones de alcance (confirmadas con el usuario, 2026-08-02)

1. **Entran**: búsqueda global (ambas plataformas), dashboard de Desktop, y el menú semanal de
   ambas. **Quedan fuera** los selectores de receta. Motivo: son diálogos de acción puntual, y el
   de Desktop es hoy un `ChoiceDialog<String>` de texto plano que habría que reescribir entero como
   `ListView` con celdas — el trozo más caro del sprint para el punto de menor tránsito.
2. **Miniatura pequeña, no card grande**, en las cinco pantallas. La card con foto de fondo sigue
   siendo exclusiva del listado principal de recetas. En el menú semanal se descartó la card grande
   porque una semana llena son hasta 14 tarjetas de 120 dp: convierte una vista de consulta rápida
   en un scroll largo.
3. **Con placeholder siempre**, también en las recetas sin foto. Se descartó omitir el hueco: deja
   los títulos en dos márgenes distintos y el ojo pierde la columna al hacer scroll.
4. **Sin tocar backend ni contrato.** El menú semanal de Desktop resuelve la portada cruzando el
   `recipeId` del `MenuItemDto` contra la caché de `RecipeRepository`, cuyos `RecipeDto` ya traen
   `coverThumbnailUrl` desde el sprint anterior. Se descartó añadir el campo a `MenuItemDto`
   (garantizaría la portada siempre, pero arrastra contrato compartido, sync y validación de los
   tres clientes según `CLAUDE.md` §3) y se descartó cargar las fotos que falten bajo demanda
   (hasta 14 peticiones al abrir una semana, y código concurrente nuevo en una vista que hoy no lo
   tiene).
5. **Componente de miniatura compartido en Desktop, y `RecipeCell` migra a él.** Se descartó dejar
   `RecipeCell` intacto: mantendría dos implementaciones del mismo guard de reciclado conviviendo, y
   la siguiente pantalla que liste recetas haría una tercera.
6. **Vista mensual del menú de Desktop fuera de alcance.** Sus celdas son diminutas y pueden
   acumular varias comidas por día.
7. **iOS fuera de alcance**, como en el sprint anterior: sin macOS no se puede ejecutar ni ver nada
   de lo que se escriba.

## Arquitectura

Sprint 100 % cliente. Cero cambios en backend, contrato, base de datos, sincronización e iOS.

```
ANDROID                                DESKTOP
───────────────────────────────        ─────────────────────────────────
Room                                   RecipeRepository.cache
 └ recipePhotoDao.observeCovers          └ RecipeDto.coverThumbnailUrl
     └ coversByRecipeId()  (ya existe)       (lo rellena el backend)
         └ viewModel.recipeCovers
              Map<recipeId, url>        ImageCache.fetch(url)  (ya existe)
                                          └ ApiClient.fetchImage
   ┌──────────┴──────────┐                    (JWT solo al origen)
   ▼                     ▼                        │
RecipeCard          RecipeThumb  NUEVO      RecipeThumbnail  NUEVO
(ya existe)          ├ búsqueda global       ├ RecipeCell (migra)
                     └ MealRow del menú      ├ GlobalSearchView
                                             ├ DashboardView
                                             └ WeeklyMenuView
```

En Android no hay lógica nueva. `viewModel.recipeCovers` (`RecetasViewModel.kt:84`) ya es un
`StateFlow<Map<String, String>>` por familia alimentado por Room; no es propiedad del listado de
recetas y cualquier pantalla puede consumirlo. Cambiar de familia cambia el `familyIdFlow` y el
mapa se recalcula solo.

En Desktop sí hay una pieza nueva de lógica: cruzar `MenuItemDto.recipeId` con la caché de recetas.
Vive en `RecipeRepository.coverUrlFor(recipeId)`, no en la vista, para poder testearla sin arrancar
JavaFX.

## Componentes

### `ui/RecipeThumbnail.java` — Desktop, nuevo

`StackPane` con placeholder e `ImageView`. Recibe el `AppContext` (para llegar a `ImageCache`) y el
tamaño en píxeles por constructor, y expone un solo método `show(String url)`. Absorbe lo que hoy
vive dentro de `RecipeCell` (`RecipeListView.java:265-345`):

- el guard `pendingUrl`, que descarta el resultado de una descarga cuando el nodo ya no le
  corresponde;
- la descarga en `Thread.ofVirtual()` contra `ImageCache.fetch`;
- el `Platform.runLater` para pintar;
- el fade sujeto a `MotionPreferences.isReducedMotion()`.

`RecipeCell` pierde `thumb`, `placeholder`, `thumbHolder` y `loadCover`, que pasan a ser el
componente. Comportamiento visual idéntico: mismo tamaño, mismo fade, mismo guard.

**Tamaños en Desktop**: 56 px en `RecipeListView` (el actual, sin cambio), en `GlobalSearchView` y
en `DashboardView`; 40 px en la celda del menú semanal, porque son siete columnas de un `GridPane` y
la celda es estrecha.

### `RecipeThumb` — Android, nuevo composable

Junto a `RecipeCovers.kt`. Caja cuadrada con `Icons.Outlined.Restaurant` sobre `secondaryContainer`
como placeholder y `Crossfade` a la `AsyncImage`, esquinas de `MaterialTheme.shapes.small`. Misma
receta visual que la card grande, en pequeño.

**Tamaños**: 56 dp en la búsqueda global, cuyo `ListItem` es holgado; 48 dp en `MealRow`, que es una
fila densa dentro de una card y ya lleva etiqueta de tipo de comida (72 dp), título y chevron. En
una pantalla de 360 dp quedan unos 200 dp para el título de la comida.

## Puntos de uso

| Archivo | Cambio |
|---|---|
| `GlobalSearchScreen.kt` | nuevo parámetro `recipeCovers`; `SearchResultRow` gana `leading` opcional, que solo usa la sección de recetas |
| `MenuScreen.kt` | nuevo parámetro `recipeCovers`; `MealRow` pinta miniatura de 48 dp cuando hay `recipeId` |
| `RecetasApp.kt` | pasa `recipeCovers` a las dos pantallas |
| `GlobalSearchView.java` | `resultRow` acepta la url de portada; solo la sección Recetas la pasa |
| `DashboardView.java` | `buildRecipeCard` antepone la miniatura al `HBox` |
| `WeeklyMenuView.java` | `filledCell` cruza `recipeId` con la caché; vista mensual sin tocar |
| `RecipeRepository.java` | nuevo `coverUrlFor(String recipeId)` |
| `RecipeListView.java` | `RecipeCell` migra al componente compartido |

## Repoblado de la caché en el menú de Desktop

`AppContext.clearFamilyScopedCaches()` (`AppContext.java:84`) vacía a la vez la caché de recetas y
la de imágenes, y se invoca al cambiar de familia y al cerrar sesión. Consecuencia directa del
enfoque elegido: si el usuario abre la aplicación y va directo a "Menú semanal" sin pasar por
"Recetas", la caché está vacía y **no se vería ni una miniatura**.

Solución dentro del mismo enfoque, sin tocar el backend: `WeeklyMenuView.refresh()` comprueba si la
caché de recetas está vacía y, solo en ese caso, dispara un `loadPage(0, 100)` en el hilo virtual
que ya usa para cargar el menú, antes de pintar las celdas. Una petición extra, únicamente cuando
hace falta, en una vista que ya hace red.

`DashboardView` no lo necesita: ya llama a `loadPage(0, 5)`. `GlobalSearchView` tampoco: si la
caché está vacía no hay resultados de recetas que mostrar.

## Errores y casos límite

| Caso | Comportamiento |
|---|---|
| Receta sin fotos | Placeholder. Nunca hueco vacío ni salto de layout |
| URL presente pero la descarga falla | Placeholder, sin mensaje de error: una miniatura no justifica interrumpir al usuario |
| Imagen corrupta (`image.isError()`) | Se descarta, queda el placeholder |
| Celda reciclada o vista re-renderizada durante la descarga | El guard `pendingUrl` descarta el resultado obsoleto |
| `recipeId` del menú ausente de la caché | Placeholder; el título de la comida se sigue viendo |
| Cambio de familia | Ambas cachés se vacían; el menú repuebla en su siguiente `refresh()` |
| Animaciones reducidas activadas | Sin fade: la imagen aparece directamente |

## Seguridad

Todas las descargas pasan por `ImageCache` → `ApiClient.fetchImage`, que restringe el JWT al origen
del backend. Las URLs proceden de la base de datos, así que ese punto no es negociable: **no se crea
ningún cliente HTTP nuevo**. Es exactamente la desviación deliberada que se documentó en el plan del
sprint anterior (Task 3) y que no hay que repetir.

El resto de la superficie no cambia: no hay endpoints nuevos, ni campos nuevos, ni entrada de
usuario que llegue a una ruta de archivo. La autorización sigue viviendo en el backend, que ya
filtra las portadas por familia.

## Testing

**Automatizado.** Desktop: `RecipeRepository.coverUrlFor` es lógica pura sobre la caché y se testea
sin JavaFX — receta con portada, receta sin portada, receta marcada como borrada, `recipeId`
desconocido, `recipeId` nulo. Android: no hay lógica nueva que testear; `coversByRecipeId` ya está
cubierta por `RecipeCoversTest`. No se escriben tests de UI: el proyecto no tiene infraestructura
para ejecutarlos en ninguna de las dos plataformas.

**Suites completas** de Desktop y Android más `assembleDebug`, y el escaneo
`run-security-scan.ps1 -Mode sprint` antes del cierre.

**Validación visual**, que es la que de verdad cubre este sprint. Desktop pilotado con `user32.dll`
desde PowerShell y Android por `adb`, con recetas sembradas cuya portada lleva su número en grande,
una de cada seis deliberadamente sin foto. Cinco escenarios:

1. El número de la miniatura coincide con su fila, en las cinco pantallas nuevas y también en el
   listado principal de recetas, que cambia de implementación al migrar `RecipeCell`.
2. Placeholder donde toca, sin descuadres.
3. Scroll con la caché de imágenes fría, sin fotos cruzadas.
4. Cambio de familia sin fotos de la familia anterior.
5. Menú semanal abierto directamente al arrancar, sin pasar por Recetas: las miniaturas aparecen.

## Riesgos

- **Migrar `RecipeCell` toca una pantalla ya validada en producción.** Mitigado repitiendo su
  validación visual, que se hace igualmente.
- **Más vistas comparten la caché de 32 MB**, así que habrá más expulsiones. Con miniaturas de 48-56
  px el impacto es menor que el de una sola portada original del listado principal.
- **El menú de Desktop depende de la caché de recetas.** Cubierto por el repoblado descrito arriba,
  pero si una familia tiene más de 100 recetas, las que caigan fuera de esa primera página no
  tendrán miniatura en el menú hasta que el listado las cargue.

## No-objetivos

- Selectores de receta: `AssignMenuDialog` (Android) y `showRecipePicker` (Desktop).
- Vista mensual del menú de Desktop.
- Cualquier cambio de contrato, backend, base de datos o sincronización.
- iOS.
- Que la búsqueda global abra la receta concreta en lugar de navegar a la pestaña Recetas. Es un
  defecto real y preexistente (`GlobalSearchScreen.kt:66`, `GlobalSearchView.java:69`), pero no es
  este sprint.
