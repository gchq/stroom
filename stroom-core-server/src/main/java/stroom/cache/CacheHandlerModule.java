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

package stroom.cache;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.cache.shared.CacheClearAction;
import stroom.cache.shared.FetchCacheNodeRowAction;
import stroom.cache.shared.FetchCacheRowAction;
import stroom.entity.shared.Clearable;
import stroom.task.api.TaskHandlerBinder;
import stroom.task.api.job.ScheduledJobsBinder;
import stroom.util.cache.CacheManager;
import stroom.util.lifecycle.LifecycleAwareBinder;

public class CacheHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StroomCacheManager.class).to(StroomCacheManagerImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(CacheClearAction.class, CacheClearHandler.class)
                .bind(CacheClearClusterTask.class, CacheClearClusterHandler.class)
                .bind(FetchCacheNodeRowAction.class, FetchCacheNodeRowHandler.class)
                .bind(FetchCacheNodeRowClusterTask.class, FetchCacheNodeRowClusterHandler.class)
                .bind(FetchCacheRowAction.class, FetchCacheRowHandler.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(StroomCacheManagerImpl.class);

        ScheduledJobsBinder.create(binder()).bind(CacheJobs.class);

        LifecycleAwareBinder.create(binder()).bind(CacheManagerImpl.class);
    }
}