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

import stroom.data.client.event.SelectAllEvent;
import stroom.data.grid.client.EventCell;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.widget.datagrid.client.model.SelectionRules;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.Selection;
import stroom.widget.util.client.SelectionType;
import stroom.widget.util.client.TypedKey;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// Turns clicks on a [DataGridWidget] into row selections, as `DataGridSelectionEventManager` does
/// for `MyDataGrid` (design requirements R-S2 to R-S7), and is the grid keyboard's selector: the
/// keys are the keyboard's ([DataGridKeyboard]), which tells this what they do to the selection.
///
/// The selection rules themselves live in [SelectionRules] so they can be unit tested. Whether
/// Ctrl and Shift can select several rows is fixed when the manager is created, from the selection
/// model's owner, as it is today.
///
/// @param <T> The row type.
public class DataGridWidgetSelectionEventManager<T>
        implements CellPreviewEvent.Handler<T>, SelectAllEvent.HasSelectAllHandlers,
        DataGridKeyboard.Selector<T> {

    private final EventBus eventBus = new SimpleEventBus();
    private final DataGridWidget<T> dataGrid;
    private final MultiSelectionModel<T> selectionModel;
    private final boolean allowMultiSelect;
    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();

    // The anchor for Shift range selection.
    private T multiSelectStart;
    private Runnable moveUpFromFirst;
    private Consumer<String> typedHandler;

    /// @param dataGrid         The grid.
    /// @param selectionModel   The selection model to update.
    /// @param allowMultiSelect Whether Ctrl and Shift can select several rows.
    public DataGridWidgetSelectionEventManager(final DataGridWidget<T> dataGrid,
                                               final MultiSelectionModel<T> selectionModel,
                                               final boolean allowMultiSelect) {
        this.dataGrid = dataGrid;
        this.selectionModel = selectionModel;
        this.allowMultiSelect = allowMultiSelect;
    }

    /// What Up on the first row does, as `DataGridSelectionEventManager`'s, e.g. move the focus to
    /// the filter box above a picker's list.
    ///
    /// @param moveUpFromFirst What to do, or null for nothing (the first row keeps the focus).
    public void setMoveUpFromFirst(final Runnable moveUpFromFirst) {
        this.moveUpFromFirst = moveUpFromFirst;
    }

    /// Where characters typed on a row go, as `DataGridSelectionEventManager`'s, e.g. into a
    /// picker's filter box, so the user can type to narrow the list without going back to it.
    /// Space is not passed on (it chooses the row), nor are keys typed into a control in a cell.
    ///
    /// @param typedHandler Given each character typed, or null to leave typing to the grid.
    public void setTypedHandler(final Consumer<String> typedHandler) {
        this.typedHandler = typedHandler;
    }

    @Override
    public void onCellPreview(final CellPreviewEvent<T> event) {
        final String type = event.getNativeEvent().getType();
        if (BrowserEvents.MOUSEDOWN.equals(type)) {
            onMouseDown(event);
        } else if (BrowserEvents.KEYDOWN.equals(type)) {
            onKeyDown(event);
        }
    }

    // Enter on a link in a cell (a query result's, which the keys give the focus) follows it, as a
    // press on it does
    private void onKeyDown(final CellPreviewEvent<T> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (typedHandler != null && !isInControl(nativeEvent)) {
            final String key = TypedKey.keyOf(nativeEvent);
            if (TypedKey.isTypedCharacter(nativeEvent, key) && !" ".equals(key)) {
                nativeEvent.preventDefault();
                nativeEvent.stopPropagation();
                typedHandler.accept(key);
                return;
            }
        }
        if (nativeEvent.getKeyCode() == KeyCodes.KEY_ENTER
            && !nativeEvent.getCtrlKey()
            && !nativeEvent.getAltKey()
            && !nativeEvent.getMetaKey()
            && Element.is(nativeEvent.getEventTarget())
            && fireHyperlink(Element.as(nativeEvent.getEventTarget()))) {
            nativeEvent.preventDefault();
            nativeEvent.stopPropagation();
        }
    }

    /// The selection follows the focus: the row is chosen, or, in a grid that chooses several, the
    /// run from the last row chosen to it with Shift, or nothing with Ctrl.
    @Override
    public void onFocusRow(final T item, final boolean shift, final boolean ctrl) {
        if (item == null || (ctrl && allowMultiSelect)) {
            return;
        }
        doSelect(item, new SelectionType(false, false, allowMultiSelect, false, shift));
    }

    /// Space chooses the row, or, in a grid that chooses several, toggles it (or chooses the run
    /// from the last row chosen to it with Shift).
    @Override
    public void onSpace(final T item, final boolean shift, final boolean ctrl) {
        if (item != null) {
            doSelect(item, new SelectionType(false, false, allowMultiSelect, !shift, shift));
        }
    }

    /// Enter chooses only the row.
    @Override
    public void onEnter(final T item) {
        if (item != null) {
            selectionModel.setSelected(item);
        }
    }

    @Override
    public void onSelectAll() {
        SelectAllEvent.fire(this);
    }

    @Override
    public boolean onUpFromFirst() {
        if (moveUpFromFirst == null) {
            return false;
        }
        moveUpFromFirst.run();
        return true;
    }

    // Whether the key is typed into a control in a cell, e.g. a text box being edited
    private static boolean isInControl(final NativeEvent event) {
        if (!Element.is(event.getEventTarget())) {
            return false;
        }
        final Element target = Element.as(event.getEventTarget());
        return target.hasTagName("input")
               || target.hasTagName("textarea")
               || target.hasTagName("select")
               || "true".equals(target.getAttribute("contenteditable"));
    }

    private void onMouseDown(final CellPreviewEvent<T> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (!MouseUtil.isPrimary(nativeEvent)) {
            // Right-click never changes the selection, so the context menu acts on it.
            return;
        }
        final EventTarget eventTarget = nativeEvent.getEventTarget();
        if (!Element.is(eventTarget)) {
            return;
        }
        final Element target = Element.as(eventTarget);

        if (fireHyperlink(target) || isConsumedByCell(event, target)) {
            return;
        }

        int index = -1;
        if (event.getValue() != null) {
            index = dataGrid.getVisibleItems().indexOf(event.getValue());
        }
        if (index == -1) {
            index = dataGrid.getKeyboardSelectedRow();
        }
        if (index != -1) {
            // Take focus so the keyboard can be used next.
            dataGrid.setKeyboardSelectedRow(index, true);
        }

        final T row = event.getValue();
        if (row != null) {
            final boolean doubleClick = doubleClickTest.test(row);
            doSelect(row, new SelectionType(
                    doubleClick,
                    false,
                    allowMultiSelect,
                    DataGridDom.isCtrl(nativeEvent),
                    nativeEvent.getShiftKey()));
        }
    }

    private boolean fireHyperlink(final Element target) {
        if (target.hasTagName("u")) {
            final String link = target.getAttribute("link");
            if (link != null && !link.isEmpty()) {
                final Hyperlink hyperlink = Hyperlink.create(link);
                if (hyperlink != null) {
                    HyperlinkEvent.fire(dataGrid, hyperlink, new DefaultTaskMonitorFactory(this));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isConsumedByCell(final CellPreviewEvent<T> event, final Element target) {
        if (target.hasAttribute("data-dg-no-select")
            || findNoSelectAncestor(target)) {
            return true;
        }

        // Interactive controls are never direct children of the cell's content wrapper, so a press
        // on the wrapper itself, or the cell, is never consumed.
        final Element parent = target.getParentElement();
        if (isCell(target) || (parent != null && isCell(parent))) {
            return false;
        }

        final int column = event.getColumn();
        if (column < 0 || column >= dataGrid.getColumnCount()) {
            return false;
        }
        final Cell<?> cell = dataGrid.getColumn(column).getCell();
        if (cell instanceof final EventCell eventCell) {
            return eventCell.isConsumed(event);
        }
        if (cell != null) {
            final Set<String> consumed = cell.getConsumedEvents();
            return consumed != null
                   && (consumed.contains(BrowserEvents.CLICK)
                       || consumed.contains(BrowserEvents.MOUSEDOWN)
                       || consumed.contains(BrowserEvents.MOUSEUP));
        }
        return false;
    }

    private boolean findNoSelectAncestor(final Element target) {
        Element element = target;
        while (element != null && !isCell(element)) {
            if (element.hasAttribute("data-dg-no-select")) {
                return true;
            }
            element = element.getParentElement();
        }
        return false;
    }

    private static boolean isCell(final Element element) {
        return element.hasClassName("data-grid__cell");
    }

    private void doSelect(final T row, final SelectionType selectionType) {
        final Selection<T> selection = selectionModel.getSelection();
        multiSelectStart = SelectionRules.select(selection,
                dataGrid.getVisibleItems(),
                multiSelectStart,
                row,
                selectionType);
        selectionModel.setSelection(selection, selectionType);
    }



    @Override
    public HandlerRegistration addSelectAllHandler(final SelectAllEvent.Handler handler) {
        return eventBus.addHandler(SelectAllEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
