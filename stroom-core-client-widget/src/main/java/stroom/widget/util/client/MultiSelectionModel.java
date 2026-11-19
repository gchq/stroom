/*
 * Copyright 2017 Crown Copyright
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

import stroom.util.shared.NullSafe;

import com.google.gwt.event.shared.HandlerRegistration;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public interface MultiSelectionModel<T> {

    Selection<T> getSelection();

    void setSelection(Selection<T> selection, SelectionType selectionType);

    /**
     * Get a list of all selected items.
     */
    List<T> getSelectedItems();

    /**
     * @return True if there are selected items
     */
    default boolean hasSelectedItems() {
        return NullSafe.hasItems(getSelectedItems());
    }

    /**
     * Set a list of all selected items.
     */
    void setSelectedItems(List<T> selectedItems);

    /**
     * Gets the number of selected items
     */
    int getSelectedCount();

    /**
     * Tests if the specified item is selected.
     */
    boolean isSelected(final T item);

    /**
     * Gets the most recently selected item or only selected item if an item is selected, null otherwise.
     */
    T getSelected();

    /**
     * Sets the specified item as the only selected item, i.e. clears the current selection and sets a
     * single item selected.
     */
    void setSelected(T item);

    /**
     * Sets the specified item as the only selected item, i.e. clears the current selection and sets a
     * single item selected.
     */
    void setSelected(T item, SelectionType selectionType);

    void setSelected(T item, SelectionType selectionType, boolean fireEvents);

    /**
     * Sets the selected state of the specified item.
     */
    void setSelected(T item, boolean selected);

    /**
     * Sets the selected state of the specified item.
     */
    void setSelected(T item, boolean selected, SelectionType selectionType);

    /**
     * Sets the selected state of the specified item.
     */
    void setSelected(T item, boolean selected, SelectionType selectionType, boolean fireEvents);

    /**
     * Clears all selected items.
     */
    void clear();

    /**
     * Clears all selected items.
     */
    void clear(boolean fireEvents);

    /// Selects the row that takes the place of rows being removed, once the list shows them
    /// gone: the row now at the first of them, else the last row (Stroom's pager moves a page they
    /// shortened back, to show a full page); nothing if none is left (UI-GUIDE.md, "What a list
    /// selects"). If the page still shows them (not removed after all, or listed whatever), they
    /// stay selected. Call it once they are removed, then fetch the list again, once all are.
    /// Selecting a row meanwhile cancels it.
    ///
    /// @param rows The rows being removed; those not shown are ignored.
    void selectNextAfterRemove(Collection<? extends T> rows);

    /// Selects the first row that matches when the list's page next arrives, e.g. a row just
    /// created whose id alone is known, and selects nothing if none does, as a row not shown can't
    /// be acted on (UI-GUIDE.md, "What a list selects"). Selecting a row meanwhile cancels it.
    ///
    /// @param match Whether a row is the one to select.
    void selectWhenShown(Predicate<? super T> match);

    /// Whether the first row is selected whenever the list's page arrives with none of its rows
    /// selected, e.g. for a list whose selected row a pane beside it shows (UI-GUIDE.md, "What a
    /// list selects"); not for a grid that keeps hidden rows selected (a picker's).
    ///
    /// @param selectFirst Whether it is.
    void setSelectFirst(boolean selectFirst);

    /**
     * Add a handler to listen to selection actions on the multi selection model.
     */
    HandlerRegistration addSelectionHandler(MultiSelectEvent.Handler handler);
}
