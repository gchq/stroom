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

import stroom.cell.client.InfoColumn;
import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.util.client.DataGridUtil;
import stroom.util.client.cell.DocRefCell;
import stroom.util.client.cell.FeedRefCell;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.OrderByColumn;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.ArrayList;
import java.util.function.Function;

/// Processor tasks, a row each (its info, when it was created, its status, node, feed, priority,
/// pipeline, and when it started and ended), sorted by a column.
public class ProcessorTasksList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ProcessorTask> dataGrid;

    private InfoHandler onShowInfo = (row, popupPosition, anchor) -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns].
    public ProcessorTasksList() {
        initWidget(BINDER.createAndBindUi(this));
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns.
    ///
    /// @param dateFormat   Formats a time, in the user's preferences.
    /// @param feedCell     The feed cell's builder, configured to open its feeds.
    /// @param pipelineCell The pipeline cell's builder, configured to open its pipelines.
    public void addColumns(final Function<Long, String> dateFormat,
                           final FeedRefCell.Builder<ProcessorTask> feedCell,
                           final DocRefCell.Builder<ProcessorTask> pipelineCell) {
        // Info column.
        dataGrid.addColumn(new InfoColumn<ProcessorTask>() {
            @Override
            protected void showInfo(final ProcessorTask row,
                                    final PopupPosition popupPosition,
                                    final Element anchor) {
                onShowInfo.show(row, popupPosition, anchor);
            }
        }, "<br/>", ColumnSizeConstants.ICON_COL);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(
                        new TextCell(),
                        ProcessorTaskFields.FIELD_CREATE_TIME,
                        false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return dateFormat.apply(row.getCreateTimeMs());
                    }
                }, "Create", ColumnSizeConstants.DATE_COL);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(
                        new TextCell(),
                        ProcessorTaskFields.FIELD_STATUS,
                        false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return row.getStatus().getDisplayValue();
                    }
                }, "Status", 80);

        dataGrid
                .addResizableColumn(new OrderByColumn<ProcessorTask, String>(
                        new TextCell(), ProcessorTaskFields.FIELD_NODE, true) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        if (row.getNodeName() != null) {
                            return row.getNodeName();
                        } else {
                            return "";
                        }
                    }
                }, "Node", ColumnSizeConstants.MEDIUM_COL);

        final Function<ProcessorTask, String> feedExtractionFunction = row -> {
            if (row.getFeedName() != null) {
                return row.getFeedName();
            } else {
                return null;
            }
        };

        DataGridUtil.addFeedColumn(feedCell, dataGrid, "Feed", feedExtractionFunction);

        dataGrid.addResizableColumn(new OrderByColumn<ProcessorTask, String>(
                new TextCell(), ProcessorTaskFields.FIELD_PRIORITY, false) {
            @Override
            public String getValue(final ProcessorTask row) {
                if (row.getProcessorFilter() != null) {
                    return String.valueOf(row.getProcessorFilter().getPriority());
                }

                return "";
            }
        }, "Priority", 60);
        final Function<ProcessorTask, DocRef> pipelineExtractionFunction = row -> {
            if (row.getProcessorFilter() != null) {
                return row.getProcessorFilter().getPipeline();
            }
            return null;
        };

        DataGridUtil.addDocRefColumn(pipelineCell, dataGrid, "Pipeline", pipelineExtractionFunction, true);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(
                        new TextCell(), ProcessorTaskFields.FIELD_START_TIME, false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return dateFormat.apply(row.getStartTimeMs());
                    }
                }, "Start Time", ColumnSizeConstants.DATE_COL);
        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(
                        new TextCell(), ProcessorTaskFields.FIELD_END_TIME_DATE, false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return dateFormat.apply(row.getEndTimeMs());
                    }
                }, "End Time", ColumnSizeConstants.DATE_COL);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<ProcessorTask> dataProvider) {
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

    /// Empties the list.
    public void clear() {
        dataGrid.setRowData(0, new ArrayList<>(0));
        dataGrid.setRowCount(0, true);
    }

    /// @param onShowInfo Shows a row's info, by the info icon pressed.
    public void setOnShowInfo(final InfoHandler onShowInfo) {
        this.onShowInfo = onShowInfo;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProcessorTasksList> {

    }

    /// Shows a row's info.
    public interface InfoHandler {

        /// @param row           The row.
        /// @param popupPosition Where to show it.
        /// @param anchor        The info icon pressed.
        void show(ProcessorTask row, PopupPosition popupPosition, Element anchor);
    }
}
