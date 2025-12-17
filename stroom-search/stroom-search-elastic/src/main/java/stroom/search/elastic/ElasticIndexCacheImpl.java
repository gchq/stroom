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

package stroom.search.elastic;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;
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
@EntityEventHandler(type = ElasticIndexDoc.TYPE, action = {
        EntityAction.CREATE,
        EntityAction.DELETE,
        EntityAction.UPDATE
})
public class ElasticIndexCacheImpl implements ElasticIndexCache, EntityEvent.Handler, Clearable {

    private static final String CACHE_NAME = "Elastic Index Cache";

    private final ElasticIndexStore elasticIndexStore;
    private final SecurityContext securityContext;
    private final LoadingStroomCache<DocRef, ElasticIndexDoc> cache;

    @Inject
    ElasticIndexCacheImpl(final CacheManager cacheManager,
                          final ElasticIndexStore elasticIndexStore,
                          final Provider<ElasticConfig> elasticConfigProvider,
                          final SecurityContext securityContext) {
        this.elasticIndexStore = elasticIndexStore;
        this.securityContext = securityContext;
        this.cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                elasticConfigProvider.get()::getIndexCache,
                this::create);
    }

    private ElasticIndexDoc create(final DocRef docRef) {
        return securityContext.asProcessingUserResult(() -> {
            final ElasticIndexDoc loaded = elasticIndexStore.readDocument(docRef);
            if (loaded == null) {
                throw new NullPointerException("No index can be found for: " + docRef);
            }

            return loaded;
        });
    }

    @Override
    public ElasticIndexDoc get(final DocRef docRef) {
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
        if (ElasticIndexDoc.TYPE.equals(event.getDocRef().getType())) {
            cache.invalidate(event.getDocRef());
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
