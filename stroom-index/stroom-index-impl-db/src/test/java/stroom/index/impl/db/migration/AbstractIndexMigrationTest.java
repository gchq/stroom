package stroom.index.impl.db.migration;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.index.impl.IndexConfig.IndexDbConfig;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.impl.db.IndexDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractIndexMigrationTest
        extends AbstractSingleFlywayMigrationTest<IndexDbConfig, IndexDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<IndexDbConfig, IndexDbConnProvider> getDatasourceModule() {

        return new IndexDbModule() {

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
    protected Class<IndexDbConnProvider> getConnectionProviderType() {
        return IndexDbConnProvider.class;
    }
}
