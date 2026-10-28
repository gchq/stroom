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

package stroom.analytics.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.client.component.ExecutionScheduleRunNow.RunNowAction;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleFields;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ScheduleBounds;
import stroom.cell.info.client.CellIcon;
import stroom.cell.info.client.IconCell;
import stroom.content.client.component.ContentTabController;
import stroom.data.client.component.ExpressionDialogController;
import stroom.data.client.component.RefCellsController;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.ExpressionValidator;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.datagrid.client.OrderByColumn;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
//

/// Shows the execution schedules of analytic rules and reports, and edits, runs and deletes them.
public class ExecutionSchedulesController extends ContentTabController {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    public static final String TAB_TYPE = "ExecutionScheduleManager";

    private final PagerPanel view;

    private final DataGrid<ExecutionSchedule> dataGrid;
    // How many schedules are selected, after the toolbar
    private final HTML selectionCountLabel = new HTML();
    private final MultiSelectionModelImpl<ExecutionSchedule> selectionModel;
    private RestDataProvider<ExecutionSchedule, ResultPage<ExecutionSchedule>> dataProvider;
    private final RestFactory restFactory;
    private final ExecutionScheduleRequest request;
    private final ScheduledProcessEditController scheduledProcessEdit;
    private final BatchExecutionScheduleEditController batchExecutionScheduleEdit;
    private final Provider<ExecutionScheduleRunNowController> runNowProvider;
    private final Provider<ExpressionDialogController> filterDialogProvider;
    private final ExpressionValidator expressionValidator;

    private ButtonView singleEditButton;
    private ButtonView batchEditButton;
    private ButtonView filterButton;
    private ButtonView clearFilterButton;
    private ButtonView runJobNowButton;
    private ButtonView deleteButton;

//    private final ScheduledQueryAnalyticExecutor scheduledQueryExecutor;

    private final RefCellsController refCells;

    /// Made by GIN, with its screen and its parts.
    @Inject
    public ExecutionSchedulesController(final EventBus eventBus,
                                        final PagerPanel view,
                                        final DateTimeFormatter dateTimeFormatter,
                                        final RestFactory restFactory,
                                        final ScheduledProcessEditController scheduledProcessEdit,
                                        final BatchExecutionScheduleEditController batchExecutionScheduleEdit,
                                        final Provider<ExecutionScheduleRunNowController> runNowProvider,
                                        final Provider<ExpressionDialogController> filterDialogProvider,
                                        final ExpressionValidator expressionValidator,
                                        final RefCellsController refCells) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.refCells = own(refCells);
        own(scheduledProcessEdit);
        own(batchExecutionScheduleEdit);
        this.restFactory = restFactory;

        this.scheduledProcessEdit = scheduledProcessEdit;
        this.batchExecutionScheduleEdit = batchExecutionScheduleEdit;
        this.runNowProvider = runNowProvider;
        this.filterDialogProvider = filterDialogProvider;
        this.expressionValidator = expressionValidator;

        dataGrid = new DataGrid<>();
        dataGrid.setTableName("Execution Schedules");
        selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<ExecutionSchedule> selectionEventManager
            = new DataGridSelectionEventManager<>(
                dataGrid,
                selectionModel,
                true);

        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        view.setDataWidget(dataGrid);

        request = ExecutionScheduleRequest.builder().build();

        createButtons();

        selectionCountLabel.addStyleName("paging-bar paging-bar__label");
        view.addToolbarWidget(selectionCountLabel);
        updateSelectionCountLabel();

        createIconColumn();
        createParentNameColumn();
        createScheduleNameColumn();
        createEnabledColumn();
        createNodeNameColumn();
        createScheduleColumn();
        createBoundsColumn(dateTimeFormatter);
        createRunAsColumn();

        refresh();

        own(dataGrid.addColumnSortHandler(event -> refresh()));
        own(selectionModel.addSelectionHandler(event -> {
            setButtonState();
            updateSelectionCountLabel();
            if (event.getSelectionType().isDoubleSelect()) {
                onSingleEdit();
            }
        }));

        batchExecutionScheduleEdit.setOnApplySelection(() ->
                batchExecutionScheduleEdit.validate(valid -> {
                    if (valid) {
                        onBatchEdit(false);
                    }
                }));
        batchExecutionScheduleEdit.setOnApplyFiltered(() ->
                batchExecutionScheduleEdit.validate(valid -> {
                    if (valid) {
                        onBatchEdit(true);
                    }
                }));
    }

    private void createButtons() {
        view.getToolbar().setName("Execution schedule actions");
        singleEditButton = view.getToolbar().button(SvgImage.EDIT, "Edit Selected Schedule",
                this::onSingleEdit);
        singleEditButton.setEnabled(false);

        batchEditButton = view.getToolbar().button(SvgImage.GENERATE, "Batch Edit Schedules",
                batchExecutionScheduleEdit::show);

        filterButton = view.getToolbar().button(SvgImage.FILTER, "Filter Schedules", this::onFilter);

        clearFilterButton = view.getToolbar().button(SvgImage.CLEAR, "Clear Schedule Filter",
                this::onClearFilter);
        clearFilterButton.setEnabled(false);

        runJobNowButton = view.getToolbar().button(SvgImage.STEP_FORWARD, "Run Schedules Now",
                () -> runNowProvider.get().show(this, action ->
                        onRunNow(action == RunNowAction.APPLY_TO_FILTERED)));

        deleteButton = view.getToolbar().button(SvgImage.DELETE, "Delete Selected Schedules",
                this::onDelete);
        deleteButton.setEnabled(false);

        setButtonState();
    }

    private void createIconColumn() {
        dataGrid.addColumn(
            new OrderByColumn<ExecutionSchedule, CellIcon>(
                new IconCell(),
                ExecutionScheduleFields.PARENT_DOC_TYPE,
                false) {
                @Override
                public CellIcon getValue(final ExecutionSchedule row) {
                    return row.getOwningDoc().getType().equals(AnalyticRuleDoc.TYPE)
                            ? CellIcon.of(SvgImage.DOCUMENT_ANALYTIC_RULE, "Analytic Rule")
                            : CellIcon.of(SvgImage.DOCUMENT_REPORT, "Report");
                }
            }, "", ColumnSizeConstants.CHECKBOX_COL
        );
    }

    private void createParentNameColumn() {
        dataGrid.addResizableColumn(
            new OrderByColumn<ExecutionSchedule, String>(
                new TextCell(),
                ExecutionScheduleFields.PARENT_DOC,
                false) {
                @Override
                public String getValue(final ExecutionSchedule row) {
                    return row.getOwningDoc().getName();
                }
            }, ExecutionScheduleFields.PARENT_DOC, ColumnSizeConstants.DATE_COL
        );
    }

    private void createScheduleNameColumn() {
        final OrderByColumn<ExecutionSchedule, String> nameColumn = new OrderByColumn<ExecutionSchedule, String>(
                new TextCell(),
                ExecutionScheduleFields.NAME,
                true) {
            @Override
            public String getValue(final ExecutionSchedule row) {
                return row.getName();
            }
        };
        dataGrid.addResizableColumn(nameColumn, ExecutionScheduleFields.SCHEDULE_NAME, ColumnSizeConstants.DATE_COL);
        // By name, A to Z, ignoring case, unless another sort is chosen
        dataGrid.setDefaultSort(new ColumnSortInfo(nameColumn, true));
    }

    private void createEnabledColumn() {

        final OrderByColumn<ExecutionSchedule, CellIcon> enabledColumn = new OrderByColumn<ExecutionSchedule, CellIcon>(
            new IconCell(false),
            ExecutionScheduleFields.ENABLED,
            false) {
            @Override
            public CellIcon getValue(final ExecutionSchedule row) {
                if (row.isEnabled()) {
                    return CellIcon.of(SvgImage.TICK, "Enabled");
                } else {
                    return CellIcon.of(SvgImage.CLOSE, "Disabled");
                }
            }
        };
        dataGrid.addResizableColumn(enabledColumn, ExecutionScheduleFields.ENABLED, ColumnSizeConstants.SMALL_COL);
    }

    private void createNodeNameColumn() {

        dataGrid.addResizableColumn(
            new OrderByColumn<ExecutionSchedule, String>(
                new TextCell(),
                ExecutionScheduleFields.NODE_NAME,
                false) {
                @Override
                public String getValue(final ExecutionSchedule row) {
                    return row.getNodeName();
                }
            }, ExecutionScheduleFields.NODE_NAME, ColumnSizeConstants.MEDIUM_COL
        );
    }

    private void createScheduleColumn() {
        dataGrid.addResizableColumn(
            new OrderByColumn<ExecutionSchedule, String>(
                new TextCell(),
                ExecutionScheduleFields.SCHEDULE,
                false) {
                @Override
                public String getValue(final ExecutionSchedule row) {
                    return row.getSchedule().toString();
                }
            }, ExecutionScheduleFields.SCHEDULE, ColumnSizeConstants.DATE_COL
        );
    }

    private void createBoundsColumn(final DateTimeFormatter dateTimeFormatter) {
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
            }, ExecutionScheduleFields.BOUNDS, ColumnSizeConstants.MEDIUM_COL
        );
    }

    private void createRunAsColumn() {
        final Column<ExecutionSchedule, ExecutionSchedule> runAsCol = DataGridUtil
            .userRefColumnBuilder(
                ExecutionSchedule::getRunAsUser,
                refCells,
                true,
                DisplayType.AUTO)
            .withSorting(ExecutionScheduleFields.RUN_AS_USER, true)
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
    }

    //Handles refreshing of the pager. Call when a schedule is expected to have changed.
    private void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<ExecutionSchedule, ResultPage<ExecutionSchedule>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<ExecutionSchedule>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    if (request != null) {
                        CriteriaUtil.setRange(request, range);
                        CriteriaUtil.setSortList(request, dataGrid.getColumnSortList());
                        final ExpressionOperator expression
                                = (ExpressionOperator) formatISOExpressions(request.getExpression());
                        final ExecutionScheduleRequest formattedRequest = request.copy().expression(expression).build();

                        restFactory
                                .create(EXECUTION_SCHEDULE_RESOURCE)
                                .method(res -> res.fetchExecutionSchedule(formattedRequest))
                                .onSuccess(dataConsumer)
                                .onFailure(errorHandler)
                                .owner(ExecutionSchedulesController.this)
                                .taskMonitorFactory(view)
                                .exec();
                    }
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
        updateSelectionCountLabel();
        flushNonexistentSelections();
    }

    private void setExpression(final ExpressionOperator expression) {
        request.setExpression(expression);
        request.obtainPageRequest().setOffset(0);
        refresh();
        setButtonState();
    }

    private void setButtonState() {
        singleEditButton.setEnabled(selectionModel.getSelectedCount() == 1);
        deleteButton.setEnabled(selectionModel.getSelectedCount() > 0);

        if (request != null && request.getExpression() != null && request.getExpression().hasChildren()) {
            clearFilterButton.setEnabled(true);
        } else {
            clearFilterButton.setEnabled(false);
        }
    }

    private void flushNonexistentSelections() {
        restFactory
            .create(EXECUTION_SCHEDULE_RESOURCE)
            .method(res -> res.fetchExecutionSchedule(
                    ExecutionScheduleRequest
                            .builder()
                            .pageRequest(PageRequest.unlimited())
                            .build())
            )
            .onSuccess(executionScheduleResultPage -> {
                final List<ExecutionSchedule> executionSchedules = executionScheduleResultPage.getValues();
                for (final ExecutionSchedule selectedSchedule : selectionModel.getSelectedItems()) {
                    if (!executionSchedules.contains(selectedSchedule)) {
                        selectionModel.setSelected(selectedSchedule, false);
                    }
                }
                updateSelectionCountLabel();
            })
            .onFailure(error -> AlertEvent.fireError(this, error.getMessage(), null))
            .owner(ExecutionSchedulesController.this)
            .taskMonitorFactory(view)
            .exec();
    }

    private void updateSelectionCountLabel() {
        selectionCountLabel.setText(
                selectionModel.getSelectedCount()
                + " schedule"
                + (selectionModel.getSelectedCount() == 1 ? "" : "s")
                + " selected"
        );
    }

    private void onSingleEdit() {
        final ExecutionSchedule selected = selectionModel.getSelected();
        if (selected != null) {
            scheduledProcessEdit.show(selected, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                        .create(EXECUTION_SCHEDULE_RESOURCE)
                        .method(res -> res.updateExecutionSchedule(executionSchedule))
                        .onSuccess(updated -> {
                            refresh();
                            selectionModel.setSelected(updated);
                        })
                        .owner(ExecutionSchedulesController.this)
                        .taskMonitorFactory(this)
                        .exec();
                }
            });
        }
    }

    private void onBatchEdit(final boolean applyToFiltered) {
        if (applyToFiltered) {
            restFactory
                    .create(EXECUTION_SCHEDULE_RESOURCE)
                    .method(res -> res.fetchExecutionSchedule(
                                    request
                                            .copy()
                                            .pageRequest(PageRequest.unlimited())
                                            .build()
                            )
                    )
                    .onSuccess(result -> checkBatchEdit(result.getValues(), true))
                    .onFailure(e -> AlertEvent.fireError(this, e.getMessage(), null))
                    .owner(ExecutionSchedulesController.this)
                    .taskMonitorFactory(view)
                    .exec();
        } else {
            checkBatchEdit(selectionModel.getSelectedItems(), false);
        }
    }

    private void checkBatchEdit(final List<ExecutionSchedule> executionSchedules, final boolean applyToFiltered) {
        if (executionSchedules.isEmpty()) {
            AlertEvent.fireWarn(
                    this,
                    applyToFiltered ? "No schedules filtered." : "No schedules selected.",
                    null
            );
            return;
        }
        if (!batchExecutionScheduleEdit.isAnyBoxEnabled()) {
            AlertEvent.fireWarn(
                    this,
                    "No changes selected.",
                    null
            );
            return;
        }
        confirmBatchEdit(executionSchedules);
    }

    private void confirmBatchEdit(final List<ExecutionSchedule> executionSchedules) {
        final StringBuilder sb = new StringBuilder();
        sb.append("You are about to edit ");
        sb.append(executionSchedules.size());
        sb.append(executionSchedules.size() > 1 ? " schedules.\n" : " schedule.\n");
        sb.append("\nSetting:\n");
        sb.append(batchExecutionScheduleEdit.getEditSummary());
        sb.append("\nAre you sure?");

        ConfirmEvent.fireWarn(
                this,
                sb.toString(),
                result -> {
                    if (result) {
                        doBatchEdit(executionSchedules);
                    }
                }
        );
    }

    private void doBatchEdit(final List<ExecutionSchedule> executionSchedules) {
        final int scheduleCount = executionSchedules.size();
        final AtomicInteger successCount = new AtomicInteger(0);
        final ArrayList<ExecutionSchedule> newSchedules = new ArrayList<ExecutionSchedule>();
        executionSchedules.forEach(executionSchedule -> {
            restFactory
                    .create(EXECUTION_SCHEDULE_RESOURCE)
                    .method(res -> res.updateExecutionSchedule(
                            batchExecutionScheduleEdit.getUpdatedExecutionSchedule(executionSchedule)
                    ))
                    .onSuccess(result -> {
                        if (selectionModel.isSelected(executionSchedule)) {
                            newSchedules.add(result);
                        }
                        if (successCount.incrementAndGet() == scheduleCount) {
                            selectionModel.setSelectedItems(newSchedules);
                            refresh();
                            AlertEvent.fireInfo(
                                    this,
                                    "Successfully changed.",
                                    null);
                        }
                    })
                    .onFailure(cause ->
                        AlertEvent.fireError(
                                this,
                                "Failed to change. " + cause.getMessage(),
                                null)
                    )
                    .owner(ExecutionSchedulesController.this)
                    .taskMonitorFactory(this)
                    .exec();
        });
    }

    private void onFilter() {
        filterDialogProvider.get().show(this,
                "Filter Schedules",
                PopupSize.resizable(1_000, 600),
                request.getExpression(),
                ExecutionScheduleFields.DOC_REF,
                ExecutionScheduleFields.getFields(),
                (expression, e) -> {
                    expressionValidator.validateExpression(
                            ExecutionSchedulesController.this,
                            ExecutionScheduleFields.getFields(),
                            expression, validExpression -> {
                                if (!validExpression.equals(request.getExpression())) {
                                    setExpression(validExpression);
                                    selectionModel.clear();
                                    e.hide();
                                } else {
                                    // Nothing changed!
                                    e.hide();
                                }
                            },
                            // If it isn't valid, let the user change it or cancel
                            e::reset,
                            this);
                });
    }

    private void onClearFilter() {
        final ExpressionOperator expression = new ExpressionOperator(true, Op.AND, new ArrayList<>());
        setExpression(expression);
    }

    private void onRunNow(final boolean applyToFiltered) {
        if (applyToFiltered) {
            final ExecutionScheduleRequest requestAll = request.copy().pageRequest(PageRequest.unlimited()).build();
            restFactory.create(EXECUTION_SCHEDULE_RESOURCE)
                    .method(resource -> resource.fetchExecutionSchedule(requestAll))
                    .onSuccess(result -> checkRunNow(result.getValues(), true))
                    .onFailure(cause -> AlertEvent.fireError(this, cause.getMessage(), null))
                    .owner(ExecutionSchedulesController.this)
                    .taskMonitorFactory(this)
                    .exec();
        } else {
            checkRunNow(selectionModel.getSelectedItems(), false);
        }
    }

    private void checkRunNow(final List<ExecutionSchedule> executionSchedules, final boolean applyToFiltered) {
        if (executionSchedules.isEmpty()) {
            AlertEvent.fireWarn(
                    this,
                    applyToFiltered ? "No schedules filtered." : "No schedules selected.",
                    null
            );
            return;
        }
        final List<ExecutionSchedule> enabledSchedules = new ArrayList<>();
        int disabledCount = 0;
        for (final ExecutionSchedule schedule : executionSchedules) {
            if (schedule.isEnabled()) {
                enabledSchedules.add(schedule);
            } else {
                disabledCount++;
            }
        }

        if (enabledSchedules.isEmpty()) {
            AlertEvent.fireError(
                    this,
                    applyToFiltered
                            ? "None of the filtered schedules are enabled."
                            : "None of the selected schedules are enabled.",
                    null);
        } else if (disabledCount > 0) {
            AlertEvent.fireWarn(
                    this,
                    applyToFiltered
                            ? "Some of the filtered schedules are disabled and will not be run."
                            : "Some of the selected schedules are disabled and will not be run.",
                    () -> confirmRunNow(enabledSchedules));
        } else {
            confirmRunNow(enabledSchedules);
        }
    }

    private void confirmRunNow(final List<ExecutionSchedule> executionSchedules) {
        final String warningMessage = "You are about to force "
                                      + executionSchedules.size()
                                      + " execution schedule"
                                      + (executionSchedules.size() > 1 ? "s" : "")
                                      + " to run now."
                                      + "\nSelected schedules will immediately be processed until all are up to date, "
                                      + "which may take some time for large selections."
                                      + "\n\nAre you sure?";
        ConfirmEvent.fire(this, warningMessage, result -> {
            if (result) {
                doRunNow(executionSchedules);
            }
        });
    }

    private void doRunNow(final List<ExecutionSchedule> executionSchedules) {
        restFactory.create(EXECUTION_SCHEDULE_RESOURCE)
            .method(resource -> resource.executeSchedulesNow(executionSchedules))
            .owner(ExecutionSchedulesController.this)
            .taskMonitorFactory(this)
            .exec();
    }

    private void onDelete() {
        ConfirmEvent.fireWarn(
                this,
                "You are about to delete "
                + selectionModel.getSelectedCount()
                + " schedule"
                + (selectionModel.getSelectedCount() > 1 ? "s" : "")
                + ".\n\nAre you sure?",
                result -> {
                    if (result) {
                        doDelete();
                    }
                }
        );
    }

    private void doDelete() {
        restFactory.create(EXECUTION_SCHEDULE_RESOURCE)
            .method(resource -> resource.deleteExecutionSchedules(selectionModel.getSelectedItems()))
            .onSuccess(result -> {
                AlertEvent.fireInfo(
                    this,
                    "Deleted "
                    + selectionModel.getSelectedCount()
                    + " schedule"
                    + (selectionModel.getSelectedCount() > 1 ? "s" : "")
                    + " successfully.",
                    null
                );
                refresh();
            })
            .onFailure(cause -> AlertEvent.fireError(this, cause.getMessage(), null))
            .owner(ExecutionSchedulesController.this)
            .taskMonitorFactory(this)
            .exec();

    }

    private ExpressionItem formatISOExpressions(final ExpressionItem item) {
        if (item == null) {
            return null;
        }
        if (item instanceof final ExpressionTerm term) {
            if (term.getField().equals(ExecutionScheduleFields.FIELD_START_TIME.getFldName())
                || term.getField().equals(ExecutionScheduleFields.FIELD_END_TIME.getFldName())) {
                return term.copy().value(ClientDateUtil.fromISOString(term.getValue()).toString()).build();
            }
            return term;
        }
        if (item instanceof final ExpressionOperator operator) {
            final ArrayList<ExpressionItem> newChildren = new ArrayList<>();
            // An operator with no terms has no children
            for (final ExpressionItem child : NullSafe.list(operator.getChildren())) {
                newChildren.add(formatISOExpressions(child));
            }
            return operator.copy().children(newChildren).build();
        }
        return null;
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.HISTORY;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Execution Schedule Manager";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

}
