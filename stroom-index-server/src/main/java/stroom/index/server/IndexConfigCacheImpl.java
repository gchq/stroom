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

import net.sf.ehcache.CacheManager;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.index.shared.Index;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexFieldsMap;
import stroom.index.shared.IndexService;
import stroom.query.api.v1.DocRef;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
public class IndexConfigCacheImpl extends AbstractCacheBean<DocRef, IndexConfig> implements IndexConfigCache {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final IndexService indexService;

    @Inject
    IndexConfigCacheImpl(final CacheManager cacheManager,
                         final IndexService indexService) {
        super(cacheManager, "Index Config Cache", MAX_CACHE_ENTRIES);
        this.indexService = indexService;

        setMaxIdleTime(10, TimeUnit.MINUTES);
        setMaxLiveTime(10, TimeUnit.MINUTES);
    }

    @Override
    public IndexConfig getOrCreate(final DocRef key) {
        return computeIfAbsent(key, this::create);
    }

    private IndexConfig create(final DocRef key) {
        final Index loaded = indexService.loadByUuid(key.getUuid());
        if (loaded == null) {
            throw new NullPointerException("No index can be found for: " + key);
        }

        // Create a map of index fields keyed by name.
        final IndexFields indexFields = loaded.getIndexFieldsObject();
        if (indexFields == null || indexFields.getIndexFields() == null || indexFields.getIndexFields().size() == 0) {
            throw new IndexException("No index fields have been set for: " + key);
        }

        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexFields);
        return new IndexConfig(loaded, indexFields, indexFieldsMap);
    }
}
