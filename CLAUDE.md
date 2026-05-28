# CLAUDE.md - Reglas del Proyecto "Recetas Familia"

Proyecto premium multiplataforma para gestión familiar de recetas, ingredientes, menús y memoria culinaria compartida.

Estas reglas deben respetar siempre:
- las instrucciones explícitas del usuario,
- las políticas de seguridad,
- los permisos reales del entorno,
- y las capacidades efectivas de las herramientas disponibles.

---

# CONTEXTO DEL PROYECTO

"Recetas Familia" es una plataforma cliente-servidor multiplataforma diseñada para ayudar a las familias a:

- guardar recetas,
- compartir tradición culinaria,
- organizar ingredientes,
- planificar menús,
- generar listas de compra,
- cocinar colaborativamente,
- y conservar memoria emocional asociada a la cocina familiar.

El proyecto es un ecosistema distribuido compuesto por:

- Backend Spring Boot
- Aplicación Android nativa (Kotlin + Compose)
- Aplicación Desktop JavaFX
- Aplicación iOS (Kotlin Multiplatform + Compose Multiplatform)
- Base de datos MySQL
- Sincronización multiplataforma

La experiencia debe sentirse:
- cálida,
- moderna,
- premium,
- fluida,
- emocional,
- y extremadamente fácil de usar.

---

# FILOSOFÍA DEL PRODUCTO

La aplicación NO debe sentirse como:
- un ERP,
- una app corporativa,
- una hoja Excel,
- ni una simple colección de recetas.

Debe sentirse como:
- un espacio familiar,
- acogedor,
- organizado,
- elegante,
- visualmente satisfactorio,
- y emocionalmente significativo.

El foco principal es:
- experiencia de usuario,
- simplicidad,
- sincronización familiar,
- y placer visual.

---

# STACK TECNOLÓGICO

## Backend

- Spring Boot
- Java 21+
- Spring Security
- Spring Data JPA
- MySQL
- JWT
- Flyway/Liquibase
- OpenAPI/Swagger

## Android

- Android nativo
- Material You 3
- Retrofit
- Room
- WorkManager
- ViewModel
- Repository Pattern

## Desktop

- JavaFX
- Maven
- HTTP API Client
- MVVM ligero
- Caché local

## iOS

- Kotlin Multiplatform (KMP)
- Compose Multiplatform (UI compartida con Android donde sea posible)
- Ktor (cliente HTTP cross-platform, reemplaza Retrofit en módulo compartido)
- SQLDelight (base de datos local cross-platform, reemplaza Room)
- iOS Background Tasks (reemplaza WorkManager)
- Keychain (reemplaza EncryptedSharedPreferences)
- MVVM compartido con Android vía módulo `shared/`

## Módulo Compartido KMP (`shared/`)

- Lógica de negocio (repositories, use cases, modelos de dominio)
- DTOs de red compartidos
- Clientes HTTP Ktor
- Esquemas SQLDelight
- Estrategia: extraer incrementalmente desde Android sin romper lo existente

## Base de Datos

- MySQL como base principal.
- Nunca depender de SQLite como fuente maestra.
- Las aplicaciones cliente deben comunicarse mediante API HTTP.

---

# ESTRUCTURA DEL PROYECTO

- `backend/` → Backend Spring Boot
- `android/` → Aplicación Android (Kotlin + Compose)
- `desktop/` → Aplicación JavaFX
- `ios/` → Aplicación iOS (KMP + Compose Multiplatform)
- `shared/` → Módulo KMP compartido Android + iOS (a crear)
- `database/` → Scripts y migraciones
- `docs/` → Documentación

---

# MÓDULOS PRINCIPALES

- Usuarios
- Familias
- Recetas
- Ingredientes
- Stock familiar
- Listas de compra
- Menús semanales
- Favoritos
- Valoraciones
- Historial culinario
- Fotos
- Temporizadores
- Notas familiares

---

# EXPERIENCIA VISUAL

## Estilo Visual

Inspiración:
- Notion
- Material You
- Apple Design
- Pinterest Food UX

La interfaz debe sentirse:
- limpia,
- moderna,
- espaciosa,
- cálida,
- premium,
- emocional.

## Paleta

- Tonos tierra
- Verdes suaves
- Amarillos cálidos
- Naranjas apetecibles

## UX

- Microanimaciones suaves
- Dark Mode premium
- Light Mode acogedor
- Navegación clara
- Tipografía muy legible en cocina
- Jerarquía visual fuerte
- Interfaces táctiles cómodas

---

# PRINCIPIOS GENERALES

- Código mínimo viable.
- Cambios quirúrgicos.
- Evitar overengineering.
- Respetar el estilo existente.
- No refactorizar código estable innecesariamente.
- Priorizar claridad y mantenibilidad.
- Priorizar UX real sobre arquitectura innecesariamente compleja.
- Aplicar YAGNI estrictamente.

---

# 1. PIENSA ANTES DE PROGRAMAR

- Declarar suposiciones relevantes.
- Detectar riesgos de regresión.
- Leer dependencias antes de modificar archivos.
- Pensar paso a paso antes de proponer código.
- Priorizar siempre la solución más simple.
- Explicar trade-offs importantes.
- No asumir nombres de métodos, endpoints o clases sin verificarlos.

---

# 2. CAMBIOS QUIRÚRGICOS

- Modificar solo lo estrictamente necesario.
- No mover archivos sin necesidad.
- No reorganizar arquitectura por iniciativa propia.
- Limpiar únicamente el código afectado.
- No tocar artefactos generados salvo petición explícita.
- Releer siempre el bloque antes de entregarlo.

---

# 3. SINCRONIZACIÓN MULTIPLATAFORMA

Regla crítica del proyecto.

Antes de modificar:
- DTOs,
- entidades,
- endpoints,
- serialización,
- autenticación,
- nombres JSON,
- enums,
- validaciones,
- modelos sincronizados,

validar impacto simultáneo en:
- Backend,
- Android,
- Desktop,
- iOS (cuando el módulo compartido esté activo).

Nunca asumir que un cambio backend es transparente para Android o Desktop.

Validar:
- serialización,
- nullabilidad,
- códigos HTTP,
- formatos de fecha,
- compatibilidad JSON,
- compatibilidad de enums,
- autenticación,
- paginación.

---

# 4. SINCRONIZACIÓN OFFLINE

Android debe poder funcionar temporalmente offline.

Toda entidad sincronizable debe incluir:
- `id`
- `createdAt`
- `updatedAt`
- `syncVersion`
- `deleted`

## Reglas

- Usar soft delete.
- Nunca eliminar físicamente registros sincronizados.
- Resolver conflictos con estrategia:
  - Last Write Wins
  - salvo reglas específicas.
- Sincronización incremental obligatoria.
- Evitar duplicados.
- Mantener integridad entre clientes.

---

# 5. AUTENTICACIÓN Y FAMILIAS

## Reglas

- Autenticación mediante JWT.
- Refresh Tokens obligatorios.
- Nunca hardcodear secretos.
- Validar ownership en todos los endpoints.
- Toda familia funciona como tenant lógico.

## Seguridad Familiar

Ningún usuario debe poder:
- acceder a recetas ajenas,
- ver fotos ajenas,
- consultar listas de otra familia,
- acceder a notas privadas.

---

# 6. CONTRATOS API

## Reglas

- Nunca exponer entidades JPA directamente.
- Usar DTOs explícitos.
- JSON camelCase consistente.
- API versionada:
  - `/api/v1/`
- Respuestas coherentes entre Android y Desktop.

## API Design

- Paginación obligatoria en listas grandes.
- Filtros simples y consistentes.
- Fechas en UTC ISO-8601.
- Manejo de errores consistente.

---

# 7. GESTIÓN DE IMÁGENES

## Reglas

- Nunca almacenar imágenes en MySQL.
- Backend almacena:
  - URLs,
  - metadata,
  - referencias.

## Clientes

### Android

- Generar thumbnails.
- Lazy loading.
- Compresión automática.
- Cache local.

### Desktop

- Lazy loading.
- Cache visual.
- Evitar cargar imágenes pesadas innecesariamente.

## Seguridad

- Validar formatos permitidos.
- Limitar tamaño máximo.
- Sanitizar nombres de archivo.

---

# 8. RENDIMIENTO

## Android

- Nunca bloquear UI Thread.
- Lazy loading obligatorio.
- Paginación obligatoria.
- Cache local para recetas y menús.
- Optimizar consumo de batería.

## Desktop

- Nunca bloquear JavaFX Thread.
- Usar Services/Tasks.
- Virtualización en tablas y grids grandes.
- Lazy loading visual.

## Backend

- Evitar N+1.
- Validar índices SQL.
- Evitar overfetching.
- Optimizar queries frecuentes.

---

# 9. BASE DE DATOS

## Reglas

- Usar Flyway o Liquibase.
- Nunca depender de `ddl-auto=update` en producción.
- Toda migración debe ser versionada.

## Seguridad

- Nunca hardcodear credenciales.
- Variables de entorno obligatorias.
- Realizar backup antes de cambios destructivos.

---

# 10. FUNCIONALIDADES IA

La IA debe ser:
- útil,
- práctica,
- opcional,
- y no intrusiva.

## Posibles funciones

- sugerencias de recetas,
- sugerencias según stock,
- OCR de recetas antiguas,
- recomendaciones de menú,
- planificación inteligente,
- sustitución de ingredientes.

## Reglas

- Nunca enviar datos sensibles sin consentimiento.
- Mantener fallback manual siempre disponible.
- Explicar claramente cuando una sugerencia proviene de IA.

---

# 11. ANDROID

## Arquitectura

- MVVM
- Repository Pattern
- Retrofit
- Room
- WorkManager
- Material You 3

## UX

- Bottom Navigation
- Navigation Drawer
- Widgets
- Accesibilidad táctil
- Modo cocina
- Modo manos libres

## Reglas

- Mantener compatibilidad moderna Android.
- Evitar Activities gigantes.
- Centralizar networking.
- Respetar lifecycle Android.

---

# 12. DESKTOP JAVAFX

## Arquitectura

- MVVM ligero
- HTTP API Client
- Caché local

## UX

- Sidebar moderna
- Dashboard visual
- Filtros rápidos
- Modo Cocina
- Navegación fluida

## Reglas

- Nunca bloquear JavaFX UI Thread.
- Virtualización obligatoria.
- Lazy loading visual.
- Mantener experiencia premium.

---

# 13. iOS (KMP + COMPOSE MULTIPLATFORM)

## Arquitectura

- Kotlin Multiplatform + Compose Multiplatform
- MVVM compartido con Android (módulo `shared/`)
- SQLDelight para base de datos local
- Ktor para red
- iOS Background Tasks para sincronización periódica
- Keychain para almacenamiento seguro de tokens

## UX

- Navegación nativa iOS (NavigationStack / TabView via Compose Multiplatform)
- Soporte Dynamic Island / widgets iOS (WidgetKit, en Swift si necesario)
- Gestos nativos iOS
- Modo cocina adaptado a iOS
- Offline-first (misma estrategia que Android)

## Reglas

- No duplicar lógica de negocio: extraer al módulo `shared/`.
- Mantener Android y Desktop estables durante la construcción de iOS.
- Migración incremental: un repositorio cada sprint, no big-bang.
- Usar `expect/actual` para APIs platform-specific (Keychain, BackgroundTasks).
- iOS no intercepta botones de volumen — el modo manos libres se adapta con gestos.
- Android Widgets ≠ iOS Widgets: WidgetKit requiere código Swift separado.

## Limitaciones conocidas (documentadas, no bloqueantes)

- Botones de volumen en CookingScreen: solo funciona en Android.
- Android Widgets (RecipeWidget, StockWidget): sin equivalente directo en KMP.
- Desktop JavaFX no migra a KMP — permanece independiente.

---

# 14. BACKEND

## Arquitectura

- Controllers ligeros
- Services con lógica de negocio
- DTOs explícitos
- Seguridad centralizada

## Seguridad

- Validar todas las entradas.
- Aplicar OWASP Top 10.
- Validar permisos en endpoints.
- Nunca confiar en datos cliente.

---

# 15. SEGURIDAD

Aplicar OWASP siempre que exista:
- autenticación,
- subida de imágenes,
- networking,
- datos personales,
- notas familiares,
- almacenamiento.

## Reglas Obligatorias

- No hardcodear secretos.
- No exponer tokens.
- Sanitizar inputs.
- Validar ownership.
- Aplicar mínimo privilegio.

---

# 15. TESTS Y VALIDACIÓN

Antes de cerrar una tarea:

- validar compilación,
- validar imports,
- ejecutar tests relevantes,
- validar sincronización,
- validar contratos API,
- validar compatibilidad Android/Desktop.

## Backend

```bash
./gradlew test
./gradlew build
```

---

# 16. ANIMACIONES Y FEEDBACK UX

## Regla Principal

La transición entre estados NUNCA debe ser abrupta. Toda aparición, desaparición o cambio de estado visible debe estar animado de forma sutil y natural.

## Compose (Android e iOS)

- Usar `AnimatedVisibility` en lugar de `if (visible) { ... }` para mostrar/ocultar paneles y secciones.
- Usar `animateContentSize()` en contenedores que cambian de tamaño al expandirse.
- Usar `Crossfade` al cambiar entre estado loading y contenido real.
- Usar `animateItemPlacement()` en `LazyColumn` al insertar o eliminar items.
- Usar `animateColorAsState` en cambios de color de badges, chips y estados.
- Usar `AnimatedContent` para cambios de número (timers, contadores).
- Física preferida: `spring()` para interacciones táctiles, `tween()` para transiciones de pantalla.
- Duraciones: entradas 200-300ms, salidas 150-200ms, transiciones de pantalla 250-350ms.

## JavaFX Desktop

- Usar `FadeTransition` al cambiar vistas en sidebar (250ms, Linear).
- Usar `ScaleTransition` al abrir modales (200ms desde 0.95 a 1.0, EaseOut).
- Usar `ScaleTransition` en hover de cards (100ms, 1.0 → 1.02).
- Usar `SequentialTransition` al eliminar items (FadeOut 150ms → colapso 150ms).
- Toda animación JavaFX corre en el JavaFX Application Thread. Nunca en hilos de fondo.
- Tooltip obligatorio en todos los botones sin label visible (delay 400ms). Formato: `"Acción (Ctrl+X)"`.

## Hápticos

- Obligatorio en acciones destructivas (eliminar, borrar).
- Obligatorio al cambiar paso en CookingScreen.
- Obligatorio al guardar con éxito o error (feedback distinto).
- Implementar vía `LocalHapticFeedback.current` en Compose Android.
- En iOS: usar `UIImpactFeedbackGenerator`, `UISelectionFeedbackGenerator`, `UINotificationFeedbackGenerator` vía `expect/actual`.
- Siempre desactivables en preferencias de usuario (activados por defecto).

## Sonidos

- Opcionales y SIEMPRE desactivables en preferencias (desactivados por defecto).
- Nunca autoplay sin acción del usuario.
- En Android: `SoundPool` para efectos cortos.
- En Desktop: `AudioClip` JavaFX para efectos.
- En iOS: seguir política Apple — no usar sonidos del sistema sin permiso explícito.

## Tooltips y Accesibilidad

- `TooltipBox + PlainTooltip` en Android en todos los botones de TopAppBar sin label visible.
- `contentDescription` completo y descriptivo en TODOS los elementos interactivos.
- `semantics { heading() }` en títulos de sección para TalkBack (Android).
- `.accessibilityLabel()` y `.accessibilityHint()` en iOS para VoiceOver.
- `.help()` modifier en botones iOS para tooltip VoiceOver y hover iPadOS.
- Focus order explícito en todos los formularios.

## Skeleton Loading

- Usar skeleton (shimmer animado) en lugar de spinners en listados con carga de red.
- Mostrar mínimo 3-5 items skeleton del tamaño real del contenido esperado.
- Desaparece con `Crossfade` o `AnimatedVisibility` al llegar los datos reales.

## Reglas de Calidad

- Ninguna animación bloquea el hilo UI.
- Evitar más de 2 animaciones simultáneas en la misma región visual.
- Todo efecto háptico o sonoro debe tener un toggle en preferencias.
- Si el sistema tiene animaciones reducidas activadas, simplificar o eliminar las propias.