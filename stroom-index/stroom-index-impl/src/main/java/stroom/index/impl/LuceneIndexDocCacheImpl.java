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
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@EntityEventHandler(
        type = LuceneIndexDoc.DOCUMENT_TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class LuceneIndexDocCacheImpl implements LuceneIndexDocCache, Clearable, EntityEvent.Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneIndexDocCacheImpl.class);

    private static final String CACHE_NAME = "Index Doc Cache";

    private final IndexStore indexStore;
    private final LoadingStroomCache<DocRef, LuceneIndexDoc> cache;

    @Inject
    LuceneIndexDocCacheImpl(final CacheManager cacheManager,
                            final IndexStore indexStore,
                            final Provider<IndexConfig> indexConfigProvider) {
        this.indexStore = indexStore;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> indexConfigProvider.get().getIndexStructureCache(),
                this::create);
    }

    private LuceneIndexDoc create(final DocRef docRef) {
        if (docRef == null) {
            throw new NullPointerException("Null key supplied");
        }

        final LuceneIndexDoc loaded = indexStore.readDocument(docRef);
        if (loaded == null) {
            throw new NullPointerException("No index can be found for: " + docRef);
        }

        return loaded;
    }

    @Override
    public LuceneIndexDoc get(final DocRef key) {
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

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("Received event {}", event);
        final EntityAction eventAction = event.getAction();

        switch (eventAction) {
            case CLEAR_CACHE -> {
                LOGGER.debug("Clearing cache");
                clear();
            }
            case UPDATE, DELETE -> {
                NullSafe.consume(
                        event.getDocRef(),
                        docRef -> {
                            LOGGER.debug("Invalidating docRef {}", docRef);
                            cache.invalidate(docRef);
                        });
            }
            default -> LOGGER.debug("Unexpected event action {}", eventAction);
        }
    }
}
