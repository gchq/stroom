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

package stroom.search.solr;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import stroom.cache.api.CacheManager;
import stroom.cache.api.CacheUtil;
import stroom.docref.DocRef;
import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEventHandler;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexField;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@EntityEventHandler(type = SolrIndexDoc.DOCUMENT_TYPE, action = {EntityAction.CREATE, EntityAction.DELETE, EntityAction.UPDATE})
class SolrIndexCacheImpl implements SolrIndexCache, EntityEvent.Handler, Clearable {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final LoadingCache<DocRef, CachedSolrIndex> cache;

    @Inject
    @SuppressWarnings("unchecked")
    SolrIndexCacheImpl(final CacheManager cacheManager,
                       final SolrIndexStore solrIndexStore) {
        final CacheLoader<DocRef, CachedSolrIndex> cacheLoader = CacheLoader.from(k -> {
            if (k == null) {
                throw new NullPointerException("Null key supplied");
            }

            final SolrIndexDoc loaded = solrIndexStore.readDocument(k);
            if (loaded == null) {
                throw new NullPointerException("No solr index can be found for: " + k);
            }

            // Create a map of index fields keyed by name.
            final List<SolrIndexField> fields = loaded.getFields();
            if (fields == null || fields.size() == 0) {
                throw new SolrIndexException("No index fields have been set for: " + k);
            }

            final Map<String, SolrIndexField> fieldMap = fields.stream().collect(Collectors.toMap(SolrIndexField::getFieldName, Function.identity()));
            return new CachedSolrIndex(loaded, fields, fieldMap);
        });

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterWrite(10, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Solr Index Cache", cacheBuilder, cache);
    }

    @Override
    public CachedSolrIndex get(final DocRef key) {
        return cache.getUnchecked(key);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (SolrIndexDoc.DOCUMENT_TYPE.equals(event.getDocRef().getType())) {
            cache.invalidate(event.getDocRef());
        }
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }
}
