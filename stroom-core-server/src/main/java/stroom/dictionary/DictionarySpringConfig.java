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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.Persistence;
import stroom.docstore.Store;
import stroom.explorer.ExplorerActionHandlers;
import stroom.importexport.ImportExportActionHandlers;
import stroom.logging.DocumentEventLog;
import stroom.security.SecurityContext;
import stroom.servlet.SessionResourceStore;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.inject.Singleton;

@Configuration
public class DictionarySpringConfig {
    @Inject
    public DictionarySpringConfig(final ExplorerActionHandlers explorerActionHandlers,
                                  final ImportExportActionHandlers importExportActionHandlers,
                                  final DictionaryStore dictionaryStore) {
        explorerActionHandlers.add(9, DictionaryDoc.ENTITY_TYPE, DictionaryDoc.ENTITY_TYPE, dictionaryStore);
        importExportActionHandlers.add(DictionaryDoc.ENTITY_TYPE, dictionaryStore);
    }

    @Bean
    public DictionaryResource dictionaryResource(final DictionaryStore dictionaryStore) {
        return new DictionaryResource(dictionaryStore);
    }

    @Bean
    @Singleton
    public DictionaryStore dictionaryStore(final Store<DictionaryDoc> store, final SecurityContext securityContext, final Persistence persistence) {
        return new DictionaryStoreImpl(store, securityContext, persistence);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public DownloadDictionaryHandler downloadDictionaryHandler(final SessionResourceStore sessionResourceStore,
                                                               final DocumentEventLog documentEventLog,
                                                               final DictionaryStore dictionaryStore) {
        return new DownloadDictionaryHandler(sessionResourceStore, documentEventLog, dictionaryStore);
    }
}