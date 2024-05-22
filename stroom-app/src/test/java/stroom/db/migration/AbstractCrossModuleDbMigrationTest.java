package stroom.db.migration;

import stroom.app.db.migration.AbstractCrossModuleJavaDbMigration;
import stroom.app.db.migration.CrossModuleDbMigrationsModule;
import stroom.app.guice.DbConnectionsModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import jakarta.inject.Inject;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.sql.DataSource;

public abstract class AbstractCrossModuleDbMigrationTest extends AbstractCrossModuleJavaDbMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractCrossModuleDbMigrationTest.class);
    private static final Pattern TEST_PREFIX_PATTERN = Pattern.compile("^Test");

    private MigrationVersion targetVersion = null;
    private MigrationVersion testDataVersion = null;

    @Inject
    private Injector injector;

    @BeforeEach
    void beforeEach() {
        LOGGER.info("Running all migrations up to and including {}, using test data {}",
                getTargetVersion(), getTestDataVersion());
        injector = Guice.createInjector(
                new DbTestModule(),
                getDbConnectionsModule());

        injector.injectMembers(this);

        getTargetVersion();
        getTestDataVersion();
    }

    @AfterEach
    void afterEach() {
        DbTestUtil.dropThreadTestDatabase(true);
    }

    /**
     * Subclasses can call this to get hold of a datasource to set up test data
     */
    protected DataSource getDbConnectionProvider(final Class<? extends DataSource> clazz) {
        return injector.getInstance(clazz);
    }


    /**
     * This is called just before the migration under test is run.
     * This method should set up any test data required to test the migration.
     */
    abstract void setupTestData() throws Exception;

    @Override
    public void migrate(final Context context) throws Exception {
        LOGGER.info("Setting up test data for migration " + getTargetVersion().getVersion());
        setupTestData();
        LOGGER.info("Completed test data setup");
    }

    private Module getDbConnectionsModule() {
        return new DbConnectionsModule() {

            @Override
            protected CrossModuleDbMigrationsModule getCrossModuleDbMigrationsModule() {

                return new CrossModuleDbMigrationsModule() {

                    @Override
                    protected Optional<MigrationVersion> getMigrationTarget() {
                        // This is the version of the mig under test
                        // This ensures we only run migrations up to the version under test
                        return Optional.ofNullable(getTargetVersion());
                    }

                    @Override
                    protected List<Class<? extends AbstractCrossModuleJavaDbMigration>> getAdditionalMigrations() {
                        // Add this test class as an extra migration class
                        return List.of(AbstractCrossModuleDbMigrationTest.this.getClass());
                    }
                };
            }
        };
    }

    @Override
    public MigrationVersion getVersion() {
        return getTestDataVersion();
    }

    @Override
    public String getDescription() {
        return "Test data for migration " + getTargetVersion().getVersion();
    }

    /**
     * This is version of the migration under test
     */
    protected MigrationVersion getTargetVersion() {
        //
        if (targetVersion == null) {
            setupTargetVersion();
        }
        return targetVersion;
    }

    /**
     * This is version of this test class which itself is a migration that is slotted
     * in just before the target version.
     */
    private MigrationVersion getTestDataVersion() {
        // This is version of the migration under test
        if (testDataVersion == null) {
            setupTestDataVersion();
        }
        return testDataVersion;
    }

    private void setupTargetVersion() {
        final String versionUnderTest = this.getClass()
                .getSimpleName()
                .replaceAll("^TestV", "")
                .replaceAll("__.*$", "");

        targetVersion = MigrationVersion.fromVersion(versionUnderTest);
        LOGGER.debug("Using target version number {}", targetVersion);
    }

    private void setupTestDataVersion() {
        final String targetVersionStr = getTargetVersion().getVersion();
        final String[] parts = targetVersionStr.split("\\.");
        final int lastPart = Integer.parseInt(parts[parts.length - 1]);

        if (lastPart == 1) {
            throw new RuntimeException(LogUtil.message(
                    "Can't work with a part number of 1. targetVersionStr: '{}'. " +
                            "Make the last three digits of the script name a multiple of 5",
                    targetVersionStr));
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            sb.append(parts[i])
                    .append(".");
        }
        final String newLastPart = Strings.padStart(String.valueOf(lastPart - 1), 3, '0');
        sb.append(newLastPart)
                .append(".1");

        testDataVersion = MigrationVersion.fromVersion(sb.toString());
        LOGGER.debug("Using test data version number {}", testDataVersion);
    }
}
