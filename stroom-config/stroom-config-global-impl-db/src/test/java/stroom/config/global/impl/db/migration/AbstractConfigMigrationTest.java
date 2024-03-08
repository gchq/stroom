package stroom.config.global.impl.db.migration;

import stroom.config.app.PropertyServiceConfig.PropertyServiceDbConfig;
import stroom.config.global.impl.db.GlobalConfigDbConnProvider;
import stroom.config.global.impl.db.GlobalConfigDbModule;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractConfigMigrationTest
        extends AbstractSingleFlywayMigrationTest<PropertyServiceDbConfig, GlobalConfigDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<PropertyServiceDbConfig, GlobalConfigDbConnProvider> getDatasourceModule() {
        return new GlobalConfigDbModule() {
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
    protected Class<GlobalConfigDbConnProvider> getConnectionProviderType() {
        return GlobalConfigDbConnProvider.class;
    }
}
