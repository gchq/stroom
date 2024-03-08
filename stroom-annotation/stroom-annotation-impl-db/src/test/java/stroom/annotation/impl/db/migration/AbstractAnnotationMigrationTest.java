package stroom.annotation.impl.db.migration;

import stroom.annotation.impl.AnnotationConfig.AnnotationDBConfig;
import stroom.annotation.impl.db.AnnotationDbConnProvider;
import stroom.annotation.impl.db.AnnotationDbModule;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;

import java.util.List;
import java.util.Optional;

public abstract class AbstractAnnotationMigrationTest
        extends AbstractSingleFlywayMigrationTest<AnnotationDBConfig, AnnotationDbConnProvider> {

    @Override
    protected AbstractFlyWayDbModule<AnnotationDBConfig, AnnotationDbConnProvider> getDatasourceModule() {
        return new AnnotationDbModule() {
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
    protected Class<AnnotationDbConnProvider> getConnectionProviderType() {
        return AnnotationDbConnProvider.class;
    }
}
