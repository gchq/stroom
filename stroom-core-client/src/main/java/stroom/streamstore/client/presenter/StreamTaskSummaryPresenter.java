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
import stroom.entity.client.presenter.HasRead;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.ResultList;
import stroom.entity.shared.SummaryDataRow;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.TaskStatus;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

public class StreamTaskSummaryPresenter extends MyPresenterWidget<DataGridView<SummaryDataRow>>
        implements HasRead<BaseEntity> {
    private EntityServiceFindSummaryActionDataProvider<FindStreamTaskCriteria> dataProvider;

    @Inject
    public StreamTaskSummaryPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                      final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<>(true, false));

        // Info column.
        final InfoColumn<SummaryDataRow> infoColumn = new InfoColumn<SummaryDataRow>() {
            @Override
            protected void showInfo(final SummaryDataRow row, final int x, final int y) {
                final StringBuilder html = new StringBuilder();

                TooltipUtil.addHeading(html, "Key Data");
                TooltipUtil.addRowData(html, "Pipeline", row.getLabel().get(FindStreamTaskCriteria.SUMMARY_POS_PIPELINE)
                        + " (" + row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_PIPELINE) + ")");
                TooltipUtil.addRowData(html, "Feed", row.getLabel().get(FindStreamTaskCriteria.SUMMARY_POS_FEED) + " ("
                        + row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_FEED) + ")");
                TooltipUtil.addRowData(html, "Priority", row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_PRIORITY));
                TooltipUtil.addRowData(html, "Status",
                        TaskStatus.PRIMITIVE_VALUE_CONVERTER
                                .fromPrimitiveValue(
                                        row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_STATUS).byteValue())
                                .getDisplayValue() + " (" + row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_STATUS)
                                + ")");

                tooltipPresenter.setHTML(html.toString());

                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(StreamTaskSummaryPresenter.this, tooltipPresenter, PopupType.POPUP, popupPosition,
                        null);
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        getView().addResizableColumn(new OrderByColumn<SummaryDataRow, String>(new TextCell(),
                FindStreamTaskCriteria.FIELD_PIPELINE_NAME, true) {
            @Override
            public String getValue(final SummaryDataRow row) {
                return row.getLabel().get(FindStreamTaskCriteria.SUMMARY_POS_PIPELINE);
            }
        }, "Pipeline", 250);

        getView().addResizableColumn(
                new OrderByColumn<SummaryDataRow, String>(new TextCell(), FindStreamTaskCriteria.FIELD_FEED_NAME, true) {
                    @Override
                    public String getValue(final SummaryDataRow row) {
                        return row.getLabel().get(FindStreamTaskCriteria.SUMMARY_POS_FEED);
                    }
                }, "Feed", 250);

        getView().addResizableColumn(
                new OrderByColumn<SummaryDataRow, String>(new TextCell(), FindStreamTaskCriteria.FIELD_PRIORITY, false) {
                    @Override
                    public String getValue(final SummaryDataRow row) {
                        return row.getLabel().get(FindStreamTaskCriteria.SUMMARY_POS_PRIORITY);
                    }
                }, "Priority", 100);

        getView().addResizableColumn(
                new OrderByColumn<SummaryDataRow, String>(new TextCell(), FindStreamTaskCriteria.FIELD_STATUS, false) {
                    @Override
                    public String getValue(final SummaryDataRow row) {
                        return TaskStatus.PRIMITIVE_VALUE_CONVERTER
                                .fromPrimitiveValue(
                                        row.getKey().get(FindStreamTaskCriteria.SUMMARY_POS_STATUS).byteValue())
                                .getDisplayValue();
                    }
                }, "Status", 100);

        getView().addResizableColumn(
                new OrderByColumn<SummaryDataRow, String>(new TextCell(), FindStreamTaskCriteria.FIELD_COUNT, false) {
                    @Override
                    public String getValue(final SummaryDataRow row) {
                        return ModelStringUtil.formatCsv(row.getCount());
                    }
                }, "Count", 100);

        getView().addEndColumn(new EndColumn<>());

        this.dataProvider = new EntityServiceFindSummaryActionDataProvider<FindStreamTaskCriteria>(dispatcher,
                getView()) {
            @Override
            protected void afterDataChange(final ResultList<SummaryDataRow> data) {
                final SummaryDataRow selected = getView().getSelectionModel().getSelected();
                if (selected != null) {
                    // Reselect the task set.
                    getView().getSelectionModel().clear();
                    if (data != null && data.contains(selected)) {
                        getView().getSelectionModel().setSelected(selected);
                    }
                }
            }
        };
    }

    public MultiSelectionModel<SummaryDataRow> getSelectionModel() {
        return getView().getSelectionModel();
    }

    private void setCriteria(final Feed feed) {
        final FindStreamTaskCriteria criteria = initCriteria();
        criteria.obtainFeedIdSet().add(feed);
        dataProvider.setCriteria(criteria);
    }

    private void setCriteria(final PipelineEntity pipelineEntity) {
        final FindStreamTaskCriteria criteria = initCriteria();
        criteria.obtainPipelineIdSet().add(pipelineEntity);
        dataProvider.setCriteria(criteria);
    }

    private void setNullCriteria() {
        dataProvider.setCriteria(initCriteria());
    }

    @Override
    public void read(final DocRef docRef, final BaseEntity entity) {
        if (entity instanceof Feed) {
            setCriteria((Feed) entity);
        } else if (entity instanceof PipelineEntity) {
            setCriteria((PipelineEntity) entity);
        } else {
            setNullCriteria();
        }
    }

    private FindStreamTaskCriteria initCriteria() {
        final FindStreamTaskCriteria criteria = new FindStreamTaskCriteria();
        criteria.obtainStatusSet().setSingleItem(StreamStatus.UNLOCKED);
        return criteria;
    }
}
