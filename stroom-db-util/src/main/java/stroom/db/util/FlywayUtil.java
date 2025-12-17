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

package stroom.db.util;

import stroom.util.db.DbMigrationState;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

public final class FlywayUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FlywayUtil.class);

    private static final ThreadLocal<Set<String>> THREAD_LOCAL_COMPLETED_MIGRATIONS = new ThreadLocal<>();

    private FlywayUtil() {
        // Utility class.
    }

    public static boolean migrationRequired(final String moduleName) {
        final Set<String> set = Objects.requireNonNullElseGet(
                THREAD_LOCAL_COMPLETED_MIGRATIONS.get(),
                () -> {
                    final Set<String> emptySet = new HashSet<>();
                    THREAD_LOCAL_COMPLETED_MIGRATIONS.set(emptySet);
                    return emptySet;
                });

        // Prevent migrations from being re-run for each test
        final boolean hasModuleBeenMigrated = !set.add(moduleName);

        final boolean haveBootstrapMigrationsBeenDone = DbMigrationState.haveBootstrapMigrationsBeenDone();

        LOGGER.debug(() ->
                LogUtil.message("Module {}, hasModuleBeenMigrated: {}, haveBootstrapMigrationsBeenDone: {}",
                        moduleName, hasModuleBeenMigrated, haveBootstrapMigrationsBeenDone));

        // haveBootstrapMigrationsBeenDone gets set as part of the bootstrapping in App, whereas
        // hasModuleBeenMigrated controls test runs where the app bootstrapping may not have been
        // done.
        final boolean isMigrationRequired;
        if (haveBootstrapMigrationsBeenDone) {
            LOGGER.debug(() -> LogUtil.message(
                    "Stroom has already been migrated to this build version (module: {}).", moduleName));
            isMigrationRequired = false;
        } else {
            if (hasModuleBeenMigrated) {
                LOGGER.debug(() -> LogUtil.message("Module {} has already been migrated.", moduleName));
                isMigrationRequired = false;
            } else {
                isMigrationRequired = true;
            }
        }
        return isMigrationRequired;
    }

    public static void migrate(final DataSource dataSource,
                               final List<String> flywayLocations,
                               final String flywayTableName,
                               final String moduleName) {
        migrate(dataSource, flywayLocations, null, null, flywayTableName, moduleName);
    }

    public static void migrate(final DataSource dataSource,
                               final List<String> flywayLocations,
                               final MigrationVersion target,
                               final String flywayTableName,
                               final String moduleName) {
        migrate(dataSource, flywayLocations, null, target, flywayTableName, moduleName);
    }

    public static void migrate(final DataSource dataSource,
                               final Collection<JavaMigration> javaMigrations,
                               final MigrationVersion target,
                               final String flywayTableName,
                               final String moduleName) {
        migrate(dataSource, null, javaMigrations, target, flywayTableName, moduleName);
    }

    public static void migrate(final DataSource dataSource,
                               final List<String> flywayLocations,
                               final Collection<JavaMigration> javaMigrations,
                               final MigrationVersion target,
                               final String flywayTableName,
                               final String moduleName) {

        if (DbMigrationState.haveBootstrapMigrationsBeenDone()) {
            // Either this is a reboot of the node with no change to the version of stroom being run
            // or another node has done the migration for us.
            LOGGER.info("Skipping database migration for module {} because Stroom is already " +
                        "at the correct version", moduleName);
        } else {
            LOGGER.info(LogUtil.inBoxOnNewLine("Migrating database module: {}", moduleName));

            FluentConfiguration fluentConfiguration = Flyway.configure()
                    .dataSource(dataSource)
                    .table(flywayTableName)
                    .baselineOnMigrate(true);

            // Typical case for our db modules. Flyway finds the sql/java migs in the
            // locations (one per module) and instantiates them.
            if (NullSafe.hasItems(flywayLocations)) {
                final String[] migrationLocations = flywayLocations.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toArray(String[]::new);

                fluentConfiguration = fluentConfiguration.locations(migrationLocations);
            }

            // For our AbstractAppWideJavaDbMigration classes which we wire up with guice
            // and provide as objects
            if (NullSafe.hasItems(javaMigrations)) {
                final JavaMigration[] javaMigrationsArr = javaMigrations.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toArray(JavaMigration[]::new);
                fluentConfiguration.javaMigrations(javaMigrationsArr);
            }

            // Set the target for the migration, i.e. only migrate up to this point.
            // Used for testing migrations.
            if (target != null) {
                LOGGER.info("Migrating with target version (inc.): {}", target);
                fluentConfiguration.target(target);
            }

            final Flyway flyway = fluentConfiguration.load();

            final String statesInfo = Arrays.stream(flyway.info().all())
                    .collect(Collectors.groupingBy(MigrationInfo::getState))
                    .entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey())
                    .map(entry -> entry.getKey() + ":" + entry.getValue().size())
                    .collect(Collectors.joining(", "));

            try {
                LOGGER.info("{} - Validating existing and pending Flyway DB migration(s) ({}) " +
                            "using history table '{}' from path {}",
                        moduleName,
                        statesInfo,
                        flywayTableName,
                        flywayLocations);

                final MigrateResult migrateResult = flyway.migrate();

                if (migrateResult.migrationsExecuted > 0) {
                    final String migrationListStr = AsciiTable.builder(NullSafe.list(migrateResult.migrations))
                            .withColumn(Column.of("Category", output -> output.category))
                            .withColumn(Column.of("Version", output -> output.version))
                            .withColumn(Column.of("Description", output -> output.description))
                            .withColumn(Column.of("path", output -> output.filepath))
                            .build();

                    LOGGER.info("{} - Successfully applied {} Flyway DB migrations using history table '{}'\n{}",
                            moduleName,
                            migrateResult.migrationsExecuted,
                            flywayTableName,
                            migrationListStr);
                } else {
                    LOGGER.info("{} - No Flyway DB migration(s) applied in path {}",
                            moduleName,
                            flywayLocations);
                }

            } catch (final FlywayException e) {
                LOGGER.error("{} - Error migrating database: {}", moduleName, e.getMessage(), e);
                throw e;
            }
        }
    }
}
