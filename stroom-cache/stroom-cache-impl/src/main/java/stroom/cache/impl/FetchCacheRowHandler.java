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

import stroom.cache.shared.CacheRow;
import stroom.cache.shared.FetchCacheRowAction;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class FetchCacheRowHandler extends AbstractTaskHandler<FetchCacheRowAction, ResultList<CacheRow>> {
    private final CacheManagerImpl cacheManager;
    private final SecurityContext securityContext;

    @Inject
    FetchCacheRowHandler(final CacheManagerImpl cacheManager,
                         final SecurityContext securityContext) {
        this.cacheManager = cacheManager;
        this.securityContext = securityContext;
    }

    @Override
    public ResultList<CacheRow> exec(final FetchCacheRowAction action) {
        return securityContext.secureResult(PermissionNames.MANAGE_CACHE_PERMISSION, () -> {
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
