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
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.OrderByColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorTaskDataSource;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.task.shared.TaskExpressionUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ProcessorTaskSummaryPresenter extends MyPresenterWidget<DataGridView<ProcessorTaskSummary>>
        implements HasDocumentRead<Object> {
    private static final ProcessorTaskResource PROCESSOR_TASK_RESOURCE = GWT.create(ProcessorTaskResource.class);

    private final RestDataProvider<ProcessorTaskSummary, ResultPage<ProcessorTaskSummary>> dataProvider;
    private final ExpressionCriteria criteria;

    @Inject
    public ProcessorTaskSummaryPresenter(final EventBus eventBus,
                                         final RestFactory restFactory,
                                         final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<>(true, false));

        // Info column.
        final InfoColumn<ProcessorTaskSummary> infoColumn = new InfoColumn<ProcessorTaskSummary>() {
            @Override
            protected void showInfo(final ProcessorTaskSummary row, final int x, final int y) {
                final StringBuilder html = new StringBuilder();

                TooltipUtil.addHeading(html, "Key Data");
                final DocRef pipeline = row.getPipeline();
                if (pipeline != null) {
                    TooltipUtil.addRowData(html, "Pipeline", DocRefUtil.createSimpleDocRefString(pipeline));
                }
                TooltipUtil.addRowData(html, "Feed", row.getFeed());
                TooltipUtil.addRowData(html, "Priority", row.getPriority());
                TooltipUtil.addRowData(html, "Status", row.getStatus());

                tooltipPresenter.setHTML(html.toString());

                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(ProcessorTaskSummaryPresenter.this, tooltipPresenter, PopupType.POPUP, popupPosition,
                        null);
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        getView().addResizableColumn(new Column<ProcessorTaskSummary, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorTaskSummary row) {
                return row.getPipeline().getName();
            }
        }, "Pipeline", ColumnSizeConstants.BIG_COL);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(), ProcessorTaskDataSource.FIELD_FEED, true) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return row.getFeed();
                    }
                }, "Feed", ColumnSizeConstants.BIG_COL);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(), ProcessorTaskDataSource.FIELD_PRIORITY, false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return String.valueOf(row.getPriority());
                    }
                }, "Priority", 60);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(), ProcessorTaskDataSource.FIELD_STATUS, false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return row.getStatus().getDisplayValue();
                    }
                }, "Status", ColumnSizeConstants.SMALL_COL);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(), ProcessorTaskDataSource.FIELD_COUNT, false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return ModelStringUtil.formatCsv(row.getCount());
                    }
                }, "Count", ColumnSizeConstants.SMALL_COL);

        getView().addEndColumn(new EndColumn<>());

        criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<ProcessorTaskSummary, ResultPage<ProcessorTaskSummary>>(eventBus, criteria.obtainPageRequest()) {
            @Override
            protected void exec(final Consumer<ResultPage<ProcessorTaskSummary>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                final Rest<ResultPage<ProcessorTaskSummary>> rest = restFactory.create();
                rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(PROCESSOR_TASK_RESOURCE).findSummary(criteria);
            }

            @Override
            protected void changeData(final ResultPage<ProcessorTaskSummary> data) {
                final ProcessorTaskSummary selected = getView().getSelectionModel().getSelected();
                if (selected != null) {
                    // Reselect the task set.
                    getView().getSelectionModel().clear();
                    if (data != null && data.getValues().contains(selected)) {
                        getView().getSelectionModel().setSelected(selected);
                    }
                }
                super.changeData(data);
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());

        getView().addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                if (event.isSortAscending()) {
                    criteria.setSort(orderByColumn.getField(), Sort.Direction.ASCENDING, orderByColumn.isIgnoreCase());
                } else {
                    criteria.setSort(orderByColumn.getField(), Sort.Direction.DESCENDING, orderByColumn.isIgnoreCase());
                }
                dataProvider.refresh();
            }
        });
    }

    public MultiSelectionModel<ProcessorTaskSummary> getSelectionModel() {
        return getView().getSelectionModel();
    }

    private void setPipeline(final PipelineDoc pipelineEntity) {
        criteria.setExpression(TaskExpressionUtil.createPipelineExpression(pipelineEntity));
        dataProvider.refresh();
    }

    private void setFeed(final String feed) {
        criteria.setExpression(TaskExpressionUtil.createFeedExpression(feed));
        dataProvider.refresh();
    }

    private void setFolder(final DocRef folder) {
        criteria.setExpression(TaskExpressionUtil.createFolderExpression(folder));
        dataProvider.refresh();
    }

    private void setNullCriteria() {
        criteria.setExpression(null);
        dataProvider.refresh();
    }

    @Override
    public void read(final DocRef docRef, final Object entity) {
        if (entity instanceof PipelineDoc) {
            setPipeline((PipelineDoc) entity);
        } else if (entity instanceof FeedDoc) {
            setFeed(((FeedDoc) entity).getName());
        } else if (docRef != null) {
            setFolder(docRef);
        } else {
            setNullCriteria();
        }
    }
}
