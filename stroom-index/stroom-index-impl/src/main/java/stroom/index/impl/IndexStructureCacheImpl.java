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
import stroom.cache.api.ICache;
import stroom.docref.DocRef;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class IndexStructureCacheImpl implements IndexStructureCache, Clearable {
    private static final String CACHE_NAME = "Index Config Cache";

    private final IndexStore indexStore;
    private final ICache<DocRef, IndexStructure> cache;

    @Inject
    IndexStructureCacheImpl(final CacheManager cacheManager,
                            final IndexStore indexStore,
                            final IndexConfig indexConfig) {
        this.indexStore = indexStore;
        cache = cacheManager.create(CACHE_NAME, indexConfig::getIndexStructureCache, this::create);
    }

    private IndexStructure create(final DocRef docRef) {
        if (docRef == null) {
            throw new NullPointerException("Null key supplied");
        }

        final IndexDoc loaded = indexStore.readDocument(docRef);
        if (loaded == null) {
            throw new NullPointerException("No index can be found for: " + docRef);
        }

        // Create a map of index fields keyed by name.
        List<IndexField> indexFields = loaded.getFields();
        if (indexFields == null) {
            indexFields = new ArrayList<>();
        }

        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexFields);
        return new IndexStructure(loaded, indexFields, indexFieldsMap);
    }

    @Override
    public IndexStructure get(final DocRef key) {
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
