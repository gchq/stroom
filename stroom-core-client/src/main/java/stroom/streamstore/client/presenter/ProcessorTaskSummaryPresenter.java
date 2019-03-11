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

package stroom.streamstore.client.presenter;

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
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.FindProcessorFilterTaskCriteria;
import stroom.processor.shared.FindProcessorFilterTaskSummaryAction;
import stroom.processor.shared.ProcessorFilterTaskSummaryRow;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultList;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

public class ProcessorTaskSummaryPresenter extends MyPresenterWidget<DataGridView<ProcessorFilterTaskSummaryRow>>
        implements HasDocumentRead<SharedObject> {
    private FindProcessorFilterTaskSummaryAction action = new FindProcessorFilterTaskSummaryAction(new FindProcessorFilterTaskCriteria());
    private ActionDataProvider<ProcessorFilterTaskSummaryRow> dataProvider;

    @Inject
    public ProcessorTaskSummaryPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                         final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<>(true, false));

        // Info column.
        final InfoColumn<ProcessorFilterTaskSummaryRow> infoColumn = new InfoColumn<ProcessorFilterTaskSummaryRow>() {
            @Override
            protected void showInfo(final ProcessorFilterTaskSummaryRow row, final int x, final int y) {
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

        getView().addResizableColumn(new OrderByColumn<ProcessorFilterTaskSummaryRow, String>(new TextCell(),
                FindProcessorFilterTaskCriteria.FIELD_PIPELINE_UUID, true) {
            @Override
            public String getValue(final ProcessorFilterTaskSummaryRow row) {
                return row.getPipeline();
            }
        }, "Pipeline", 250);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorFilterTaskSummaryRow, String>(new TextCell(), FindProcessorFilterTaskCriteria.FIELD_FEED_NAME, true) {
                    @Override
                    public String getValue(final ProcessorFilterTaskSummaryRow row) {
                        return row.getFeed();
                    }
                }, "Feed", 250);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorFilterTaskSummaryRow, String>(new TextCell(), FindProcessorFilterTaskCriteria.FIELD_PRIORITY, false) {
                    @Override
                    public String getValue(final ProcessorFilterTaskSummaryRow row) {
                        return String.valueOf(row.getPriority());
                    }
                }, "Priority", 100);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorFilterTaskSummaryRow, String>(new TextCell(), FindProcessorFilterTaskCriteria.FIELD_STATUS, false) {
                    @Override
                    public String getValue(final ProcessorFilterTaskSummaryRow row) {
                        return row.getStatus().getDisplayValue();
                    }
                }, "Status", 100);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorFilterTaskSummaryRow, String>(new TextCell(), FindProcessorFilterTaskCriteria.FIELD_COUNT, false) {
                    @Override
                    public String getValue(final ProcessorFilterTaskSummaryRow row) {
                        return ModelStringUtil.formatCsv(row.getCount());
                    }
                }, "Count", 100);

        getView().addEndColumn(new EndColumn<>());

        this.dataProvider = new ActionDataProvider<ProcessorFilterTaskSummaryRow>(dispatcher, action) {
            @Override
            protected void changeData(final ResultList<ProcessorFilterTaskSummaryRow> data) {
                super.changeData(data);
                final ProcessorFilterTaskSummaryRow selected = getView().getSelectionModel().getSelected();
                if (selected != null) {
                    // Reselect the task set.
                    getView().getSelectionModel().clear();
                    if (data != null && data.contains(selected)) {
                        getView().getSelectionModel().setSelected(selected);
                    }
                }
            }
        };
        dataProvider.addDataDisplay(getView().getDataDisplay());
    }

    public MultiSelectionModel<ProcessorFilterTaskSummaryRow> getSelectionModel() {
        return getView().getSelectionModel();
    }

    private void setFeedCriteria(final String feedName) {
//        final FindProcessorFilterTaskCriteria criteria = initCriteria();
//        criteria.obtainFeedNameSet().add(feedName);
//        dataProvider.setCriteria(criteria);
    }

    private void setPipelineCriteria(final DocRef pipelineRef) {
        final FindProcessorFilterTaskCriteria criteria = initCriteria();
        criteria.obtainPipelineUuidCriteria().setString(pipelineRef.getUuid());
        action.setCriteria(criteria);
        dataProvider.refresh();
    }

    private void setNullCriteria() {
        action.setCriteria(initCriteria());
        dataProvider.refresh();
    }

    @Override
    public void read(final DocRef docRef, final SharedObject entity) {
        if (entity instanceof FeedDoc) {
            setFeedCriteria(docRef.getName());
        } else if (entity instanceof PipelineDoc) {
            setPipelineCriteria(docRef);
        } else {
            setNullCriteria();
        }
    }

    private FindProcessorFilterTaskCriteria initCriteria() {
        final FindProcessorFilterTaskCriteria criteria = new FindProcessorFilterTaskCriteria();
        criteria.obtainStatusSet().setSingleItem(Status.UNLOCKED);
        return criteria;
    }
}
