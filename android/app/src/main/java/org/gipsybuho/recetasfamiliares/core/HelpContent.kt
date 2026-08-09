package org.gipsybuho.recetasfamiliares.core

/**
 * Todo el texto de la ayuda de Android, en un solo sitio y sin Compose, para
 * poder probarlo.
 *
 * Espejo del de Desktop en contenido, no en literalidad: aqui no hay menu
 * lateral, ni F1, ni Ctrl+F, y los gestos sustituyen a los atajos de teclado.
 *
 * Se escribe de tu, con frases cortas y sin jerga: lo lee la familia.
 */
object HelpContent {

    /** Un tema contextual: se abre con el icono de ayuda de la pantalla. */
    data class Topic(val emoji: String, val title: String, val tips: List<String>)

    /** Una seccion del centro de ayuda. */
    data class Section(val emoji: String, val title: String, val blocks: List<String>)

    private val GENERAL = Topic("❓", "Ayuda", listOf(
        "Cambia de sección con las pestañas de abajo.",
        "El icono de ayuda de arriba te explica siempre la pantalla en la que estés.",
        "En Perfil tienes el centro de ayuda con todos los temas."
    ))

    private val TOPICS = mapOf(
        "recipes" to Topic("📖", "Recetas", listOf(
            "Crea una receta con el botón de añadir: título, raciones, tiempos, ingredientes y pasos.",
            "A cada paso puedes ponerle minutos de temporizador, y el modo cocina los usará.",
            "«Cocinar» abre el modo cocina a pantalla completa, para seguir la receta sin tocar el móvil más de lo necesario.",
            "Marca tus favoritas con la estrella para tenerlas a mano.",
            "Desliza hacia abajo en la lista para sincronizar y traer lo que hayan cambiado los demás."
        )),

        "stock" to Topic("🧂", "Despensa", listOf(
            "Apunta lo que tienes en casa con su cantidad, su unidad y su fecha de caducidad.",
            "Lo que está a punto de caducar se señala para que lo gastes a tiempo.",
            "El umbral mínimo sirve para no quedarte sin lo básico: cuando bajas de ahí, se marca.",
            "Lo que compras desde la lista de la compra puede pasar aquí sin volver a escribirlo."
        )),

        "shopping" to Topic("🛒", "Lista de la compra", listOf(
            "Añade cosas a mano o genera la lista desde el menú semanal.",
            "Marca lo que ya has cogido: los demás lo ven al momento y no lo compran dos veces.",
            "Lo comprado se puede pasar a la despensa directamente.",
            "La lista es de la familia: cualquiera puede añadir y marcar."
        )),

        "notes" to Topic("📝", "Notas familiares", listOf(
            "Para lo que no cabe en una receta: trucos, recuerdos, la letra de la abuela.",
            "Las notas se sincronizan con el ordenador y con los demás móviles.",
            "No son privadas: las ve toda tu familia. Para algo entre dos personas, usa un chat privado."
        )),

        "menu" to Topic("📅", "Menú semanal", listOf(
            "Asigna recetas a cada comida del día para dejar la semana planificada.",
            "Desde el menú puedes generar la lista de la compra con lo que falte.",
            "Lo que planifiques lo ve toda la familia en cuanto se sincroniza."
        )),

        "chat" to Topic("💬", "Chat familiar", listOf(
            "Conversación de toda la familia, para el día a día: qué falta, quién cocina.",
            "Los mensajes llegan al momento; si pierdes la cobertura, se ponen al día al volver.",
            "El punto de la pestaña te dice que hay mensajes sin leer.",
            "Para hablar con una sola persona, usa los chats privados."
        )),

        "conversations" to Topic("✉", "Chats privados", listOf(
            "Conversaciones de dos, separadas del chat de la familia.",
            "Nadie más de la familia ve lo que escribís aquí.",
            "Puedes empezar una conversación desde la ficha de cualquier miembro."
        )),

        "profile" to Topic("👤", "Perfil", listOf(
            "Cambia tu foto y tu nombre: los ve toda la familia.",
            "Verifica tu correo: es lo que te permite recuperar la contraseña si la olvidas.",
            "Aquí ves tu versión instalada y puedes buscar actualizaciones.",
            "Si perteneces a varias familias, cambia de una a otra desde aquí.",
            "«Eliminar cuenta» borra tus datos y no tiene vuelta atrás. Salir de una familia no borra nada tuyo."
        ))
    )

    private val SECTIONS = listOf(
        Section("🚀", "Primeros pasos", listOf(
            "Recetas Familiares guarda las recetas de tu familia en un sitio común, y las mantiene iguales en el móvil y en el ordenador.",
            "Todo gira alrededor de una familia: es el grupo con el que compartes recetas, despensa, menús y notas. Puedes pertenecer a más de una.",
            "Para entrar necesitas un correo y una contraseña. Si no tienes cuenta, te la crea el propietario de la familia y te da los datos.",
            "El icono de ayuda de la barra de arriba te explica siempre la pantalla en la que estés."
        )),

        Section("📖", "Recetas", listOf(
            "Una receta tiene título, descripción, raciones, tiempos y dificultad. Solo el título es obligatorio.",
            "Los ingredientes se añaden con cantidad y unidad; los pasos, en orden.",
            "A un paso puedes ponerle minutos de temporizador: el modo cocina lo usará cuando llegues a él.",
            "Puedes añadir fotos. Se comprimen antes de subirlas para no gastar datos.",
            "La estrella marca favoritas. Sirve para tenerlas a mano, no las cambia de sitio."
        )),

        Section("👨‍🍳", "Modo cocina", listOf(
            "Se abre con «Cocinar» desde una receta. Ocupa toda la pantalla y la mantiene encendida mientras cocinas.",
            "Muestra un paso cada vez, con letra grande para leerlo de lejos.",
            "Desliza a izquierda o derecha para cambiar de paso.",
            "Si el paso tiene temporizador, aparece listo para arrancar. Al terminar vibra y suena, si lo tienes activado.",
            "Los sonidos de cocina están dentro del nivel «solo los importantes»: se cocina sin mirar la pantalla y conviene oírlos."
        )),

        Section("🧂", "Despensa", listOf(
            "La despensa es la lista de lo que hay en casa, con cantidad, unidad y caducidad.",
            "Lo que está a punto de caducar se señala para que lo gastes a tiempo.",
            "El umbral mínimo marca cuándo consideras que algo se está acabando.",
            "La despensa es de la familia: si alguien gasta lo último y lo apunta, lo ves tú también."
        )),

        Section("📅", "Menú semanal", listOf(
            "Planifica qué se come cada día asignando recetas a las comidas de la semana.",
            "Desde el menú se genera la lista de la compra con los ingredientes de lo planificado.",
            "Reasignar una comida sustituye la anterior; no hace falta borrar primero.",
            "El menú es compartido: lo que planifiques lo ve toda la familia."
        )),

        Section("🛒", "Lista de la compra", listOf(
            "Puedes añadir cosas a mano o generarlas desde el menú semanal.",
            "Marcar un artículo como comprado lo tacha para todos, así dos personas no compran lo mismo.",
            "Lo comprado se puede pasar a la despensa directamente, con su cantidad.",
            "La lista no se borra sola: se limpia cuando tú quieras."
        )),

        Section("📝", "Notas y chat", listOf(
            "Las notas familiares son para lo que no es una receta: trucos, recuerdos, cantidades de la abuela.",
            "El chat familiar es la conversación de todo el grupo, en tiempo real.",
            "Los chats privados son de dos personas. El resto de la familia no los ve.",
            "Si se corta la cobertura, los mensajes se ponen al día al volver.",
            "Las notas las ve toda la familia. Si algo es entre dos personas, va en un chat privado."
        )),

        Section("👨‍👩‍👧", "Tu familia", listOf(
            "Una familia es el grupo con el que compartes todo. Sus datos no los ve nadie de fuera.",
            "Hay tres papeles. El propietario creó la familia y no se le puede quitar el puesto. El administrador puede invitar, cambiar papeles y sacar a alguien. El miembro usa la aplicación con normalidad.",
            "Para dar de alta a alguien, un administrador crea su cuenta con su correo, su nombre y una contraseña, y se la entrega en persona.",
            "Puedes pertenecer a varias familias y cambiar de una a otra desde Perfil.",
            "Salir de una familia no borra tu cuenta, pero pierdes el acceso a lo de esa familia."
        )),

        Section("🔐", "Cuenta y privacidad", listOf(
            "Si olvidas la contraseña, pulsa «¿Has olvidado tu contraseña?» y te llega un correo con un código.",
            "El código es largo: cópialo entero del correo y pégalo en «Ya tengo el código». Caduca a los pocos minutos.",
            "Verificar tu correo es lo que hace posible esa recuperación. Merece la pena hacerlo el primer día.",
            "«Cerrar sesión» te saca de este móvil y borra los datos guardados aquí; tus recetas siguen en el servidor.",
            "«Eliminar cuenta» borra tu usuario y no tiene vuelta atrás. Te la pide confirmando la contraseña."
        )),

        Section("🔄", "Sincronización y sin conexión", listOf(
            "La aplicación guarda una copia en el móvil para que puedas consultar recetas y despensa sin cobertura.",
            "Al recuperar la conexión, los cambios suben y bajan solos. También puedes forzarlo deslizando hacia abajo.",
            "Si dos personas cambian lo mismo a la vez, gana el último cambio guardado.",
            "Borrar algo no lo elimina del todo de inmediato: se marca como borrado para que desaparezca también en los demás dispositivos."
        )),

        Section("🎨", "Apariencia, sonido y accesibilidad", listOf(
            "Desde el icono de la paleta eliges tema de color y modo claro, oscuro o el del sistema.",
            "Los hápticos dan respuesta táctil en las acciones importantes y se pueden desactivar.",
            "El sonido tiene tres niveles: silencio, solo los importantes (guardado, error, borrado, avisos, temporizador y pasos de cocina) o todos, que añade la navegación.",
            "La aplicación respeta el tamaño de letra del sistema."
        )),

        Section("⬆", "Actualizaciones", listOf(
            "Cuando hay una versión nueva, la aplicación te avisa al arrancar con el enlace de descarga. No descarga ni instala nada por su cuenta.",
            "También puedes comprobarlo cuando quieras desde Perfil, en «Buscar actualizaciones».",
            "No hay que desinstalar nada: la versión nueva se instala encima y conserva tus datos y tu sesión.",
            "La primera vez, Android te pedirá permiso para instalar desde esa fuente. Es normal: la aplicación no está en Google Play."
        )),

        Section("🩹", "Problemas frecuentes", listOf(
            "«No se pudo conectar con el servidor»: revisa tu conexión. La aplicación sigue funcionando con lo que tenga guardado y se sincroniza al volver la red.",
            "Te ha sacado a la pantalla de entrar: tu sesión ha caducado. Vuelve a entrar con tu correo y contraseña.",
            "No llega el correo de recuperación: mira la carpeta de correo no deseado y comprueba que escribiste bien tu dirección.",
            "El código de recuperación no funciona: caduca a los pocos minutos y es de un solo uso. Pide otro y cópialo entero.",
            "No ves un cambio que hizo otra persona: desliza hacia abajo para sincronizar.",
            "No se instala la versión nueva: comprueba que no estás intentando instalar una más antigua que la que tienes."
        ))
    )

    /** Ayuda de una pantalla concreta, o null si no la tiene. */
    fun topic(screenKey: String): Topic? = TOPICS[screenKey]

    /** Como [topic] pero nunca devuelve null. */
    fun topicOrGeneral(screenKey: String): Topic = TOPICS[screenKey] ?: GENERAL

    /** Las secciones del centro de ayuda, en orden de lectura. */
    fun sections(): List<Section> = SECTIONS
}
