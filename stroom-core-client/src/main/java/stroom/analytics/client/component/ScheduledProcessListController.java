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

import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.data.client.component.RefCellsController;
import stroom.data.client.component.UserRefCell;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.node.client.NodeClient;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// A document's execution schedules ([ScheduledProcessList]), a page at a time, the first selected
/// when none is; a schedule's tick box enables or disables it. New, Edit and Delete go to its owner.
/// Nothing can be changed when its document is read only ([Contexts#READ_ONLY]).
public class ScheduledProcessListController extends Controller {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final ScheduledProcessList view;
    private final RestFactory restFactory;
    private RestDataProvider<ExecutionSchedule, ResultPage<ExecutionSchedule>> dataProvider;
    private ExecutionScheduleRequest request = ExecutionScheduleRequest.builder().build();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param restFactory       Finds, and enables or disables, the schedules.
    /// @param dateTimeFormatter Formats the times shown.
    /// @param nodeClient        Lists the nodes, to mark schedules naming one that no longer exists.
    /// @param refCells          Lets the run as user cells open their users.
    @Inject
    public ScheduledProcessListController(final EventBus eventBus,
                                          final ScheduledProcessList view,
                                          final RestFactory restFactory,
                                          final DateTimeFormatter dateTimeFormatter,
                                          final NodeClient nodeClient,
                                          final RefCellsController refCells) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        own(refCells);
        initWidget(view);
        view.addColumns(dateTimeFormatter::format, refCells.configure(new UserRefCell.Builder<>()));
        view.setOnEnabledChange(this::updateEnabledState);
        view.setOnSortChange(this::refresh);
        watch(Contexts.READ_ONLY, view::setReadOnly);

        // Used to mark schedules that name a node that no longer exists. A failure to load isn't reported because
        // this only enriches the display; without it we simply mark nothing.
        nodeClient.listAllNodes(
                nodeNames -> view.setKnownNodeNames(NullSafe.list(nodeNames)),
                throwable -> view.clearKnownNodeNames(),
                view.getTaskMonitor());
    }

    private void updateEnabledState(final ExecutionSchedule row, final Boolean enabled) {
        if (view.isReadOnly()) {
            return;
        }
        restFactory
                .create(EXECUTION_SCHEDULE_RESOURCE)
                .method(resource ->
                        resource.updateExecutionSchedule(row.copy()
                                .enabled(enabled)
                                .build()))
                .onSuccess(ignored -> refresh())
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    /// Shows a document's execution schedules.
    ///
    /// @param ownerDocRef The document.
    public void read(final DocRef ownerDocRef) {
        request = request.copy().ownerDocRef(ownerDocRef).build();
        refresh();
    }

    /// @param onAdd Run when New is pressed.
    public void setOnAdd(final Runnable onAdd) {
        view.setOnAdd(onAdd);
    }

    /// @param onEdit Run when Edit is pressed, or a schedule is double clicked.
    public void setOnEdit(final Runnable onEdit) {
        view.setOnEdit(onEdit);
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        view.setOnDelete(onDelete);
    }

    /// Finds the schedules again; the first time, starts the list.
    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<ExecutionSchedule, ResultPage<ExecutionSchedule>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<ExecutionSchedule>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    if (request != null) {
                        CriteriaUtil.setRange(request, range);
                        CriteriaUtil.setSortList(request, view.getSortList());
                        restFactory
                                .create(EXECUTION_SCHEDULE_RESOURCE)
                                .method(res -> res.fetchExecutionSchedule(request))
                                .onSuccess(results -> {
                                    dataConsumer.accept(results);

                                    // Select the first item if there are any, so that we populate the history pane
                                    // to save the user having to click
                                    if (results != null
                                        && results.hasItems()
                                        && view.getSelectionModel().getSelected() == null) {
                                        view.getSelectionModel().setSelected(results.getFirst());
                                    }
                                })
                                .onFailure(errorHandler)
                                .owner(ScheduledProcessListController.this)
                                .taskMonitorFactory(view.getTaskMonitor())
                                .exec();
                    }
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// @param handler Told when the selection changes.
    /// @return Its registration.
    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return view.getSelectionModel().addSelectionHandler(handler);
    }

    /// @return The selected schedule, or `null`.
    public ExecutionSchedule getSelected() {
        return view.getSelectionModel().getSelected();
    }

    /// @param executionSchedule The schedule to select.
    public void setSelected(final ExecutionSchedule executionSchedule) {
        view.getSelectionModel().setSelected(executionSchedule);
    }
}
