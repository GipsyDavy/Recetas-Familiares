# MACRO-PROMPT-RECETAS-FAMILIA.md

Plantilla para pedir ayuda a otro agente IA sobre Recetas Familiares.

Uso correcto:
- Copiar solo el bloque necesario.
- Rellenar campos concretos.
- No pedir cambios masivos sin archivos y objetivo claros.
- No afirmar validaciones no ejecutadas.
- Mantener alineacion con `CLAUDE.md`.

---

## BLOQUE PARA OTRO AGENTE

```markdown
Proyecto: Recetas Familiares
Ruta del proyecto: C:\Users\GipsyDavy\MAVEN\Recetas Familiares
Agente solicitado: <Codex | Gemini | Claude | otro>
Agente lider actual: <nombre del agente lider>
Tipo de apoyo requerido: <seguridad | arquitectura | UI/UX | backend | Android | Desktop | iOS/KMP | base de datos | validacion | otro>
Skills/revisiones necesarias: <VibeSec | security-review | accessibility | performance | ninguna | otras disponibles>

Objetivo:
<Explica en 2-5 lineas que se necesita conseguir.>

Archivos implicados:
- <ruta 1>
- <ruta 2>

Contexto minimo:
- Producto: app familiar premium para recetas, stock, menus, lista de compra, notas, fotos, miembros y sincronizacion.
- Plataformas: Backend Spring Boot, Android Compose, Desktop JavaFX, iOS KMP/Compose.
- Reglas de proyecto: seguir `CLAUDE.md`.
- UX visual: seguir `Interfaz.md` si afecta UI.
- Estado operativo: revisar `CONTINUAR.md` si afecta sprint o deuda tecnica.
- Auditoria: revisar `auditoria.md` si afecta IDs `SEC-*`, `COD-*` o `UX-*`.

Lectura previa obligatoria:
- Leer los archivos listados en `Archivos implicados`.
- Leer cualquier contrato, DTO, repositorio, pantalla o migracion relacionado antes de opinar.
- Si falta contexto, pedirlo explicitamente; no inventar estado del proyecto.

Restricciones:
- Cambios quirurgicos.
- No refactorizar arquitectura estable sin necesidad.
- No exponer entidades JPA directamente.
- Mantener ownership familiar y privacidad.
- No hardcodear secretos.
- No inventar resultados de tests.
- Si se toca UI, respetar temas, accesibilidad y estados loading/error/empty.

Que debe hacer:
1. Confirmar que contexto leyo.
2. Analizar impacto tecnico, de seguridad, UX y multiplataforma segun aplique.
3. Proponer o aplicar la solucion mas simple.
4. Indicar archivos cambiados o recomendados.
5. Indicar validaciones necesarias.
6. Senalar riesgos residuales.

Que no debe hacer:
- No cambiar archivos no relacionados.
- No borrar cambios ajenos.
- No introducir dependencias pesadas sin justificar.
- No copiar reglas de otros proyectos.
- No marcar nada como cerrado sin validacion real.

Validacion requerida:
- Backend: tests/build relevantes.
- Android: `assembleDebug` o tests relevantes.
- Desktop: `mvn test` o `mvn -DskipTests compile` segun alcance.
- iOS: explicar limitaciones si no hay macOS/Xcode.
- Seguridad: VibeSec/security-review si aplica segun `CLAUDE.md`.

Formato de respuesta esperado:
- Hallazgos o propuesta.
- Cambios realizados/recomendados.
- Validacion ejecutada.
- Riesgos residuales.
- Siguientes pasos concretos.
```

---

## CONTEXTO RAPIDO DEL PRODUCTO

Recetas Familiares es una aplicacion premium multiplataforma para gestionar recetas, ingredientes, stock, menus, listas de compra, notas, fotos y colaboracion familiar.

Principios clave:
- Privacidad familiar.
- Ownership por familia en backend.
- Offline-first en clientes moviles.
- Sincronizacion incremental con soft delete y `syncVersion`.
- UX calida, premium y accesible.
- Cambios quirurgicos y YAGNI.

---

## ROLES RECOMENDADOS SEGUN TAREA

Seleccionar solo los relevantes:

- Backend/Security: auth, JWT, Spring Security, ownership, CORS, endpoints, storage, uploads.
- Android/KMP: Compose, Room, WorkManager, DataStore, EncryptedSharedPreferences, offline.
- iOS/KMP: Compose Multiplatform, Ktor, SQLDelight, Keychain, Background Tasks.
- Desktop JavaFX: MVVM ligero, Maven, Services/Tasks, CSS, instalador.
- UX/Product: flujos, accesibilidad, estados, microcopy, diseño visual.
- Database: Flyway/Liquibase, MySQL, migraciones, indices.

No usar listas genericas de skills inexistentes. Pedir capacidades concretas vinculadas a la tarea.

---

## CUANDO PEDIR SEGUNDA OPINION

Conviene pedir otro agente cuando:
- hay cambios de seguridad o auth,
- se modifica sincronizacion/offline,
- se tocan contratos API compartidos,
- se diseña una migracion de base de datos,
- hay UI principal o cambio visual transversal,
- existe incertidumbre arquitectonica real.

No hace falta para:
- cambios triviales de texto,
- ajustes documentales menores,
- fixes locales obvios con validacion directa.

---

## INICIO DE SPRINT CON APOYO IA

Antes de iniciar un sprint, preparar internamente:
- objetivo del sprint,
- archivos y modulos afectados,
- documentos que deben leerse,
- agentes IA o skills necesarias,
- validaciones esperadas,
- riesgos previsibles,
- criterio de cierre.

Si se consulta otro agente, usar el bloque anterior. Si no se consulta, dejar constancia breve del motivo: por ejemplo, `No se consulta agente externo porque el cambio es documental y verificable localmente`.
