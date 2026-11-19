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

import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SelectionModel.AbstractSelectionModel;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class MultiSelectionModelImpl<T>
        extends AbstractSelectionModel<T>
        implements MultiSelectionModel<T> {

    private final HandlerManager handlerManager;
    private ProvidesKey<T> keyProvider;
    private Selection<T> selection = new Selection<>();
    // Gives a row's absolute index in the list's page (the grid's), or -1 if it isn't shown
    private ToIntFunction<T> rowIndex = row -> -1;
    // The rows being removed and the absolute index of the first of them shown, so the row that
    // takes its place is selected when the page arrives without them; -1 when nothing is
    private Selection<T> removedRows = new Selection<>();
    private int removedIndex = -1;
    // Picks the row to select when the next page arrives (e.g. one just created), or null
    private Predicate<? super T> pendingMatch;
    // Whether the first row is selected when the page shows none selected (a list whose selected
    // row a pane beside it shows)
    private boolean selectFirst;
    // Whether a selected row arrived changed, so a selection event is owed (fired once, as the
    // selection settles, with any change the settling makes)
    private boolean followChanged;
    // While settling, events are held and fired once at the end
    private boolean holdingEvents;
    private boolean heldChange;

    public MultiSelectionModelImpl() {
        super(null);
        this.handlerManager = new HandlerManager(this);
    }

    /// Compares rows by the keys a key provider gives, so a row fetched again, or an edited copy of
    /// it, is the same row. A grid gives its selection model its own key provider
    /// (`DataGrid.setKeyProvider`).
    ///
    /// @param keyProvider The key provider, or `null` to compare rows with `equals`.
    public void setKeyProvider(final ProvidesKey<T> keyProvider) {
        // A pending choice compares rows by the old key
        cancelPending();
        this.keyProvider = keyProvider;
        final Selection<T> keyed = new Selection<>(keyProvider);
        // Oldest first, so the most recent stays the most recent
        final List<T> items = selection.getSelectedItems();
        for (int i = items.size() - 1; i >= 0; i--) {
            keyed.setSelected(items.get(i), true);
        }
        selection = keyed;
    }

    @Override
    public ProvidesKey<T> getKeyProvider() {
        return keyProvider;
    }

    @Override
    public Object getKey(final T item) {
        return keyProvider == null || item == null
                ? item
                : keyProvider.getKey(item);
    }

    /// Swaps each selected row for its new object among rows that have just arrived (see
    /// [Selection#followRows(List)]). The same rows are selected, so nothing is owed unless a
    /// selected row has changed (its new object isn't equal to the old): then one plain selection
    /// event is owed, as selecting the row again would fire, so what shows the selected row shows
    /// it as it now is. It fires when the selection settles ([#settle]), with any change the
    /// settling makes, or at [#flushFollowed()]; a selection made meanwhile fires its own. A list
    /// whose rows are equal by id owes nothing as it polls. The grid calls it when it is given rows.
    ///
    /// @param rows The rows that have arrived.
    public void followRows(final List<? extends T> rows) {
        if (selection.followRows(rows)) {
            followChanged = true;
        }
    }

    /// Fires the selection event a changed row's arrival owes ([#followRows(List)]), if any, e.g.
    /// when the page can't settle yet.
    public void flushFollowed() {
        if (followChanged) {
            followChanged = false;
            fireSelectionChangeEvent();
            fireChange(new SelectionType());
        }
    }

    /// Deselects every row that isn't among the rows a list shows (see [Selection#retainRows]),
    /// firing one plain selection event if any was, so the buttons and anything showing the
    /// selected row follow. The grid calls it once a page's rows have all arrived.
    ///
    /// @param rows The rows the list shows.
    public void retainRows(final Collection<? extends T> rows) {
        if (selection.retainRows(rows)) {
            fireSelectionChangeEvent();
            fireChange(new SelectionType());
        }
    }

    /// The grid gives the model a way to find where a row is shown, for
    /// [#selectNextAfterRemove(Collection)].
    ///
    /// @param rowIndex Gives a row's absolute index in the page shown, or -1 if it isn't shown.
    public void setRowIndex(final ToIntFunction<T> rowIndex) {
        this.rowIndex = rowIndex;
    }

    @Override
    public void selectNextAfterRemove(final Collection<? extends T> rows) {
        cancelPending();
        if (rows != null) {
            final Selection<T> removed = new Selection<>(keyProvider);
            int first = -1;
            for (final T row : rows) {
                removed.setSelected(row, true);
                final int index = row == null
                        ? -1
                        : rowIndex.applyAsInt(row);
                if (index != -1 && (first == -1 || index < first)) {
                    first = index;
                }
            }
            if (first != -1) {
                removedRows = removed;
                removedIndex = first;
            }
        }
    }

    @Override
    public void selectWhenShown(final Predicate<? super T> match) {
        cancelPending();
        pendingMatch = match;
    }

    /// @return Whether rows are being removed, the row in place of which is selected when the page
    /// arrives without them.
    public boolean isRemovalPending() {
        return removedIndex != -1;
    }

    /// Settles the selection on a page whose rows have all arrived (the grid calls it): a row
    /// waiting to be selected ([#selectWhenShown(Predicate)]) is, if shown, else none is; after
    /// rows are removed ([#selectNextAfterRemove(Collection)]), the row now in the first's place
    /// is, else the last row, unless the page still shows them, when they stay selected; otherwise
    /// rows not shown are deselected, unless hidden rows are kept. One plain selection event fires
    /// if the selection changes.
    ///
    /// @param rows       The page's rows.
    /// @param pageStart  The absolute index of the page's first row.
    /// @param keepHidden Whether rows not shown stay selected (a picker's).
    public void settle(final List<T> rows, final int pageStart, final boolean keepHidden) {
        holdingEvents = true;
        heldChange = false;
        try {
            settleHeld(rows, pageStart, keepHidden);
        } finally {
            holdingEvents = false;
        }
        if (heldChange || followChanged) {
            heldChange = false;
            followChanged = false;
            fireSelectionChangeEvent();
            fireChange(new SelectionType());
        }
    }

    private void settleHeld(final List<T> rows, final int pageStart, final boolean keepHidden) {
        if (pendingMatch != null) {
            final T match = rows.stream()
                    .filter(row -> row != null && pendingMatch.test(row))
                    .findFirst()
                    .orElse(null);
            setSelected(orFirst(match, rows));
        } else if (removedIndex != -1) {
            final Selection<T> removed = removedRows;
            final int index = removedIndex;
            cancelPending();
            if (rows.stream().anyMatch(removed::isSelected)) {
                // Still listed (not removed after all, or listed whatever, e.g. a user whose access
                // is revoked): still selected, as an edited row is
                if (!keepHidden) {
                    retainRows(rows);
                }
            } else {
                setSelected(orFirst(ListSelection.rowAt(rows, Math.max(0, index - pageStart)), rows));
            }
        } else if (!keepHidden) {
            if (selectFirst && !rows.isEmpty() && rows.stream().noneMatch(selection::isSelected)) {
                setSelected(rows.get(0));
            } else {
                retainRows(rows);
            }
        }
    }

    @Override
    public void setSelectFirst(final boolean selectFirst) {
        this.selectFirst = selectFirst;
    }

    // The row to select, else the first row if the list always has one selected
    private T orFirst(final T row, final List<T> rows) {
        return row == null && selectFirst
                ? ListSelection.rowAt(rows, 0)
                : row;
    }

    // Held while settling; otherwise fired, covering any event a changed row's arrival owes
    @Override
    protected void fireSelectionChangeEvent() {
        if (holdingEvents) {
            heldChange = true;
        } else {
            followChanged = false;
            super.fireSelectionChangeEvent();
        }
    }

    // A selection made meanwhile is the one to keep
    private void cancelPending() {
        removedRows = new Selection<>();
        removedIndex = -1;
        pendingMatch = null;
    }

    @Override
    public Selection<T> getSelection() {
        return new Selection<T>(selection);
    }

    @Override
    public void setSelection(final Selection<T> selection, final SelectionType selectionType) {
        cancelPending();
        if (selection.getKeyProvider() != keyProvider) {
            selection.setKeyProvider(keyProvider);
        }
        // Whether an item is selected that wasn't, or isn't that was
        final boolean changed = selection.getSelectedItems().stream()
                                        .anyMatch(selected -> !this.selection.isSelected(selected))
                                || this.selection.getSelectedItems().stream()
                                        .anyMatch(selected -> !selection.isSelected(selected));
        this.selection = selection;

        if (changed) {
            fireSelectionChangeEvent();
            fireChange(selectionType);
        } else if (selectionType.isDoubleSelect() || selectionType.isRightClick()) {
            fireChange(selectionType);
        }
    }

    /**
     * Get a list of all selected items.
     */
    @Override
    public List<T> getSelectedItems() {
        return selection.getSelectedItems();
    }

    @Override
    public void setSelectedItems(final List<T> selectedItems) {
        clear(false);
        if (selectedItems != null) {
            for (final T t : selectedItems) {
                setSelected(t, true, new SelectionType(), false);
            }
        }
    }

    @Override
    public int getSelectedCount() {
        return selection.size();
    }

    /**
     * Tests if the specified item is selected.
     */
    @Override
    public boolean isSelected(final T item) {
        return selection.isSelected(item);
    }

    @Override
    public void setSelected(final T item, final boolean selected) {
        setSelected(item, selected, new SelectionType());
    }

    @Override
    public void setSelected(final T item) {
        setSelected(item, new SelectionType());
    }

    /**
     * Sets the specified item as the only selected item, i.e. clears the current
     * selection and sets a single item selected.
     */
    @Override
    public void setSelected(final T item, final SelectionType selectionType) {
        setSelected(item, selectionType, true);
    }

    @Override
    public void setSelected(final T item, final SelectionType selectionType, final boolean fireEvents) {
        cancelPending();
        if (item == null) {
            clear(fireEvents);

        } else {
            final boolean currentlySelected = isSelected(item);
            if (!currentlySelected || selection.size() != 1) {
                selection.setSelected(item);

                fireSelectionChangeEvent();
                if (fireEvents) {
                    fireChange(selectionType);
                }
            } else {
                // Already the only selected row: keep the object given (e.g. the row fetched again),
                // telling the handlers if it has changed, as selecting a different row would
                final boolean changed = !item.equals(selection.find(item));
                selection.setSelected(item);
                if (changed) {
                    fireSelectionChangeEvent();
                    if (fireEvents) {
                        fireChange(selectionType);
                    }
                } else if (selectionType.isDoubleSelect()) {
                    MultiSelectEvent.fire(handlerManager, selectionType);
                }
            }
        }
    }

    /**
     * Sets the selected state of the specified item.
     */
    @Override
    public void setSelected(final T item, final boolean selected, final SelectionType selectionType) {
        setSelected(item, selected, selectionType, true);
    }

    @Override
    public void setSelected(final T item,
                            final boolean selected,
                            final SelectionType selectionType,
                            final boolean fireEvents) {
        cancelPending();
        if (item != null) {
            final T current = selection.find(item);
            final boolean currentlySelected = current != null;
            selection.setSelected(item, selected);
            // Selected as it was, or a changed object for a row selected already
            if (currentlySelected != selected || (selected && !item.equals(current))) {
                fireSelectionChangeEvent();
                if (fireEvents) {
                    fireChange(selectionType);
                }
            }
        }
    }

    /**
     * Gets the most recently selected item or only selected item if an item is selected, null otherwise.
     */
    @Override
    public T getSelected() {
        return selection.getSelected();
    }

    /**
     * Clears all selected items.
     */
    @Override
    public void clear() {
        clear(true);
    }

    @Override
    public void clear(final boolean fireEvents) {
        cancelPending();
        if (selection.size() > 0) {
            selection.clear();
            fireSelectionChangeEvent();
            if (fireEvents) {
                fireChange(new SelectionType());
            }
        }
    }

    @Override
    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return handlerManager.addHandler(MultiSelectEvent.getType(), handler);
    }

    private void fireChange(final SelectionType selectionType) {
        if (holdingEvents) {
            heldChange = true;
        } else {
            MultiSelectEvent.fire(handlerManager, selectionType);
        }
    }
}
