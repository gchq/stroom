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

package stroom.explorer.impl;

import stroom.collection.api.CollectionService;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.explorer.api.ExplorerActionHandlerBinder;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class ExplorerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ExplorerNodeService.class).to(ExplorerNodeServiceImpl.class);
        bind(ExplorerSession.class).to(ExplorerSessionImpl.class);
        bind(ExplorerService.class).to(ExplorerServiceImpl.class);
        bind(ExplorerEventLog.class).to(ExplorerEventLogImpl.class);
        bind(CollectionService.class).to(ExplorerServiceImpl.class);
        bind(DocRefInfoService.class).to(DocRefInfoServiceImpl.class);

        ExplorerActionHandlerBinder.create(binder())
                .bind(FolderExplorerActionHandler.class)
                .bind(SystemExplorerActionHandler.class);

        RestResourcesBinder.create(binder())
                .bind(ExplorerResourceImpl.class)
                .bind(NewUIExplorerResource.class);

        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(DocRefInfoCache.class);
    }
}