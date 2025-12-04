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

package stroom.data.grid.client;

import stroom.data.client.event.SelectAllEvent;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.Selection;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.view.client.CellPreviewEvent;

import java.util.List;

public class DataGridSelectionEventManager<T>
        extends AbstractSelectionEventManager<T>
        implements SelectAllEvent.HasSelectAllHandlers {

    private final EventBus eventBus = new SimpleEventBus();
    private final MyDataGrid<T> dataGrid;
    private final MultiSelectionModel<T> selectionModel;
    private final boolean allowMultiSelect;
    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
    // Required for multiple selection using shift and control key modifiers.
    private T multiSelectStart;

    public DataGridSelectionEventManager(final MyDataGrid<T> dataGrid,
                                         final MultiSelectionModel<T> selectionModel,
                                         final boolean allowMultiSelect) {
        super(dataGrid);
        this.dataGrid = dataGrid;
        this.selectionModel = selectionModel;
        this.allowMultiSelect = allowMultiSelect;
    }

    @Override
    protected void onExecute(final CellPreviewEvent<T> e) {
        final int row = dataGrid.getKeyboardSelectedRow();
        final List<T> items = dataGrid.getVisibleItems();
        if (row >= 0 && row < items.size()) {
            final T item = items.get(row);
            selectionModel.setSelected(item);
        }
    }

    @Override
    protected void onSelectAll(final CellPreviewEvent<T> e) {
        SelectAllEvent.fire(this);
    }

    @Override
    protected void onSelect(final CellPreviewEvent<T> event) {
        // Called when user hits <space> so they can do single or multi-select
        // via the keyboard using up/down/j/k, then space or space + shift/ctrl
        NullSafe.consume(event.getValue(), row -> {
            doSelect(row, new SelectionType(
                    false,
                    false,
                    allowMultiSelect,
                    event.getNativeEvent().getCtrlKey(),
                    event.getNativeEvent().getShiftKey()));
        });
    }

    @Override
    protected void onMouseDown(final CellPreviewEvent<T> event) {
        if (MouseUtil.isPrimary(event.getNativeEvent())) {
            // Find out if the cell consumes this event because if it does then we won't use it to select the row.
            boolean consumed = false;

            String parentTag = null;
            final Element target = event.getNativeEvent().getEventTarget().cast();
            if (target.getParentElement() != null) {
                parentTag = target.getParentElement().getTagName();
            }

            // If the user has clicked on a link then consume the event.
            if (target.hasTagName("u")) {
                final String link = target.getAttribute("link");
                if (link != null) {
                    final Hyperlink hyperlink = Hyperlink.create(link);
                    if (hyperlink != null) {
                        consumed = true;
                        // TODO : Don't use the default task listener here.
                        HyperlinkEvent.fire(dataGrid, hyperlink, new DefaultTaskMonitorFactory(this));
                    }
                }
            }

            if (!consumed) {
                // Since all of the controls we care about will not have interactive elements that are
                // direct children of the td we can assume that the cell will not consume the event if
                // the parent of the target is the td.
                if (!"td".equalsIgnoreCase(parentTag)) {
                    final Cell<?> cell = dataGrid.getColumn(event.getColumn()).getCell();
                    if (cell instanceof EventCell) {
                        final EventCell eventCell = (EventCell) cell;
                        consumed = eventCell.isConsumed(event);

                    } else if (cell != null && cell.getConsumedEvents() != null) {
                        if (cell.getConsumedEvents().contains(BrowserEvents.CLICK)
                                || cell.getConsumedEvents().contains(BrowserEvents.MOUSEDOWN)
                                || cell.getConsumedEvents().contains(BrowserEvents.MOUSEUP)) {
                            consumed = true;
                        }
                    }
                }
            }

            if (!consumed) {
                int index = -1;
                if (event.getValue() != null) {
                    final List<T> rows = dataGrid.getVisibleItems();
                    index = rows.indexOf(event.getValue());
                }
                if (index == -1) {
                    index = dataGrid.getKeyboardSelectedRow();
                }
                if (index != -1) {
                    // We set focus here so that we can use the keyboard to navigate once we have focus.
                    dataGrid.setKeyboardSelectedRow(index, true);
                }

                NullSafe.consume(event.getValue(), row -> {
                    final boolean doubleClick = doubleClickTest.test(row);
                    doSelect(row, new SelectionType(
                            doubleClick,
                            false,
                            allowMultiSelect,
                            event.getNativeEvent().getCtrlKey(),
                            event.getNativeEvent().getShiftKey()));
                });
            }
        }
    }

    private void doSelect(final T row, final SelectionType selectionType) {
        final Selection<T> selection = selectionModel.getSelection();

        if (row == null) {
            multiSelectStart = null;
            selection.clear();
        } else if (selectionType.isAllowMultiSelect() && selectionType.isShiftPressed() && multiSelectStart != null) {
            // If control isn't pressed as well as shift then we are selecting a new range so clear.
            if (!selectionType.isControlPressed()) {
                selection.clear();
            }

            final List<T> rows = dataGrid.getVisibleItems();
            final int index1 = rows.indexOf(multiSelectStart);
            final int index2 = rows.indexOf(row);
            if (index1 != -1 && index2 != -1) {
                final int start = Math.min(index1, index2);
                final int end = Math.max(index1, index2);
                for (int i = start; i <= end; i++) {
                    selection.setSelected(rows.get(i), true);
                }
            } else if (selectionType.isControlPressed()) {
                multiSelectStart = row;
                selection.setSelected(row, !selection.isSelected(row));
            } else {
                multiSelectStart = row;
                selection.setSelected(row);
            }
        } else if (selectionType.isAllowMultiSelect() && selectionType.isControlPressed()) {
            multiSelectStart = row;
            selection.setSelected(row, !selection.isSelected(row));
        } else {
            multiSelectStart = row;
            selection.setSelected(row);
        }

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
