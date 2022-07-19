package stroom.db.util;

import stroom.util.db.DbMigrationState;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
                               final String flywayLocations,
                               final String flywayTableName,
                               final String moduleName) {
        LOGGER.info(""
                + "\n-----------------------------------------------------------"
                + "\n  Migrating database module: " + moduleName
                + "\n-----------------------------------------------------------");

        final Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(flywayLocations)
                .table(flywayTableName)
                .baselineOnMigrate(true)
                .load();

        int pendingMigrations = flyway.info().pending().length;

        if (pendingMigrations > 0) {
            try {
                LOGGER.info(() -> "Applying " +
                        pendingMigrations +
                        " Flyway DB migration(s) to " +
                        moduleName +
                        " in table " +
                        flywayTableName +
                        " from " +
                        flywayLocations);

                flyway.migrate();

                LOGGER.info(() -> "Completed Flyway DB migration for " +
                        moduleName +
                        " in table " +
                        flywayTableName);
            } catch (FlywayException e) {
                LOGGER.error(() -> "Error migrating " +
                        moduleName +
                        " database", e);
                throw e;
            }
        } else {
            LOGGER.info(() -> "No pending Flyway DB migration(s) for " +
                    moduleName +
                    " in " +
                    flywayLocations);
        }
    }
}
