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

import com.google.gwt.cell.client.TextCell;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.info.client.InfoColumn;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.OrderByColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.docref.DocRef;
import stroom.docref.SharedObject;
import stroom.entity.client.presenter.FindActionDataProvider;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.FindProcessorTaskSummaryAction;
import stroom.processor.shared.ProcessorTaskDataSource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.task.shared.ExpressionUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultList;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

public class ProcessorTaskSummaryPresenter extends MyPresenterWidget<DataGridView<ProcessorTaskSummary>>
        implements HasDocumentRead<SharedObject> {
    private final FindActionDataProvider<ExpressionCriteria, ProcessorTaskSummary> dataProvider;
    private final ExpressionCriteria criteria;

    @Inject
    public ProcessorTaskSummaryPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                         final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<>(true, false));

        // Info column.
        final InfoColumn<ProcessorTaskSummary> infoColumn = new InfoColumn<ProcessorTaskSummary>() {
            @Override
            protected void showInfo(final ProcessorTaskSummary row, final int x, final int y) {
                final StringBuilder html = new StringBuilder();

                TooltipUtil.addHeading(html, "Key Data");
                TooltipUtil.addRowData(html, "Pipeline", row.getPipeline());
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

        getView().addResizableColumn(new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(),
                ProcessorTaskDataSource.FIELD_PIPELINE, true) {
            @Override
            public String getValue(final ProcessorTaskSummary row) {
                return row.getPipeline().getName();
            }
        }, "Pipeline", 250);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(), ProcessorTaskDataSource.FIELD_FEED, true) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return row.getFeed();
                    }
                }, "Feed", 250);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(), ProcessorTaskDataSource.FIELD_PRIORITY, false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return String.valueOf(row.getPriority());
                    }
                }, "Priority", 100);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(), ProcessorTaskDataSource.FIELD_STATUS, false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return row.getStatus().getDisplayValue();
                    }
                }, "Status", 100);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTaskSummary, String>(new TextCell(), ProcessorTaskDataSource.FIELD_COUNT, false) {
                    @Override
                    public String getValue(final ProcessorTaskSummary row) {
                        return ModelStringUtil.formatCsv(row.getCount());
                    }
                }, "Count", 100);

        getView().addEndColumn(new EndColumn<>());

        criteria = new ExpressionCriteria();
        dataProvider = new FindActionDataProvider<ExpressionCriteria, ProcessorTaskSummary>(dispatcher, getView(), new FindProcessorTaskSummaryAction()) {
            @Override
            protected ResultList<ProcessorTaskSummary> processData(final ResultList<ProcessorTaskSummary> data) {
                final ProcessorTaskSummary selected = getView().getSelectionModel().getSelected();
                if (selected != null) {
                    // Reselect the task set.
                    getView().getSelectionModel().clear();
                    if (data != null && data.contains(selected)) {
                        getView().getSelectionModel().setSelected(selected);
                    }
                }
                return super.processData(data);
            }
        };
    }

    public MultiSelectionModel<ProcessorTaskSummary> getSelectionModel() {
        return getView().getSelectionModel();
    }

    private void setPipeline(final PipelineDoc pipelineEntity) {
        criteria.setExpression(ExpressionUtil.createPipelineExpression(pipelineEntity));
        dataProvider.setCriteria(criteria);
    }

    private void setFeed(final String feed) {
        criteria.setExpression(ExpressionUtil.createFeedExpression(feed));
        dataProvider.setCriteria(criteria);
    }

    private void setFolder(final DocRef folder) {
        criteria.setExpression(ExpressionUtil.createFolderExpression(folder));
        dataProvider.setCriteria(criteria);
    }

    private void setNullCriteria() {
        criteria.setExpression(null);
        dataProvider.setCriteria(criteria);
    }

    @Override
    public void read(final DocRef docRef, final SharedObject entity) {
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
