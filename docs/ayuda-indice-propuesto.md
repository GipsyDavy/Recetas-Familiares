# Ayuda completa — índice propuesto

> **Documento para revisar, no implementado.** Propone qué debe cubrir la ayuda de Desktop y de
> Android antes de escribir una sola sección. Escribir 40 secciones y que no sean las que se querían
> sería tirar el trabajo.

## Para quién se escribe

Una familia, no un equipo técnico. Edades variadas y prisa: alguien con las manos manchadas
buscando cómo poner el temporizador. De ahí tres reglas:

1. **Responder en el sitio.** La ayuda contextual de cada pantalla es la principal; el manual es el
   respaldo.
2. **Frases cortas y de tú.** Nada de «el sistema permite al usuario»; sí «pulsa Cocinar».
3. **No documentar lo obvio.** Si un botón dice «Guardar», no hace falta una sección para explicarlo.

## Qué hay hoy

| | Desktop | Android |
|---|---|---|
| Ayuda contextual | **9 temas**, botón del sidebar y F1 | no hay |
| Guía de bienvenida | sí, reabrible desde Ajustes | sí, onboarding inicial |
| Manual, FAQ, glosario, buscador | no | no |

## Estructura propuesta

### Capa 1 — Ayuda contextual (ampliar lo que ya existe)

Los 9 temas actuales dan 3 consejos cada uno. La propuesta es llevarlos a **una pantalla = un tema
con lo que de verdad se puede hacer ahí**, incluyendo el «cómo se hace» paso a paso y los atajos.

Pantallas a cubrir: Inicio · Recetas · Detalle de receta · Modo cocina · Stock · Menú semanal ·
Lista de la compra · Notas · Chat familiar · Chats privados · Miembros · Búsqueda global · Ajustes ·
Perfil y cuenta.

Son **14 temas** frente a los 9 de hoy.

### Capa 2 — Centro de ayuda

Ventana propia con índice navegable. Trece secciones:

| # | Sección | Cubre |
|---|---|---|
| 1 | Primeros pasos | instalar, entrar, qué es una familia |
| 2 | Recetas | crear, editar, ingredientes, pasos, fotos, PDF, favoritas, copiar entre familias |
| 3 | Modo cocina | pasos, temporizador, atajos de teclado, gestos en móvil |
| 4 | Despensa | cantidades, unidades, caducidades, umbral mínimo, avisos |
| 5 | Menú semanal | asignar comidas, generar la lista de la compra |
| 6 | Lista de la compra | añadir, marcar, pasar lo comprado al stock |
| 7 | Notas y chat | notas familiares, chat de familia, chats privados |
| 8 | Tu familia | invitar, roles y qué puede hacer cada uno, salir de una familia |
| 9 | Cuenta y privacidad | contraseña, recuperación por código, verificar correo, cerrar sesión, borrar cuenta y qué se borra |
| 10 | Sincronización | qué pasa sin conexión, cuándo se sincroniza, conflictos |
| 11 | Apariencia y accesibilidad | temas, modo oscuro, tipografía, reducir movimiento, sonidos, hápticos |
| 12 | Actualizaciones | cómo llega el aviso, cómo se instala, por qué no hay que desinstalar |
| 13 | Problemas frecuentes | los de abajo |

### Capa 3 — Desde el error a la solución

Cada mensaje de error con causa conocida enlaza a su sección. Los candidatos reales, sacados del
código y de lo que ha pasado estos días:

- «No se pudo conectar con el servidor» → Sincronización.
- Sesión caducada → Cuenta.
- «No se pudo comprobar si hay actualizaciones» → Actualizaciones.
- Contraseña olvidada → Cuenta, con el detalle de que el código llega por correo y se pega en la app.
- SmartScreen al instalar en Windows → Actualizaciones.
- Android pide permiso para instalar desde fuente desconocida → Actualizaciones.

## Android

**Mismo contenido, distinta navegación.** No conviene calcar la ventana de Desktop en un móvil.

- Icono **?** en la barra superior de cada pantalla → ayuda contextual de esa pantalla, en hoja
  inferior (bottom sheet).
- Entrada **«Ayuda»** en Perfil → centro de ayuda con las 13 secciones.
- Sin buscador en la primera versión (ver decisiones).

## Decisiones que hacen falta antes de escribir

Cada una lleva mi recomendación. Si estás de acuerdo con todas, basta con decir «adelante».

| # | Decisión | Recomendación | Por qué |
|---|---|---|---|
| 1 | **¿Buscador de ayuda?** | **No en la primera versión** | Con 13 secciones bien tituladas, el índice basta. El buscador cuesta bastante y se puede añadir después sin rehacer nada. |
| 2 | **¿Contenido embebido o descargado del servidor?** | **Embebido** | Funciona sin conexión, que es justo cuando más falta hace. No hay CMS ni ganas de mantener uno. Coste: cambiar la ayuda exige publicar versión. |
| 3 | **¿FAQ y glosario aparte?** | **FAQ sí, glosario no** | Las preguntas frecuentes resuelven; un glosario en una app de cocina familiar es relleno. |
| 4 | **¿Capturas de pantalla?** | **No** | Envejecen mal: cada cambio de interfaz las deja mintiendo, como pasó con «Versión 1.1» y con «MySQL». Texto y, donde ayude, un icono. |
| 5 | **¿Se escribe una vez para las dos plataformas?** | **Texto compartido, presentación distinta** | El contenido es el mismo; solo cambia cómo se muestra. Evita que Desktop y Android digan cosas distintas. |
| 6 | **¿Tono?** | **De tú, frases cortas, sin jerga** | Es el tono que ya usa la aplicación. |

## Cómo se haría, por orden

1. **Redactar el contenido** de las 13 secciones y los 14 temas contextuales. Es la mayor parte del
   trabajo y no es código.
2. **Desktop**: ampliar `HelpDialog` a 14 temas y añadir el centro de ayuda.
3. **Android**: hoja inferior contextual y pantalla de centro de ayuda.
4. **Enlazar los errores** a sus secciones.
5. **Publicar la v1.4** con esto y con todo lo que ya está en `main` sin publicar: el arreglo del
   diálogo de ayuda, «Buscar actualizaciones» y los sonidos por niveles.

## Lo que este sprint no resuelve

- **No hay tests de renderizado.** La ayuda se verificará abriéndola, como se ha hecho hoy. Sigue
  siendo el hueco grande del proyecto.
- La ayuda embebida obliga a publicar versión para corregir una errata.
