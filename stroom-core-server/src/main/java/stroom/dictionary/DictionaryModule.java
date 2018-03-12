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

package stroom.dictionary;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.Persistence;
import stroom.docstore.Store;
import stroom.entity.FindService;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.logging.DocumentEventLog;
import stroom.resource.ResourceStore;
import stroom.security.SecurityContext;
import stroom.task.TaskHandler;
import stroom.util.spring.StroomScope;


public class DictionaryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DictionaryStore.class).to(DictionaryStoreImpl.class).in(Singleton.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.dictionary.DownloadDictionaryHandler.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.dictionary.DictionaryStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.dictionary.DictionaryStoreImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(DictionaryDoc.ENTITY_TYPE).to(stroom.dictionary.DictionaryStoreImpl.class);

//        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
//        findServiceBinder.addBinding().to(stroom.dictionary.DictionaryStoreImpl.class);
    }
//
//    @Bean
//    public DictionaryResource dictionaryResource(final DictionaryStore dictionaryStore) {
//        return new DictionaryResource(dictionaryStore);
//    }

//    @Bean
//    @Singleton
//    public DictionaryStore dictionaryStore(final Store<DictionaryDoc> store, final SecurityContext securityContext, final Persistence persistence) {
//        return new DictionaryStoreImpl(store, securityContext, persistence);
//    }
//
//    @Bean
//    @Scope(StroomScope.PROTOTYPE)
//    public DownloadDictionaryHandler downloadDictionaryHandler(final ResourceStore resourceStore,
//                                                               final DocumentEventLog documentEventLog,
//                                                               final DictionaryStore dictionaryStore) {
//        return new DownloadDictionaryHandler(resourceStore, documentEventLog, dictionaryStore);
//    }
}