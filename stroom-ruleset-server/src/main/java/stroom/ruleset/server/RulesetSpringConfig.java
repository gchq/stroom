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

package stroom.ruleset.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.datafeed.server.MetaMapFilterFactory;
import stroom.dictionary.server.DictionaryStore;
import stroom.docstore.server.Store;
import stroom.explorer.server.ExplorerActionHandlers;
import stroom.importexport.server.ImportExportActionHandlers;
import stroom.ruleset.shared.RuleSet;

import javax.inject.Inject;
import javax.inject.Singleton;

@Configuration
public class RulesetSpringConfig {
    @Inject
    public RulesetSpringConfig(final ExplorerActionHandlers explorerActionHandlers,
                               final ImportExportActionHandlers importExportActionHandlers,
                               final RuleSetService ruleSetService) {
        explorerActionHandlers.add(100, RuleSet.DOCUMENT_TYPE, "Rule Set", ruleSetService);
        importExportActionHandlers.add(RuleSet.DOCUMENT_TYPE, ruleSetService);
    }

    @Bean
    public MetaMapFilterFactory metaMapFilterFactory(final RuleSetService ruleSetService,
                                                     final DictionaryStore dictionaryStore) {
        return new MetaMapFilterFactoryImpl(ruleSetService, dictionaryStore);
    }

    @Bean
    public RuleSetResource ruleSetResource(final RuleSetService ruleSetService) {
        return new RuleSetResource(ruleSetService);
    }

    @Bean
    @Singleton
    public RuleSetService ruleSetService(final Store<RuleSet> store) {
        return new RuleSetServiceImpl(store);
    }
}