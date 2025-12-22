/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.identity.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.security.identity.config.IdentityConfig.IdentityDbConfig;

import java.util.List;
import javax.sql.DataSource;

public class IdentityDbModule extends AbstractFlyWayDbModule<IdentityDbConfig, IdentityDbConnProvider> {

    private static final String MODULE = "stroom-security-identity";
    private static final String FLYWAY_LOCATIONS = "stroom/security/identity/db/migration";
    private static final String FLYWAY_TABLE = "identity_schema_history";

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected List<String> getFlyWayLocations() {
        return List.of(FLYWAY_LOCATIONS);
    }

    @Override
    protected Class<IdentityDbConnProvider> getConnectionProviderType() {
        return IdentityDbConnProvider.class;
    }

    @Override
    protected IdentityDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    public static class DataSourceImpl extends DataSourceProxy implements IdentityDbConnProvider {

        public DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
