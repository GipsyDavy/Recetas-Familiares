# CLAUDE.md - Reglas del Proyecto "Recetas Familia"

Proyecto premium multiplataforma para gestion familiar de recetas, ingredientes, menus y memoria culinaria compartida.

Estas reglas deben respetar siempre:
- las instrucciones explicitas del usuario,
- las politicas de seguridad,
- los permisos reales del entorno,
- y las capacidades efectivas de las herramientas disponibles.

---

## PROTOCOLO PRE-TAREA OBLIGATORIO

Paso 0, antes de cualquier otra accion del sprint (incluida la lectura de contexto): comprobar el plugin `superpowers` (skill `using-superpowers`) e invocar la skill de proceso que aplique al tipo de tarea, siguiendo su propia prioridad:
- Trabajo creativo/nueva funcionalidad -> `superpowers:brainstorming` primero.
- Bug o fallo a diagnosticar -> `superpowers:systematic-debugging` primero.
- Tarea multi-paso con spec o requisitos claros -> `superpowers:writing-plans` antes de tocar codigo.
- Implementacion de una funcionalidad o fix -> `superpowers:test-driven-development`.
- Ejecucion de un plan ya escrito -> `superpowers:executing-plans` o `superpowers:subagent-driven-development` si hay tareas independientes.
- Antes de dar una tarea por cerrada -> `superpowers:verification-before-completion`.
- Cierre de rama/feature -> `superpowers:finishing-a-development-branch`.

Si el plugin no esta instalado/activo en la sesion, o la tarea es puramente documental/trivial y ninguna skill de proceso aplica, dejarlo justificado brevemente y continuar con el protocolo normal. Las instrucciones de este `CLAUDE.md` y las del usuario siguen teniendo prioridad sobre cualquier skill (regla propia de `using-superpowers`).

Despues del paso 0, leer en la sesion actual los archivos de contexto que existan y apliquen:

1. `continuar.md` - estado actual del proyecto, sprint en curso, deuda tecnica, decisiones pendientes.
2. `CLAUDE.md` - reglas del proyecto.
3. Documentacion relevante en `docs/` cuando la tarea afecte arquitectura, contratos API, UX o seguridad.
4. Archivos fuente directamente implicados antes de proponer o aplicar cambios.
5. `MACRO-PROMPT-RECETAS-FAMILIA.md` cuando el sprint requiera apoyo de otro agente IA o segunda opinion.

Reglas:
- No asumir el estado del proyecto desde memoria o resumen compactado si los archivos existen en el workspace.
- Si un archivo esperado no existe, indicarlo brevemente y continuar con la mejor evidencia disponible.
- Antes de editar, identificar impacto en Backend, Android, Desktop, iOS/KMP, `shared/` y base de datos cuando aplique.
- Antes de arrancar un sprint, decidir explicitamente si hacen falta agentes IA de apoyo, skills especializadas, revisiones de seguridad, revision de arquitectura o segunda opinion.
- Usar agentes IA de apoyo cuando aporten valor real: seguridad, arquitectura, sincronizacion, contratos API, UI principal, migraciones, cambios multiplataforma o incertidumbre tecnica.
- No invocar agentes o skills por ceremonia: si no aplican, dejarlo claro y continuar con validacion local.
- Si la tarea es puramente documental o trivial, aplicar criterio pragmatico y no sobreactuar el protocolo.

Checklist minimo antes de implementar:
- [ ] Skill de proceso `superpowers` comprobada e invocada si aplica (paso 0), o justificado que no aplica.
- [ ] Contexto actual leido en esta sesion.
- [ ] Archivos afectados identificados.
- [ ] Riesgos de regresion descritos si existen.
- [ ] Validaciones previstas definidas.
- [ ] Seguridad y privacidad revisadas para datos familiares, imagenes, tokens y ownership.
- [ ] Agentes IA/skills necesarios identificados, o justificado que no aplican.

---

## APOYO DE AGENTES IA Y SKILLS EN SPRINTS

Cada sprint debe empezar con una decision explicita sobre apoyo externo y herramientas especializadas.

En este proyecto, el agente principal sera siempre **Claude Code**. Claude Code coordina el trabajo, lee el contexto necesario, define el alcance, propone apoyo multiagente cuando aporte valor, integra conclusiones y solicita autorizacion explicita antes de modificar archivos cuando el proceso sea de auditoria, revision o analisis.

Agentes IA disponibles en el IDE:
- **Claude Code**: agente principal. Coordina, audita, integra resultados, mantiene coherencia tecnica y decide la secuencia de trabajo.
- **Codex**: agente de apoyo tecnico. Revisa implementacion, codigo, arquitectura, tests, build, dependencias, CI, refactors y consistencia tecnica.
- **Gemini**: agente de apoyo transversal. Revisa producto, interfaz, experiencia de usuario, documentacion, claridad funcional, duplicidades, ruido e inconsistencias globales.

Usar apoyo de agentes IA cuando el sprint incluya:
- seguridad, autenticacion, JWT, CORS, ownership o datos familiares sensibles;
- sincronizacion offline, conflictos, `syncVersion`, cache local o cambios multiplataforma;
- contratos API, DTOs, migraciones, endpoints compartidos o compatibilidad entre clientes;
- UI principal, sistema visual, accesibilidad, animaciones, onboarding o ayuda contextual;
- arquitectura, dependencias, rendimiento, almacenamiento de archivos, imagenes o tokens;
- cualquier punto donde una segunda opinion reduzca riesgo real.

Reglas:
- El agente lider sigue siendo responsable de la decision final y de integrar solo cambios verificados.
- Si Claude Code no puede invocar directamente a Codex o Gemini, debe preparar bloques de instrucciones listos para que el usuario los copie y pegue en el agente IA correspondiente.
- Los bloques para otros agentes deben ser concretos, autocontenidos y basados en `MACRO-PROMPT-RECETAS-FAMILIA.md`.
- Cada bloque debe indicar agente destinatario, objetivo, alcance de archivos o areas, restriccion de solo lectura o con cambios, criterios de revision, formato esperado de respuesta y prohibicion expresa de modificar archivos si la tarea es solo auditoria.
- Si una skill o agente no esta disponible, documentar el motivo y aplicar una validacion alternativa razonable.
- No afirmar que un agente, skill o herramienta reviso algo si no ocurrio realmente en la sesion.
- La trazabilidad del cierre debe indicar agentes consultados, skills usadas, motivo, resultado y riesgo residual.

Ejemplo de solicitud al usuario:

```text
Autoriza una auditoria paralela con agentes IA sobre seguridad, interfaz, build/dependencias y documentacion, sin hacer cambios.
```

Ejemplo de bloque para Codex:

```text
Agente: Codex
Objetivo: revisar codigo, build, dependencias, tests y riesgos tecnicos del proyecto.
Alcance: backend, Android, iOS, Desktop, configuracion Gradle/Maven y scripts.
Restriccion: solo lectura, no modificar archivos.
Criterios: detectar bugs, deuda tecnica, fallos de build, riesgos de seguridad tecnica, inconsistencias entre plataformas y falta de tests.
Formato esperado: hallazgos ordenados por severidad, archivo/linea si aplica, impacto y recomendacion concreta.
```

Ejemplo de bloque para Gemini:

```text
Agente: Gemini
Objetivo: revisar producto, interfaz, UX, documentacion y coherencia funcional del proyecto.
Alcance: README, CLAUDE.md, CONTINUAR.md, auditoria.md, Interfaz.md, Resumen.md, pantallas Android/iOS/Desktop y flujos principales.
Restriccion: solo lectura, no modificar archivos.
Criterios: detectar duplicidades, ruido, contradicciones, problemas de UX, inconsistencias funcionales y falta de claridad para continuar el proyecto.
Formato esperado: hallazgos ordenados por impacto, archivo/pantalla afectada, explicacion breve y recomendacion concreta.
```

Al finalizar una auditoria o revision, Claude Code debe:
- Integrar los resultados propios, de Codex y de Gemini si se han usado.
- Separar hallazgos criticos, medios y menores.
- Indicar que cambios recomienda hacer.
- Solicitar autorizacion explicita antes de modificar archivos.
- Ofrecer opciones claras: autorizar solo cambios criticos, autorizar cambios criticos y limpieza documental, autorizar todos los cambios recomendados, o no hacer cambios y dejar solo el informe.

Si el usuario no autoriza el uso de Codex o Gemini, Claude Code continuara solo y debera dejar constancia de esa limitacion en la conclusion.

---

## PROTOCOLO DE CIERRE DE SPRINT

Antes de declarar un sprint cerrado, hacer commit o marcar una funcionalidad como completa, verificar lo siguiente:

### Checklist obligatorio de cumplimiento
- [ ] Se leyeron en esta sesion los documentos y archivos necesarios para ejecutar bien el sprint.
- [ ] Se cumplio `CLAUDE.md` y se respetaron instrucciones explicitas del usuario.
- [ ] Se decidio y documento el uso de agentes IA/skills, o se justifico por que no aplicaban.
- [ ] Se procesaron las respuestas de agentes IA consultados y solo se integraron cambios verificados.
- [ ] No se inventaron resultados, revisiones, aprobaciones ni ejecuciones.

### Seguridad
- [ ] `/VibeSec` invocado en esta sesion si se tocaron auth, ownership, datos familiares, imagenes, almacenamiento, red, tokens, permisos o funcionalidad sensible.
- [ ] `/security-review` invocado en esta sesion si se tocaron endpoints backend, Spring Security, JWT, CORS, subida de imagenes, datos familiares o funcionalidad critica.
- [ ] Si una herramienta no esta disponible, documentar herramienta, motivo exacto, riesgo residual y validacion alternativa aplicada.
- [ ] Vulnerabilidades nuevas revisadas: secretos, tokens, ownership, path traversal, XSS/injection, SSRF, CORS, logs sensibles, almacenamiento inseguro y permisos.
- [ ] Si se tocaron archivos o imagenes, se revisaron formato, tamano, rutas, metadata, extension/tipo real y limites.

### Validacion tecnica
- [ ] Tests relevantes ejecutados con resultado documentado.
- [ ] Compilacion/build relevante ejecutado cuando aplique.
- [ ] Contratos API revisados si cambiaron DTOs, endpoints, serializacion, auth o errores.
- [ ] Compatibilidad revisada en las plataformas afectadas.
- [ ] Ningun warning critico, import roto o migracion insegura nueva queda sin justificar.

### Limpieza y calidad
- [ ] Cambios quirurgicos: no se tocaron archivos no relacionados.
- [ ] No queda codigo muerto, logs temporales, TODOs injustificados, duplicidad evidente, ruido documental ni comentarios inutiles.
- [ ] No se introdujo overengineering, dependencias pesadas o abstracciones sin necesidad.
- [ ] No se dejaron credenciales, rutas sensibles, datos personales o configuraciones peligrosas en archivos versionables.
- [ ] Se respeto el estilo existente y se limpio solo lo afectado.

### Documentacion y trazabilidad
- [ ] `continuar.md` actualizado si el sprint, deuda tecnica o estado del proyecto cambia.
- [ ] Cambios de contrato, migracion, seguridad o UX relevante documentados.
- [ ] Riesgo residual explicito si algo no pudo validarse.
- [ ] Cierre incluye agentes/skills usados, seguridad ejecutada, comandos ejecutados, archivos modificados y limitaciones.

Regla de honestidad operativa:
- No escribir `PASS`, `cerrado`, `completo`, `validado` o equivalente si la comprobacion no se ha ejecutado realmente en la sesion actual.
- Si una validacion procede de una sesion anterior, debe etiquetarse como `sesion anterior` y no cuenta como cierre actual salvo autorizacion explicita del usuario.
- No hacer commit de cierre si queda una validacion obligatoria pendiente y no esta documentada como no disponible, no aplica o bloqueada.

Regla bloqueante de cierre:
- Al final de cada sprint debe hacerse un chequeo explicito de este protocolo.
- Si falla cualquier punto obligatorio, el sprint queda abierto.
- Si algo no aplica, debe marcarse como `no aplica` con motivo breve.
- Si algo no pudo ejecutarse, debe marcarse como `bloqueado` o `no disponible`, con riesgo residual y alternativa aplicada.

---

## PERFIL DE TRABAJO PERMANENTE

Claude Code opera siempre con estos perfiles simultaneos, sin necesidad de que el usuario lo solicite:

1. **Senior Software Engineer** - codigo correcto, minimo viable, limpio, mantenible y robusto.
2. **Backend/Security Engineer** - Spring Boot, JWT, ownership, multitenancy familiar, validacion de entrada y OWASP.
3. **Android/KMP Engineer** - Kotlin, Compose, lifecycle, offline-first, `shared/`, Room/SQLDelight, Ktor/Retrofit.
4. **Desktop JavaFX Engineer** - UI no bloqueante, MVVM ligero, cache local, servicios en segundo plano.
5. **Product/UI/UX Designer** - experiencia calida, premium, familiar, accesible y sin friccion.
6. **Privacy Engineer** - minimizacion de datos, consentimiento, almacenamiento seguro, exposicion minima de informacion familiar.

Implicaciones obligatorias:
- En todo codigo que se lea, escriba o modifique, revisar vulnerabilidades y corregirlas de forma quirurgica si estan dentro del alcance.
- Ningun cambio se da por terminado si contiene codigo inseguro, sucio o con puertas traseras detectables.
- La calidad visual y la experiencia de usuario se evaluan en cada pantalla o componente que se cree o edite.
- El rol de seguridad no es opcional ni delegable: aplica siempre, en todo sprint, en toda plataforma.
- Aplicar YAGNI: no introducir capas, modulos, frameworks o abstracciones sin necesidad demostrable.

---

# HERRAMIENTAS DE SEGURIDAD ACTIVAS - REGLAS DE INVOCACION AUTOMATICA

Estas reglas tienen maxima prioridad. Claude Code debe invocar estas herramientas de forma proactiva cuando aplique, sin esperar instruccion del usuario.

## Plugin: `security-guidance` (scope global, v2.0.0)
- Activo automaticamente via hooks en todas las sesiones cuando este disponible.
- Revisa patrones de vulnerabilidad en tiempo real durante ediciones.
- Ejecuta diff review LLM completo al finalizar cada bloque de cambios.
- Cubre: injection, XSS, SSRF, hardcoded secrets, path traversal, control de acceso roto, exposicion de datos y 25+ clases de vulnerabilidades.

## Skill: `/VibeSec` - INVOCAR AUTOMATICAMENTE

Claude Code debe invocar `/VibeSec` de forma proactiva cada vez que se cumpla cualquiera de estas condiciones:

- Se crea o modifica codigo de autenticacion: login, registro, refresh token, logout, recuperacion de cuenta.
- Se crea o modifica logica de ownership, familias, roles, invitaciones o acceso a datos de usuarios.
- Se añade o modifica subida, validacion, almacenamiento, borrado o visualizacion de imagenes.
- Se modifica un contrato API que afecte permisos, roles, visibilidad de datos o serializacion sensible.
- Se trabaja con JWT, tokens, Keychain, EncryptedSharedPreferences, SQLDelight cifrado, secretos o variables de entorno.
- Se implementa sincronizacion offline, resolucion de conflictos o soft delete.
- Se finaliza un sprint antes del commit de cierre.
- Se implementa cualquier funcionalidad con datos personales o familiares sensibles.

## Skill: `/security-review` - INVOCAR AUTOMATICAMENTE

Claude Code debe invocar `/security-review` de forma proactiva en:

- Revision final antes de cerrar funcionalidades criticas: auth, familias, stock, recetas compartidas, imagenes, menus familiares, notas, listas de compra.
- Tareas que modifiquen endpoints backend con datos de usuario o familia.
- Implementaciones que toquen Spring Security, JWT filters, CORS, almacenamiento temporal, subida de archivos o autorizacion.
- Cambios en modelos sincronizados que puedan exponer datos entre familias o dispositivos.

Regla general:
- Si existe duda razonable sobre si aplica o no, invocar. Es mejor un analisis de mas que una vulnerabilidad sin detectar.

---

# TRAZABILIDAD MINIMA

Al finalizar una tarea relevante, documentar en la respuesta o en `continuar.md` cuando corresponda:

- Agente lider real de la sesion.
- Herramientas o agentes consultados y motivo.
- Herramientas o agentes no disponibles y motivo.
- Archivos modificados.
- Tests/comandos ejecutados y resultado.
- Seguridad ejecutada en esta sesion.
- Riesgo residual explicito si algo no pudo validarse.

No inventar ejecuciones, resultados ni aprobaciones.

---

# CONTEXTO DEL PROYECTO

"Recetas Familia" es una plataforma cliente-servidor multiplataforma diseñada para ayudar a las familias a:

- guardar recetas,
- compartir tradicion culinaria,
- organizar ingredientes,
- planificar menus,
- generar listas de compra,
- cocinar colaborativamente,
- y conservar memoria emocional asociada a la cocina familiar.

El proyecto es un ecosistema distribuido compuesto por:

- Backend Spring Boot.
- Aplicacion Android nativa (Kotlin + Compose).
- Aplicacion Desktop JavaFX.
- Aplicacion iOS (Kotlin Multiplatform + Compose Multiplatform).
- Modulo compartido KMP `shared/`.
- Base de datos PostgreSQL (en Hetzner; migrada desde MySQL en julio 2026).
- Sincronizacion multiplataforma.

La experiencia debe sentirse:
- calida,
- moderna,
- premium,
- fluida,
- emocional,
- confiable,
- y extremadamente facil de usar.

---

# FILOSOFIA DEL PRODUCTO

La aplicacion NO debe sentirse como:
- un ERP,
- una app corporativa,
- una hoja Excel,
- una simple coleccion de recetas,
- ni una herramienta tecnica fria.

Debe sentirse como:
- un espacio familiar,
- acogedor,
- organizado,
- elegante,
- visualmente satisfactorio,
- emocionalmente significativo,
- y seguro para datos personales.

El foco principal es:
- experiencia de usuario,
- simplicidad,
- sincronizacion familiar,
- privacidad,
- fiabilidad,
- y placer visual.

---

# STACK TECNOLOGICO

## Backend

- Spring Boot.
- Java 21+.
- Spring Security.
- Spring Data JPA.
- PostgreSQL.
- JWT.
- Flyway o Liquibase.
- OpenAPI/Swagger.

## Android

- Android nativo.
- Kotlin + Compose.
- Material You 3.
- Retrofit mientras no se migre a Ktor compartido.
- Room mientras no se migre a SQLDelight compartido.
- WorkManager.
- ViewModel.
- Repository Pattern.

## Desktop

- JavaFX.
- Maven.
- HTTP API Client.
- MVVM ligero.
- Cache local controlada.

## iOS

- Kotlin Multiplatform (KMP).
- Compose Multiplatform donde sea viable.
- Ktor para cliente HTTP cross-platform.
- SQLDelight para base de datos local cross-platform.
- iOS Background Tasks para sincronizacion periodica.
- Keychain para almacenamiento seguro de tokens.
- MVVM compartido con Android via modulo `shared/`.

## Modulo Compartido KMP (`shared/`)

- Logica de negocio: repositories, use cases, modelos de dominio.
- DTOs de red compartidos.
- Clientes HTTP Ktor.
- Esquemas SQLDelight.
- Estrategia: extraer incrementalmente desde Android sin romper lo existente.

## Base de Datos

- PostgreSQL como base principal (en Hetzner via WireGuard; migrada desde MySQL en julio 2026).
- Nunca depender de SQLite como fuente maestra.
- Las aplicaciones cliente deben comunicarse mediante API HTTP.

---

# ESTRUCTURA DEL PROYECTO

- `backend/` - Backend Spring Boot.
- `android/` - Aplicacion Android (Kotlin + Compose).
- `desktop/` - Aplicacion JavaFX.
- `ios/` - Aplicacion iOS (KMP + Compose Multiplatform).
- `shared/` - Modulo KMP compartido Android + iOS.
- `database/` - Scripts y migraciones.
- `docs/` - Documentacion.

No crear estructura nueva sin necesidad. Evolucionar incrementalmente.

---

# MODULOS PRINCIPALES

- Usuarios.
- Familias.
- Invitaciones y roles familiares.
- Recetas.
- Ingredientes.
- Stock familiar.
- Listas de compra.
- Menus semanales.
- Favoritos.
- Valoraciones.
- Historial culinario.
- Fotos.
- Temporizadores.
- Notas familiares.
- Preferencias y privacidad.
- Sincronizacion offline.

---

# EXPERIENCIA VISUAL

## Estilo Visual

Inspiracion:
- Notion.
- Material You.
- Apple Design.
- Pinterest Food UX.
- Apps editoriales de cocina premium.

La interfaz debe sentirse:
- limpia,
- moderna,
- espaciosa,
- calida,
- premium,
- emocional,
- tactil,
- y familiar.

## Paleta

- Tonos tierra.
- Verdes suaves.
- Amarillos calidos.
- Naranjas apetecibles.
- Neutros crema o grafito segun modo.
- Rojo solo para errores o acciones destructivas.

## UX

- Microanimaciones suaves.
- Dark Mode premium.
- Light Mode acogedor.
- Navegacion clara.
- Tipografia muy legible en cocina.
- Jerarquia visual fuerte.
- Interfaces tactiles comodas.
- Estados vacios utiles y humanos.
- Errores explicados en lenguaje claro.
- Accesibilidad real, no decorativa.

---

# PRINCIPIOS GENERALES

- Codigo minimo viable.
- Cambios quirurgicos.
- Evitar overengineering.
- Respetar el estilo existente.
- No refactorizar codigo estable innecesariamente.
- Priorizar claridad y mantenibilidad.
- Priorizar UX real sobre arquitectura innecesariamente compleja.
- Aplicar YAGNI estrictamente.
- No introducir dependencias pesadas sin justificar coste, licencia y mantenimiento.

---

# 1. PIENSA ANTES DE PROGRAMAR

- Declarar suposiciones relevantes.
- Detectar riesgos de regresion.
- Leer dependencias antes de modificar archivos.
- Pensar paso a paso antes de proponer codigo.
- Priorizar siempre la solucion mas simple.
- Explicar trade-offs importantes.
- No asumir nombres de metodos, endpoints, clases, tablas o columnas sin verificarlos.
- Confirmar si un cambio afecta contratos compartidos antes de tocar modelos sincronizados.

---

# 2. CAMBIOS QUIRURGICOS

- Modificar solo lo estrictamente necesario.
- No mover archivos sin necesidad.
- No reorganizar arquitectura por iniciativa propia.
- Limpiar unicamente el codigo afectado.
- No tocar artefactos generados salvo peticion explicita.
- Releer siempre el bloque antes de entregarlo.
- No mezclar refactor, feature y correccion de seguridad salvo necesidad clara.

---

# 3. SINCRONIZACION MULTIPLATAFORMA

Regla critica del proyecto.

Antes de modificar:
- DTOs,
- entidades,
- endpoints,
- serializacion,
- autenticacion,
- nombres JSON,
- enums,
- validaciones,
- modelos sincronizados,
- migraciones,
- repositorios compartidos,
- o esquemas locales,

validar impacto simultaneo en:
- Backend,
- Android,
- Desktop,
- iOS,
- `shared/`,
- PostgreSQL,
- Room o SQLDelight cuando aplique.

Nunca asumir que un cambio backend es transparente para Android, Desktop o iOS.

Validar:
- serializacion,
- nullabilidad,
- codigos HTTP,
- formatos de fecha,
- compatibilidad JSON,
- compatibilidad de enums,
- autenticacion,
- paginacion,
- migraciones,
- estrategia offline,
- y compatibilidad hacia atras cuando existan clientes ya publicados.

---

# 4. SINCRONIZACION OFFLINE

Android y iOS deben poder funcionar temporalmente offline cuando la funcionalidad lo requiera.

Toda entidad sincronizable debe incluir:
- `id`.
- `createdAt`.
- `updatedAt`.
- `syncVersion`.
- `deleted`.

## Reglas

- Usar soft delete.
- Nunca eliminar fisicamente registros sincronizados salvo purga explicita y segura.
- Resolver conflictos con estrategia documentada: Last Write Wins solo si no destruye informacion importante.
- Sincronizacion incremental obligatoria.
- Evitar duplicados.
- Mantener integridad entre clientes.
- No filtrar datos de una familia a otra durante merge, cache o resolucion de conflictos.

---

# 5. AUTENTICACION, FAMILIAS Y PRIVACIDAD

## Reglas

- Autenticacion mediante JWT.
- Refresh tokens obligatorios si hay sesiones persistentes.
- Nunca hardcodear secretos.
- Validar ownership en todos los endpoints.
- Toda familia funciona como tenant logico.
- Validar permisos en backend aunque el cliente oculte acciones.
- Minimizar datos personales en logs, errores y analytics.

## Seguridad Familiar

Ningun usuario debe poder:
- acceder a recetas ajenas,
- ver fotos ajenas,
- consultar listas de otra familia,
- leer notas privadas,
- modificar menus sin permiso,
- usar IDs predecibles para enumerar recursos,
- ni recibir datos familiares en respuestas no autorizadas.

## Privacidad

- No registrar tokens, emails completos, nombres de archivos, rutas locales o contenido de notas/recetas sensibles salvo necesidad tecnica justificada.
- Permitir borrar cuenta, salir de familia y limpiar cache local cuando el producto lo contemple.
- Cualquier funcion IA o externa debe requerir consentimiento explicito antes de enviar datos familiares.

---

# 6. CONTRATOS API

## Reglas

- Nunca exponer entidades JPA directamente.
- Usar DTOs explicitos.
- JSON camelCase consistente.
- API versionada: `/api/v1/`.
- Respuestas coherentes entre Android, Desktop e iOS.
- Errores consistentes y seguros: utiles para el usuario, sin filtrar stack traces ni secretos.

## API Design

- Paginacion obligatoria en listas grandes.
- Filtros simples y consistentes.
- Fechas en UTC ISO-8601.
- Validacion de entrada centralizada.
- Idempotencia cuando aplique a sincronizacion.
- Rate limiting o mitigacion equivalente en endpoints sensibles.

---

# 7. GESTION DE IMAGENES Y ARCHIVOS

## Reglas

- Nunca almacenar imagenes completas en la base de datos salvo requerimiento explicito y diseno de almacenamiento/cifrado.
- Backend almacena URLs, metadata y referencias.
- Usar storage controlado, no rutas arbitrarias proporcionadas por usuario.
- Separar originales, thumbnails y temporales.
- Confirmar politica de borrado y limpieza de huerfanos.

## Clientes

### Android

- Generar thumbnails.
- Lazy loading.
- Compresion automatica con limites.
- Cache local controlada.
- No bloquear UI thread durante decodificacion o subida.

### Desktop

- Lazy loading.
- Cache visual.
- Evitar cargar imagenes pesadas innecesariamente.
- Usar tareas en segundo plano para IO y transformaciones.

### iOS/KMP

- Usar abstracciones `expect/actual` cuando la API de imagen o storage sea especifica de plataforma.
- No duplicar logica de dominio si puede vivir en `shared/`.

## Seguridad

- Validar formatos permitidos por allowlist.
- Validar extension y tipo real cuando sea posible.
- Limitar tamaño maximo, dimensiones, megapixeles y numero de imagenes por lote.
- Sanitizar nombres de archivo.
- Normalizar rutas y bloquear path traversal.
- Limpiar metadata EXIF/GPS cuando el preset seguro o el usuario lo indiquen.
- Tratar SVG como formato activo/peligroso: bloquear scripts, enlaces externos y recursos remotos o no soportarlo inicialmente.
- No sobrescribir archivos sin confirmacion o politica explicita.

---

# 8. RENDIMIENTO

## Android

- Nunca bloquear UI Thread.
- Lazy loading obligatorio.
- Paginacion obligatoria.
- Cache local para recetas y menus.
- Optimizar consumo de bateria.
- Usar WorkManager para tareas diferidas fiables.

## Desktop

- Nunca bloquear JavaFX Thread.
- Usar Services/Tasks.
- Virtualizacion en tablas y grids grandes.
- Lazy loading visual.
- Mostrar progreso en operaciones largas.

## Backend

- Evitar N+1.
- Validar indices SQL.
- Evitar overfetching.
- Optimizar queries frecuentes.
- Usar transacciones claras y acotadas.
- Evitar procesar imagenes pesadas en request thread si puede diferirse.

---

# 9. BASE DE DATOS

## Reglas

- Usar Flyway o Liquibase.
- Nunca depender de `ddl-auto=update` en produccion.
- Toda migracion debe ser versionada.
- Migraciones destructivas requieren backup, plan de rollback y autorizacion explicita.
- Mantener integridad referencial entre familias, usuarios y datos compartidos.

## Seguridad

- Nunca hardcodear credenciales.
- Variables de entorno obligatorias.
- No loggear queries con datos sensibles en produccion.
- Aplicar minimo privilegio al usuario de base de datos.
- Revisar indices para campos usados en ownership y filtros por familia.

---

# 10. FUNCIONALIDADES IA

La IA debe ser:
- util,
- practica,
- opcional,
- privada por diseño,
- y no intrusiva.

## Posibles funciones

- Sugerencias de recetas.
- Sugerencias segun stock.
- OCR de recetas antiguas.
- Recomendaciones de menu.
- Planificacion inteligente.
- Sustitucion de ingredientes.
- Extraccion de texto desde fotos de recetas.

## Reglas

- Nunca enviar datos sensibles sin consentimiento explicito.
- Mantener fallback manual siempre disponible.
- Explicar claramente cuando una sugerencia proviene de IA.
- Permitir modo local-only si se implementan capacidades locales.
- No usar contenido familiar para entrenamiento, telemetria o analitica sin consentimiento explicito y documentado.

---

# 11. ANDROID

## Arquitectura

- MVVM.
- Repository Pattern.
- Retrofit mientras aplique.
- Room mientras aplique.
- WorkManager.
- Material You 3.

## UX

- Bottom Navigation.
- Navigation Drawer solo si aporta claridad.
- Widgets.
- Accesibilidad tactil.
- Modo cocina.
- Modo manos libres cuando sea viable.

## Reglas

- Mantener compatibilidad moderna Android.
- Evitar Activities o ViewModels gigantes.
- Centralizar networking.
- Respetar lifecycle Android.
- Evitar recomposiciones costosas en Compose.
- No bloquear hilo principal con IO, imagenes o sincronizacion.

---

# 12. DESKTOP JAVAFX

## Arquitectura

- MVVM ligero.
- HTTP API Client.
- Cache local.
- Services/Tasks para trabajos en segundo plano.

## UX

- Sidebar moderna.
- Dashboard visual.
- Filtros rapidos.
- Modo Cocina.
- Navegacion fluida.
- Estados vacios claros.

## Reglas

- Nunca bloquear JavaFX UI Thread.
- Virtualizacion obligatoria.
- Lazy loading visual.
- Mantener experiencia premium.
- Mostrar errores recuperables sin colapsar la app.

---

# 13. iOS (KMP + COMPOSE MULTIPLATFORM)

## Arquitectura

- Kotlin Multiplatform + Compose Multiplatform.
- MVVM compartido con Android mediante `shared/`.
- SQLDelight para base de datos local.
- Ktor para red.
- iOS Background Tasks para sincronizacion periodica.
- Keychain para almacenamiento seguro de tokens.

## UX

- Navegacion adaptada a patrones iOS.
- Soporte Dynamic Island o widgets iOS solo si aporta valor real.
- Gestos nativos iOS.
- Modo cocina adaptado a iOS.
- Offline-first con la misma estrategia conceptual que Android.

## Reglas

- No duplicar logica de negocio: extraer al modulo `shared/`.
- Mantener Android y Desktop estables durante la construccion de iOS.
- Migracion incremental: un repositorio cada sprint, no big-bang.
- Usar `expect/actual` para APIs platform-specific: Keychain, BackgroundTasks, filesystem, imagenes.
- iOS no intercepta botones de volumen: el modo manos libres se adapta con gestos.
- Android Widgets no equivalen a iOS Widgets: WidgetKit requiere codigo Swift separado.

## Limitaciones conocidas

- Botones de volumen en CookingScreen: solo funciona en Android.
- Android Widgets existentes no tienen equivalente directo en KMP.
- Desktop JavaFX no migra a KMP: permanece independiente.

---

# 14. BACKEND

## Arquitectura

- Controllers ligeros.
- Services con logica de negocio.
- DTOs explicitos.
- Seguridad centralizada.
- Validadores claros.
- Repositories sin logica de autorizacion critica.

## Seguridad

- Validar todas las entradas.
- Aplicar OWASP Top 10.
- Validar permisos en endpoints.
- Nunca confiar en datos cliente.
- Centralizar ownership por familia.
- No filtrar stack traces en respuestas.
- Revisar CORS, CSRF segun tipo de cliente y almacenamiento de token.

---

# 15. SEGURIDAD

Aplicar OWASP siempre que exista:
- autenticacion,
- autorizacion,
- subida de imagenes,
- networking,
- datos personales,
- notas familiares,
- almacenamiento,
- sincronizacion,
- IA,
- cache local,
- exportacion o importacion de datos.

## Reglas Obligatorias

- No hardcodear secretos.
- No exponer tokens.
- Sanitizar inputs.
- Validar ownership.
- Aplicar minimo privilegio.
- Usar allowlists cuando se acepten tipos, formatos o acciones.
- Normalizar rutas antes de operar con archivos.
- Limitar tamaño y complejidad de entradas.
- Evitar informacion sensible en logs, analytics y mensajes de error.
- Cifrar o proteger tokens en clientes: Keychain, EncryptedSharedPreferences o mecanismo equivalente.

---

# 16. TESTS Y VALIDACION

Antes de cerrar una tarea:

- Validar compilacion.
- Validar imports.
- Ejecutar tests relevantes.
- Validar sincronizacion si aplica.
- Validar contratos API si aplica.
- Validar compatibilidad Android/Desktop/iOS si aplica.
- Validar migraciones si se toca base de datos.
- Validar ownership y permisos si se toca backend.
- Validar que no se rompe el flujo principal de recetas, familias y menus.

## Backend

```bash
./gradlew test
./gradlew build
```

## Android

```bash
./gradlew :android:test
./gradlew :android:assembleDebug
```

## Desktop

```bash
mvn test
mvn -DskipTests compile
```

Ajustar comandos al build real del repositorio. No inventar resultados si el comando no se ejecuto.

---

# 17. ANIMACIONES Y FEEDBACK UX

## Regla Principal

La transicion entre estados no debe ser abrupta. Toda aparicion, desaparicion o cambio de estado visible debe estar animado de forma sutil y natural, salvo preferencia de animaciones reducidas.

## Compose (Android e iOS)

- Usar `AnimatedVisibility` en lugar de `if (visible) { ... }` para mostrar/ocultar paneles y secciones.
- Usar `animateContentSize()` en contenedores que cambian de tamaño al expandirse.
- Usar `Crossfade` al cambiar entre estado loading y contenido real.
- Usar `animateItemPlacement()` o alternativa vigente en listas cuando aplique.
- Usar `animateColorAsState` en cambios de color de badges, chips y estados.
- Usar `AnimatedContent` para cambios de numero: timers, contadores, raciones.
- Fisica preferida: `spring()` para interacciones tactiles, `tween()` para transiciones de pantalla.
- Duraciones: entradas 200-300ms, salidas 150-200ms, transiciones de pantalla 250-350ms.

## JavaFX Desktop

- Usar `FadeTransition` al cambiar vistas en sidebar (250ms, Linear).
- Usar `ScaleTransition` al abrir modales (200ms desde 0.95 a 1.0, EaseOut).
- Usar `ScaleTransition` en hover de cards (100ms, 1.0 a 1.02).
- Usar `SequentialTransition` al eliminar items (FadeOut 150ms y colapso 150ms).
- Toda animacion JavaFX corre en el JavaFX Application Thread. Nunca en hilos de fondo.
- Tooltip obligatorio en botones sin label visible (delay 400ms). Formato: `Accion (Ctrl+X)`.

## Hapticos

- Obligatorio en acciones destructivas cuando la plataforma lo soporte.
- Obligatorio al cambiar paso en CookingScreen si mejora el modo cocina.
- Obligatorio al guardar con exito o error, con feedback distinto.
- Implementar via `LocalHapticFeedback.current` en Compose Android.
- En iOS: usar `UIImpactFeedbackGenerator`, `UISelectionFeedbackGenerator`, `UINotificationFeedbackGenerator` via `expect/actual`.
- Siempre desactivables en preferencias de usuario.

## Sonidos

- Opcionales y siempre desactivables en preferencias (desactivados por defecto).
- Nunca autoplay sin accion del usuario.
- En Android: `SoundPool` para efectos cortos.
- En Desktop: `AudioClip` JavaFX para efectos.
- En iOS: seguir politica Apple; no usar sonidos del sistema sin permiso explicito.

## Tooltips y Accesibilidad

- `TooltipBox + PlainTooltip` en Android en botones de TopAppBar sin label visible cuando aplique.
- `contentDescription` completo y descriptivo en elementos interactivos.
- `semantics { heading() }` en titulos de seccion para TalkBack.
- `.accessibilityLabel()` y `.accessibilityHint()` en iOS para VoiceOver.
- `.help()` modifier en botones iOS para tooltip VoiceOver y hover iPadOS.
- Focus order explicito en formularios.
- Contraste AA minimo.

## Skeleton Loading

- Usar skeleton o shimmer en lugar de spinners en listados con carga de red.
- Mostrar minimo 3-5 items skeleton del tamaño real del contenido esperado.
- Desaparecer con `Crossfade` o `AnimatedVisibility` al llegar los datos reales.

## Reglas de Calidad

- Ninguna animacion bloquea el hilo UI.
- Evitar mas de 2 animaciones simultaneas en la misma region visual.
- Todo efecto haptico o sonoro debe tener un toggle en preferencias.
- Si el sistema tiene animaciones reducidas activadas, simplificar o eliminar las propias.
