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

package stroom.cache.client.component;

import stroom.cache.shared.CacheNamesResponse;
import stroom.cache.shared.CacheResource;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeClient;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.PageResponse;
import stroom.util.shared.cache.CacheIdentity;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The caches ([CacheList]) on every node, by name: each node is asked for its caches, and the list
/// shows them all once the replies are in. Clearing a cache rebuilds it, and its owner is told.
public class CacheListController extends Controller {

    private static final CacheResource CACHE_RESOURCE = GWT.create(CacheResource.class);

    private final CacheList view;
    private final RestFactory restFactory;
    private final Set<CacheIdentity> allCacheIdentities = new HashSet<>();
    private final DelayedUpdate delayedUpdate;

    private Range range;
    private Consumer<CacheNamesResponse> dataConsumer;
    private Consumer<String> onCacheCleared = cacheName -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Fetches and clears the caches.
    /// @param nodeClient  Lists the nodes.
    @Inject
    public CacheListController(final EventBus eventBus,
                               final CacheList view,
                               final RestFactory restFactory,
                               final NodeClient nodeClient) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.delayedUpdate = new DelayedUpdate(this::update);
        onDispose(delayedUpdate::reset);
        initWidget(view);
        view.setOnClear(this::clear);
        view.setDataProvider(new RestDataProvider<CacheIdentity, CacheNamesResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<CacheNamesResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CacheListController.this.range = range;
                CacheListController.this.dataConsumer = dataConsumer;
                nodeClient.listAllNodes(nodeNames -> fetchNamesForNodes(nodeNames), errorHandler,
                        view.getTaskMonitor());
            }
        });
    }

    /// @return The selection, one cache at most.
    public MultiSelectionModel<CacheIdentity> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// @param onCacheCleared Given a cache's name once it is cleared.
    public void setOnCacheCleared(final Consumer<String> onCacheCleared) {
        this.onCacheCleared = onCacheCleared;
    }

    private void clear(final String cacheName) {
        restFactory
                .create(CACHE_RESOURCE)
                .method(res -> res.clear(cacheName, null))
                .onSuccess(result -> onCacheCleared.accept(cacheName))
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
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
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }
    }

    // The page asked for, of every node's caches by name
    private void update() {
        if (isDisposed()) {
            return;
        }
        final List<CacheIdentity> list = allCacheIdentities.stream().sorted().collect(Collectors.toList());
        final List<CacheIdentity> trimmed = new ArrayList<>();
        for (int i = range.getStart(); i < range.getStart() + range.getLength() && i < list.size(); i++) {
            trimmed.add(list.get(i));
        }
        dataConsumer.accept(new CacheNamesResponse(trimmed,
                new PageResponse((long) range.getStart(), trimmed.size(), (long) list.size(), true)));
    }
}
