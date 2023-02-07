package stroom.test.common.util.db;

import stroom.config.common.AbstractDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceFactory;
import stroom.db.util.JooqUtil;
import stroom.util.ConsoleColour;
import stroom.util.NullSafe;
import stroom.util.db.ForceLegacyMigration;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.internal.util.ExceptionUtils;
import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

import static org.assertj.core.api.Fail.fail;

/**
 * <p>
 * Superclass for testing a single migration script.
 * It works be writing the result of {@link AbstractSingleFlywayMigrationTest#getTestDataScript()}
 * to a file with a migration version number just before the migration under test.
 * It then runs all migrations up to and including the one under test. The inserted migration
 * is used to populate tables with data prior to the migration under test. The migration will run
 * as part of the test setup, so the test can do any assertions on the post-migration state.
 * </p>
 * <p>
 * Concrete classes MUST be named in the form {@code TestVnn_nn_nn_nnn}, i.e. to match
 * the version part of the migration being tested. E.g. if the migration script under test is
 * called {@code V07_00_21_005__processor_task_status.sql}, then the class to test it must be
 * called {@code TestV07_00_21_005}.
 * </p>
 * <p>
 * Also, migration scripts should be numbered such that the migration number (the {@code nnn} bit)
 * is a multiple of 5.  This leaves a gap for a test data migration script to be added in before it.
 * Each test class should test one migration script and can only use one set of test data. Therefore,
 * multiple tests on a single migration script will require multiple test classes.
 * </p>
 * <p>
 * It is recommended to create an abstract sub-class of this for each module.
 * </p>
 * <p>
 * IMPORTANT, you must not use JOOQ generated code to make assertions about the DB state
 * as the generated code will diverge from the DB state under test as more migrations are
 * added after it, so you will need to use hand crafted sql. This is fine as you are testing
 * a fixed DB state.
 * </p>
 */
public abstract class AbstractSingleFlywayMigrationTest<
        T_CONFIG extends AbstractDbConfig, T_CONN_PROV extends DataSource> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractSingleFlywayMigrationTest.class);

    protected Injector injector;

    @Inject
    protected DataSourceFactory dataSourceFactory;
    @Inject
    private Provider<T_CONFIG> configProvider;

    protected T_CONN_PROV dataSource;

    private Path testDataDir;
    private MigrationVersion targetVersion = null;
    private MigrationVersion testDataVersion = null;

    /**
     * @return A string containing the SQL script to run prior to the migration under test.
     * This can contain multiple statements, stored procedures, SET calls, etc. Same as a SQL
     * migration script. It does not use bind variables.
     */
    protected abstract String getTestDataScript();

    protected abstract AbstractFlyWayDbModule<T_CONFIG, T_CONN_PROV> getDatasourceModule();

    protected abstract Class<T_CONN_PROV> getConnectionProviderType();

    @BeforeEach
    void beforeEach(@TempDir Path tempDir) {
        LOGGER.info("Running all migrations up to and including {}, using test data {}",
                getTargetVersion(), getTestDataVersion());
        this.testDataDir = tempDir;

        setupTestDataScript(tempDir);

        try {
            injector = Guice.createInjector(
                    new UniqueDbTestModule(),
                    getDatasourceModule(),
                    new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(ForceLegacyMigration.class)
                                    .toInstance(new ForceLegacyMigration() {
                                    });
                        }
                    }
            );
            injector.injectMembers(this);
        } catch (Exception e) {
            if (ExceptionUtils.getRootCause(e) instanceof SQLException) {
                throw new RuntimeException("There is an error in the migrations or in the test data script", e);
            } else {
                throw e;
            }
        }

        // It is not bound to DataSource so have to get it this way
        dataSource = injector.getInstance(getConnectionProviderType());
    }

    @AfterEach
    void teardown() {
        // DB is not shared with other tests so bin it
        ((UniqueTestDataSourceFactory) dataSourceFactory).drop();

        // Remove the test data migration script
        NullSafe.consume(testDataDir, FileUtil::deleteDir);
    }

    protected List<String> getLocations() {
        return Stream.of(testDataDir)
                .filter(Objects::nonNull)
                .peek(path -> {
                    if (!Files.isDirectory(path)) {
                        throw new RuntimeException(LogUtil.message("Can't find directory {}",
                                path.normalize().toAbsolutePath().toString()));
                    }
                })
                .map(path -> path.normalize().toAbsolutePath().toString())
                .map(str -> "filesystem:" + str)
                .collect(Collectors.toList());
    }

    public T_CONN_PROV getDataSource() {
        return dataSource;
    }

    private void setupTestDataScript(final Path tempDir) {
        final String testDataScriptContent = getTestDataScript();

        LOGGER.debug("Test data script content\n{}", ConsoleColour.yellow(testDataScriptContent));

        if (NullSafe.isEmptyString(testDataScriptContent)) {
            fail("No test data script content provided");
        } else {
            final MigrationVersion testDataVersion = getTestDataVersion();
            final MigrationVersion targetVersion = getTargetVersion();
            final String filename = "V"
                    + testDataVersion.getVersion()
                    + "__test_data_for_"
                    + targetVersion.getVersion()
                    + ".sql";
            final Path testDataFile = tempDir.resolve(filename);

            LOGGER.debug("Writing test data script to temporary file {}",
                    testDataFile.normalize().toAbsolutePath());
            try {
                Files.writeString(testDataFile, testDataScriptContent);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void setupTargetVersion() {
        final String versionUnderTest = this.getClass()
                .getSimpleName()
                .replaceAll("^TestV", "")
                .replaceAll("__.*$", "");

        targetVersion = MigrationVersion.fromVersion(versionUnderTest);
        LOGGER.info("Using target version number {}", targetVersion);
    }

    protected MigrationVersion getTargetVersion() {
        if (targetVersion == null) {
            setupTargetVersion();
        }
        return targetVersion;
    }

    private void setupTestDataVersion() {
        final String targetVersionStr = getTargetVersion().getVersion();
        final String[] parts = targetVersionStr.split("\\.");
        final int lastPart = Integer.parseInt(parts[parts.length - 1]);

        if (lastPart == 1) {
            throw new RuntimeException(LogUtil.message("Can't work with a part number of 1"));
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
        LOGGER.info("Using test data version number {}", testDataVersion);
    }

    private MigrationVersion getTestDataVersion() {
        if (testDataVersion == null) {
            setupTestDataVersion();
        }
        return testDataVersion;
    }

    protected int getTableCount(final String tableName) {
        return getSingleValue(
                LogUtil.message("select count(*) from {}", tableName),
                int.class);
    }

    protected <R> R getSingleValue(final String sql, final Class<R> type) {
        return JooqUtil.contextResult(dataSource, context -> context
                .fetch(sql)
                .get(0)
                .getValue(0, type));
    }

    protected <R> List<R> getRows(final String sql,
                                  final Function<Record, R> rowMapper) {
        return JooqUtil.contextResult(dataSource, context -> context
                .fetch(sql)
                .map(rowMapper::apply));
    }

    /**
     * Add single quotes around a value.
     *
     * @param value
     * @return
     */
    protected String quote(final String value) {
        if (value == null || value.equals("null")) {
            return "null";
        } else if (value.startsWith("'") && value.endsWith("'")) {
            return value;
        } else {
            // Quote and escape any
            return "'" + value.replace("'", "''") + "'";
        }
    }
}
