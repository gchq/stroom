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

import com.google.common.base.Functions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.stereotype.Component;
import stroom.query.api.v2.DocRef;
import stroom.search.solr.shared.SolrIndex;
import stroom.search.solr.shared.SolrIndexField;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class SolrIndexCacheImpl implements SolrIndexCache {
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

            final SolrIndex loaded = solrIndexStore.read(k.getUuid());
            if (loaded == null) {
                throw new NullPointerException("No solr index can be found for: " + k);
            }

            // Create a map of index fields keyed by name.
            final List<SolrIndexField> fields = loaded.getFields();
            if (fields == null || fields.size() == 0) {
                throw new SolrIndexException("No index fields have been set for: " + k);
            }

            final Map<String, SolrIndexField> fieldMap = fields.stream().collect(Collectors.toMap(SolrIndexField::getFieldName, Functions.identity()));
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
}
