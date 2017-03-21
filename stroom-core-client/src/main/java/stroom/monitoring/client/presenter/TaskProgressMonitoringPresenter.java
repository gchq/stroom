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

package stroom.monitoring.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.entity.shared.BaseCriteria.OrderByDirection;
import stroom.entity.shared.ResultList;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressAction;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TerminateTaskProgressAction;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.TaskId;
import stroom.util.shared.VoidResult;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.Icon;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import java.util.HashSet;
import java.util.Set;

public class TaskProgressMonitoringPresenter extends ContentTabPresenter<DataGridView<TaskProgress>>
        implements HasDataSelectionHandlers<Set<String>>, Refreshable {
    private final ClientDispatchAsync dispatcher;
    private final FindTaskProgressCriteria criteria = new FindTaskProgressCriteria();
    private final FindTaskProgressAction action = new FindTaskProgressAction(criteria);
    private final ActionDataProvider<TaskProgress> dataProvider;
    private final Set<TaskProgress> selectedTaskProgress = new HashSet<>();
    private final Set<TaskProgress> requestedTerminateTaskProgress = new HashSet<>();
    private final TooltipPresenter tooltipPresenter;
    private final GlyphButtonView terminateButton;

    @Inject
    public TaskProgressMonitoringPresenter(final EventBus eventBus,
                                           final ClientDispatchAsync dispatcher, final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<>(false, 1000));
        this.dispatcher = dispatcher;
        this.tooltipPresenter = tooltipPresenter;
        this.criteria.setOrderBy(FindTaskProgressCriteria.ORDER_BY_AGE, OrderByDirection.DESCENDING);

        terminateButton = getView().addButton(GlyphIcons.DELETE);
        terminateButton.addClickHandler(event -> endSelectedTask());
        terminateButton.setEnabled(true);

        dataProvider = new ActionDataProvider<TaskProgress>(dispatcher, action) {
            @Override
            protected void changeData(final ResultList<TaskProgress> data) {
                super.changeData(data);
                onChangeData(data);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());

        initTableColumns();
    }

    private void onChangeData(final ResultList<TaskProgress> data) {
        final HashSet<TaskProgress> currentTaskSet = new HashSet<>();
        for (final TaskProgress value : data) {
            currentTaskSet.add(value);
        }
        selectedTaskProgress.retainAll(currentTaskSet);
        requestedTerminateTaskProgress.retainAll(currentTaskSet);
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

        getView().addColumn(column, "", 15);

        // Expander column.
        final Column<TaskProgress, Expander> expanderColumn = new Column<TaskProgress, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final TaskProgress row) {
                return buildExpander(row);
            }
        };
        getView().addColumn(expanderColumn, "");

        expanderColumn.setFieldUpdater((index, row, value) -> {
            action.setRowExpanded(row, !value.isExpanded());
            dataProvider.refresh();
        });

        final InfoColumn<TaskProgress> furtherInfoColumn = new InfoColumn<TaskProgress>() {
            @Override
            protected void showInfo(final TaskProgress row, final int x, final int y) {
                final StringBuilder html = new StringBuilder();
                TooltipUtil.addHeading(html, "Task");
                TooltipUtil.addRowData(html, "Name", row.getTaskName());
                TooltipUtil.addRowData(html, "User", row.getUserName());
                TooltipUtil.addRowData(html, "Submit Time", ClientDateUtil.createDateTimeString(row.getSubmitTimeMs()));
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

                TooltipUtil.addRowData(html, "Session Id", row.getSessionId());
                TooltipUtil.addRowData(html, row.getTaskInfo());

                tooltipPresenter.setHTML(html.toString());

                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(TaskProgressMonitoringPresenter.this, tooltipPresenter, PopupType.POPUP,
                        popupPosition, null);
            }
        };
        getView().addColumn(furtherInfoColumn, "<br/>", 15);

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
        final Column<TaskProgress, String> nodeColumn = new Column<TaskProgress, String>(new TextCell()) {
            @Override
            public String getValue(final TaskProgress value) {
                if (value.getNode() != null) {
                    return value.getNode().getName();
                }
                return "?";
            }
        };
        getView().addResizableColumn(nodeColumn, "Node", 150);

        // Name.
        final Column<TaskProgress, String> nameColumn = new Column<TaskProgress, String>(new TextCell()) {
            @Override
            public String getValue(final TaskProgress value) {
                return value.getTaskName();
            }
        };
        getView().addResizableColumn(nameColumn, "Name", 150);

        // User.
        final Column<TaskProgress, String> userColumn = new Column<TaskProgress, String>(new TextCell()) {
            @Override
            public String getValue(final TaskProgress value) {
                return value.getUserName();
            }
        };
        getView().addResizableColumn(userColumn, "User", 80);

        // Submit Time.
        final Column<TaskProgress, String> submitTimeColumn = new Column<TaskProgress, String>(new TextCell()) {
            @Override
            public String getValue(final TaskProgress value) {
                return ClientDateUtil.createDateTimeString(value.getSubmitTimeMs());
            }
        };
        getView().addResizableColumn(submitTimeColumn, "Submit Time", ColumnSizeConstants.DATE_COL);

        // Age.
        final Column<TaskProgress, String> ageColumn = new Column<TaskProgress, String>(new TextCell()) {
            @Override
            public String getValue(final TaskProgress value) {
                return ModelStringUtil.formatDurationString(value.getAgeMs());
            }
        };
        getView().addResizableColumn(ageColumn, "Age", 50);

        // Info.
        final Column<TaskProgress, String> infoColumn = new Column<TaskProgress, String>(new TextCell()) {
            @Override
            public String getValue(final TaskProgress value) {
                if (value.isOrphan()) {
                    return "??? Orphan Task ??? " + value.getTaskInfo();
                }

                return value.getTaskInfo();
            }
        };
        getView().addResizableColumn(infoColumn, "Info", 400);
        getView().addEndColumn(new EndColumn<>());

        // Handle use of the expander column.
        dataProvider.setTreeRowHandler(new TreeRowHandler<>(action, getView(), expanderColumn));
    }

    private Expander buildExpander(final TaskProgress row) {
        return row.getExpander();
    }

    @Override
    public void refresh() {
        // expanderColumnWidth = 0;
        // dataProvider.refresh();
    }

    @Override
    public String getLabel() {
        return "Server Tasks";
    }

    @Override
    public Icon getIcon() {
        return GlyphIcons.JOBS;
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
        final TerminateTaskProgressAction action = new TerminateTaskProgressAction(
                "Terminate: " + taskProgress.getTaskName(), findTaskCriteria, kill);

        requestedTerminateTaskProgress.add(taskProgress);
        dispatcher.execute(action, new AsyncCallbackAdaptor<VoidResult>() {
        });
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Set<String>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }
}
