/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.datagrid.client;

import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.SingleSelectionModel;

/// Turns presses and keys on a [DataGrid]'s rows into a single selection, for a grid whose
/// selection model is GWT's `SingleSelectionModel` rather than Stroom's `MultiSelectionModel`,
/// which [DataGridSelectionEventManager] serves. A press on a row, or Space or Enter on it, selects
/// it, and the selection follows the focus as Up and Down move it, unless Ctrl is held. A press
/// also moves the grid's keyboard row there and focuses it. A right-click leaves the selection
/// alone, so a context menu acts on it, and so does a press on a control in a cell (e.g. a button
/// cell) or on a part of the row marked `data-dg-no-select`.
///
/// Give it to the grid with its selection model: `dataGrid.setSelectionModel(selectionModel,
/// new SingleSelectionEventManager<>(dataGrid, selectionModel))`.
///
/// @param <T> The row type.
public class SingleSelectionEventManager<T>
        implements CellPreviewEvent.Handler<T>, DataGridKeyboard.Selector<T> {

    private final DataGrid<T> dataGrid;
    private final SingleSelectionModel<? super T> selectionModel;

    /// @param dataGrid       The grid whose rows are pressed.
    /// @param selectionModel The selection model to update.
    public SingleSelectionEventManager(final DataGrid<T> dataGrid,
                                       final SingleSelectionModel<? super T> selectionModel) {
        this.dataGrid = dataGrid;
        this.selectionModel = selectionModel;
    }

    @Override
    public void onCellPreview(final CellPreviewEvent<T> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (!BrowserEvents.MOUSEDOWN.equals(nativeEvent.getType())
            || !MouseUtil.isPrimary(nativeEvent)
            || !Element.is(nativeEvent.getEventTarget())) {
            return;
        }
        final Element target = Element.as(nativeEvent.getEventTarget());
        if (DataGridSelectionEventManager.isConsumedByCell(dataGrid, event, target)) {
            return;
        }
        final T item = event.getValue();
        final int index = item == null
                ? -1
                : dataGrid.getVisibleItems().indexOf(item);
        if (index != -1) {
            // Take the focus, so the keyboard can be used next
            dataGrid.setKeyboardSelectedRow(index, true);
        }
        select(item);
    }

    /// The selection follows the focus, unless Ctrl is held (move without choosing).
    @Override
    public void onFocusRow(final T item, final boolean shift, final boolean ctrl) {
        if (!ctrl) {
            select(item);
        }
    }

    /// Space selects the row.
    @Override
    public void onSpace(final T item, final boolean shift, final boolean ctrl) {
        select(item);
    }

    /// Enter selects the row.
    @Override
    public void onEnter(final T item) {
        select(item);
    }

    /// A single selection has no "all" to select.
    @Override
    public void onSelectAll() {
    }

    private void select(final T item) {
        if (item != null) {
            selectionModel.setSelected(item, true);
        }
    }
}
