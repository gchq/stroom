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
import com.google.gwt.resources.client.ImageResource;
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
import stroom.entity.client.presenter.EntityServiceFindActionDataProvider;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.ResultList;
import stroom.feed.shared.Feed;
import stroom.node.shared.Volume;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.button.client.ImageButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractStreamListPresenter extends MyPresenterWidget<DataGridView<StreamAttributeMap>> implements HasDataSelectionHandlers<EntityIdSet<Stream>>, Refreshable {
    private final TooltipPresenter tooltipPresenter;
    private final ClientSecurityContext securityContext;

    //    private final MySingleSelectionModel<StreamAttributeMap> selectionModel = new MySingleSelectionModel<StreamAttributeMap>();
    // private final EntityIdSet<Stream> masterEntityIdSet = new
    // EntityIdSet<Stream>();
    private final EntityIdSet<Stream> entityIdSet = new EntityIdSet<Stream>();
    private final ClientDispatchAsync dispatcher;
    protected EntityServiceFindActionDataProvider<FindStreamAttributeMapCriteria, StreamAttributeMap> dataProvider;
    private ResultList<StreamAttributeMap> resultList = null;

    public AbstractStreamListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                       final TooltipPresenter tooltipPresenter, final ClientSecurityContext securityContext,
                                       final boolean allowSelectAll) {
        super(eventBus, new DataGridViewImpl<StreamAttributeMap>(true));
        this.tooltipPresenter = tooltipPresenter;
        this.dispatcher = dispatcher;
        this.securityContext = securityContext;

        entityIdSet.setMatchAll(false);
//        getView().setSelectionModel(selectionModel);

        addColumns(allowSelectAll);

        this.dataProvider = new EntityServiceFindActionDataProvider<FindStreamAttributeMapCriteria, StreamAttributeMap>(
                dispatcher, getView()) {
            @Override
            protected ResultList<StreamAttributeMap> processData(final ResultList<StreamAttributeMap> data) {
                return onProcessData(data);
            }
        };
    }

    public EntityServiceFindActionDataProvider<FindStreamAttributeMapCriteria, StreamAttributeMap> getDataProvider() {
        return dataProvider;
    }

    protected ResultList<StreamAttributeMap> onProcessData(final ResultList<StreamAttributeMap> data) {
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
            final List<StreamAttributeMap> oldList = resultList.getValues();
            final List<StreamAttributeMap> newList = data.getValues();

            if (oldList.size() != newList.size()) {
                equalsList = false;
            } else {
                for (int i = 0; i < oldList.size(); i++) {
                    final Stream oldStream = oldList.get(i).getStream();
                    final Stream newStream = newList.get(i).getStream();

                    if (!oldStream.equalsEntityVersion(newStream)) {
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

            if (entityIdSet.getIdSet() != null && entityIdSet.getIdSet().size() > 0) {
                final Boolean oldMatchAll = entityIdSet.getMatchAll();
                final Set<Long> oldIdSet = new HashSet<Long>(entityIdSet.getIdSet());
                entityIdSet.clear();
                entityIdSet.setMatchAll(oldMatchAll);
                if (data != null) {
                    for (final StreamAttributeMap map : data) {
                        final long id = map.getStream().getId();
                        if (oldIdSet.contains(id)) {
                            entityIdSet.add(id);
                        }
                    }
                }
            }

            DataSelectionEvent.fire(AbstractStreamListPresenter.this, entityIdSet, false);
        }

        StreamAttributeMap selected = getView().getSelectionModel().getSelected();
        if (selected != null) {
            if (!resultList.contains(selected)) {
                getView().getSelectionModel().setSelected(selected, false);
            }
        }

        return data;
    }

//    @Override
//    protected void onBind() {
//        super.onBind();
//        registerHandler(getView().addSelectionHandler().addSelectionChangeHandler(interceptingSelectionChangeHandler));
//    }

    protected abstract void addColumns(boolean allowSelectAll);

    protected void addSelectedColumn(final boolean allowSelectAll) {
        final TickBoxCell.MarginAppearance tickBoxAppearance = GWT.create(TickBoxCell.MarginAppearance.class);

        // Select Column
        final Column<StreamAttributeMap, TickBoxState> column = new Column<StreamAttributeMap, TickBoxState>(
                TickBoxCell.create(tickBoxAppearance, false, false)) {
            @Override
            public TickBoxState getValue(final StreamAttributeMap object) {
                return TickBoxState.fromBoolean(entityIdSet.isMatch(object.getStream()));
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
                    // masterEntityIdSet.clear();
                    // masterEntityIdSet.setMatchAll(false);
                    entityIdSet.clear();
                    entityIdSet.setMatchAll(false);
                }
                if (value.equals(TickBoxState.TICK)) {
                    // masterEntityIdSet.clear();
                    // masterEntityIdSet.setMatchAll(true);
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
                // masterEntityIdSet.add(row.getStream());
                entityIdSet.add(row.getStream());

            } else {
                // De-selecting one and currently matching all ?
                if (Boolean.TRUE.equals(entityIdSet.getMatchAll())) {
                    // masterEntityIdSet.setMatchAll(false);
                    entityIdSet.setMatchAll(false);

                    final Set<Long> resultStreamIdSet = getResultStreamIdSet();
                    // masterEntityIdSet.addAll(resultStreamIdSet);
                    entityIdSet.addAll(resultStreamIdSet);
                }
                // masterEntityIdSet.remove(row.getStream());
                entityIdSet.remove(row.getStream());
            }
            DataSelectionEvent.fire(AbstractStreamListPresenter.this, entityIdSet, false);
        });
    }

    public GlyphIcon getInfoCellState(final StreamAttributeMap object) {
        // Should only show unlocked ones by default
        if (StreamStatus.UNLOCKED.equals(object.getStream().getStatus())) {
            return GlyphIcons.INFO;
        }
        if (StreamStatus.DELETED.equals(object.getStream().getStatus())) {
            return GlyphIcons.DELETE;
        }

        return GlyphIcons.ALERT;
    }

    protected void addInfoColumn() {
        // Info column.
        final InfoColumn<StreamAttributeMap> infoColumn = new InfoColumn<StreamAttributeMap>() {
            @Override
            public GlyphIcon getValue(final StreamAttributeMap object) {
                return getInfoCellState(object);
            }

            @Override
            protected void showInfo(final StreamAttributeMap row, final int x, final int y) {
                if (row.getStream().isStub()) {
                    final StringBuilder html = new StringBuilder();
                    TooltipUtil.addHeading(html, "Stream");

                    TooltipUtil.addRowData(html, "Deleted Stream Id", row.getStream().getId());

                    tooltipPresenter.setHTML(html.toString());
                    final PopupPosition popupPosition = new PopupPosition(x, y);
                    ShowPopupEvent.fire(AbstractStreamListPresenter.this, tooltipPresenter, PopupType.POPUP,
                            popupPosition, null);

                } else {
                    final FindStreamAttributeMapCriteria streamAttributeMapCriteria = new FindStreamAttributeMapCriteria();
                    streamAttributeMapCriteria.setUseCache(false);
                    streamAttributeMapCriteria.obtainFindStreamCriteria().obtainStreamIdSet().add(row.getStream());
                    populateFetchSet(streamAttributeMapCriteria.getFetchSet(), true);

                    dispatcher.exec(
                            new EntityServiceFindAction<FindStreamAttributeMapCriteria, StreamAttributeMap>(
                                    streamAttributeMapCriteria)).onSuccess(result -> {
                        final StreamAttributeMap streamAttributeMap = result.get(0);
                        final StringBuilder html = new StringBuilder();

                        buildTipText(streamAttributeMap, html);

                        tooltipPresenter.setHTML(html.toString());
                        final PopupPosition popupPosition = new PopupPosition(x, y);
                        ShowPopupEvent.fire(AbstractStreamListPresenter.this, tooltipPresenter, PopupType.POPUP,
                                popupPosition, null);
                    });
                }
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.GLYPH_COL);
    }

    public void buildTipText(final StreamAttributeMap row, final StringBuilder html) {
        TooltipUtil.addHeading(html, "Stream");

        TooltipUtil.addRowData(html, "Stream Id", row.getStream().getId());
        TooltipUtil.addRowData(html, "Status", row.getStream().getStatus());
        StreamTooltipPresenterUtil.addRowDateString(html, "Status Ms", row.getStream().getStatusMs());
        TooltipUtil.addRowData(html, "Stream Task Id", row.getStream().getStreamTaskId());
        TooltipUtil.addRowData(html, "Parent Stream Id", row.getStream().getParentStreamId());
        StreamTooltipPresenterUtil.addRowDateString(html, "Created", row.getStream().getCreateMs());
        StreamTooltipPresenterUtil.addRowDateString(html, "Effective", row.getStream().getEffectiveMs());

        //if (securityContext.hasAppPermission(StreamType.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        StreamTooltipPresenterUtil.addRowNameString(html, "Stream Type", row.getStream().getStreamType());
        //}
        //if (securityContext.hasAppPermission(Feed.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        StreamTooltipPresenterUtil.addRowNameString(html, "Feed", row.getStream().getFeed());
        //}

        //if (securityContext.hasAppPermission(StreamProcessor.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        if (row.getStream().getStreamProcessor() != null) {
            TooltipUtil.addRowData(html, "Stream Processor Id", row.getStream().getStreamProcessor().getId());
            if (row.getStream().getStreamProcessor().getPipeline() != null) {
                //if (securityContext.hasAppPermission(PipelineEntity.ENTITY_TYPE, DocumentPermissionNames.READ)) {
                StreamTooltipPresenterUtil.addRowNameString(html, "Stream Processor Pipeline",
                        row.getStream().getStreamProcessor().getPipeline());
                //}
            }
        }
        //}
        TooltipUtil.addBreak(html);
        TooltipUtil.addHeading(html, "Attributes");

        try {
            final List<String> keys = new ArrayList<String>(row.getAttributeKeySet());

            Collections.sort(keys);

            for (final String key : keys) {
                if (!key.equals(StreamAttributeConstants.RETENTION_AGE) &&
                        !key.equals(StreamAttributeConstants.RETENTION_UNTIL) &&
                        !key.equals(StreamAttributeConstants.RETENTION_RULE)) {
                    TooltipUtil.addRowData(html, key, row.formatAttribute(key));
                }
            }
        } catch (final Exception ex) {
            html.append(ex.getMessage());
        }

        // if (securityContext.hasAppPermission(Volume.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        if (row.getFileNameList() != null) {
            TooltipUtil.addBreak(html);
            TooltipUtil.addHeading(html, "Files");
            for (final String file : row.getFileNameList()) {
                TooltipUtil.addRowData(html, file);
            }
        }
        // }

        TooltipUtil.addBreak(html);
        TooltipUtil.addHeading(html, "Retention");
        TooltipUtil.addRowData(html, StreamAttributeConstants.RETENTION_AGE, row.getAttributeValue(StreamAttributeConstants.RETENTION_AGE));
        TooltipUtil.addRowData(html, StreamAttributeConstants.RETENTION_UNTIL, row.getAttributeValue(StreamAttributeConstants.RETENTION_UNTIL));
        TooltipUtil.addRowData(html, StreamAttributeConstants.RETENTION_RULE, row.getAttributeValue(StreamAttributeConstants.RETENTION_RULE));
    }

    protected void addCreatedColumn() {
        // Created.
        getView().addResizableColumn(new Column<StreamAttributeMap, String>(new TextCell()) {
            @Override
            public String getValue(final StreamAttributeMap row) {
                return ClientDateUtil.toISOString(row.getStream().getCreateMs());
            }
        }, "Created", ColumnSizeConstants.DATE_COL);
    }

    protected void addEffectiveColumn() {
        // Effective.
        getView().addResizableColumn(new Column<StreamAttributeMap, String>(new TextCell()) {
            @Override
            public String getValue(final StreamAttributeMap row) {
                return ClientDateUtil.toISOString(row.getStream().getEffectiveMs());
            }
        }, "Effective", ColumnSizeConstants.DATE_COL);
    }

    protected void addFeedColumn() {
        // if (securityContext.hasAppPermission(Feed.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<StreamAttributeMap, String>(new TextCell()) {
            @Override
            public String getValue(final StreamAttributeMap row) {
                return ModelStringUtil.format(row.getStream().getFeed());
            }
        }, "Feed", ColumnSizeConstants.BIG_COL);
        // }
    }

    protected void addStreamTypeColumn() {
        // if (securityContext.hasAppPermission(StreamType.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<StreamAttributeMap, String>(new TextCell()) {
            @Override
            public String getValue(final StreamAttributeMap row) {
                return ModelStringUtil.format(row.getStream().getStreamType());
            }
        }, "Type", 80);
        // }
    }

    protected void addPipelineColumn() {
        // if (securityContext.hasAppPermission(PipelineEntity.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<StreamAttributeMap, String>(new TextCell()) {
            @Override
            public String getValue(final StreamAttributeMap row) {
                if (row.getStream().getStreamProcessor() != null) {
                    if (row.getStream().getStreamProcessor().getPipeline() != null) {
                        return row.getStream().getStreamProcessor().getPipeline().getDisplayValue();
                    } else {
                        return "Not visible";
                    }
                }
                return "";

            }
        }, "Pipeline", ColumnSizeConstants.BIG_COL);
        // }
    }

    protected MultiSelectionModel<StreamAttributeMap> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public EntityIdSet<Stream> getSelectedEntityIdSet() {
        return entityIdSet;
    }

    protected Set<Long> getResultStreamIdSet() {
        final HashSet<Long> rtn = new HashSet<Long>();
        if (resultList != null) {
            for (final StreamAttributeMap e : resultList) {
                rtn.add(e.getStream().getId());
            }
        }
        return rtn;

    }

    public ResultList<StreamAttributeMap> getResultList() {
        return resultList;
    }

    protected void addAttributeColumn(final String name, final String attribute, final int size) {
        final Column<StreamAttributeMap, String> column = new Column<StreamAttributeMap, String>(new TextCell()) {
            @Override
            public String getValue(final StreamAttributeMap row) {
                return row.formatAttribute(attribute);
            }
        };
        getView().addResizableColumn(column, name, size);
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    private void populateFetchSet(final Set<String> fetchSet, final boolean forInfo) {
        // if (securityContext.hasAppPermission(StreamType.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        fetchSet.add(StreamType.ENTITY_TYPE);
        // }
        // if (securityContext.hasAppPermission(Feed.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        fetchSet.add(Feed.ENTITY_TYPE);
        // }
        // if (securityContext.hasAppPermission(StreamProcessor.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        fetchSet.add(StreamProcessor.ENTITY_TYPE);
        // }
        // if (securityContext.hasAppPermission(PipelineEntity.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        fetchSet.add(PipelineEntity.ENTITY_TYPE);
        // }

        // For info ? load up the files
        // if (securityContext.hasAppPermission(Volume.ENTITY_TYPE, DocumentPermissionNames.READ)) {
        fetchSet.add(Volume.ENTITY_TYPE);
        //  }

    }

    public void setCriteria(final FindStreamAttributeMapCriteria criteria) {
        if (criteria != null) {
            criteria.obtainPageRequest().setLength(PageRequest.DEFAULT_PAGE_SIZE);
            populateFetchSet(criteria.getFetchSet(), false);
            populateFetchSet(criteria.obtainFindStreamCriteria().getFetchSet(), false);
        }
        dataProvider.setCriteria(criteria);
    }

    public StreamAttributeMap getSelectedStream() {
        return getView().getSelectionModel().getSelected();
    }

//    public void setSelectedStream(final StreamAttributeMap selectedStream, final boolean fireEvents,
//            final boolean showHiddenStream) {
//        setSelectedStreamInternal(selectedStream, fireEvents);
//    }

//    protected void setSelectedStreamInternal(final StreamAttributeMap selectedStream, final boolean fireEvents) {
//        interceptingSelectionChangeHandler.setIgnoreNextEvent(!fireEvents);
//        selectionModel.setSelected(selectedStream, true);
//    }

    @Override
    public com.google.web.bindery.event.shared.HandlerRegistration addDataSelectionHandler(
            final DataSelectionHandler<EntityIdSet<Stream>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    public ImageButtonView add(final String title, final ImageResource enabledImage, final ImageResource disabledImage,
                               final boolean enabled) {
        return getView().addButton(title, enabledImage, disabledImage, enabled);
    }

    public GlyphButtonView add(final GlyphIcon preset) {
        return getView().addButton(preset);
    }
}
