/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.impl.db;

import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.AbstractModule;

import static org.mockito.Mockito.mock;

/**
 * Guice wiring for the real-database {@link UpdatableTemporalStoreDaoImpl} tests.
 *
 * <p>Installs both production halves — {@link SqlStoreDbModule} for the datasource and its Flyway
 * migration, and {@link SqlStoreDaoModule} for the DAO binding — on top of {@link DbTestModule},
 * which swaps in a uniquely-named test database so parallel Gradle forks never collide. The app
 * installs the two in different injectors (see {@link SqlStoreDbModule}); here they go together.</p>
 *
 * <p>The DAO builds an {@code ExpressionMapper} whose {@code TermHandlerFactory}
 * requires {@link WordListProvider}, {@link CollectionService} and
 * {@link DocFinder}. These are bound here as Mockito mocks rather than via
 * shared {@code Mock*Module}s: the test queries only use simple map-name and
 * effective-time terms, so the collaborators are never actually invoked, and
 * binding them directly keeps this test insulated from churn in the mock
 * modules. {@code SqlStoreDbConfig} is just-in-time constructed from its no-arg
 * constructor.</p>
 */
public class TestModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        // Production modules: the Flyway-migrated SqlStoreDbConnProvider, and the
        // UpdatableTemporalStoreDao -> ...Impl binding.
        install(new SqlStoreDbModule());
        install(new SqlStoreDaoModule());

        // Test datasource: unique per-fork/thread DB, cleared after the run.
        install(new DbTestModule());

        // Collaborators required to construct the ExpressionMapper term handlers.
        bind(WordListProvider.class).toInstance(mock(WordListProvider.class));
        bind(CollectionService.class).toInstance(mock(CollectionService.class));
        bind(DocFinder.class).toInstance(mock(DocFinder.class));
    }
}
