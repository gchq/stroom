/*
 * Copyright 2023 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.analytics.shared.AnalyticDataShard;
import stroom.analytics.shared.AnalyticDataShardResource;
import stroom.analytics.shared.FindAnalyticDataShardCriteria;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.node.client.NodeClient;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// An analytic's data shards ([AnalyticDataShardList]), asked of every node and shown together once
/// the answers stop arriving.
public class AnalyticDataShardListController extends Controller {

    private static final AnalyticDataShardResource ANALYTIC_DATA_SHARD_RESOURCE =
            GWT.create(AnalyticDataShardResource.class);

    private AnalyticDataShardList view;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;
    private final DateTimeFormatter dateTimeFormatter;
    private final FindAnalyticDataShardCriteria criteria = new FindAnalyticDataShardCriteria();
    private final RestDataProvider<AnalyticDataShard, ResultPage<AnalyticDataShard>> dataProvider;
    private final Map<String, List<AnalyticDataShard>> responseMap = new HashMap<>();
    private final DelayedUpdate delayedUpdate;
    private boolean initialised;
    private Consumer<ResultPage<AnalyticDataShard>> dataConsumer;

    /// Made by GIN; [#attach(AnalyticDataShardList)] gives it its list.
    ///
    /// @param eventBus          The application's event bus.
    /// @param restFactory       Finds the shards on each node.
    /// @param nodeClient        Lists the nodes.
    /// @param dateTimeFormatter Formats when each shard was made.
    @Inject
    public AnalyticDataShardListController(final EventBus eventBus,
                                           final RestFactory restFactory,
                                           final NodeClient nodeClient,
                                           final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        this.dateTimeFormatter = dateTimeFormatter;

        delayedUpdate = new DelayedUpdate(this::update);
        onDispose(delayedUpdate::reset);
        dataProvider = new RestDataProvider<AnalyticDataShard, ResultPage<AnalyticDataShard>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<AnalyticDataShard>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                AnalyticDataShardListController.this.dataConsumer = dataConsumer;
                delayedUpdate.reset();
                nodeClient.listAllNodes(
                        nodeNames -> fetchShardsForNodes(range, nodeNames),
                        errorHandler,
                        view.getTaskMonitor());
            }
        };
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public AnalyticDataShardListController attach(final AnalyticDataShardList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.addColumns(dateTimeFormatter::formatWithDuration);
        return this;
    }

    // Combines the shards from all the nodes
    private void update() {
        final List<AnalyticDataShard> list = responseMap
                .values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        dataConsumer.accept(ResultPage.createCriterialBasedList(list, criteria));
    }

    private void fetchShardsForNodes(final Range range,
                                     final List<String> nodeNames) {
        responseMap.clear();
        for (final String nodeName : nodeNames) {
            if (criteria.getAnalyticDocUuid() != null) {
                CriteriaUtil.setRange(criteria, range);
                restFactory
                        .create(ANALYTIC_DATA_SHARD_RESOURCE)
                        .method(res -> res.find(nodeName, criteria))
                        .onSuccess(response -> {
                            responseMap.put(nodeName, response.getValues());
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
    }

    /// Shows an analytic's shards.
    ///
    /// @param docRef The analytic.
    public void read(final DocRef docRef) {
        criteria.setAnalyticDocUuid(docRef.getUuid());
        refresh();
    }

    /// Finds the shards again; the first time, starts the list.
    public void refresh() {
        if (!initialised) {
            initialised = true;
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// @return The selection.
    public MultiSelectionModelImpl<AnalyticDataShard> getSelectionModel() {
        return view.getSelectionModel();
    }
}
