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

package stroom.jobsystem.client.presenter;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.FieldUpdater;
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
import stroom.entity.client.EntitySaveTask;
import stroom.entity.client.SaveQueue;
import stroom.entity.client.presenter.HasRead;
import stroom.jobsystem.client.TaskTypeCell;
import stroom.jobsystem.shared.FetchJobDataAction;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.jobsystem.shared.JobNode.JobType;
import stroom.jobsystem.shared.JobNodeInfo;
import stroom.jobsystem.shared.JobNodeRow;
import stroom.jobsystem.shared.TaskType;
import stroom.monitoring.client.presenter.SchedulePresenter;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

public class JobNodeListPresenter extends MyPresenterWidget<DataGridView<JobNodeRow>> implements HasRead<Job> {
    private final SchedulePresenter schedulePresenter;

    private final SaveQueue<JobNode> jobNodeSaver;

    private final ActionDataProvider<JobNodeRow> dataProvider;
    private final FetchJobDataAction action = new FetchJobDataAction();

    @Inject
    public JobNodeListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                final TooltipPresenter tooltipPresenter, final SchedulePresenter schedulePresenter) {
        super(eventBus, new DataGridViewImpl<JobNodeRow>(false));
        this.schedulePresenter = schedulePresenter;

        initTable();

        jobNodeSaver = new SaveQueue<JobNode>(dispatcher) {
            @Override
            public void onComplete() {
                super.onComplete();
                dataProvider.refresh();
            }
        };

        dataProvider = new ActionDataProvider<JobNodeRow>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());

    }

    /**
     * Add the columns to the table.
     */
    private void initTable() {
        final Column<JobNodeRow, String> nameColumn = new Column<JobNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final JobNodeRow row) {
                return row.getEntity().getJob().getName();
            }
        };
        getView().addResizableColumn(nameColumn, "Job", 200);

        final Column<JobNodeRow, String> nodeColumn = new Column<JobNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final JobNodeRow row) {
                return row.getEntity().getNode().getName();
            }
        };
        getView().addResizableColumn(nodeColumn, "Node", 200);

        // Type.
        final Column<JobNodeRow, TaskType> typeColumn = new Column<JobNodeRow, TaskType>(new TaskTypeCell()) {
            @Override
            public TaskType getValue(final JobNodeRow row) {
                if (row.getEntity().isPersistent()) {
                    return new TaskType(row.getEntity().getJobType(), row.getEntity().getSchedule());
                }

                return null;
            }

            @Override
            public void onBrowserEvent(final Context context, final Element elem, final JobNodeRow row,
                                       final NativeEvent event) {
                super.onBrowserEvent(context, elem, row, event);

                // Get the target element.
                final Element target = event.getEventTarget().cast();

                final String eventType = event.getType();
                if ("click".equals(eventType)) {
                    final String tagName = target.getTagName();
                    if ("img".equalsIgnoreCase(tagName)) {
                        if (row instanceof JobNodeRow) {
                            final JobNodeRow jobNodeRow = row;
                            final JobNode jobNode = jobNodeRow.getEntity();
                            JobNodeInfo jobNodeInfo = jobNodeRow.getJobNodeInfo();
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
                                    if (ok && jobNodeRow != null) {
                                        jobNodeSaver.save(new EntitySaveTask<JobNode>(jobNodeRow) {
                                            @Override
                                            protected void setValue(final JobNode entity) {
                                                entity.setSchedule(schedulePresenter.getScheduleString());
                                            }
                                        });
                                    }
                                }
                            };

                            schedulePresenter.show(popupUiHandlers);
                        }
                    }
                }
            }
        };
        getView().addColumn(typeColumn, "Type", 300);

        // Max.
        final Column<JobNodeRow, Number> maxColumn = new Column<JobNodeRow, Number>(new ValueSpinnerCell(1, 100)) {
            @Override
            public Number getValue(final JobNodeRow row) {
                if (row instanceof JobNodeRow) {
                    final JobNodeRow jobNodeRow = (row);
                    if (jobNodeRow.getEntity().getJobType().equals(JobType.DISTRIBUTED)) {
                        return new EditableInteger(row.getEntity().getTaskLimit());
                    }
                }
                return null;
            }
        };

        maxColumn.setFieldUpdater(new FieldUpdater<JobNodeRow, Number>() {
            @Override
            public void update(final int index, final JobNodeRow row, final Number value) {
                if (row instanceof JobNodeRow) {
                    final JobNodeRow jobNodeRow = row;
                    jobNodeSaver.save(new EntitySaveTask<JobNode>(jobNodeRow) {
                        @Override
                        protected void setValue(final JobNode entity) {
                            entity.setTaskLimit(value.intValue());
                        }
                    });
                }
            }
        });
        getView().addColumn(maxColumn, "Max", 59);

        // Cur.
        final Column<JobNodeRow, String> curColumn = new Column<JobNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final JobNodeRow row) {
                if (row instanceof JobNodeRow) {
                    final JobNodeRow jobNodeRow = row;
                    if (jobNodeRow.getJobNodeInfo() != null) {
                        return ModelStringUtil.formatCsv(jobNodeRow.getJobNodeInfo().getCurrentTaskCount());
                    } else {
                        return "?";
                    }
                }
                return null;
            }
        };
        getView().addColumn(curColumn, "Cur", 59);

        // Last executed.
        final Column<JobNodeRow, String> lastExecutedColumn = new Column<JobNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final JobNodeRow row) {
                if (row instanceof JobNodeRow) {
                    final JobNodeRow jobNodeRow = (row);
                    if (jobNodeRow.getJobNodeInfo() != null) {
                        return ClientDateUtil.createDateTimeString(jobNodeRow.getJobNodeInfo().getLastExecutedTime());
                    } else {
                        return "?";
                    }
                }
                return null;
            }
        };
        getView().addColumn(lastExecutedColumn, "Last Executed", ColumnSizeConstants.DATE_COL);

        // Enabled.
        final Column<JobNodeRow, TickBoxState> enabledColumn = new Column<JobNodeRow, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final JobNodeRow row) {
                return TickBoxState.fromBoolean(row.getEntity().isEnabled());
            }
        };
        enabledColumn.setFieldUpdater(new FieldUpdater<JobNodeRow, TickBoxState>() {
            @Override
            public void update(final int index, final JobNodeRow jobNodeRow, final TickBoxState value) {
                final boolean newValue = value.toBoolean();
                jobNodeSaver.save(new EntitySaveTask<JobNode>(jobNodeRow) {
                    @Override
                    protected void setValue(final JobNode entity) {
                        entity.setEnabled(newValue);
                    }
                });
            }
        });
        getView().addColumn(enabledColumn, "Enabled", 80);

        getView().addEndColumn(new EndColumn<JobNodeRow>());
    }

    @Override
    public void read(final Job entity) {
        action.setJob(entity);
        dataProvider.refresh();
    }
}
