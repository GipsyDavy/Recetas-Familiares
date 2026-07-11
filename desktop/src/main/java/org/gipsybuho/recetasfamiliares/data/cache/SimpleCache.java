package org.gipsybuho.recetasfamiliares.data.cache;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

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

    /** Merge server-side sync deltas by id. Must be called on the JavaFX Application Thread. */
    public void mergeById(List<T> changes, Function<T, String> idExtractor, Predicate<T> deletedPredicate) {
        if (changes == null) {
            return;
        }
        LinkedHashMap<String, T> merged = new LinkedHashMap<>();
        for (T item : items) {
            String id = idExtractor.apply(item);
            if (id != null) {
                merged.put(id, item);
            }
        }
        for (T change : changes) {
            String id = idExtractor.apply(change);
            if (id == null) {
                continue;
            }
            if (deletedPredicate.test(change)) {
                merged.remove(id);
            } else {
                merged.put(id, change);
            }
        }
        items.setAll(merged.values());
    }

    /** Add a single item. Must be called on the JavaFX Application Thread. */
    public void add(T item) {
        items.add(item);
    }

    /** Remove a single item. Must be called on the JavaFX Application Thread. */
    public void remove(T item) {
        items.remove(item);
    }

    /** Replace the first item matching {@code matcher}, or add if absent. FX thread only. */
    public void replaceOrAdd(T item, java.util.function.Predicate<T> matcher) {
        for (int i = 0; i < items.size(); i++) {
            if (matcher.test(items.get(i))) {
                items.set(i, item);
                return;
            }
        }
        items.add(item);
    }

    public void clear() {
        items.clear();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
