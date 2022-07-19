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

package stroom.data.client.presenter;

import stroom.cell.info.client.InfoColumn;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.preferences.client.DateTimeFormatter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskExpressionUtil;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.ResultPage;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.function.Consumer;

public class ProcessorTaskListPresenter
        extends MyPresenterWidget<PagerView>
        implements HasDocumentRead<Object> {

    private static final ProcessorTaskResource PROCESSOR_TASK_RESOURCE = GWT.create(ProcessorTaskResource.class);
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final MyDataGrid<ProcessorTask> dataGrid;
    private final TooltipPresenter tooltipPresenter;
    private final DateTimeFormatter dateTimeFormatter;
    private final RestDataProvider<ProcessorTask, ResultPage<ProcessorTask>> dataProvider;
    private final ExpressionCriteria criteria;
    private boolean initialised;

    @Inject
    public ProcessorTaskListPresenter(final EventBus eventBus,
                                      final PagerView view,
                                      final RestFactory restFactory,
                                      final TooltipPresenter tooltipPresenter,
                                      final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        view.setDataWidget(dataGrid);

        this.tooltipPresenter = tooltipPresenter;
        this.dateTimeFormatter = dateTimeFormatter;

        criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<ProcessorTask, ResultPage<ProcessorTask>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<ProcessorTask>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                if (criteria.getExpression() != null) {
                    CriteriaUtil.setRange(criteria, range);
                    final Rest<ResultPage<ProcessorTask>> rest = restFactory.create();
                    rest
                            .onSuccess(dataConsumer)
                            .onFailure(throwableConsumer)
                            .call(PROCESSOR_TASK_RESOURCE)
                            .find(criteria);
                }
            }
        };

        // Info column.
        dataGrid.addColumn(new InfoColumn<ProcessorTask>() {
            @Override
            protected void showInfo(final ProcessorTask row, final int x, final int y) {
                final Rest<ResultPage<MetaRow>> rest = restFactory.create();
                rest
                        .onSuccess(metaRows -> {
                            if (metaRows != null && metaRows.size() == 1) {
                                final MetaRow metaRow = metaRows.getFirst();
                                showTooltip(x, y, row, metaRow);
                            }
                        })
                        .call(META_RESOURCE)
                        .findMetaRow(FindMetaCriteria.createFromId(row.getMetaId()));
            }
        }, "<br/>", ColumnSizeConstants.ICON_COL);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(
                        new TextCell(),
                        ProcessorTaskFields.FIELD_CREATE_TIME,
                        false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return dateTimeFormatter.format(row.getCreateTimeMs());
                    }
                }, "Create", ColumnSizeConstants.DATE_COL);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(
                        new TextCell(),
                        ProcessorTaskFields.FIELD_STATUS,
                        false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return row.getStatus().getDisplayValue();
                    }
                }, "Status", 80);

        dataGrid
                .addResizableColumn(new OrderByColumn<ProcessorTask, String>(
                        new TextCell(), ProcessorTaskFields.FIELD_NODE, true) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        if (row.getNodeName() != null) {
                            return row.getNodeName();
                        } else {
                            return "";
                        }
                    }
                }, "Node", ColumnSizeConstants.MEDIUM_COL);
        dataGrid
                .addResizableColumn(new OrderByColumn<ProcessorTask, String>(
                        new TextCell(), ProcessorTaskFields.FIELD_FEED, true) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        if (row.getFeedName() != null) {
                            return row.getFeedName();
                        } else {
                            return "";
                        }
                    }
                }, "Feed", ColumnSizeConstants.BIG_COL);
        dataGrid.addResizableColumn(new OrderByColumn<ProcessorTask, String>(
                new TextCell(), ProcessorTaskFields.FIELD_PRIORITY, false) {
            @Override
            public String getValue(final ProcessorTask row) {
                if (row.getProcessorFilter() != null) {
                    return String.valueOf(row.getProcessorFilter().getPriority());
                }

                return "";
            }
        }, "Priority", 60);
        dataGrid.addResizableColumn(
                new Column<ProcessorTask, String>(new TextCell()) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        if (row.getProcessorFilter() != null) {
                            if (row.getProcessorFilter().getPipelineName() != null) {
                                return row.getProcessorFilter().getPipelineName();
                            }
                        }
                        return "";

                    }
                }, "Pipeline", ColumnSizeConstants.BIG_COL);
        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(
                        new TextCell(), ProcessorTaskFields.FIELD_START_TIME, false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return dateTimeFormatter.format(row.getStartTimeMs());
                    }
                }, "Start Time", ColumnSizeConstants.DATE_COL);
        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(
                        new TextCell(), ProcessorTaskFields.FIELD_END_TIME_DATE, false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return dateTimeFormatter.format(row.getEndTimeMs());
                    }
                }, "End Time", ColumnSizeConstants.DATE_COL);

        dataGrid.addEndColumn(new EndColumn<>());

        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
                refresh();
            }
        });
    }

    private void showTooltip(final int x, final int y, final ProcessorTask processorTask, final MetaRow metaRow) {
        final Meta meta = metaRow.getMeta();

        final TooltipUtil.Builder builder = TooltipUtil.builder()
                .addTwoColTable(tableBuilder -> {
                    tableBuilder.addHeaderRow("Stream Task")
                            .addRow("Stream Task Id", processorTask.getId())
                            .addRow("Status", processorTask.getStatus().getDisplayValue());

                    if (processorTask.getProcessorFilter() != null) {
                        tableBuilder.addRow("Priority", processorTask.getProcessorFilter().getPriority());
                    }

                    tableBuilder
                            .addRow("Status Time", toDateString(processorTask.getStatusTimeMs()))
                            .addRow("Start Time", toDateString(processorTask.getStartTimeMs()))
                            .addRow("End Time", toDateString(processorTask.getEndTimeMs()))
                            .addRow("Node", processorTask.getNodeName())
                            .addRow("Feed", processorTask.getFeedName())
                            .addBlankRow()
                            .addHeaderRow("Stream")
                            .addRow("Stream Id", meta.getId())
                            .addRow("Status", meta.getStatus().getDisplayValue())
                            .addRow("Parent Stream Id", meta.getParentMetaId())
                            .addRow("Created", toDateString(meta.getCreateMs()))
                            .addRow("Effective", toDateString(meta.getEffectiveMs()))
                            .addRow("Stream Type", meta.getTypeName());

                    if (processorTask.getProcessorFilter() != null) {
                        if (processorTask.getProcessorFilter().getProcessor() != null) {
                            if (processorTask.getProcessorFilter().getProcessor().getPipelineUuid() != null) {
                                tableBuilder
                                        .addBlankRow()
                                        .addHeaderRow("Stream Processor")
                                        .addRow("Stream Processor Id",
                                                processorTask.getProcessorFilter().getProcessor().getId())
                                        .addRow("Stream Processor Filter Id",
                                                processorTask.getProcessorFilter().getId());
                                if (processorTask.getProcessorFilter().getProcessor().getPipelineUuid() != null) {
                                    tableBuilder.addRow("Stream Processor Pipeline",
                                            DocRefUtil.createSimpleDocRefString(
                                                    processorTask.getProcessorFilter().getPipeline()));
                                }
                            }
                        }
                    }
                    return tableBuilder.build();
                });
        tooltipPresenter.show(builder.build(), x, y);
    }

    private String toDateString(final Long ms) {
        if (ms != null) {
            return dateTimeFormatter.format(ms) + " (" + ms + ")";
        } else {
            return "";
        }
    }

    @Override
    public void read(final DocRef docRef, final Object entity) {
        if (docRef == null) {
            setExpression(null);
        } else if (PipelineDoc.DOCUMENT_TYPE.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createPipelineExpression(docRef));
        } else if (FeedDoc.DOCUMENT_TYPE.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createFeedExpression(docRef));
        } else if (ExplorerConstants.FOLDER.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createFolderExpression(docRef));
        }
    }

    public void setExpression(final ExpressionOperator expression) {
        criteria.setExpression(expression);
        refresh();
    }

    public void clear() {
        dataGrid.setRowData(0, new ArrayList<>(0));
        dataGrid.setRowCount(0, true);
    }

    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }
}
