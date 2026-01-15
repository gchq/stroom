/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.cache.client.presenter;

import stroom.cache.shared.CacheNamesResponse;
import stroom.cache.shared.CacheResource;
import stroom.cell.info.client.ActionCell;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.PageResponse;
import stroom.util.shared.cache.CacheIdentity;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CacheListPresenter extends MyPresenterWidget<PagerView> {

    private static final CacheResource CACHE_RESOURCE = GWT.create(CacheResource.class);
    private static final int ICON_COL = 18;

    private final RestFactory restFactory;
    private final Set<CacheIdentity> allCacheIdentities = new HashSet<>();
    private final DelayedUpdate delayedUpdate;

    private final MultiSelectionModelImpl<CacheIdentity> selectionModel;

    private Range range;
    private Consumer<CacheNamesResponse> dataConsumer;
    private Consumer<String> cacheUpdateHandler;

    @Inject
    public CacheListPresenter(final EventBus eventBus,
                              final PagerView view,
                              final RestFactory restFactory,
                              final NodeManager nodeManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.delayedUpdate = new DelayedUpdate(this::update);

        final MyDataGrid<CacheIdentity> dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        // Clear.
        addIconButtonColumn(
                dataGrid,
                SvgPresets.of(SvgPresets.DELETE, "Clear and rebuild cache", true),
                (row, nativeEvent) -> {
                    restFactory
                            .create(CACHE_RESOURCE)
                            .method(res -> res.clear(row, null))
                            .onSuccess(result -> {
                                if (cacheUpdateHandler != null) {
                                    cacheUpdateHandler.accept(row);
                                }
                            })
                            .taskMonitorFactory(view)
                            .exec();
                });

        // Name
        dataGrid.addResizableColumn(new Column<CacheIdentity, String>(new TextCell()) {
            @Override
            public String getValue(final CacheIdentity cacheIdentity) {
                return cacheIdentity.getCacheName();
            }
        }, "Name", 250);

        // Property Path Base
        dataGrid.addResizableColumn(new Column<CacheIdentity, String>(new TextCell()) {
            @Override
            public String getValue(final CacheIdentity cacheIdentity) {
                return cacheIdentity.getBasePropertyPath().toString();
            }
        }, "Property Path Base", 500);

        dataGrid.addEndColumn(new EndColumn<>());

        final RestDataProvider<CacheIdentity, CacheNamesResponse> dataProvider =
                new RestDataProvider<CacheIdentity, CacheNamesResponse>(getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<CacheNamesResponse> dataConsumer,
                                        final RestErrorHandler errorHandler) {
                        CacheListPresenter.this.range = range;
                        CacheListPresenter.this.dataConsumer = dataConsumer;
                        nodeManager.listAllNodes(nodeNames -> fetchNamesForNodes(nodeNames), errorHandler, getView());
                    }
                };
        dataProvider.addDataDisplay(dataGrid);
    }

    private void addIconButtonColumn(final MyDataGrid<CacheIdentity> dataGrid,
                                     final Preset svgPreset,
                                     final BiConsumer<String, NativeEvent> action) {
        final ActionCell<String> cell = new stroom.cell.info.client.ActionCell<String>(
                svgPreset, action);

        final Column<CacheIdentity, String> col =
                new Column<CacheIdentity, String>(cell) {
                    @Override
                    public String getValue(final CacheIdentity row) {
                        return row.getCacheName();
                    }

                    @Override
                    public void onBrowserEvent(final Context context,
                                               final Element elem,
                                               final CacheIdentity rule,
                                               final NativeEvent event) {
                        super.onBrowserEvent(context, elem, rule, event);
                    }
                };

        dataGrid.addColumn(col, "", ICON_COL);
    }

    private void fetchNamesForNodes(final List<String> nodeNames) {
        for (final String nodeName : nodeNames) {
            restFactory
                    .create(CACHE_RESOURCE)
                    .method(res -> res.list(nodeName))
                    .onSuccess(response -> {
                        allCacheIdentities.addAll(response.getValues());
                        delayedUpdate.update();
                    })
                    .onFailure(throwable -> delayedUpdate.update())
                    .taskMonitorFactory(getView())
                    .exec();
        }
    }

    private void update() {
        final List<CacheIdentity> list = allCacheIdentities.stream().sorted().collect(Collectors.toList());
        final long total = list.size();
        final List<CacheIdentity> trimmed = new ArrayList<>();
        for (int i = range.getStart(); i < range.getStart() + range.getLength() && i < list.size(); i++) {
            trimmed.add(list.get(i));
        }
        final CacheNamesResponse response = new CacheNamesResponse(trimmed,
                new PageResponse(range.getStart(), trimmed.size(), total, true));
        dataConsumer.accept(response);
    }

    public MultiSelectionModel<CacheIdentity> getSelectionModel() {
        return selectionModel;
    }

    public void setCacheUpdateHandler(final Consumer<String> cacheUpdateHandler) {
        this.cacheUpdateHandler = cacheUpdateHandler;
    }
}
