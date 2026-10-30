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
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.LinkValue;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.widget.datagrid.client.model.SelectionRules;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.KeyBinding;
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
import com.google.gwt.view.client.Range;

import java.util.Set;
import java.util.function.Consumer;

/// Turns clicks on a [DataGrid] into row selections, as `DataGridSelectionEventManager` does
/// for `MyDataGrid` (design requirements R-S2 to R-S7), and is the grid keyboard's selector: the
/// keys are the keyboard's ([DataGridKeyboard]), which tells this what they do to the selection.
///
/// The selection rules themselves live in [SelectionRules] so they can be unit tested. Whether
/// Ctrl and Shift can select several rows is fixed when the manager is created, from the selection
/// model's owner, as it is today.
///
/// @param <T> The row type.
public class DataGridSelectionEventManager<T>
        implements CellPreviewEvent.Handler<T>, SelectAllEvent.HasSelectAllHandlers,
        DataGridKeyboard.Selector<T> {

    private final EventBus eventBus = new SimpleEventBus();
    private final DataGrid<T> dataGrid;
    private final MultiSelectionModel<T> selectionModel;
    private final boolean allowMultiSelect;
    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();

    // The anchor for Shift range selection.
    private T multiSelectStart;
    private Runnable moveUpFromFirst;
    private Consumer<String> typedHandler;
    // What chooses a row pressed, or Enter or Space on it, instead of selecting it; null to select
    private Consumer<T> chooser;
    // Whether Left and Right on a list's row show the page before or after
    private boolean leftRightPage;

    /// @param dataGrid         The grid.
    /// @param selectionModel   The selection model to update.
    /// @param allowMultiSelect Whether Ctrl and Shift can select several rows.
    public DataGridSelectionEventManager(final DataGrid<T> dataGrid,
                                               final MultiSelectionModel<T> selectionModel,
                                               final boolean allowMultiSelect) {
        this.dataGrid = dataGrid;
        this.selectionModel = selectionModel;
        this.allowMultiSelect = allowMultiSelect;
    }

    /// @return Whether Ctrl and Shift can select several rows.
    public boolean isAllowMultiSelect() {
        return allowMultiSelect;
    }

    /// Makes the rows choices that the host acts on, as a chooser's list's are (e.g. an annotation's
    /// status): moving the focus chooses nothing, and a press on a row, or Enter or Space on it,
    /// gives the row to the chooser instead of selecting it, even if it is already selected (the
    /// chooser selects it, if it shows what was chosen).
    ///
    /// @param chooser Given each row chosen, or null for rows that are selected as the focus moves.
    public void setChooser(final Consumer<T> chooser) {
        this.chooser = chooser;
    }

    /// Makes Left and Right on a list's row show the page before or after, as the find dialogs'
    /// results do.
    ///
    /// @param leftRightPage Whether Left and Right change page.
    public void setLeftRightPage(final boolean leftRightPage) {
        this.leftRightPage = leftRightPage;
    }

    @Override
    public void onLeft() {
        if (leftRightPage) {
            showPage(-1);
        }
    }

    @Override
    public void onRight() {
        if (leftRightPage) {
            showPage(1);
        }
    }

    // Shows the page before (-1) or after (1) the one shown, if there is one
    private void showPage(final int direction) {
        final Range range = dataGrid.getVisibleRange();
        int start = range.getStart() + direction * range.getLength();
        if (dataGrid.isRowCountExact()) {
            start = Math.min(start, dataGrid.getRowCount() - range.getLength());
        }
        start = Math.max(0, start);
        if (start != range.getStart()) {
            dataGrid.setPageStart(start);
        }
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
        // A character typed on a row goes to the typed handler (e.g. a picker's filter box), or, in a
        // list, to the next row starting with what is typed
        final boolean list = !dataGrid.getProfile().hasCells();
        // A key that runs a command (e.g. '?', the keyboard shortcuts) is left for it
        if ((typedHandler != null || list)
            && !isInControl(nativeEvent)
            && !KeyBinding.isCommandShortcut(nativeEvent)) {
            final String key = TypedKey.keyOf(nativeEvent);
            if (TypedKey.isTypedCharacter(nativeEvent, key) && !" ".equals(key)) {
                nativeEvent.preventDefault();
                nativeEvent.stopPropagation();
                if (typedHandler != null) {
                    typedHandler.accept(key);
                } else {
                    dataGrid.typeAhead(key);
                }
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
        if (item == null || chooser != null || (ctrl && allowMultiSelect)) {
            return;
        }
        doSelect(item, new SelectionType(false, false, allowMultiSelect, false, shift));
    }

    /// Space chooses the row, or, in a grid that chooses several, toggles it (or chooses the run
    /// from the last row chosen to it with Shift).
    @Override
    public void onSpace(final T item, final boolean shift, final boolean ctrl) {
        if (item != null && chooser != null) {
            chooser.accept(item);
        } else if (item != null) {
            doSelect(item, new SelectionType(false, false, allowMultiSelect, !shift, shift));
        }
    }

    /// Enter chooses only the row. In a list it also opens it, as a double click does (a
    /// `MultiSelectEvent` whose selection type is a double select), e.g. confirming a dialog's choice.
    @Override
    public void onEnter(final T item) {
        if (item != null && chooser != null) {
            chooser.accept(item);
        } else if (item != null && !dataGrid.getProfile().hasCells()) {
            selectionModel.setSelected(item, new SelectionType(true, false, allowMultiSelect, false, false));
        } else if (item != null) {
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

        if (fireHyperlink(target) || isConsumedByCell(dataGrid, event, target)) {
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
        if (row != null && chooser != null) {
            chooser.accept(row);
        } else if (row != null) {
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
                final LinkValue hyperlink = LinkValue.create(link);
                if (hyperlink != null) {
                    HyperlinkEvent.fire(dataGrid, hyperlink, new DefaultTaskMonitorFactory(this));
                    return true;
                }
            }
        }
        return false;
    }

    /// Whether a press on `target` is the cell's own (a control in it, e.g. a button cell), or in
    /// a part of the row marked as not selecting it (`data-dg-no-select`), so it doesn't select
    /// the row.
    ///
    /// @param dataGrid The grid pressed on.
    /// @param event    The press.
    /// @param target   The element pressed.
    /// @return Whether the press doesn't select the row.
    static boolean isConsumedByCell(final DataGrid<?> dataGrid,
                                    final CellPreviewEvent<?> event,
                                    final Element target) {
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

    private static boolean findNoSelectAncestor(final Element target) {
        Element element = target;
        while (element != null && !isCell(element)) {
            if (element.hasAttribute("data-dg-no-select")) {
                return true;
            }
            element = element.getParentElement();
        }
        return false;
    }

    // ElementUtil's, as the element may be an SVG one (e.g. a cell's copy icon), whose className
    // isn't a string, so Element.hasClassName threw and a press on the icon did nothing
    private static boolean isCell(final Element element) {
        return ElementUtil.hasClassName(element, "data-grid__cell");
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
