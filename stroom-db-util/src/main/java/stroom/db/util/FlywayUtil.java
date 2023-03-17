package stroom.db.util;

import stroom.util.NullSafe;
import stroom.util.db.DbMigrationState;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
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
        migrate(dataSource, flywayLocations, null, flywayTableName, moduleName);
    }

    public static void migrate(final DataSource dataSource,
                               final List<String> flywayLocations,
                               final MigrationVersion target,
                               final String flywayTableName,
                               final String moduleName) {

        LOGGER.info(LogUtil.inBoxOnNewLine("Migrating database module: {}", moduleName));

        final String[] migrationLocations = NullSafe.list(flywayLocations)
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .toArray(String[]::new);

        final FluentConfiguration fluentConfiguration = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocations)
                .table(flywayTableName)
                .baselineOnMigrate(true);

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

            // This will see if anything needs doing
            final MigrateResult migrateResult = flyway.migrate();

            if (migrateResult.migrationsExecuted > 0) {
                LOGGER.info("{} - Successfully applied {} Flyway DB migrations using history table '{}'",
                        moduleName,
                        migrateResult.migrationsExecuted,
                        flywayTableName);
            } else {
                LOGGER.info("{} - No Flyway DB migration(s) applied in path {}",
                        moduleName,
                        flywayLocations);
            }

        } catch (FlywayException e) {
            LOGGER.error("{} - Error migrating database: {}", moduleName, e.getMessage(), e);
            throw e;
        }
    }
}
