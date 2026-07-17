# Spec: iOS copiar receta entre familias + Desktop crear familia

Fecha: 2026-07-17
Estado: aprobado en diseño (conversación), pendiente de revisión escrita del usuario.

## Contexto

- El backend expone desde Sprint D `POST /api/v1/families/{familyId}/recipes/{recipeId}/copy`
  (body `{"targetFamilyId": "..."}` → 201 con la receta copiada). Android y Desktop ya lo
  consumen; iOS no.
- `POST /api/v1/families` (crear familia) exige que el usuario sea OWNER o ADMIN en alguna
  membresía activa (o que no tenga ninguna) y aplica tope de 10 membresías
  (`FamilyService.createFamily`). Android expone el botón solo a OWNER/ADMIN; iOS lo muestra
  a todos los roles; Desktop no tiene la función.
- iOS ya tiene multi-familia (Spec 1, 2026-07-16): `FamilyMemberRepository`, `FamilyViewModel`
  y `FamilyListSheet` (listar/cambiar/crear familia desde Ajustes).

## Objetivo

1. iOS: copiar una receta de la familia activa a otra familia donde el usuario sea OWNER/ADMIN.
2. Desktop: crear familia desde la pantalla de miembros, con paridad funcional con Android.
3. iOS: ocultar "Crear familia" a usuarios que el backend rechazaría (sin rol OWNER/ADMIN en
   ninguna familia).

## Alcance 1 — iOS: copiar receta entre familias

Contrato (sin cambios backend):

- `POST /api/v1/families/{origen}/recipes/{recipeId}/copy`, body `{"targetFamilyId": "..."}`.
- Backend valida: membresía en la familia origen, rol OWNER/ADMIN en la destino,
  destino ≠ origen (400). Copia título, descripción, raciones, tiempos, dificultad,
  ingredientes, pasos y fotos (referencia compartida de `storagePath`).

Cambios iOS (`ios/composeApp/src/commonMain`):

1. `network/ApiDtos.kt`: añadir `CopyRecipeRequestDto(targetFamilyId: String)` serializable,
   espejo del DTO de Android.
2. `recipes/RecipeRepository.kt`: `suspend fun copyToFamily(recipeId, targetFamilyId): Boolean`.
   POST al endpoint con origen = `session.familyId`; `true` si 2xx, `false` en error
   (mismo patrón que `addFavorite`). Sin sesión de familia → `false`.
3. `recipes/RecipeDetailScreen.kt`:
   - Nuevo parámetro opcional `familyRepository: FamilyMemberRepository? = null`,
     inyectado desde la navegación (`App.kt`/`MainTabScreen.kt`).
   - `LaunchedEffect`: carga `families()`; destinos calculados con función pura
     `copyTargets(families, activeFamilyId)` = familias con rol OWNER/ADMIN distintas de la
     activa (mismo filtro que Android). Fallo de red → sin icono, sin error visible.
   - Icono "copiar" en la barra superior (junto a compartir), visible solo si hay destinos.
     `contentDescription`: "Copiar a otra familia".
   - Tap → `ModalBottomSheet` con el texto "La receta y sus fotos se copiarán a la familia
     elegida." y filas de familias (nombre + rol).
   - Tap en familia → `copyToFamily`, cierra el sheet, háptico distinto para éxito y error,
     mensaje en `SnackbarHost` local del detalle: "Receta y fotos copiadas a {nombre}" /
     "No se pudo copiar la receta". Tras copiar se permanece en la receta; la familia activa
     no cambia (paridad Android).

Decisión UX (usuario, 2026-07-17): icono directo en la barra superior, no menú "⋮".

## Alcance 2 — Desktop: crear familia

Cambios Desktop (`desktop/src/main/java/org/gipsybuho/recetasfamiliares`):

1. `api/dto/FamilyDtos.java`: añadir record `CreateFamilyRequest(String name)`.
2. `data/repository/FamilyRepository.java`: `createFamily(String name)` →
   `POST api/v1/families`, devuelve `FamilyResponse`.
3. `ui/FamilyMembersView.java`: botón "Crear familia" en la toolbar de administración
   (la toolbar ya es visible solo para OWNER/ADMIN vía `session.isAdmin()`).
   - Diálogo de nombre (estilizado con `DialogStyler`), validación de nombre no vacío.
   - Petición en hilo virtual (patrón existente); nunca bloquear el hilo JavaFX.
   - Éxito: barra de estado "Familia creada: {nombre}" y refresco del selector
     "Familia activa" del sidebar (callback provisto por `MainWindow`); la familia activa
     NO cambia automáticamente (paridad Android).
   - Error: mensaje claro en la barra de estado (403 → permiso insuficiente;
     400 → límite de familias alcanzado; resto → genérico).
4. `ui/MainWindow.java`: pasar a `FamilyMembersView` un callback para recargar el selector
   de familias del sidebar tras crear una.

Nota de paridad: Android muestra el botón según el rol en la familia activa; el backend
permite crear si se es OWNER/ADMIN en cualquier familia. Desktop hereda el criterio de la
toolbar (rol activo), igual que Android. La divergencia (MEMBER en la activa pero OWNER en
otra) queda cubierta por el backend y no se resuelve en UI en este sprint.

## Alcance 3 — iOS: visibilidad de "Crear familia"

`families/FamilyListSheet.kt`: mostrar el botón "Crear familia" solo si el usuario tiene rol
OWNER o ADMIN en alguna de sus familias (criterio idéntico al del backend), mediante función
pura testeable (por ejemplo `canCreateFamily(families)` junto a la lógica de familias).

## Fuera de alcance

- Cambios en backend y Android (ya cumplen los requisitos).
- Snackbar global iOS; el mensaje de copia es local al detalle de receta.
- Deuda conocida "respuesta `GET /families` vacía deja contexto local anterior"
  (sprint propio, documentado el 2026-07-16).
- Cambio automático a la familia recién creada o destino de copia.

## Seguridad

- La autoridad de permisos es siempre el backend (403/400); los clientes solo ocultan UI.
- Sin secretos nuevos, sin datos sensibles en logs ni mensajes de error.
- VibeSec sobre el diff antes del cierre (ownership multi-familia).

## Testing y validación

- iOS (limitación conocida: en Windows los tests iOS compilan pero no se ejecutan;
  RED = error de compilación, GREEN = `BUILD SUCCESSFUL`; nunca declarar "pasaron"):
  - Test de `copyTargets()` (filtro rol/activa).
  - Test de `RecipeRepository.copyToFamily` con MockEngine Ktor: 201, 403, sin sesión.
  - Test de `canCreateFamily()`.
- Desktop: `mvn test` (suite existente) + compilación; test unitario de
  `FamilyRepository.createFamily` si el harness HTTP fake existente lo permite;
  prueba manual del diálogo pendiente del usuario.
- Gate diferido a macOS: ejecutar los tests iOS en runtime y smoke de copia real.
