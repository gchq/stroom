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

package stroom.query.client.presenter;

import stroom.cell.expander.client.ExpanderCell;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableResult;
import stroom.query.client.presenter.QueryResultTablePresenter.QueryResultTableView;
import stroom.query.client.presenter.TableRow.Cell;
import stroom.util.shared.Expander;
import stroom.widget.util.client.ExpanderEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.HasOpenHandlers;
import com.google.gwt.event.logical.shared.HasShowRangeHandlers;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.event.logical.shared.ShowRangeEvent;
import com.google.gwt.event.logical.shared.ShowRangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.RangeChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class QueryResultTablePresenter
        extends MyPresenterWidget<QueryResultTableView>
        implements ResultConsumer {

    private static final int MIN_EXPANDER_COL_WIDTH = 0;
    private int expanderColumnWidth;
    private final Column<TableRow, Expander> expanderColumn;

    private final MyDataGrid<TableRow> dataGrid;
    private final MultiSelectionModelImpl<TableRow> selectionModel;
    private final DataGridSelectionEventManager<TableRow> selectionEventManager;
    private boolean ignoreRangeChange;
    private boolean pause;
    private int currentRequestCount;
//    private QueryModel currentSearchModel;

    private OffsetRange requestedRange = new OffsetRange(0, 100);
    private Set<String> openGroups = null;




    @Inject
    public QueryResultTablePresenter(final EventBus eventBus,
                                     final QueryResultTableView tableView,
                                     final PagerView pagerView) {
        super(eventBus, tableView);

        this.dataGrid = new MyDataGrid<>();
        selectionModel = new MultiSelectionModelImpl<>(dataGrid);
        selectionEventManager = new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);

        pagerView.setDataWidget(dataGrid);
        tableView.setTableView(pagerView);

        // Expander column.
        expanderColumn = new Column<TableRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final TableRow row) {
                if (row == null) {
                    return null;
                }
                return row.getExpander();
            }
        };
        expanderColumn.setFieldUpdater((index, result, value) -> {
            toggleOpenGroup(result.getGroupKey());
            ExpanderEvent.fire(this, result.getGroupKey());
        });
    }

    private void toggleOpenGroup(final String group) {
        openGroup(group, !isGroupOpen(group));
    }

    private void openGroup(final String group, final boolean open) {
        if (openGroups == null) {
            openGroups = new HashSet<>();
        }

        if (open) {
            openGroups.add(group);
        } else {
            openGroups.remove(group);
        }
    }

    private boolean isGroupOpen(final String group) {
        return openGroups != null && openGroups.contains(group);
    }

//    public void setRequestedRange(final OffsetRange requestedRange) {
//        this.requestedRange = requestedRange;
//    }

    @Override
    public Set<String> getOpenGroups() {
        return openGroups;
    }

    @Override
    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    @Override
    protected void onBind() {
        registerHandler(dataGrid.addRangeChangeHandler(event -> {
            final com.google.gwt.view.client.Range range = event.getNewRange();
            requestedRange = new OffsetRange(range.getStart(), range.getLength());
            if (!ignoreRangeChange) {
                fireEvent(event);
            }
        }));
        registerHandler(dataGrid.addHyperlinkHandler(event -> getEventBus().fireEvent(event)));

//        registerHandler(dataGrid.addColumnSortHandler(event -> {
//            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
//                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
//                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
//                refresh();
//            }
//        }));
    }

//    @Override
//    protected void addColumns(final boolean allowSelectAll) {
//        addSelectedColumn(allowSelectAll);
//
//        addInfoColumn();
//
//        addCreatedColumn();
//        addStreamTypeColumn();
//        addFeedColumn();
//        addPipelineColumn();
//
//        addRightAlignedAttributeColumn(
//                "Raw",
//                MetaFields.RAW_SIZE,
//                v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)),
//                ColumnSizeConstants.SMALL_COL);
//        addRightAlignedAttributeColumn(
//                "Disk",
//                MetaFields.FILE_SIZE,
//                v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)),
//                ColumnSizeConstants.SMALL_COL);
//        addRightAlignedAttributeColumn(
//                "Read",
//                MetaFields.REC_READ,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                ColumnSizeConstants.SMALL_COL);
//        addRightAlignedAttributeColumn(
//                "Write",
//                MetaFields.REC_WRITE,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                ColumnSizeConstants.SMALL_COL);
//        addRightAlignedAttributeColumn(
//                "Fatal",
//                MetaFields.REC_FATAL,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                40);
//        addRightAlignedAttributeColumn(
//                "Error",
//                MetaFields.REC_ERROR,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                40);
//        addRightAlignedAttributeColumn(
//                "Warn",
//                MetaFields.REC_WARN,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                40);
//        addRightAlignedAttributeColumn(
//                "Info",
//                MetaFields.REC_INFO,
//                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
//                40);
//        addAttributeColumn(
//                "Retention",
//                DataRetentionFields.RETENTION_AGE_FIELD,
//                Function.identity(),
//                ColumnSizeConstants.SMALL_COL);
//
//        addEndColumn();
//    }


//    @Override
//    public ComponentResultRequest getResultRequest(final boolean ignorePause) {
//        return null;
//    }

    @Override
    public void reset() {
        final long length = Math.max(1, requestedRange.getLength());

        // Reset the data grid paging.
        if (dataGrid.getVisibleRange().getStart() > 0) {
            dataGrid.setVisibleRange(0, (int) length);
        }

        requestedRange = new OffsetRange(0L, length);
    }

    @Override
    public void startSearch() {
//        final TableSettings tableSettings = getTableSettings()
//                .copy()
//                .buildTableSettings();
//        tableResultRequest = tableResultRequest
//                .copy()
//                .tableSettings(tableSettings)
//                .build();

        getView().setRefreshing(true);
    }

    @Override
    public void endSearch() {
        getView().setRefreshing(false);
    }

    @Override
    public void setData(final Result componentResult) {
        GWT.log("setData");

        if (!pause) {
            setDataInternal(componentResult);
        }
    }

    private List<Field> currentFields;
    private final List<Column<TableRow, ?>> existingColumns = new ArrayList<>();

    private void setDataInternal(final Result componentResult) {
        GWT.log("setDataInternal");

        ignoreRangeChange = true;

        try {
            if (componentResult != null) {
                // Don't refresh the table unless the results have changed.
                final TableResult tableResult = (TableResult) componentResult;

                if (!Objects.equals(currentFields, tableResult.getFields())) {
//                    final Set<String> newIdSet = tableResult
//                            .getFields()
//                            .stream()
//                            .map(Field::getId)
//                            .collect(Collectors.toSet());

                    // First remove stale fields.
                    updateColumns(tableResult.getFields());

//                    removeAllColumns();
//
//                    // Add new columns.
//                    for (final Field field : tableResult.getFields()) {
//                        addColumn(field);
//                    }
                    currentFields = tableResult.getFields();
                }

                final List<TableRow> values = processData(tableResult.getFields(), tableResult.getRows());
                final OffsetRange valuesRange = tableResult.getResultRange();

                // Only set data in the table if we have got some results and
                // they have changed.
                if (valuesRange.getOffset() == 0 || values.size() > 0) {
                    dataGrid.setRowData(valuesRange.getOffset().intValue(), values);
                    dataGrid.setRowCount(tableResult.getTotalResults(), true);
                }

//                // Enable download of current results.
//                downloadButton.setEnabled(true);
            } else {
//                // Disable download of current results.
//                downloadButton.setEnabled(false);

                dataGrid.setRowData(0, new ArrayList<>());
                dataGrid.setRowCount(0, true);

                selectionModel.clear();
            }
        } catch (final RuntimeException e) {
            GWT.log(e.getMessage());
        }

        ignoreRangeChange = false;
    }

    private void removeAllColumns() {
        for (Column<TableRow, ?> column : existingColumns) {
            dataGrid.removeColumn(column);
        }
        existingColumns.clear();
    }

    void updateColumns(final List<Field> fields) {
//        // Now make sure special fields exist for stream id and event id.
//        ensureSpecialFields(IndexConstants.STREAM_ID, IndexConstants.EVENT_ID, "Id");

        // Remove existing columns.
        for (final Column<TableRow, ?> column : existingColumns) {
            dataGrid.removeColumn(column);
        }
        existingColumns.clear();

//        final List<Field> fields = getTableSettings().getFields();
        addExpanderColumn();
//        fieldsManager.setFieldsStartIndex(1);

        // Add fields as columns.
        for (final Field field : fields) {
            // Only include the field if it is supposed to be visible.
            if (field.isVisible()) {
                addColumn(field);
            }
        }

        dataGrid.resizeTableToFitColumns();
    }

    private void addExpanderColumn() {
        dataGrid.addColumn(expanderColumn, "<br/>", expanderColumnWidth);
        existingColumns.add(expanderColumn);
    }

    private void addColumn(final Field field) {
        final Column<TableRow, SafeHtml> column = new Column<TableRow, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final TableRow row) {
                if (row == null) {
                    return null;
                }

                return row.getValue(field.getId());
            }
        };

        final FieldHeader fieldHeader = new FieldHeader(field);
        dataGrid.addResizableColumn(column, fieldHeader, field.getWidth());
        existingColumns.add(column);
    }

    private List<TableRow> processData(final List<Field> fields, final List<Row> values) {
        // See if any fields have more than 1 level. If they do then we will add
        // an expander column.
        int maxGroup = -1;
        final boolean showDetail = true;//getTableSettings().showDetail();
        for (final Field field : fields) {
            if (field.getGroup() != null) {
                final int group = field.getGroup();
                if (group > maxGroup) {
                    maxGroup = group;
                }
            }
        }
        int maxDepth = maxGroup;
        if (showDetail) {
            maxDepth++;
        }

        final List<TableRow> processed = new ArrayList<>(values.size());
        for (final Row row : values) {
            SafeStylesBuilder rowStyle = new SafeStylesBuilder();

            // Row styles.
            if (row.getBackgroundColor() != null
                    && !row.getBackgroundColor().isEmpty()) {
                rowStyle.trustedBackgroundColor(row.getBackgroundColor());
            }
            if (row.getTextColor() != null
                    && !row.getTextColor().isEmpty()) {
                rowStyle.trustedColor(row.getTextColor());
            }

            final Map<String, Cell> cellsMap = new HashMap<>();
            for (int i = 0; i < fields.size() && i < row.getValues().size(); i++) {
                final Field field = fields.get(i);
                final String value = row.getValues().get(i) != null
                        ? row.getValues().get(i)
                        : "";

                SafeStylesBuilder stylesBuilder = new SafeStylesBuilder();
                stylesBuilder.append(rowStyle.toSafeStyles());

                // Wrap
                if (field.getFormat() != null && field.getFormat().getWrap() != null && field.getFormat().getWrap()) {
                    stylesBuilder.whiteSpace(Style.WhiteSpace.NORMAL);
                }
                // Grouped
                if (field.getGroup() != null && field.getGroup() >= row.getDepth()) {
                    stylesBuilder.fontWeight(Style.FontWeight.BOLD);
                }

                final String style = stylesBuilder.toSafeStyles().asString();

                final TableRow.Cell cell = new TableRow.Cell(value, style);
                cellsMap.put(field.getId(), cell);
            }

            // Create an expander for the row.
            Expander expander = null;
            if (row.getDepth() < maxDepth) {
                final boolean open = isGroupOpen(row.getGroupKey());
                expander = new Expander(row.getDepth(), open, false);
            } else if (row.getDepth() > 0) {
                expander = new Expander(row.getDepth(), false, true);
            }

            processed.add(new TableRow(expander, row.getGroupKey(), cellsMap));
        }

        // Set the expander column width.
        if (maxDepth > 0) {
            expanderColumnWidth = 16 + (maxDepth * 10);
        } else {
            expanderColumnWidth = MIN_EXPANDER_COL_WIDTH;
        }
        dataGrid.setColumnWidth(expanderColumn, expanderColumnWidth, Unit.PX);

        return processed;
    }



//    private void refresh() {
//        currentRequestCount++;
//        getView().setPaused(pause && currentRequestCount == 0);
//        getView().setRefreshing(true);
//        currentSearchModel.refresh("table", result -> {
//            try {
//                if (result != null) {
//                    setDataInternal(result);
//                }
//            } catch (final Exception e) {
//                GWT.log(e.getMessage());
//            }
//            currentRequestCount--;
//            getView().setPaused(pause && currentRequestCount == 0);
//            getView().setRefreshing(currentSearchModel.getMode());
//        });
//    }

    public HandlerRegistration addRangeChangeHandler(RangeChangeEvent.Handler handler) {
        return addHandler(RangeChangeEvent.getType(), handler);
    }

    public HandlerRegistration addExpanderHandler(final ExpanderEvent.Handler handler) {
        return addHandler(ExpanderEvent.getType(), handler);
    }

    public interface QueryResultTableView extends View, HasUiHandlers<TableUiHandlers> {

        void setTableView(View view);

        void setRefreshing(boolean refreshing);

        void setPaused(boolean paused);
    }
}
