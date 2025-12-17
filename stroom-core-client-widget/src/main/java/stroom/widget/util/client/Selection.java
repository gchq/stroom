/*
 * Copyright 2016-2025 Crown Copyright
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Selection<T> {

    private final Deque<T> selectedItems;

    public Selection() {
        selectedItems = new ArrayDeque<>();
    }

    public Selection(final Selection<T> selection) {
        this.selectedItems = new ArrayDeque<>(selection.selectedItems);
    }

    /**
     * Get a list of all selected items.
     */
    public List<T> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }

    /**
     * Tests if the specified item is selected.
     */
    public boolean isSelected(final T item) {
        return selectedItems.contains(item);
    }

    /**
     * Sets the selected state of the specified item.
     */
    public void setSelected(final T item, final boolean selected) {
        if (item != null) {
            selectedItems.remove(item);
            if (selected) {
                selectedItems.addFirst(item);
            }
        }
    }

    /**
     * Gets the most recently selected item or only selected item if an item is selected, null otherwise.
     */
    public T getSelected() {
        return selectedItems.peekFirst();
    }

    /**
     * Sets the specified item as the only selected item,
     * i.e. clears the current selection and sets a single item selected.
     */
    public void setSelected(final T item) {
        selectedItems.clear();
        if (item != null) {
            selectedItems.add(item);
        }
    }

    /**
     * Clears all selected items.
     */
    public void clear() {
        selectedItems.clear();
    }

    public int size() {
        return selectedItems.size();
    }
}
