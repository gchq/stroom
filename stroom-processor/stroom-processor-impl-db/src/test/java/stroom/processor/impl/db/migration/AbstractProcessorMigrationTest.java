package stroom.processor.impl.db.migration;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.processor.impl.ProcessorConfig.ProcessorDbConfig;
import stroom.processor.impl.db.ProcessorDbConnProvider;
import stroom.processor.impl.db.ProcessorDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractProcessorMigrationTest
        extends AbstractSingleFlywayMigrationTest<ProcessorDbConfig, ProcessorDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<ProcessorDbConfig, ProcessorDbConnProvider> getDatasourceModule() {

        return new ProcessorDbModule() {

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
    protected Class<ProcessorDbConnProvider> getConnectionProviderType() {
        return ProcessorDbConnProvider.class;
    }
}
