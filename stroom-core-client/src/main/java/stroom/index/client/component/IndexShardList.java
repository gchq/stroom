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

package stroom.index.client.component;

import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.Selection;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.OrderByColumn;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.popup.client.PopupPosition;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Function;

/// An index's shards, a row each (its info, node, partition, path, status, document count, file size,
/// bytes per document, last commit and index version), each with a tick box to choose it; the
/// header's tick box chooses all or none. Flush and Delete act on the shards chosen.
public class IndexShardList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton flushButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    DataGrid<IndexShard> dataGrid;

    private final String flushTitle;
    private final String deleteTitle;
    private Selection<Long> selection = Selection.selectNone();
    private Runnable onFlush = () -> {
    };
    private Runnable onDelete = () -> {
    };
    private Runnable onSelectionChange = () -> {
    };
    private Runnable onSortChange = () -> {
    };
    private InfoHandler onShowInfo = (row, popupPosition, anchor) -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public IndexShardList() {
        initWidget(BINDER.createAndBindUi(this));
        flushTitle = flushButton.getTitle();
        deleteTitle = deleteButton.getTitle();
        // Rows keyed by id, so the keyboard row survives a refresh
        dataGrid.setKeyProvider(IndexShard::getId);
        flushButton.setOnClick(() -> onFlush.run());
        deleteButton.setOnClick(() -> onDelete.run());
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns, once.
    ///
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch.
    public void addColumns(final Function<Long, String> dateTimeFormat) {
        addSelectedColumn();
        addInfoColumn();
        addNodeColumn();
        addPartitionColumn();
        addPathColumn();
        addStatusColumn();
        addDocCountColumn();
        addFileSizeColumn();
        addBytesPDColumn();
        addCommitColumn(dateTimeFormat);
        addVersionColumn();
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<IndexShard> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @param indexName The index's name, which names the table.
    public void setIndexName(final String indexName) {
        dataGrid.setTableName("Index '" + indexName + "' Shards");
    }

    /// @param selection The shards chosen, which the tick boxes show and change.
    public void setSelection(final Selection<Long> selection) {
        this.selection = selection;
        dataGrid.redraw();
    }

    /// @param visible Whether Flush and Delete show (for those who may manage index shards).
    public void setShardActionsVisible(final boolean visible) {
        pager.setToolbarVisible(visible);
    }

    /// @param enabled        Whether Flush may be pressed.
    /// @param disabledReason Why not, added to its title, or `null`.
    public void setFlushEnabled(final boolean enabled, final String disabledReason) {
        flushButton.setEnabled(enabled);
        flushButton.setTitle(withReason(flushTitle, disabledReason));
    }

    /// @param enabled        Whether Delete may be pressed.
    /// @param disabledReason Why not, added to its title, or `null`.
    public void setDeleteEnabled(final boolean enabled, final String disabledReason) {
        deleteButton.setEnabled(enabled);
        deleteButton.setTitle(withReason(deleteTitle, disabledReason));
    }

    /// @param onFlush Run when Flush is pressed.
    public void setOnFlush(final Runnable onFlush) {
        this.onFlush = onFlush;
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = onDelete;
    }

    /// @param onSelectionChange Run when the user ticks or unticks a shard, or all of them.
    public void setOnSelectionChange(final Runnable onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    /// @param onShowInfo Shows a shard's info, when its info icon is pressed.
    public void setOnShowInfo(final InfoHandler onShowInfo) {
        this.onShowInfo = onShowInfo;
    }

    // The name stays, with why the button is disabled
    private static String withReason(final String title, final String reason) {
        return reason == null
                ? title
                : title + " (" + reason + ")";
    }

    private void addSelectedColumn() {
        // Select Column
        final Column<IndexShard, TickBoxState> column = new Column<IndexShard, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final IndexShard indexShard) {
                return TickBoxState.fromBoolean(selection.isMatch(indexShard.getId()));
            }

        };
        final Header<TickBoxState> header = new Header<TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                if (selection.isMatchAll()) {
                    return TickBoxState.TICK;
                } else if (selection.size() > 0) {
                    return TickBoxState.HALF_TICK;
                }
                return TickBoxState.UNTICK;
            }
        };
        dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
        header.setUpdater(value -> {
            if (value.equals(TickBoxState.UNTICK)) {
                selection.clear();
                selection.setMatchAll(false);
            } else if (value.equals(TickBoxState.TICK)) {
                selection.clear();
                selection.setMatchAll(true);
            }
            dataGrid.redraw();
            onSelectionChange.run();
        });
        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                selection.add(row.getId());
            } else {
                // De-selecting one and currently matching all ?
                if (selection.isMatchAll()) {
                    selection.setMatchAll(false);
                    for (final IndexShard shard : dataGrid.getVisibleItems()) {
                        selection.add(shard.getId());
                    }
                }
                selection.remove(row.getId());
            }
            dataGrid.redrawHeaders();
            onSelectionChange.run();
        });
    }

    private void addInfoColumn() {
        dataGrid.addColumn(new InfoColumn<IndexShard>() {
            @Override
            protected void showInfo(final IndexShard indexShard,
                                    final PopupPosition popupPosition,
                                    final Element anchor) {
                onShowInfo.show(indexShard, popupPosition, anchor);
            }
        }, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addNodeColumn() {
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_NODE, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getNodeName();
            }
        }, FindIndexShardCriteria.FIELD_NODE, 100);
    }

    private void addPartitionColumn() {
        final OrderByColumn<IndexShard, String> col = new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_PARTITION, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getPartition();
            }
        };
        dataGrid.addResizableColumn(col, FindIndexShardCriteria.FIELD_PARTITION, 100);

        // Sort by partition ascending by default.
        dataGrid.sort(col);
    }

    private void addPathColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getVolume().getPath();
            }
        }, "Path", 200);
    }

    private void addStatusColumn() {
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_STATUS, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getStatus().getDisplayValue();
            }
        }, FindIndexShardCriteria.FIELD_STATUS, 100);
    }

    private void addDocCountColumn() {
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_DOC_COUNT, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return intToString(indexShard.getDocumentCount());
            }
        }, FindIndexShardCriteria.FIELD_DOC_COUNT, 100);
    }

    private void addFileSizeColumn() {
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_FILE_SIZE, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getFileSizeString();
            }
        }, FindIndexShardCriteria.FIELD_FILE_SIZE, 100);
    }

    private void addBytesPDColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return intToString(indexShard.getBytesPerDocument());
            }
        }, "Bytes pd", 100);
    }

    private void addCommitColumn(final Function<Long, String> dateTimeFormat) {
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_LAST_COMMIT, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return dateTimeFormat.apply(indexShard.getCommitMs());
            }
        }, FindIndexShardCriteria.FIELD_LAST_COMMIT, 170);
    }

    private void addVersionColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getIndexVersion();
            }
        }, "Index Version", 100);
    }

    /// @param integer A number, or `null`.
    /// @return It as text, or `null`.
    static String intToString(final Integer integer) {
        if (integer == null) {
            return null;
        }
        return integer.toString();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IndexShardList> {

    }

    /// Shows a shard's info.
    public interface InfoHandler {

        /// @param row           The shard.
        /// @param popupPosition Where to show it.
        /// @param anchor        The info icon pressed.
        void show(IndexShard row, PopupPosition popupPosition, Element anchor);
    }
}
