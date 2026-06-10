# Roadmap

## Fase 0 - Base del repositorio

- Monorepo limpio.
- Git y GitHub configurados.
- `.gitignore` global.
- Documentacion inicial.

## Fase 1 - Backend base

- Proyecto Spring Boot.
- MySQL y Flyway.
- Autenticacion JWT.
- Usuarios y familias.
- OpenAPI.

## Fase 2 - Recetas MVP

- CRUD de recetas.
- Ingredientes y pasos.
- Favoritos.
- Busqueda simple.
- Paginacion.

## Fase 3 - Desktop MVP

- Proyecto JavaFX real.
- Login.
- Dashboard.
- Listado y detalle de recetas.
- Crear y editar recetas.

## Fase 4 - Android MVP

- Proyecto Android real.
- Login.
- Recetas.
- Cache Room inicial.
- Sincronizacion basica.

## Fase 5 - Sincronizacion

- Pull incremental.
- Push de cambios locales.
- Soft delete.
- Last Write Wins inicial.

## Fase 6 - Experiencia premium

- Modo cocina.
- Menus semanales.
- Lista de compra.
- Stock familiar.
- Fotos.
- Dark mode.

## Fase 7 - Ayuda integrada y documentacion de usuario

- Centro de ayuda dentro de la aplicacion.
- Manual de usuario integrado.
- Ayuda contextual por pantalla.
- Guias paso a paso y onboarding.
- Tooltips avanzados.
- Ejemplos, FAQ y glosario.
- Documentacion offline.
- Buscador de ayuda.
- Enlaces desde errores a soluciones.
- Modo principiante / avanzado.

## Fase 8 - Chat familiar

- Chat por familia como modulo independiente de notas.
- Backend con mensajes paginados, autor, fecha, familia y validacion estricta de ownership.
- REST para historial paginado y WebSocket/STOMP o equivalente para tiempo real.
- Fase 8.1: texto, emojis, historial paginado, estados basicos de lectura y fallback por polling.
- Fase 8.2: imagenes con storage protegido, validacion de tipo/tamano y miniaturas.
- Fase 8.3: videos con limites estrictos, previews, control de progreso y politica de limpieza.
- Fase 8.4: push notifications para Android/iOS y mejoras avanzadas.
- Primera pantalla candidata en Android; segunda implantacion en Desktop; iOS despues de estabilizar contrato y paridad basica.
- Reacciones, audio, edicion avanzada, borrado complejo y chats privados quedan para iteraciones posteriores.
- Requisitos de seguridad: no exponer mensajes entre familias, proteger adjuntos, validar MIME/extension/tamano, aplicar rate limiting y minimizar datos sensibles en logs.
