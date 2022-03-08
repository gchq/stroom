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
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.util.client.DelayedUpdate;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.cell.client.ButtonCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CacheListPresenter extends MyPresenterWidget<DataGridView<String>> {

    private static final CacheResource CACHE_RESOURCE = GWT.create(CacheResource.class);

    private final RestFactory restFactory;
    private final Set<String> allNames = new HashSet<>();
    private DelayedUpdate delayedUpdate;

    @Inject
    public CacheListPresenter(final EventBus eventBus,
                              final RestFactory restFactory,
                              final NodeManager nodeManager) {
        super(eventBus, new DataGridViewImpl<>(true));
        this.restFactory = restFactory;

        // Name
        getView().addResizableColumn(new Column<String, String>(new TextCell()) {
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
        getView().addColumn(clearColumn, "</br>", 50);

        getView().addEndColumn(new EndColumn<>());

        final RestDataProvider<String, CacheNamesResponse> dataProvider =
                new RestDataProvider<String, CacheNamesResponse>(getEventBus()) {
                    @Override
                    protected void exec(final Consumer<CacheNamesResponse> dataConsumer,
                                        final Consumer<Throwable> throwableConsumer) {
                        if (delayedUpdate == null) {
                            delayedUpdate = new DelayedUpdate(() -> combineNodeTasks(dataConsumer));
                        }
                        delayedUpdate.reset();
                        nodeManager.listAllNodes(nodeNames -> fetchNamesForNodes(nodeNames), throwableConsumer);
                    }
                };
        dataProvider.addDataDisplay(getView().getDataDisplay());
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

    private void combineNodeTasks(final Consumer<CacheNamesResponse> dataConsumer) {
        // Combine data from all nodes.
        final List<String> list = allNames.stream().sorted().collect(Collectors.toList());
        final CacheNamesResponse response = new CacheNamesResponse(list);
        dataConsumer.accept(response);
    }

    public MultiSelectionModel<String> getSelectionModel() {
        return getView().getSelectionModel();
    }
}
