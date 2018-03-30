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

package stroom.cache;

import stroom.cache.shared.CacheRow;
import stroom.cache.shared.FetchCacheRowAction;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.security.shared.ApplicationPermissionNames;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@TaskHandlerBean(task = FetchCacheRowAction.class)
class FetchCacheRowHandler extends AbstractTaskHandler<FetchCacheRowAction, ResultList<CacheRow>> {
    private final CacheManager cacheManager;
    private final Security security;

    @Inject
    FetchCacheRowHandler(final CacheManager cacheManager,
                         final Security security) {
        this.cacheManager = cacheManager;
        this.security = security;
    }

    @Override
    public ResultList<CacheRow> exec(final FetchCacheRowAction action) {
        return security.secureResult(ApplicationPermissionNames.MANAGE_CACHE_PERMISSION, () -> {
            final List<CacheRow> values = cacheManager.getCaches()
                    .keySet()
                    .stream()
                    .sorted(Comparator.naturalOrder())
                    .map(CacheRow::new)
                    .collect(Collectors.toList());

            return BaseResultList.createUnboundedList(values);
        });
    }
}
