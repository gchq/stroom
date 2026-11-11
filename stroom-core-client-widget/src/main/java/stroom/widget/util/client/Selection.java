/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.widget.util.client;

import com.google.gwt.view.client.ProvidesKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// The rows selected in a list, the most recently selected first.
///
/// Rows are compared by their key, from the key provider, so an edited copy of a row, or the row
/// fetched again, is the same row. Without a key provider, or for a row whose key is null, rows
/// are compared with `equals`.
public class Selection<T> {

    private final Deque<T> selectedItems;
    private ProvidesKey<T> keyProvider;

    /// A selection that compares rows with `equals`.
    public Selection() {
        this((ProvidesKey<T>) null);
    }

    /// @param keyProvider Gives each row's key, or `null` to compare rows with `equals`.
    public Selection(final ProvidesKey<T> keyProvider) {
        this.selectedItems = new ArrayDeque<>();
        this.keyProvider = keyProvider;
    }

    /// A copy of a selection, with its key provider.
    ///
    /// @param selection The selection to copy.
    public Selection(final Selection<T> selection) {
        this.selectedItems = new ArrayDeque<>(selection.selectedItems);
        this.keyProvider = selection.keyProvider;
    }

    /// @return The key provider, or `null` if rows are compared with `equals`.
    public ProvidesKey<T> getKeyProvider() {
        return keyProvider;
    }

    // The model gives a selection made without a key its own
    void setKeyProvider(final ProvidesKey<T> keyProvider) {
        this.keyProvider = keyProvider;
    }

    /// @return All the selected rows, the most recently selected first.
    public List<T> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }

    /// @param item A row.
    /// @return Whether the row is selected.
    public boolean isSelected(final T item) {
        return find(item) != null;
    }

    /// Selects or deselects a row. A selected row is stored as the object given, replacing the
    /// object it was selected as, and becomes the most recently selected.
    ///
    /// @param item     A row; `null` does nothing.
    /// @param selected Whether it is selected.
    public void setSelected(final T item, final boolean selected) {
        if (item != null) {
            remove(item);
            if (selected) {
                selectedItems.addFirst(item);
            }
        }
    }

    /// @return The most recently selected row, or `null` if none is.
    public T getSelected() {
        return selectedItems.peekFirst();
    }

    /// Makes a row the only selected row.
    ///
    /// @param item The row, or `null` to select none.
    public void setSelected(final T item) {
        selectedItems.clear();
        if (item != null) {
            selectedItems.add(item);
        }
    }

    /// Deselects every row.
    public void clear() {
        selectedItems.clear();
    }

    /// @return The number of selected rows.
    public int size() {
        return selectedItems.size();
    }

    /// Swaps each selected row for its new object among rows that have just arrived, e.g. fetched
    /// again or an edited copy, keeping the order. A selected row that isn't among them stays
    /// selected as it is, as they may be only some of the page's rows (see [#retainRows]).
    ///
    /// @param rows The rows that have arrived; `null` rows are ignored.
    /// @return Whether any selected row has changed, i.e. was swapped for an object not equal to the
    ///         one it was selected as.
    public boolean followRows(final List<? extends T> rows) {
        if (selectedItems.isEmpty() || rows == null || rows.isEmpty()) {
            return false;
        }
        final Rows<T> arrived = new Rows<>(rows);
        boolean changed = false;
        final List<T> followed = new ArrayList<>(selectedItems.size());
        for (final T item : selectedItems) {
            final T row = arrived.find(item);
            if (row != null && row != item) {
                followed.add(row);
                changed |= !row.equals(item);
            } else {
                followed.add(item);
            }
        }
        selectedItems.clear();
        selectedItems.addAll(followed);
        return changed;
    }

    /// Deselects every row that isn't among the given rows, e.g. the rows of the page a list shows,
    /// so that nothing acts on a row that can't be seen.
    ///
    /// @param rows The rows to keep selected if they are; `null` rows are ignored.
    /// @return Whether any row was deselected.
    public boolean retainRows(final Collection<? extends T> rows) {
        if (selectedItems.isEmpty()) {
            return false;
        }
        final Rows<T> shown = new Rows<>(rows);
        return selectedItems.removeIf(item -> shown.find(item) == null);
    }

    // What makes a row the same row: its key, else the row itself (compared with equals)
    private Object identity(final T item) {
        final Object key = keyProvider != null
                ? keyProvider.getKey(item)
                : null;
        return key != null
                ? new Key(key)
                : item;
    }

    // The object a row is selected as, or null if it isn't selected
    T find(final T item) {
        if (item == null) {
            return null;
        }
        final Object identity = identity(item);
        for (final T selected : selectedItems) {
            if (identity.equals(identity(selected))) {
                return selected;
            }
        }
        return null;
    }

    private void remove(final T item) {
        final T selected = find(item);
        if (selected != null) {
            selectedItems.remove(selected);
        }
    }

    // A row's key, wrapped so that it never equals a row compared with equals (a row whose key is
    // null), even if the key and the row are the same kind of object.
    private record Key(Object value) {

    }

    // Rows to find a selected row among: by key in a map, else with equals alone (a row class may
    // override equals without hashCode)
    private final class Rows<R extends T> {

        private final Map<Object, R> byKey = new HashMap<>();
        private final List<R> unkeyed = new ArrayList<>();

        private Rows(final Collection<? extends R> rows) {
            if (rows != null) {
                for (final R row : rows) {
                    if (row != null) {
                        final Object identity = identity(row);
                        if (identity instanceof Key) {
                            byKey.put(identity, row);
                        } else {
                            unkeyed.add(row);
                        }
                    }
                }
            }
        }

        // The row that is the same row as a selected one, or null
        private R find(final T item) {
            final Object identity = identity(item);
            if (identity instanceof Key) {
                return byKey.get(identity);
            }
            for (final R row : unkeyed) {
                if (row.equals(item)) {
                    return row;
                }
            }
            return null;
        }
    }
}
