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

/*
 * This class is adapted from the class com.google.gwt.view.client.SingleSelectionModel
 * which is licenced under the Apache Licence v2.0.  See NOTICE.md in the root of
 * this repository for details of the GWT licence.
 */

package stroom.widget.util.client;

import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SelectionModel.AbstractSelectionModel;
import com.google.gwt.view.client.SetSelectionModel;

import java.util.HashSet;
import java.util.Set;

/**
 * A simple selection model that allows only one item to be selected a a time.
 */
public class MySingleSelectionModel<T> extends AbstractSelectionModel<T> implements SetSelectionModel<T> {

    private Object curKey;
    private T curSelection;

    // Pending selection change
    private boolean newSelected;
    private T newSelectedItem = null;
    private boolean newSelectedPending;
    private DoubleSelectTester doubleSelectTest;

    /**
     * Constructs a SingleSelectionModel without a key provider.
     */
    public MySingleSelectionModel() {
        super(null);
    }

    /**
     * Constructs a SingleSelectionModel with the given key provider.
     *
     * @param keyProvider an instance of ProvidesKey<T>, or null if the item should act
     *                    as its own key
     */
    public MySingleSelectionModel(final ProvidesKey<T> keyProvider) {
        super(keyProvider);
    }

    @Override
    public void clear() {
        setSelected(getSelectedObject(), false);
    }

    /**
     * Gets the currently-selected item.
     *
     * @return the selected item
     */
    public T getSelectedObject() {
        resolveChanges();
        return curSelection;
    }

    @Override
    public Set<T> getSelectedSet() {
        final Set<T> set = new HashSet<>();
        final T item = getSelectedObject();
        if (item != null) {
            set.add(item);
        }
        return set;
    }

    /**
     * Some items can be unselectable
     */
    protected boolean isSelectable(final T item) {
        return true;
    }

    @Override
    public boolean isSelected(final T item) {
        resolveChanges();
        if (curSelection == null || curKey == null || item == null) {
            return false;
        }
        return curKey.equals(getKey(item));
    }

    @Override
    public void setSelected(final T item, final boolean selected) {
        if (!isSelectable(item)) {
            return;
        }

        boolean ignore = false;
        if (doubleSelectTest != null) {
            if (doubleSelectTest.test(item)) {
                DoubleSelectEvent.fire(this);
                ignore = true;
            }
        }

        if (!ignore) {
//        // If we are deselecting an item that isn't actually selected, ignore
//        // it.
//        if (!selected) {

            if (newSelected == selected) {
                final Object oldKey = newSelectedPending
                        ? getKey(newSelectedItem)
                        : curKey;
                final Object newKey = getKey(item);
                if (equalsOrBothNull(oldKey, newKey)) {
                    return;
                }
            }

            newSelectedItem = item;
            newSelected = selected;
            newSelectedPending = true;
            scheduleSelectionChangeEvent();
        }
    }

    @Override
    protected void fireSelectionChangeEvent() {
        if (isEventScheduled()) {
            setEventCancelled(true);
        }
        resolveChanges();
    }

    private boolean equalsOrBothNull(final Object a, final Object b) {
        return (a == null)
                ? (b == null)
                : a.equals(b);
    }

    private void resolveChanges() {
        if (!newSelectedPending) {
            return;
        }

        final Object key = getKey(newSelectedItem);
        final boolean sameKey = equalsOrBothNull(curKey, key);
        boolean changed = false;
        if (newSelected) {
            changed = !sameKey;
            curSelection = newSelectedItem;
            curKey = key;
        } else if (sameKey) {
            changed = true;
            curSelection = null;
            curKey = null;
        }

        newSelectedItem = null;
        newSelectedPending = false;

        // Fire a selection change event.
        if (changed) {
            SelectionChangeEvent.fire(this);
        }
    }

    public HandlerRegistration addDoubleSelectHandler(final DoubleSelectEvent.Handler handler) {
        if (doubleSelectTest == null) {
            doubleSelectTest = new DoubleSelectTester();
        }

        return doubleSelectTest.addDoubleSelectHandler(handler);
    }


//    @Override
//    public void setSelected(T item, boolean selected) {
//        super.setSelected(item, selected);
//        if (doubleClickTest != null) {
//            if (doubleClickTest.isDoubleClick(selected)) {
//                DoubleClickEvent.fire(eventBus);
//            }
//        }
//    }
//
//    public HandlerRegistration addDoubleClickHandler(final DoubleClickEvent.Handler handler) {
//        if (eventBus == null) {
//            eventBus = new SimpleEventBus();
//            doubleClickTest = new DoubleClickTester();
//        }
//
//        return eventBus.addHandler(DoubleClickEvent.getType(), handler);
//    }
}
