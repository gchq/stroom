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

import net.sf.ehcache.CacheManager;
import org.springframework.context.annotation.Scope;
import stroom.cache.shared.CacheRow;
import stroom.cache.shared.FetchCacheRowAction;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.security.Secured;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@TaskHandlerBean(task = FetchCacheRowAction.class)
@Scope(StroomScope.TASK)
@Secured(CacheRow.MANAGE_CACHE_PERMISSION)
public class FetchCacheRowHandler extends AbstractTaskHandler<FetchCacheRowAction, ResultList<CacheRow>> {
    @Resource
    private CacheManager cacheManager;

    @Override
    public ResultList<CacheRow> exec(final FetchCacheRowAction action) {
        final List<CacheRow> values = new ArrayList<>();

        final String[] cacheNames = cacheManager.getCacheNames();
        Arrays.sort(cacheNames);
        for (final String cacheName : cacheNames) {
            values.add(new CacheRow(cacheName));
        }

        // Sort the cache names.
        Collections.sort(values, (o1, o2) -> o1.getCacheName().compareTo(o2.getCacheName()));

        return BaseResultList.createUnboundedList(values);
    }
}
