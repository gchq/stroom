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
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorTaskExpressionUtil;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.query.api.ExpressionOperator;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import java.util.function.Function;

public class ProcessorTaskSummaryPresenter extends MyPresenterWidget<PagerView>
        implements HasDocumentRead<Object> {

    private static final ProcessorTaskResource PROCESSOR_TASK_RESOURCE = GWT.create(ProcessorTaskResource.class);

    private final RestFactory restFactory;
    private final TooltipPresenter tooltipPresenter;
    private final MyDataGrid<ProcessorTaskSummary> dataGrid;
    private final MultiSelectionModelImpl<ProcessorTaskSummary> selectionModel;
    private RestDataProvider<ProcessorTaskSummary, ResultPage<ProcessorTaskSummary>> dataProvider;
    private final ExpressionCriteria criteria;

    @Inject
    public ProcessorTaskSummaryPresenter(final EventBus eventBus,
                                         final PagerView view,
                                         final RestFactory restFactory,
                                         final TooltipPresenter tooltipPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.tooltipPresenter = tooltipPresenter;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        criteria = new ExpressionCriteria();
        addColumns();
    }

    @Override
    protected void onBind() {
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    private void addColumns() {
        // Info column.
        final InfoColumn<ProcessorTaskSummary> infoColumn = new InfoColumn<ProcessorTaskSummary>() {
            @Override
            protected void showInfo(final ProcessorTaskSummary row, final PopupPosition popupPosition) {
                final TableBuilder tb = new TableBuilder();
                tb.row(TableCell.header("Key Data", 2));
                final DocRef pipeline = row.getPipeline();
                if (pipeline != null) {
                    tb.row("Pipeline", DocRefUtil.createSimpleDocRefString(pipeline));
                }

                tb
                        .row("Feed", row.getFeed())
                        .row("Priority", String.valueOf(row.getPriority()))
                        .row("Status", String.valueOf(row.getStatus()));

                final HtmlBuilder htmlBuilder = new HtmlBuilder();
                htmlBuilder.div(tb::write, Attribute.className("infoTable"));

                tooltipPresenter.show(htmlBuilder.toSafeHtml(), popupPosition);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        final Function<ProcessorTaskSummary, DocRef> pipelineExtractionFunction = ProcessorTaskSummary::getPipeline;
        DataGridUtil.addDocRefColumn(getEventBus(), dataGrid, "Pipeline", pipelineExtractionFunction);

        final Function<ProcessorTaskSummary, String> feedExtractionFunction = ProcessorTaskSummary::getFeed;
        DataGridUtil.addFeedColumn(getEventBus(), dataGrid, "Feed", feedExtractionFunction);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(),
                        ProcessorTaskFields.FIELD_PRIORITY,
                        false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return String.valueOf(row.getPriority());
                    }
                }, "Priority", 60);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(),
                        ProcessorTaskFields.FIELD_STATUS,
                        false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return row.getStatus().getDisplayValue();
                    }
                }, "Status", ColumnSizeConstants.SMALL_COL);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(),
                        ProcessorTaskFields.FIELD_COUNT,
                        false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return ModelStringUtil.formatCsv(row.getCount());
                    }
                }, "Count", ColumnSizeConstants.SMALL_COL);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    public MultiSelectionModel<ProcessorTaskSummary> getSelectionModel() {
        return selectionModel;
    }

    private void setPipeline(final DocRef pipeline) {
        criteria.setExpression(ProcessorTaskExpressionUtil.createPipelineExpression(pipeline));
    }

    private void setFeed(final DocRef feed) {
        criteria.setExpression(ProcessorTaskExpressionUtil.createFeedExpression(feed));
    }

    private void setFolder(final DocRef folder) {
        criteria.setExpression(ProcessorTaskExpressionUtil.createFolderExpression(folder));
    }

    private void setNullCriteria() {
        criteria.setExpression(null);
    }

    @Override
    public void read(final DocRef docRef, final Object document, final boolean readOnly) {
        if (document instanceof PipelineDoc) {
            setPipeline(docRef);
        } else if (document instanceof FeedDoc) {
            setFeed(docRef);
        } else if (docRef != null) {
            setFolder(docRef);
        } else {
            setNullCriteria();
        }

        refresh();
    }

    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<ProcessorTaskSummary, ResultPage<ProcessorTaskSummary>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<ProcessorTaskSummary>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    CriteriaUtil.setRange(criteria, range);
                    CriteriaUtil.setSortList(criteria, dataGrid.getColumnSortList());
                    restFactory
                            .create(PROCESSOR_TASK_RESOURCE)
                            .method(res -> res.findSummary(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .taskMonitorFactory(getView())
                            .exec();
                }

                @Override
                protected void changeData(final ResultPage<ProcessorTaskSummary> data) {
                    final ProcessorTaskSummary selected = selectionModel.getSelected();
                    if (selected != null) {
                        // Reselect the task set.
                        selectionModel.clear();
                        if (data != null && data.getValues().contains(selected)) {
                            selectionModel.setSelected(selected);
                        }
                    }
                    super.changeData(data);
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    public void setExpression(final ExpressionOperator expressionOperator) {
        criteria.setExpression(expressionOperator);
        refresh();
    }
}
