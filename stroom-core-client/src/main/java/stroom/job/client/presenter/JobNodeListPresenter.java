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

package stroom.job.client.presenter;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.client.ValueSpinnerCell;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.ActionQueue;
import stroom.job.client.JobTypeCell;
import stroom.job.shared.FindJobNodeAction;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeRow;
import stroom.job.shared.UpdateJobNodeAction;
import stroom.monitoring.client.presenter.SchedulePresenter;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

public class JobNodeListPresenter extends MyPresenterWidget<DataGridView<JobNodeRow>> {
    private final SchedulePresenter schedulePresenter;

    private final FindJobNodeAction action = new FindJobNodeAction();
    private final ActionDataProvider<JobNodeRow> dataProvider;
    private final ActionQueue<JobNode> actionQueue;

    @Inject
    public JobNodeListPresenter(final EventBus eventBus,
                                final ClientDispatchAsync dispatcher,
                                final SchedulePresenter schedulePresenter) {
        super(eventBus, new DataGridViewImpl<>(false));
        this.schedulePresenter = schedulePresenter;

        initTable();

        dataProvider = new ActionDataProvider<>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());

        actionQueue = new ActionQueue<>(dispatcher) {
            @Override
            public void onComplete() {
                dataProvider.refresh();
            }
        };
    }

    /**
     * Add the columns to the table.
     */
    private void initTable() {
        final Column<JobNodeRow, String> nameColumn = new Column<JobNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final JobNodeRow row) {
                return row.getJobNode().getJob().getName();
            }
        };
        getView().addResizableColumn(nameColumn, "Job", 200);

        final Column<JobNodeRow, String> nodeColumn = new Column<JobNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final JobNodeRow row) {
                return row.getJobNode().getNodeName();
            }
        };
        getView().addResizableColumn(nodeColumn, "Node", 200);

        // Schedule.
        final Column<JobNodeRow, String> typeColumn = new Column<JobNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final JobNodeRow row) {
                final JobNode jobNode = row.getJobNode();
                final JobType jobType = jobNode.getJobType();
                if (JobType.CRON.equals(jobType)) {
                    return "Cron " + jobNode.getSchedule();
                } else if (JobType.FREQUENCY.equals(jobType)) {
                    return "Frequency " + jobNode.getSchedule();
                } else if (JobType.DISTRIBUTED.equals(jobType)) {
                    return "Distributed";
                }
                return null;
            }
        };
        getView().addResizableColumn(typeColumn, "Type", 250);

        // Job Type.
        final Column<JobNodeRow, JobType> typeEditColumn = new Column<JobNodeRow, JobType>(new JobTypeCell()) {
            @Override
            public JobType getValue(final JobNodeRow row) {
                return row.getJobNode().getJobType();
            }

            @Override
            public void onBrowserEvent(final Context context, final Element elem, final JobNodeRow row,
                                       final NativeEvent event) {
                super.onBrowserEvent(context, elem, row, event);

                // Get the target element.
                final Element target = event.getEventTarget().cast();

                final String eventType = event.getType();
                if (row != null && "click".equals(eventType)) {
                    final String tagName = target.getTagName();
                    if ("img".equalsIgnoreCase(tagName)) {
                        final JobNode jobNode = row.getJobNode();
                        JobNodeInfo jobNodeInfo = row.getJobNodeInfo();
                        if (jobNodeInfo == null) {
                            jobNodeInfo = new JobNodeInfo();
                        }

                        schedulePresenter.setSchedule(jobNode.getJobType(),
                                jobNodeInfo.getScheduleReferenceTime(),
                                jobNodeInfo.getLastExecutedTime(),
                                jobNode.getSchedule());

                        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                            @Override
                            public void onHideRequest(final boolean autoClose, final boolean ok) {
                                schedulePresenter.hide(autoClose, ok);
                            }

                            @Override
                            public void onHide(final boolean autoClose, final boolean ok) {
                                if (ok) {
                                    final JobNode jobNode = row.getJobNode();
                                    jobNode.setSchedule(schedulePresenter.getScheduleString());
                                    actionQueue.dispatch(jobNode, new UpdateJobNodeAction(jobNode));
                                }
                            }
                        };

                        schedulePresenter.show(popupUiHandlers);
                    }
                }
            }
        };
        getView().addColumn(typeEditColumn, "", 20);

        // Max.
        final Column<JobNodeRow, Number> maxColumn = new Column<JobNodeRow, Number>(new ValueSpinnerCell(1, 1000)) {
            @Override
            public Number getValue(final JobNodeRow row) {
                if (row.getJobNode().getJobType().equals(JobType.DISTRIBUTED)) {
                    return new EditableInteger(row.getJobNode().getTaskLimit());
                }
                return null;
            }
        };

        maxColumn.setFieldUpdater((index, row, value) -> {
            final JobNode jobNode = row.getJobNode();
            jobNode.setTaskLimit(value.intValue());
            actionQueue.dispatch(jobNode, new UpdateJobNodeAction(jobNode));
        });
        getView().addColumn(maxColumn, "Max", 59);

        // Cur.
        final Column<JobNodeRow, String> curColumn = new Column<JobNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final JobNodeRow row) {
                if (row.getJobNodeInfo() != null) {
                    return ModelStringUtil.formatCsv(row.getJobNodeInfo().getCurrentTaskCount());
                } else {
                    return "?";
                }
            }
        };
        getView().addColumn(curColumn, "Cur", 59);

        // Last executed.
        final Column<JobNodeRow, String> lastExecutedColumn = new Column<JobNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final JobNodeRow row) {
                if (row.getJobNodeInfo() != null) {
                    return ClientDateUtil.toISOString(row.getJobNodeInfo().getLastExecutedTime());
                } else {
                    return "?";
                }
            }
        };
        getView().addColumn(lastExecutedColumn, "Last Executed", ColumnSizeConstants.DATE_COL);

        // Enabled.
        final Column<JobNodeRow, TickBoxState> enabledColumn = new Column<JobNodeRow, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final JobNodeRow row) {
                return TickBoxState.fromBoolean(row.getJobNode().isEnabled());
            }
        };
        enabledColumn.setFieldUpdater((index, jobNodeRow, value) -> {
            final boolean newValue = value.toBoolean();
            final JobNode jobNode = jobNodeRow.getJobNode();
            jobNode.setEnabled(newValue);
            actionQueue.dispatch(jobNode, new UpdateJobNodeAction(jobNode));
        });
        getView().addColumn(enabledColumn, "Enabled", 80);

        getView().addEndColumn(new EndColumn<>());
    }

    public void read(final Job job) {
        final FindJobNodeCriteria criteria = new FindJobNodeCriteria();
        criteria.getJobName().setString(job.getName());
        action.setCriteria(criteria);
        dataProvider.refresh();
    }
}
