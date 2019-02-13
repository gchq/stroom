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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.impl.db.ExplorerDbModule;
import stroom.explorer.shared.ExplorerServiceCopyAction;
import stroom.explorer.shared.ExplorerServiceCreateAction;
import stroom.explorer.shared.ExplorerServiceDeleteAction;
import stroom.explorer.shared.ExplorerServiceInfoAction;
import stroom.explorer.shared.ExplorerServiceMoveAction;
import stroom.explorer.shared.ExplorerServiceRenameAction;
import stroom.explorer.shared.FetchDocRefsAction;
import stroom.explorer.shared.FetchDocumentTypesAction;
import stroom.explorer.shared.FetchExplorerNodeAction;
import stroom.explorer.shared.FetchExplorerPermissionsAction;
import stroom.task.api.TaskHandlerBinder;

public class ExplorerModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new ExplorerDbModule());

        bind(ExplorerNodeService.class).to(ExplorerNodeServiceImpl.class);
        bind(ExplorerService.class).to(ExplorerServiceImpl.class);
        bind(ExplorerEventLog.class).to(ExplorerEventLogImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(ExplorerServiceCopyAction.class, ExplorerServiceCopyHandler.class)
                .bind(ExplorerServiceCreateAction.class, ExplorerServiceCreateHandler.class)
                .bind(ExplorerServiceDeleteAction.class, ExplorerServiceDeleteHandler.class)
                .bind(ExplorerServiceInfoAction.class, ExplorerServiceInfoHandler.class)
                .bind(ExplorerServiceMoveAction.class, ExplorerServiceMoveHandler.class)
                .bind(ExplorerServiceRenameAction.class, ExplorerServiceRenameHandler.class)
                .bind(FetchDocRefsAction.class, FetchDocRefsHandler.class)
                .bind(FetchDocumentTypesAction.class, FetchDocumentTypesHandler.class)
                .bind(FetchExplorerNodeAction.class, FetchExplorerNodeHandler.class)
                .bind(FetchExplorerPermissionsAction.class, FetchExplorerPermissionsHandler.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(FolderExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(SystemExplorerActionHandler.class);
    }
}