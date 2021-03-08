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

    public static void migrate(final DataSource dataSource,
                               final String flywayLocations,
                               final String flywayTableName,
                               final String moduleName) {
        // Prevent migrations from being re-run for each test
        Set<String> set = COMPLETED_MIGRATIONS.get();
        if (set == null) {
            set = new HashSet<>();
            COMPLETED_MIGRATIONS.set(set);
        }
        final boolean required = set.add(moduleName);

//        final boolean required = COMPLETED_MIGRATIONS
//                .computeIfAbsent(dataSource, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
//                .add(getModuleName());

        if (required) {
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
}
