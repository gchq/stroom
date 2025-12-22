/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.analytics.shared.AnalyticRuleDoc;
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
import stroom.data.client.presenter.OpenLinkUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.TreeRowHandler;
import stroom.pipeline.shared.PipelineDoc;
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
import stroom.query.api.ExpressionOperator;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Expander;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.NumberCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class ProcessorListPresenter extends MyPresenterWidget<PagerView>
        implements Refreshable, HasDocumentRead<Object> {

    private static final ProcessorResource PROCESSOR_RESOURCE = GWT.create(ProcessorResource.class);
    private static final ProcessorFilterResource PROCESSOR_FILTER_RESOURCE = GWT.create(ProcessorFilterResource.class);

    private final ClientSecurityContext securityContext;
    private final RestDataProvider<ProcessorListRow, ProcessorListRowResultPage> dataProvider;
    private final TooltipPresenter tooltipPresenter;
    private final FetchProcessorRequest request;
    private final ProcessorInfoBuilder processorInfoBuilder;
    private Column<ProcessorListRow, Expander> expanderColumn;
    private ProcessorListRow nextSelection;
    private final MyDataGrid<ProcessorListRow> dataGrid;
    private final MultiSelectionModelImpl<ProcessorListRow> selectionModel;

    private final RestSaveQueue<Integer, Boolean> processorEnabledSaveQueue;
    private final RestSaveQueue<Integer, Boolean> processorFilterEnabledSaveQueue;
    private final RestSaveQueue<Integer, Integer> processorFilterPrioritySaveQueue;
    private final RestSaveQueue<Integer, Integer> processorFilterMaxProcessingTasksSaveQueue;

    private boolean allowUpdate;
    private ExpressionOperator expression;
    private boolean initiated;
    private ProcessorListRowResultPage currentResultPageResponse;

    @Inject
    public ProcessorListPresenter(final EventBus eventBus,
                                  final PagerView view,
                                  final ClientSecurityContext securityContext,
                                  final TooltipPresenter tooltipPresenter,
                                  final RestFactory restFactory,
                                  final ProcessorInfoBuilder processorInfoBuilder) {
        super(eventBus, view);
        this.securityContext = securityContext;

        this.dataGrid = new MyDataGrid<>(this);
        this.selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.tooltipPresenter = tooltipPresenter;
        this.processorInfoBuilder = processorInfoBuilder;

        this.request = new FetchProcessorRequest();
        this.processorEnabledSaveQueue = new RestSaveQueue<Integer, Boolean>(eventBus) {
            @Override
            protected void doAction(final Integer key, final Boolean value, final Consumer<Integer> consumer) {
                restFactory
                        .create(PROCESSOR_RESOURCE)
                        .method(res -> res.setEnabled(key, value))
                        .onSuccess(res -> consumer.accept(key))
                        .onFailure(res -> {
                            AlertEvent.fireError(this, res.getMessage(), null);
                            consumer.accept(key);
                        })
                        .taskMonitorFactory(getView())
                        .exec();
            }
        };
        processorFilterEnabledSaveQueue = new RestSaveQueue<Integer, Boolean>(eventBus) {
            @Override
            protected void doAction(final Integer key, final Boolean value, final Consumer<Integer> consumer) {
                restFactory
                        .create(PROCESSOR_FILTER_RESOURCE)
                        .method(res -> res.setEnabled(key, value))
                        .onSuccess(res -> consumer.accept(key))
                        .onFailure(res -> {
                            AlertEvent.fireError(this, res.getMessage(), null);
                            consumer.accept(key);
                        })
                        .taskMonitorFactory(getView())
                        .exec();
            }
        };
        processorFilterPrioritySaveQueue = new RestSaveQueue<Integer, Integer>(eventBus) {
            @Override
            protected void doAction(final Integer key, final Integer value, final Consumer<Integer> consumer) {
                restFactory
                        .create(PROCESSOR_FILTER_RESOURCE)
                        .method(res -> res.setPriority(key, value))
                        .onSuccess(res -> consumer.accept(key))
                        .onFailure(res -> {
                            AlertEvent.fireError(this, res.getMessage(), null);
                            consumer.accept(key);
                        })
                        .taskMonitorFactory(getView())
                        .exec();
            }
        };
        processorFilterMaxProcessingTasksSaveQueue = new RestSaveQueue<Integer, Integer>(eventBus) {
            @Override
            protected void doAction(final Integer key, final Integer value, final Consumer<Integer> consumer) {
                restFactory
                        .create(PROCESSOR_FILTER_RESOURCE)
                        .method(res -> res.setMaxProcessingTasks(key, value))
                        .onSuccess(res -> consumer.accept(key))
                        .onFailure(res -> {
                            AlertEvent.fireError(this, res.getMessage(), null);
                            consumer.accept(key);
                        })
                        .taskMonitorFactory(getView())
                        .exec();
            }
        };

        dataProvider = new RestDataProvider<ProcessorListRow, ProcessorListRowResultPage>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ProcessorListRowResultPage> dataConsumer,
                                final RestErrorHandler errorHandler) {
                request.setExpression(expression);
                restFactory
                        .create(PROCESSOR_FILTER_RESOURCE)
                        .method(res -> res.find(request))
                        .onSuccess(dataConsumer)
                        .onFailure(errorHandler)
                        .taskMonitorFactory(getView())
                        .exec();
            }

            @Override
            protected void changeData(final ProcessorListRowResultPage data) {
                currentResultPageResponse = data;
                super.changeData(data);
                onChangeData(data);
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
        final ProcessorListRow selected = selectionModel.getSelected();

        if (nextSelection != null) {
            for (final ProcessorListRow row : data.getValues()) {
                if (row instanceof ProcessorFilterRow) {
                    if (nextSelection.equals(row)) {
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
        // TODO Change all the cols to use DataGridUtil and enabledWhen() so the disabled
        //  ones get low-lighted

        // TODO Add tooltips to all the cols

        // TODO Why show the Pipeline col when you are viewing a PipelineDoc? Appreciate the filter
        //  automatically includes a term for the pipe and that this presenter is used on folders but might
        //  be better to hard code the pipe term on a PipelineDoc, make it not visible in the filter and prevent
        //  its use. Then you can remove the col.

        addExpanderColumn();
        addIconColumn();
        addInfoColumn();
        addEnabledColumn();
        addPipelineColumn();
        addPriorityColumn();
        addMaxProcessingTasksColumn();
        addStatusColumn();
//        addTrackerColumns();
        addLastPollColumns();
        addTasksColumn();
//        addEventsColumn();
        addReprocessColumn();
        addRunAsUserColumn();
        addEndColumn();
    }

    private void addInfoColumn() {
        // Info column.
        final InfoColumn<ProcessorListRow> infoColumn = new InfoColumn<ProcessorListRow>() {
            @Override
            protected void showInfo(final ProcessorListRow row, final PopupPosition popupPosition) {
                final SafeHtml safeHtml = processorInfoBuilder.get(row);
                tooltipPresenter.show(safeHtml, popupPosition);
            }
        };
        OpenLinkUtil.addClickHandler(this, tooltipPresenter.getWidget());

        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addExpanderColumn() {
        expanderColumn = new Column<ProcessorListRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final ProcessorListRow row) {
                Expander expander = null;
                if (row != null) {
                    expander = row.getExpander();
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
        final Function<ProcessorListRow, DocRef> extractionFunction = row -> {
            DocRef docRef = null;
            if (row instanceof ProcessorFilterRow) {
                //noinspection PatternVariableCanBeUsed Not in GWT
                final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();

                if (processorFilter.getPipelineUuid() != null) {
                    docRef = processorFilter.getPipeline();
                }

                if (docRef == null) {
                    final Processor processor = processorFilter.getProcessor();
                    if (processor != null) {
                        if (processor.getPipelineUuid() != null || processor.getPipelineName() != null) {
                            docRef = processor.getPipeline();
                        }
                    }
                }
            } else if (row instanceof ProcessorRow) {
                //noinspection PatternVariableCanBeUsed Not in GWT
                final ProcessorRow processorRow = (ProcessorRow) row;
                final Processor processor = processorRow.getProcessor();
                if (processor != null) {
                    if (processor.getPipelineUuid() != null || processor.getPipelineName() != null) {
                        docRef = processor.getPipeline();
                    }
                }
            }

            return docRef;
        };

        DataGridUtil.addDocRefColumn(getEventBus(), dataGrid, "Pipeline", extractionFunction);
    }

//    private void addTrackerColumns() {
//        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
//            @Override
//            public String getValue(final ProcessorListRow row) {
//                String lastStream = null;
//                if (row instanceof ProcessorFilterRow) {
//                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
//                    lastStream = dateTimeFormatter.format(
//                            processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getMetaCreateMs());
//                }
//                return lastStream;
//            }
//        }, "Tracker Ms", ColumnSizeConstants.DATE_COL);
//    }

    private void addLastPollColumns() {
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                String lastPoll = null;
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    lastPoll = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getLastPollAge();
                }
                return lastPoll;
            }
        }, "Last Poll Age", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addPriorityColumn() {
        final Column<ProcessorListRow, Number> priorityColumn = new Column<ProcessorListRow, Number>(
                new ValueSpinnerCell(ProcessorFilter.MIN_PRIORITY, ProcessorFilter.MAX_PRIORITY)) {
            @Override
            public Number getValue(final ProcessorListRow row) {
                Number priority = null;
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
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
            priorityColumn.setFieldUpdater((index, row, value) -> {
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
                    processorFilter.setPriority(value.intValue());
                    processorFilterPrioritySaveQueue.setValue(processorFilter.getId(), value.intValue());
                }
            });
        }
        dataGrid.addColumn(priorityColumn, "Priority", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addMaxProcessingTasksColumn() {
        final Column<ProcessorListRow, Number> maxProcessingTasksColumn = new Column<ProcessorListRow, Number>(
                new ValueSpinnerCell(
                        ProcessorFilter.MIN_MAX_PROCESSING_TASKS,
                        ProcessorFilter.MAX_MAX_PROCESSING_TASKS)) {
            @Override
            public Number getValue(final ProcessorListRow row) {
                Number maxProcessingTasks = null;
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    if (allowUpdate) {
                        maxProcessingTasks = new EditableInteger(processorFilterRow.getProcessorFilter()
                                .getMaxProcessingTasks());
                    } else {
                        maxProcessingTasks = processorFilterRow.getProcessorFilter().getMaxProcessingTasks();
                    }
                }
                return maxProcessingTasks;
            }
        };
        if (allowUpdate) {
            maxProcessingTasksColumn.setFieldUpdater((index, row, value) -> {
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
                    processorFilter.setMaxProcessingTasks(value.intValue());
                    processorFilterMaxProcessingTasksSaveQueue.setValue(processorFilter.getId(), value.intValue());
                }
            });
        }
        dataGrid.addColumn(maxProcessingTasksColumn, "Max Concurrent", 120);
    }

    private void addTasksColumn() {
        dataGrid.addResizableColumn(new Column<ProcessorListRow, Number>(new NumberCell()) {
            @Override
            public Number getValue(final ProcessorListRow row) {
                Number value = null;
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    value = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getMetaCount();
                }
                return value;
            }
        }, "Tasks", ColumnSizeConstants.MEDIUM_COL);
    }

//    private void addEventsColumn() {
//        dataGrid.addResizableColumn(new Column<ProcessorListRow, Number>(new NumberCell()) {
//            @Override
//            public Number getValue(final ProcessorListRow row) {
//                Number value = null;
//                if (row instanceof ProcessorFilterRow) {
//                    final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
//                    value = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getEventCount();
//                }
//                return value;
//            }
//        }, "Events", ColumnSizeConstants.MEDIUM_COL);
//    }

    private void addStatusColumn() {
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                return ProcessorStatusUtil.getValue(row);
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
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();
                    processorFilter.setEnabled(value.toBoolean());

                    processorFilterEnabledSaveQueue.setValue(processorFilter.getId(), value.toBoolean());
//                    final Rest<ProcessorFilter> rest = restFactory.create();
//                    rest.call(PROCESSOR_FILTER_RESOURCE).setEnabled(processorFilter.getId(), value.toBoolean());

                } else if (row instanceof final ProcessorRow processorRow) {
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
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    reprocess = processorFilterRow.getProcessorFilter().isReprocess()
                            ? "True"
                            : "False";
                }
                return reprocess;
            }
        }, "Reprocess", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addRunAsUserColumn() {
        dataGrid.addResizableColumn(
                DataGridUtil.userRefColumnBuilder(
                                (final ProcessorListRow row) -> {
                                    if (row instanceof final ProcessorFilterRow processorFilterRow) {
                                        return Optional
                                                .of(processorFilterRow)
                                                .map(ProcessorFilterRow::getProcessorFilter)
                                                .map(ProcessorFilter::getRunAsUser)
                                                .orElse(null);
                                    } else {
                                        return null;
                                    }
                                },
                                getEventBus(),
                                securityContext,
                                true,
                                DisplayType.AUTO)
                        .enabledWhen((final ProcessorListRow row) -> {
                            if (row instanceof final ProcessorFilterRow processorFilterRow) {
                                return Optional
                                        .of(processorFilterRow)
                                        .map(ProcessorFilterRow::getProcessorFilter)
                                        .map(ProcessorFilter::getRunAsUser)
                                        .map(UserRef::isEnabled)
                                        .orElse(true);
                            } else {
                                return true;
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("Run As User")
                        .withToolTip("The processor will run with the same permissions as the Run As User.")
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);
    }

    private void addEndColumn() {
        dataGrid.addEndColumn(new EndColumn<>());
    }

    public MultiSelectionModel<ProcessorListRow> getSelectionModel() {
        return selectionModel;
    }

    @Override
    public void refresh() {
        if (!initiated) {
            initiated = true;
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    @Override
    public void read(final DocRef docRef, final Object document, final boolean readOnly) {
        if (docRef == null) {
            expression = ProcessorFilterExpressionUtil.createBasicExpression();
        } else if (PipelineDoc.TYPE.equals(docRef.getType())) {
            expression = ProcessorFilterExpressionUtil.createPipelineExpression(docRef);
        } else if (AnalyticRuleDoc.TYPE.equals(docRef.getType())) {
            expression = ProcessorFilterExpressionUtil.createAnalyticRuleExpression(docRef);
        } else {
            expression = ProcessorFilterExpressionUtil.createFolderExpression(docRef);
        }

        refresh();
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    void setNextSelection(final ProcessorListRow nextSelection) {
        this.nextSelection = nextSelection;
    }

    public ProcessorListRowResultPage getCurrentResultPageResponse() {
        return currentResultPageResponse;
    }
}
