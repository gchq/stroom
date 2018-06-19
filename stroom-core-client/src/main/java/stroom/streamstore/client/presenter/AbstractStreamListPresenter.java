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
import stroom.data.meta.api.FindStreamCriteria;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamStatus;
import stroom.streamstore.shared.FetchFullStreamInfoAction;
import stroom.data.meta.api.StreamDataRow;
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

public abstract class AbstractStreamListPresenter extends MyPresenterWidget<DataGridView<StreamDataRow>> implements HasDataSelectionHandlers<IdSet>, Refreshable {
    private final TooltipPresenter tooltipPresenter;

    private final IdSet entityIdSet = new IdSet();
    private final ClientDispatchAsync dispatcher;
    protected FindStreamActionDataProvider dataProvider;
    private ResultList<StreamDataRow> resultList = null;

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
            protected ResultList<StreamDataRow> processData(final ResultList<StreamDataRow> data) {
                return onProcessData(data);
            }
        };
    }

    public FindStreamActionDataProvider getDataProvider() {
        return dataProvider;
    }

    protected ResultList<StreamDataRow> onProcessData(final ResultList<StreamDataRow> data) {
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
            final List<StreamDataRow> oldList = resultList.getValues();
            final List<StreamDataRow> newList = data.getValues();

            if (oldList.size() != newList.size()) {
                equalsList = false;
            } else {
                for (int i = 0; i < oldList.size(); i++) {
                    final Stream oldStream = oldList.get(i).getStream();
                    final Stream newStream = newList.get(i).getStream();

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
            // final long id = map.getStream().getId();
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
                    for (final StreamDataRow map : data) {
                        final long id = map.getStream().getId();
                        if (oldIdSet.contains(id)) {
                            entityIdSet.add(id);
                        }
                    }
                }
            }

            DataSelectionEvent.fire(AbstractStreamListPresenter.this, entityIdSet, false);
        }

        StreamDataRow selected = getView().getSelectionModel().getSelected();
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
        final Column<StreamDataRow, TickBoxState> column = new Column<StreamDataRow, TickBoxState>(
                TickBoxCell.create(tickBoxAppearance, false, false)) {
            @Override
            public TickBoxState getValue(final StreamDataRow object) {
                return TickBoxState.fromBoolean(entityIdSet.isMatch(object.getStream().getId()));
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
                entityIdSet.add(row.getStream().getId());

            } else {
                // De-selecting one and currently matching all ?
                if (Boolean.TRUE.equals(entityIdSet.getMatchAll())) {
                    entityIdSet.setMatchAll(false);

                    final Set<Long> resultStreamIdSet = getResultStreamIdSet();
                    entityIdSet.addAll(resultStreamIdSet);
                }
                entityIdSet.remove(row.getStream().getId());
            }
            getView().redrawHeaders();
            DataSelectionEvent.fire(AbstractStreamListPresenter.this, entityIdSet, false);
        });
    }

    private SvgPreset getInfoCellState(final StreamDataRow object) {
        // Should only show unlocked ones by default
        if (StreamStatus.UNLOCKED.equals(object.getStream().getStatus())) {
            return SvgPresets.INFO;
        }
        if (StreamStatus.DELETED.equals(object.getStream().getStatus())) {
            return SvgPresets.DELETE;
        }

        return SvgPresets.ALERT;
    }

    void addInfoColumn() {
        // Info column.
        final InfoColumn<StreamDataRow> infoColumn = new InfoColumn<StreamDataRow>() {
            @Override
            public SvgPreset getValue(final StreamDataRow object) {
                return getInfoCellState(object);
            }

            @Override
            protected void showInfo(final StreamDataRow row, final int x, final int y) {
                final FetchFullStreamInfoAction action = new FetchFullStreamInfoAction(row.getStream());
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
//        TooltipUtil.addRowData(html, "Stream Id", row.getStream().getId());
//        TooltipUtil.addRowData(html, "Status", row.getStream().getStatus());
//        StreamTooltipPresenterUtil.addRowDateString(html, "Status Ms", row.getStream().getStatusMs());
//        TooltipUtil.addRowData(html, "Stream Task Id", row.getStream().getStreamTaskId());
//        TooltipUtil.addRowData(html, "Parent Stream Id", row.getStream().getParentStreamId());
//        StreamTooltipPresenterUtil.addRowDateString(html, "Created", row.getStream().getCreateMs());
//        StreamTooltipPresenterUtil.addRowDateString(html, "Effective", row.getStream().getEffectiveMs());
//        TooltipUtil.addRowData(html, "Stream Type", row.getStream().getStreamTypeName());
//        TooltipUtil.addRowData(html, "Feed", row.getStream().getFeedName());
//        if (row.getStream().getStreamProcessorId() != null) {
//            TooltipUtil.addRowData(html, "Stream Processor Id", row.getStream().getStreamProcessorId());
//        }
//        if (row.getStream().getPipelineUuid() != null) {
//            TooltipUtil.addRowData(html, "Stream Processor Pipeline", row.getStream().getPipelineUuid());
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
        getView().addResizableColumn(new Column<StreamDataRow, String>(new TextCell()) {
            @Override
            public String getValue(final StreamDataRow row) {
                return ClientDateUtil.toISOString(row.getStream().getCreateMs());
            }
        }, "Created", ColumnSizeConstants.DATE_COL);
    }

//    void addEffectiveColumn() {
//        // Effective.
//        getView().addResizableColumn(new Column<StreamAttributeMap, String>(new TextCell()) {
//            @Override
//            public String getValue(final StreamAttributeMap row) {
//                return ClientDateUtil.toISOString(row.getStream().getEffectiveMs());
//            }
//        }, "Effective", ColumnSizeConstants.DATE_COL);
//    }

    void addFeedColumn() {
        // if (securityContext.hasAppPermission(Feed.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<StreamDataRow, String>(new TextCell()) {
            @Override
            public String getValue(final StreamDataRow row) {
                if (row != null && row.getStream() != null && row.getStream().getFeedName() != null) {
                    return row.getStream().getFeedName();
                }
                return "";
            }
        }, "Feed", ColumnSizeConstants.BIG_COL);
        // }
    }

    void addStreamTypeColumn() {
        // if (securityContext.hasAppPermission(StreamType.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<StreamDataRow, String>(new TextCell()) {
            @Override
            public String getValue(final StreamDataRow row) {
                if (row != null && row.getStream() != null && row.getStream().getStreamTypeName() != null) {
                    return row.getStream().getStreamTypeName();
                }
                return "";
            }
        }, "Type", 80);
        // }
    }

    void addPipelineColumn() {
        // if (securityContext.hasAppPermission(PipelineEntity.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<StreamDataRow, String>(new TextCell()) {
            @Override
            public String getValue(final StreamDataRow row) {
                if (row.getStream().getStreamProcessorId() != null) {
                    if (row.getStream().getPipelineUuid() != null) {
                        return row.getStream().getPipelineUuid();
                    } else {
                        return "Not visible";
                    }
                }
                return "";

            }
        }, "Pipeline", ColumnSizeConstants.BIG_COL);
        // }
    }

    protected MultiSelectionModel<StreamDataRow> getSelectionModel() {
        return getView().getSelectionModel();
    }

    IdSet getSelectedEntityIdSet() {
        return entityIdSet;
    }

    private Set<Long> getResultStreamIdSet() {
        final HashSet<Long> rtn = new HashSet<>();
        if (resultList != null) {
            for (final StreamDataRow e : resultList) {
                rtn.add(e.getStream().getId());
            }
        }
        return rtn;

    }

    ResultList<StreamDataRow> getResultList() {
        return resultList;
    }

    void addAttributeColumn(final String name, final String attribute, final Function<String, String> formatter, final int size) {
        final Column<StreamDataRow, String> column = new Column<StreamDataRow, String>(new TextCell()) {
            @Override
            public String getValue(final StreamDataRow row) {
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

    public void setCriteria(final FindStreamCriteria criteria) {
        if (criteria != null) {
            criteria.obtainPageRequest().setLength(PageRequest.DEFAULT_PAGE_SIZE);
        }
        dataProvider.setCriteria(criteria);
    }

    StreamDataRow getSelectedStream() {
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
