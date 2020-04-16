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
import stroom.datasource.api.v2.AbstractField;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaInfoSection;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.IdSet;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
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
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractMetaListPresenter extends MyPresenterWidget<DataGridView<MetaRow>> implements HasDataSelectionHandlers<IdSet>, Refreshable {
    private static final MetaResource META_RESOURCE = com.google.gwt.core.client.GWT.create(MetaResource.class);
    private final TooltipPresenter tooltipPresenter;

    private final IdSet entityIdSet = new IdSet();
    private final RestFactory restFactory;
    private RestDataProvider<MetaRow, ResultPage<MetaRow>> dataProvider;
    boolean allowNoConstraint;
    private ResultPage<MetaRow> resultPage;
    private FindMetaCriteria criteria;
    private EventBus eventBus;

    AbstractMetaListPresenter(final EventBus eventBus,
                              final RestFactory restFactory,
                              final TooltipPresenter tooltipPresenter,
                              final boolean allowSelectAll) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.tooltipPresenter = tooltipPresenter;
        this.restFactory = restFactory;
        this.eventBus = eventBus;

        entityIdSet.setMatchAll(false);
        addColumns(allowSelectAll);
    }

    public RestDataProvider<MetaRow, ResultPage<MetaRow>> getDataProvider() {
        return dataProvider;
    }

    protected ResultPage<MetaRow> onProcessData(final ResultPage<MetaRow> data) {
        boolean equalsList = true;

        // We compare the old and new lists to see if we need to do
        // the work of refreshing selections etc

        // Lists may have same entities but different versions (e.g. status)
        if (data == null && resultPage != null) {
            equalsList = false;
        }
        if (data != null && resultPage == null) {
            equalsList = false;
        }
        if (data != null && resultPage != null) {
            final List<MetaRow> oldList = resultPage.getValues();
            final List<MetaRow> newList = data.getValues();

            if (oldList.size() != newList.size()) {
                equalsList = false;
            } else {
                for (int i = 0; i < oldList.size(); i++) {
                    final Meta oldMeta = oldList.get(i).getMeta();
                    final Meta newMeta = newList.get(i).getMeta();

                    if (!oldMeta.equals(newMeta)) {
                        equalsList = false;
                        break;
                    }
                }
            }
        }

        this.resultPage = data;

        // List changed in any way ... fire a refresh
        if (!equalsList) {
            // entityIdSet.clear();
            // entityIdSet.setMatchAll(masterEntityIdSet.getMatchAll());
            // if (masterEntityIdSet.getIdSet() != null &&
            // masterEntityIdSet.getIdSet().size() > 0) {
            // for (final StreamAttributeMap map : data) {
            // final long id = map.getMeta().getId();
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
                    for (final MetaRow map : data.getValues()) {
                        final long id = map.getMeta().getId();
                        if (oldIdSet.contains(id)) {
                            entityIdSet.add(id);
                        }
                    }
                }
            }

            DataSelectionEvent.fire(AbstractMetaListPresenter.this, entityIdSet, false);
        }

        MetaRow selected = getView().getSelectionModel().getSelected();
        if (selected != null) {
            if (!resultPage.getValues().contains(selected)) {
                getView().getSelectionModel().setSelected(selected, false);
            }
        }

        return data;
    }

    protected abstract void addColumns(boolean allowSelectAll);

    void addSelectedColumn(final boolean allowSelectAll) {
        final TickBoxCell.MarginAppearance tickBoxAppearance = GWT.create(TickBoxCell.MarginAppearance.class);

        // Select Column
        final Column<MetaRow, TickBoxState> column = new Column<MetaRow, TickBoxState>(
                TickBoxCell.create(tickBoxAppearance, false, false)) {
            @Override
            public TickBoxState getValue(final MetaRow object) {
                return TickBoxState.fromBoolean(entityIdSet.isMatch(object.getMeta().getId()));
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
                dataProvider.updateRowData(dataProvider.getRanges()[0].getStart(), resultPage.getValues());
                DataSelectionEvent.fire(AbstractMetaListPresenter.this, entityIdSet, false);
            });

        } else {
            getView().addColumn(column, "", ColumnSizeConstants.CHECKBOX_COL);
        }

        // Add Handlers
        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                entityIdSet.add(row.getMeta().getId());

            } else {
                // De-selecting one and currently matching all ?
                if (Boolean.TRUE.equals(entityIdSet.getMatchAll())) {
                    entityIdSet.setMatchAll(false);

                    final Set<Long> resultStreamIdSet = getResultStreamIdSet();
                    entityIdSet.addAll(resultStreamIdSet);
                }
                entityIdSet.remove(row.getMeta().getId());
            }
            getView().redrawHeaders();
            DataSelectionEvent.fire(AbstractMetaListPresenter.this, entityIdSet, false);
        });
    }

    private SvgPreset getInfoCellState(final MetaRow object) {
        // Should only show unlocked ones by default
        if (Status.UNLOCKED.equals(object.getMeta().getStatus())) {
            return SvgPresets.INFO;
        }
        if (Status.DELETED.equals(object.getMeta().getStatus())) {
            return SvgPresets.DELETE;
        }

        return SvgPresets.ALERT;
    }

    void addInfoColumn() {
        // Info column.
        final InfoColumn<MetaRow> infoColumn = new InfoColumn<MetaRow>() {
            @Override
            public SvgPreset getValue(final MetaRow object) {
                return getInfoCellState(object);
            }

            @Override
            protected void showInfo(final MetaRow row, final int x, final int y) {
                final Rest<List<MetaInfoSection>> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            final StringBuilder html = new StringBuilder();

                            result.forEach(section -> {
                                TooltipUtil.addHeading(html, section.getTitle());
                                section.getEntries().forEach(entry -> TooltipUtil.addRowData(html, entry.getKey(), entry.getValue()));
                            });

                            tooltipPresenter.setHTML(html.toString());
                            final PopupPosition popupPosition = new PopupPosition(x, y);
                            ShowPopupEvent.fire(AbstractMetaListPresenter.this, tooltipPresenter, PopupType.POPUP,
                                    popupPosition, null);
                        })
                        .call(META_RESOURCE)
                        .fetchFullMetaInfo(row.getMeta().getId());
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    void addCreatedColumn() {
        // Created.
        getView().addResizableColumn(new Column<MetaRow, String>(new TextCell()) {
            @Override
            public String getValue(final MetaRow row) {
                return ClientDateUtil.toISOString(row.getMeta().getCreateMs());
            }
        }, "Created", ColumnSizeConstants.DATE_COL);
    }

//    void addEffectiveColumn() {
//        // Effective.
//        getView().addResizableColumn(new Column<StreamAttributeMap, String>(new TextCell()) {
//            @Override
//            public String getValue(final StreamAttributeMap row) {
//                return ClientDateUtil.toISOString(row.getMeta().getEffectiveMs());
//            }
//        }, "Effective", ColumnSizeConstants.DATE_COL);
//    }

    void addFeedColumn() {
        // if (securityContext.hasAppPermission(Feed.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<MetaRow, String>(new TextCell()) {
            @Override
            public String getValue(final MetaRow row) {
                if (row != null && row.getMeta() != null && row.getMeta().getFeedName() != null) {
                    return row.getMeta().getFeedName();
                }
                return "";
            }
        }, "Feed", ColumnSizeConstants.BIG_COL);
        // }
    }

    void addStreamTypeColumn() {
        // if (securityContext.hasAppPermission(StreamType.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<MetaRow, String>(new TextCell()) {
            @Override
            public String getValue(final MetaRow row) {
                if (row != null && row.getMeta() != null && row.getMeta().getTypeName() != null) {
                    return row.getMeta().getTypeName();
                }
                return "";
            }
        }, "Type", 80);
        // }
    }

    void addPipelineColumn() {
        // if (securityContext.hasAppPermission(PipelineEntity.DOCUMENT_TYPE, DocumentPermissionNames.READ)) {
        getView().addResizableColumn(new Column<MetaRow, String>(new TextCell()) {
            @Override
            public String getValue(final MetaRow row) {
                if (row.getMeta().getProcessorUuid() != null) {
                    if (row.getMeta().getPipelineUuid() != null) {
                        return row.getMeta().getPipelineUuid();
                    } else {
                        return "Not visible";
                    }
                }
                return "";

            }
        }, "Pipeline", ColumnSizeConstants.BIG_COL);
        // }
    }

    protected MultiSelectionModel<MetaRow> getSelectionModel() {
        return getView().getSelectionModel();
    }

    IdSet getSelectedEntityIdSet() {
        return entityIdSet;
    }

    private Set<Long> getResultStreamIdSet() {
        final HashSet<Long> rtn = new HashSet<>();
        if (resultPage != null) {
            for (final MetaRow e : resultPage.getValues()) {
                rtn.add(e.getMeta().getId());
            }
        }
        return rtn;

    }

    ResultPage<MetaRow> getResultPage() {
        return resultPage;
    }

    void addAttributeColumn(final String name, final AbstractField attribute, final Function<String, String> formatter, final int size) {
        final Column<MetaRow, String> column = new Column<MetaRow, String>(new TextCell()) {
            @Override
            public String getValue(final MetaRow row) {
                final String value = row.getAttributeValue(attribute.getName());
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
        if (dataProvider != null && (allowNoConstraint || criteria != null)) {
            dataProvider.refresh();
        }
    }

    public void setCriteria(final FindMetaCriteria criteria) {
        if (criteria != null && criteria.getExpression() != null) {
            this.criteria = criteria;
            this.criteria.obtainPageRequest().setLength(PageRequest.DEFAULT_PAGE_SIZE);

            if (dataProvider == null) {
                this.dataProvider = new RestDataProvider<MetaRow, ResultPage<MetaRow>>(eventBus, criteria.obtainPageRequest()) {
                    @Override
                    protected void exec(final Consumer<ResultPage<MetaRow>> dataConsumer, final Consumer<Throwable> throwableConsumer) {
                        final Rest<ResultPage<MetaRow>> rest = restFactory.create();
                        rest.onSuccess(dataConsumer).onFailure(throwableConsumer).call(META_RESOURCE).findMetaRow(criteria);
                    }

                    @Override
                    protected void changeData(final ResultPage<MetaRow> data) {
                        super.changeData(onProcessData(data));
                    }
                };
                dataProvider.addDataDisplay(getView().getDataDisplay());
            } else {
                dataProvider.refresh();
            }
        }
    }

    MetaRow getSelected() {
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
