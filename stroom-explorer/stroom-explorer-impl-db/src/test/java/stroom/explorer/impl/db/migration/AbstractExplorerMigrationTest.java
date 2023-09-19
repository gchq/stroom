package stroom.explorer.impl.db.migration;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.explorer.impl.ExplorerConfig.ExplorerDbConfig;
import stroom.explorer.impl.db.ExplorerDbConnProvider;
import stroom.explorer.impl.db.ExplorerDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractExplorerMigrationTest
        extends AbstractSingleFlywayMigrationTest<ExplorerDbConfig, ExplorerDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<ExplorerDbConfig, ExplorerDbConnProvider> getDatasourceModule() {

        return new ExplorerDbModule() {

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
    protected Class<ExplorerDbConnProvider> getConnectionProviderType() {
        return ExplorerDbConnProvider.class;
    }
}
