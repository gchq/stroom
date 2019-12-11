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
import stroom.entity.shared.NamedEntity;
import stroom.explorer.shared.ExplorerConstants;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.FindProcessorTaskAction;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskDataSource;
import stroom.processor.shared.ProcessorTaskExpressionUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import java.util.ArrayList;

public class ProcessorTaskListPresenter extends MyPresenterWidget<DataGridView<ProcessorTask>> implements HasDocumentRead<SharedObject> {
    private final FindActionDataProvider<ExpressionCriteria, ProcessorTask> dataProvider;
    private final ExpressionCriteria criteria;

    @Inject
    public ProcessorTaskListPresenter(final EventBus eventBus,
                                      final ClientDispatchAsync dispatcher,
                                      final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<>(false));

        // Info column.
        getView().addColumn(new InfoColumn<ProcessorTask>() {
            @Override
            protected void showInfo(final ProcessorTask row, final int x, final int y) {
                final StringBuilder html = new StringBuilder();
                TooltipUtil.addHeading(html, "Stream Task");
                TooltipUtil.addRowData(html, "Stream Task Id", row.getId());
                TooltipUtil.addRowData(html, "Status", row.getStatus().getDisplayValue());

                if (row.getProcessorFilter() != null) {
                    TooltipUtil.addRowData(html, "Priority", row.getProcessorFilter().getPriority());
                }

                TooltipUtil.addRowData(html, "Status Time", toDateString(row.getStatusTimeMs()));
                TooltipUtil.addRowData(html, "Start Time", toDateString(row.getStartTimeMs()));
                TooltipUtil.addRowData(html, "End Time", toDateString(row.getEndTimeMs()));
                TooltipUtil.addRowData(html, "Node", row.getNodeName());
                TooltipUtil.addRowData(html, "Feed", row.getFeedName());

                // TODO : @66 REINSTATE STREAM DETAILS FOR A TASK

//                TooltipUtil.addBreak(html);
//                TooltipUtil.addHeading(html, "Stream");
//                TooltipUtil.addRowData(html, "Stream Id", row.getMeta().getId());
//                TooltipUtil.addRowData(html, "Status", row.getMeta().getStatus().getDisplayValue());
//                TooltipUtil.addRowData(html, "Parent Stream Id", row.getMeta().getParentMetaId());
//                TooltipUtil.addRowData(html, "Created", toDateString(row.getMeta().getCreateTimeMs()));
//                TooltipUtil.addRowData(html, "Effective", toDateString(row.getMeta().getEffectiveMs()));
//                TooltipUtil.addRowData(html, "Stream Type", row.getMeta().getTypeName());
//                TooltipUtil.addRowData(html, "Feed", row.getMeta().getFeedName());

                if (row.getProcessorFilter() != null) {
                    if (row.getProcessorFilter().getProcessor() != null) {
                        if (row.getProcessorFilter().getProcessor().getPipelineUuid() != null) {
                            TooltipUtil.addBreak(html);
                            TooltipUtil.addHeading(html, "Stream Processor");
                            TooltipUtil.addRowData(html, "Stream Processor Id",
                                    row.getProcessorFilter().getProcessor().getId());
                            TooltipUtil.addRowData(html, "Stream Processor Filter Id",
                                    row.getProcessorFilter().getId());
                            if (row.getProcessorFilter().getProcessor().getPipelineUuid() != null) {
                                TooltipUtil.addRowData(html, "Stream Processor Pipeline",
                                        row.getProcessorFilter().getProcessor().getPipelineUuid());
                            }
                        }
                    }
                }

                tooltipPresenter.setHTML(html.toString());

                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(ProcessorTaskListPresenter.this, tooltipPresenter, PopupType.POPUP, popupPosition,
                        null);
            }
        }, "<br/>", ColumnSizeConstants.ICON_COL);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(new TextCell(), ProcessorTaskDataSource.FIELD_CREATE_TIME, false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return ClientDateUtil.toISOString(row.getCreateTimeMs());
                    }
                }, "Create", ColumnSizeConstants.DATE_COL);

        getView().addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(new TextCell(), ProcessorTaskDataSource.FIELD_STATUS, false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return row.getStatus().getDisplayValue();
                    }
                }, "Status", 80);

        getView()
                .addColumn(new OrderByColumn<ProcessorTask, String>(new TextCell(), ProcessorTaskDataSource.FIELD_NODE, true) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        if (row.getNodeName() != null) {
                            return row.getNodeName();
                        } else {
                            return "";
                        }
                    }
                }, "Node", 100);
        getView()
                .addColumn(new OrderByColumn<ProcessorTask, String>(new TextCell(), ProcessorTaskDataSource.FIELD_FEED, true) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        if (row.getFeedName() != null) {
                            return row.getFeedName();
                        } else {
                            return "";
                        }
                    }
                }, "Feed", 100);
        getView().addColumn(new OrderByColumn<ProcessorTask, String>(new TextCell(), ProcessorTaskDataSource.FIELD_PRIORITY, false) {
            @Override
            public String getValue(final ProcessorTask row) {
                if (row.getProcessorFilter() != null) {
                    return String.valueOf(row.getProcessorFilter().getPriority());
                }

                return "";
            }
        }, "Priority", 100);
        getView().addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(new TextCell(), ProcessorTaskDataSource.FIELD_PIPELINE, true) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        if (row.getProcessorFilter() != null) {
                            if (row.getProcessorFilter().getProcessor() != null) {
                                if (row.getProcessorFilter().getProcessor().getPipelineUuid() != null) {
                                    return row.getProcessorFilter().getProcessor().getPipelineUuid();
                                }
                            }
                        }
                        return "";

                    }
                }, "Pipeline", 200);
        getView().addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(new TextCell(), ProcessorTaskDataSource.FIELD_START_TIME, false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return ClientDateUtil.toISOString(row.getStartTimeMs());
                    }
                }, "Start Time", ColumnSizeConstants.DATE_COL);
        getView().addResizableColumn(
                new OrderByColumn<ProcessorTask, String>(new TextCell(), ProcessorTaskDataSource.FIELD_END_TIME_DATE, false) {
                    @Override
                    public String getValue(final ProcessorTask row) {
                        return ClientDateUtil.toISOString(row.getEndTimeMs());
                    }
                }, "End Time", ColumnSizeConstants.DATE_COL);

        getView().addEndColumn(new EndColumn<>());

        criteria = new ExpressionCriteria();
        dataProvider = new FindActionDataProvider<ExpressionCriteria, ProcessorTask>(dispatcher, getView(), new FindProcessorTaskAction());
    }

    private String toDateString(final Long ms) {
        if (ms != null) {
            return ClientDateUtil.toISOString(ms) + " (" + ms + ")";
        } else {
            return "";
        }
    }

    private String toNameString(final NamedEntity namedEntity) {
        if (namedEntity != null) {
            return namedEntity.getName() + " (" + namedEntity.getId() + ")";
        } else {
            return "";
        }
    }

    @Override
    public void read(final DocRef docRef, final SharedObject entity) {
        if (docRef == null) {
            setExpression(null);
        } else if (PipelineDoc.DOCUMENT_TYPE.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createPipelineExpression(docRef));
//        } else if (FeedDoc.DOCUMENT_TYPE.equals(docRef.getType())) {
//            setExpression(ExpressionUtil.createFeedExpression(docRef));
        } else if (ExplorerConstants.FOLDER.equals(docRef.getType())) {
            setExpression(ProcessorTaskExpressionUtil.createFolderExpression(docRef));
        }
    }

    public void setExpression(final ExpressionOperator expression) {
        criteria.setExpression(expression);
        dataProvider.setCriteria(criteria);
    }

    public void clear() {
        getView().setRowData(0, new ArrayList<>(0));
        getView().setRowCount(0, true);
    }
}