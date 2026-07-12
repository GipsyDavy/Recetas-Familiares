# iOS — pertenecer a varias familias, cambiar familia activa, crear familia

Fecha: 2026-07-13
Estado: aprobado por el usuario (brainstorming), pendiente de plan de implementación.
Alcance: solo cliente iOS (`ios/composeApp/`). Sin cambios de backend.

## Contexto

Android y Desktop ya soportan que un usuario pertenezca a varias familias (hasta
`MAX_ACTIVE_MEMBERSHIPS = 10`, `backend/.../families/FamilyService.java:102`), cambiar de
familia activa y crear una familia nueva. iOS no tiene nada de esto: solo conoce una
familia fija, guardada en `SessionStore.familyId` al hacer login
(`ios/composeApp/src/commonMain/.../auth/AuthRepository.kt:21`).

Este spec cubre **solo** listar/cambiar/crear familia en iOS. "Copiar receta a otra
familia" es un spec independiente (Spec 2), posterior a este, porque depende de que
multi-familia funcione primero.

Fuera de alcance explícito de este spec:
- Copiar receta entre familias (Spec 2).
- Arreglar el bug conocido de `FamilyMemberRepository.invite()` en iOS (backend devuelve
  201 vacío, iOS espera JSON — bug preexistente, no bloquea este spec porque el sprint
  añade `createFamily` como vía alternativa para tener 2+ familias).
- Añadir `createFamily` a Desktop (hoy solo lo tiene Android; asimetría existente, no se
  toca aquí).
- Backend: no requiere cambios. No existe ni se crea un endpoint de "cambiar familia
  activa" — sigue siendo 100% estado de cliente, igual que en Android/Desktop.

## Hallazgo clave que simplifica el diseño

Los repositorios iOS ya leen `session.familyId` de forma dinámica en cada llamada, no lo
cachean en el constructor. Confirmado por grep (20 sitios en 7 archivos):
`RecipeRepository.kt`, `StockRepository.kt`, `NoteRepository.kt`, `MenuRepository.kt`,
`ShoppingListRepository.kt`, `SyncRepository.kt`, `FamilyMemberRepository.kt`.

Consecuencia: cambiar `session.familyId` redirige automáticamente todas las peticiones
**futuras** a la nueva familia sin tocar esos repos. Además, el schema SQLDelight
(`AppDatabase.sq`) ya tiene columna `familyId` por fila y las queries ya filtran por
familia — no hace falta vaciar caché local al cambiar de familia (a diferencia de
Android, que sí mantiene un `RecetasViewModel` grande con ~10 `StateFlow` que limpia
explícitamente en `clearFamilyScopedState()`,
`android/.../ui/RecetasViewModel.kt:327-339`).

Lo que sí falta: notificar a las pantallas ya abiertas de que la familia activa cambió,
para que vuelvan a pedir sus datos.

## Componentes

### 1. `SessionStore` (expect/actual, Keychain) — CORRECCIÓN: ya existe

Verificado en código (no en memoria): `familyRole: String?` y `familyRoleFlow:
StateFlow<String?>` **ya están implementados** (`core/SessionStore.kt:13-14`, Keychain
en `SessionStore.ios.kt:67-74`), añadidos en un sprint anterior para el check de
admin de `SettingsScreen.kt:78`. No requiere ningún cambio. (Versión anterior de este
spec decía que había que añadirlo — error corregido tras verificar el código real antes
de escribir el plan.)

### 2. DTOs — `network/ApiDtos.kt`

`FamilyDto` ya existe (`ApiDtos.kt:31`) pero sin `avatarUrl`:

```kotlin
// Antes: data class FamilyDto(val id: String, val name: String, val role: String? = null)
data class FamilyDto(val id: String, val name: String, val role: String? = null, val avatarUrl: String? = null)
data class CreateFamilyRequestDto(val name: String) // nuevo
```

Mismos campos que `FamilyResponse`/`CreateFamilyRequest` del backend
(`backend/.../families/FamilyResponse.java`, `CreateFamilyRequest.java`).

### 3. `FamilyMemberRepository.kt` (extender el existente)

Añadir a la clase ya existente (no crear otra):
- `suspend fun families(): List<FamilyDto>` → `GET /api/v1/families`.
- `suspend fun createFamily(name: String): FamilyDto` → `POST /api/v1/families`.
- `fun setActiveFamily(family: FamilyDto)` → escribe `session.familyId` +
  `session.familyRole`. **Sin llamada de red**, igual que Android
  (`android/.../data/repository/Repositories.kt:183-185`).

### 4. `FamilyViewModel` (nuevo, `families/FamilyViewModel.kt`)

Único punto de estado reactivo para familias en iOS:
- `families: StateFlow<List<FamilyDto>>`
- `activeFamily: StateFlow<FamilyDto?>`
- `loadFamilies()` — trae la lista, resuelve cuál es la activa (la que coincide con
  `session.familyId`, o la primera si no coincide ninguna — igual regla que Android en
  `refreshFamiliesFromServer()`, `RecetasViewModel.kt:360-380`).
- `switchActiveFamily(familyId: String)` — valida que exista en `families.value`,
  llama a `repository.setActiveFamily(target)`, actualiza `activeFamily`.
- `createFamily(name: String)` — llama al repositorio, recarga `families()`.

Las pantallas que muestran datos de familia (recetas, stock, notas, menú, lista de la
compra) colectan `activeFamily` en un `LaunchedEffect(activeFamily)` para volver a pedir
sus datos cuando cambia. No hace falta un "clear" de caché — ver sección anterior.

### 5. UI (adaptada a iOS, no clon de Android)

- Nueva entrada "Familias" en `ui/SettingsScreen.kt`.
- Nueva pantalla `families/FamilyListScreen.kt`: lista de familias con marca visual en
  la activa, tap para cambiar; acción de crear vía diálogo Compose Multiplatform con
  campo de texto (mismo toolkit que ya usa `OnboardingScreen.kt`, no `AlertDialog`
  Material de Android).
- Sin pantalla de "invitar a esta familia nueva" en este spec (ya existe `invite()` en
  Android/Desktop; en iOS ese flujo tiene el bug conocido documentado arriba, fuera de
  alcance).

### 6. Tests (bootstrap) — LIMITACIÓN DE ENTORNO CONOCIDA

iOS no tiene ningún test hoy (`commonTest` no existe). Se bootstrapea un source set
`commonTest` mínimo: `kotlin("test")` + `kotlinx-coroutines-test` + `ktor-client-mock`
(mismo `ktor` version.ref ya usado). `ApiClient` gana un parámetro opcional
`engine: HttpClientEngine? = null` (si no es null, se usa en vez del engine real) para
poder construir un `ApiClient` real de test contra `MockEngine` — se prueba el
`FamilyViewModel` + `FamilyMemberRepository` reales, no un doble a mano.

**Limitación bloqueante verificada:** este proyecto solo puede compilar la metadata
común de iOS en Windows (`:composeApp:compileKotlinMetadata`); compilar o ejecutar
código para los targets reales (`iosX64`/`iosArm64`/`iosSimulatorArm64`, incluidos sus
tests) requiere macOS + Xcode, no disponibles en esta máquina — la misma razón por la
que "iOS bloqueado" aparece repetido en el historial del proyecto. Los tests de este
spec se **escriben completos y correctos por diseño**, pero **no se ejecutan** en esta
sesión: no hay ciclo RED→GREEN real, solo verificación de que el código (producción +
tests) compila vía `compileKotlinMetadata`. Decisión explícita del usuario
(2026-07-13): escribir los tests sin ejecutarlos, dejar constancia clara de esta
limitación en vez de omitirlos o afirmar una ejecución que no ocurrió.

## Seguridad

Sin superficie nueva de riesgo: el backend revalida ownership/rol de `familyId` en cada
endpoint igual que hoy (path param, no hay claim de "familia activa" en el JWT que
falsificar). El único dato nuevo persistido en Keychain es `familyRole`, no sensible más
allá de lo que ya se guarda (`familyId`, tokens).

## Riesgos / limitaciones aceptadas

- No se corrige el bug de `invite()` en iOS — un usuario sin invitación previa solo puede
  tener 2+ familias creando una nueva él mismo.
- No hay paridad total con Android todavía tras este spec (falta "copiar receta", Spec 2).
- `commonTest` nuevo cubre solo `FamilyViewModel`, no es una suite general para todo iOS.
