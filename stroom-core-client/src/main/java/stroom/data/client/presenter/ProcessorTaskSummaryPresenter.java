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
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorTaskExpressionUtil;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ProcessorTaskSummaryPresenter extends MyPresenterWidget<PagerView>
        implements HasDocumentRead<Object> {

    private static final ProcessorTaskResource PROCESSOR_TASK_RESOURCE = GWT.create(ProcessorTaskResource.class);

    private final MyDataGrid<ProcessorTaskSummary> dataGrid;
    private final MultiSelectionModelImpl<ProcessorTaskSummary> selectionModel;
    private final RestDataProvider<ProcessorTaskSummary, ResultPage<ProcessorTaskSummary>> dataProvider;
    private final ExpressionCriteria criteria;
    private boolean initialised;

    @Inject
    public ProcessorTaskSummaryPresenter(final EventBus eventBus,
                                         final PagerView view,
                                         final RestFactory restFactory,
                                         final TooltipPresenter tooltipPresenter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<ProcessorTaskSummary, ResultPage<ProcessorTaskSummary>>(eventBus,
                criteria.obtainPageRequest()) {
            @Override
            protected void exec(final Consumer<ResultPage<ProcessorTaskSummary>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                final Rest<ResultPage<ProcessorTaskSummary>> rest = restFactory.create();
                rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(PROCESSOR_TASK_RESOURCE).findSummary(
                        criteria);
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

        // Info column.
        final InfoColumn<ProcessorTaskSummary> infoColumn = new InfoColumn<ProcessorTaskSummary>() {
            @Override
            protected void showInfo(final ProcessorTaskSummary row, final int x, final int y) {
                final TooltipUtil.Builder builder = TooltipUtil.builder()
                        .addTwoColTable(tableBuilder -> {
                            tableBuilder.addHeaderRow("Key Data");
                            final DocRef pipeline = row.getPipeline();
                            if (pipeline != null) {
                                tableBuilder.addRow("Pipeline", DocRefUtil.createSimpleDocRefString(pipeline));
                            }
                            return tableBuilder
                                    .addRow("Feed", row.getFeed())
                                    .addRow("Priority", row.getPriority())
                                    .addRow("Status", row.getStatus())
                                    .build();
                        });
                tooltipPresenter.show(builder.build(), x, y);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        dataGrid.addResizableColumn(new Column<ProcessorTaskSummary, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorTaskSummary row) {
                return row.getPipeline().getName();
            }
        }, "Pipeline", ColumnSizeConstants.BIG_COL);

        dataGrid.addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(), ProcessorTaskFields.FIELD_FEED, true) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return row.getFeed();
                    }
                }, "Feed", ColumnSizeConstants.BIG_COL);

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

        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                criteria.setSort(orderByColumn.getField(), !event.isSortAscending(), orderByColumn.isIgnoreCase());
                refresh();
            }
        });
    }

    public MultiSelectionModel<ProcessorTaskSummary> getSelectionModel() {
        return selectionModel;
    }

    private void setPipeline(final DocRef pipeline) {
        criteria.setExpression(ProcessorTaskExpressionUtil.createPipelineExpression(pipeline));
        refresh();
    }

    private void setFeed(final DocRef feed) {
        criteria.setExpression(ProcessorTaskExpressionUtil.createFeedExpression(feed));
        refresh();
    }

    private void setFolder(final DocRef folder) {
        criteria.setExpression(ProcessorTaskExpressionUtil.createFolderExpression(folder));
        refresh();
    }

    private void setNullCriteria() {
        criteria.setExpression(null);
        refresh();
    }

    @Override
    public void read(final DocRef docRef, final Object entity) {
        if (entity instanceof PipelineDoc) {
            setPipeline(docRef);
        } else if (entity instanceof FeedDoc) {
            setFeed(docRef);
        } else if (docRef != null) {
            setFolder(docRef);
        } else {
            setNullCriteria();
        }
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
