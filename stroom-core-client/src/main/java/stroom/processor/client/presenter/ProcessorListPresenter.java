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

package stroom.processor.client.presenter;

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
import stroom.docref.DocRef;
import stroom.docref.SharedObject;
import stroom.entity.client.ActionQueue;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.entity.shared.NamedEntity;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.FetchProcessorAction;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorRow;
import stroom.processor.shared.UpdateProcessorAction;
import stroom.processor.shared.UpdateProcessorFilterAction;
import stroom.data.client.presenter.ActionDataProvider;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.MetaTooltipPresenterUtil;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultList;
import stroom.util.shared.TreeRow;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

public class ProcessorListPresenter extends MyPresenterWidget<DataGridView<SharedObject>>
        implements Refreshable, HasDocumentRead<SharedObject> {
    private final ActionDataProvider<SharedObject> dataProvider;
    private final TooltipPresenter tooltipPresenter;
    private final FetchProcessorAction action;
    private final ActionQueue<Processor> processorSaveQueue;
    private final ActionQueue<ProcessorFilter> processorFilterSaveQueue;
    private boolean doneDataDisplay = false;
    private Column<SharedObject, Expander> expanderColumn;
    private ProcessorFilter nextSelection;

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

        processorSaveQueue = new ActionQueue<>(dispatcher);
        processorFilterSaveQueue = new ActionQueue<>(dispatcher);
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
                if (row instanceof ProcessorFilterRow) {
                    if (nextSelection.equals(((ProcessorFilterRow) row).getProcessorFilter())) {
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

                if (row instanceof ProcessorRow) {
                    final ProcessorRow processorRow = (ProcessorRow) row;
                    final Processor processor = processorRow.getProcessor();
                    TooltipUtil.addHeading(html, "Stream Processor");
                    TooltipUtil.addRowData(html, "Id", String.valueOf(processor.getId()));
                    TooltipUtil.addRowData(html, "Created By", processor.getCreateUser());
                    MetaTooltipPresenterUtil.addRowDateString(html, "Created On", processor.getCreateTimeMs());
                    TooltipUtil.addRowData(html, "Updated By", processor.getUpdateUser());
                    MetaTooltipPresenterUtil.addRowDateString(html, "Updated On", processor.getUpdateTimeMs());
                    TooltipUtil.addRowData(html, "Pipeline", processor.getPipelineUuid());

                } else if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    final ProcessorFilter filter = processorFilterRow.getProcessorFilter();
                    final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
                    TooltipUtil.addHeading(html, "Stream Processor Filter");
                    TooltipUtil.addRowData(html, "Id", filter.getId());
                    TooltipUtil.addRowData(html, "Created By", filter.getCreateUser());
                    MetaTooltipPresenterUtil.addRowDateString(html, "Created On", filter.getCreateTimeMs());
                    TooltipUtil.addRowData(html, "Updated By", filter.getUpdateUser());
                    MetaTooltipPresenterUtil.addRowDateString(html, "Updated On", filter.getUpdateTimeMs());
                    MetaTooltipPresenterUtil.addRowDateString(html, "Min Stream Create Ms", tracker.getMinMetaCreateMs());
                    MetaTooltipPresenterUtil.addRowDateString(html, "Max Stream Create Ms", tracker.getMaxMetaCreateMs());
                    MetaTooltipPresenterUtil.addRowDateString(html, "Stream Create Ms", tracker.getMetaCreateMs());
                    TooltipUtil.addRowData(html, "Stream Create %", tracker.getTrackerStreamCreatePercentage());
                    MetaTooltipPresenterUtil.addRowDateString(html, "Last Poll", tracker.getLastPollMs());
                    TooltipUtil.addRowData(html, "Last Poll Age", tracker.getLastPollAge());
                    TooltipUtil.addRowData(html, "Last Poll Task Count", tracker.getLastPollTaskCount());
                    TooltipUtil.addRowData(html, "Min Stream Id", tracker.getMinMetaId());
                    TooltipUtil.addRowData(html, "Min Event Id", tracker.getMinEventId());
                    TooltipUtil.addRowData(html, "Streams", tracker.getMetaCount());
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
                if (row instanceof ProcessorFilterRow) {
                    icon = SvgPresets.FILTER.enabled(true);
                } else if (row instanceof ProcessorRow) {
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
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    final Processor processor = processorFilterRow.getProcessorFilter().getProcessor();
                    if (processor != null) {
                        final String pipelineUuid = processor.getPipelineUuid();
                        if (pipelineUuid != null) {
                            name = pipelineUuid;
                        }
                    }
                } else if (row instanceof ProcessorRow) {
                    final ProcessorRow processorRow = (ProcessorRow) row;
                    final String pipelineUuid = processorRow.getProcessor().getPipelineUuid();
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
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    lastStream = ClientDateUtil.toISOString(
                            processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getMetaCreateMs());
                }
                return lastStream;
            }
        }, "Tracker Ms", ColumnSizeConstants.DATE_COL);
        getView().addResizableColumn(new Column<SharedObject, String>(new TextCell()) {
            @Override
            public String getValue(final SharedObject row) {
                final String lastStream = null;
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    return ModelStringUtil.formatCsv(processorFilterRow.getProcessorFilter()
                            .getProcessorFilterTracker().getTrackerStreamCreatePercentage());
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
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    lastPoll = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getLastPollAge();
                }
                return lastPoll;
            }
        }, "Last Poll Age", ColumnSizeConstants.MEDIUM_COL);
        getView().addResizableColumn(new Column<SharedObject, Number>(new NumberCell()) {
            @Override
            public Number getValue(final SharedObject row) {
                Number currentTasks = null;
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    currentTasks = processorFilterRow.getProcessorFilter().getProcessorFilterTracker()
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
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    if (allowUpdate) {
                        priority = new EditableInteger(processorFilterRow.getProcessorFilter().getPriority());
                    } else {
                        priority = processorFilterRow.getProcessorFilter().getPriority();
                    }
                }
                return priority;
            }
        };
        if (allowUpdate) {
            priorityColumn.setFieldUpdater(new FieldUpdater<SharedObject, Number>() {
                @Override
                public void update(final int index, final SharedObject row, final Number value) {
                    if (row instanceof ProcessorFilterRow) {
                        final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                        final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
                        processorFilter.setPriority(value.intValue());
//                        final Processor processor = processorFilter.getProcessor();
                        processorFilterSaveQueue.dispatch(processorFilter, new UpdateProcessorFilterAction(processorFilter));
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
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    value = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getMetaCount();
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
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    value = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getEventCount();
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
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    status = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getStatus();
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
                if (row instanceof ProcessorFilterRow) {
                    return TickBoxState.fromBoolean(((ProcessorFilterRow) row).getProcessorFilter().isEnabled());
                } else if (row instanceof ProcessorRow) {
                    return TickBoxState.fromBoolean(((ProcessorRow) row).getProcessor().isEnabled());
                }
                return null;
            }
        };

        if (allowUpdate) {
            enabledColumn.setFieldUpdater(new FieldUpdater<SharedObject, TickBoxState>() {
                @Override
                public void update(final int index, final SharedObject row, final TickBoxState value) {
                    if (row instanceof ProcessorFilterRow) {
                        final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                        final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
//                        final Processor processor = processorFilter                                .getProcessor();

                        processorFilter.setEnabled(value.toBoolean());
                        processorFilterSaveQueue.dispatch(processorFilter, new UpdateProcessorFilterAction(processorFilter));
                    } else if (row instanceof ProcessorRow) {
                        final ProcessorRow processorRow = (ProcessorRow) row;
                        final Processor processor = processorRow.getProcessor();
                        processor.setEnabled(value.toBoolean());
//                        final String pipelineUuid = processor.getPipelineUuid();
                        processorSaveQueue.dispatch(processor, new UpdateProcessorAction(processor));
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
    public void read(final DocRef docRef, final SharedObject entity) {
        if (entity instanceof PipelineDoc) {
            setPipeline(docRef);
        } else {
            setNullCriteria();
        }
    }

    void setNextSelection(final ProcessorFilter nextSelection) {
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
