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

package stroom.process.client.presenter;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.NumberCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.info.client.SvgCell;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxCell.Appearance;
import stroom.cell.tickbox.client.TickBoxCell.DefaultAppearance;
import stroom.cell.tickbox.client.TickBoxCell.NoBorderAppearance;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.client.ValueSpinnerCell;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.EntitySaveTask;
import stroom.entity.client.SaveQueue;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.ResultList;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.DocRef;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.streamstore.client.presenter.StreamTooltipPresenterUtil;
import stroom.streamtask.shared.FetchProcessorAction;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterRow;
import stroom.streamtask.shared.StreamProcessorFilterTracker;
import stroom.streamtask.shared.StreamProcessorRow;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.SharedObject;
import stroom.util.shared.TreeRow;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

public class ProcessorListPresenter<E> extends MyPresenterWidget<DataGridView<SharedObject>>
        implements Refreshable, HasDocumentRead<E> {
    private final ActionDataProvider<SharedObject> dataProvider;
    private final TooltipPresenter tooltipPresenter;
    private final FetchProcessorAction action;
    private final SaveQueue<StreamProcessor> streamProcessorSaveQueue;
    private final SaveQueue<StreamProcessorFilter> streamProcessorFilterSaveQueue;
    private boolean doneDataDisplay = false;
    private Column<SharedObject, Expander> expanderColumn;
    private StreamProcessorFilter nextSelection;

    private boolean allowUpdate;

    @Inject
    public ProcessorListPresenter(final EventBus eventBus,
                                  final TooltipPresenter tooltipPresenter,
                                  final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.tooltipPresenter = tooltipPresenter;

        action = new FetchProcessorAction();
        dataProvider = new ActionDataProvider<SharedObject>(dispatcher, action) {
            @Override
            protected void changeData(final ResultList<SharedObject> data) {
                super.changeData(data);
                onChangeData(data);
            }
        };

        streamProcessorSaveQueue = new SaveQueue<>(dispatcher);
        streamProcessorFilterSaveQueue = new SaveQueue<>(dispatcher);
    }

    void setAllowUpdate(final boolean allowUpdate) {
        this.allowUpdate = allowUpdate;

        if (expanderColumn == null) {
            addColumns();

            // Handle use of the expander column.
            dataProvider.setTreeRowHandler(new TreeRowHandler<>(action, getView(), expanderColumn));
        }
    }

    private void onChangeData(final ResultList<SharedObject> data) {
        SharedObject selected = getView().getSelectionModel().getSelected();

        if (nextSelection != null) {
            for (final SharedObject row : data) {
                if (row instanceof StreamProcessorFilterRow) {
                    if (nextSelection.equals(((StreamProcessorFilterRow) row).getEntity())) {
                        getView().getSelectionModel().setSelected(row);
                        break;
                    }
                }
            }
            nextSelection = null;

        } else if (selected != null) {
            if (!data.contains(selected)) {
                getView().getSelectionModel().setSelected(selected, false);
            }
        }
    }

    private void addColumns() {
        addExpanderColumn();
        addIconColumn();
        addInfoColumn();
        addPipelineColumn();
        addTrackerColumns();
        addLastPollColumns();
        addPriorityColumn();
        addStreamsColumn();
        addEventsColumn();
        addStatusColumn();
        addEnabledColumn();
        addEndColumn();
    }

    private void addInfoColumn() {
        // Info column.
        final InfoColumn<SharedObject> infoColumn = new InfoColumn<SharedObject>() {
            @Override
            protected void showInfo(final SharedObject row, final int x, final int y) {
                final StringBuilder html = new StringBuilder();

                if (row instanceof StreamProcessorRow) {
                    final StreamProcessorRow streamProcessorRow = (StreamProcessorRow) row;
                    final StreamProcessor processor = streamProcessorRow.getEntity();
                    TooltipUtil.addHeading(html, "Stream Processor");
                    TooltipUtil.addRowData(html, "Id", String.valueOf(processor.getId()));
                    TooltipUtil.addRowData(html, "Created By", processor.getCreateUser());
                    StreamTooltipPresenterUtil.addRowDateString(html, "Created On", processor.getCreateTime());
                    TooltipUtil.addRowData(html, "Updated By", processor.getUpdateUser());
                    StreamTooltipPresenterUtil.addRowDateString(html, "Updated On", processor.getUpdateTime());
                    TooltipUtil.addRowData(html, "Pipeline", processor.getPipelineUuid());

                } else if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    final StreamProcessorFilter filter = streamProcessorFilterRow.getEntity();
                    final StreamProcessorFilterTracker tracker = filter.getStreamProcessorFilterTracker();
                    TooltipUtil.addHeading(html, "Stream Processor Filter");
                    TooltipUtil.addRowData(html, "Id", filter.getId());
                    TooltipUtil.addRowData(html, "Created By", filter.getCreateUser());
                    StreamTooltipPresenterUtil.addRowDateString(html, "Created On", filter.getCreateTime());
                    TooltipUtil.addRowData(html, "Updated By", filter.getUpdateUser());
                    StreamTooltipPresenterUtil.addRowDateString(html, "Updated On", filter.getUpdateTime());
                    StreamTooltipPresenterUtil.addRowDateString(html, "Min Stream Create Ms", tracker.getMinStreamCreateMs());
                    StreamTooltipPresenterUtil.addRowDateString(html, "Max Stream Create Ms", tracker.getMaxStreamCreateMs());
                    StreamTooltipPresenterUtil.addRowDateString(html, "Stream Create Ms", tracker.getStreamCreateMs());
                    TooltipUtil.addRowData(html, "Stream Create %", tracker.getTrackerStreamCreatePercentage());
                    StreamTooltipPresenterUtil.addRowDateString(html, "Last Poll", tracker.getLastPollMs());
                    TooltipUtil.addRowData(html, "Last Poll Age", tracker.getLastPollAge());
                    TooltipUtil.addRowData(html, "Last Poll Task Count", tracker.getLastPollTaskCount());
                    TooltipUtil.addRowData(html, "Min Stream Id", tracker.getMinStreamId());
                    TooltipUtil.addRowData(html, "Min Event Id", tracker.getMinEventId());
                    TooltipUtil.addRowData(html, "Streams", tracker.getStreamCount());
                    TooltipUtil.addRowData(html, "Events", tracker.getEventCount());
                    TooltipUtil.addRowData(html, "Status", tracker.getStatus());
                }

                tooltipPresenter.setHTML(html.toString());

                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(ProcessorListPresenter.this, tooltipPresenter, PopupType.POPUP, popupPosition,
                        null);
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addExpanderColumn() {
        expanderColumn = new Column<SharedObject, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final SharedObject row) {
                Expander expander = null;
                if (row instanceof TreeRow) {
                    final TreeRow treeRow = (TreeRow) row;
                    expander = treeRow.getExpander();
                }
                return expander;
            }
        };
        expanderColumn.setFieldUpdater((index, row, value) -> {
            action.setRowExpanded(row, !value.isExpanded());
            refresh();
        });
        getView().addColumn(expanderColumn, "<br/>", 0);
    }

    private void addIconColumn() {
        getView().addColumn(new Column<SharedObject, SvgPreset>(new SvgCell()) {
            @Override
            public SvgPreset getValue(final SharedObject row) {
                SvgPreset icon = null;
                if (row instanceof StreamProcessorFilterRow) {
                    icon = SvgPresets.FILTER.enabled(true);
                } else if (row instanceof StreamProcessorRow) {
                    icon = SvgPresets.PROCESS.enabled(true);
                }
                return icon;
            }
        }, "", ColumnSizeConstants.ICON_COL);
    }

    private void addPipelineColumn() {
        getView().addResizableColumn(new Column<SharedObject, String>(new TextCell()) {
            @Override
            public String getValue(final SharedObject row) {
                String name = null;
                if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    final StreamProcessor streamProcessor = streamProcessorFilterRow.getEntity().getStreamProcessor();
                    if (streamProcessor != null) {
                        final String pipelineUuid = streamProcessor.getPipelineUuid();
                        if (pipelineUuid != null) {
                            name = pipelineUuid;
                        }
                    }
                } else if (row instanceof StreamProcessorRow) {
                    final StreamProcessorRow streamProcessorRow = (StreamProcessorRow) row;
                    final String pipelineUuid = streamProcessorRow.getEntity().getPipelineUuid();
                    if (pipelineUuid != null) {
                        name = pipelineUuid;
                    }
                }

                return name;
            }
        }, "Pipeline", 300);
    }

    private void addTrackerColumns() {
        getView().addResizableColumn(new Column<SharedObject, String>(new TextCell()) {
            @Override
            public String getValue(final SharedObject row) {
                String lastStream = null;
                if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    lastStream = ClientDateUtil.toISOString(
                            streamProcessorFilterRow.getEntity().getStreamProcessorFilterTracker().getStreamCreateMs());
                }
                return lastStream;
            }
        }, "Tracker Ms", ColumnSizeConstants.DATE_COL);
        getView().addResizableColumn(new Column<SharedObject, String>(new TextCell()) {
            @Override
            public String getValue(final SharedObject row) {
                final String lastStream = null;
                if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    return ModelStringUtil.formatCsv(streamProcessorFilterRow.getEntity()
                            .getStreamProcessorFilterTracker().getTrackerStreamCreatePercentage());
                }
                return lastStream;
            }
        }, "Tracker %", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addLastPollColumns() {
        getView().addResizableColumn(new Column<SharedObject, String>(new TextCell()) {
            @Override
            public String getValue(final SharedObject row) {
                String lastPoll = null;
                if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    lastPoll = streamProcessorFilterRow.getEntity().getStreamProcessorFilterTracker().getLastPollAge();
                }
                return lastPoll;
            }
        }, "Last Poll Age", ColumnSizeConstants.MEDIUM_COL);
        getView().addResizableColumn(new Column<SharedObject, Number>(new NumberCell()) {
            @Override
            public Number getValue(final SharedObject row) {
                Number currentTasks = null;
                if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    currentTasks = streamProcessorFilterRow.getEntity().getStreamProcessorFilterTracker()
                            .getLastPollTaskCount();
                }
                return currentTasks;
            }
        }, "Task Count", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addPriorityColumn() {
        final Column<SharedObject, Number> priorityColumn = new Column<SharedObject, Number>(
                new ValueSpinnerCell(1, 100)) {
            @Override
            public Number getValue(final SharedObject row) {
                Number priority = null;
                if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    if (allowUpdate) {
                        priority = new EditableInteger(streamProcessorFilterRow.getEntity().getPriority());
                    } else {
                        priority = streamProcessorFilterRow.getEntity().getPriority();
                    }
                }
                return priority;
            }
        };
        if (allowUpdate) {
            priorityColumn.setFieldUpdater(new FieldUpdater<SharedObject, Number>() {
                @Override
                public void update(final int index, final SharedObject row, final Number value) {
                    if (row instanceof StreamProcessorFilterRow) {
                        final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                        final StreamProcessor streamProcessor = streamProcessorFilterRow.getEntity()
                                .getStreamProcessor();
                        streamProcessorFilterSaveQueue
                                .save(new EntitySaveTask<StreamProcessorFilter>(streamProcessorFilterRow) {
                                    @Override
                                    protected void setValue(final StreamProcessorFilter entity) {
                                        entity.setPriority(value.intValue());
                                    }

                                    @Override
                                    protected void setEntity(final StreamProcessorFilter entity) {
                                        entity.setStreamProcessor(streamProcessor);
                                        super.setEntity(entity);
                                    }
                                });
                    }
                }
            });
        }
        getView().addColumn(priorityColumn, "Priority", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addStreamsColumn() {
        getView().addResizableColumn(new Column<SharedObject, Number>(new NumberCell()) {
            @Override
            public Number getValue(final SharedObject row) {
                Number value = null;
                if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    value = streamProcessorFilterRow.getEntity().getStreamProcessorFilterTracker().getStreamCount();
                }
                return value;
            }
        }, "Streams", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addEventsColumn() {
        getView().addResizableColumn(new Column<SharedObject, Number>(new NumberCell()) {
            @Override
            public Number getValue(final SharedObject row) {
                Number value = null;
                if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    value = streamProcessorFilterRow.getEntity().getStreamProcessorFilterTracker().getEventCount();
                }
                return value;
            }
        }, "Events", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addStatusColumn() {
        getView().addResizableColumn(new Column<SharedObject, String>(new TextCell()) {
            @Override
            public String getValue(final SharedObject row) {
                String status = null;
                if (row instanceof StreamProcessorFilterRow) {
                    final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                    status = streamProcessorFilterRow.getEntity().getStreamProcessorFilterTracker().getStatus();
                }
                return status;
            }
        }, "Status", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addEnabledColumn() {
        final Appearance appearance = allowUpdate ? new DefaultAppearance() : new NoBorderAppearance();

        // Enabled.
        final Column<SharedObject, TickBoxState> enabledColumn = new Column<SharedObject, TickBoxState>(
                TickBoxCell.create(appearance, false, false, allowUpdate)) {
            @Override
            public TickBoxState getValue(final SharedObject row) {
                if (row instanceof StreamProcessorFilterRow) {
                    return TickBoxState.fromBoolean(((StreamProcessorFilterRow) row).getEntity().isEnabled());
                } else if (row instanceof StreamProcessorRow) {
                    return TickBoxState.fromBoolean(((StreamProcessorRow) row).getEntity().isEnabled());
                }
                return null;
            }
        };

        if (allowUpdate) {
            enabledColumn.setFieldUpdater(new FieldUpdater<SharedObject, TickBoxState>() {
                @Override
                public void update(final int index, final SharedObject row, final TickBoxState value) {
                    if (row instanceof StreamProcessorFilterRow) {
                        final StreamProcessorFilterRow streamProcessorFilterRow = (StreamProcessorFilterRow) row;
                        final StreamProcessor streamProcessor = streamProcessorFilterRow.getEntity()
                                .getStreamProcessor();
                        streamProcessorFilterSaveQueue
                                .save(new EntitySaveTask<StreamProcessorFilter>(streamProcessorFilterRow) {
                                    @Override
                                    protected void setValue(final StreamProcessorFilter entity) {
                                        entity.setEnabled(value.toBoolean());
                                    }

                                    @Override
                                    protected void setEntity(final StreamProcessorFilter entity) {
                                        entity.setStreamProcessor(streamProcessor);
                                        super.setEntity(entity);
                                    }
                                });
                    } else if (row instanceof StreamProcessorRow) {
                        final StreamProcessorRow streamProcessorRow = (StreamProcessorRow) row;
                        final String pipelineUuid = streamProcessorRow.getEntity().getPipelineUuid();
                        streamProcessorSaveQueue.save(new EntitySaveTask<StreamProcessor>(streamProcessorRow) {
                            @Override
                            protected void setValue(final StreamProcessor entity) {
                                entity.setEnabled(value.toBoolean());
                            }

                            @Override
                            protected void setEntity(final StreamProcessor entity) {
                                entity.setPipelineUuid(pipelineUuid);
                                super.setEntity(entity);
                            }
                        });
                    }
                }
            });
        }
        getView().addColumn(enabledColumn, "Enabled", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addEndColumn() {
        getView().addEndColumn(new EndColumn<>());
    }

    public MultiSelectionModel<SharedObject> getSelectionModel() {
        return getView().getSelectionModel();
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    private void doDataDisplay() {
        if (!doneDataDisplay) {
            doneDataDisplay = true;
            dataProvider.addDataDisplay(getView().getDataDisplay());
        } else {
            dataProvider.refresh();
        }
    }

    private void setPipeline(final DocRef pipelineRef) {
        action.setPipeline(pipelineRef);
        doDataDisplay();

    }

    private void setNullCriteria() {
        action.setPipeline(null);
        doDataDisplay();
    }

    @Override
    public void read(final DocRef docRef, final E entity) {
        if (entity instanceof PipelineDoc) {
            setPipeline(docRef);
        } else {
            setNullCriteria();
        }
    }

    void setNextSelection(final StreamProcessorFilter nextSelection) {
        this.nextSelection = nextSelection;
    }

    private String toNameString(final NamedEntity namedEntity) {
        if (namedEntity != null) {
            return namedEntity.getName() + " (" + namedEntity.getId() + ")";
        } else {
            return "";
        }
    }
}
