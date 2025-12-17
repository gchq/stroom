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
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
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
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

public class ScheduledProcessListPresenter
        extends MyPresenterWidget<PagerView> {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final MyDataGrid<ExecutionSchedule> dataGrid;
    private final MultiSelectionModelImpl<ExecutionSchedule> selectionModel;
    private final DataGridSelectionEventManager<ExecutionSchedule> selectionEventManager;
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

        final CriteriaFieldSort defaultSort = new CriteriaFieldSort(ExecutionScheduleFields.ID, true, true);
        request = ExecutionScheduleRequest.builder().sortList(Collections.singletonList(defaultSort)).build();
        dataGrid = new MyDataGrid<>(this);
        selectionModel = new MultiSelectionModelImpl<>();
        selectionEventManager = new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
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
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    private void addColumns() {
        final Column<ExecutionSchedule, TickBoxState> enabledColumn = new Column<ExecutionSchedule, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final ExecutionSchedule row) {
                if (row != null) {
                    return TickBoxState.fromBoolean(row.isEnabled());
                }
                return null;
            }
        };
        enabledColumn.setFieldUpdater((index, row, value) -> {
            restFactory
                    .create(EXECUTION_SCHEDULE_RESOURCE)
                    .method(res -> res.updateExecutionSchedule(row.copy().enabled(value.toBoolean()).build()))
                    .onSuccess(updated -> refresh())
                    .taskMonitorFactory(getView())
                    .exec();
        });
        dataGrid.addColumn(enabledColumn, "Enabled", 80);

        dataGrid.addResizableColumn(
                new OrderByColumn<ExecutionSchedule, String>(
                        new TextCell(),
                        ExecutionScheduleFields.NAME,
                        false) {
                    @Override
                    public String getValue(final ExecutionSchedule row) {
                        return row.getName();
                    }
                }, ExecutionScheduleFields.NAME, ColumnSizeConstants.DATE_COL);

        dataGrid.addResizableColumn(
                new OrderByColumn<ExecutionSchedule, String>(
                        new TextCell(),
                        ExecutionScheduleFields.NODE_NAME,
                        false) {
                    @Override
                    public String getValue(final ExecutionSchedule row) {
                        return row.getNodeName();
                    }
                }, ExecutionScheduleFields.NODE_NAME, ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                new OrderByColumn<ExecutionSchedule, String>(
                        new TextCell(),
                        ExecutionScheduleFields.SCHEDULE,
                        false) {
                    @Override
                    public String getValue(final ExecutionSchedule row) {
                        return row.getSchedule().toString();
                    }
                }, ExecutionScheduleFields.SCHEDULE, ColumnSizeConstants.DATE_COL);


        final Column<ExecutionSchedule, ExecutionSchedule> runAsCol = DataGridUtil
                .userRefColumnBuilder(
                        ExecutionSchedule::getRunAsUser,
                        getEventBus(),
                        securityContext,
                        true,
                        DisplayType.AUTO)
                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                .enabledWhen(executionSchedule ->
                        Optional.ofNullable(executionSchedule)
                                .map(ExecutionSchedule::getRunAsUser)
                                .map(UserRef::isEnabled)
                                .orElse(true))
                .build();
        dataGrid.addResizableColumn(runAsCol,
                DataGridUtil.headingBuilder(ExecutionScheduleFields.RUN_AS_USER)
                        .withToolTip("The processor will run with the same permissions as the Run As User.")
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);

        dataGrid.addAutoResizableColumn(
                new OrderByColumn<ExecutionSchedule, String>(
                        new TextCell(),
                        ExecutionScheduleFields.BOUNDS,
                        false) {
                    @Override
                    public String getValue(final ExecutionSchedule row) {
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
                }, ExecutionScheduleFields.BOUNDS, ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void enableButtons() {
        addButton.setEnabled(true);
        editButton.setEnabled(selectionModel.getSelectedItems().size() > 0);
        removeButton.setEnabled(selectionModel.getSelectedItems().size() > 0);
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
                                .onSuccess(dataConsumer)
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
