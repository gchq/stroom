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
import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.IdSet;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.ResultList;
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataStatus;
import stroom.streamstore.shared.FetchFullStreamInfoAction;
import stroom.data.meta.api.DataRow;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public abstract class AbstractStreamListPresenter extends MyPresenterWidget<DataGridView<DataRow>> implements HasDataSelectionHandlers<IdSet>, Refreshable {
    private final TooltipPresenter tooltipPresenter;

    private final IdSet entityIdSet = new IdSet();
    private final ClientDispatchAsync dispatcher;
    protected FindStreamActionDataProvider dataProvider;
    private ResultList<DataRow> resultList = null;

    AbstractStreamListPresenter(final EventBus eventBus,
                                final ClientDispatchAsync dispatcher,
                                final TooltipPresenter tooltipPresenter,
                                final boolean allowSelectAll) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.tooltipPresenter = tooltipPresenter;
        this.dispatcher = dispatcher;

        entityIdSet.setMatchAll(false);

        addColumns(allowSelectAll);

        this.dataProvider = new FindStreamActionDataProvider(dispatcher, getView()) {
            @Override
            protected ResultList<DataRow> processData(final ResultList<DataRow> data) {
                return onProcessData(data);
            }
        };
    }

    public FindStreamActionDataProvider getDataProvider() {
        return dataProvider;
    }

    protected ResultList<DataRow> onProcessData(final ResultList<DataRow> data) {
        boolean equalsList = true;

        // We compare the old and new lists to see if we need to do
        // the work of refreshing selections etc

        // Lists may have same entities but different versions (e.g. status)
        if (data == null && resultList != null) {
            equalsList = false;
        }
        if (data != null && resultList == null) {
            equalsList = false;
        }
        if (data != null && resultList != null) {
            final List<DataRow> oldList = resultList.getValues();
            final List<DataRow> newList = data.getValues();

            if (oldList.size() != newList.size()) {
                equalsList = false;
            } else {
                for (int i = 0; i < oldList.size(); i++) {
                    final Data oldStream = oldList.get(i).getData();
                    final Data newStream = newList.get(i).getData();

                    if (!oldStream.equals(newStream)) {
                        equalsList = false;
                        break;
                    }
                }
            }
        }

        this.resultList = data;

        // List changed in any way ... fire a refresh
        if (!equalsList) {
            // entityIdSet.clear();
            // entityIdSet.setMatchAll(masterEntityIdSet.getMatchAll());
            // if (masterEntityIdSet.getIdSet() != null &&
            // masterEntityIdSet.getIdSet().size() > 0) {
            // for (final StreamAttributeMap map : data) {
            // final long id = map.getData().getId();
            // if (masterEntityIdSet.contains(id)) {
            // entityIdSet.add(id);
            // }
            // }
            // }

            if (entityIdSet.getSet() != null && entityIdSet.getSet().size() > 0) {
                final Boolean oldMatchAll = entityIdSet.getMatchAll();
                final Set<Long> oldIdSet = new HashSet<>(entityIdSet.getSet());
                entityIdSet.clear();
                entityIdSet.setMatchAll(oldMatchAll);
                if (data != null) {
                    for (final DataRow map : data) {
                        final long id = map.getData().getId();
                        if (oldIdSet.contains(id)) {
                            entityIdSet.add(id);
                        }
                    }
                }
            }

            DataSelectionEvent.fire(AbstractStreamListPresenter.this, entityIdSet, false);
        }

        DataRow selected = getView().getSelectionModel().getSelected();
        if (selected != null) {
            if (!resultList.contains(selected)) {
                getView().getSelectionModel().setSelected(selected, false);
            }
        }

        return data;
    }

    protected abstract void addColumns(boolean allowSelectAll);

    void addSelectedColumn(final boolean allowSelectAll) {
        final TickBoxCell.MarginAppearance tickBoxAppearance = GWT.create(TickBoxCell.MarginAppearance.class);

        // Select Column
        final Column<DataRow, TickBoxState> column = new Column<DataRow, TickBoxState>(
                TickBoxCell.create(tickBoxAppearance, false, false)) {
            @Override
            public TickBoxState getValue(final DataRow object) {
                return TickBoxState.fromBoolean(entityIdSet.isMatch(object.getData().getId()));
            }
        };
        if (allowSelectAll) {
            final Header<TickBoxState> header = new Header<TickBoxState>(
                    TickBoxCell.create(tickBoxAppearance, false, false)) {
                @Override
                public TickBoxState getValue() {
                    if (Boolean.TRUE.equals(entityIdSet.getMatchAll())) {
                        return TickBoxState.TICK;
                    }
                    if (entityIdSet.getSet().size() > 0) {
                        return TickBoxState.HALF_TICK;
                    }
                    return TickBoxState.UNTICK;
                }
            };
            getView().addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

            header.setUpdater(value -> {
                if (value.equals(TickBoxState.UNTICK)) {
                    entityIdSet.clear();
                    entityIdSet.setMatchAll(false);
                }
                if (value.equals(TickBoxState.TICK)) {
                    entityIdSet.clear();
                    entityIdSet.setMatchAll(true);
                }
                dataProvider.getDataProvider()
                        .updateRowData(dataProvider.getDataProvider().getRanges()[0].getStart(), resultList);
                DataSelectionEvent.fire(AbstractStreamListPresenter.this, entityIdSet, false);
            });

        } else {
            getView().addColumn(column, "", ColumnSizeConstants.CHECKBOX_COL);
        }

        // Add Handlers
        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                entityIdSet.add(row.getData().getId());

            } else {
                // De-selecting one and currently matching all ?
                if (Boolean.TRUE.equals(entityIdSet.getMatchAll())) {
                    entityIdSet.setMatchAll(false);

                    final Set<Long> resultStreamIdSet = getResultStreamIdSet();
                    entityIdSet.addAll(resultStreamIdSet);
                }
                entityIdSet.remove(row.getData().getId());
            }
            getView().redrawHeaders();
            DataSelectionEvent.fire(AbstractStreamListPresenter.this, entityIdSet, false);
        });
    }

    private SvgPreset getInfoCellState(final DataRow object) {
        // Should only show unlocked ones by default
        if (DataStatus.UNLOCKED.equals(object.getData().getStatus())) {
            return SvgPresets.INFO;
        }
        if (DataStatus.DELETED.equals(object.getData().getStatus())) {
            return SvgPresets.DELETE;
        }

        return SvgPresets.ALERT;
    }

    void addInfoColumn() {
        // Info column.
        final InfoColumn<DataRow> infoColumn = new InfoColumn<DataRow>() {
            @Override
            public SvgPreset getValue(final DataRow object) {
                return getInfoCellState(object);
            }

            @Override
            protected void showInfo(final DataRow row, final int x, final int y) {
                final FetchFullStreamInfoAction action = new FetchFullStreamInfoAction(row.getData());
                dispatcher.exec(action).onSuccess(result -> {
                    final StringBuilder html = new StringBuilder();

                    result.getSections().forEach(section -> {
                        TooltipUtil.addHeading(html, section.getTitle());
                        section.getEntries().forEach(entry -> TooltipUtil.addRowData(html, entry.getKey(), entry.getValue()));
                    });

                    tooltipPresenter.setHTML(html.toString());
                    final PopupPosition popupPosition = new PopupPosition(x, y);
                    ShowPopupEvent.fire(AbstractStreamListPresenter.this, tooltipPresenter, PopupType.POPUP,
                            popupPosition, null);
                });
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

//    private void buildTipText(final StreamAttributeMap row, final StringBuilder html) {
//        TooltipUtil.addHeading(html, "Stream");
//
//        TooltipUtil.addRowData(html, "Stream Id", row.getData().getId());
//        TooltipUtil.addRowData(html, "Status", row.getData().getStatus());
//        StreamTooltipPresenterUtil.addRowDateString(html, "Status Ms", row.getData().getStatusMs());
//        TooltipUtil.addRowData(html, "Stream Task Id", row.getData().getProcessTaskId());
//        TooltipUtil.addRowData(html, "Parent Stream Id", row.getData().getParentDataId());
//        StreamTooltipPresenterUtil.addRowDateString(html, "Created", row.getData().getCreateMs());
//        StreamTooltipPresenterUtil.addRowDateString(html, "Effective", row.getData().getEffectiveMs());
//        TooltipUtil.addRowData(html, "Stream Type", row.getData().getTypeName());
//        TooltipUtil.addRowData(html, "Feed", row.getData().getFeedName());
//        if (row.getData().getProcessorId() != null) {
//            TooltipUtil.addRowData(html, "Stream Processor Id", row.getData().getProcessorId());
//        }
//        if (row.getData().getPipelineUuid() != null) {
//            TooltipUtil.addRowData(html, "Stream Processor Pipeline", row.getData().getPipelineUuid());
//        }
//        TooltipUtil.addBreak(html);
//        TooltipUtil.addHeading(html, "Attributes");
//
//        try {
//            final List<String> keys = new ArrayList<>(row.getAttributeKeySet());
//
//            Collections.sort(keys);
//
//            for (final String key : keys) {
//                if (!key.equals(StreamAttributeConstants.RETENTION_AGE) &&
//                        !key.equals(StreamAttributeConstants.RETENTION_UNTIL) &&
//                        !key.equals(StreamAttributeConstants.RETENTION_RULE)) {
//                    TooltipUtil.addRowData(html, key, row.formatAttribute(key));
//                }
//            }
//        } catch (final RuntimeException e) {
//            html.append(e.getMessage());
//        }
//
//        if (row.getFileNameList() != null) {
//            TooltipUtil.addBreak(html);
//            TooltipUtil.addHeading(html, "Files");
//            for (final String file : row.getFileNameList()) {
//                TooltipUtil.addRowData(html, file);
//            }
//        }
//
//        TooltipUtil.addBreak(html);
//        TooltipUtil.addHeading(html, "Retention");
//        TooltipUtil.addRowData(html, StreamAttributeConstants.RETENTION_AGE, row.getAttributeValue(StreamAttributeConstants.RETENTION_AGE));
//        TooltipUtil.addRowData(html, StreamAttributeConstants.RETENTION_UNTIL, row.getAttributeValue(StreamAttributeConstants.RETENTION_UNTIL));
//        TooltipUtil.addRowData(html, StreamAttributeConstants.RETENTION_RULE, row.getAttributeValue(StreamAttributeConstants.RETENTION_RULE));
//    }

    void addCreatedColumn() {
        // Created.
        getView().addResizableColumn(new Column<DataRow, String>(new TextCell()) {
            @Override
            public String getValue(final DataRow row) {
                return ClientDateUtil.toISOString(row.getData().getCreateMs());
            }
        }, "Created", ColumnSizeConstants.DATE_COL);
    }

//    void addEffectiveColumn() {
//        // Effective.
//        getView().addResizableColumn(new Column<StreamAttributeMap, String>(new TextCell()) {
//            @Override
//            public String getValue(final StreamAttributeMap row) {
//                return ClientDateUtil.toISOString(row.getData().getEffectiveMs());
//            }
//        }, "Effective", ColumnSizeConstants.DATE_COL);
//    }

    void addFeedColumn() {
        // if (securityContext.hasAppPermission(Feed.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<DataRow, String>(new TextCell()) {
            @Override
            public String getValue(final DataRow row) {
                if (row != null && row.getData() != null && row.getData().getFeedName() != null) {
                    return row.getData().getFeedName();
                }
                return "";
            }
        }, "Feed", ColumnSizeConstants.BIG_COL);
        // }
    }

    void addStreamTypeColumn() {
        // if (securityContext.hasAppPermission(StreamType.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<DataRow, String>(new TextCell()) {
            @Override
            public String getValue(final DataRow row) {
                if (row != null && row.getData() != null && row.getData().getTypeName() != null) {
                    return row.getData().getTypeName();
                }
                return "";
            }
        }, "Type", 80);
        // }
    }

    void addPipelineColumn() {
        // if (securityContext.hasAppPermission(PipelineEntity.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<DataRow, String>(new TextCell()) {
            @Override
            public String getValue(final DataRow row) {
                if (row.getData().getProcessorId() != null) {
                    if (row.getData().getPipelineUuid() != null) {
                        return row.getData().getPipelineUuid();
                    } else {
                        return "Not visible";
                    }
                }
                return "";

            }
        }, "Pipeline", ColumnSizeConstants.BIG_COL);
        // }
    }

    protected MultiSelectionModel<DataRow> getSelectionModel() {
        return getView().getSelectionModel();
    }

    IdSet getSelectedEntityIdSet() {
        return entityIdSet;
    }

    private Set<Long> getResultStreamIdSet() {
        final HashSet<Long> rtn = new HashSet<>();
        if (resultList != null) {
            for (final DataRow e : resultList) {
                rtn.add(e.getData().getId());
            }
        }
        return rtn;

    }

    ResultList<DataRow> getResultList() {
        return resultList;
    }

    void addAttributeColumn(final String name, final String attribute, final Function<String, String> formatter, final int size) {
        final Column<DataRow, String> column = new Column<DataRow, String>(new TextCell()) {
            @Override
            public String getValue(final DataRow row) {
                final String value = row.getAttributeValue(attribute);
                if (value == null) {
                    return null;
                }
                return formatter.apply(value);
            }
        };
        getView().addResizableColumn(column, name, size);
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    public void setCriteria(final FindDataCriteria criteria) {
        if (criteria != null) {
            criteria.obtainPageRequest().setLength(PageRequest.DEFAULT_PAGE_SIZE);
        }
        dataProvider.setCriteria(criteria);
    }

    DataRow getSelectedStream() {
        return getView().getSelectionModel().getSelected();
    }

    @Override
    public com.google.web.bindery.event.shared.HandlerRegistration addDataSelectionHandler(
            final DataSelectionHandler<IdSet> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    public ButtonView add(final SvgPreset preset) {
        return getView().addButton(preset);
    }
}
