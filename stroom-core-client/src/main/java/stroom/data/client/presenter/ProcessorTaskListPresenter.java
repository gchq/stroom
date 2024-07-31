/*
 * Copyright 2024 Crown Copyright
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
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
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
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Optional;
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
                                final RestErrorHandler errorHandler) {
                if (criteria.getExpression() != null) {
                    CriteriaUtil.setRange(criteria, range);
                    restFactory
                            .create(PROCESSOR_TASK_RESOURCE)
                            .method(res -> res.find(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .taskListener(view)
                            .exec();
                }
            }
        };

        // Info column.
        dataGrid.addColumn(new InfoColumn<ProcessorTask>() {
            @Override
            protected void showInfo(final ProcessorTask row, final PopupPosition popupPosition) {
                FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
                findMetaCriteria.setExpression(MetaExpressionUtil.createDataIdExpression(row.getMetaId()));

                restFactory
                        .create(META_RESOURCE)
                        .method(res -> res.findMetaRow(findMetaCriteria))
                        .onSuccess(metaRows -> {
                            // Should only get one back
                            final Meta meta = Optional.ofNullable(metaRows)
                                    .map(ResultPage::getFirst)
                                    .map(MetaRow::getMeta)
                                    .orElse(null);
                            showTooltip(popupPosition, row, meta);
                        })
                        .taskListener(getView())
                        .exec();
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
                .addResizableColumn(new OrderByColumn<ProcessorTask, DocRef>(
                        new DocRefCell(getEventBus(), true),
                        ProcessorTaskFields.FIELD_FEED,
                        true) {
                    @Override
                    public DocRef getValue(final ProcessorTask row) {
                        if (row.getFeedName() != null) {
                            return new DocRef(FeedDoc.DOCUMENT_TYPE, null, row.getFeedName());
                        } else {
                            return null;
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
                new Column<ProcessorTask, DocRef>(new DocRefCell(getEventBus(), false)) {
                    @Override
                    public DocRef getValue(final ProcessorTask row) {
                        if (row.getProcessorFilter() != null) {
                            return row.getProcessorFilter().getPipeline();
                        }
                        return null;
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
                //noinspection PatternVariableCanBeUsed // GWT
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
                refresh();
            }
        });
    }

    private void showTooltip(final PopupPosition popupPosition,
                             final ProcessorTask processorTask,
                             final Meta meta) {

        final TableBuilder tb = new TableBuilder();
        tb
                .row(TableCell.header("Stream Task", 2))
                .row("Stream Task Id", String.valueOf(processorTask.getId()))
                .row("Status", processorTask.getStatus().getDisplayValue());

        if (processorTask.getProcessorFilter() != null) {
            tb.row("Priority", String.valueOf(processorTask.getProcessorFilter().getPriority()));
        }

        tb
                .row("Status Time", toDateString(processorTask.getStatusTimeMs()))
                .row("Start Time", toDateString(processorTask.getStartTimeMs()))
                .row("End Time", toDateString(processorTask.getEndTimeMs()))
                .row("Node", processorTask.getNodeName())
                .row("Feed", processorTask.getFeedName())
                .row();

        if (meta != null) {
            tb
                    .row(TableCell.header("Stream", 2))
                    .row("Stream Id", String.valueOf(meta.getId()))
                    .row("Status", meta.getStatus().getDisplayValue())
                    .row("Parent Stream Id", String.valueOf(meta.getParentMetaId()))
                    .row("Created", toDateString(meta.getCreateMs()))
                    .row("Effective", toDateString(meta.getEffectiveMs()))
                    .row("Stream Type", meta.getTypeName());
        } else {
            tb
                    .row(TableCell.data("[Physically deleted]", 2));
        }

        if (processorTask.getProcessorFilter() != null) {
            if (processorTask.getProcessorFilter().getProcessor() != null) {
                if (processorTask.getProcessorFilter().getProcessor().getPipelineUuid() != null) {
                    tb
                            .row()
                            .row(TableCell.header("Stream Processor", 2))
                            .row("Stream Processor Id",
                                    String.valueOf(processorTask.getProcessorFilter().getProcessor().getId()))
                            .row("Stream Processor Filter Id",
                                    String.valueOf(processorTask.getProcessorFilter().getId()));
                    if (processorTask.getProcessorFilter().getProcessor().getPipelineUuid() != null) {
                        tb.row("Stream Processor Pipeline",
                                DocRefUtil.createSimpleDocRefString(
                                        processorTask.getProcessorFilter().getPipeline()));
                    }
                }
            }
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        tooltipPresenter.show(htmlBuilder.toSafeHtml(), popupPosition);
    }

    private String toDateString(final Long ms) {
        if (ms != null) {
            return dateTimeFormatter.formatWithDuration(ms);
        } else {
            return "";
        }
    }

    @Override
    public void read(final DocRef docRef, final Object document, final boolean readOnly) {
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
