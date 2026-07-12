package org.gipsybuho.recetasfamiliares.ui;

import javafx.geometry.Bounds;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

final class DesktopScroll {

    private DesktopScroll() {
    }

    static void configurePage(ScrollPane scroll, Region content) {
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.viewportBoundsProperty().addListener((obs, oldBounds, bounds) ->
                syncContentSize(content, bounds));
    }

    private static void syncContentSize(Region content, Bounds bounds) {
        content.setMinHeight(bounds.getHeight());
    }
}
