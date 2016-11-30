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

package stroom.dashboard.server;

import net.sf.ehcache.CacheManager;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;

@Component
public class ActiveQueriesManager extends AbstractCacheBean<String, ActiveQueries> {
    private static final int MAX_ACTIVE_QUERIES = 10000;

    @Inject
    public ActiveQueriesManager(final CacheManager cacheManager) {
        super(cacheManager, "Active Queries", MAX_ACTIVE_QUERIES);
    }

    @Override
    protected ActiveQueries create(final String key) {
        return new ActiveQueries();
    }

    @Override
    @StroomFrequencySchedule("10s")
    public void evictExpiredElements() {
        super.evictExpiredElements();
    }
}
