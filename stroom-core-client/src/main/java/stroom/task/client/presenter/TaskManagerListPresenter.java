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

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.NodeResource;
import stroom.node.shared.NodeStatusResult;
import stroom.processor.shared.ProcessorListRow;
import stroom.svg.client.SvgPresets;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Sort.Direction;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class TaskManagerListPresenter
        extends MyPresenterWidget<DataGridView<TaskProgress>>
        implements HasDataSelectionHandlers<Set<String>>, Refreshable, ColumnSortEvent.Handler {
    private static final NodeResource NODE_RESOURCE = GWT.create(NodeResource.class);
    private static final TaskResource TASK_RESOURCE = GWT.create(TaskResource.class);

    private final FindTaskProgressCriteria criteria = new FindTaskProgressCriteria();
    private final FindTaskProgressRequest request = new FindTaskProgressRequest(criteria);
    private final Set<TaskProgress> selectedTaskProgress = new HashSet<>();
    private final Set<TaskProgress> requestedTerminateTaskProgress = new HashSet<>();
    private final TooltipPresenter tooltipPresenter;
    private final RestFactory restFactory;
    private final NameFilterTimer timer = new NameFilterTimer();
    private final Map<String, List<TaskProgress>> responseMap = new HashMap<>();
    private final RestDataProvider<TaskProgress, TaskProgressResponse> dataProvider;

    private FetchNodeStatusResponse fetchNodeStatusResponse;
    private Column<TaskProgress, Expander> expanderColumn;

    @Inject
    public TaskManagerListPresenter(final EventBus eventBus,
                                    final TooltipPresenter tooltipPresenter,
                                    final RestFactory restFactory) {
        super(eventBus, new DataGridViewImpl<>(false, 1000));
        this.tooltipPresenter = tooltipPresenter;
        this.restFactory = restFactory;
        this.criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, Direction.DESCENDING, false);

        final ButtonView terminateButton = getView().addButton(SvgPresets.DELETE);
        terminateButton.addClickHandler(event -> endSelectedTask());
        terminateButton.setEnabled(true);

        getView().addColumnSortHandler(this);

        initTableColumns();

        dataProvider = new RestDataProvider<TaskProgress, TaskProgressResponse>(eventBus) {
            @Override
            protected void exec(final Consumer<TaskProgressResponse> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                fetchNodes(dataConsumer, throwableConsumer);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());

        // Handle use of the expander column.
        dataProvider.setTreeRowHandler(new TreeRowHandler<TaskProgress>(request, getView(), expanderColumn));
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        final TickBoxCell.MarginAppearance tickBoxAppearance = GWT.create(TickBoxCell.MarginAppearance.class);

        // Select Column
        final Column<TaskProgress, TickBoxState> column = new Column<TaskProgress, TickBoxState>(
                TickBoxCell.create(tickBoxAppearance, false, false)) {
            @Override
            public TickBoxState getValue(final TaskProgress object) {
                return TickBoxState.fromBoolean(selectedTaskProgress.contains(object));
            }
        };

        getView().addColumn(column, "", ColumnSizeConstants.CHECKBOX_COL);

        // Expander column.
        expanderColumn = new Column<TaskProgress, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final TaskProgress row) {
                return buildExpander(row);
            }
        };
        getView().addColumn(expanderColumn, "");

        expanderColumn.setFieldUpdater((index, row, value) -> {
            request.setRowExpanded(row, !value.isExpanded());
            refresh();
        });

        final InfoColumn<TaskProgress> furtherInfoColumn = new InfoColumn<TaskProgress>() {
            @Override
            protected void showInfo(final TaskProgress row, final int x, final int y) {
                final StringBuilder html = new StringBuilder();
                TooltipUtil.addHeading(html, "Task");
                TooltipUtil.addRowData(html, "Name", row.getTaskName());
                TooltipUtil.addRowData(html, "User", row.getUserName());
                TooltipUtil.addRowData(html, "Submit Time", ClientDateUtil.toISOString(row.getSubmitTimeMs()));
                TooltipUtil.addRowData(html, "Age", ModelStringUtil.formatDurationString(row.getAgeMs()));
                TooltipUtil.addBreak(html);
                TooltipUtil.addRowData(html, "Id", row.getId());
                TooltipUtil.addRowData(html, "Thread Name", row.getThreadName());

                if (row.getId() != null) {
                    final TaskId parentId = row.getId().getParentId();
                    if (parentId != null) {
                        TooltipUtil.addRowData(html, "Parent Id", parentId);
                    }
                }

                TooltipUtil.addRowData(html, row.getTaskInfo());

                tooltipPresenter.setHTML(html.toString());

                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(TaskManagerListPresenter.this, tooltipPresenter, PopupType.POPUP,
                        popupPosition, null);
            }
        };
        getView().addColumn(furtherInfoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

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
        final Column<TaskProgress, String> nodeColumn = new OrderByColumn<TaskProgress, String>(
                new TextCell(), FindTaskProgressCriteria.FIELD_NODE, false) {
            @Override
            public String getValue(final TaskProgress value) {
                if (value.getNodeName() != null) {
                    return value.getNodeName();
                }
                return "?";
            }
        };
        getView().addResizableColumn(nodeColumn, FindTaskProgressCriteria.FIELD_NODE, 150);

        // Name.
        final Column<TaskProgress, String> nameColumn = new OrderByColumn<TaskProgress, String>(
                new TextCell(), FindTaskProgressCriteria.FIELD_NAME, false) {
            @Override
            public String getValue(final TaskProgress value) {
                return value.getTaskName();
            }
        };
        getView().addResizableColumn(nameColumn, FindTaskProgressCriteria.FIELD_NAME, 150);

        // User.
        final Column<TaskProgress, String> userColumn = new OrderByColumn<TaskProgress, String>(
                new TextCell(), FindTaskProgressCriteria.FIELD_USER, false) {
            @Override
            public String getValue(final TaskProgress value) {
                return value.getUserName();
            }
        };
        getView().addResizableColumn(userColumn, FindTaskProgressCriteria.FIELD_USER, 80);

        // Submit Time.
        final Column<TaskProgress, String> submitTimeColumn = new OrderByColumn<TaskProgress, String>(
                new TextCell(), FindTaskProgressCriteria.FIELD_SUBMIT_TIME, false) {
            @Override
            public String getValue(final TaskProgress value) {
                return ClientDateUtil.toISOString(value.getSubmitTimeMs());
            }
        };
        getView().addResizableColumn(submitTimeColumn, FindTaskProgressCriteria.FIELD_SUBMIT_TIME, ColumnSizeConstants.DATE_COL);

        // Age.
        final Column<TaskProgress, String> ageColumn = new OrderByColumn<TaskProgress, String>(
                new TextCell(), FindTaskProgressCriteria.FIELD_AGE, false) {
            @Override
            public String getValue(final TaskProgress value) {
                return ModelStringUtil.formatDurationString(value.getAgeMs());
            }
        };
        getView().addResizableColumn(ageColumn, FindTaskProgressCriteria.FIELD_AGE, ColumnSizeConstants.SMALL_COL);

        // Info.
        final Column<TaskProgress, String> infoColumn = new OrderByColumn<TaskProgress, String>(
                new TextCell(), FindTaskProgressCriteria.FIELD_INFO, false) {
            @Override
            public String getValue(final TaskProgress value) {
                return value.getTaskInfo();
            }
        };
        getView().addResizableColumn(infoColumn, FindTaskProgressCriteria.FIELD_INFO, 1000);
        getView().addEndColumn(new EndColumn<>());
    }

    private Expander buildExpander(final TaskProgress row) {
        return row.getExpander();
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    public void fetchNodes(final Consumer<TaskProgressResponse> dataConsumer,
                           final Consumer<Throwable> throwableConsumer) {
        if (fetchNodeStatusResponse == null) {
            final Rest<FetchNodeStatusResponse> rest = restFactory.create();
            rest.onSuccess(fetchNodeStatusResponse -> {
                // Store node list for future queries.
                this.fetchNodeStatusResponse = fetchNodeStatusResponse;
                fetchTasksForNodes(dataConsumer, throwableConsumer, fetchNodeStatusResponse);
            }).onFailure(throwableConsumer).call(NODE_RESOURCE).list();
        } else {
            fetchTasksForNodes(dataConsumer, throwableConsumer, fetchNodeStatusResponse);
        }
    }

    private void fetchTasksForNodes(final Consumer<TaskProgressResponse> dataConsumer,
                                    final Consumer<Throwable> throwableConsumer,
                                    final FetchNodeStatusResponse fetchNodeStatusResponse) {
        for (final NodeStatusResult nodeStatusResult : fetchNodeStatusResponse.getValues()) {
            final String nodeName = nodeStatusResult.getNode().getName();
            final Rest<TaskProgressResponse> rest = restFactory.create();
            rest
                    .onSuccess(response -> {
                        responseMap.put(nodeName, response.getValues());
                        combineNodeTasks(dataConsumer, throwableConsumer);
                    })
                    .onFailure(throwable -> {
                        responseMap.remove(nodeName);
                        combineNodeTasks(dataConsumer, throwableConsumer);
                    })
                    .call(TASK_RESOURCE).find(nodeName, request);
        }
    }

    private void combineNodeTasks(final Consumer<TaskProgressResponse> dataConsumer,
                                  final Consumer<Throwable> throwableConsumer) {
        // Combine data from all nodes.
        final List<TaskProgress> list = TaskProgressUtil.combine(criteria, responseMap.values());

        final HashSet<TaskProgress> currentTaskSet = new HashSet<>(list);
        selectedTaskProgress.retainAll(currentTaskSet);
        requestedTerminateTaskProgress.retainAll(currentTaskSet);

        final TaskProgressResponse response = new TaskProgressResponse();
        response.init(list);
        dataConsumer.accept(response);
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
            final boolean kill = requestedTerminateTaskProgress.contains(taskProgress);
            if (kill) {
                ConfirmEvent.fireWarn(this, "Task " + taskProgress.getTaskName() + " has not finished ... will kill",
                        result -> {
                            if (result) {
                                doTerminate(taskProgress, true);
                            }
                        });

            } else {
                doTerminate(taskProgress, kill);
            }

        }
        refresh();
    }

    private void doTerminate(final TaskProgress taskProgress, final boolean kill) {
        final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
        findTaskCriteria.addId(taskProgress.getId());
        final TerminateTaskProgressRequest request = new TerminateTaskProgressRequest(findTaskCriteria, kill);

        requestedTerminateTaskProgress.add(taskProgress);
        final Rest<Boolean> rest = restFactory.create();
        rest.call(TASK_RESOURCE).terminate(taskProgress.getNodeName(), request);
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Set<String>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    @Override
    public void onColumnSort(final ColumnSortEvent event) {
        if (event.getColumn() instanceof OrderByColumn<?, ?>) {
            final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
            if (event.isSortAscending()) {
                request.getCriteria().setSort(orderByColumn.getField(), Direction.ASCENDING, orderByColumn.isIgnoreCase());
            } else {
                request.getCriteria().setSort(orderByColumn.getField(), Direction.DESCENDING, orderByColumn.isIgnoreCase());
            }
            refresh();
        }
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
                criteria.setNameFilter(filter);
                refresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
