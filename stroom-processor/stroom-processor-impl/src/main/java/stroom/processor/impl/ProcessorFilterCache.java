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

package stroom.processor.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class ProcessorFilterCache implements Clearable {

    private static final String CACHE_NAME = "Processor Filter Cache";

    private final LoadingStroomCache<Integer, Optional<ProcessorFilter>> cache;
    private final ProcessorFilterDao processorFilterDao;
    private final SecurityContext securityContext;

    @Inject
    public ProcessorFilterCache(final CacheManager cacheManager,
                                final ProcessorFilterDao processorFilterDao,
                                final SecurityContext securityContext,
                                final Provider<ProcessorConfig> processorConfigProvider) {
        this.processorFilterDao = processorFilterDao;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> processorConfigProvider.get().getProcessorFilterCache(),
                this::create);
    }

    public Optional<ProcessorFilter> get(final int id) {
        return cache.get(id);
    }

    private Optional<ProcessorFilter> create(final int id) {
        return securityContext.asProcessingUserResult(() -> processorFilterDao.fetch(id));
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
