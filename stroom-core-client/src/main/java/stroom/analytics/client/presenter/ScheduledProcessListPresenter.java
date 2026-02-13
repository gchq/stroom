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

import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleFields;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ScheduleBounds;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.UserFields;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef.DisplayType;
import stroom.util.shared.scheduler.Schedule;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

public class ScheduledProcessListPresenter
        extends MyPresenterWidget<PagerView> {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final MyDataGrid<ExecutionSchedule> dataGrid;
    private final MultiSelectionModelImpl<ExecutionSchedule> selectionModel;
    private RestDataProvider<ExecutionSchedule, ResultPage<ExecutionSchedule>> dataProvider;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final ClientSecurityContext securityContext;
    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private ExecutionScheduleRequest request;
    private ScheduledProcessingPresenter scheduledProcessingPresenter;

    @Inject
    public ScheduledProcessListPresenter(final EventBus eventBus,
                                         final PagerView view,
                                         final RestFactory restFactory,
                                         final DateTimeFormatter dateTimeFormatter,
                                         final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.securityContext = securityContext;

        final CriteriaFieldSort defaultSort = new CriteriaFieldSort(
                ExecutionScheduleFields.ID, true, true);
        request = ExecutionScheduleRequest.builder().sortList(Collections.singletonList(defaultSort)).build();
        dataGrid = new MyDataGrid<>(this);
        selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<ExecutionSchedule> selectionEventManager =
                new DataGridSelectionEventManager<>(
                        dataGrid,
                        selectionModel,
                        false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        view.setDataWidget(dataGrid);

        addButton = view.addButton(SvgPresets.ADD);
        editButton = view.addButton(SvgPresets.EDIT);
        removeButton = view.addButton(SvgPresets.DELETE);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        registerHandler(addButton.addClickHandler(e -> scheduledProcessingPresenter.add()));
        registerHandler(editButton.addClickHandler(e -> scheduledProcessingPresenter.edit()));
        registerHandler(removeButton.addClickHandler(e -> scheduledProcessingPresenter.remove()));
        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                scheduledProcessingPresenter.edit();
            }
        }));
        registerHandler(dataGrid.addColumnSortHandler(ignored -> refresh()));
    }

    private void addColumns() {
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(ExecutionSchedule::isEnabled))
                        .withSorting(ExecutionScheduleFields.ENABLED)
                        .withFieldUpdater((ignored, row, value) ->
                                updateEnabledState(row, value))
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether this execution schedule is enabled or not.")
                        .build(),
                80);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ExecutionSchedule::getName)
                        .withSorting(ExecutionScheduleFields.NAME, false)
                        .enabledWhen(ExecutionSchedule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionScheduleFields.NAME)
                        .withToolTip("The name for this execution schedule.")
                        .build(),
                200);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ExecutionSchedule::getNodeName)
                        .withSorting(ExecutionScheduleFields.NODE_NAME, false)
                        .enabledWhen(ExecutionSchedule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionScheduleFields.NODE_NAME)
                        .withToolTip("The name of the node that it will be executed on.")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(
                                ExecutionSchedule::getSchedule,
                                Schedule::toString))
                        .withSorting(ExecutionScheduleFields.SCHEDULE, false)
                        .enabledWhen(ExecutionSchedule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionScheduleFields.SCHEDULE)
                        .withToolTip("The execution schedule.")
                        .build(),
                200);

        final Column<ExecutionSchedule, ExecutionSchedule> runAsCol = DataGridUtil
                .userRefColumnBuilder(
                        ExecutionSchedule::getRunAsUser,
                        getEventBus(),
                        securityContext,
                        true,
                        DisplayType.AUTO)
                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                .enabledWhen(ScheduledProcessListPresenter::areScheduleAndUserEnabled)
                .build();
        dataGrid.addResizableColumn(
                runAsCol,
                DataGridUtil.headingBuilder(ExecutionScheduleFields.RUN_AS_USER)
                        .withToolTip("The processor will run with the same permissions as the Run As User.")
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);

        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(this::getBoundsAsString)
                        .withSorting(ExecutionScheduleFields.BOUNDS, false)
                        .enabledWhen(ExecutionSchedule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionScheduleFields.BOUNDS)
                        .withToolTip("The time bounds for the schedule.")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private void updateEnabledState(final ExecutionSchedule row, final TickBoxState value) {
        restFactory
                .create(EXECUTION_SCHEDULE_RESOURCE)
                .method(resource ->
                        resource.updateExecutionSchedule(row.copy()
                                .enabled(TickBoxState.getAsBoolean(value))
                                .build()))
                .onSuccess(ignored2 ->
                        refresh())
                .taskMonitorFactory(getView())
                .exec();
    }

    private static Boolean areScheduleAndUserEnabled(final ExecutionSchedule executionSchedule) {
        if (executionSchedule == null) {
            return true;
        } else {
            final boolean isUserEnabled = NullSafe.test(
                    executionSchedule,
                    ExecutionSchedule::getRunAsUser,
                    userRef -> userRef == null || userRef.isEnabled());
            return isUserEnabled && executionSchedule.isEnabled();
        }
    }

    private String getBoundsAsString(final ExecutionSchedule row) {
        final ScheduleBounds bounds = row.getScheduleBounds();
        if (bounds != null) {
            if (bounds.getStartTimeMs() != null && bounds.getEndTimeMs() != null) {
                if (bounds.getStartTimeMs().equals(bounds.getEndTimeMs())) {
                    return "On " +
                           dateTimeFormatter.format(bounds.getStartTimeMs());
                } else {
                    return "Between " +
                           dateTimeFormatter.format(bounds.getStartTimeMs()) +
                           " and " +
                           dateTimeFormatter.format(bounds.getEndTimeMs());
                }
            } else if (bounds.getStartTimeMs() != null) {
                return "After " +
                       dateTimeFormatter.format(bounds.getStartTimeMs());
            } else if (bounds.getEndTimeMs() != null) {
                return "Until " +
                       dateTimeFormatter.format(bounds.getEndTimeMs());
            }
        }
        return "Unbounded";
    }

    private void enableButtons() {
        addButton.setEnabled(true);
        editButton.setEnabled(selectionModel.hasSelectedItems());
        removeButton.setEnabled(selectionModel.hasSelectedItems());
        addButton.setTitle("Add Execution Schedule");
        editButton.setTitle("Edit Execution Schedule");
        removeButton.setTitle("Remove Execution Schedule");
    }

    public void read(final DocRef ownerDocRef) {
        request = request.copy().ownerDocRef(ownerDocRef).build();
        refresh();
    }

    public void clear() {
        dataGrid.setRowData(0, new ArrayList<>(0));
        dataGrid.setRowCount(0, true);
    }

    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<ExecutionSchedule, ResultPage<ExecutionSchedule>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<ExecutionSchedule>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    if (request != null) {
                        CriteriaUtil.setRange(request, range);
                        CriteriaUtil.setSortList(request, dataGrid.getColumnSortList());
                        restFactory
                                .create(EXECUTION_SCHEDULE_RESOURCE)
                                .method(res -> res.fetchExecutionSchedule(request))
                                .onSuccess(results -> {
                                    dataConsumer.accept(results);

                                    // Select the first item if there are any, so that we populate the history pane
                                    // to save the user having to click
                                    if (results != null
                                        && results.hasItems()
                                        && selectionModel.getSelected() == null) {
                                        selectionModel.setSelected(results.getFirst());
                                    }
                                })
                                .onFailure(errorHandler)
                                .taskMonitorFactory(getView())
                                .exec();
                    }
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return selectionModel.addSelectionHandler(handler);
    }

    public ExecutionSchedule getSelected() {
        return selectionModel.getSelected();
    }

    public void setSelected(final ExecutionSchedule executionSchedule) {
        selectionModel.setSelected(executionSchedule);
    }

    public void setScheduledProcessingPresenter(final ScheduledProcessingPresenter scheduledProcessingPresenter) {
        this.scheduledProcessingPresenter = scheduledProcessingPresenter;
    }
}
