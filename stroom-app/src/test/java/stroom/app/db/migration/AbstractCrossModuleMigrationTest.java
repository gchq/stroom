/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.app.db.migration;

import stroom.app.guice.DbConnectionsModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Superclass for all test classes that test cross module DB migrations (i.e. migrations the subclass
 * {@link AbstractCrossModuleJavaDbMigration}.
 * <p>
 * The test method's role is to assert the state of the database AFTER the test data has been set up and
 * the migration under test has been run. The {@link BeforeEach} and {@link AfterEach} methods will ensure
 * that each test method will be working with a freshly migrated database however each test will use the
 * same test data. If you need different test data then create a new test class.
 * </p>
 * <p>
 * Each subclass should have its own static inner class that subclasses {@link AbstractCrossModuleMigrationTestData}
 * and links to it using {@link AbstractCrossModuleMigrationTest#getTestDataClass()}.
 * </p>
 */
public abstract class AbstractCrossModuleMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractCrossModuleMigrationTest.class);

    private Injector injector;

    private final MigrationVersion targetVersion;
    private final MigrationVersion testDataVersion;

    public AbstractCrossModuleMigrationTest() {
        targetVersion = getTargetVersion();
        testDataVersion = getTestDataVersion();
    }

    /**
     * @return The class that will set up any test data prior to the migration that is being tested.
     * Said class will be instantiated by guice so constructor injection can be used to
     * pass any data sources it needs. Its constructor must inject {@link TestState} and pass to the
     * superclass.
     */
    abstract Class<? extends AbstractCrossModuleMigrationTestData> getTestDataClass();

    /**
     * @return The migration class that this test is testing.
     */
    abstract Class<? extends AbstractCrossModuleJavaDbMigration> getTargetClass();

    /**
     * Get an instance of a datasource for use when asserting database state after the migration has
     * been run.
     */
    protected <T extends DataSource> T getDatasource(final Class<T> clazz) {
        return injector.getInstance(clazz);
    }

    @BeforeEach
    void beforeEach() {
        // Ensure we work with an empty DB, so we migrate from a clean slate.
        // This thread may have run migrations for other tests.
        LOGGER.info("beforeEach() - Dropping DB");
        DbTestUtil.dropThreadTestDatabase(true);

        LOGGER.info("Running all migrations up to and including {}, using test data {}",
                targetVersion, testDataVersion);
        injector = Guice.createInjector(
                new DbTestModule(),
                getDbConnectionsModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        // Bind the test state so the migration get at it
                        bind(TestState.class)
                                .toInstance(new TestState(
                                        targetVersion,
                                        testDataVersion,
                                        getTestDataClass(),
                                        AbstractCrossModuleMigrationTest.this.getClass(),
                                        getTargetClass()));
                    }
                });
    }

    @AfterEach
    void afterEach() {
        // Drop the db as it contains migration classes that have run (i.e. the
        // TestData ones) but won't be visible to flyway in other tests.
        LOGGER.info("afterEach() - Dropping DB");
        DbTestUtil.dropThreadTestDatabase(true);
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
                        return Optional.ofNullable(targetVersion);
                    }

                    @Override
                    protected List<Class<? extends JavaMigration>> getAdditionalMigrationClasses() {
                        // Get the subclass to tell us what test data class is being used as a 'migration'
                        // to load test data prior to the migration under test.
                        return List.of(AbstractCrossModuleMigrationTest.this.getTestDataClass());
                    }
                };
            }
        };
    }

    /**
     * This is version of the migration under test
     */
    protected MigrationVersion getTargetVersion() {
        final String versionUnderTest = getTargetClass()
                .getSimpleName()
                .replaceAll("^V", "")
                .replaceAll("__.*$", "");

        final MigrationVersion targetVersion = MigrationVersion.fromVersion(versionUnderTest);
        LOGGER.debug("Using target version number {}", targetVersion);
        return targetVersion;
    }

    /**
     * This is version of this test class which itself is a migration that is slotted
     * in just before the target version.
     */
    private MigrationVersion getTestDataVersion() {
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

        final MigrationVersion testDataVersion = MigrationVersion.fromVersion(sb.toString());
        LOGGER.debug("Using test data version number {}", testDataVersion);
        return testDataVersion;
    }
}
