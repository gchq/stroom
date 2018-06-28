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

package stroom.item.client;

import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.ListBox;
import stroom.docref.HasDisplayValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ItemListBox<T extends HasDisplayValue> extends Composite implements ItemListBoxDisplay<T> {
    private final ListBox listBox;
    private final List<T> items;
    private final String nonSelectString;
    private List<T> pendingSelection;

    public ItemListBox() {
        this(null, false);
    }

    public ItemListBox(final String nonSelectString) {
        this(nonSelectString, false);
    }

    public ItemListBox(final String nonSelectString, final boolean multiSelect) {
        this.nonSelectString = nonSelectString;
        listBox = new ListBox();
        listBox.setMultipleSelect(multiSelect);
        items = new ArrayList<>();

        if (nonSelectString != null) {
            listBox.addItem(nonSelectString);
        }

        listBox.addChangeHandler(event -> fireUpdate());

        initWidget(listBox);
    }

    private void fireUpdate() {
        SelectionEvent.fire(this, getSelectedItem());
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<T> handler) {
        return addHandler(handler, SelectionEvent.getType());
    }

    @Override
    public void addItem(final T item) {
        if (item != null) {
            if (!items.contains(item)) {
                items.add(item);
                listBox.addItem(item.getDisplayValue());

                if (pendingSelection != null && pendingSelection.contains(item)) {
                    pendingSelection.remove(item);
                    setItemSelected(item, true);
                }
            }
        }
    }

    @Override
    public void addItems(final Collection<T> list) {
        if (list != null) {
            for (final T item : list) {
                addItem(item);
            }
        }
    }

    @Override
    public void addItems(final T[] list) {
        if (list != null) {
            for (final T item : list) {
                addItem(item);
            }
        }
    }

    @Override
    public void removeItem(final T item) {
        if (item != null) {
            items.remove(item);
            for (int i = 0; i < listBox.getItemCount(); i++) {
                if (listBox.getValue(i).equals(item.getDisplayValue())) {
                    listBox.removeItem(i);
                }
            }
        }
    }

    @Override
    public void setName(final String name) {
        listBox.setName(name);
    }

    @Override
    public void clear() {
        items.clear();
        listBox.clear();

        if (nonSelectString != null) {
            listBox.addItem(nonSelectString);
        }
    }

    @Override
    public T getSelectedItem() {
        int index = listBox.getSelectedIndex();
        if (nonSelectString != null) {
            index--;
        }

        if (index != -1) {
            return items.get(index);
        }

        return null;
    }

    @Override
    public void setSelectedItem(final T item) {
        if (items == null || items.size() == 0) {
            if (pendingSelection == null) {
                pendingSelection = new ArrayList<>();
            }
            pendingSelection.add(item);

        } else {
            int index = items.indexOf(item);
            if (nonSelectString != null) {
                index++;
            }

            listBox.setSelectedIndex(index);
        }
    }

    public Set<T> getSelectedItems() {
        final Set<T> set = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            if (nonSelectString != null) {
                index++;
            }

            if (listBox.isItemSelected(index)) {
                set.add(items.get(i));
            }
        }

        return set;
    }

    protected List<T> getItems() {
        return items;
    }

    public void setItemSelected(final T item, final boolean selected) {
        if (items == null || items.size() == 0) {
            if (pendingSelection == null) {
                pendingSelection = new ArrayList<>();
            }
            if (selected) {
                pendingSelection.add(item);
            } else {
                pendingSelection.remove(item);
            }

        } else {
            int index = items.indexOf(item);
            if (nonSelectString != null) {
                index++;
            }

            listBox.setItemSelected(index, selected);
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        listBox.setEnabled(enabled);
    }
}
