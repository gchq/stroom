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

package stroom.cache.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.CacheNodeRow;
import stroom.cache.shared.FetchCacheNodeRowAction;
import stroom.cache.shared.FindCacheInfoCriteria;
import stroom.cluster.task.api.ClusterCallEntry;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.DefaultClusterResultCollector;
import stroom.cluster.task.api.TargetType;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;
import stroom.util.shared.StringCriteria;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;


class FetchCacheNodeRowHandler extends AbstractTaskHandler<FetchCacheNodeRowAction, ResultList<CacheNodeRow>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchCacheNodeRowHandler.class);

    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final SecurityContext securityContext;

    @Inject
    FetchCacheNodeRowHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                             final SecurityContext securityContext) {
        this.dispatchHelper = dispatchHelper;
        this.securityContext = securityContext;
    }

    @Override
    public ResultList<CacheNodeRow> exec(final FetchCacheNodeRowAction action) {
        return securityContext.secureResult(PermissionNames.MANAGE_CACHE_PERMISSION, () -> {
            final List<CacheNodeRow> values = new ArrayList<>();

            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
            criteria.setName(new StringCriteria(action.getCacheName(), null));
            final FetchCacheNodeRowClusterTask task = new FetchCacheNodeRowClusterTask(
                    action.getUserToken(), "Find cache info", criteria);
            final DefaultClusterResultCollector<ResultList<CacheInfo>> collector = dispatchHelper.execAsync(task,
                    TargetType.ACTIVE);

            // Sort the list of node names.
            final List<String> nodes = new ArrayList<>(collector.getTargetNodes());
            nodes.sort((o1, o2) -> {
                if (o1 == null || o2 == null) {
                    return 0;
                }
                return o1.compareToIgnoreCase(o2);
            });

            for (final String nodeName : nodes) {
                final ClusterCallEntry<ResultList<CacheInfo>> response = collector.getResponse(nodeName);

                if (response == null) {
                    LOGGER.debug("No response from node: {}", nodeName);
                } else if (response.getError() != null) {
                    LOGGER.debug("Error from node: {} - {}", nodeName, response.getError().getMessage());
                    LOGGER.debug(response.getError().getMessage(), response.getError());
                } else {
                    final ResultList<CacheInfo> result = response.getResult();
                    if (result == null) {
                        LOGGER.debug("No response object received from node: {}", nodeName);
                    } else {
                        for (final CacheInfo value : result) {
                            values.add(new CacheNodeRow(nodeName, value));
                        }
                    }
                }
            }

            return BaseResultList.createUnboundedList(values);
        });
    }
}
