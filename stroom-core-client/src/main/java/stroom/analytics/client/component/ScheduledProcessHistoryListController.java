/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionHistoryRequest;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The past executions of the execution schedule its owner gives ([ScheduledProcessHistoryList]), a
/// page at a time; Replay gives the selected one to its owner, unless its document is read only
/// ([Contexts#READ_ONLY]).
public class ScheduledProcessHistoryListController extends Controller {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final ScheduledProcessHistoryList view;
    private final RestFactory restFactory;
    private RestDataProvider<ExecutionHistory, ResultPage<ExecutionHistory>> dataProvider;
    private ExecutionHistoryRequest request = ExecutionHistoryRequest.builder().build();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param restFactory       Finds the executions.
    /// @param dateTimeFormatter Formats the times shown.
    @Inject
    public ScheduledProcessHistoryListController(final EventBus eventBus,
                                                 final ScheduledProcessHistoryList view,
                                                 final RestFactory restFactory,
                                                 final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        view.addColumns(dateTimeFormatter::format);
        view.setOnSortChange(this::refresh);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// Shows an execution schedule's past executions, with none selected.
    ///
    /// @param executionSchedule The schedule, or `null` for none.
    public void setExecutionSchedule(final ExecutionSchedule executionSchedule) {
        request = request.copy().executionSchedule(executionSchedule).build();
        view.getSelectionModel().clear();
        refresh();
    }

    /// @param onReplay Given the selected execution when Replay is pressed.
    public void setOnReplay(final Consumer<ExecutionHistory> onReplay) {
        view.setOnReplay(onReplay);
    }

    /// Finds the executions again; the first time, starts the list.
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
                        CriteriaUtil.setSortList(request, view.getSortList());
                        restFactory
                                .create(EXECUTION_SCHEDULE_RESOURCE)
                                .method(res -> res.fetchExecutionHistory(request))
                                .onSuccess(dataConsumer)
                                .onFailure(errorHandler)
                                .owner(ScheduledProcessHistoryListController.this)
                                .taskMonitorFactory(view.getTaskMonitor())
                                .exec();
                    } else {
                        dataConsumer.accept(ResultPage.empty());
                    }
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }
}
