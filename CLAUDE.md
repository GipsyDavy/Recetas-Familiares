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
- Aplicación Android nativa
- Aplicación Desktop JavaFX
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

## Base de Datos

- MySQL como base principal.
- Nunca depender de SQLite como fuente maestra.
- Las aplicaciones cliente deben comunicarse mediante API HTTP.

---

# ESTRUCTURA DEL PROYECTO

- `backend/` → Backend Spring Boot
- `android/` → Aplicación Android
- `desktop/` → Aplicación JavaFX
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
- Desktop.

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

# 13. BACKEND

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

# 14. SEGURIDAD

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