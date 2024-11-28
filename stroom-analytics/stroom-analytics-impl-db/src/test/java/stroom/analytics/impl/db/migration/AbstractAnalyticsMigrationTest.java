package stroom.analytics.impl.db.migration;

import stroom.analytics.impl.AnalyticsConfig.AnalyticsDbConfig;
import stroom.analytics.impl.db.AnalyticsDbConnProvider;
import stroom.analytics.impl.db.AnalyticsDbModule;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractAnalyticsMigrationTest
        extends AbstractSingleFlywayMigrationTest<AnalyticsDbConfig, AnalyticsDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<AnalyticsDbConfig, AnalyticsDbConnProvider> getDatasourceModule() {

        return new AnalyticsDbModule() {

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
    protected Class<AnalyticsDbConnProvider> getConnectionProviderType() {
        return AnalyticsDbConnProvider.class;
    }
}
