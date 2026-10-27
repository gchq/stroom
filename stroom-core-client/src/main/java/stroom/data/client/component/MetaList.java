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

package stroom.data.client.component;

import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.info.client.CellIcon;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.query.api.datasource.QueryField;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Selection;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnBuilder;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/// Streams, a row each (as the columns chosen allow, a tick box to choose it, the tree of streams it
/// came from, then its status, when it was made, its type, feed and pipeline, its sizes and record
/// counts and its retention); one can be selected.
public class MetaList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<MetaRow> dataGrid;

    private final MultiSelectionModelImpl<MetaRow> selectionModel;
    private final DataGridSelectionEventManager<MetaRow> selectionEventManager;
    private Selection<Long> selection = Selection.selectNone();
    private Column<MetaRow, Expander> expanderColumn;
    private Consumer<Boolean> onSelectAll = selectAll -> {
    };
    private Runnable onSelectionChange = () -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until
    /// [#addColumns(Columns, Function, FeedRefCell.Builder, DocRefCell.Builder, Function)].
    public MetaList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = new MultiSelectionModelImpl<>();
        selectionEventManager = new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns, once.
    ///
    /// @param columns      Which of the optional columns to add.
    /// @param dateFormat   Formats a time in milliseconds since the epoch.
    /// @param feedCell     The feed cell's builder, configured to open its feeds.
    /// @param pipelineCell The pipeline cell's builder, configured to open its pipelines.
    /// @param expander     Gives a stream's place in the tree of streams, for [Columns#LINEAGE].
    public void addColumns(final Columns columns,
                           final Function<Long, String> dateFormat,
                           final FeedRefCell.Builder<MetaRow> feedCell,
                           final DocRefCell.Builder<MetaRow> pipelineCell,
                           final Function<MetaRow, Expander> expander) {
        if (columns != Columns.PLAIN) {
            addSelectedColumn(columns == Columns.SELECTABLE);
        }
        if (columns == Columns.LINEAGE) {
            expanderColumn = DataGridUtil.expanderColumn(expander);
            dataGrid.addColumn(expanderColumn, "<br/>", 0);
        }

        DataGridUtil.addStatusIconColumn(dataGrid, MetaList::getStatusIcon);

        final Column<MetaRow, String> createdColumn = DataGridUtil.copyTextColumnBuilder((MetaRow metaRow) ->
                        dateFormat.apply(metaRow.getMeta().getCreateMs()))
                .withSorting(MetaFields.CREATE_TIME)
                .defaultSortAscending(false)
                .build();
        dataGrid.addResizableColumn(createdColumn,
                "Created",
                ColumnSizeConstants.DATE_COL);
        // Sort by create time by default.
        dataGrid.sort(createdColumn);

        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((MetaRow metaRow) ->
                                Optional.ofNullable(metaRow)
                                        .map(MetaRow::getMeta)
                                        .map(Meta::getTypeName)
                                        .orElse(""))
                        .withSorting(MetaFields.TYPE)
                        .build(),
                "Type",
                80);

        dataGrid.addResizableColumn(
                new ColumnBuilder<MetaRow, MetaRow, Cell<MetaRow>>(Function.identity(),
                        () -> feedCell
                                .nameFunction((MetaRow metaRow) -> NullSafe.get(metaRow, MetaRow::getMeta,
                                        Meta::getFeedName))
                                .build())
                        .withSorting(MetaFields.FEED)
                        .build(),
                "Feed",
                ColumnSizeConstants.BIG_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.docRefColumnBuilder(pipelineCell
                                .docRefFunction((MetaRow metaRow) ->
                                        NullSafe.nonNull(metaRow, MetaRow::getMeta, Meta::getProcessorUuid)
                                                ? metaRow.getPipeline()
                                                : null))
                        .withSorting(MetaFields.PIPELINE_NAME)
                        .build(),
                "Pipeline",
                ColumnSizeConstants.BIG_COL);

        addRightAlignedAttributeColumn("Raw", MetaFields.RAW_SIZE,
                v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addRightAlignedAttributeColumn("Disk", MetaFields.FILE_SIZE,
                v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addRightAlignedAttributeColumn("Read", MetaFields.REC_READ,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addRightAlignedAttributeColumn("Write", MetaFields.REC_WRITE,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addRightAlignedAttributeColumn("Fatal", MetaFields.REC_FATAL,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addRightAlignedAttributeColumn("Error", MetaFields.REC_ERROR,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addRightAlignedAttributeColumn("Warn", MetaFields.REC_WARN,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addRightAlignedAttributeColumn("Info", MetaFields.REC_INFO,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Retention", DataRetentionFields.RETENTION_AGE_FIELD, ColumnSizeConstants.SMALL_COL);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<MetaRow> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    /// @return The selection, one stream at most.
    public MultiSelectionModelImpl<MetaRow> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @param tableName The list's name, for assistive technology.
    public void setTableName(final String tableName) {
        dataGrid.setTableName(tableName);
    }

    /// @param selection The streams chosen, which the tick boxes show and change.
    public void setSelection(final Selection<Long> selection) {
        this.selection = selection;
    }

    /// Draws the rows again, e.g. once the streams chosen have changed.
    public void redraw() {
        dataGrid.redraw();
    }

    /// Makes the tree column wide enough for the deepest stream shown.
    ///
    /// @param maxDepth The depth of the deepest stream, or -1 for none below a root.
    public void setExpanderDepth(final int maxDepth) {
        dataGrid.setColumnWidth(expanderColumn, ExpanderCell.getColumnWidth(maxDepth), Unit.PX);
        // Give any width the expander column gave up to the fill columns (the grid has sized its
        // columns before the first rows), as TreeRowHandler does
        dataGrid.resizeTableToFitColumns(true);
    }

    /// @param onSelectAll Given whether all streams are now chosen, when the header's tick box or the
    ///                    select all key is pressed.
    public void setOnSelectAll(final Consumer<Boolean> onSelectAll) {
        this.onSelectAll = onSelectAll;
    }

    /// @param onSelectionChange Run when the user ticks or unticks a stream.
    public void setOnSelectionChange(final Runnable onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    private void addSelectedColumn(final boolean allowSelectAll) {
        // Select Column
        final Column<MetaRow, TickBoxState> column = new Column<MetaRow, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final MetaRow object) {
                return TickBoxState.fromBoolean(selection.isMatch(object.getMeta().getId()));
            }
        };
        if (allowSelectAll) {
            final Header<TickBoxState> header = new Header<TickBoxState>(
                    TickBoxCell.create(false, false)) {
                @Override
                public TickBoxState getValue() {
                    if (selection.isMatchAll()) {
                        return TickBoxState.TICK;
                    }
                    if (selection.size() > 0) {
                        return TickBoxState.HALF_TICK;
                    }
                    return TickBoxState.UNTICK;
                }
            };
            dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

            header.setUpdater(value -> {
                if (value.equals(TickBoxState.UNTICK)) {
                    onSelectAll.accept(false);
                } else if (value.equals(TickBoxState.TICK)) {
                    onSelectAll.accept(true);
                }
            });

            selectionEventManager.addSelectAllHandler(event -> onSelectAll.accept(!selection.isMatchAll()));

        } else {
            dataGrid.addColumn(column, "", ColumnSizeConstants.CHECKBOX_COL);
        }

        // Add Handlers
        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                selection.add(row.getMeta().getId());

            } else {
                // De-selecting one and currently matching all ?
                if (selection.isMatchAll()) {
                    selection.setMatchAll(false);
                    for (final MetaRow metaRow : dataGrid.getVisibleItems()) {
                        selection.add(metaRow.getMeta().getId());
                    }
                }
                selection.remove(row.getMeta().getId());
            }
            dataGrid.redrawHeaders();
            onSelectionChange.run();
        });
    }

    private static CellIcon getStatusIcon(final MetaRow metaRow) {
        if (metaRow == null || metaRow.getMeta() == null || metaRow.getMeta().getStatus() == null) {
            return null;
        }

        // Should only show unlocked ones by default
        final Status status = metaRow.getMeta().getStatus();
        if (Status.UNLOCKED.equals(status)) {
            // Be default the screen only shows unlocked streams so have no icon for those
            return null;
        } else if (Status.DELETED.equals(status)) {
            return CellIcon.of(SvgImage.DELETE, "Deleted Stream");
        } else if (Status.LOCKED.equals(status)) {
            return CellIcon.of(SvgImage.LOCKED, "Locked Stream");
        } else {
            throw new RuntimeException("Unknown status " + status);
        }
    }

    private void addAttributeColumn(final String name,
                                    final QueryField attribute,
                                    final int size) {
        final Function<MetaRow, String> extractor = metaRow ->
                metaRow.getAttributeValue(attribute.getFldName());
        final Column<MetaRow, String> column = DataGridUtil.columnBuilder(extractor, TextCell::new)
                .build();
        dataGrid.addResizableColumn(
                column,
                name,
                size);
    }

    private void addRightAlignedAttributeColumn(final String name,
                                                final QueryField attribute,
                                                final Function<String, String> formatter,
                                                final int size) {
        final Function<MetaRow, String> extractor = metaRow -> {
            final String value = metaRow.getAttributeValue(attribute.getFldName());
            if (value == null) {
                return null;
            } else {
                return formatter.apply(value);
            }
        };
        final Column<MetaRow, String> column = DataGridUtil.columnBuilder(extractor, TextCell::new)
                .rightAligned()
                .build();
        dataGrid.addResizableColumn(
                column,
                DataGridUtil.createRightAlignedHeader(name),
                size);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, MetaList> {

    }


    /// Which of a list of streams' optional columns it has.
    public enum Columns {
        /// A tick box to choose each stream, and one in the header to choose all of them.
        SELECTABLE,
        /// No tick boxes.
        PLAIN,
        /// A tick box to choose each stream, and the tree of streams each came from.
        LINEAGE
    }
}
