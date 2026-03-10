/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleFields;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionScheduleResource;
import stroom.analytics.shared.ScheduleBounds;
import stroom.cell.info.client.SvgCell;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.ExpressionPresenter;
import stroom.data.client.presenter.ExpressionValidator;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.IconColour;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
//

public class ExecutionScheduleManager
        extends ContentTabPresenter<PagerView> {

    private static final ExecutionScheduleResource EXECUTION_SCHEDULE_RESOURCE =
            GWT.create(ExecutionScheduleResource.class);

    private final MyDataGrid<ExecutionSchedule> dataGrid;
    private final MultiSelectionModelImpl<ExecutionSchedule> selectionModel;
    private RestDataProvider<ExecutionSchedule, ResultPage<ExecutionSchedule>> dataProvider;
    private final RestFactory restFactory;
    private final ExecutionScheduleRequest request;
    private final ScheduledProcessEditPresenter scheduledProcessEditPresenter;
    private final BatchExecutionScheduleEditPresenter batchExecutionScheduleEditPresenter;
    private final ExecutionScheduleRunNowPresenter executionScheduleRunNowPresenter;
    private final Provider<ExpressionPresenter> expressionPresenterProvider;
    private final ExpressionValidator expressionValidator;

    private ButtonView singleEditButton;
    private ButtonView batchEditButton;
    private ButtonView filterButton;
    private ButtonView clearFilterButton;
    private ButtonView runJobNowButton;
    private ButtonView deleteButton;

//    private final ScheduledQueryAnalyticExecutor scheduledQueryExecutor;

    @Inject
    public ExecutionScheduleManager(final EventBus eventBus,
                                    final PagerView view,
                                    final DateTimeFormatter dateTimeFormatter,
                                    final ClientSecurityContext securityContext,
                                    final RestFactory restFactory,
                                    final ScheduledProcessEditPresenter scheduledProcessEditPresenter,
                                    final BatchExecutionScheduleEditPresenter batchExecutionScheduleEditPresenter,
                                    final ExecutionScheduleRunNowPresenter executionScheduleRunNowPresenter,
                                    final Provider<ExpressionPresenter> expressionPresenterProvider,
                                    final ExpressionValidator expressionValidator) {
        super(eventBus, view);
        this.restFactory = restFactory;

        this.scheduledProcessEditPresenter = scheduledProcessEditPresenter;
        this.batchExecutionScheduleEditPresenter = batchExecutionScheduleEditPresenter;
        this.executionScheduleRunNowPresenter = executionScheduleRunNowPresenter;
        this.expressionPresenterProvider = expressionPresenterProvider;
        this.expressionValidator = expressionValidator;


        dataGrid = new MyDataGrid<>(this);
        selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<ExecutionSchedule> selectionEventManager
            = new DataGridSelectionEventManager<>(
                dataGrid,
                selectionModel,
                true);

        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        view.setDataWidget(dataGrid);

        final CriteriaFieldSort defaultSort = new CriteriaFieldSort(ExecutionScheduleFields.NAME, true, true);
        request = ExecutionScheduleRequest.builder().sortList(Collections.singletonList(defaultSort)).build();

        createButtons();

        final HTML label = new HTML();
        label.addStyleName("pager-paging pager-label");
        view.addInfoLabel(label);
        updateSelectionCountLabel();

        createIconColumn();
        createParentNameColumn();
        createScheduleNameColumn();
        createEnabledColumn();
        createNodeNameColumn();
        createScheduleColumn();
        createBoundsColumn(dateTimeFormatter);
        createRunAsColumn(securityContext);

        refresh();
    }



    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
        registerHandler(selectionModel.addSelectionHandler(event -> {
            setButtonState();
            updateSelectionCountLabel();
            if (event.getSelectionType().isDoubleSelect()) {
                onSingleEdit();
            }
        }));

        batchExecutionScheduleEditPresenter.getView().getApplySelectionButton().addClickHandler(
                clickEvent -> {
                    batchExecutionScheduleEditPresenter.validate(valid -> {
                        if (valid) {
                            onBatchEdit(false);
                        }
                    });
                }
        );

        batchExecutionScheduleEditPresenter.getView().getApplyFilteredButton().addClickHandler(
                clickEvent -> {
                    batchExecutionScheduleEditPresenter.validate(valid -> {
                        if (valid) {
                            onBatchEdit(true);
                        }
                    });
                }
        );

        executionScheduleRunNowPresenter.getView().getApplySelectionButton().addClickHandler(
                clickEvent -> onRunNow(false)
        );

        executionScheduleRunNowPresenter.getView().getApplyFilteredButton().addClickHandler(
                clickEvent -> onRunNow(true)
        );
    }


    private void createButtons() {
        singleEditButton = this.getView().addButton(new Preset(
                SvgImage.EDIT,
                "Edit Selected Schedule",
                false));
        registerHandler(singleEditButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onSingleEdit();
            }
        }));

        batchEditButton = this.getView().addButton(new Preset(
                SvgImage.GENERATE,
                "Batch Edit Schedules",
                true));
        registerHandler(batchEditButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                batchExecutionScheduleEditPresenter.show();
            }
        }));

        filterButton = this.getView().addButton(new Preset(
                SvgImage.FILTER,
                "Filter Schedules",
                true));
        registerHandler(filterButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onFilter();
            }
        }));

        clearFilterButton = this.getView().addButton(new Preset(
                SvgImage.CLEAR,
                "Clear Filter",
                false));
        registerHandler(clearFilterButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onClearFilter();
            }
        }));

        runJobNowButton = this.getView().addButton(new Preset(
                SvgImage.STEP_FORWARD,
                "Run Schedules Now",
                true));
        registerHandler(runJobNowButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                executionScheduleRunNowPresenter.show();
            }
        }));

        deleteButton = this.getView().addButton(new Preset(
                SvgImage.DELETE,
                "Delete Schedules",
                false));
        registerHandler(deleteButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onDelete();
            }
        }));

        setButtonState();
    }

    private void createIconColumn() {
        dataGrid.addColumn(
            new OrderByColumn<ExecutionSchedule, Preset>(
                new SvgCell(),
                ExecutionScheduleFields.PARENT_DOC_TYPE,
                false) {
                @Override
                public Preset getValue(final ExecutionSchedule row) {
                    return row.getOwningDoc().getType().equals(AnalyticRuleDoc.TYPE)
                            ? new Preset(SvgImage.DOCUMENT_ANALYTIC_RULE, "Analytic Rule", true)
                            : new Preset(SvgImage.DOCUMENT_REPORT, "Report", true);
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
        dataGrid.addResizableColumn(
            new OrderByColumn<ExecutionSchedule, String>(
                new TextCell(),
                ExecutionScheduleFields.NAME,
                false) {
                @Override
                public String getValue(final ExecutionSchedule row) {
                    return row.getName();
                }
            }, ExecutionScheduleFields.SCHEDULE_NAME, ColumnSizeConstants.DATE_COL
        );
    }

    private void createEnabledColumn() {

        final OrderByColumn<ExecutionSchedule, Preset> enabledColumn = new OrderByColumn<ExecutionSchedule, Preset>(
            new SvgCell(false),
            ExecutionScheduleFields.ENABLED,
            false) {
            @Override
            public Preset getValue(final ExecutionSchedule row) {
                if (row.isEnabled()) {
                    return new Preset(SvgImage.TICK, "Enabled", true);
                } else {
                    return new Preset(SvgImage.CLOSE, "Disabled", true);
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
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void createRunAsColumn(final ClientSecurityContext securityContext) {
        final Column<ExecutionSchedule, ExecutionSchedule> runAsCol = DataGridUtil
            .userRefColumnBuilder(
                ExecutionSchedule::getRunAsUser,
                getEventBus(),
                securityContext,
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
                                .taskMonitorFactory(getView())
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
            .taskMonitorFactory(getView())
            .exec();
    }


    private void updateSelectionCountLabel() {
        this.getView().getInfoLabel(0).setText(
                selectionModel.getSelectedCount()
                + " schedule"
                + (selectionModel.getSelectedCount() == 1 ? "" : "s")
                + " selected"
        );
    }

    private void onSingleEdit() {
        final ExecutionSchedule selected = selectionModel.getSelected();
        if (selected != null) {
            scheduledProcessEditPresenter.setTaskMonitorFactory(this);
            scheduledProcessEditPresenter.show(selected, executionSchedule -> {
                if (executionSchedule != null) {
                    restFactory
                        .create(EXECUTION_SCHEDULE_RESOURCE)
                        .method(res -> res.updateExecutionSchedule(executionSchedule))
                        .onSuccess(updated -> {
                            refresh();
                            selectionModel.setSelected(updated);
                        })
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
                    .taskMonitorFactory(getView())
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
        if (!batchExecutionScheduleEditPresenter.getView().isAnyBoxEnabled()) {
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
        sb.append(batchExecutionScheduleEditPresenter.getView().getEditSummary());
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
                            batchExecutionScheduleEditPresenter.getView().getUpdatedExecutionSchedule(executionSchedule)
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
                    .taskMonitorFactory(this)
                    .exec();
        });
    }

    private void onFilter() {
        final ExpressionPresenter presenter = expressionPresenterProvider.get();
        final HidePopupRequestEvent.Handler HidePopupRequestEventHandler = e -> {
            if (e.isOk()) {
                final ExpressionOperator expression = presenter.write();
                expressionValidator.validateExpression(
                        ExecutionScheduleManager.this,
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
                        }, this);
            } else {
                e.hide();
            }
        };
        presenter.read(request.getExpression(),
            ExecutionScheduleFields.DOC_REF,
            ExecutionScheduleFields.getFields());

        presenter.getWidget().getElement().addClassName("default-min-sizes");
        final PopupSize popupSize = PopupSize.resizable(1_000, 600);
        ShowPopupEvent.builder(presenter)
            .popupType(PopupType.OK_CANCEL_DIALOG)
            .popupSize(popupSize)
            .caption("Filter Schedules")
            .onShow(e -> presenter.focus())
            .onHideRequest(HidePopupRequestEventHandler)
            .fire();
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
            for (final ExpressionItem child : operator.getChildren()) {
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
        return "ExecutionScheduleManager";
    }

}
