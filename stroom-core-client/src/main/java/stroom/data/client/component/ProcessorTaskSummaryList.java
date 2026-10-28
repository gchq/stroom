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

import stroom.cell.info.client.InfoColumn;
import stroom.data.client.ColumnSizeConstants;
import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.OrderByColumn;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.ProvidesKey;

import java.util.function.Function;

/// Processor tasks counted by pipeline, feed, priority and status, a row each, sorted by a column;
/// one can be selected.
public class ProcessorTaskSummaryList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ProcessorTaskSummary> dataGrid;

    private final MultiSelectionModelImpl<ProcessorTaskSummary> selectionModel;
    private InfoHandler onShowInfo = (row, popupPosition, anchor) -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns].
    public ProcessorTaskSummaryList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by their group, not their count, which changes
        dataGrid.setKeyProvider(summary -> NullSafe.get(summary.getPipeline(), DocRef::getUuid)
                                           + ":" + summary.getFeed()
                                           + ":" + summary.getPriority()
                                           + ":" + summary.getStatus());
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns.
    ///
    /// @param pipelineCell The pipeline cell's builder, configured to open its pipelines.
    /// @param feedCell     The feed cell's builder, configured to open its feeds.
    public void addColumns(final DocRefCell.Builder<ProcessorTaskSummary> pipelineCell,
                           final FeedRefCell.Builder<ProcessorTaskSummary> feedCell) {
        // Info column.
        final InfoColumn<ProcessorTaskSummary> infoColumn = new InfoColumn<ProcessorTaskSummary>() {
            @Override
            protected void showInfo(final ProcessorTaskSummary row,
                                    final PopupPosition popupPosition,
                                    final Element anchor) {
                onShowInfo.show(row, popupPosition, anchor);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        final Function<ProcessorTaskSummary, DocRef> pipelineExtractionFunction = ProcessorTaskSummary::getPipeline;
        DataGridUtil.addDocRefColumn(pipelineCell, dataGrid, "Pipeline", pipelineExtractionFunction, true);

        final Function<ProcessorTaskSummary, String> feedExtractionFunction = ProcessorTaskSummary::getFeed;
        DataGridUtil.addFeedColumn(feedCell, dataGrid, "Feed", feedExtractionFunction);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(),
                        ProcessorTaskFields.FIELD_PRIORITY,
                        false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return String.valueOf(row.getPriority());
                    }
                }, "Priority", 60);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(),
                        ProcessorTaskFields.FIELD_STATUS,
                        false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return row.getStatus().getDisplayValue();
                    }
                }, "Status", ColumnSizeConstants.SMALL_COL);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(),
                        ProcessorTaskFields.FIELD_COUNT,
                        false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return ModelStringUtil.formatCsv(row.getCount());
                    }
                }, "Count", ColumnSizeConstants.SMALL_COL);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<ProcessorTaskSummary> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one row at most.
    public MultiSelectionModelImpl<ProcessorTaskSummary> getSelectionModel() {
        return selectionModel;
    }

    /// @return Keys a row by its group, not its count.
    public ProvidesKey<ProcessorTaskSummary> getKeyProvider() {
        return dataGrid.getKeyProvider();
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
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


    interface Binder extends UiBinder<Widget, ProcessorTaskSummaryList> {

    }

    /// Shows a row's info.
    public interface InfoHandler {

        /// @param row           The row.
        /// @param popupPosition Where to show it.
        /// @param anchor        The info icon pressed.
        void show(ProcessorTaskSummary row, PopupPosition popupPosition, Element anchor);
    }
}
