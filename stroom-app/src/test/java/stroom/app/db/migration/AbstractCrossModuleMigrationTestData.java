package stroom.app.db.migration;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;

/**
 * Superclass for classes that act as a FlyWay {@link JavaMigration} to set up test data
 * prior to a migration in order to test that migration.
 */
public abstract class AbstractCrossModuleMigrationTestData implements JavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            AbstractCrossModuleMigrationTestData.class);

    private final MigrationVersion targetVersion;
    private final MigrationVersion testDataVersion;
    private final TestState testState;

    protected AbstractCrossModuleMigrationTestData(final TestState testState) {
        this.testState = testState;
        this.targetVersion = testState.targetVersion();
        this.testDataVersion = testState.testDataVersion();
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
        LOGGER.info("Setting up test data for migration {} ({}) using test {} and test data {}",
                targetVersion.getVersion(),
                testState.targetClass().getName(),
                testState.testClass().getName(),
                testState.testDataClass().getName());
        setupTestData();
        LOGGER.info("Completed test data setup");
    }

    @Override
    public MigrationVersion getVersion() {
        return testDataVersion;
    }

    @Override
    public String getDescription() {
        return "Test data for migration " + targetVersion.getVersion() + " (" + testState.testClass().getName() + ")";
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
