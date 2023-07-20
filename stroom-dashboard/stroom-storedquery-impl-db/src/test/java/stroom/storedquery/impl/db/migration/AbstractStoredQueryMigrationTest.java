package stroom.storedquery.impl.db.migration;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.storedquery.impl.StoredQueryConfig.StoredQueryDbConfig;
import stroom.storedquery.impl.db.StoredQueryDbConnProvider;
import stroom.storedquery.impl.db.StoredQueryDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractStoredQueryMigrationTest
        extends AbstractSingleFlywayMigrationTest<StoredQueryDbConfig, StoredQueryDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<StoredQueryDbConfig, StoredQueryDbConnProvider> getDatasourceModule() {
        return new StoredQueryDbModule() {
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
    protected Class<StoredQueryDbConnProvider> getConnectionProviderType() {
        return StoredQueryDbConnProvider.class;
    }
}
