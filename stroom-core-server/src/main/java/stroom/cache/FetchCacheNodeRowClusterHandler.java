/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.shared.CacheInfo;
import stroom.entity.shared.ResultList;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class FetchCacheNodeRowClusterHandler extends AbstractTaskHandler<FetchCacheNodeRowClusterTask, ResultList<CacheInfo>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchCacheNodeRowClusterHandler.class);

    private final CacheManagerService stroomCacheManager;
    private final Security security;

    @Inject
    FetchCacheNodeRowClusterHandler(final CacheManagerService stroomCacheManager,
                                    final Security security) {
        this.stroomCacheManager = stroomCacheManager;
        this.security = security;
    }

    @SuppressWarnings({"rawtypes"})
    @Override
    public ResultList<CacheInfo> exec(final FetchCacheNodeRowClusterTask task) {
        return security.secureResult(() -> {
            ResultList<CacheInfo> result = null;

            try {
                if (task == null) {
                    throw new RuntimeException("No task supplied");
                }
                result = stroomCacheManager.find(task.getCriteria());

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            return result;
        });
    }
}
