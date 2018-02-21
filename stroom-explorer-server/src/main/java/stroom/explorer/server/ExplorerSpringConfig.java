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

package stroom.explorer.server;

import fri.util.database.jpa.commons.DbSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.logging.StroomEventLoggingService;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.persistence.EntityManager;

@Configuration
public class ExplorerSpringConfig {
    @Inject
    public ExplorerSpringConfig(final ExplorerActionHandlers explorerActionHandlers,
                                final SystemExplorerActionHandler systemExplorerActionHandler,
                                final FolderExplorerActionHandler folderExplorerActionHandler) {
        explorerActionHandlers.add(0, SystemExplorerActionHandler.SYSTEM, SystemExplorerActionHandler.SYSTEM, systemExplorerActionHandler);
        explorerActionHandlers.add(1, FolderExplorerActionHandler.FOLDER, FolderExplorerActionHandler.FOLDER, folderExplorerActionHandler);
    }

    @Bean
    public DbSession dbSessionJpa(final EntityManager entityManager) {
        return new DbSessionJpaImpl(entityManager);
    }

    @Bean
    public ExplorerActionHandlers explorerActionHandlers() {
        return new ExplorerActionHandlersImpl();
    }

    @Bean
    public ExplorerEventLog explorerEventLog(final StroomEventLoggingService eventLoggingService) {
        return new ExplorerEventLogImpl(eventLoggingService);
    }

    @Bean
    public ExplorerNodeService explorerNodeService(final ExplorerTreeDao explorerTreeDao,
                                                   final SecurityContext securityContext) {
        return new ExplorerNodeServiceImpl(explorerTreeDao, securityContext);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public ExplorerService explorerService(final ExplorerNodeService explorerNodeService,
                                           final ExplorerTreeModel explorerTreeModel,
                                           final ExplorerActionHandlersImpl explorerActionHandlers,
                                           final SecurityContext securityContext,
                                           final ExplorerEventLog explorerEventLog) {
        return new ExplorerServiceImpl(explorerNodeService, explorerTreeModel, explorerActionHandlers, securityContext, explorerEventLog);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ExplorerServiceCopyHandler explorerServiceCopyHandler(final ExplorerService explorerService) {
        return new ExplorerServiceCopyHandler(explorerService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ExplorerServiceCreateHandler explorerServiceCreateHandler(final ExplorerService explorerService) {
        return new ExplorerServiceCreateHandler(explorerService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ExplorerServiceDeleteHandler explorerServiceDeleteHandler(final ExplorerService explorerService) {
        return new ExplorerServiceDeleteHandler(explorerService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ExplorerServiceInfoHandler explorerServiceInfoHandler(final ExplorerService explorerService) {
        return new ExplorerServiceInfoHandler(explorerService);
    }

    @Scope(value = StroomScope.TASK)
    public ExplorerServiceMoveHandler explorerServiceMoveHandler(final ExplorerService explorerService) {
        return new ExplorerServiceMoveHandler(explorerService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ExplorerServiceRenameHandler explorerServiceRenameHandler(final ExplorerService explorerService) {
        return new ExplorerServiceRenameHandler(explorerService);
    }

    @Bean
    public ExplorerTreeDao explorerTreeDao(final DbSession session) {
        return new ExplorerTreeDaoImpl(session);
    }

    @Bean
    public ExplorerTreeModel explorerTreeModel(final ExplorerTreeDao explorerTreeDao, final ExplorerActionHandlersImpl explorerActionHandlers) {
        return new ExplorerTreeModel(explorerTreeDao, explorerActionHandlers);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchDocRefsHandler fetchDocRefsHandler(final ExplorerNodeService explorerNodeService,
                                                   final SecurityContext securityContext) {
        return new FetchDocRefsHandler(explorerNodeService, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchDocumentTypesHandler fetchDocumentTypesHandler(final ExplorerService explorerService) {
        return new FetchDocumentTypesHandler(explorerService);
    }

    @Bean
    public FolderExplorerActionHandler folderExplorerActionHandler(final SecurityContext securityContext, final ExplorerTreeDao explorerTreeDao) {
        return new FolderExplorerActionHandler(securityContext, explorerTreeDao);
    }

    @Bean
    public SystemExplorerActionHandler systemExplorerActionHandler(final SecurityContext securityContext, final ExplorerTreeDao explorerTreeDao) {
        return new SystemExplorerActionHandler(securityContext, explorerTreeDao);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchExplorerNodeHandler fetchExplorerNodeHandler(final ExplorerService explorerService) {
        return new FetchExplorerNodeHandler(explorerService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchExplorerPermissionsHandler fetchExplorerPermissionsHandler(final ExplorerService explorerService,
                                                                           final SecurityContext securityContext) {
        return new FetchExplorerPermissionsHandler(explorerService, securityContext);
    }
}