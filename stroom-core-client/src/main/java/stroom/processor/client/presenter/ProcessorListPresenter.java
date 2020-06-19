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
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterExpressionUtil;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorListRowResultPage;
import stroom.processor.shared.ProcessorResource;
import stroom.processor.shared.ProcessorRow;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.TreeRow;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil.TableBuilder;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.NumberCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ProcessorListPresenter extends MyPresenterWidget<DataGridView<ProcessorListRow>>
        implements Refreshable, HasDocumentRead<Object> {
    private static final ProcessorResource PROCESSOR_RESOURCE = GWT.create(ProcessorResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final RestDataProvider<ProcessorListRow, ProcessorListRowResultPage> dataProvider;
    private final TooltipPresenter tooltipPresenter;
    private final FetchProcessorRequest request;
    private boolean doneDataDisplay = false;
    private Column<ProcessorListRow, Expander> expanderColumn;
    private ProcessorListRow nextSelection;

    private final RestSaveQueue<Integer, Boolean> processorEnabledSaveQueue;
    private final RestSaveQueue<Integer, Boolean> processorFilterEnabledSaveQueue;
    private final RestSaveQueue<Integer, Integer> processorFilterPrioritySaveQueue;

    private boolean allowUpdate;

    @Inject
    public ProcessorListPresenter(final EventBus eventBus,
                                  final TooltipPresenter tooltipPresenter,
                                  final RestFactory restFactory) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.tooltipPresenter = tooltipPresenter;

        request = new FetchProcessorRequest();
        dataProvider = new RestDataProvider<ProcessorListRow, ProcessorListRowResultPage>(eventBus) {
            @Override
            protected void exec(final Consumer<ProcessorListRowResultPage> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                final Rest<ProcessorListRowResultPage> rest = restFactory.create();
                rest
                        .onSuccess(dataConsumer)
                        .onFailure(throwableConsumer)
                        .call(PROCESSOR_FILTER_RESOURCE)
                        .find(request);
            }

            @Override
            protected void changeData(final ProcessorListRowResultPage data) {
                super.changeData(data);
                onChangeData(data);
            }
        };
        processorEnabledSaveQueue = new RestSaveQueue<Integer, Boolean>(eventBus, restFactory) {
            @Override
            protected void doAction(final Rest<?> rest, final Integer key, final Boolean value) {
                rest
                        .call(PROCESSOR_RESOURCE)
                        .setEnabled(key, value);
            }
        };
        processorFilterEnabledSaveQueue = new RestSaveQueue<Integer, Boolean>(eventBus, restFactory) {
            @Override
            protected void doAction(final Rest<?> rest, final Integer key, final Boolean value) {
                rest
                        .call(PROCESSOR_FILTER_RESOURCE)
                        .setEnabled(key, value);
            }
        };
        processorFilterPrioritySaveQueue = new RestSaveQueue<Integer, Integer>(eventBus, restFactory) {
            @Override
            protected void doAction(final Rest<?> rest, final Integer key, final Integer value) {
                rest
                        .call(PROCESSOR_FILTER_RESOURCE)
                        .setPriority(key, value);
            }
        };
    }

    void setAllowUpdate(final boolean allowUpdate) {
        this.allowUpdate = allowUpdate;

        if (expanderColumn == null) {
            addColumns();

            // Handle use of the expander column.
            dataProvider.setTreeRowHandler(new TreeRowHandler<ProcessorListRow>(request, getView(), expanderColumn));
        }
    }

    private void onChangeData(final ProcessorListRowResultPage data) {
        ProcessorListRow selected = getView().getSelectionModel().getSelected();

        if (nextSelection != null) {
            for (final ProcessorListRow row : data.getValues()) {
                if (row instanceof ProcessorFilterRow) {
                    if (nextSelection.equals(((ProcessorFilterRow) row).getProcessorFilter())) {
                        getView().getSelectionModel().setSelected(row);
                        break;
                    }
                }
            }
            nextSelection = null;

        } else if (selected != null) {
            if (!data.getValues().contains(selected)) {
                getView().getSelectionModel().setSelected(selected, false);
            }
        }
    }

    private void addColumns() {
        addExpanderColumn();
        addIconColumn();
        addInfoColumn();
        addEnabledColumn();
        addPipelineColumn();
        addTrackerColumns();
        addLastPollColumns();
        addPriorityColumn();
        addStreamsColumn();
        addEventsColumn();
        addStatusColumn();
        addReprocessColumn();
        addEndColumn();
    }

    private void addInfoColumn() {
        // Info column.
        final InfoColumn<ProcessorListRow> infoColumn = new InfoColumn<ProcessorListRow>() {
            @Override
            protected void showInfo(final ProcessorListRow row, final int x, final int y) {
                final TooltipUtil.Builder builder = TooltipUtil.builder()
                        .addTable(tableBuilder -> {
                            if (row instanceof ProcessorRow) {
                                final ProcessorRow processorRow = (ProcessorRow) row;
                                final Processor processor = processorRow.getProcessor();
                                tableBuilder.addHeaderRow("Stream Processor");
                                tableBuilder.addRow("Id", String.valueOf(processor.getId()));
                                tableBuilder.addRow("Created By", processor.getCreateUser());
                                addRowDateString(tableBuilder, "Created On", processor.getCreateTimeMs());
                                tableBuilder.addRow("Updated By", processor.getUpdateUser());
                                addRowDateString(tableBuilder, "Updated On", processor.getUpdateTimeMs());
                                tableBuilder.addRow("Pipeline", DocRefUtil.createSimpleDocRefString(processor.getPipeline()));

                            } else if (row instanceof ProcessorFilterRow) {
                                final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                                final ProcessorFilter filter = processorFilterRow.getProcessorFilter();
                                final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
                                tableBuilder.addHeaderRow("Stream Processor Filter");
                                tableBuilder.addRow("Id", filter.getId());
                                tableBuilder.addRow("Created By", filter.getCreateUser());
                                addRowDateString(tableBuilder, "Created On", filter.getCreateTimeMs());
                                tableBuilder.addRow("Updated By", filter.getUpdateUser());
                                addRowDateString(tableBuilder, "Updated On", filter.getUpdateTimeMs());
                                tableBuilder.addRow("Pipeline", DocRefUtil.createSimpleDocRefString(filter.getPipeline()));
                                addRowDateString(tableBuilder, "Min Stream Create Ms", tracker.getMinMetaCreateMs());
                                addRowDateString(tableBuilder, "Max Stream Create Ms", tracker.getMaxMetaCreateMs());
                                addRowDateString(tableBuilder, "Stream Create Ms", tracker.getMetaCreateMs());
                                tableBuilder.addRow("Stream Create %", tracker.getTrackerStreamCreatePercentage());
                                addRowDateString(tableBuilder, "Last Poll", tracker.getLastPollMs());
                                tableBuilder.addRow("Last Poll Age", tracker.getLastPollAge());
                                tableBuilder.addRow("Last Poll Task Count", tracker.getLastPollTaskCount());
                                tableBuilder.addRow("Min Stream Id", tracker.getMinMetaId());
                                tableBuilder.addRow("Min Event Id", tracker.getMinEventId());
                                tableBuilder.addRow("Streams", tracker.getMetaCount());
                                tableBuilder.addRow("Events", tracker.getEventCount());
                                tableBuilder.addRow("Status", tracker.getStatus());
                            }
                            return tableBuilder.build();
                        });

                tooltipPresenter.setHTML(builder.build());

                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(ProcessorListPresenter.this, tooltipPresenter, PopupType.POPUP, popupPosition,
                        null);
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addExpanderColumn() {
        expanderColumn = new Column<ProcessorListRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final ProcessorListRow row) {
                Expander expander = null;
                if (row instanceof TreeRow) {
                    final TreeRow treeRow = (TreeRow) row;
                    expander = treeRow.getExpander();
                }
                return expander;
            }
        };
        expanderColumn.setFieldUpdater((index, row, value) -> {
            request.setRowExpanded(row, !value.isExpanded());
            refresh();
        });
        getView().addColumn(expanderColumn, "<br/>", 0);
    }

    private void addIconColumn() {
        getView().addColumn(new Column<ProcessorListRow, SvgPreset>(new SvgCell()) {
            @Override
            public SvgPreset getValue(final ProcessorListRow row) {
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
        getView().addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                String name = null;
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
                    name = processorFilter.getPipelineName();
                    if (name == null) {
                        final Processor processor = processorFilter.getProcessor();
                        if (processor != null) {
                            final String pipelineName = processor.getPipelineName();
                            if (pipelineName != null) {
                                name = pipelineName;
                            } else {
                                final String pipelineUuid = processor.getPipelineUuid();
                                if (pipelineUuid != null) {
                                    name = pipelineUuid;
                                }
                            }
                        }
                    }
                } else if (row instanceof ProcessorRow) {
                    final ProcessorRow processorRow = (ProcessorRow) row;
                    final Processor processor = processorRow.getProcessor();
                    if (processor != null) {
                        final String pipelineName = processor.getPipelineName();
                        if (pipelineName != null) {
                            name = pipelineName;
                        } else {
                            final String pipelineUuid = processor.getPipelineUuid();
                            if (pipelineUuid != null) {
                                name = pipelineUuid;
                            }
                        }
                    }
                }

                return name;
            }
        }, "Pipeline", 300);
    }

    private void addTrackerColumns() {
        getView().addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                String lastStream = null;
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    lastStream = ClientDateUtil.toISOString(
                            processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getMetaCreateMs());
                }
                return lastStream;
            }
        }, "Tracker Ms", ColumnSizeConstants.DATE_COL);
        getView().addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
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
        getView().addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                String lastPoll = null;
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    lastPoll = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getLastPollAge();
                }
                return lastPoll;
            }
        }, "Last Poll Age", ColumnSizeConstants.MEDIUM_COL);
        getView().addResizableColumn(new Column<ProcessorListRow, Number>(new NumberCell()) {
            @Override
            public Number getValue(final ProcessorListRow row) {
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
        final Column<ProcessorListRow, Number> priorityColumn = new Column<ProcessorListRow, Number>(
                new ValueSpinnerCell(1, 100)) {
            @Override
            public Number getValue(final ProcessorListRow row) {
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
            priorityColumn.setFieldUpdater(new FieldUpdater<ProcessorListRow, Number>() {
                @Override
                public void update(final int index, final ProcessorListRow row, final Number value) {
                    if (row instanceof ProcessorFilterRow) {
                        final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                        final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
                        processorFilter.setPriority(value.intValue());
                        processorFilterPrioritySaveQueue.setValue(processorFilter.getId(), value.intValue());
                    }
                }
            });
        }
        getView().addColumn(priorityColumn, "Priority", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addStreamsColumn() {
        getView().addResizableColumn(new Column<ProcessorListRow, Number>(new NumberCell()) {
            @Override
            public Number getValue(final ProcessorListRow row) {
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
        getView().addResizableColumn(new Column<ProcessorListRow, Number>(new NumberCell()) {
            @Override
            public Number getValue(final ProcessorListRow row) {
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
        getView().addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
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
        final Column<ProcessorListRow, TickBoxState> enabledColumn = new Column<ProcessorListRow, TickBoxState>(
                TickBoxCell.create(appearance, false, false, allowUpdate)) {
            @Override
            public TickBoxState getValue(final ProcessorListRow row) {
                if (row instanceof ProcessorFilterRow) {
                    return TickBoxState.fromBoolean(((ProcessorFilterRow) row).getProcessorFilter().isEnabled());
                } else if (row instanceof ProcessorRow) {
                    return TickBoxState.fromBoolean(((ProcessorRow) row).getProcessor().isEnabled());
                }
                return null;
            }
        };

        if (allowUpdate) {
            enabledColumn.setFieldUpdater((index, row, value) -> {
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
                    processorFilter.setEnabled(value.toBoolean());

                    processorFilterEnabledSaveQueue.setValue(processorFilter.getId(), value.toBoolean());
//                    final Rest<ProcessorFilter> rest = restFactory.create();
//                    rest.call(PROCESSOR_FILTER_RESOURCE).setEnabled(processorFilter.getId(), value.toBoolean());

                } else if (row instanceof ProcessorRow) {
                    final ProcessorRow processorRow = (ProcessorRow) row;
                    final Processor processor = processorRow.getProcessor();
                    processor.setEnabled(value.toBoolean());

                    processorEnabledSaveQueue.setValue(processor.getId(), value.toBoolean());
//                    final Rest<Processor> rest = restFactory.create();
//                    rest.call(PROCESSOR_RESOURCE).setEnabled(processor.getId(), value.toBoolean());
                }
            });
        }
        getView().addColumn(enabledColumn, "Enabled", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addReprocessColumn() {
        getView().addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                String reprocess = null;
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    reprocess = String.valueOf(
                            processorFilterRow
                                    .getProcessorFilter()
                                    .isReprocess()).toLowerCase();
                }
                return reprocess;
            }
        }, "Reprocess", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addEndColumn() {
        getView().addEndColumn(new EndColumn<>());
    }

    public MultiSelectionModel<ProcessorListRow> getSelectionModel() {
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
        request.setExpression(ProcessorFilterExpressionUtil.createPipelineExpression(pipelineRef));
        doDataDisplay();
    }

    private void setFolder(final DocRef folder) {
        request.setExpression(ProcessorFilterExpressionUtil.createFolderExpression(folder));
        doDataDisplay();
    }

    private void setNullCriteria() {
        request.setExpression(ProcessorFilterExpressionUtil.createBasicExpression());
        doDataDisplay();
    }

    @Override
    public void read(final DocRef docRef, final Object entity) {
        if (docRef == null) {
            setNullCriteria();
        } else if (PipelineDoc.DOCUMENT_TYPE.equals(docRef.getType())) {
            setPipeline(docRef);
        } else {
            setFolder(docRef);
        }
    }

    void setNextSelection(final ProcessorListRow nextSelection) {
        this.nextSelection = nextSelection;
    }

    private void addRowDateString(final TableBuilder builder, final String label, final Long ms) {
        if (ms != null) {
            builder.addRow(label, ClientDateUtil.toISOString(ms) + " (" + ms + ")");
        }
    }
}
