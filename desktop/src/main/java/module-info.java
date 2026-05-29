module org.gipsybuho.recetasfamiliares {
    requires javafx.controls;
    requires javafx.fxml;
    requires okhttp3;
    requires com.google.gson;
    requires org.apache.pdfbox;
    requires java.prefs;
    requires java.desktop;

    exports org.gipsybuho.recetasfamiliares;
    exports org.gipsybuho.recetasfamiliares.ui;
    exports org.gipsybuho.recetasfamiliares.core;
    exports org.gipsybuho.recetasfamiliares.api;
    exports org.gipsybuho.recetasfamiliares.api.dto;
    exports org.gipsybuho.recetasfamiliares.data.cache;
    exports org.gipsybuho.recetasfamiliares.data.repository;
}
