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

package stroom.widget.tickbox.client.view;

import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TickList<T extends TickBox> extends Composite implements HasSelectionHandlers<Set<T>> {

    private final Set<T> items = new HashSet<>();
    @UiField
    FlowPanel list;
    // If we are set with selected items before we have populated our list then
    // remember them and reselect them once we have filled the list.
    private Set<T> itemsToSelectOncePopulated = null;

    @Inject
    public TickList(final Binder binder) {
        initWidget(binder.createAndBindUi(this));
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<Set<T>> handler) {
        return addHandler(handler, SelectionEvent.getType());
    }

    public void addItem(final T item) {
        if (!items.contains(item)) {
            item.getElement().getStyle().setDisplay(Display.BLOCK);
            items.add(item);
            list.add(item);
            item.addValueChangeHandler(event -> fireUpdate());
        }
    }

    private void fireUpdate() {
        SelectionEvent.fire(this, getSelectedItems());
    }

    public void addItems(final Collection<T> list) {
        if (list != null) {
            for (final T item : list) {
                addItem(item);
            }
        }
        reselectItems();
    }

    public void addItems(final T[] list) {
        if (list != null) {
            for (final T item : list) {
                addItem(item);
            }
        }
        reselectItems();
    }

    /**
     * Handle the case when the list gets populated after the selected items
     * have been set.
     */
    private void reselectItems() {
        if (itemsToSelectOncePopulated != null) {
            setSelectedItems(itemsToSelectOncePopulated);
            itemsToSelectOncePopulated = null;
        }
    }

    public void clear() {
        items.clear();
        list.clear();
    }

    public Set<T> getSelectedItems() {
        final Set<T> selectedItems = new HashSet<>();
        for (final T tickBox : items) {
            if (tickBox.getBooleanValue()) {
                selectedItems.add(tickBox);
            }
        }
        return selectedItems;
    }

    public void setSelectedItems(final Set<T> selectedItems) {
        if (items.isEmpty()) {
            itemsToSelectOncePopulated = selectedItems;
        } else {
            for (final T tickBox : items) {
                tickBox.setBooleanValue(false);
                for (final T t : selectedItems) {
                    if (tickBox.equals(t)) {
                        tickBox.setBooleanValue(true);
                    }
                }
            }
        }
    }

    public Set<T> getItems() {
        return items;
    }

    public interface Binder extends UiBinder<Widget, TickList<?>> {

    }
}
