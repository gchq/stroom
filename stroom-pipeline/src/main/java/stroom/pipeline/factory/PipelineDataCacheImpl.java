/*
 * Copyright 2016 Crown Copyright
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
import stroom.cache.api.ICache;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.security.api.DocumentPermissionCache;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PipelineDataCacheImpl implements PipelineDataCache, Clearable {
    private static final String CACHE_NAME = "Pipeline Structure Cache";

    private final PipelineStackLoader pipelineStackLoader;
    private final ICache<PipelineDoc, PipelineData> cache;
    private final SecurityContext securityContext;
    private final DocumentPermissionCache documentPermissionCache;

    @Inject
    public PipelineDataCacheImpl(final CacheManager cacheManager,
                                 final PipelineStackLoader pipelineStackLoader,
                                 final SecurityContext securityContext,
                                 final DocumentPermissionCache documentPermissionCache,
                                 final PipelineConfig pipelineConfig) {
        this.pipelineStackLoader = pipelineStackLoader;
        this.securityContext = securityContext;
        this.documentPermissionCache = documentPermissionCache;
        cache = cacheManager.create(CACHE_NAME, pipelineConfig::getPipelineDataCache, this::create);
    }

    @Override
    public PipelineData get(final PipelineDoc pipelineDoc) {
        if (!documentPermissionCache.hasDocumentPermission(pipelineDoc.getUuid(), DocumentPermissionNames.USE)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to use " + pipelineDoc);
        }

        return cache.get(pipelineDoc);
    }

    private PipelineData create(final PipelineDoc pipelineDoc) {
        return securityContext.asProcessingUserResult(() -> {
            final List<PipelineDoc> pipelines = pipelineStackLoader.loadPipelineStack(pipelineDoc);
            // Iterate over the pipeline list reading the deepest ancestor first.
            final List<PipelineData> configStack = new ArrayList<>(pipelines.size());

            for (final PipelineDoc pipe : pipelines) {
                final PipelineData pipelineData = pipe.getPipelineData();
                if (pipelineData != null) {
                    configStack.add(pipelineData);
                }
            }

            final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
            try {
                pipelineDataMerger.merge(configStack);
            } catch (final PipelineModelException e) {
                throw new PipelineFactoryException(e);
            }

            return pipelineDataMerger.createMergedData();
        });
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
