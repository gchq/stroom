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

package stroom.data.client.presenter;

import stroom.cell.info.client.InfoColumn;
import stroom.data.client.presenter.OpenLinkUtil.LinkType;
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
import stroom.query.api.ExpressionOperator;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class ProcessorTaskListPresenter
        extends MyPresenterWidget<PagerView>
        implements HasDocumentRead<Object> {

    private static final ProcessorTaskResource PROCESSOR_TASK_RESOURCE = GWT.create(ProcessorTaskResource.class);
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final RestFactory restFactory;
    private final MyDataGrid<ProcessorTask> dataGrid;
    private final TooltipPresenter tooltipPresenter;
    private final DateTimeFormatter dateTimeFormatter;
    private final ExpressionCriteria criteria;
    private RestDataProvider<ProcessorTask, ResultPage<ProcessorTask>> dataProvider;

    @Inject
    public ProcessorTaskListPresenter(final EventBus eventBus,
                                      final PagerView view,
                                      final RestFactory restFactory,
                                      final TooltipPresenter tooltipPresenter,
                                      final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;

        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);

        this.tooltipPresenter = tooltipPresenter;
        this.dateTimeFormatter = dateTimeFormatter;

        criteria = new ExpressionCriteria();
        addColumns();
    }

    @Override
    protected void onBind() {
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    private void addColumns() {
        // Info column.
        dataGrid.addColumn(new InfoColumn<ProcessorTask>() {
            @Override
            protected void showInfo(final ProcessorTask row, final PopupPosition popupPosition) {
                final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
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
                        .taskMonitorFactory(getView())
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

        final Function<ProcessorTask, String> feedExtractionFunction = row -> {
            if (row.getFeedName() != null) {
                return row.getFeedName();
            } else {
                return null;
            }
        };

        DataGridUtil.addFeedColumn(getEventBus(), dataGrid, "Feed", feedExtractionFunction);

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
        final Function<ProcessorTask, DocRef> pipelineExtractionFunction = row -> {
            if (row.getProcessorFilter() != null) {
                return row.getProcessorFilter().getPipeline();
            }
            return null;
        };

        DataGridUtil.addDocRefColumn(getEventBus(), dataGrid, "Pipeline", pipelineExtractionFunction);

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
                .row(SafeHtmlUtils.fromString("Feed"),
                        OpenLinkUtil.render(processorTask.getFeedName(), LinkType.FEED))
                .row();

        if (meta != null) {
            tb
                    .row(TableCell.header("Stream", 2))
                    .row(SafeHtmlUtils.fromString("Stream Id"),
                            OpenLinkUtil.render(String.valueOf(meta.getId()), LinkType.STREAM))
                    .row("Status", meta.getStatus().getDisplayValue());

            if (meta.getParentMetaId() != null) {
                tb.row(SafeHtmlUtils.fromString("Parent Stream Id"),
                        OpenLinkUtil.render(String.valueOf(meta.getParentMetaId()), LinkType.STREAM));
            }

            tb
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

        OpenLinkUtil.addClickHandler(this, tooltipPresenter.getView().asWidget());
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
        } else if (PipelineDoc.TYPE.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createPipelineExpression(docRef));
        } else if (FeedDoc.TYPE.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createFeedExpression(docRef));
        } else if (ExplorerConstants.FOLDER_TYPE.equals(docRef.getType())) {
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
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<ProcessorTask, ResultPage<ProcessorTask>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<ProcessorTask>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    if (criteria.getExpression() != null) {
                        CriteriaUtil.setRange(criteria, range);
                        CriteriaUtil.setSortList(criteria, dataGrid.getColumnSortList());
                        restFactory
                                .create(PROCESSOR_TASK_RESOURCE)
                                .method(res -> res.find(criteria))
                                .onSuccess(dataConsumer)
                                .onFailure(errorHandler)
                                .taskMonitorFactory(getView())
                                .exec();
                    }
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }
}
