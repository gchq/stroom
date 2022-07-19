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
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.pipeline.shared.PipelineDoc;
import stroom.preferences.client.DateTimeFormatter;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterExpressionUtil;
import stroom.processor.shared.ProcessorFilterResource;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorListRowResultPage;
import stroom.processor.shared.ProcessorResource;
import stroom.processor.shared.ProcessorRow;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.TreeRow;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.NumberCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ProcessorListPresenter extends MyPresenterWidget<PagerView>
        implements Refreshable, HasDocumentRead<Object> {

    private static final ProcessorResource PROCESSOR_RESOURCE = GWT.create(ProcessorResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final RestDataProvider<ProcessorListRow, ProcessorListRowResultPage> dataProvider;
    private final TooltipPresenter tooltipPresenter;
    private final FetchProcessorRequest request;
    private final DateTimeFormatter dateTimeFormatter;
    private final ProcessorInfoBuilder processorInfoBuilder;
    private boolean doneDataDisplay = false;
    private Column<ProcessorListRow, Expander> expanderColumn;
    private ProcessorListRow nextSelection;
    private final MyDataGrid<ProcessorListRow> dataGrid;
    private final MultiSelectionModelImpl<ProcessorListRow> selectionModel;

    private final RestSaveQueue<Integer, Boolean> processorEnabledSaveQueue;
    private final RestSaveQueue<Integer, Boolean> processorFilterEnabledSaveQueue;
    private final RestSaveQueue<Integer, Integer> processorFilterPrioritySaveQueue;

    private boolean allowUpdate;

    @Inject
    public ProcessorListPresenter(final EventBus eventBus,
                                  final PagerView view,
                                  final TooltipPresenter tooltipPresenter,
                                  final RestFactory restFactory,
                                  final DateTimeFormatter dateTimeFormatter,
                                  final ProcessorInfoBuilder processorInfoBuilder) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.tooltipPresenter = tooltipPresenter;
        this.dateTimeFormatter = dateTimeFormatter;
        this.processorInfoBuilder = processorInfoBuilder;

        request = new FetchProcessorRequest();
        dataProvider = new RestDataProvider<ProcessorListRow, ProcessorListRowResultPage>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ProcessorListRowResultPage> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
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
            dataProvider.setTreeRowHandler(new TreeRowHandler<ProcessorListRow>(request, dataGrid, expanderColumn));
        }
    }

    private void onChangeData(final ProcessorListRowResultPage data) {
        ProcessorListRow selected = selectionModel.getSelected();

        if (nextSelection != null) {
            for (final ProcessorListRow row : data.getValues()) {
                if (row instanceof ProcessorFilterRow) {
                    if (nextSelection.equals(((ProcessorFilterRow) row).getProcessorFilter())) {
                        selectionModel.setSelected(row);
                        break;
                    }
                }
            }
            nextSelection = null;

        } else if (selected != null) {
            if (!data.getValues().contains(selected)) {
                selectionModel.setSelected(selected, false);
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
                final SafeHtml safeHtml = processorInfoBuilder.get(row);
                tooltipPresenter.show(safeHtml, x, y);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addExpanderColumn() {
        expanderColumn = new Column<ProcessorListRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final ProcessorListRow row) {
                Expander expander = null;
                if (row instanceof TreeRow) {
                    final TreeRow treeRow = row;
                    expander = treeRow.getExpander();
                }
                return expander;
            }
        };
        expanderColumn.setFieldUpdater((index, row, value) -> {
            request.setRowExpanded(row, !value.isExpanded());
            refresh();
        });
        dataGrid.addColumn(expanderColumn, "<br/>", 0);
    }

    private void addIconColumn() {
        dataGrid.addColumn(new Column<ProcessorListRow, Preset>(new SvgCell()) {
            @Override
            public Preset getValue(final ProcessorListRow row) {
                Preset icon = null;
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
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
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
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                String lastStream = null;
                if (row instanceof ProcessorFilterRow) {
                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                    lastStream = dateTimeFormatter.format(
                            processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getMetaCreateMs());
                }
                return lastStream;
            }
        }, "Tracker Ms", ColumnSizeConstants.DATE_COL);
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
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
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
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
        dataGrid.addResizableColumn(new Column<ProcessorListRow, Number>(new NumberCell()) {
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
        dataGrid.addColumn(priorityColumn, "Priority", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addStreamsColumn() {
        dataGrid.addResizableColumn(new Column<ProcessorListRow, Number>(new NumberCell()) {
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
        dataGrid.addResizableColumn(new Column<ProcessorListRow, Number>(new NumberCell()) {
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
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
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
        final Appearance appearance = allowUpdate
                ? new DefaultAppearance()
                : new NoBorderAppearance();

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
        dataGrid.addColumn(enabledColumn, "Enabled", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addReprocessColumn() {
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
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
        dataGrid.addEndColumn(new EndColumn<>());
    }

    public MultiSelectionModel<ProcessorListRow> getSelectionModel() {
        return selectionModel;
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    private void doDataDisplay() {
        if (!doneDataDisplay) {
            doneDataDisplay = true;
            dataProvider.addDataDisplay(dataGrid);
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
}
