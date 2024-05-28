package stroom.app.db.migration;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;

public abstract class AbstractCrossModuleMigrationTestData implements JavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            AbstractCrossModuleMigrationTestData.class);

    private final MigrationVersion targetVersion;
    private final MigrationVersion testDataVersion;

    protected AbstractCrossModuleMigrationTestData(final TestState testState) {
        targetVersion = testState.targetVersion();
        testDataVersion = testState.testDataVersion();
    }

    /**
     * This method essentially becomes a FlyWay migration that is run just prior to
     * the migration under test.
     * This method should set up any test data required to test that the migration
     * works as expected.
     */
    abstract void setupTestData() throws Exception;

    @Override
    public void migrate(final Context context) throws Exception {
        LOGGER.info("Setting up test data for migration " + targetVersion.getVersion());
        setupTestData();
        LOGGER.info("Completed test data setup");
    }

    @Override
    public MigrationVersion getVersion() {
        return testDataVersion;
    }

    @Override
    public String getDescription() {
        return "Test data (" + testDataVersion.getVersion() + ") for migration " + targetVersion.getVersion();
    }

    @Override
    public Integer getChecksum() {
        return null;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }
}
