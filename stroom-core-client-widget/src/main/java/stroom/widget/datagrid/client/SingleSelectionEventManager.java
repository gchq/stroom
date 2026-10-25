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
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.SelectionModel;

/// Turns presses and keys on a [DataGrid]'s rows into a single selection, for a grid whose
/// selection model is one of GWT's (e.g. a `SingleSelectionModel`) rather than Stroom's
/// `MultiSelectionModel`, which [DataGridSelectionEventManager] serves. A press on a row, or Space
/// or Enter on it, selects it, and the selection follows the focus as Up and Down move it, unless
/// Ctrl is held. A right-click leaves the selection alone, so a context menu acts on it.
///
/// Give it to the grid with its selection model:
/// `dataGrid.setSelectionModel(selectionModel, new SingleSelectionEventManager<>(selectionModel))`.
///
/// @param <T> The row type.
public class SingleSelectionEventManager<T>
        implements CellPreviewEvent.Handler<T>, DataGridKeyboard.Selector<T> {

    private final SelectionModel<? super T> selectionModel;

    /// @param selectionModel The selection model to update.
    public SingleSelectionEventManager(final SelectionModel<? super T> selectionModel) {
        this.selectionModel = selectionModel;
    }

    @Override
    public void onCellPreview(final CellPreviewEvent<T> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (BrowserEvents.MOUSEDOWN.equals(nativeEvent.getType())
            && MouseUtil.isPrimary(nativeEvent)
            && !isNoSelect(nativeEvent.getEventTarget())) {
            select(event.getValue());
        }
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

    // A press in a part of a row marked as not selecting it (data-dg-no-select), e.g. a control
    private static boolean isNoSelect(final EventTarget eventTarget) {
        if (!Element.is(eventTarget)) {
            return true;
        }
        Element element = Element.as(eventTarget);
        while (element != null) {
            if (element.hasAttribute("data-dg-no-select")) {
                return true;
            }
            element = element.getParentElement();
        }
        return false;
    }
}
