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

Cada tema debe tener:
- modo claro,
- modo oscuro,
- opcion sistema cuando la plataforma lo soporte,
- persistencia local,
- aplicacion inmediata sin reinicio.

Reglas:
- No introducir colores sueltos fuera de tokens o variables del tema.
- Rojo solo para errores o acciones destructivas.
- Amarillo/ambar solo para advertencias o caducidad.
- El modo oscuro debe ser premium y legible, no negro plano.

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

Prioridad alta:
1. Proteger coherencia tipografica con fuentes reales empaquetadas.
2. Completar paridad iOS en busqueda, filtros y skeletons.
3. Integrar stats familiares en perfil/dashboard.
4. Crear perfil Desktop completo.
5. Toggle global para hapticos.

Prioridad media:
1. Onboarding Desktop.
2. Ayuda contextual MVP.
3. Shortcuts completos en modo cocina Desktop.
4. Fechas de caducidad iOS en lenguaje humano.
5. Mejorar algoritmo de iniciales.

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
