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

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingICache;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Clearable;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class ProcessorCache implements Clearable {

    private static final String CACHE_NAME = "Processor Cache";

    private final LoadingICache<Integer, Optional<Processor>> cache;
    private final ProcessorService processorService;
    private final SecurityContext securityContext;

    @Inject
    public ProcessorCache(final CacheManager cacheManager,
                          final ProcessorService processorService,
                          final SecurityContext securityContext,
                          final Provider<ProcessorConfig> processorConfigProvider) {
        this.processorService = processorService;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> processorConfigProvider.get().getProcessorCache(),
                this::create);
    }

    public Optional<Processor> get(final int id) {
        return cache.get(id);
    }

    private Optional<Processor> create(final int id) {
        return securityContext.asProcessingUserResult(() -> processorService.fetch(id));
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
