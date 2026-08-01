# Interfaz.md - Sistema Visual y UX Recetas Familiares

Este documento define la experiencia visual, UI/UX, animaciones, accesibilidad y ayuda contextual del producto.

No sustituye a:
- `CLAUDE.md`: reglas de trabajo y seguridad.
- `CONTINUAR.md`: estado operativo actual.
- `Resumen.md`: vision y estado funcional consolidado.

---

## 1. Principio de Producto

Recetas Familiares debe sentirse como un espacio digital familiar, calido, organizado y premium. No debe parecer un ERP, una tabla de datos ni una utilidad tecnica.

La interfaz debe priorizar:
- claridad,
- calma visual,
- lectura facil en cocina,
- accesibilidad,
- acciones seguras,
- feedback inmediato,
- coherencia multiplataforma.

---

## 2. Direccion Visual

Inspiracion:
- Notion por claridad y orden.
- Material You por adaptabilidad y componentes tactiles.
- Apple Design por suavidad y jerarquia.
- Pinterest Food por apetito visual e imagenes.
- Apps editoriales de cocina premium por calidez.

Personalidad visual:
- Calida, no infantil.
- Premium, no ostentosa.
- Emocional, no recargada.
- Familiar, no corporativa.
- Moderna, no generica.

---

## 3. Sistema de Temas

El sistema de temas debe mantenerse coherente en Android, Desktop e iOS.

Temas actuales/documentados:
1. Bosque.
2. Terracota.
3. Ocaso.
4. Mediterraneo.
5. Lavanda.
6. Oliva.
7. Canela.
8. Menta.
9. Frambuesa.
10. Noche de Verano.

Temas de identidad contemporanea añadidos en 2026:
11. Rubi Nocturno: tema principal, con carbon, borgoña, rubi y luz coral/cobre. Su
    expresion de referencia es la variante oscura, premium y calida, sin estetica
    agresiva ni de videojuego.
12. Aurora Boreal: indigo, menta luminosa y violeta.
13. Jade Imperial: jade profundo, celadon y cobre.
14. Cobre Lunar: grafito, cobre y amatista.
15. Ciruela Solar: ciruela, ambar y seda.
16. Coral Abisal: oceano profundo, turquesa y coral.

Cada tema debe tener:
- modo claro,
- modo oscuro,
- opcion sistema cuando la plataforma lo soporte,
- persistencia local,
- aplicacion inmediata sin reinicio.

Reglas:
- No introducir colores sueltos fuera de tokens o variables del tema.
- Rojo solo para errores o acciones destructivas, salvo el rojo de identidad de
  Rubi Nocturno. En ese tema, error y peligro deben conservar tokens propios y
  diferenciarse tambien mediante icono, texto, contexto y confirmacion; nunca solo
  por el color.
- Amarillo/ambar solo para advertencias o caducidad.
- El modo oscuro debe ser premium y legible, no negro plano.
- Los diez identificadores historicos no se renombran ni se eliminan: forman parte
  de las preferencias persistidas de los clientes.
- Rubi Nocturno se presenta como tema principal y recomienda modo oscuro, pero no
  fuerza el modo ni sustituye una seleccion guardada por el usuario.
- Los selectores deben mostrar nombre completo, identidad cromatica y seleccion
  accesible; no deben depender de una cuadricula fija que recorte temas o contenido.

---

## 4. Tipografia

Objetivo:
- titulos calidos y con personalidad,
- cuerpo muy legible,
- lectura comoda en cocina y dispositivos moviles.

Direccion recomendada:
- Titulos: Nunito o alternativa redondeada premium.
- Cuerpo: Lato o alternativa legible neutra.
- Desktop puede usar fallback del sistema si no hay empaquetado de fuentes, pero debe mantener escala y pesos coherentes.

Reglas:
- Si se declara una fuente premium, empaquetarla realmente en la plataforma correspondiente.
- No depender de fuentes externas por red.
- Mantener contraste AA minimo.
- Tamano suficiente en modo cocina.

---

## 5. Espaciado, Forma y Elevacion

Usar tokens, no valores arbitrarios repetidos.

Escala base recomendada:
- `xxs`: 2
- `xs`: 4
- `sm`: 6
- `md`: 8
- `lg`: 12
- `xl`: 16
- `xxl`: 24
- `section`: 32

Radios:
- Chips: redondeados suaves.
- Cards: radio medio/alto.
- Modales: radio amplio.
- Avatares: circulares.

Elevacion:
- Usar elevacion para jerarquia funcional, no decoracion.
- Evitar sombras duras.
- En Desktop, hover y estado activo deben sustituir exceso de sombra.

Profundidad contemporanea:
- Los temas nuevos pueden combinar gradientes de sus colores semanticos, bordes
  luminosos muy sutiles y sombras tintadas en elementos destacados.
- El efecto 2.5D se limita a seleccion, hover o pulsacion: escala aproximada
  `0.98-1.02`, desplazamiento de `1-2 px/dp` e inclinacion minima.
- No aplicar blur, parallax o rotacion continua a listas, texto ni pantallas
  completas. La legibilidad y el rendimiento tienen prioridad.

---

## 6. Componentes Clave

### Recetas
- Cards con imagen o placeholder cuidado.
- Chips de tiempo, dificultad y porciones.
- Estado favorito visible pero no invasivo.
- Detalle con portada, ingredientes, pasos y accion principal `Cocinar`.

### Stock
- Fechas de caducidad en lenguaje humano.
- Colores contextuales: correcto, proximo, caducado.
- Umbral minimo explicado con ayuda contextual.
- Acciones de editar/eliminar claras y seguras.

### Menu Semanal
- Semana visible.
- Dia actual destacado.
- Comidas organizadas por franja.
- Accion para asignar receta evidente.

### Lista de Compra
- Marcado rapido.
- Tachado legible.
- Agrupacion futura por categoria si aporta valor.
- Generacion desde menu explicada.

### Notas
- Deben sentirse privadas/familiares.
- Empty state humano.
- Busqueda visible.
- Pin o prioridad si se implementa.

### Perfil y Familia
- Avatar, nombre, email y rol.
- Stats familiares utiles.
- Invitaciones con explicacion de permisos.
- Acciones peligrosas con confirmacion.

---

## 7. Animaciones y Feedback

Regla general:
- Ningun cambio visual importante debe ser abrupto salvo que el usuario tenga animaciones reducidas.
- Las animaciones deben ayudar a entender estado, no distraer.

Duraciones recomendadas:
- Hover / press: 80-120ms.
- Mostrar/ocultar panel: 200-250ms.
- Transicion de pantalla: 250-350ms.
- Interpolacion al cambiar tema: 280-320ms.
- Modal/sheet entrada: 200-300ms.
- Skeleton a contenido: 300-400ms.
- Reordenacion/lista: 200-250ms.

Compose:
- `AnimatedVisibility` para aparicion/desaparicion.
- `Crossfade` para loading/contenido.
- `AnimatedContent` para contadores/timers.
- `animateContentSize()` en tarjetas expandibles.
- `spring()` para interaccion tactil.

JavaFX:
- `FadeTransition` en cambios de vista.
- `ScaleTransition` en modales y hover de cards.
- `SequentialTransition` al eliminar items.
- Animaciones siempre en JavaFX Application Thread.

Feedback:
- Visual siempre presente.
- Haptico activado si la plataforma lo soporta y con toggle.
- Sonido desactivado por defecto y con toggle.

Reglas de movimiento:
- La interpolacion de color se usa entre temas del mismo modo. El cambio
  claro/oscuro aplica el esquema completo para no perder contraste durante el
  punto medio de la transicion.
- Android respeta la escala de animacion configurada en el sistema.
- iOS consulta `Reducir movimiento` del sistema para omitir transiciones de tema,
  pestañas y profundidad interactiva, y reacciona si el ajuste cambia con la app
  abierta.
- Desktop ofrece `Ajustes > Apariencia > Movimiento > Reducir movimiento` y, al activarlo,
  omite fades, escalado, entradas de dialogo, desplazamientos y shimmer cosmeticos;
  los temporizadores funcionales se mantienen.

---

## 8. Accesibilidad

Obligatorio:
- Contraste AA minimo.
- Labels accesibles en iconos.
- Orden de foco claro.
- Navegacion por teclado en Desktop.
- Touch targets comodos en movil.
- Textos comprensibles sin depender solo de color.
- Soporte para animaciones reducidas.
- Modo cocina con letra grande y controles simples.

Android:
- `contentDescription` completo en elementos interactivos.
- `semantics { heading() }` en titulos relevantes.
- Tooltips en iconos sin texto.

iOS:
- `.accessibilityLabel()` y `.accessibilityHint()` donde aplique.
- Gestos compatibles con patrones iOS.

Desktop:
- Tooltips en botones sin label claro.
- Atajos documentados.
- Estados de foco visibles.

---

## 9. Estados de Carga, Error y Vacio

Loading:
- Preferir skeletons al spinner cuando se carga una lista o detalle estructurado.
- Mantener tamano similar al contenido real.
- Transicion suave a contenido.

Errores:
Cada error debe explicar:
- que ha pasado,
- por que puede haber pasado,
- que puede hacer el usuario,
- si puede reintentar,
- si hay detalles tecnicos opcionales.

Empty states:
- Sin recetas: crear primera receta + guia.
- Sin stock: anadir producto + explicar caducidades.
- Sin menu: planificar semana + ejemplo.
- Sin notas: crear nota familiar + explicar uso.
- Sin conexion: explicar que queda guardado localmente y que se sincronizara despues.

---

## 10. Ayuda Integrada

La ayuda debe vivir dentro de la app, no solo en documentos externos.

Capas:
1. Tooltip corto.
2. Popover contextual.
3. Articulo completo en centro de ayuda.

Centro de ayuda recomendado:
- Primeros pasos.
- Recetas.
- Ingredientes y pasos.
- Modo cocina.
- Stock y caducidades.
- Menu semanal.
- Lista de compra.
- Notas familiares.
- Fotos.
- Sincronizacion/offline.
- Familia, miembros, roles y permisos.
- Perfil, temas y ajustes.
- Seguridad y privacidad.
- FAQ.
- Glosario.

MVP de ayuda:
- Centro offline basico.
- Guia inicial saltable.
- Tooltips en campos complejos.
- Enlaces desde errores y empty states.
- Modo principiante/avanzado futuro.

---

## 11. Criterios Por Plataforma

### Android
- Bottom navigation clara.
- FAB solo para accion primaria real.
- Pull-to-refresh donde tenga sentido.
- Widgets utiles, no decorativos.
- Modo cocina optimizado para manos ocupadas.
- Offline-first visible y comprensible.

### Desktop
- Sidebar productiva con estado activo.
- `Perfil y cuenta` vive como pestaña comun dentro de `Ajustes`; Apariencia y Acerca de
  tambien son comunes, mientras Servidor y Diagnostico permanecen limitados a roles
  administrativos.
- Scrollbars estrechos y redondeados, con area interactiva estable y colores derivados
  de los tokens del tema activo; el sidebar usa sus propios tokens de contraste.
- Busqueda global accesible.
- Atajos de teclado.
- Tablas virtualizadas.
- Ventanas/modales no bloqueantes visualmente.
- Ajustes y diagnostico integrados como vistas.

### iOS
- Coherencia con Apple sin perder identidad Recetas.
- Gestos nativos.
- Navegacion clara.
- Paridad funcional razonable con Android.
- Keychain y permisos explicados al usuario cuando sea necesario.

---

## 12. Backlog UX Actual Recomendado

Resuelto recientemente (no retomar sin motivo):
- Fuentes reales empaquetadas: Nunito/Lato en `res/font` Android (Sprint 44). Desktop mantiene fallback del sistema segun seccion 4.
- Stats familiares en perfil/dashboard: Android y Desktop (Sprint 43).
- Perfil Desktop completo e integrado en `Ajustes > Perfil y cuenta`.
- Onboarding Desktop de primer arranque, reabrible desde Ajustes > Acerca de (Sprint 44).
- Shortcuts completos en modo cocina Desktop (Sprint 44).

Prioridad alta:
1. Completar paridad iOS en busqueda, filtros y skeletons.
2. Toggle global para hapticos.

Prioridad media:
1. Ayuda contextual MVP.
2. Fechas de caducidad iOS en lenguaje humano.
3. Mejorar algoritmo de iniciales.

Prioridad baja:
1. Mas ilustraciones editoriales.
2. Guias interactivas completas.
3. Modo principiante/avanzado.
4. FAQ y glosario extensos.

---

## 13. Criterios De Aceptacion Visual

Una mejora UI/UX se considera terminada solo si:
- respeta tema y tokens,
- funciona en claro y oscuro cuando aplique,
- tiene estado loading/error/empty si corresponde,
- no bloquea UI thread,
- es accesible por teclado o lector donde aplique,
- mantiene coherencia con las otras plataformas,
- fue validada con build o justificada si no se pudo validar.

---

## 14. Herramienta De Apoyo De Diseno

Plugin `impeccable` (`impeccable@impeccable`, v4.0.4, scope `user` en Claude Code, instalado 2026-08-01). Disponible en todos los proyectos.

Uso recomendado sobre este documento:
- `/impeccable shape <pantalla>` antes de construir una pantalla nueva.
- `/impeccable critique <pantalla>` para revision UX contra las secciones 1-6.
- `/impeccable audit <pantalla>` para accesibilidad (seccion 8) y responsive.
- `/impeccable animate <pantalla>` para aplicar la seccion 7.
- `/impeccable clarify <pantalla>` para copy de errores y estados vacios (seccion 9).
- `/impeccable polish` y `/impeccable harden` antes de aplicar la seccion 13.

Limites:
- Este documento manda. Si `impeccable` propone paleta, tipografia o patron que contradiga las secciones 2-6, gana `Interfaz.md`.
- Sus sugerencias no sustituyen validacion con build ni los criterios de aceptacion de la seccion 13.
- No usar subcomandos con egreso de red sobre datos familiares reales sin consentimiento explicito.
