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

    private volatile AbstractFlyWayDbModule<ProcessorDbConfig, ProcessorDbConnProvider> module = null;

    @Override
    protected AbstractFlyWayDbModule<ProcessorDbConfig, ProcessorDbConnProvider> getDatasourceModule() {

        if (module == null) {
            module = new ProcessorDbModule() {

                @Override
                protected List<String> getFlyWayLocations() {
                    return getLocations();
                }

                // Override this, so we target a specific version and don't run all migrations
                @Override
                protected Optional<MigrationVersion> getMigrationTarget() {
                    return Optional.ofNullable(getTargetVersion());
                }
            };
        }
        return module;
    }

    @Override
    protected Class<ProcessorDbConnProvider> getConnectionProviderType() {
        return ProcessorDbConnProvider.class;
    }
}
