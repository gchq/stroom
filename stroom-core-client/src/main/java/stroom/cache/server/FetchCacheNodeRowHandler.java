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

package stroom.cache.server;

import stroom.cache.StroomCacheManager;
import stroom.cache.shared.*;
import stroom.entity.cluster.FindServiceClusterTask;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.entity.shared.StringCriteria;
import stroom.node.shared.Node;
import stroom.security.Secured;
import stroom.task.cluster.ClusterCallEntry;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.DefaultClusterResultCollector;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomScope;
import org.springframework.context.annotation.Scope;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@TaskHandlerBean(task = FetchCacheNodeRowAction.class)
@Scope(StroomScope.TASK)
@Secured(CacheRow.MANAGE_CACHE_PERMISSION)
class FetchCacheNodeRowHandler extends AbstractTaskHandler<FetchCacheNodeRowAction, ResultList<CacheNodeRow>> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(FetchCacheNodeRowHandler.class);

    private final ClusterDispatchAsyncHelper dispatchHelper;

    @Inject
    FetchCacheNodeRowHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
        this.dispatchHelper = dispatchHelper;
    }

    @Override
    public ResultList<CacheNodeRow> exec(final FetchCacheNodeRowAction action) {
        final List<CacheNodeRow> values = new ArrayList<>();

        final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
        criteria.setName(new StringCriteria(action.getCacheName(), null));
        final FindServiceClusterTask<FindCacheInfoCriteria, CacheInfo> task = new FindServiceClusterTask<>(
                action.getSessionId(), action.getUserId(), "Find cache info", StroomCacheManager.class, criteria);
        final DefaultClusterResultCollector<ResultList<CacheInfo>> collector = dispatchHelper.execAsync(task,
                TargetType.ACTIVE);

        // Sort the list of node names.
        final List<Node> nodes = new ArrayList<Node>(collector.getTargetNodes());
        Collections.sort(nodes, new Comparator<Node>() {
            @Override
            public int compare(final Node o1, final Node o2) {
                if (o1.getName() == null || o2.getName() == null) {
                    return 0;
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        for (final Node node : nodes) {
            final ClusterCallEntry<ResultList<CacheInfo>> response = collector.getResponse(node);

            if (response == null) {
                LOGGER.debug("No response from node: %s", node);
            } else if (response.getError() != null) {
                LOGGER.debug("Error from node: %s - %s", node, response.getError().getMessage());
                LOGGER.debug(response.getError().getMessage(), response.getError());
            } else {
                final ResultList<CacheInfo> result = response.getResult();
                if (result == null) {
                    LOGGER.debug("No response object received from node: %s", node);
                } else {
                    for (final CacheInfo value : result) {
                        values.add(new CacheNodeRow(node, value));
                    }
                }
            }
        }

        return BaseResultList.createUnboundedList(values);
    }
}
