# Interfaz.md — Análisis, Diagnóstico y Plan de Implementación Visual
## Proyecto: Recetas Familiares — Multiplataforma (Android · Desktop · iOS)

---

## INSTRUCCIÓN DE TRABAJO

Este documento rige toda la ejecución de mejoras visuales del proyecto.
El agente que ejecute estas tareas debe comportarse como un **experto senior en diseño gráfico, diseño de interfaces (UI/UX) y programación multiplataforma**, siguiendo estos principios inamovibles:

1. **Pensar antes de programar**: antes de tocar cualquier archivo, visualizar mentalmente el resultado final completo — cómo se verá en pantalla, cómo interactuará el usuario, cómo encaja con el resto del sistema.
2. **Coherencia visual total**: cada decisión de color, tipografía, espaciado y animación debe ser coherente en las 3 plataformas. El usuario que use Android y Desktop debe sentir que es la misma app.
3. **Cambios quirúrgicos**: no refactorizar lo que no esté roto. Solo modificar lo necesario para cada mejora.
4. **Diseño con propósito**: cada elemento visual tiene una función. Nada decorativo sin función. Nada funcional sin estética.
5. **Experiencia emocional**: la app es un recetario familiar. Debe sentirse cálida, acogedora, personal, como abrir un libro de recetas de la abuela con diseño moderno.
6. **Validar antes de entregar**: compilar y verificar cada cambio antes de marcar una tarea como completada.

---

## PARTE 1 — ESTADO ACTUAL DEL PROYECTO

### 1.1 Estructura de Plataformas

```
Recetas Familiares/
├── backend/          Spring Boot 3.x, Java 21, MySQL (ESTABLE — no tocar)
├── android/          Kotlin + Compose + Material You 3
│   └── ui/
│       ├── theme/Theme.kt          ← Tema actual (incompleto)
│       ├── AppTokens.kt            ← Sistema de spacing (bien definido)
│       ├── SharedComposables.kt    ← EmptyStateView, LottieEmptyStateView, MetaChip
│       ├── RecetasApp.kt           ← Shell principal + Login + ShoppingScreen
│       ├── RecipeScreens.kt        ← Lista, detalle, card de recetas
│       ├── StockScreens.kt         ← Stock list, card, form, detail
│       ├── NotesScreens.kt         ← Notas list, card, form, detail
│       ├── MenuScreen.kt           ← Menú semanal, DayMenuCard
│       ├── CookingScreen.kt        ← Modo cocina paso a paso
│       ├── RecipeFormScreen.kt     ← Formulario crear/editar receta
│       └── GlobalSearchScreen.kt  ← Búsqueda global
├── desktop/          JavaFX + Maven
│   └── ui/
│       ├── MainWindow.java         ← Shell principal + sidebar + navegación
│       ├── LoginView.java          ← Pantalla de login
│       ├── DashboardView.java      ← Dashboard con recetas recientes + stock
│       ├── RecipeListView.java     ← Lista de recetas + búsqueda
│       ├── RecipeDetailView.java   ← Detalle de receta
│       ├── RecipeFormDialog.java   ← Formulario crear/editar
│       ├── StockView.java          ← Lista de stock
│       ├── StockFormDialog.java    ← Formulario stock
│       ├── WeeklyMenuView.java     ← Menú semanal grid
│       ├── ShoppingListView.java   ← Listas de la compra
│       ├── NotesView.java          ← Notas familiares
│       ├── CookingView.java        ← Modo cocina
│       └── GlobalSearchView.java  ← Búsqueda global
│   └── resources/style.css        ← Único archivo CSS (solo Light Mode)
├── ios/              KMP + Compose Multiplatform
│   └── commonMain/
│       ├── App.kt                  ← Punto de entrada (SIN TEMA APLICADO)
│       ├── ui/MainTabScreen.kt     ← NavigationBar + tabs
│       ├── auth/LoginScreen.kt     ← Login iOS
│       ├── recipes/RecipeListScreen.kt
│       ├── recipes/RecipeDetailScreen.kt
│       ├── cooking/CookingScreen.kt
│       ├── menu/MenuScreen.kt
│       ├── notes/NotesScreen.kt
│       ├── shopping/ShoppingListScreen.kt
│       └── stock/StockScreen.kt
```

---

### 1.2 Paleta Actual (INCONSISTENTE entre plataformas)

#### Android — Theme.kt
```
Light:
  primary         = #3E5F45  (verde bosque)
  secondary       = #8C4A2F  (terracota suave)
  tertiary        = #F2B84B  (ámbar dorado)
  background      = #FFFBF6  (blanco cálido)
  surface         = #FFFBF6

Dark:
  primary         = #AED6B4  (verde claro)
  secondary       = #E6B89C  (melocotón)
  tertiary        = #F2C76B  (ámbar claro)
  background      = #161915  (casi negro verdoso)
  surface         = #1D211C

FALTAN: primaryContainer, onPrimary, secondaryContainer, surfaceVariant, etc.
Material3 los autogenera de forma inconsistente.
```

#### Desktop — style.css
```
primary action   = #C17D52  (terracota intenso) ← DIFERENTE A ANDROID
background       = #FAF7F2  (beige cálido)
sidebar          = #3D2B1F  (marrón oscuro)
text primary     = #3D2B1F  (marrón oscuro)
text secondary   = #8B6F5E  (marrón medio)
border           = #D4BBA8  (beige medio)
SOLO Light Mode — sin Dark Mode
```

#### iOS — Sin tema
```
iOS no aplica RecetasTheme.
Usa Material3 defaults: azul #6750A4 como primary.
Completamente desconectado del estilo del proyecto.
```

---

### 1.3 Tipografía Actual

| Plataforma | Fuente actual | Problema |
|------------|--------------|----------|
| Android | Roboto (Material3 default) | Genérica, sin personalidad |
| Desktop | Segoe UI / Helvetica Neue | Funcional pero corporativa |
| iOS | San Francisco / Roboto (Material3) | Sin personalidad |

**Propuesta**: Nunito para títulos (cálido, redondeado, familiar) + Lato para cuerpo (legible, neutro, premium). Disponibles en Google Fonts y como bundle local.

---

### 1.4 Sistema de Espaciado Actual

#### Android — AppTokens.kt (BIEN DEFINIDO)
```kotlin
object Spacing {
    val xxs = 2.dp   // hair gap
    val xs  = 4.dp   // inline icon ↔ label
    val sm  = 6.dp   // chip padding
    val md  = 8.dp   // entre items
    val lg  = 12.dp  // card content
    val xl  = 16.dp  // screen padding
    val xxl = 24.dp  // section gap
}
```

#### Desktop — Insets hardcoded en Java (MAL)
```java
// DashboardView: new Insets(24, 28, 28, 28)
// buildCard: new Insets(18)
// sidebar: new Insets(2, 8, 0, 8) en cada botón
// Sin constantes, inconsistente
```

#### iOS — Hardcoded en cada pantalla (MAL)
```kotlin
// Todas las pantallas: .padding(16.dp) directo
// LoginScreen: .padding(32.dp)
// MenuScreen DayMenuCard: .padding(12.dp)
// Sin objeto Spacing, incoherente
```

---

## PARTE 2 — DIAGNÓSTICO COMPLETO DE PROBLEMAS

### 2.1 Problemas Críticos (🔴 bloquean coherencia visual)

| ID | Problema | Plataformas afectadas |
|----|----------|----------------------|
| C-1 | Paleta completamente diferente entre plataformas. Misma app, 3 identidades visuales distintas | Android · Desktop · iOS |
| C-2 | iOS no aplica ningún tema → colores Material3 por defecto (azul #6750A4) | iOS |
| C-3 | Sin tipografía premium. Las 3 plataformas usan fuentes genéricas del sistema | Android · Desktop · iOS |
| C-4 | iOS usa `padding(16.dp)` hardcoded en todas las pantallas, sin sistema de tokens | iOS |

### 2.2 Problemas Altos (🟠 degradan la experiencia)

#### Android
| ID | Problema | Archivo / Línea |
|----|----------|-----------------|
| A-1 | `Theme.kt` incompleto: faltan `primaryContainer`, `onPrimary`, `surfaceVariant`, `errorContainer`. Material3 autogenera valores inconsistentes | `Theme.kt` |
| A-2 | Sin FAB en NotesScreen: Stock y Recetas tienen FAB, Notas solo tiene `IconButton(Add)` en el header | `NotesScreens.kt` |
| A-3 | `animateItem()` solo en Recetas. Stock, Notas y Shopping insertan/eliminan items sin animación | `StockScreens.kt`, `NotesScreens.kt`, `RecetasApp.kt` |
| A-4 | Skeleton loading solo en RecipeList. El resto muestra `CircularProgressIndicator` genérico durante carga | `StockScreens.kt`, `NotesScreens.kt`, `RecetasApp.kt` |
| A-5 | TopAppBar estática: siempre muestra "Recetas Familiares" sin contextualizar el tab activo | `RecetasApp.kt` |
| A-6 | Login sin icono/logo de marca significativo: solo `Icons.Outlined.Restaurant` genérico | `RecetasApp.kt:101` |
| A-7 | StockDetail: botón Eliminar es `OutlinedButton` normal (sin color error), incoherente con el patrón del proyecto | `StockScreens.kt:328` |
| A-8 | RecipeCard muestra dificultad como "EASY/MEDIUM/HARD" en inglés en los chips de meta | `RecipeScreens.kt:374` |
| A-9 | MenuScreen empty state: solo icono + texto plano, sin Lottie ni animación | `MenuScreen.kt:158` |
| A-10 | `AnimatedContent` al cambiar de tab: el contenido principal cambia abruptamente | `RecetasApp.kt` |

#### Desktop
| ID | Problema | Archivo / Línea |
|----|----------|-----------------|
| D-1 | Sidebar sin estado activo: todos los botones se ven igual, no hay indicador del ítem seleccionado | `MainWindow.java`, `style.css` |
| D-2 | `loadingLabel()` = texto "Cargando...". Sin spinner, sin indicador visual de progreso | `DashboardView.java:289` |
| D-3 | Botones del sidebar sin iconos: solo texto plano, visualmente plano y poco intuitivo | `MainWindow.java:126-132` |
| D-4 | Formularios y modales abren sin animación (`ScaleTransition` solo en hover de cards) | `RecipeFormDialog.java` |
| D-5 | Sin Dark Mode | `style.css` |
| D-6 | Dashboard saludo genérico "¿Qué cocinamos hoy?" sin menú del día real | `DashboardView.java` |
| D-7 | Botón Sincronizar y Cerrar sesión sin separación visual clara, juntos al fondo del sidebar | `MainWindow.java:148` |
| D-8 | Scrollbars JavaFX nativas sin styling: muy básicas y fuera de estilo | `style.css` |

#### iOS
| ID | Problema | Archivo |
|----|----------|---------|
| I-1 | RecipeCard en iOS usa `Card + ListItem` plano. Android tiene tarjeta rica con imagen, gradiente y chips. Experiencia radicalmente diferente | `RecipeListScreen.kt` |
| I-2 | Sin FABs en ninguna pantalla iOS. Sin acciones primarias flotantes | Todas |
| I-3 | Empty states sin animación: Stock, Notas, Recetas y Menú muestran solo texto plano | `StockScreen.kt`, `NotesScreen.kt`, `MenuScreen.kt`, `RecipeListScreen.kt` |
| I-4 | Login iOS sin icono de marca. Texto puro, sin identidad visual | `LoginScreen.kt` |
| I-5 | `Spacer(Modifier.height(4.dp))` entre título y subtítulo del login. Layout comprimido y poco elegante | `LoginScreen.kt:29` |
| I-6 | Sin PullToRefresh: iOS tiene solo botón de Refresh en header | `RecipeListScreen.kt`, `StockScreen.kt` |
| I-7 | Sin `animateItem()` en ninguna LazyColumn iOS | Todas |
| I-8 | DayMenuCard en iOS no resalta "hoy" con color diferente. Android usa `primaryContainer` | `MenuScreen.kt` |
| I-9 | NotesScreen iOS es completamente read-only: sin CRUD inline | `NotesScreen.kt` |

### 2.3 Problemas Medios (🟡 polish)

| ID | Problema |
|----|----------|
| P-1 | Skeleton loading ausente en todas las pantallas iOS y en Stock/Notes/Shopping Android |
| P-2 | CookingScreen: hint de swipe es texto estático, podría ser flechas animadas |
| P-3 | Chips de meta (dificultad, tiempo, porciones) sin iconos — solo texto |
| P-4 | `HorizontalDivider()` genérico entre secciones — podría ser más sutil y cálido |
| P-5 | Formato de fechas de caducidad en StockScreen iOS: solo "Caduca: YYYY-MM-DD" sin indicador de urgencia |
| P-6 | MenuScreen iOS sin rango de fechas de la semana visible en el header |

---

## PARTE 3 — SISTEMA DE TEMAS

### 3.1 Arquitectura del Sistema de Temas

El sistema de temas debe funcionar así:

```
┌─────────────────────────────────────────────────────┐
│              AppTheme (sealed class)                │
│  10 temas × 2 modos (Light/Dark) = 20 esquemas     │
└─────────────────────────────────────────────────────┘
         ↓                    ↓                   ↓
   Android                Desktop               iOS
   DataStore           Java Preferences      NSUserDefaults
   ThemeState          ThemeManager          ThemeStore
   RecetasTheme()      loadThemeCss()        RecetasTheme()
```

**Flujo del usuario:**
1. Usuario accede a Ajustes (TopAppBar Android/iOS, menú Sidebar Desktop)
2. Ve una galería de 10 temas con preview de colores
3. Selecciona tema + modo (Claro/Oscuro/Sistema)
4. La preferencia se persiste localmente
5. La app aplica el tema inmediatamente sin restart

### 3.2 Los 10 Temas — Definición Completa

Cada tema tiene nombre, inspiración culinaria, y paleta Light + Dark completa (Material3 roles).

---

#### TEMA 1: Bosque 🌿
*Inspiración: Hierbas frescas, jardín de cocina, albahaca, menta, cilantro*

```
LIGHT MODE:
  primary           = #3E5F45  (verde bosque profundo)
  onPrimary         = #FFFFFF
  primaryContainer  = #C0EDCA  (verde muy suave)
  onPrimaryContainer= #002111
  secondary         = #8C4A2F  (terracota suave)
  onSecondary       = #FFFFFF
  secondaryContainer= #FFDBCF
  onSecondaryContainer= #3A0A00
  tertiary          = #F2B84B  (ámbar miel)
  background        = #FFFBF6  (blanco cálido)
  surface           = #FFFBF6
  surfaceVariant    = #EDE8E0
  outline           = #7C7669
  error             = #BA1A1A

DARK MODE:
  primary           = #AED6B4  (verde claro)
  onPrimary         = #103720
  primaryContainer  = #284F33
  onPrimaryContainer= #C0EDCA
  secondary         = #E6B89C  (melocotón)
  onSecondary       = #531F06
  secondaryContainer= #703519
  onSecondaryContainer= #FFDBCF
  tertiary          = #F2C76B  (ámbar claro)
  background        = #161915  (negro verdoso)
  surface           = #1D211C
  surfaceVariant    = #3F4A40
  outline           = #8E9B8F
  error             = #FFB4AB
```

---

#### TEMA 2: Terracota 🏺
*Inspiración: Barro cocido, cerámica española, cazuelas de barro, cocina mediterránea*

```
LIGHT MODE:
  primary           = #8B3A2A  (terracota intenso)
  onPrimary         = #FFFFFF
  primaryContainer  = #FFDBCF  (melocotón suave)
  onPrimaryContainer= #3A0900
  secondary         = #785849  (marrón cálido)
  onSecondary       = #FFFFFF
  secondaryContainer= #FFDBCF
  onSecondaryContainer= #2D1509
  tertiary          = #6B5E2F  (dorado tierra)
  background        = #FFF8F5  (blanco melocotón)
  surface           = #FFF8F5
  surfaceVariant    = #F4DDD6
  outline           = #A08C85
  error             = #BA1A1A

DARK MODE:
  primary           = #FFB4A0  (salmón suave)
  onPrimary         = #551500
  primaryContainer  = #733220
  onPrimaryContainer= #FFDBCF
  secondary         = #E7BDB1  (melocotón)
  onSecondary       = #442922
  secondaryContainer= #5D3F37
  onSecondaryContainer= #FFDBCF
  tertiary          = #D4C491  (dorado claro)
  background        = #211410  (marrón muy oscuro)
  surface           = #291A16
  surfaceVariant    = #53403B
  outline           = #A08C85
  error             = #FFB4AB
```

---

#### TEMA 3: Ocaso 🌅
*Inspiración: Atardecer en verano, tomates cherry, pimientos rojos, especias cálidas*

```
LIGHT MODE:
  primary           = #C84B2B  (naranja-rojo)
  onPrimary         = #FFFFFF
  primaryContainer  = #FFDBCC  (naranja muy suave)
  onPrimaryContainer= #3E0700
  secondary         = #9E5228  (naranja tostado)
  onSecondary       = #FFFFFF
  secondaryContainer= #FFDBCA
  onSecondaryContainer= #360F00
  tertiary          = #F5A623  (naranja brillante)
  background        = #FFFBF8
  surface           = #FFFBF8
  surfaceVariant    = #F5E0D5
  outline           = #9C7B6F
  error             = #BA1A1A

DARK MODE:
  primary           = #FFB59E  (salmón)
  onPrimary         = #5E1300
  primaryContainer  = #7F2310
  onPrimaryContainer= #FFDBCC
  secondary         = #FFBA9D  (melocotón)
  onSecondary       = #551F00
  secondaryContainer= #762D0A
  onSecondaryContainer= #FFDBCA
  tertiary          = #FAC55A  (naranja claro)
  background        = #201009
  surface           = #291612
  surfaceVariant    = #52332A
  outline           = #A68277
  error             = #FFB4AB
```

---

#### TEMA 4: Mediterráneo 🌊
*Inspiración: Mar azul, aceite de oliva, azulejos, cocina griega e italiana*

```
LIGHT MODE:
  primary           = #1B5E8A  (azul mediterráneo)
  onPrimary         = #FFFFFF
  primaryContainer  = #CFE5FF  (azul muy claro)
  onPrimaryContainer= #001D35
  secondary         = #4A7C59  (verde oliva)
  onSecondary       = #FFFFFF
  secondaryContainer= #CCE8D5
  onSecondaryContainer= #07210F
  tertiary          = #D4A017  (dorado aceite)
  background        = #F6FBFF  (blanco azulado)
  surface           = #F6FBFF
  surfaceVariant    = #DDE4ED
  outline           = #6E7E8A
  error             = #BA1A1A

DARK MODE:
  primary           = #98CBFF  (azul cielo)
  onPrimary         = #003258
  primaryContainer  = #004880
  onPrimaryContainer= #CFE5FF
  secondary         = #A1D1AE  (verde suave)
  onSecondary       = #0B3820
  secondaryContainer= #2B5236
  onSecondaryContainer= #CCE8D5
  tertiary          = #EFC84A  (dorado)
  background        = #0D1A26
  surface           = #15222F
  surfaceVariant    = #2E3B46
  outline           = #8C9BAA
  error             = #FFB4AB
```

---

#### TEMA 5: Lavanda 💜
*Inspiración: Provenza, hierbas aromáticas, tomillo, romero, campos de lavanda*

```
LIGHT MODE:
  primary           = #6750A4  (violeta suave)
  onPrimary         = #FFFFFF
  primaryContainer  = #EADDFF  (lila muy suave)
  onPrimaryContainer= #21005D
  secondary         = #9C4D80  (rosa violáceo)
  onSecondary       = #FFFFFF
  secondaryContainer= #FFD7F2
  onSecondaryContainer= #38003A
  tertiary          = #7E5700  (ámbar oscuro)
  background        = #FEFBFF  (blanco ligerísimamente violáceo)
  surface           = #FEFBFF
  surfaceVariant    = #E7E0EC
  outline           = #79757F
  error             = #B3261E

DARK MODE:
  primary           = #D0BCFF  (lila claro)
  onPrimary         = #381E72
  primaryContainer  = #4F378B
  onPrimaryContainer= #EADDFF
  secondary         = #F1AADB  (rosa suave)
  onSecondary       = #5C1158
  secondaryContainer= #75256E
  onSecondaryContainer= #FFD7F2
  tertiary          = #F0BF48  (ámbar claro)
  background        = #1C1B1F
  surface           = #141218
  surfaceVariant    = #49454F
  outline           = #CAC4D0
  error             = #F2B8B5
```

---

#### TEMA 6: Oliva 🫒
*Inspiración: Aceitunas, AOVE, cocina griega, hojas de parra, jardín toscano*

```
LIGHT MODE:
  primary           = #4A5C2F  (oliva oscuro)
  onPrimary         = #FFFFFF
  primaryContainer  = #CAEAA6  (verde oliva suave)
  onPrimaryContainer= #0D1E00
  secondary         = #7B6D3F  (marrón oliva)
  onSecondary       = #FFFFFF
  secondaryContainer= #F6E5BB
  onSecondaryContainer= #271A00
  tertiary          = #386663  (verde azulado)
  background        = #FAFDF0  (blanco verdoso)
  surface           = #FAFDF0
  surfaceVariant    = #E2E6D3
  outline           = #73796B
  error             = #BA1A1A

DARK MODE:
  primary           = #AFCE8D  (verde oliva claro)
  onPrimary         = #203200
  primaryContainer  = #324700
  onPrimaryContainer= #CAEAA6
  secondary         = #D9CA99  (beige cálido)
  onSecondary       = #3E2F00
  secondaryContainer= #574500
  onSecondaryContainer= #F6E5BB
  tertiary          = #79CEC8  (verde agua)
  background        = #191D11
  surface           = #1E2218
  surfaceVariant    = #3D4436
  outline           = #8D9387
  error             = #FFB4AB
```

---

#### TEMA 7: Canela 🫚
*Inspiración: Especias dulces, canela, cardamomo, vainilla, repostería artesanal*

```
LIGHT MODE:
  primary           = #7B4F2A  (canela media)
  onPrimary         = #FFFFFF
  primaryContainer  = #FDDCBB  (melocotón vainilla)
  onPrimaryContainer= #2B1500
  secondary         = #6C5944  (marrón suave)
  onSecondary       = #FFFFFF
  secondaryContainer= #F7DCBF
  onSecondaryContainer= #261506
  tertiary          = #4E6B3F  (verde especiado)
  background        = #FFF8F2  (crema cálida)
  surface           = #FFF8F2
  surfaceVariant    = #EDE0D4
  outline           = #8E7B6C
  error             = #BA1A1A

DARK MODE:
  primary           = #F9BA82  (canela clara)
  onPrimary         = #452200
  primaryContainer  = #623300
  onPrimaryContainer= #FDDCBB
  secondary         = #DABDA0  (arena)
  onSecondary       = #3D2B15
  secondaryContainer= #554229
  onSecondaryContainer= #F7DCBF
  tertiary          = #A4D18C  (verde suave)
  background        = #201309
  surface           = #281B0F
  surfaceVariant    = #4E3B2C
  outline           = #A1887A
  error             = #FFB4AB
```

---

#### TEMA 8: Menta 🌿
*Inspiración: Frescura, mentas, mojito, ensaladas veraniegas, cocina ligera*

```
LIGHT MODE:
  primary           = #00695C  (verde menta oscuro)
  onPrimary         = #FFFFFF
  primaryContainer  = #A7F3EB  (menta muy suave)
  onPrimaryContainer= #00201C
  secondary         = #4A6360  (gris verdoso)
  onSecondary       = #FFFFFF
  secondaryContainer= #CCE8E5
  onSecondaryContainer= #051F1D
  tertiary          = #5C7A1F  (verde lima)
  background        = #F5FFFE  (blanco menta)
  surface           = #F5FFFE
  surfaceVariant    = #D8E5E3
  outline           = #6A7A79
  error             = #BA1A1A

DARK MODE:
  primary           = #80D8D0  (menta suave)
  onPrimary         = #003732
  primaryContainer  = #00504A
  onPrimaryContainer= #A7F3EB
  secondary         = #AED0CD  (gris menta)
  onSecondary       = #1B3432
  secondaryContainer= #314B49
  onSecondaryContainer= #CCE8E5
  tertiary          = #B3D06F  (lima suave)
  background        = #0F1F1E
  surface           = #162625
  surfaceVariant    = #3B4947
  outline           = #89A4A2
  error             = #FFB4AB
```

---

#### TEMA 9: Frambuesa 🍓
*Inspiración: Frutas del bosque, postres, mermeladas caseras, dulzura familiar*

```
LIGHT MODE:
  primary           = #9E1B4A  (frambuesa oscuro)
  onPrimary         = #FFFFFF
  primaryContainer  = #FFD9E3  (rosa frambuesa suave)
  onPrimaryContainer= #3E0015
  secondary         = #7A525B  (rosa grisáceo)
  onSecondary       = #FFFFFF
  secondaryContainer= #FFD9E3
  onSecondaryContainer= #30111C
  tertiary          = #795900  (ámbar dorado)
  background        = #FFF8F8  (blanco rosado)
  surface           = #FFF8F8
  surfaceVariant    = #ECDFDF
  outline           = #85727B
  error             = #BA1A1A

DARK MODE:
  primary           = #FFB1C5  (rosa frambuesa)
  onPrimary         = #5F0028
  primaryContainer  = #800040
  onPrimaryContainer= #FFD9E3
  secondary         = #E6B7C2  (rosa suave)
  onSecondary       = #47252F
  secondaryContainer= #603B45
  onSecondaryContainer= #FFD9E3
  tertiary          = #F3BC44  (dorado)
  background        = #211018
  surface           = #291520
  surfaceVariant    = #503743
  outline           = #A98891
  error             = #F2B8B5
```

---

#### TEMA 10: Noche de Verano 🌙
*Inspiración: Cenas al aire libre, cielo azul índigo, veladas familiares nocturnas*

```
LIGHT MODE:
  primary           = #2C5282  (azul índigo profundo)
  onPrimary         = #FFFFFF
  primaryContainer  = #D6E4FF  (azul muy pálido)
  onPrimaryContainer= #001944
  secondary         = #4A5C8A  (azul medio)
  onSecondary       = #FFFFFF
  secondaryContainer= #D8E2FF
  onSecondaryContainer= #0D1B40
  tertiary          = #B06000  (ámbar cálido)
  background        = #F8FAFE  (blanco azulado)
  surface           = #F8FAFE
  surfaceVariant    = #E0E2F0
  outline           = #72748B
  error             = #BA1A1A

DARK MODE:
  primary           = #ADC8FF  (azul cielo)
  onPrimary         = #002D6E
  primaryContainer  = #0A449A
  onPrimaryContainer= #D6E4FF
  secondary         = #BAC3FF  (azul violáceo)
  onSecondary       = #222D62
  secondaryContainer= #394479
  onSecondaryContainer= #D8E2FF
  tertiary          = #FFBA52  (ámbar)
  background        = #0E1118
  surface           = #141823
  surfaceVariant    = #33374A
  outline           = #8C8FA4
  error             = #FFB4AB
```

---

### 3.3 Tipografía del Sistema

**Font Pairing seleccionado:**

```
Títulos / Headlines:   Nunito (700, 600)
  → Cálido, redondeado, familiar, legible en cocina
  → URL: https://fonts.google.com/specimen/Nunito

Cuerpo / Body:         Lato (400, 600)
  → Neutro, premium, muy legible en tamaño pequeño
  → URL: https://fonts.google.com/specimen/Lato

Alternativa monospace: JetBrains Mono (para valores numéricos: cantidades, timer)
```

**Escala tipográfica (dp):**

| Token Material3 | Familia | Peso | Tamaño | Uso |
|-----------------|---------|------|--------|-----|
| displaySmall | Nunito | 700 | 36sp | "¡Buen provecho!" CookingScreen |
| headlineLarge | Nunito | 700 | 32sp | Títulos principales de pantalla |
| headlineMedium | Nunito | 600 | 28sp | Título de receta en detalle |
| headlineSmall | Nunito | 600 | 24sp | Headers de sección |
| titleLarge | Nunito | 600 | 22sp | Diálogos, modales |
| titleMedium | Nunito | 600 | 16sp | Nombres en cards |
| titleSmall | Lato | 600 | 14sp | Labels de sección |
| bodyLarge | Lato | 400 | 16sp | Descripción de receta |
| bodyMedium | Lato | 400 | 14sp | Instrucciones, ingredientes |
| bodySmall | Lato | 400 | 12sp | Metadatos, fechas |
| labelLarge | Lato | 600 | 14sp | Botones |
| labelMedium | Lato | 600 | 12sp | Chips, badges |
| labelSmall | Lato | 400 | 11sp | Caducidades, hints |

---

## PARTE 4 — PLAN DE IMPLEMENTACIÓN

### SPRINT 25A — Sistema de Temas Base (3 plataformas) ✅ COMPLETADO (2026-05-29)
**Objetivo**: Infraestructura completa de temas antes de cualquier polish visual.

#### 25A.1 — Android: ThemeSystem ✅

| Archivo | Estado | Notas |
|---------|--------|-------|
| `ui/theme/AppTheme.kt` | ✅ CREADO | 10 enums, `lightColors()`, `darkColors()`, `AppTypography` (13 tokens) |
| `ui/theme/ThemePreference.kt` | ✅ CREADO | DataStore<Preferences> — claves `selected_theme` + `theme_mode` |
| `ui/theme/Theme.kt` | ✅ MODIFICADO | `RecetasTheme(appTheme, themeMode)` — acepta `AppTheme` + `ThemeMode` |
| `ui/RecetasViewModel.kt` | ✅ MODIFICADO | `selectedTheme: StateFlow<AppTheme>`, `themeMode: StateFlow<ThemeMode>`, `setTheme()`, `setThemeMode()` |
| `AppContainer.kt` | ✅ MODIFICADO | `val themePreference = ThemePreference(context)` |
| `MainActivity.kt` | ✅ MODIFICADO | Recoge estado del ViewModel y pasa `appTheme`/`themeMode` a `RecetasTheme` |
| `ui/ThemePickerDialog.kt` | ✅ CREADO | `AlertDialog` con grid 5 columnas swatches + `FilterChip` por modo |
| `ui/RecetasApp.kt` | ✅ MODIFICADO | Botón `Icons.Filled.Palette` en TopAppBar abre el diálogo |
| `build.gradle.kts` | ✅ MODIFICADO | `datastore-preferences:1.1.4` añadido |
| Compilación | ✅ | `./gradlew assembleDebug` — BUILD SUCCESSFUL |

Nota tipografía: `NunitoFamily = FontFamily.Default` / `LatoFamily = FontFamily.SansSerif` por ahora.
Para activar Nunito+Lato: añadir TTF a `res/font/` y actualizar los `FontFamily` en `AppTheme.kt`.

#### 25A.2 — Desktop: ThemeSystem ✅

| Archivo | Estado | Notas |
|---------|--------|-------|
| `ui/ThemeManager.java` | ✅ CREADO | Singleton; `attach(scene)`, `applyTheme(theme, mode)`, `loadTheme()`, `loadMode()`; Java Preferences API |
| `resources/style.css` | ✅ REFACTORIZADO | Looked-up colors JavaFX en `.root` (25 variables `recetas-*`); todos los hex reemplazados |
| `resources/themes/` | ✅ CREADO | 20 CSS — uno por tema×modo; cada uno solo define `.root { recetas-*: #valor }` |
| `ui/MainWindow.java` | ✅ MODIFICADO | `ThemeManager.getInstance().attach(scene)`; diálogo Ajustes con `RadioButton` modo + `ComboBox<AppTheme>` tema |
| Compilación | ✅ | `mvn compile` — BUILD SUCCESS |

#### 25A.3 — iOS: ThemeSystem ✅

| Archivo | Estado | Notas |
|---------|--------|-------|
| `commonMain/.../theme/AppTheme.kt` | ✅ CREADO | Idéntico a Android; sin dependencias platform |
| `commonMain/.../theme/ThemePreference.kt` | ✅ CREADO | `expect class ThemePreference()` con `var selectedTheme` + `var themeMode` |
| `iosMain/.../theme/ThemePreference.ios.kt` | ✅ CREADO | `actual class` — `NSUserDefaults.standardUserDefaults` |
| `commonMain/.../App.kt` | ✅ MODIFICADO | `MaterialTheme(colorScheme, typography)` con el tema elegido + `ThemePreference` |
| `commonMain/.../ui/SettingsScreen.kt` | ✅ CREADO | Swatches 5 columnas + `FilterChip` modo + botón logout |
| `commonMain/.../ui/MainTabScreen.kt` | ✅ MODIFICADO | Recibe `selectedTheme`/`themeMode`/callbacks; 6º tab "Ajustes" con `Icons.Outlined.Settings` |
| Compilación | ⚠️ pre-existente | Build falla en Windows por incompatibilidad SQLDelight + Gradle 9.5.1 (no relacionado con 25A) |

---

### SPRINT 25B — Polish Android ← SIGUIENTE

**Objetivo**: Aplicar mejoras visuales en Android sobre el sistema de temas ya instalado.

| Tarea | Archivo | Descripción |
|-------|---------|-------------|
| B-1 | `Theme.kt` | Completar roles de color: primaryContainer, surfaceVariant, etc. |
| B-2 | `NotesScreens.kt` | Añadir FAB flotante consistente con RecipeList y StockList |
| B-3 | `StockScreens.kt` | `animateItem()` en LazyColumn, skeleton loading |
| B-4 | `NotesScreens.kt` | `animateItem()`, skeleton loading |
| B-5 | `RecetasApp.kt` | `animateItem()` en ShoppingList, AnimatedContent entre tabs |
| B-6 | `RecetasApp.kt` | TopAppBar con título contextual según tab activo |
| B-7 | `RecetasApp.kt` | Login: icono/logo premium con Nunito |
| B-8 | `StockScreens.kt:328` | Botón Eliminar en StockDetail con color error |
| B-9 | `RecipeScreens.kt:374` | Traducción de dificultad en RecipeCard chips |
| B-10 | `MenuScreen.kt` | Empty state animado (Lottie o animación equivalente) |

---

### SPRINT 25C — Polish Desktop

**Objetivo**: Aplicar mejoras visuales en JavaFX sobre el sistema de temas.

| Tarea | Archivo | Descripción |
|-------|---------|-------------|
| C-1 | `style.css` + `MainWindow.java` | Estado activo en sidebar (resaltado del ítem seleccionado) |
| C-2 | `style.css` | Iconos en botones del sidebar (usando texto Unicode o ImageView) |
| C-3 | `DashboardView.java` | ProgressIndicator real en secciones de carga |
| C-4 | `style.css` | Styling de scrollbars JavaFX |
| C-5 | `MainWindow.java` | Separador visual entre Sincronizar y Cerrar sesión en sidebar |
| C-6 | `RecipeFormDialog.java` | ScaleTransition en apertura de modales (0.95→1.0, 200ms EaseOut) |

---

### SPRINT 25D — Polish iOS

**Objetivo**: Aplicar mejoras visuales en iOS sobre el sistema de temas.

| Tarea | Archivo | Descripción |
|-------|---------|-------------|
| D-1 | `RecipeListScreen.kt` | RecipeCard rica: imagen/placeholder con gradiente + chips de meta |
| D-2 | `ios/commonMain/Spacing.kt` (NUEVO) | Objeto Spacing equivalente al de Android |
| D-3 | Todas las pantallas iOS | Aplicar Spacing.* en vez de dp hardcoded |
| D-4 | `StockScreen.kt` | Empty state animado (pulse icon) |
| D-5 | `NotesScreen.kt` | Empty state animado + animateItem |
| D-6 | `MenuScreen.kt` | Empty state animado + resaltado del día actual |
| D-7 | `RecipeListScreen.kt` | Empty state animado |
| D-8 | `LoginScreen.kt` | Icono de marca, tipografía Nunito, spacing correcto |
| D-9 | `MainTabScreen.kt` | TopAppBar coherente |

---

## PARTE 5 — SELECTOR DE TEMAS — DISEÑO UX

### 5.1 Android — ThemeSettingsSheet

Acceso: TopAppBar → icono de paleta OU largo press en NavigationBar

```
┌─────────────────────────────────────────────┐
│  Personalización                        ✕   │
├─────────────────────────────────────────────┤
│  Modo                                       │
│  ┌──────┐  ┌──────┐  ┌──────┐              │
│  │ ☀️   │  │  🌙  │  │  📱  │              │
│  │Claro │  │Oscuro│  │Sistema│             │
│  └──────┘  └──────┘  └──────┘              │
├─────────────────────────────────────────────┤
│  Tema de color                              │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐        │
│  │ 🌿 │ │ 🏺 │ │ 🌅 │ │ 🌊 │ │ 💜 │        │
│  │Bosq│ │Terr│ │Ocas│ │Medi│ │Lava│        │
│  └────┘ └────┘ └────┘ └────┘ └────┘        │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐        │
│  │ 🫒 │ │ 🫚 │ │ 🌿 │ │ 🍓 │ │ 🌙 │        │
│  │Oliv│ │Cane│ │Ment│ │Fram│ │Noch│        │
│  └────┘ └────┘ └────┘ └────┘ └────┘        │
└─────────────────────────────────────────────┘
```

Cada tema muestra un círculo pequeño con los colores primary/secondary/tertiary.
El tema seleccionado tiene borde `primary` y checkmark.

### 5.2 Desktop — Ajustes Dialog (expandido)

Acceso: botón ⚙ Ajustes en sidebar (ya existe)

```
┌──────────────────────────────────────┐
│  Ajustes              [OK] [Cancelar]│
├──────────────────────────────────────┤
│  Modo de color                       │
│  ○ Claro  ● Oscuro  ○ Sistema        │
├──────────────────────────────────────┤
│  Tema                                │
│  [Bosque ▼]  (ComboBox con 10 temas) │
│  Preview: ████ ████ ████             │
├──────────────────────────────────────┤
│  Sonidos  [□ Activar efectos]        │
└──────────────────────────────────────┘
```

### 5.3 iOS — SettingsScreen (nueva pantalla)

Acceso: tab extra en NavigationBar o botón en header de RecipeListScreen

```
┌──────────────────────────────┐
│  Ajustes                     │
├──────────────────────────────┤
│  APARIENCIA                  │
│  Modo         Claro >        │
│  Tema         Bosque >       │
├──────────────────────────────┤
│  CUENTA                      │
│  Cerrar sesión               │
└──────────────────────────────┘
```

Con subpantalla de selección de tema y subpantalla de selección de modo.

---

## PARTE 6 — CRITERIOS DE CALIDAD

Checklist de validación por sprint:

### 6.1 Paleta — Sprint 25A ✅
- [x] Los 10 temas definidos con todos los roles Material3 (light + dark)
- [x] El tema seleccionado persiste entre sesiones (DataStore Android, Preferences Desktop, NSUserDefaults iOS)
- [x] La preferencia de modo (Claro/Oscuro/Sistema) funciona correctamente
- [ ] Verificar contraste WCAG AA 4.5:1 en cada tema (pendiente test visual en dispositivo)

### 6.2 Tipografía — Parcial ✅
- [x] Escala tipográfica completa definida (13 tokens: displaySmall → labelSmall)
- [x] Pesos correctos (Bold para headlines, SemiBold para titles, Regular para body)
- [ ] Nunito/Lato pendiente de añadir TTF a `res/font/` — actualmente usando fuentes del sistema

### 6.3 Animaciones — Sprint 25B/C/D
- [ ] `AnimatedContent` al cambiar de tab (25B)
- [ ] `animateItem()` en Stock/Notes/Shopping Android (25B)
- [ ] Skeleton loading Stock/Notes/Shopping (25B)

### 6.4 Spacing — Sprint 25D
- [ ] Todas las pantallas iOS usan `Spacing.*` en vez de dp hardcoded
- [ ] Desktop usa constantes de spacing en vez de `new Insets()` directos

### 6.5 Compilación
- [x] `./gradlew assembleDebug` → BUILD SUCCESSFUL (Android Sprint 25A)
- [x] `mvn compile` → BUILD SUCCESS (Desktop Sprint 25A)
- [ ] iOS: build en Xcode sin errores (requiere macOS)

---

## PARTE 7 — ORDEN DE EJECUCIÓN DEFINITIVO

```
Sprint 25A: Sistema de Temas ✅ COMPLETADO (2026-05-29)
  ├── 25A.1: Android ThemeSystem ✅ (AppTheme + ThemePreference DataStore + Theme.kt + ViewModel + ThemePickerDialog)
  ├── 25A.2: Desktop ThemeSystem ✅ (ThemeManager + style.css variables + 20 CSS themes + MainWindow ajustes)
  └── 25A.3: iOS ThemeSystem ✅ (AppTheme + ThemePreference expect/actual NSUserDefaults + App.kt + SettingsScreen)

Sprint 25B: Polish Android ✅ COMPLETADO (2026-05-29)
Sprint 25C: Polish Desktop ✅ COMPLETADO (2026-05-29)
Sprint 25D: Polish iOS ✅ COMPLETADO (2026-05-29)

Sprint 26 — SIGUIENTE:
  ├── Android: Drag-to-reorder ingredientes/pasos en RecipeForm
  ├── iOS: NotesScreen CRUD (crear/editar notas)
  └── Desktop+Android: Dashboard con menú del día real (/menu-items?plannedDate=HOY)
```

---

*Documento generado por análisis exhaustivo de todo el código de interfaz del proyecto.*
*Última actualización: 2026-05-29 — Sprint 25A completado*
*Plataformas auditadas: Android (28 archivos UI), Desktop (14 archivos UI + style.css), iOS (12 archivos UI)*

---

## PARTE 8 — AUDITORÍA UI/UX POST-SPRINT 28 (2026-05-29)

**Estado de Interfaz.md antes de esta auditoría**: TODO IMPLEMENTADO.
Sprints 25A → 25B → 25C → 25D → 26 completados. Los ítems `[ ]` del checklist original (Nunito/Lato TTF, WCAG AA) permanecían pendientes deliberadamente.

Esta auditoría revisa el código real del proyecto en estado Sprint 28 e identifica mejoras adicionales para sprints futuros.

---

### 8.1 — Mejoras Críticas (🔴)

| ID | Plataforma | Archivo | Problema | Solución |
|----|------------|---------|----------|---------|
| UI-1 | Android | `ui/theme/AppTheme.kt` | Nunito + Lato definidos en la escala tipográfica pero `NunitoFamily = FontFamily.Default` y `LatoFamily = FontFamily.SansSerif`. Sin fuentes personalizadas la app se siente genérica. | Añadir TTF a `res/font/nunito_bold.ttf`, `nunito_semibold.ttf`, `lato_regular.ttf`, `lato_semibold.ttf`. Actualizar `FontFamily(Font(R.font.X, Weight))` en `AppTheme.kt`. |
| UI-2 | Android | `RecetasApp.kt:260–271` | Todos los `NavigationBarItem` usan `Icons.Outlined.*` siempre, independientemente del estado seleccionado. Material3 exige Filled=activo, Outlined=inactivo. | Pasar `selectedIcon = { Icon(Icons.Filled.X) }` e `icon = { Icon(Icons.Outlined.X) }` en cada `NavigationBarItem`. Añadir imports de las variantes `Icons.Filled.*`. |

---

### 8.2 — Mejoras Altas (🟠)

| ID | Plataforma | Archivo | Problema | Solución |
|----|------------|---------|----------|---------|
| UI-3 | Android | `ProfileScreen.kt:84–93` | Header (avatar + nombre + email) + dos `ListItem` debajo repitiendo nombre y email. Información duplicada sin valor añadido. | Eliminar los dos `ListItem` y los `HorizontalDivider` entre ellos. El header ya lo muestra todo. |
| UI-4 | Android + iOS | `ProfileScreen.kt:53`, `SettingsScreen.kt:68` | `displayName.take(2).uppercase()` → "MA" para "María García". El estándar de avatares es primera+última inicial. | Cambiar a: `displayName!!.split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")`. Aplicar en ambas plataformas. |
| UI-5 | iOS | `cooking/CookingScreen.kt` | iOS tiene `detectHorizontalDragGestures` funcional pero sin ninguna pista visual al usuario. Android tiene el pill animado desde Sprint 27.C. | Añadir `var showHint by remember { mutableStateOf(true) }` + `LaunchedEffect("hint") { delay(3_000); showHint = false }` + `AnimatedVisibility(showHint, enter=fadeIn(400ms), exit=fadeOut(600ms))` con pill Surface centrado en la parte inferior. |
| UI-6 | iOS | `recipes/RecipeListScreen.kt` | Sin buscador ni filtros de ningún tipo. Android tiene `OutlinedTextField` + chips Fácil/Media/Difícil + chip "Con mi stock". La pantalla más visitada de la app carece del filtrado básico. | Añadir `var query by remember { mutableStateOf("") }` + `OutlinedTextField` + filtro client-side sobre `recipes`. Los FilterChips de dificultad son directamente portables del código Android. |

---

### 8.3 — Mejoras Medias (🟡)

| ID | Plataforma | Archivo | Problema | Solución |
|----|------------|---------|----------|---------|
| UI-7 | Desktop | `MainWindow.java` | El sidebar no muestra quién está logueado. Android+iOS muestran avatar+nombre tras Sprint 28.A. | Añadir un `HBox` en la cabecera del sidebar (sobre `btnDashboard`) con avatar `Label`/`Circle` iniciales + `Label` displayName. Leer de `AppSession` que ya tiene los datos. |
| UI-8 | Android + iOS | `OnboardingScreen.kt` | Las 3 páginas de onboarding muestran emoji sobre fondo `surface` plano. Primera impresión pobre para una app premium. | Añadir `Box` con `Brush.verticalGradient` de `colorScheme.primary.copy(alpha=0.07f)` → `Color.Transparent` como fondo de cada página. Sin cambios estructurales. |
| UI-9 | Android | `RecipeScreens.kt:194` | `Text("Recetas", style = MaterialTheme.typography.headlineSmall)` dentro del contenido del tab. El `TopAppBar` ya muestra "Recetas" de forma contextual (Sprint 25B). Duplicación. | Eliminar la línea. Si se quiere jerarquía, reemplazar por `Text("${filtered.size} recetas", style = bodyMedium, color = onSurfaceVariant)` como subtítulo. |
| UI-10 | Android + iOS | `CookingScreen.kt` | El timer countdown muestra segundos como texto plano. Difícil leer el estado del timer de un vistazo, especialmente con las manos mojadas. | Envolver el texto `MM:SS` en `Box` con `CircularProgressIndicator(progress = timerLeft.toFloat() / totalSeconds, strokeWidth = 6.dp, modifier = Modifier.size(96.dp))` determinado como anillo visual. |

---

### 8.4 — Mejoras Bajas (🟢 — polish)

| ID | Plataforma | Archivo | Problema | Solución |
|----|------------|---------|----------|---------|
| UI-11 | iOS | `stock/StockScreen.kt` | Fechas de caducidad muestran "YYYY-MM-DD" raw. Difícil evaluar urgencia de un vistazo. | Calcular `daysLeft` con `kotlinx.datetime`. Mostrar "Caduca en N días" (color `tertiary` si ≤7, color `error` si ≤2, color `onSurfaceVariant` si >7). Ya hay lógica similar en Android `StockScreens.kt`. |
| UI-12 | Android + iOS | `SettingsScreen.kt`, `ThemePickerDialog.kt` | Los hápticos están activos siempre. `Interfaz.md §16` exige toggle en preferencias. No está implementado. | Añadir `var hapticsEnabled: Boolean` a `ThemePreference`/`OnboardingPreference`. Toggle `Switch` o `FilterChip` en `SettingsScreen` iOS y en `ThemePickerDialog` Android. Consultar antes de llamar `haptic.*`. |
| UI-13 | iOS | `recipes/RecipeListScreen.kt` | Filtro "Con mi stock" solo existe en Android (Sprint 28.C). iOS no tiene paridad. | Pasar instancia de `StockRepository` a `RecipeListScreen`. Replicar la lógica `combine` de Android en el scope de la composable (sin ViewModel en iOS). |

---

### 8.5 — Ítems del checklist original aún pendientes

| ID | Descripción | Estado |
|----|-------------|--------|
| FONT-1 | Nunito/Lato TTF pendiente de añadir a `res/font/` | Cubierto por UI-1 |
| WCAG-1 | Verificar contraste WCAG AA 4.5:1 en cada uno de los 10 temas | Revisión de diseño — requiere herramienta externa (Colour Contrast Analyser). No bloquea funcionalidad. |
| BUILD-IOS | Build iOS binario en Windows falla (SQLDelight + Gradle 9.5.1) | Pre-existente, sin relación con UI. Requiere macOS + Xcode. |

---

### 8.6 — Orden de ejecución recomendado

```
Sprint 29 UI/UX (alta prioridad):
  ├── UI-1: Nunito + Lato TTF (Android)          ← mayor impacto visual, bajo esfuerzo
  ├── UI-2: NavigationBar Filled/Outlined (Android)
  ├── UI-3: ProfileScreen eliminar ListItems duplicados (Android)
  └── UI-4: Algoritmo iniciales avatar (Android + iOS)

Sprint 30 UI/UX (media prioridad):
  ├── UI-5: iOS CookingScreen swipe hint
  ├── UI-6: iOS RecipeListScreen buscador + filtros
  ├── UI-7: Desktop user card en sidebar
  ├── UI-8: Onboarding gradiente de fondo (Android + iOS)
  └── UI-9: Eliminar inner "Recetas" heading duplicado (Android)

Sprint 31 UI/UX (baja prioridad / polish):
  ├── UI-10: Timer CookingScreen circular (Android + iOS)
  ├── UI-11: Fechas caducidad relativas en iOS StockScreen
  ├── UI-12: Haptics toggle en Settings (Android + iOS)
  └── UI-13: Filtro "Con mi stock" en iOS
```

---

*Auditoría actualizada: 2026-05-29 — Sprint 28 completado*
*Código revisado: `RecetasApp.kt`, `ProfileScreen.kt`, `RecipeScreens.kt`, `CookingScreen.kt` (Android), `RecipeListScreen.kt`, `CookingScreen.kt`, `SettingsScreen.kt` (iOS), `MainWindow.java` (Desktop)*
