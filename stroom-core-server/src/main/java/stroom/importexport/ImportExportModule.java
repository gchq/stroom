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

package stroom.importexport;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.task.TaskHandler;

public class ImportExportModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ImportExportService.class).to(ImportExportServiceImpl.class);
        bind(ImportExportSerializer.class).to(ImportExportSerializerImpl.class);
        bind(ImportExportDocumentEventLog.class).to(ImportExportDocumentEventLogImpl.class);
        bind(DependencyService.class).to(DependencyServiceImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.importexport.ExportConfigHandler.class);
        taskHandlerBinder.addBinding().to(stroom.importexport.FetchDependenciesHandler.class);
        taskHandlerBinder.addBinding().to(stroom.importexport.ImportConfigConfirmationHandler.class);
        taskHandlerBinder.addBinding().to(stroom.importexport.ImportConfigHandler.class);
    }
    //    @Bean
//    public ContentPackImport contentPackImport(final ImportExportService importExportService, final StroomPropertyService stroomPropertyService) {
//        return new ContentPackImport(importExportService, stroomPropertyService);
//    }
//
//    @Bean
//    public DependencyService dependencyService(final ImportExportActionHandlers importExportActionHandlers) {
//        return new DependencyServiceImpl(importExportActionHandlers);
//    }
//
//    @Bean
//    public ImportExportActionHandlers importExportActionHandlers(final StroomBeanStore beanStore) {
//        return new ImportExportActionHandlers(beanStore);
//    }
//
//    @Bean
//    public ImportExportDocumentEventLog importExportDocumentEventLog(final StroomEventLoggingService eventLoggingService) {
//        return new ImportExportDocumentEventLogImpl(eventLoggingService);
//    }
//
//    @Bean
//    public ImportExportHelper importExportHelper(final GenericEntityService genericEntityService) {
//        return new ImportExportHelper(genericEntityService);
//    }
//
//    @Bean
//    public ImportExportSerializer importExportSerializer(final ExplorerService explorerService,
//                                                         final ExplorerNodeService explorerNodeService,
//                                                         final ImportExportActionHandlers importExportActionHandlers,
//                                                         final SecurityContext securityContext,
//                                                         final ImportExportDocumentEventLog importExportEventLog) {
//        return new ImportExportSerializerImpl(explorerService, explorerNodeService, importExportActionHandlers, securityContext, importExportEventLog);
//    }
//
//    @Bean
//    public ImportExportService importExportService(final ImportExportSerializer importExportSerializer) {
//        return new ImportExportServiceImpl(importExportSerializer);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public ExportConfigHandler exportConfigHandler(final ImportExportService importExportService,
//                                                   final stroom.logging.ImportExportEventLog eventLog,
//                                                   final ResourceStore resourceStore) {
//        return new ExportConfigHandler(importExportService, eventLog, resourceStore);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.PROTOTYPE)
//    public FetchDependenciesHandler fetchDependenciesHandler(final DependencyService dependencyService) {
//        return new FetchDependenciesHandler(dependencyService);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public ImportConfigConfirmationHandler importConfigConfirmationHandler(final ImportExportService importExportService,
//                                                                           final ResourceStore resourceStore) {
//        return new ImportConfigConfirmationHandler(importExportService, resourceStore);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public ImportConfigHandler importConfigHandler(final ImportExportService importExportService,
//                                                   final stroom.logging.ImportExportEventLog eventLog,
//                                                   final ResourceStore resourceStore) {
//        return new ImportConfigHandler(importExportService, eventLog, resourceStore);
//    }
}