package org.gipsybuho.recetasfamiliares.core;

import java.util.List;
import java.util.Map;

/**
 * Todo el texto de la ayuda, en un solo sitio y sin JavaFX, para poder probarlo.
 *
 * Dos capas: {@link #topic(String)} da la ayuda de la pantalla en la que estas,
 * que es la que resuelve en el momento, y {@link #sections()} el centro de
 * ayuda, que es el respaldo cuando la contextual no basta.
 *
 * Se escribe de tu, con frases cortas y sin jerga: lo lee la familia, no un
 * equipo tecnico. No se documenta lo obvio.
 */
public final class HelpContent {

    /** Un tema contextual: se abre con el boton Ayuda o con F1. */
    public record Topic(String emoji, String title, List<String> tips) {}

    /** Una seccion del centro de ayuda. */
    public record Section(String emoji, String title, List<String> blocks) {}

    private HelpContent() {}

    private static final Topic GENERAL = new Topic("❓", "Ayuda", List.of(
            "Navega con el menú lateral entre recetas, despensa, menú y lista de la compra.",
            "Busca en todo a la vez con Ctrl+F.",
            "Pulsa F1 en cualquier pantalla para ver los consejos de esa vista.",
            "En Ajustes > Acerca de tienes el centro de ayuda con todos los temas."));

    private static final Map<String, Topic> TOPICS = Map.ofEntries(
        Map.entry("dashboard", new Topic("🏠", "Inicio", List.of(
            "Es el resumen de tu familia: recetas recientes, lo que está a punto de caducar y el menú de hoy.",
            "«Sincronizar ahora» trae los últimos cambios de los demás. También se sincroniza solo de vez en cuando.",
            "Los números de arriba (recetas, miembros, despensa) son atajos: haz clic para ir a esa sección.",
            "Busca en todo con Ctrl+F: recetas, notas, ingredientes y artículos de la despensa a la vez."))),

        Map.entry("recipes", new Topic("📖", "Recetas", List.of(
            "Crea una receta con «Nueva receta»: título, raciones, tiempos, ingredientes y pasos.",
            "Los ingredientes y los pasos se añaden de uno en uno; puedes reordenarlos arrastrando.",
            "A cada paso puedes ponerle un temporizador en minutos, y el modo cocina lo usará.",
            "«Cocinar» abre el modo cocina a pantalla completa, pensado para seguirla con las manos ocupadas.",
            "Marca tus favoritas con la estrella para tenerlas a mano.",
            "Puedes exportar una receta a PDF para imprimirla o mandársela a alguien."))),

        Map.entry("stock", new Topic("🧂", "Despensa", List.of(
            "Apunta lo que tienes en casa con su cantidad, su unidad y su fecha de caducidad.",
            "Lo que caduca en los próximos días aparece en Inicio y te avisa al sincronizar.",
            "El umbral mínimo sirve para no quedarte sin lo básico: cuando bajas de ahí, se marca.",
            "Lo que compras desde la lista de la compra puede pasar aquí directamente."))),

        Map.entry("menu", new Topic("📅", "Menú semanal", List.of(
            "Asigna recetas a cada comida del día para dejar la semana planificada.",
            "Desde el menú puedes generar la lista de la compra con lo que falte.",
            "Lo que planifiques lo ve toda la familia en cuanto se sincroniza.",
            "Si cambias de idea, arrastra o vuelve a asignar: no hay que borrar nada primero."))),

        Map.entry("shopping", new Topic("🛒", "Lista de la compra", List.of(
            "Añade cosas a mano o genera la lista desde el menú semanal.",
            "Marca lo que ya has cogido; los demás lo ven al momento y no lo compran dos veces.",
            "Lo comprado se puede pasar a la despensa sin volver a escribirlo.",
            "La lista es de la familia, no tuya: cualquiera puede añadir y marcar."))),

        Map.entry("notes", new Topic("📝", "Notas familiares", List.of(
            "Para lo que no cabe en una receta: trucos, recuerdos, la letra de la abuela.",
            "Las notas se sincronizan entre todos los dispositivos de la familia.",
            "Búscalas con Ctrl+F junto con el resto de contenidos.",
            "No son privadas: las ve toda tu familia. Para algo entre dos personas, usa un chat privado."))),

        Map.entry("chat", new Topic("💬", "Chat familiar", List.of(
            "Conversación de toda la familia, para lo del día a día: qué falta, quién cocina.",
            "Los mensajes llegan al momento; si pierdes la conexión, se ponen al día al volver.",
            "El aviso del menú lateral te dice cuántos mensajes no has leído.",
            "Para hablar con una sola persona, usa los chats privados."))),

        Map.entry("conversations", new Topic("✉", "Chats privados", List.of(
            "Conversaciones de dos, separadas del chat de la familia.",
            "Nadie más de la familia ve lo que escribís aquí.",
            "El punto en la lista marca las conversaciones con mensajes nuevos.",
            "Puedes empezar una conversación desde la ficha de cualquier miembro."))),

        Map.entry("members", new Topic("👨‍👩‍👧", "Miembros", List.of(
            "Aquí ves quién está en tu familia y qué puede hacer cada uno.",
            "Hay tres papeles: propietario, administrador y miembro. El propietario no se puede cambiar.",
            "Para invitar a alguien, el propietario o un administrador crea su cuenta con su correo y una contraseña, y se la entrega en persona.",
            "Un administrador puede cambiar papeles y sacar a alguien de la familia. Un miembro, no.",
            "Todo lo de la familia (recetas, despensa, menús, notas) solo lo ven sus miembros."))),

        Map.entry("settings", new Topic("⚙", "Ajustes", List.of(
            "Apariencia: tema de color, modo claro u oscuro, tipografía, movimiento reducido y sonidos.",
            "Los sonidos tienen tres niveles: silencio, solo los importantes, o todos.",
            "En «Acerca de» ves tu versión y puedes buscar actualizaciones.",
            "Ctrl + coma abre Ajustes desde cualquier pantalla."))),

        Map.entry("profile", new Topic("👤", "Perfil y cuenta", List.of(
            "Cambia tu foto y tu nombre: los ve toda la familia.",
            "Desde aquí verificas tu correo, que es lo que te permite recuperar la contraseña si la olvidas.",
            "Puedes cambiar de familia activa si perteneces a más de una.",
            "«Eliminar cuenta» borra tus datos y no tiene vuelta atrás. Salir de una familia no borra nada tuyo.")))
    );

    private static final List<Section> SECTIONS = List.of(
        new Section("🚀", "Primeros pasos", List.of(
            "Recetas Familiares guarda las recetas de tu familia en un sitio común, y las mantiene iguales en el ordenador y en el móvil.",
            "Todo gira alrededor de una familia: es el grupo con el que compartes recetas, despensa, menús y notas. Puedes pertenecer a más de una.",
            "Para entrar necesitas un correo y una contraseña. Si no tienes cuenta, te la crea el propietario de la familia y te da los datos.",
            "La primera vez verás una guía de bienvenida. Puedes volver a verla cuando quieras desde Ajustes > Acerca de.",
            "El botón «Ayuda» del menú lateral, o la tecla F1, te explica siempre la pantalla en la que estés.")),

        new Section("📖", "Recetas", List.of(
            "Una receta tiene título, descripción, raciones, tiempo de preparación, tiempo de cocción y dificultad. Solo el título es obligatorio.",
            "Los ingredientes se añaden con cantidad y unidad. Los pasos, en orden; puedes reordenarlos arrastrándolos.",
            "A un paso puedes ponerle minutos de temporizador: el modo cocina lo usará automáticamente cuando llegues a él.",
            "Puedes añadir fotos a una receta. Se comprimen antes de subirlas para no gastar datos.",
            "La estrella marca favoritas. Sirve para tenerlas a mano, no las cambia de sitio.",
            "«Exportar a PDF» genera una hoja imprimible con ingredientes y pasos.",
            "Si perteneces a varias familias, puedes copiar una receta de una a otra sin volver a escribirla.")),

        new Section("👨‍🍳", "Modo cocina", List.of(
            "Se abre con «Cocinar» desde una receta. Ocupa toda la pantalla y mantiene el equipo despierto mientras cocinas.",
            "Muestra un paso cada vez, con letra grande para leerlo de lejos.",
            "En el ordenador: flecha derecha para avanzar, flecha izquierda para volver, barra espaciadora para el temporizador y Esc para salir.",
            "En el móvil: desliza a izquierda o derecha para cambiar de paso.",
            "Si el paso tiene temporizador, aparece listo para arrancar. Al terminar suena y vibra, si lo tienes activado.",
            "Los sonidos de cocina están dentro del nivel «solo los importantes»: se cocina sin mirar la pantalla y conviene oírlos.")),

        new Section("🧂", "Despensa", List.of(
            "La despensa es la lista de lo que hay en casa, con cantidad, unidad y caducidad.",
            "Lo que caduca en los próximos tres días aparece en Inicio y te avisa con una notificación al sincronizar.",
            "El umbral mínimo marca cuándo consideras que algo se está acabando. Por debajo de ese número, el artículo se señala.",
            "La despensa es de la familia: si alguien gasta el último litro de leche y lo apunta, lo ves tú también.")),

        new Section("📅", "Menú semanal", List.of(
            "Planifica qué se come cada día asignando recetas a las comidas de la semana.",
            "Desde el menú se genera la lista de la compra: coge los ingredientes de las recetas planificadas.",
            "Reasignar una comida sustituye la anterior; no hace falta borrar primero.",
            "El menú es compartido: lo que planifiques lo ve toda la familia.")),

        new Section("🛒", "Lista de la compra", List.of(
            "Puedes añadir cosas a mano o generarlas desde el menú semanal.",
            "Marcar un artículo como comprado lo tacha para todos, así dos personas no compran lo mismo.",
            "Lo comprado se puede pasar a la despensa directamente, con su cantidad.",
            "La lista no se borra sola: se limpia cuando tú quieras.")),

        new Section("📝", "Notas y chat", List.of(
            "Las notas familiares son para lo que no es una receta: trucos, recuerdos, cantidades de la abuela.",
            "El chat familiar es la conversación de todo el grupo, en tiempo real.",
            "Los chats privados son conversaciones de dos personas. El resto de la familia no las ve.",
            "Los mensajes llegan al instante mientras haya conexión. Si se corta, se ponen al día al volver.",
            "Las notas las ve toda la familia. Si algo es entre dos personas, va en un chat privado.")),

        new Section("👨‍👩‍👧", "Tu familia", List.of(
            "Una familia es el grupo con el que compartes todo. Los datos de una familia no los ve nadie de fuera.",
            "Hay tres papeles. El propietario creó la familia y no se le puede quitar el puesto. El administrador puede invitar, cambiar papeles y sacar a alguien. El miembro usa la aplicación con normalidad.",
            "Para dar de alta a alguien, un administrador crea su cuenta con su correo, su nombre y una contraseña, y se la entrega en persona.",
            "Puedes pertenecer a varias familias y cambiar de una a otra desde Perfil.",
            "Salir de una familia no borra tu cuenta ni tus datos personales, pero pierdes el acceso a lo de esa familia.")),

        new Section("🔐", "Cuenta y privacidad", List.of(
            "Si olvidas la contraseña, pulsa «¿Has olvidado tu contraseña?» y te llega un correo con un código.",
            "El código es largo: cópialo entero del correo y pégalo en la aplicación, en «Ya tengo el código». Caduca a los pocos minutos.",
            "Verificar tu correo es lo que hace posible esa recuperación. Merece la pena hacerlo el primer día.",
            "«Cerrar sesión» te saca de este equipo y borra los datos guardados aquí; tus recetas siguen en el servidor.",
            "«Eliminar cuenta» borra tu usuario y no tiene vuelta atrás. Te la pide confirmando la contraseña.",
            "Tus datos familiares solo viajan entre tus dispositivos y el servidor de tu familia.")),

        new Section("🔄", "Sincronización y sin conexión", List.of(
            "La aplicación guarda una copia local para que puedas consultar recetas y despensa sin conexión.",
            "Al recuperar la conexión, los cambios suben y bajan solos. También puedes forzarlo con «Sincronizar».",
            "Si dos personas cambian lo mismo a la vez, gana el último cambio guardado.",
            "Borrar algo no lo elimina del todo de inmediato: se marca como borrado para que desaparezca también en los demás dispositivos.")),

        new Section("🎨", "Apariencia, sonido y accesibilidad", List.of(
            "Hay dieciséis temas de color y modo claro, oscuro o el del sistema.",
            "Puedes cambiar la tipografía y su tamaño si te cuesta leer la pantalla desde lejos.",
            "«Reducir movimiento» quita las animaciones. Útil si marean o si el equipo va justo.",
            "El sonido tiene tres niveles: silencio, solo los importantes (guardado, error, borrado, avisos, temporizador y pasos de cocina) o todos, que añade la navegación.",
            "En el móvil, los hápticos dan respuesta táctil en las acciones importantes y se pueden desactivar.")),

        new Section("⬆", "Actualizaciones", List.of(
            "Cuando hay una versión nueva, la aplicación te avisa al arrancar con el enlace de descarga. No descarga ni instala nada por su cuenta.",
            "También puedes comprobarlo cuando quieras: en el ordenador, Ajustes > Acerca de > «Buscar actualizaciones»; en el móvil, Perfil > «Buscar actualizaciones».",
            "No hay que desinstalar nada: la versión nueva se instala encima y conserva tus datos y tu sesión.",
            "En Windows, el aviso azul de SmartScreen es normal: el instalador no está firmado. Pulsa «Más información» y luego «Ejecutar de todos modos».",
            "En Android, la primera vez el sistema te pedirá permiso para instalar desde esa fuente.")),

        new Section("🩹", "Problemas frecuentes", List.of(
            "«No se pudo conectar con el servidor»: revisa tu conexión. La aplicación sigue funcionando con lo que tenga guardado; al volver la red, se sincroniza sola.",
            "Te ha sacado a la pantalla de entrar: tu sesión ha caducado. Vuelve a entrar con tu correo y contraseña.",
            "No llega el correo de recuperación: mira la carpeta de correo no deseado y comprueba que escribiste bien tu dirección.",
            "El código de recuperación no funciona: caduca a los pocos minutos y es de un solo uso. Pide otro y cópialo entero.",
            "No ves un cambio que hizo otra persona: pulsa «Sincronizar».",
            "La aplicación dice que estás al día pero sabes que hay versión nueva: el aviso lo da el servidor; espera un momento y vuelve a comprobarlo.",
            "Sale un aviso de Windows al instalar: es SmartScreen porque el instalador no está firmado. Ver la sección de Actualizaciones."))
    );

    /** Ayuda de una pantalla concreta, o null si no la tiene. */
    public static Topic topic(String viewKey) {
        return TOPICS.get(viewKey);
    }

    /** Como {@link #topic(String)} pero nunca devuelve null. */
    public static Topic topicOrGeneral(String viewKey) {
        return TOPICS.getOrDefault(viewKey, GENERAL);
    }

    /** Las secciones del centro de ayuda, en orden de lectura. */
    public static List<Section> sections() {
        return SECTIONS;
    }
}
