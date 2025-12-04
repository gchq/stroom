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

package stroom.cluster.lock.impl.db;

import stroom.cluster.lock.impl.db.ClusterLockConfig.ClusterLockDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;

import java.util.List;
import javax.sql.DataSource;

public class ClusterLockDbModule extends AbstractFlyWayDbModule<ClusterLockDbConfig, ClusterLockDbConnProvider> {

    private static final String MODULE = "stroom-cluster-lock";
    private static final String FLYWAY_LOCATIONS = "stroom/cluster/lock/impl/db/migration";
    private static final String FLYWAY_TABLE = "cluster_lock_schema_history";

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
    protected boolean createUniquePool() {
        // We need the cluster lock connection pool to be unique as cluster lock connections are held open while other
        // DB operations are performed and if the pool were not unique then we would run the risk of deadlocks.
        return true;
    }

    @Override
    protected Class<ClusterLockDbConnProvider> getConnectionProviderType() {
        return ClusterLockDbConnProvider.class;
    }

    @Override
    protected ClusterLockDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements ClusterLockDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }

}
