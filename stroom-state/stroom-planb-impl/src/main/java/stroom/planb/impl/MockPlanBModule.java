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

package stroom.planb.impl;

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.docstore.api.ContentIndexable;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.pipeline.xsltfunctions.PlanBLookup;
import stroom.planb.impl.data.FileTransferClient;
import stroom.planb.impl.data.FileTransferClientImpl;
import stroom.planb.impl.pipeline.PlanBElementModule;
import stroom.planb.impl.pipeline.PlanBLookupImpl;
import stroom.planb.impl.pipeline.StateProviderImpl;
import stroom.query.language.functions.StateProvider;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class MockPlanBModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new PlanBElementModule());

        bind(PlanBLookup.class).to(PlanBLookupImpl.class);
        GuiceUtil.buildMultiBinder(binder(), StateProvider.class).addBinding(StateProviderImpl.class);

        // Caches
        bind(PlanBDocCache.class).to(PlanBDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(PlanBDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(PlanBDocCacheImpl.class);

        // State
        bind(PlanBDocStore.class).to(PlanBDocStoreImpl.class);
        bind(FileTransferClient.class).to(FileTransferClientImpl.class);
        bind(TargetNodeSetFactory.class).toProvider(() -> null);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(PlanBDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(PlanBDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(PlanBDocStoreImpl.class);
    }
}
