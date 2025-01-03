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

package stroom.planb.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.job.api.ScheduledJobsBinder;
import stroom.pipeline.xsltfunctions.StateLookupProvider;
import stroom.planb.impl.pipeline.PlanBElementModule;
import stroom.planb.impl.pipeline.StateLookupProviderImpl;
import stroom.planb.impl.pipeline.StateProviderImpl;
import stroom.planb.shared.PlanBDoc;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.SearchProvider;
import stroom.query.language.functions.StateProvider;
import stroom.util.RunnableWrapper;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class StateModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new PlanBElementModule());

        GuiceUtil.buildMultiBinder(binder(), StateLookupProvider.class).addBinding(StateLookupProviderImpl.class);
        GuiceUtil.buildMultiBinder(binder(), StateProvider.class).addBinding(StateProviderImpl.class);

        // Caches
        bind(PlanBDocCache.class).to(PlanBDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(PlanBDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(PlanBDocCacheImpl.class);

        // State
        bind(PlanBDocStore.class).to(PlanBDocStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(PlanBDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(PlanBDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(PlanBDocStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(PlanBDoc.TYPE, PlanBDocStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(PlanBDocResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(StateSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), SearchProvider.class)
                .addBinding(StateSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), IndexFieldProvider.class)
                .addBinding(StateSearchProvider.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(StateMaintenanceRunnable.class, builder -> builder
                        .name(StateMaintenanceExecutor.TASK_NAME)
                        .description("State store maintenance")
                        .cronSchedule("0 0 0 * * ?")
                        .advanced(true));
    }

    private static class StateMaintenanceRunnable extends RunnableWrapper {

        @Inject
        StateMaintenanceRunnable(final StateMaintenanceExecutor condenserExecutor,
                                 final ClusterLockService clusterLockService) {
            super(() -> clusterLockService.tryLock(StateMaintenanceExecutor.TASK_NAME, condenserExecutor::exec));
        }
    }
}
