/*
 * Copyright 2018 Crown Copyright
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

package stroom.cache.service.impl;

import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class CacheServiceModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CacheManagerService.class).to(CacheManagerServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(CacheManagerServiceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(EvictExpiredElements.class, builder -> builder
                        .name("Evict expired elements")
                        .description("Evicts expired cache entries")
                        .managed(false)
                        .frequencySchedule("1m"));
    }


    // --------------------------------------------------------------------------------


    private static class EvictExpiredElements extends RunnableWrapper {

        @Inject
        EvictExpiredElements(final CacheManagerService stroomCacheManager) {
            super(stroomCacheManager::evictExpiredElements);
        }
    }
}
