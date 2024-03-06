package stroom.data.store.impl.fs.db.migration;

import stroom.data.store.impl.fs.DataStoreServiceConfig.DataStoreServiceDbConfig;
import stroom.data.store.impl.fs.db.FsDataStoreDbConnProvider;
import stroom.data.store.impl.fs.db.FsDataStoreDbModule;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractDataStoreMigrationTest
        extends AbstractSingleFlywayMigrationTest<DataStoreServiceDbConfig, FsDataStoreDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<DataStoreServiceDbConfig, FsDataStoreDbConnProvider> getDatasourceModule() {

        return new FsDataStoreDbModule() {

            @Override
            protected List<String> getFlyWayLocations() {
                return mergeLocations(super.getFlyWayLocations(), getTestDataMigrationLocation());
            }

            // Override this, so we target a specific version and don't run all migrations
            @Override
            protected Optional<MigrationVersion> getMigrationTarget() {
                return Optional.ofNullable(getTargetVersion());
            }
        };
    }

    @Override
    protected Class<FsDataStoreDbConnProvider> getConnectionProviderType() {
        return FsDataStoreDbConnProvider.class;
    }
}
