package org.gipsybuho.recetasfamiliares.data.cache;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * In-memory ObservableList cache for a single entity type.
 * Backed by JavaFX ObservableList so UI components can bind directly.
 */
public class SimpleCache<T> {

    private final ObservableList<T> items = FXCollections.observableArrayList();

    public ObservableList<T> getItems() {
        return FXCollections.unmodifiableObservableList(items);
    }

    /** Replace the entire cached list. Must be called on the JavaFX Application Thread. */
    public void replaceAll(List<T> newItems) {
        items.setAll(newItems);
    }

    public void clear() {
        items.clear();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
