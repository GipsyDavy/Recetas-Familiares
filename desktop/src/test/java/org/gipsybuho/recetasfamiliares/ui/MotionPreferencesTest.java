package org.gipsybuho.recetasfamiliares.ui;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotionPreferencesTest {

    @Test
    void persistsReducedMotionWithoutUsingTheApplicationNode() throws Exception {
        Preferences testNode = Preferences.userRoot().node(
                "recetas-motion-test-" + UUID.randomUUID());
        Preferences parent = testNode.parent();
        try {
            MotionPreferences preferences = new MotionPreferences(testNode);

            assertFalse(preferences.read());
            preferences.write(true);
            testNode.flush();

            assertTrue(new MotionPreferences(testNode).read());
            preferences.write(false);
            assertFalse(preferences.read());
        } finally {
            testNode.removeNode();
            parent.flush();
        }
    }
}
