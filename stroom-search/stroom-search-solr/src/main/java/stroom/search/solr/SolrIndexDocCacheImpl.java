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

package stroom.search.solr;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

@Singleton
@EntityEventHandler(type = SolrIndexDoc.TYPE, action = {
        EntityAction.CREATE,
        EntityAction.DELETE,
        EntityAction.UPDATE})
class SolrIndexDocCacheImpl implements SolrIndexDocCache, EntityEvent.Handler, Clearable {

    private static final String CACHE_NAME = "Solr Index Doc Cache";

    private final SolrIndexStore solrIndexStore;
    private final SecurityContext securityContext;
    private final LoadingStroomCache<DocRef, SolrIndexDoc> cache;

    @Inject
    SolrIndexDocCacheImpl(final CacheManager cacheManager,
                          final SolrIndexStore solrIndexStore,
                          final SecurityContext securityContext,
                          final Provider<SolrConfig> solrConfigProvider) {
        this.solrIndexStore = solrIndexStore;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> solrConfigProvider.get().getIndexCache(),
                this::create);
    }

    private SolrIndexDoc create(final DocRef docRef) {
        return securityContext.asProcessingUserResult(() -> {
            final SolrIndexDoc loaded = solrIndexStore.readDocument(docRef);
            if (loaded == null) {
                throw new NullPointerException("No index can be found for: " + docRef);
            }

            return loaded;
        });
    }

    @Override
    public SolrIndexDoc get(final DocRef docRef) {
        Objects.requireNonNull(docRef, "Null DocRef supplied");

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to read {}", docRef));
        }
        return cache.get(docRef);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (SolrIndexDoc.TYPE.equals(event.getDocRef().getType())) {
            cache.invalidate(event.getDocRef());
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
