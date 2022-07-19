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
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.PageResponse;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.ButtonCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CacheListPresenter extends MyPresenterWidget<PagerView> {

    private static final CacheResource CACHE_RESOURCE = GWT.create(CacheResource.class);

    private final RestFactory restFactory;
    private final Set<String> allNames = new HashSet<>();
    private final DelayedUpdate delayedUpdate;

    private final MultiSelectionModelImpl<String> selectionModel;

    private Range range;
    private Consumer<CacheNamesResponse> dataConsumer;

    @Inject
    public CacheListPresenter(final EventBus eventBus,
                              final PagerView view,
                              final RestFactory restFactory,
                              final NodeManager nodeManager) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.delayedUpdate = new DelayedUpdate(this::update);

        final MyDataGrid<String> dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        // Name
        dataGrid.addResizableColumn(new Column<String, String>(new TextCell()) {
            @Override
            public String getValue(final String row) {
                return row;
            }
        }, "Name", 400);

        // Clear.
        final Column<String, String> clearColumn = new Column<String, String>(new ButtonCell()) {
            @Override
            public String getValue(final String row) {
                return "Clear";
            }
        };
        clearColumn.setFieldUpdater((index, row, value) -> {
            final Rest<Boolean> rest = restFactory.create();
            rest.call(CACHE_RESOURCE).clear(row, null);
        });
        dataGrid.addColumn(clearColumn, "</br>", 80);

        dataGrid.addEndColumn(new EndColumn<>());

        final RestDataProvider<String, CacheNamesResponse> dataProvider =
                new RestDataProvider<String, CacheNamesResponse>(getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<CacheNamesResponse> dataConsumer,
                                        final Consumer<Throwable> throwableConsumer) {
                        CacheListPresenter.this.range = range;
                        CacheListPresenter.this.dataConsumer = dataConsumer;
                        delayedUpdate.reset();
                        nodeManager.listAllNodes(nodeNames -> fetchNamesForNodes(nodeNames), throwableConsumer);
                    }
                };
        dataProvider.addDataDisplay(dataGrid);
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
        return selectionModel;
    }
}
