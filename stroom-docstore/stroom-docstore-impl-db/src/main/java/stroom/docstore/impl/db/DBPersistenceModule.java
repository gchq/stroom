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

package stroom.docstore.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.docstore.impl.Persistence;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import javax.sql.DataSource;

public class DBPersistenceModule extends AbstractFlyWayDbModule<DocStoreConfig, DocStoreDbConnProvider> {
    private static final String MODULE = "stroom-docstore";
    private static final String FLYWAY_LOCATIONS = "stroom/docstore/impl/db/migration";
    private static final String FLYWAY_TABLE = "docstore_history";

    @Override
    protected void configure() {
        super.configure();

        bind(Persistence.class).to(DBPersistence.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(DBPersistence.class);
    }

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    protected Class<DocStoreDbConnProvider> getConnectionProviderType() {
        return DocStoreDbConnProvider.class;
    }

    @Override
    protected DocStoreDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements DocStoreDbConnProvider {
        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}