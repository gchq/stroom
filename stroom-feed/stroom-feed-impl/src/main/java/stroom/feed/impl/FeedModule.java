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

package stroom.feed.impl;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.FeedStore;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.meta.api.MetaSecurityFilter;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class FeedModule extends AbstractModule {

    @Override
    protected void configure() {
        // Needed in FeedStoreImpl
        requireBinding(FsVolumeGroupService.class);

        bind(FeedStore.class).to(FeedStoreImpl.class);
        bind(FeedProperties.class).to(FeedPropertiesImpl.class);
        bind(MetaSecurityFilter.class).to(MetaSecurityFilterImpl.class);
        bind(VolumeGroupNameProvider.class).to(VolumeGroupNameProviderImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(FeedStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(FeedStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(FeedStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(FeedDocCache.class)
                .addBinding(MetaSecurityFilterImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(FeedDocCache.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(FeedDoc.TYPE, FeedStoreImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(FeedDoc.class, FeedDocObjectInfoProvider.class);

        RestResourcesBinder.create(binder())
                .bind(FeedResourceImpl.class);
    }
}
