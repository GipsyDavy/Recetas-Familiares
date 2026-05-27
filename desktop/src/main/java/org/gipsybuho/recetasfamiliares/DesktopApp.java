package org.gipsybuho.recetasfamiliares;

import javafx.application.Application;
import javafx.stage.Stage;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.ui.MainWindow;

public class DesktopApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        AppContext context = AppContext.getInstance();
        MainWindow window = new MainWindow(primaryStage, context);
        window.show();
    }

    @Override
    public void stop() {
        AppContext.getInstance().shutdown();
    }
}
