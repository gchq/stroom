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

import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.springframework.stereotype.Component;
import stroom.cache.Loader;
import stroom.entity.server.DocumentPermissionCache;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.PermissionException;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pool.VersionedEntityDecorator;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.cache.CentralCacheManager;
import stroom.util.task.ServerTask;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Insecure
@Component
public class PipelineDataCacheImpl implements PipelineDataCache {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final PipelineStackLoader pipelineStackLoader;
    private final Cache<VersionedEntityDecorator, PipelineData> cache;
    private final SecurityContext securityContext;
    private final DocumentPermissionCache documentPermissionCache;

    @Inject
    public PipelineDataCacheImpl(final CentralCacheManager cacheManager,
                                 final PipelineStackLoader pipelineStackLoader,
                                 final SecurityContext securityContext,
                                 final DocumentPermissionCache documentPermissionCache) {
        this.pipelineStackLoader = pipelineStackLoader;
        this.securityContext = securityContext;
        this.documentPermissionCache = documentPermissionCache;

        final Loader<VersionedEntityDecorator, PipelineData> loader = new Loader<VersionedEntityDecorator, PipelineData>() {
            @Override
            public PipelineData load(final VersionedEntityDecorator key) throws Exception {
                return create(key);
            }
        };

        final CacheConfiguration<VersionedEntityDecorator, PipelineData> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(VersionedEntityDecorator.class, PipelineData.class,
                ResourcePoolsBuilder.heap(MAX_CACHE_ENTRIES))
                .withExpiry(Expirations.timeToIdleExpiration(Duration.of(10, TimeUnit.MINUTES)))
                .withLoaderWriter(loader)
                .build();

        cache = cacheManager.createCache("Pipeline Structure Cache", cacheConfiguration);
    }

    @Override
    public PipelineData get(final PipelineEntity pipelineEntity) {
        if (!documentPermissionCache.hasDocumentPermission(pipelineEntity.getType(), pipelineEntity.getUuid(), DocumentPermissionNames.USE)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to use " + DocRef.create(pipelineEntity));
        }

        return cache.get(new VersionedEntityDecorator<>(pipelineEntity));
    }

    private PipelineData create(final VersionedEntityDecorator key) {
        securityContext.pushUser(ServerTask.INTERNAL_PROCESSING_USER_TOKEN);
        try {
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
        } finally {
            securityContext.popUser();
        }
    }
}
