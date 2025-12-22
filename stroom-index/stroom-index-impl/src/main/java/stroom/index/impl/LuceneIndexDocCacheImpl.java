/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@Singleton
// EntityEvents are handled by IndexConfigCacheEntityEventHandler
public class LuceneIndexDocCacheImpl implements LuceneIndexDocCache, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneIndexDocCacheImpl.class);

    private static final String CACHE_NAME = "Index Doc Cache";

    private final IndexStore indexStore;
    private final LoadingStroomCache<DocRef, LuceneIndexDoc> cache;
    private final SecurityContext securityContext;

    @Inject
    LuceneIndexDocCacheImpl(final CacheManager cacheManager,
                            final IndexStore indexStore,
                            final SecurityContext securityContext,
                            final Provider<IndexConfig> indexConfigProvider) {
        this.indexStore = indexStore;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> indexConfigProvider.get().getIndexCache(),
                this::create);
    }

    private LuceneIndexDoc create(final DocRef docRef) {
        return securityContext.asProcessingUserResult(() -> {
            final LuceneIndexDoc loaded = indexStore.readDocument(docRef);
            if (loaded == null) {
                throw new NullPointerException("No index can be found for: " + docRef);
            }

            return loaded;
        });
    }

    @Override
    public LuceneIndexDoc get(final DocRef docRef) {
        Objects.requireNonNull(docRef, "Null DocRef supplied");

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to read {}", docRef));
        }
        return cache.get(docRef);
    }

    @Override
    public void remove(final DocRef docRef) {
        LOGGER.debug("Invalidating docRef {}", docRef);
        cache.invalidate(docRef);
    }

    @Override
    public void clear() {
        LOGGER.debug("Clearing cache");
        cache.clear();
    }
}
