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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MultiSelectionModel<T> extends AbstractSelectionModel<T> implements HasSelection<T> {
    private final Set<T> selectedItems = new HashSet<>();
    private final Set<T> changes = new HashSet<>();

    public MultiSelectionModel() {
        super(null);
    }

    /**
     * Get the set of selected items.
     *
     * @return the set of selected items
     */
    public Set<T> getSelectedSet() {
        return Collections.unmodifiableSet(selectedItems);
    }

    @Override
    public boolean isSelected(final T item) {
        return selectedItems.contains(item);
    }

    @Override
    public void setSelected(final T item, final boolean selected) {
        final boolean currentState = isSelected(item);
        if (currentState != selected) {
            changes.add(item);
            if (selected) {
                selectedItems.add(item);
            } else {
                selectedItems.remove(item);
            }
            fireSelectionChangeEvent();
        }
    }

    @Override
    public boolean hasSelectionChanged(final T item) {
        return changes.remove(item);
    }

    public void clear() {
        if (selectedItems.size() > 0) {
            changes.addAll(selectedItems);
            selectedItems.clear();
            fireSelectionChangeEvent();
        }
    }
}
