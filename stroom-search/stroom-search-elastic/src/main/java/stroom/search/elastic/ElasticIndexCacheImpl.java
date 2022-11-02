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

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@EntityEventHandler(type = ElasticIndexDoc.DOCUMENT_TYPE, action = {
        EntityAction.CREATE,
        EntityAction.DELETE,
        EntityAction.UPDATE
})
public class ElasticIndexCacheImpl implements ElasticIndexCache, EntityEvent.Handler, Clearable {

    private static final String CACHE_NAME = "Elastic Index Cache";

    private final ElasticIndexStore elasticIndexStore;
    private final ElasticIndexService elasticIndexService;
    private final LoadingStroomCache<DocRef, ElasticIndexDoc> cache;

    @Inject
    ElasticIndexCacheImpl(final CacheManager cacheManager,
                          final ElasticIndexStore elasticIndexStore,
                          final ElasticConfig elasticConfig,
                          final ElasticIndexService elasticIndexService
    ) {
        this.elasticIndexStore = elasticIndexStore;
        this.elasticIndexService = elasticIndexService;
        this.cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                elasticConfig::getIndexCache,
                this::create);
    }

    private ElasticIndexDoc create(final DocRef docRef) {
        if (docRef == null) {
            throw new NullPointerException("Null key supplied");
        }

        final ElasticIndexDoc index = elasticIndexStore.readDocument(docRef);

        if (index == null) {
            throw new NullPointerException("No Elasticsearch index can be found for: " + docRef);
        }

        // Query field mappings and cache with the index
        index.setFields(elasticIndexService.getFields(index));

        return index;
    }

    @Override
    public ElasticIndexDoc get(final DocRef key) {
        return cache.get(key);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (ElasticIndexDoc.DOCUMENT_TYPE.equals(event.getDocRef().getType())) {
            cache.invalidate(event.getDocRef());
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
