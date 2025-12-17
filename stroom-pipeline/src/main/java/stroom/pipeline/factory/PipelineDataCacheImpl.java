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
import stroom.docstore.shared.DocRefUtil;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.cache.DocumentPermissionCache;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineLayer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@EntityEventHandler(type = PipelineDoc.TYPE)
public class PipelineDataCacheImpl implements PipelineDataCache, Clearable, EntityEvent.Handler {

    private static final String CACHE_NAME = "Pipeline Structure Cache";

    private final PipelineStackLoader pipelineStackLoader;
    private final LoadingStroomCache<PipelineDoc, PipelineDataHolder> cache;
    private final SecurityContext securityContext;
    private final DocumentPermissionCache documentPermissionCache;

    @Inject
    public PipelineDataCacheImpl(final CacheManager cacheManager,
                                 final PipelineStackLoader pipelineStackLoader,
                                 final SecurityContext securityContext,
                                 final DocumentPermissionCache documentPermissionCache,
                                 final Provider<PipelineConfig> pipelineConfigProvider) {
        this.pipelineStackLoader = pipelineStackLoader;
        this.securityContext = securityContext;
        this.documentPermissionCache = documentPermissionCache;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> pipelineConfigProvider.get().getPipelineDataCache(),
                this::create);
    }

    @Override
    public PipelineData get(final PipelineDoc pipelineDoc) {
        if (!documentPermissionCache.canUseDocument(pipelineDoc.asDocRef())) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to use " + pipelineDoc);
        }

        final PipelineDataHolder pipelineDataHolder = cache.get(pipelineDoc);
        return NullSafe.get(pipelineDataHolder, PipelineDataHolder::getMergedPipelineData);
    }

    private PipelineDataHolder create(final PipelineDoc pipelineDoc) {
        return securityContext.asProcessingUserResult(() -> {
            final List<PipelineDoc> pipelines = pipelineStackLoader.loadPipelineStack(pipelineDoc);
            // Iterate over the pipeline list reading the deepest ancestor first.
            final List<PipelineLayer> pipelineLayers = new ArrayList<>(pipelines.size());

            for (final PipelineDoc pipe : pipelines) {
                final PipelineData pipelineData = pipe.getPipelineData();
                if (pipelineData != null) {
                    pipelineLayers.add(new PipelineLayer(DocRefUtil.create(pipe), pipelineData));
                }
            }

            final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
            try {
                pipelineDataMerger.merge(pipelineLayers);
            } catch (final PipelineModelException e) {
                throw new PipelineFactoryException(e);
            }

            final PipelineData mergedPipelineData = pipelineDataMerger.createMergedData();
            // Include all the docRefs of the docs in the inheritance chain so we can invalidate
            // cache entries if any one of them is changed.
            final Set<DocRef> docRefs = pipelines.stream()
                    .map(DocRefUtil::create)
                    .collect(Collectors.toSet());
            return new PipelineDataHolder(mergedPipelineData, docRefs);
        });
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
            final DocRef docRef = event.getDocRef();
            final DocRef oldDocRef = event.getOldDocRef();

            // The cached PipelineData is a merge of the pipelineData from
            // all pipelineDocs in the inheritance chain so need to check each
            // entry to see if any of them relate to the changed docRef(s)
            cache.invalidateEntries((pipelineDoc, pipelineDataHolder) ->
                    DocRefUtil.isSameDocument(pipelineDoc, docRef)
                    || DocRefUtil.isSameDocument(pipelineDoc, oldDocRef)
                    || pipelineDataHolder.containsDocRef(docRef)
                    || pipelineDataHolder.containsDocRef(oldDocRef));
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class PipelineDataHolder {

        private final PipelineData mergedPipelineData;
        private final Set<DocRef> docRefs;

        private PipelineDataHolder(final PipelineData mergedPipelineData,
                                   final Set<DocRef> docRefs) {
            this.mergedPipelineData = mergedPipelineData;
            this.docRefs = docRefs;
        }

        PipelineData getMergedPipelineData() {
            return mergedPipelineData;
        }

        boolean containsDocRef(final DocRef docRef) {
            return docRefs.contains(docRef);
        }

        @Override
        public String toString() {
            return "PipelineDataHolder{" +
                   "mergedPipelineData=" + mergedPipelineData +
                   ", docRefs=" + docRefs +
                   '}';
        }
    }
}
