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

package stroom.search.elastic;

import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.EntityAction;
import stroom.query.api.v2.DocRef;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.util.cache.CacheManager;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@EntityEventHandler(type = ElasticIndex.ENTITY_TYPE, action = {EntityAction.CREATE, EntityAction.DELETE, EntityAction.UPDATE})
@Component
public class ElasticIndexCacheImpl implements ElasticIndexCache, EntityEvent.Handler {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final ElasticIndexService elasticIndexService;

    private final LoadingCache<DocRef, ElasticIndex> cache;

    @Inject
    @SuppressWarnings("unchecked")
    ElasticIndexCacheImpl(final CacheManager cacheManager,
                          final ElasticIndexStore elasticIndexStore,
                          final ElasticIndexService elasticIndexService
    ) {
        this.elasticIndexService = elasticIndexService;

        final CacheLoader<DocRef, ElasticIndex> cacheLoader = CacheLoader.from(k -> {
            if (k == null) {
                throw new NullPointerException("Null key supplied");
            }

            final ElasticIndex index = elasticIndexStore.read(k.getUuid());

            // Query field mappings and cache with the index
            index.setFields(elasticIndexService.getFields(index));
            index.setDataSourceFields(elasticIndexService.getDataSourceFields(index));

            if (index == null) {
                throw new NullPointerException("No Elasticsearch index can be found for: " + k);
            }

            return new ElasticIndex();
        });

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterWrite(10, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Elasticsearch Index Cache", cacheBuilder, cache);
    }

    @Override
    public ElasticIndex get(final DocRef key) {
        return cache.getUnchecked(key);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (ElasticIndex.ENTITY_TYPE.equals(event.getDocRef().getType())) {
            cache.invalidate(event.getDocRef());
        }
    }
}
