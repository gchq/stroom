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

package stroom.processor.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import stroom.cache.api.CacheManager;
import stroom.cache.api.CacheUtil;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
public class ProcessorFilterCache implements Clearable {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final LoadingCache<Integer, Optional<ProcessorFilter>> cache;
    private final ProcessorFilterService processorFilterService;
    private final SecurityContext securityContext;

    @Inject
    @SuppressWarnings("unchecked")
    public ProcessorFilterCache(final CacheManager cacheManager,
                                final ProcessorFilterService processorFilterService,
                                final SecurityContext securityContext) {
        this.processorFilterService = processorFilterService;
        this.securityContext = securityContext;

        final CacheLoader<Integer, Optional<ProcessorFilter>> cacheLoader = CacheLoader.from(this::create);
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.SECONDS);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Processor Filter Cache", cacheBuilder, cache);
    }

    public Optional<ProcessorFilter> get(final int id) {
        return cache.getUnchecked(id);
    }

    private Optional<ProcessorFilter> create(final int id) {
        return securityContext.asProcessingUserResult(() -> processorFilterService.fetch(id));
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }
}
