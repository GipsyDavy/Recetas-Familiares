# Avisos de Actividad Familiar — Diseño

**Item de backlog:** punto 20 (segunda mitad), `paraImplementar.txt`. La primera mitad (presencia
online) se implemento en el sprint `2026-07-19-presencia-online-design.md`, que dejo
explicitamente fuera de alcance "avisos de actualizaciones de recetas, notas o stock... como
sprint propio futuro" (lineas 150-152 de ese spec). Este documento cierra esa mitad pendiente.

## Objetivo

Un miembro de la familia debe poder saber, sin abrir cada seccion una por una, si otro miembro
ha creado, editado o borrado una receta, una nota familiar o un item de stock desde la ultima vez
que el visito esa seccion — sin perderse cambios importantes, incluso si vuelve a la app dias
despues (no es un aviso efimero tipo "toast", es un indicador persistente tipo bandeja).

Fuera de alcance explicito: `MenuItem` y `ShoppingList` no generan aviso (el backlog solo
menciona recetas, notas y stock). Notificacion push con la app cerrada/en segundo plano: fuera de
alcance, ver "Decisiones de alcance" abajo.

## Decisiones de alcance (confirmadas con el usuario)

1. **Proposito:** aviso persistente tipo bandeja, no un toast ambiental. Debe quedar marcado
   hasta que el usuario abre esa seccion, incluso dias despues — mismo criterio que el badge de
   no-leidos del chat privado.
2. **Eventos que cuentan:** crear, editar y borrar, en las tres secciones (recetas, notas, stock)
   por igual. No se distingue por tipo de cambio ni se excluye la edicion de cantidad de stock
   (aceptado explicitamente el trade-off de mas "ruido" en stock a cambio de una regla unica y
   simple de explicar).
3. **Granularidad de "visto":** por seccion completa, no por item individual. Abrir la pantalla
   de Recetas marca TODOS los cambios pendientes de Recetas como vistos de una vez — mismo
   patron que abrir una conversacion de chat privado marca todos sus mensajes como leidos.
4. **Precision del indicador:** indicador simple (hay/no hay cambios sin ver), sin numero exacto.
   Se descarta deliberadamente un contador de eventos individuales: el mismo dia se corrigio un
   bug real en el chat privado (`PrivateInboxPing` sin `messageId`, sobre-contando en
   ediciones/borrados) causado exactamente por ese patron. Comparar "ultima actividad de la
   seccion" contra "ultima vez que el usuario la vio" es estructuralmente inmune a ese problema:
   no hay eventos que contar ni deduplicar.
5. **Mecanismo de entrega:** solo con la app abierta (en primer plano o recien reabierta), igual
   que el badge de chat privado hoy. Sin notificaciones push reales (Firebase Cloud Messaging ni
   equivalente) — anadir eso es un salto de infraestructura nuevo (tokens de dispositivo, permisos
   de notificacion del SO, envio push desde backend) no justificado por este alcance.
6. **Ubicacion visual:** un badge (punto, sin numero) sobre cada tab/item ya existente de
   Recetas, Stock y Notas — no un icono nuevo centralizado en la barra superior/TopAppBar, que ya
   tiene chat familiar, chat privado, tema y busqueda.

## Modelo de datos (backend)

Dos tablas nuevas, sin tabla de eventos que crezca sin limite:

- **`family_section_activity`**: `(family_id, section, last_activity_at)`, clave primaria
  compuesta `(family_id, section)`. Se actualiza (upsert atomico, `ON CONFLICT DO UPDATE` o
  equivalente JPA) cada vez que alguien crea, edita o borra una receta, nota o item de stock en
  esa familia.
- **`user_section_last_seen`**: `(user_id, family_id, section, last_seen_at)`, clave primaria
  compuesta `(user_id, family_id, section)`. Se actualiza cuando el usuario abre esa seccion.

`section` es un enum con 3 valores: `RECIPE`, `NOTE`, `STOCK`.

**Calculo de "no visto":** `family_section_activity.last_activity_at >
COALESCE(user_section_last_seen.last_seen_at, EPOCH)`. Un miembro sin fila de "ultima vez visto"
para una seccion (recien invitado, o seccion que nunca abrio) ve esa seccion como "no vista" — es
el comportamiento correcto: hay actividad historica que nunca ha mirado.

**Auto-actor:** cuando un usuario hace el cambio (crea/edita/borra), su propia fila en
`user_section_last_seen` se actualiza a `now()` en la MISMA operacion que actualiza
`family_section_activity` — asi el autor nunca ve su propio badge encendido por su propio cambio.

**Instrumentacion:** un servicio nuevo, `FamilyActivityService.recordActivity(familyId, section,
actorUserId)`, es el unico punto que escribe en `family_section_activity` +
`user_section_last_seen` (para el actor). `RecipeService`, `FamilyNoteService` y
`StockItemService` lo llaman desde sus metodos de crear/editar/borrar existentes — logica
centralizada en un solo sitio, no duplicada en los tres servicios.

## Endpoints REST

- `GET /api/v1/families/{familyId}/activity` → `{"RECIPE": bool, "NOTE": bool, "STOCK": bool}`.
  Requiere membership de la familia (mismo patron de autorizacion que el resto de endpoints de
  `families/**`). Se llama al cargar la familia activa (mismo momento que `loadFamilyStats()` /
  `loadFamilyMembers()` en Android, equivalente en Desktop) y al reconectar tras background/cierre.
- `POST /api/v1/families/{familyId}/activity/{section}/seen` → marca esa seccion como vista para
  el usuario autenticado (actualiza `user_section_last_seen`). Se llama al navegar a esa seccion.

## Tiempo real

Nuevo topic STOMP `/topic/families/{familyId}/activity`, mensaje ligero `{"section": "RECIPE"}`
(sin cuerpo del cambio, mismo criterio de minimizacion que el ping de inbox de chat privado).
Reutiliza el socket ya conectado para presencia — no se abre una conexion nueva, solo se
suscribe un topic adicional sobre el mismo `ChatSocket`/equivalente Desktop ya existente.

**Autorizacion:** `ChatStompAuthChannelInterceptor` ya valida membership de familia para
cualquier sufijo reconocido de `/topic/families/{familyId}/*` (hoy `/chat` y `/presence`, via
`extractFamilyId`). Se anade `/activity` a esa misma lista de sufijos reconocidos — sin logica de
autorizacion nueva, solo extender la lista ya existente.

**Efecto en el cliente al recibir el ping:** si el usuario no esta actualmente viendo esa
seccion, enciende el badge correspondiente sin necesidad de otro round-trip REST. Si SI la esta
viendo, no hace falta encenderlo (ya la esta viendo, cuenta como visto en cuanto salga de ella
via el `POST .../seen` que dispara la navegacion).

## Clientes

**Android:** `Badge` sin numero (Material3, `BadgedBox` alrededor del `icon` de cada
`NavigationBarItem`) sobre RECIPES, STOCK y NOTES en el `NavigationBar` existente
(`RecetasApp.kt`). Al cambiar `tab = MainTab.X` para una de esas tres, se llama
`POST .../activity/{section}/seen`. El `GET .../activity` inicial se carga en el mismo punto que
`loadFamilyStats()`/`loadFamilyMembers()` en `RecetasViewModel.kt`.

**Desktop:** mismo patron visual que `updateChatBadge(int unread)` (clase CSS superpuesta sobre
el boton del sidebar en `MainWindow.java`), pero sin numero — una marca/punto. Al hacer click en
`btnRecipes`/`btnStock`/`btnNotes` (dentro de `navigateTo(...)`), se llama al endpoint de "seen"
correspondiente.

**Sin cambios en `MenuItem`/`ShoppingList`** — fuera de alcance, ver Objetivo.

## Seguridad

- Ambos endpoints REST requieren membership de familia activa, mismo patron que el resto de
  `families/**` — 404 (no 403) si la familia no existe o el usuario no pertenece, evitando
  enumeracion.
- El topic STOMP nuevo se autoriza reutilizando la validacion de membership ya existente en
  `ChatStompAuthChannelInterceptor`, sin logica nueva de autorizacion.
- Sin filtracion de contenido: el ping de tiempo real solo lleva `section` (un enum de 3 valores
  fijos), nunca el contenido del cambio ni quien lo hizo.
- Notas familiares no tienen concepto de "nota privada" (confirmado en `FamilyNoteEntity`: no
  existe ningun campo de visibilidad) — todas son compartidas con la familia, asi que notificar
  "cambio en Notas" a todos los miembros nunca expone algo que un miembro no pudiera ya ver al
  abrir esa seccion.

## Testing

- Backend: tests de `FamilyActivityService` (upsert de actividad, auto-actor marca su propia
  vista, calculo correcto de "no visto" con `COALESCE`/epoch para primera vez). Tests de
  integracion en los controllers de recetas/notas/stock verificando que crear/editar/borrar
  dispara la actividad correcta. Test de autorizacion del nuevo topic STOMP (mismo patron que los
  tests ya existentes de `ChatStompAuthChannelInterceptor` para `/chat`/`/presence`).
- Android/Desktop: sin infraestructura de tests de UI existente para este tipo de indicador
  (mismo criterio ya aplicado hoy en el chat privado) — validacion manual del badge encendiendose
  y apagandose al navegar, documentada como pendiente humana.
