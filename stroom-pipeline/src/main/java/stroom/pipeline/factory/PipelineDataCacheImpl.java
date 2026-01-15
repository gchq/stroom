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

package stroom.pipeline.factory;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.cache.DocumentPermissionCache;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@EntityEventHandler(type = PipelineDoc.TYPE)
public class PipelineDataCacheImpl implements PipelineDataCache, Clearable, EntityEvent.Handler {

    private static final String CACHE_NAME = "Pipeline Structure Cache";

    private final LoadingStroomCache<DocRef, PipelineDataHolder> cache;
    private final SecurityContext securityContext;
    private final DocumentPermissionCache documentPermissionCache;

    @Inject
    public PipelineDataCacheImpl(final CacheManager cacheManager,
                                 final PipelineDataHolderFactory pipelineDataHolderFactory,
                                 final SecurityContext securityContext,
                                 final DocumentPermissionCache documentPermissionCache,
                                 final Provider<PipelineConfig> pipelineConfigProvider) {
        this.securityContext = securityContext;
        this.documentPermissionCache = documentPermissionCache;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> pipelineConfigProvider.get().getPipelineDataCache(),
                pipelineDataHolderFactory::create);
    }

    @Override
    public PipelineData get(final PipelineDoc pipelineDoc) {
        final DocRef docRef = pipelineDoc.asDocRef();
        if (!documentPermissionCache.canUseDocument(docRef)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to use " + pipelineDoc);
        }

        final PipelineDataHolder pipelineDataHolder = cache.get(docRef);
        return NullSafe.get(pipelineDataHolder, PipelineDataHolder::getMergedPipelineData);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (EntityAction.CLEAR_CACHE.equals(event.getAction())) {
            clear();
        } else {
            final DocRef changedDocRef = event.getDocRef();
            final DocRef oldDocRef = event.getOldDocRef();

            // The cached PipelineData is a merge of the pipelineData from
            // all pipelineDocs in the inheritance chain so need to check each
            // entry to see if any of them relate to the changed docRef(s)
            cache.invalidateEntries((docRef, pipelineDataHolder) ->
                    docRef.equals(changedDocRef)
                    || docRef.equals(oldDocRef)
                    || pipelineDataHolder.containsDocRef(changedDocRef)
                    || pipelineDataHolder.containsDocRef(oldDocRef));
        }
    }
}
