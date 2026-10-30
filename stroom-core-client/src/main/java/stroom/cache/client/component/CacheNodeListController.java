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

import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheResource;
import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeClient;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.PageResponse;
import stroom.util.shared.cache.CacheInfo;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// A cache on each node ([CacheNodeList]): each node is asked for the cache's statistics, and the
/// list shows them all, with a column for each statistic reported, once the replies are in.
/// Clearing the cache on a node rebuilds it there.
public class CacheNodeListController extends Controller {

    private static final CacheResource CACHE_RESOURCE = GWT.create(CacheResource.class);

    private CacheNodeList view;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;
    private final Map<String, List<CacheInfo>> responseMap = new HashMap<>();
    private final Set<String> cacheInfoKeys = new HashSet<>();
    private final DelayedUpdate delayedUpdate;

    private RestDataProvider<CacheInfo, CacheInfoResponse> dataProvider;
    private String cacheName;
    private Range range;
    private Consumer<CacheInfoResponse> dataConsumer;

    /// Made by GIN; [#attach(CacheNodeList)] gives it its list.
    ///
    /// @param eventBus    The application's event bus.
    /// @param restFactory Fetches and clears the cache on each node.
    /// @param nodeClient  Lists the nodes.
    @Inject
    public CacheNodeListController(final EventBus eventBus,
                                   final RestFactory restFactory,
                                   final NodeClient nodeClient) {
        super(eventBus);
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        this.delayedUpdate = new DelayedUpdate(this::update);
        onDispose(delayedUpdate::reset);
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public CacheNodeListController attach(final CacheNodeList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.setOnClear(this::clear);
        return this;
    }

    /// Shows a cache on each node.
    ///
    /// @param cacheName The cache's name; null does nothing.
    public void read(final String cacheName) {
        if (cacheName == null) {
            return;
        }
        this.cacheName = cacheName;
        responseMap.clear();
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<CacheInfoResponse> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    CacheNodeListController.this.range = range;
                    CacheNodeListController.this.dataConsumer = dataConsumer;
                    delayedUpdate.reset();
                    nodeClient.listAllNodes(nodeNames -> fetchForNodes(nodeNames), errorHandler,
                            view.getTaskMonitor());
                }
            };
            view.setDataProvider(dataProvider);
        }
        dataProvider.refresh();
    }

    private void clear(final CacheInfo row) {
        restFactory
                .create(CACHE_RESOURCE)
                .method(res -> res.clear(row.getName(), row.getNodeName()))
                .onSuccess(result -> dataProvider.refresh())
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private void fetchForNodes(final List<String> nodeNames) {
        cacheInfoKeys.clear();
        for (final String nodeName : nodeNames) {
            restFactory
                    .create(CACHE_RESOURCE)
                    .method(res -> res.info(cacheName, nodeName))
                    .onSuccess(response -> {
                        responseMap.put(nodeName, response.getValues());
                        cacheInfoKeys.addAll(response.getValues()
                                .stream()
                                .flatMap(resp -> resp.getMap().keySet().stream())
                                .collect(Collectors.toSet()));
                        delayedUpdate.update();
                    })
                    .onFailure(throwable -> {
                        responseMap.remove(nodeName);
                        delayedUpdate.update();
                    })
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }
    }

    // Every node's rows, by name, the page asked for, with a column for each statistic
    private void update() {
        if (isDisposed()) {
            return;
        }
        view.setStatKeys(cacheInfoKeys);
        final List<CacheInfo> list = new ArrayList<>();
        responseMap.values().forEach(list::addAll);
        list.sort(Comparator.comparing(CacheInfo::getName));
        final List<CacheInfo> trimmed = new ArrayList<>();
        for (int i = range.getStart(); i < range.getStart() + range.getLength() && i < list.size(); i++) {
            trimmed.add(list.get(i));
        }
        dataConsumer.accept(new CacheInfoResponse(trimmed,
                new PageResponse((long) range.getStart(), trimmed.size(), (long) list.size(), true)));
    }
}
