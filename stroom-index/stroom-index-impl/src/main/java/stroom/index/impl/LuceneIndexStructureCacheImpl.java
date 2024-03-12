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

package stroom.index.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.index.shared.LuceneIndexFieldsMap;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class LuceneIndexStructureCacheImpl implements LuceneIndexStructureCache, Clearable {

    private static final String CACHE_NAME = "Index Config Cache";

    private final IndexStore indexStore;
    private final LoadingStroomCache<DocRef, LuceneIndexStructure> cache;

    @Inject
    LuceneIndexStructureCacheImpl(final CacheManager cacheManager,
                                  final IndexStore indexStore,
                                  final Provider<IndexConfig> indexConfigProvider) {
        this.indexStore = indexStore;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> indexConfigProvider.get().getIndexStructureCache(),
                this::create);
    }

    private LuceneIndexStructure create(final DocRef docRef) {
        if (docRef == null) {
            throw new NullPointerException("Null key supplied");
        }

        final LuceneIndexDoc loaded = indexStore.readDocument(docRef);
        if (loaded == null) {
            throw new NullPointerException("No index can be found for: " + docRef);
        }

        // Create a map of index fields keyed by name.
        List<LuceneIndexField> indexFields = loaded.getFields();
        if (indexFields == null) {
            indexFields = new ArrayList<>();
        }

        final LuceneIndexFieldsMap indexFieldsMap = new LuceneIndexFieldsMap(indexFields);
        return new LuceneIndexStructure(loaded, indexFields, indexFieldsMap);
    }

    @Override
    public LuceneIndexStructure get(final DocRef key) {
        return cache.get(key);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
