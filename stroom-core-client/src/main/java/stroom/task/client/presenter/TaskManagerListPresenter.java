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

package stroom.task.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.node.client.NodeManager;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.client.SvgPresets;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TaskManagerListPresenter
        extends MyPresenterWidget<PagerView>
        implements HasDataSelectionHandlers<Set<String>>, Refreshable {

    private static final TaskResource TASK_RESOURCE = GWT.create(TaskResource.class);

    private final DateTimeFormatter dateTimeFormatter;
    private final FindTaskProgressCriteria criteria = new FindTaskProgressCriteria();
    private final FindTaskProgressRequest request = new FindTaskProgressRequest(criteria);
    private final Set<TaskProgress> selectedTaskProgress = new HashSet<>();
    private final TooltipPresenter tooltipPresenter;
    private final RestFactory restFactory;
    private final NodeManager nodeManager;
    private final NameFilterTimer timer = new NameFilterTimer();
    private final Map<String, List<TaskProgress>> responseMap = new HashMap<>();
    private final Map<String, List<String>> errorMap = new HashMap<>();
    private final RestDataProvider<TaskProgress, TaskProgressResponse> dataProvider;
    private final MyDataGrid<TaskProgress> dataGrid;

    private final ButtonView expandAllButton;
    private final ButtonView collapseAllButton;
    private final ButtonView warningsButton;

    private String currentWarnings;
    private Column<TaskProgress, Expander> expanderColumn;

    private final TaskManagerTreeAction treeAction = new TaskManagerTreeAction();
    private final DelayedUpdate delayedUpdate;

    private Range range;
    private Consumer<TaskProgressResponse> dataConsumer;

    @Inject
    public TaskManagerListPresenter(final EventBus eventBus,
                                    final PagerView view,
                                    final TooltipPresenter tooltipPresenter,
                                    final RestFactory restFactory,
                                    final NodeManager nodeManager,
                                    final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.tooltipPresenter = tooltipPresenter;
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;
        this.criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, true, false);
        this.dateTimeFormatter = dateTimeFormatter;

        dataGrid = new MyDataGrid<>(1000);
        view.setDataWidget(dataGrid);

        final ButtonView terminateButton = getView().addButton(SvgPresets.DELETE.with("Terminate Task", true));
        terminateButton.addClickHandler(event -> endSelectedTask());

        expandAllButton = getView().addButton(SvgPresets.EXPAND_DOWN.with("Expand All", false));
        collapseAllButton = getView().addButton(SvgPresets.COLLAPSE_UP.with("Collapse All", false));
        warningsButton = getView().addButton(SvgPresets.ALERT.title("Show Warnings"));
        warningsButton.setVisible(false);

        updateButtonStates();

        initTableColumns();

        delayedUpdate = new DelayedUpdate(this::update);
        dataProvider = new RestDataProvider<TaskProgress, TaskProgressResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<TaskProgressResponse> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                TaskManagerListPresenter.this.range = range;
                TaskManagerListPresenter.this.dataConsumer = dataConsumer;
                delayedUpdate.reset();
                fetchNodes(range, dataConsumer, throwableConsumer);
            }
        };
        dataProvider.addDataDisplay(dataGrid);

        // Handle use of the expander column.
        dataProvider.setTreeRowHandler(new TreeRowHandler<TaskProgress>(treeAction, dataGrid, expanderColumn));

        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
                // As we get data async from all nodes we can't be sure when we have finished so
                // need to clear the expandAllRequestState prior to fetching
                treeAction.resetExpandAllRequestState();
                dataProvider.refresh();
            }
        });
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(expandAllButton.addClickHandler(event -> {
            treeAction.expandAll();
            dataProvider.refresh();
            updateButtonStates();
        }));

        registerHandler(collapseAllButton.addClickHandler(event -> {
            treeAction.collapseAll();
            dataProvider.refresh();
            updateButtonStates();
        }));

        registerHandler(warningsButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                showWarnings();
            }
        }));
    }

    private void showWarnings() {
        if (currentWarnings != null && !currentWarnings.isEmpty()) {
            AlertEvent.fireWarn(this, "The following warnings have been created while fetching tasks:",
                    currentWarnings, null);
        }
    }

    public void setErrors(final String errors) {
        currentWarnings = errors;
        warningsButton.setVisible(currentWarnings != null && !currentWarnings.isEmpty());
    }

    private void updateButtonStates() {
        expandAllButton.setEnabled(treeAction.hasCollapsedRows());
        collapseAllButton.setEnabled(treeAction.hasExpandedRows());
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Select Column
        final Column<TaskProgress, TickBoxState> column = new Column<TaskProgress, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final TaskProgress taskProgress) {
                if (TaskProgressUtil.DEAD_TASK_NAME.equals(taskProgress.getTaskName())) {
                    // Dead tasks cannot be deleted so don't show a checkbox
                    // They are only there to show that an orphaned child task was a child of something
                    return null;
                } else {
                    return TickBoxState.fromBoolean(selectedTaskProgress.contains(taskProgress));
                }
            }
        };

        dataGrid.addColumn(column, "", ColumnSizeConstants.CHECKBOX_COL);

        // Expander column.
        expanderColumn = new Column<TaskProgress, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final TaskProgress row) {
                return buildExpander(row);
            }
        };
        dataGrid.addColumn(expanderColumn, "");

        expanderColumn.setFieldUpdater((index, row, value) -> {
            treeAction.setRowExpanded(row, !value.isExpanded());
            // As we get data async from all nodes we can't be sure when we have finished so
            // need to clear the expandAllRequestState prior to fetching
            treeAction.resetExpandAllRequestState();
            updateButtonStates();
            refresh();
        });

        final InfoColumn<TaskProgress> furtherInfoColumn = new InfoColumn<TaskProgress>() {
            @Override
            protected void showInfo(final TaskProgress row, final int x, final int y) {
                final SafeHtml tooltipHtml = buildTooltipHtml(row);
                tooltipPresenter.show(tooltipHtml, x, y);
            }
        };
        dataGrid.addColumn(furtherInfoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        // Add Handlers
        column.setFieldUpdater((index, object, value) -> {
            if (value.toBoolean()) {
                selectedTaskProgress.add(object);
            } else {
                // De-selecting one and currently matching all ?
                selectedTaskProgress.remove(object);
            }
//            setButtonsEnabled();
        });

        // Node.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(getColouredCellFunc(taskProgress ->
                                taskProgress.getNodeName() != null
                                        ? taskProgress.getNodeName()
                                        : "?"))
                        .withSorting(FindTaskProgressCriteria.FIELD_NODE)
                        .build(),
                FindTaskProgressCriteria.FIELD_NODE,
                150);

        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(getColouredCellFunc(TaskProgress::getTaskName))
                        .withSorting(FindTaskProgressCriteria.FIELD_NAME)
                        .build(),
                FindTaskProgressCriteria.FIELD_NAME,
                150);

        // User.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(getColouredCellFunc(TaskProgress::getUserName))
                        .withSorting(FindTaskProgressCriteria.FIELD_USER)
                        .build(),
                FindTaskProgressCriteria.FIELD_USER,
                80);

        // Submit Time.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(getColouredCellFunc(taskProgress ->
                                dateTimeFormatter.format(taskProgress.getSubmitTimeMs())))
                        .withSorting(FindTaskProgressCriteria.FIELD_SUBMIT_TIME)
                        .build(),
                FindTaskProgressCriteria.FIELD_SUBMIT_TIME,
                ColumnSizeConstants.DATE_COL);

        // Age.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(getColouredCellFunc(taskProgress ->
                                ModelStringUtil.formatDurationString(taskProgress.getAgeMs())))
                        .withSorting(FindTaskProgressCriteria.FIELD_AGE)
                        .build(),
                FindTaskProgressCriteria.FIELD_AGE,
                ColumnSizeConstants.SMALL_COL);

        // Info
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(getColouredCellFunc(TaskProgress::getTaskInfo))
                        .withSorting(FindTaskProgressCriteria.FIELD_INFO)
                        .build(),
                FindTaskProgressCriteria.FIELD_INFO,
                1000);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    private SafeHtml buildTooltipHtml(final TaskProgress row) {
        return TooltipUtil.builder()
                .addTwoColTable(tableBuilder -> {
                    tableBuilder.addHeaderRow("Task")
                            .addRow("Name", row.getTaskName())
                            .addRow("User", row.getUserName())
                            .addRow("Submit Time", dateTimeFormatter.format(row.getSubmitTimeMs()))
                            .addRow("Age", ModelStringUtil.formatDurationString(row.getAgeMs()))
                            .addBlankRow()
                            .addRow("Id", row.getId())
                            .addRow("Thread Name", row.getThreadName());

                    if (row.getId() != null) {
                        final TaskId parentId = row.getId().getParentId();
                        if (parentId != null) {
                            tableBuilder.addRow("Parent Id", parentId);
                        }
                    }
                    return tableBuilder.build();
                })
                .addLine(row.getTaskInfo())
                .build();
    }

    private Function<TaskProgress, SafeHtml> getColouredCellFunc(final Function<TaskProgress, String> extractor) {
        return DataGridUtil.highlightedCellExtractor(
                extractor,
                TaskProgress::isMatchedInFilter);
    }

    private Expander buildExpander(final TaskProgress row) {
        return row.getExpander();
    }

    @Override
    public void refresh() {
        treeAction.resetExpandAllRequestState();
        dataProvider.refresh();
    }

    public void fetchNodes(final Range range,
                           final Consumer<TaskProgressResponse> dataConsumer,
                           final Consumer<Throwable> throwableConsumer) {
        nodeManager.listAllNodes(
                nodeNames -> fetchTasksForNodes(range, dataConsumer, nodeNames),
                throwableConsumer);
    }

    private void fetchTasksForNodes(final Range range,
                                    final Consumer<TaskProgressResponse> dataConsumer,
                                    final List<String> nodeNames) {
        responseMap.clear();
        for (final String nodeName : nodeNames) {
            final Rest<TaskProgressResponse> rest = restFactory.create();
            rest
                    .onSuccess(response -> {
                        responseMap.put(nodeName, response.getValues());
                        errorMap.put(nodeName, response.getErrors());
                        delayedUpdate.update();
                    })
                    .onFailure(throwable -> {
                        responseMap.remove(nodeName);
                        errorMap.put(nodeName, Collections.singletonList(throwable.getMessage()));
                        delayedUpdate.update();
                    })
                    .call(TASK_RESOURCE)
                    .find(nodeName, request);
        }
    }

    private void update() {
        // Combine data from all nodes.
        final ResultPage<TaskProgress> resultPage = TaskProgressUtil.combine(
                range,
                criteria,
                responseMap.values(),
                treeAction);

        final HashSet<TaskProgress> currentTaskSet = new HashSet<TaskProgress>(resultPage.getValues());
        selectedTaskProgress.retainAll(currentTaskSet);

        final String allErrors = errorMap.entrySet()
                .stream()
                .flatMap(r -> r.getValue().stream().map(message -> r.getKey() + ": " + message))
                .collect(Collectors.joining("\n"));
        setErrors(allErrors);

        final TaskProgressResponse response = new TaskProgressResponse(
                resultPage.getValues(),
                null,
                resultPage.getPageResponse());

        dataConsumer.accept(response);
        updateButtonStates();
    }

    /**
     * This sets the name filter to be used when fetching items. This method
     * returns false is the filter is set to the same value that is already set.
     *
     * @param name
     * @return
     */
    public void setNameFilter(final String name) {
        timer.setName(name);
        timer.cancel();
        timer.schedule(300);
    }

    private void endSelectedTask() {
        final Set<TaskProgress> cloneSelectedTaskProgress = new HashSet<>(selectedTaskProgress);
        for (final TaskProgress taskProgress : cloneSelectedTaskProgress) {
            doTerminate(taskProgress);
        }
        refresh();
    }

    private void doTerminate(final TaskProgress taskProgress) {
        final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
        findTaskCriteria.addId(taskProgress.getId());
        final TerminateTaskProgressRequest request = new TerminateTaskProgressRequest(findTaskCriteria);
        restFactory.create()
                .call(TASK_RESOURCE)
                .terminate(taskProgress.getNodeName(), request);
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Set<String>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    private class NameFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            String filter = name;
            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            if (!Objects.equals(filter, criteria.getNameFilter())) {

                // This is a new filter so reset all the expander states
                treeAction.reset();
                criteria.setNameFilter(filter);
                refresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
