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

package stroom.ruleset;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.datafeed.MetaMapFilterFactory;
import stroom.dictionary.DictionaryStore;
import stroom.docstore.Store;
import stroom.entity.FindService;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.ruleset.shared.RuleSet;
import stroom.xmlschema.shared.XMLSchema;

public class RulesetModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RuleSetService.class).to(RuleSetServiceImpl.class).in(Singleton.class);
        bind(MetaMapFilterFactory.class).to(MetaMapFilterFactoryImpl.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.ruleset.RuleSetServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.ruleset.RuleSetServiceImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(RuleSet.DOCUMENT_TYPE).to(stroom.ruleset.RuleSetServiceImpl.class);

//        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
//        findServiceBinder.addBinding().to(stroom.ruleset.RuleSetServiceImpl.class);
    }

//    @Bean
//    public MetaMapFilterFactory metaMapFilterFactory(final RuleSetService ruleSetService,
//                                                     final DictionaryStore dictionaryStore) {
//        return new MetaMapFilterFactoryImpl(ruleSetService, dictionaryStore);
//    }
//
//    @Bean
//    public RuleSetResource ruleSetResource(final RuleSetService ruleSetService) {
//        return new RuleSetResource(ruleSetService);
//    }

//    @Bean
//    @Singleton
//    public RuleSetService ruleSetService(final Store<RuleSet> store) {
//        return new RuleSetServiceImpl(store);
//    }
}