package stroom.jooq.codegen;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

public final class FlywayUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayUtil.class);

    private FlywayUtil() {
        // Utility class.
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
                LOGGER.info("Applying " +
                        pendingMigrations +
                        " Flyway DB migration(s) to " +
                        moduleName +
                        " in table " +
                        flywayTableName +
                        " from " +
                        flywayLocations);

                flyway.migrate();

                LOGGER.info("Completed Flyway DB migration for " +
                        moduleName +
                        " in table " +
                        flywayTableName);
            } catch (FlywayException e) {
                LOGGER.error("Error migrating " +
                        moduleName +
                        " database", e);
                throw e;
            }
        } else {
            LOGGER.info("No pending Flyway DB migration(s) for " +
                    moduleName +
                    " in " +
                    flywayLocations);
        }
    }
}
