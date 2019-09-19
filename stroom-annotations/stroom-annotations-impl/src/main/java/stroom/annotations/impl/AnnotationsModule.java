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

package stroom.annotations.impl;

import com.google.inject.AbstractModule;
import stroom.search.extraction.AnnotationsDecoratorFactory;

public class AnnotationsModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AnnotationsDecoratorFactory.class).to(AnnotationsReceiverDecoratorFactory.class);
//        bind(ExplorerNodeService.class).to(ExplorerNodeServiceImpl.class);
//        bind(ExplorerSession.class).to(ExplorerSessionImpl.class);
//        bind(ExplorerService.class).to(ExplorerServiceImpl.class);
//        bind(ExplorerEventLog.class).to(ExplorerEventLogImpl.class);
//        bind(CollectionService.class).to(ExplorerServiceImpl.class);
//
//        TaskHandlerBinder.create(binder())
//                .bind(ExplorerServiceCopyAction.class, ExplorerServiceCopyHandler.class)
//                .bind(ExplorerServiceCreateAction.class, ExplorerServiceCreateHandler.class)
//                .bind(ExplorerServiceDeleteAction.class, ExplorerServiceDeleteHandler.class)
//                .bind(ExplorerServiceInfoAction.class, ExplorerServiceInfoHandler.class)
//                .bind(ExplorerServiceMoveAction.class, ExplorerServiceMoveHandler.class)
//                .bind(ExplorerServiceRenameAction.class, ExplorerServiceRenameHandler.class)
//                .bind(FetchDocRefsAction.class, FetchDocRefsHandler.class)
//                .bind(FetchDocumentTypesAction.class, FetchDocumentTypesHandler.class)
//                .bind(FetchExplorerNodeAction.class, FetchExplorerNodeHandler.class)
//                .bind(FetchExplorerPermissionsAction.class, FetchExplorerPermissionsHandler.class);
//
//        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
//                .addBinding(FolderExplorerActionHandler.class)
//                .addBinding(SystemExplorerActionHandler.class);
//
//        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
//                .addBinding(ExplorerResource.class);
    }
}