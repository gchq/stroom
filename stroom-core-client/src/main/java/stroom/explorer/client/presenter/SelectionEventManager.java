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

package stroom.explorer.client.presenter;

import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.Range;

import java.util.function.Consumer;

public class SelectionEventManager<I>
        extends AbstractSelectionEventManager<I> {

    private final MultiSelectionModelImpl<I> selectionModel;
    private final DoubleSelectTester doubleClickTest = new DoubleSelectTester();
    private final boolean allowMultiSelect = false;
    private final Consumer<I> selectionConsumer;
    private final Consumer<I> keyboardSelectionConsumer;

    public SelectionEventManager(final AbstractHasData<I> cellTable,
                                 final MultiSelectionModelImpl<I> selectionModel,
                                 final Consumer<I> selectionConsumer,
                                 final Consumer<I> keyboardSelectionConsumer) {
        super(cellTable);
        this.selectionModel = selectionModel;
        this.selectionConsumer = selectionConsumer;
        this.keyboardSelectionConsumer = keyboardSelectionConsumer;
    }

    @Override
    protected void onMoveRight(final CellPreviewEvent<I> e) {
        nextPage();
    }

    @Override
    protected void onMoveLeft(final CellPreviewEvent<I> e) {
        previousPage();
    }

    @Override
    protected void onExecute(final CellPreviewEvent<I> e) {
        final NativeEvent nativeEvent = e.getNativeEvent();
        final I item = e.getValue();
        doSelect(item,
                new SelectionType(true,
                        false,
                        allowMultiSelect,
                        nativeEvent.getCtrlKey(),
                        nativeEvent.getShiftKey()));
    }

    @Override
    protected void onSelect(final CellPreviewEvent<I> e) {
        final NativeEvent nativeEvent = e.getNativeEvent();
        // Change the selection.
        doSelect(e.getValue(),
                new SelectionType(false,
                        false,
                        allowMultiSelect,
                        nativeEvent.getCtrlKey(),
                        nativeEvent.getShiftKey()));
    }

    @Override
    protected void onMouseDown(final CellPreviewEvent<I> e) {
        final NativeEvent nativeEvent = e.getNativeEvent();
        final I selectedItem = e.getValue();
        if (MouseUtil.isPrimary(nativeEvent)) {
            final boolean doubleClick = doubleClickTest.test(selectedItem);
            if (selectedItem != null) {
                doSelect(selectedItem,
                        new SelectionType(doubleClick,
                                false,
                                allowMultiSelect,
                                nativeEvent.getCtrlKey(),
                                nativeEvent.getShiftKey()));
            }
        }
    }

    void doSelect(final I row, final SelectionType selectionType) {
        if (selectionModel != null) {
            if (!selectionModel.isSelected(row)) {
                selectionModel.setSelected(row);
            }
        }

        if (selectionType.isDoubleSelect()) {
            if (selectionConsumer != null) {
                selectionConsumer.accept(row);
            }
        }

        setKeyboardSelection(row);
    }

    @Override
    protected void onKeyboardSelectRow(final int row, final boolean stealFocus) {
        super.onKeyboardSelectRow(row, stealFocus);
        if (keyboardSelectionConsumer != null && cellTable.getVisibleItems().size() > row) {
            final I item = cellTable.getVisibleItems().get(row);
            keyboardSelectionConsumer.accept(item);
        }
    }

    private void setKeyboardSelection(final I value) {
        final int row = cellTable.getVisibleItems().indexOf(value);
        if (row >= 0) {
            cellTable.setKeyboardSelectedRow(row, true);
        } else {
            cellTable.setKeyboardSelectedRow(cellTable.getKeyboardSelectedRow(), true);
        }
    }

    protected void nextPage() {
        if (this.cellTable != null) {
            final Range range = this.cellTable.getVisibleRange();
            this.setPageStart(range.getStart() + range.getLength());
        }
    }

    protected void previousPage() {
        if (this.cellTable != null) {
            final Range range = this.cellTable.getVisibleRange();
            this.setPageStart(range.getStart() - range.getLength());
        }
    }

    protected void setPageStart(int index) {
        if (this.cellTable != null) {
            final Range range = this.cellTable.getVisibleRange();
            final int pageSize = range.getLength();
            if (this.cellTable.isRowCountExact()) {
                index = Math.min(index, this.cellTable.getRowCount() - pageSize);
            }

            index = Math.max(0, index);
            if (index != range.getStart()) {
                this.cellTable.setVisibleRange(index, pageSize);
            }
        }
    }
}
