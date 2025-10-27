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
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.api.ExplorerDecorator;
import stroom.explorer.api.ExplorerNodePermissionsService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerFields;
import stroom.suggestions.api.SuggestionsQueryHandler;
import stroom.suggestions.api.SuggestionsServiceBinder;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class ExplorerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DocRefInfoModule());
        bind(ExplorerNodeService.class).to(ExplorerNodeServiceImpl.class);
        bind(ExplorerNodePermissionsService.class).to(ExplorerNodePermissionsServiceImpl.class);
        bind(ExplorerSession.class).to(ExplorerSessionImpl.class);
        bind(ExplorerService.class).to(ExplorerServiceImpl.class);
        bind(SuggestionsQueryHandler.class).to(ExplorerServiceImpl.class);
        bind(ExplorerDecorator.class).to(ExplorerDecoratorImpl.class);
        bind(ExplorerEventLog.class).to(ExplorerEventLogImpl.class);
        bind(CollectionService.class).to(ExplorerServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(FolderExplorerActionHandler.class)
                .addBinding(SystemExplorerActionHandler.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(ExplorerTreeModel.class);

        RestResourcesBinder.create(binder())
                .bind(ExplorerResourceImpl.class);
        RestResourcesBinder.create(binder())
                .bind(PermissionChangeResourceImpl.class);

        SuggestionsServiceBinder.create(binder())
                .bind(ExplorerFields.EXPLORER_TYPE, ExplorerServiceImpl.class);
    }
}
