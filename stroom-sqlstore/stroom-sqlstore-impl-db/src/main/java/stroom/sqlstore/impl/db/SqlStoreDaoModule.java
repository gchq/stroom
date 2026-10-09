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

import stroom.sqlstore.impl.UpdatableTemporalStoreDao;

import com.google.inject.AbstractModule;

/**
 * Binds the SQL temporal store DAO.
 *
 * <p>Separate from {@link SqlStoreDbModule} because the DAO depends on
 * {@code ExpressionMapperFactory}, which in turn needs {@code CollectionService},
 * {@code WordListProvider} and {@code DocFinder} — none of which exist in the bootstrap
 * injector. Install this in {@code CoreModule} and {@link SqlStoreDbModule} in
 * {@code DbConnectionsModule}; see that class for why the datasource half has to be the one
 * available at bootstrap. This mirrors how document-asset splits
 * {@code DocumentAssetDbModule} from {@code DocumentAssetDaoModule}.</p>
 */
public class SqlStoreDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(UpdatableTemporalStoreDao.class).to(UpdatableTemporalStoreDaoImpl.class);
    }
}
