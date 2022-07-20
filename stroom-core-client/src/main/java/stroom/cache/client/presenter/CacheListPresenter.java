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

package stroom.cache.client.presenter;

import stroom.cache.shared.CacheNamesResponse;
import stroom.cache.shared.CacheResource;
import stroom.cell.info.client.ActionCell;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.PageResponse;
import stroom.widget.util.client.MultiSelectionModel;

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

public class CacheListPresenter extends MyPresenterWidget<DataGridView<String>> {

    private static final CacheResource CACHE_RESOURCE = GWT.create(CacheResource.class);
    private static final int ICON_COL = 18;

    private final RestFactory restFactory;
    private final Set<String> allNames = new HashSet<>();
    private final DelayedUpdate delayedUpdate;

    private Range range;
    private Consumer<CacheNamesResponse> dataConsumer;
    private Consumer<String> cacheUpdateHandler;

    @Inject
    public CacheListPresenter(final EventBus eventBus,
                              final RestFactory restFactory,
                              final NodeManager nodeManager) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.restFactory = restFactory;
        this.delayedUpdate = new DelayedUpdate(this::update);


        // Clear.
        addIconButtonColumn(
                SvgPresets.of(SvgPresets.DELETE, "Clear cache", true),
                (row, nativeEvent) -> {
                    final Rest<Boolean> rest = restFactory.create();
                    rest
                            .onSuccess(result -> {
                                if (cacheUpdateHandler != null) {
                                    cacheUpdateHandler.accept(row);
                                }
                            })
                            .call(CACHE_RESOURCE)
                            .clear(row, null);
                });

        // Name
        getView().addResizableColumn(new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String row) {
                return row;
            }
        }, "Name", 400);

        getView().addEndColumn(new EndColumn<>());

        final RestDataProvider<String, CacheNamesResponse> dataProvider =
                new RestDataProvider<String, CacheNamesResponse>(getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<CacheNamesResponse> dataConsumer,
                                        final Consumer<Throwable> throwableConsumer) {
                        CacheListPresenter.this.range = range;
                        CacheListPresenter.this.dataConsumer = dataConsumer;
                        nodeManager.listAllNodes(nodeNames -> fetchNamesForNodes(nodeNames), throwableConsumer);
                    }
                };
        dataProvider.addDataDisplay(getView().getDataDisplay());

    }

    private void addIconButtonColumn(final SvgPreset svgPreset,
                                     final BiConsumer<String, NativeEvent> action) {
        final ActionCell<String> cell = new stroom.cell.info.client.ActionCell<String>(
                svgPreset, action);
        final Column<String, String> col =
                new Column<String, String>(cell) {
                    @Override
                    public String getValue(final String row) {
                        return row;
                    }

                    @Override
                    public void onBrowserEvent(final Context context,
                                               final Element elem,
                                               final String rule,
                                               final NativeEvent event) {
                        super.onBrowserEvent(context, elem, rule, event);
                    }
                };
        getView().addColumn(col, "", ICON_COL);
    }

    private void fetchNamesForNodes(final List<String> nodeNames) {
        for (final String nodeName : nodeNames) {
            final Rest<CacheNamesResponse> rest = restFactory.create();
            rest
                    .onSuccess(response -> {
                        allNames.addAll(response.getValues());
                        delayedUpdate.update();
                    })
                    .onFailure(throwable -> {
                        delayedUpdate.update();
                    })
                    .call(CACHE_RESOURCE).list(nodeName);
        }
    }

    private void update() {
        final List<String> list = allNames.stream().sorted().collect(Collectors.toList());
        final long total = list.size();
        final List<String> trimmed = new ArrayList<>();
        for (int i = range.getStart(); i < range.getStart() + range.getLength() && i < list.size(); i++) {
            trimmed.add(list.get(i));
        }
        final CacheNamesResponse response = new CacheNamesResponse(trimmed,
                new PageResponse(range.getStart(), trimmed.size(), total, true));
        dataConsumer.accept(response);
    }

    public MultiSelectionModel<String> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public void setCacheUpdateHandler(final Consumer<String> cacheUpdateHandler) {
        this.cacheUpdateHandler = cacheUpdateHandler;
    }
}
