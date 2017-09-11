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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import stroom.security.Insecure;
import stroom.cache.AbstractCacheBean;
import stroom.entity.shared.VersionedEntityDecorator;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import net.sf.ehcache.CacheManager;
import stroom.security.SecurityContext;
import stroom.util.shared.Task;

@Insecure
@Component
public class PipelineDataCacheImpl extends AbstractCacheBean<VersionedEntityDecorator<PipelineEntity>, PipelineData>
        implements PipelineDataCache {
    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final PipelineStackLoader pipelineStackLoader;
    private final SecurityContext securityContext;

    @Inject
    public PipelineDataCacheImpl(final CacheManager cacheManager,
                                 final PipelineStackLoader pipelineStackLoader,
                                 final SecurityContext securityContext) {
        super(cacheManager, "Pipeline Structure Cache", MAX_CACHE_ENTRIES);
        this.pipelineStackLoader = pipelineStackLoader;
        this.securityContext = securityContext;

        setMaxIdleTime(10, TimeUnit.MINUTES);
        setMaxLiveTime(10, TimeUnit.MINUTES);
    }

    @Override
    public PipelineData getOrCreate(final PipelineEntity pipelineEntity) {
        final VersionedEntityDecorator<PipelineEntity> key = new VersionedEntityDecorator<>(pipelineEntity, getUser());
        return computeIfAbsent(key, this::create);
    }

    private String getUser() {
        if (securityContext == null) {
            return null;
        }
        return securityContext.getUserId();
    }

    private PipelineData create(final VersionedEntityDecorator<PipelineEntity> key) {
        final PipelineEntity pipelineEntity = key.getEntity();
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

        final PipelineData mergedPipelineData = pipelineDataMerger.createMergedData();
        return mergedPipelineData;
    }
}
