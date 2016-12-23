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

package stroom.explorer.client.presenter;

import com.google.gwt.user.cellview.client.HasSelection;
import com.google.gwt.view.client.SelectionModel.AbstractSelectionModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiSelectionModel<T> extends AbstractSelectionModel<T> implements HasSelection<T> {
    private final Deque<T> selectedItems = new ArrayDeque<>();
    private final Set<T> changes = new HashSet<>();

    public MultiSelectionModel() {
        super(null);
    }

    /**
     * Get the list of selected items.
     *
     * @return the list of selected items
     */
    public List<T> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }

    @Override
    public boolean isSelected(final T item) {
        return selectedItems.contains(item);
    }

    @Override
    public void setSelected(final T item, final boolean selected) {
        if (item != null) {
            final boolean currentlySelected = isSelected(item);
            selectedItems.remove(item);
            if (selected) {
                selectedItems.addFirst(item);
            }
            if (currentlySelected != selected) {
                changes.add(item);
                fireSelectionChangeEvent();
            }
        }
    }

    @Override
    public boolean hasSelectionChanged(final T item) {
        return changes.remove(item);
    }

    public T getSelected() {
        return selectedItems.peekFirst();
    }

    public void setSelected(final T item) {
        if (item == null) {
            clear();

        } else {
            final boolean currentlySelected = isSelected(item);
            if (!currentlySelected || selectedItems.size() != 1) {
                // Mark changes.
                if (currentlySelected) {
                    selectedItems.stream().filter(t -> !t.equals(item)).forEach(changes::add);
                } else {
                    changes.addAll(selectedItems);
                    changes.add(item);
                }

                selectedItems.clear();
                selectedItems.add(item);

                fireSelectionChangeEvent();
            }
        }
    }

    public void clear() {
        if (selectedItems.size() > 0) {
            changes.addAll(selectedItems);
            selectedItems.clear();
            fireSelectionChangeEvent();
        }
    }
}
