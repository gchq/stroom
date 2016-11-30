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

package stroom.index.server;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.Clearable;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.query.shared.IndexFields;
import stroom.query.shared.IndexFieldsMap;

import javax.annotation.Resource;

@Component
@EntityEventHandler(type = Index.ENTITY_TYPE)
public class CachedIndexService implements Clearable, InitializingBean, EntityEvent.Handler {
    private static final int MAX_CACHE_ENTRIES = 10;
    private final Cache cache;
    private final SelfPopulatingCache selfPopulatingCache;
    @Resource
    private CacheManager cacheManager;
    @Resource
    private IndexService indexService;
    public CachedIndexService() {
        final CacheConfiguration cacheConfiguration = new CacheConfiguration("Index Fields Map Cache",
                MAX_CACHE_ENTRIES);
        cacheConfiguration.setEternal(false);
        // Allow collectors to idle for 10 minutes.
        cacheConfiguration.setTimeToIdleSeconds(600);
        // Allow collectors to live for 10 minutes.
        cacheConfiguration.setTimeToLiveSeconds(600);

        cache = new Cache(cacheConfiguration);
        selfPopulatingCache = new SelfPopulatingCache(cache, key -> {
            Index index = (Index) key;
            index = indexService.load(index);
            if (index != null) {
                // Create a map of index fields keyed by name.
                final IndexFields indexFields = index.getIndexFieldsObject();
                final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexFields);
                return new CachedIndex(index, indexFields, indexFieldsMap);
            }

            return null;
        });
    }

    public CachedIndex get(final Index index) {
        final Element element = selfPopulatingCache.get(index);
        if (element == null) {
            return null;
        }
        return (CachedIndex) element.getObjectValue();
    }

    @Override
    public void clear() {
        selfPopulatingCache.removeAll();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        cacheManager.addCache(cache);
    }

    @Override
    public void onChange(final EntityEvent event) {
        clear();
    }

    public class CachedIndex {
        private final Index index;
        private final IndexFields indexFields;
        private final IndexFieldsMap indexFieldsMap;

        public CachedIndex(final Index index, final IndexFields indexFields, final IndexFieldsMap indexFieldsMap) {
            this.index = index;
            this.indexFields = indexFields;
            this.indexFieldsMap = indexFieldsMap;
        }

        public Index getIndex() {
            return index;
        }

        public IndexFields getIndexFields() {
            return indexFields;
        }

        public IndexFieldsMap getIndexFieldsMap() {
            return indexFieldsMap;
        }
    }
}
