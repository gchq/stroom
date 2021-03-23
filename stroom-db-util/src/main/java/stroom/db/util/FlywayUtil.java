package stroom.db.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;

public final class FlywayUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FlywayUtil.class);

    private static final ThreadLocal<Set<String>> COMPLETED_MIGRATIONS = new ThreadLocal<>();

    private FlywayUtil() {
        // Utility class.
    }

    public static boolean migrationRequired(final String moduleName) {
        // Prevent migrations from being re-run for each test
        Set<String> set = COMPLETED_MIGRATIONS.get();
        if (set == null) {
            set = new HashSet<>();
            COMPLETED_MIGRATIONS.set(set);
        }
        return set.add(moduleName);
    }

    public static void migrate(final DataSource dataSource,
                               final String flywayLocations,
                               final String flywayTableName,
                               final String moduleName) {
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
