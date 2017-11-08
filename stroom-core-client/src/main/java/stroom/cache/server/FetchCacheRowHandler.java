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

import org.springframework.context.annotation.Scope;
import stroom.cache.shared.CacheRow;
import stroom.cache.shared.FetchCacheRowAction;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.security.Secured;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@TaskHandlerBean(task = FetchCacheRowAction.class)
@Scope(StroomScope.TASK)
@Secured(CacheRow.MANAGE_CACHE_PERMISSION)
class FetchCacheRowHandler extends AbstractTaskHandler<FetchCacheRowAction, ResultList<CacheRow>> {
    private final CacheManager cacheManager;

    @Inject
    FetchCacheRowHandler(final CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public ResultList<CacheRow> exec(final FetchCacheRowAction action) {
        final List<CacheRow> values = cacheManager.getCaches()
                .keySet()
                .stream()
                .sorted(Comparator.naturalOrder())
                .map(CacheRow::new)
                .collect(Collectors.toList());

        return BaseResultList.createUnboundedList(values);
    }
}
