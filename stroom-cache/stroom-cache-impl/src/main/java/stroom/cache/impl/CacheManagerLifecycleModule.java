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

package stroom.cache.impl;


import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

public class CacheManagerLifecycleModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        LifecycleBinder.create(binder())
                .bindStartupTaskTo(CacheManagerClose.class);
    }

    private static class CacheManagerClose extends RunnableWrapper {
        @Inject
        CacheManagerClose(final CacheManagerImpl cacheManager) {
            super(cacheManager::close);
        }
    }
}