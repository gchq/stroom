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

package stroom.analytics.client.presenter;

import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryFields;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

public class ScheduledProcessHistoryListPresenter
        extends MyPresenterWidget<PagerView> {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final RestFactory restFactory;
    private final MyDataGrid<ExecutionHistory> dataGrid;
    private final MultiSelectionModelImpl<ExecutionHistory> selectionModel;
    private RestDataProvider<ExecutionHistory, ResultPage<ExecutionHistory>> dataProvider;
    private final DateTimeFormatter dateTimeFormatter;
    private ExecutionHistoryRequest request;
    private final ButtonView replayButton;
    private ScheduledProcessingPresenter scheduledProcessingPresenter;

    @Inject
    public ScheduledProcessHistoryListPresenter(final EventBus eventBus,
                                                final PagerView view,
                                                final RestFactory restFactory,
                                                final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;

        final CriteriaFieldSort defaultSort = new CriteriaFieldSort(
                ExecutionHistoryFields.ID, true, true);
        request = ExecutionHistoryRequest.builder().sortList(Collections.singletonList(defaultSort)).build();
        dataGrid = new MyDataGrid<>(this);
        dataGrid.setMultiLine(true);
        selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<ExecutionHistory> selectionEventManager =
                new DataGridSelectionEventManager<>(
                        dataGrid,
                        selectionModel,
                        false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        view.setDataWidget(dataGrid);

        replayButton = view.addButton(SvgPresets.RERUN);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        registerHandler(replayButton.addClickHandler(ignored -> replay()));
        registerHandler(selectionModel.addSelectionHandler(ignored -> enableButtons()));
        registerHandler(dataGrid.addColumnSortHandler(ignored -> refresh()));
    }

    private void addColumns() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(
                                ExecutionHistory::getExecutionTimeMs,
                                dateTimeFormatter::format))
                        .withSorting(ExecutionHistoryFields.EXECUTION_TIME, false)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionHistoryFields.EXECUTION_TIME)
                        .withToolTip("The time it was actually executed.")
                        .build(),
                ColumnSizeConstants.DATE_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(
                                ExecutionHistory::getEffectiveExecutionTimeMs,
                                dateTimeFormatter::format))
                        .withSorting(ExecutionHistoryFields.EFFECTIVE_EXECUTION_TIME, false)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionHistoryFields.EFFECTIVE_EXECUTION_TIME)
                        .withToolTip("The time the data was based on rather than the time it was executed.")
                        .build(),
                ColumnSizeConstants.DATE_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.redGreenTextColumnBuilder(
                                ExecutionHistory::isComplete,
                                ExecutionHistory.STATUS_COMPLETE,
                                ExecutionHistory.STATUS_ERROR)
                        .withSorting(ExecutionHistoryFields.STATUS, false)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionHistoryFields.STATUS)
                        .withToolTip("The status of the execution (Complete|Error).")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(ExecutionHistory::getMessage)
                        .withSorting(ExecutionHistoryFields.MESSAGE, false)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionHistoryFields.MESSAGE)
                        .withToolTip("Any output or error message output by the execution.")
                        .build(),
                ColumnSizeConstants.BIG_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private void replay() {
        final ExecutionHistory executionHistory = selectionModel.getSelected();
        scheduledProcessingPresenter.replay(executionHistory);
    }

    public void setExecutionSchedule(final ExecutionSchedule executionSchedule) {
        request = request.copy().executionSchedule(executionSchedule).build();
        selectionModel.clear();
        refresh();
        enableButtons();
    }

    public void clear() {
        dataGrid.setRowData(0, new ArrayList<>(0));
        dataGrid.setRowCount(0, true);
    }

    public void refresh() {
        if (dataProvider == null) {
            //noinspection Convert2Diamond // Cos GWT
            dataProvider = new RestDataProvider<ExecutionHistory, ResultPage<ExecutionHistory>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<ExecutionHistory>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    if (request != null && request.getExecutionSchedule() != null) {
                        CriteriaUtil.setRange(request, range);
                        CriteriaUtil.setSortList(request, dataGrid.getColumnSortList());
                        restFactory
                                .create(EXECUTION_SCHEDULE_RESOURCE)
                                .method(res -> res.fetchExecutionHistory(request))
                                .onSuccess(dataConsumer)
                                .onFailure(errorHandler)
                                .taskMonitorFactory(getView())
                                .exec();
                    } else {
                        dataConsumer.accept(ResultPage.empty());
                    }
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    private void enableButtons() {
        replayButton.setEnabled(selectionModel.hasSelectedItems());
        replayButton.setTitle("Replay Execution");
    }

    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return selectionModel.addSelectionHandler(handler);
    }

    public void setScheduledProcessingPresenter(final ScheduledProcessingPresenter scheduledProcessingPresenter) {
        this.scheduledProcessingPresenter = scheduledProcessingPresenter;
    }
}
