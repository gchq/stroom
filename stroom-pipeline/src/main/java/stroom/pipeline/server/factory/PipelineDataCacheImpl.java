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

package stroom.pipeline.server.factory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.stereotype.Component;
import stroom.entity.server.DocumentPermissionCache;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.PermissionException;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pool.SecurityHelper;
import stroom.pool.VersionedEntityDecorator;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Insecure
@Component
public class PipelineDataCacheImpl implements PipelineDataCache {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final PipelineStackLoader pipelineStackLoader;
    private final LoadingCache<VersionedEntityDecorator<PipelineEntity>, PipelineData> cache;
    private final SecurityContext securityContext;
    private final DocumentPermissionCache documentPermissionCache;

    @Inject
    public PipelineDataCacheImpl(final CacheManager cacheManager,
                                 final PipelineStackLoader pipelineStackLoader,
                                 final SecurityContext securityContext,
                                 final DocumentPermissionCache documentPermissionCache) {
        this.pipelineStackLoader = pipelineStackLoader;
        this.securityContext = securityContext;
        this.documentPermissionCache = documentPermissionCache;

        final CacheLoader<VersionedEntityDecorator<PipelineEntity>, PipelineData> cacheLoader = CacheLoader.from(this::create);
        cache = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(cacheLoader);
        cacheManager.registerCache("Pipeline Structure Cache", cache);
    }

    @Override
    public PipelineData get(final PipelineEntity pipelineEntity) {
        if (!documentPermissionCache.hasDocumentPermission(pipelineEntity.getType(), pipelineEntity.getUuid(), DocumentPermissionNames.USE)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to use " + DocRef.create(pipelineEntity));
        }

        return cache.getUnchecked(new VersionedEntityDecorator<>(pipelineEntity));
    }

    private PipelineData create(final VersionedEntityDecorator key) {
        try (SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            final PipelineEntity pipelineEntity = (PipelineEntity) key.getEntity();
            final List<PipelineEntity> pipelines = pipelineStackLoader.loadPipelineStack(pipelineEntity);
            // Iterate over the pipeline list reading the deepest ancestor first.
            final List<PipelineData> configStack = new ArrayList<>(pipelines.size());

            for (final PipelineEntity pipe : pipelines) {
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
        }
    }
}
