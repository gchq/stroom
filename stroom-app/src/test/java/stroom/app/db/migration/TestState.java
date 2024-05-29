package stroom.app.db.migration;

import org.flywaydb.core.api.MigrationVersion;

public record TestState(MigrationVersion targetVersion,
                        MigrationVersion testDataVersion,
                        Class<? extends AbstractCrossModuleMigrationTestData> testDataClass,
                        Class<? extends AbstractCrossModuleMigrationTest> testClass,
                        Class<? extends AbstractCrossModuleJavaDbMigration> targetClass) {

}
