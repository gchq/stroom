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

package stroom.explorer;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.explorer.impl.db.ExplorerDbModule;
import stroom.task.api.TaskHandler;

public class ExplorerModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new ExplorerDbModule());

        bind(ExplorerNodeService.class).to(ExplorerNodeServiceImpl.class);
        bind(ExplorerService.class).to(ExplorerServiceImpl.class);
        bind(ExplorerEventLog.class).to(ExplorerEventLogImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.ExplorerServiceCopyHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.ExplorerServiceCreateHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.ExplorerServiceDeleteHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.ExplorerServiceInfoHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.ExplorerServiceMoveHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.ExplorerServiceRenameHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.FetchDocRefsHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.FetchDocumentTypesHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.FetchExplorerNodeHandler.class);
        taskHandlerBinder.addBinding().to(stroom.explorer.FetchExplorerPermissionsHandler.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.explorer.FolderExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.explorer.SystemExplorerActionHandler.class);
    }
}