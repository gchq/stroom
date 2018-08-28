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
import com.google.inject.Provides;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportActionHandlerFactory;

public class MockExplorerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ExplorerNodeService.class).to(MockExplorerNodeService.class);
//        bind(ExplorerTreeDao.class).to(ExplorerTreeDaoImpl.class);
//        bind(DbSession.class).to(DbSessionJpaImpl.class);
        bind(ExplorerService.class).to(MockExplorerService.class);
//        bind(ExplorerEventLog.class).to(ExplorerEventLogImpl.class);
//
//        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
//        taskHandlerBinder.addBinding().to(ExplorerServiceCopyHandler.class);
//        taskHandlerBinder.addBinding().to(ExplorerServiceCreateHandler.class);
//        taskHandlerBinder.addBinding().to(ExplorerServiceDeleteHandler.class);
//        taskHandlerBinder.addBinding().to(ExplorerServiceInfoHandler.class);
//        taskHandlerBinder.addBinding().to(ExplorerServiceMoveHandler.class);
//        taskHandlerBinder.addBinding().to(ExplorerServiceRenameHandler.class);
//        taskHandlerBinder.addBinding().to(FetchDocRefsHandler.class);
//        taskHandlerBinder.addBinding().to(FetchDocumentTypesHandler.class);
//        taskHandlerBinder.addBinding().to(FetchExplorerNodeHandler.class);
//        taskHandlerBinder.addBinding().to(FetchExplorerPermissionsHandler.class);
//
//        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
//        explorerActionHandlerBinder.addBinding().to(FolderExplorerActionHandler.class);
//        explorerActionHandlerBinder.addBinding().to(SystemExplorerActionHandler.class);
    }
    //    @PersistenceContext
//    private EntityManager entityManager;
//
//    @Bean
//    public DbSession dbSessionJpa() {
//        return new DbSessionJpaImpl(entityManager);
//    }
//
//    @Bean
//    public ExplorerActionHandlers explorerActionHandlers(final StroomBeanStore beanStore) {
//        return new ExplorerActionHandlers(beanStore);
//    }
//
//    @Bean
//    public ExplorerEventLog explorerEventLog(final StroomEventLoggingService eventLoggingService) {
//        return new ExplorerEventLogImpl(eventLoggingService);
//    }
//
//    @Bean
//    public ExplorerNodeService explorerNodeService(final ExplorerTreeDao explorerTreeDao,
//                                                   final SecurityContext securityContext) {
//        return new ExplorerNodeServiceImpl(explorerTreeDao, securityContext);
//    }
//
//    @Bean
//    @Scope(StroomScope.PROTOTYPE)
//    public ExplorerService explorerService(final ExplorerNodeService explorerNodeService,
//                                           final ExplorerTreeModel explorerTreeModel,
//                                           final ExplorerActionHandlers explorerActionHandlers,
//                                           final SecurityContext securityContext,
//                                           final ExplorerEventLog explorerEventLog) {
//        return new ExplorerServiceImpl(explorerNodeService, explorerTreeModel, explorerActionHandlers, securityContext, explorerEventLog);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public ExplorerServiceCopyHandler explorerServiceCopyHandler(final ExplorerService explorerService) {
//        return new ExplorerServiceCopyHandler(explorerService);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public ExplorerServiceCreateHandler explorerServiceCreateHandler(final ExplorerService explorerService) {
//        return new ExplorerServiceCreateHandler(explorerService);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public ExplorerServiceDeleteHandler explorerServiceDeleteHandler(final ExplorerService explorerService) {
//        return new ExplorerServiceDeleteHandler(explorerService);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public ExplorerServiceInfoHandler explorerServiceInfoHandler(final ExplorerService explorerService) {
//        return new ExplorerServiceInfoHandler(explorerService);
//    }
//
//    @Scope(value = StroomScope.TASK)
//    public ExplorerServiceMoveHandler explorerServiceMoveHandler(final ExplorerService explorerService) {
//        return new ExplorerServiceMoveHandler(explorerService);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public ExplorerServiceRenameHandler explorerServiceRenameHandler(final ExplorerService explorerService) {
//        return new ExplorerServiceRenameHandler(explorerService);
//    }
//
//    @Bean
//    public ExplorerTreeDao explorerTreeDao(final DbSession session) {
//        return new ExplorerTreeDaoImpl(session);
//    }
//
//    @Bean
//    public ExplorerTreeModel explorerTreeModel(final ExplorerTreeDao explorerTreeDao, final ExplorerActionHandlers explorerActionHandlers) {
//        return new ExplorerTreeModel(explorerTreeDao, explorerActionHandlers);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public FetchDocRefsHandler fetchDocRefsHandler(final ExplorerNodeService explorerNodeService,
//                                                   final SecurityContext securityContext) {
//        return new FetchDocRefsHandler(explorerNodeService, securityContext);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public FetchDocumentTypesHandler fetchDocumentTypesHandler(final ExplorerService explorerService) {
//        return new FetchDocumentTypesHandler(explorerService);
//    }
//
//    @Bean
//    public FolderExplorerActionHandler folderExplorerActionHandler(final SecurityContext securityContext, final ExplorerTreeDao explorerTreeDao) {
//        return new FolderExplorerActionHandler(securityContext, explorerTreeDao);
//    }
//
//    @Bean
//    public SystemExplorerActionHandler systemExplorerActionHandler(final SecurityContext securityContext, final ExplorerTreeDao explorerTreeDao) {
//        return new SystemExplorerActionHandler(securityContext, explorerTreeDao);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public FetchExplorerNodeHandler fetchExplorerNodeHandler(final ExplorerService explorerService) {
//        return new FetchExplorerNodeHandler(explorerService);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public FetchExplorerPermissionsHandler fetchExplorerPermissionsHandler(final ExplorerService explorerService,
//                                                                           final SecurityContext securityContext) {
//        return new FetchExplorerPermissionsHandler(explorerService, securityContext);
//    }
//
//    @Provides
//    public ImportExportActionHandlerFactory importExportActionHandlerFactory() {
//        return type -> null;
//    }
}