package stroom.core.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.db.util.HikariUtil;
import stroom.util.shared.Version;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Singleton
public class DataSourceProvider implements Provider<DataSource> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceProvider.class);
    private static final String MODULE = "stroom-corer";
    private static final String FLYWAY_LOCATIONS = "stroom/core/db/migration/mysql";
    private static final String FLYWAY_TABLE = "schema_version";

    private final Provider<DbConfig> configProvider;
    private volatile DataSource dataSource;

    @Inject
    DataSourceProvider(final Provider<DbConfig> configProvider) {
        this.configProvider = configProvider;
    }

    private DataSource dataSource() {
        LOGGER.info("Creating connection provider for {}", MODULE);
        final ConnectionConfig connectionConfig = configProvider.get().getConnectionConfig();
        final ConnectionPoolConfig connectionPoolConfig = configProvider.get().getConnectionPoolConfig();
        final HikariConfig config = HikariUtil.createConfig(connectionConfig, connectionPoolConfig);
        return new HikariDataSource(config);
    }

    private Flyway flyway(final DataSource dataSource) {
            final Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(FLYWAY_LOCATIONS)
                    .table(FLYWAY_TABLE)
                    .baselineOnMigrate(true)
                    .load();
            Version version = null;
            boolean usingFlyWay = false;
            LOGGER.info("Testing installed Stroom schema version");

            try (final Connection connection = dataSource.getConnection()) {
                try {
                    try (final Statement statement = connection.createStatement()) {
                        try (final ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version ORDER BY installed_rank DESC")) {
                            if (resultSet.next()) {
                                usingFlyWay = true;

                                final String ver = resultSet.getString(1);
                                final String[] parts = ver.split("\\.");
                                int maj = 0;
                                int min = 0;
                                int pat = 0;
                                if (parts.length > 0) {
                                    maj = Integer.valueOf(parts[0]);
                                }
                                if (parts.length > 1) {
                                    min = Integer.valueOf(parts[1]);
                                }
                                if (parts.length > 2) {
                                    pat = Integer.valueOf(parts[2]);
                                }

                                version = new Version(maj, min, pat);
                                LOGGER.info("Found schema_version.version " + ver);
                            }
                        }
                    }
                } catch (final SQLException e) {
                    LOGGER.debug(e.getMessage());
                    // Ignore.
                }

                if (version == null) {
                    try {
                        try (final Statement statement = connection.createStatement()) {
                            try (final ResultSet resultSet = statement.executeQuery("SELECT VER_MAJ, VER_MIN, VER_PAT FROM STROOM_VER ORDER BY VER_MAJ DESC, VER_MIN DESC, VER_PAT DESC LIMIT 1")) {
                                if (resultSet.next()) {
                                    version = new Version(resultSet.getInt(1), resultSet.getInt(2), resultSet.getInt(3));
                                    LOGGER.info("Found STROOM_VER.VER_MAJ/VER_MIN/VER_PAT " + version);
                                }
                            }
                        }
                    } catch (final SQLException e) {
                        LOGGER.debug(e.getMessage(), e);
                        // Ignore.
                    }
                }

                if (version == null) {
                    try {
                        try (final Statement statement = connection.createStatement()) {
                            try (final ResultSet resultSet = statement.executeQuery("SELECT ID FROM FD LIMIT 1")) {
                                if (resultSet.next()) {
                                    version = new Version(2, 0, 0);
                                }
                            }
                        }
                    } catch (final SQLException e) {
                        LOGGER.debug(e.getMessage(), e);
                        // Ignore.
                    }
                }

                if (version == null) {
                    try {
                        try (final Statement statement = connection.createStatement()) {
                            try (final ResultSet resultSet = statement.executeQuery("SELECT ID FROM FEED LIMIT 1")) {
                                if (resultSet.next()) {
                                    version = new Version(2, 0, 0);
                                }
                            }
                        }
                    } catch (final SQLException e) {
                        LOGGER.debug(e.getMessage(), e);
                        // Ignore.
                    }
                }
            } catch (final SQLException e) {
                LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }

            if (version != null) {
                LOGGER.info("Detected current Stroom version is v" + version.toString());
            } else {
                LOGGER.info("This is a new installation!");
            }

            if (version == null) {
                // If we have no version then this is a new Stroom instance so perform full FlyWay migration.
                migrateDatabase(flyway);
            } else if (usingFlyWay) {
                // If we are already using FlyWay then allow FlyWay to attempt migration.
                migrateDatabase(flyway);
            } else if (version.getMajor() == 4 && version.getMinor() == 0 && version.getPatch() >= 60) {
                // If Stroom is currently at v4.0.60+ then tell FlyWay to baseline at that version.
                flyway.setBaselineVersionAsString("4.0.60");
                flyway.baseline();
                migrateDatabase(flyway);
            } else {
                final String message = "The current Stroom version cannot be upgraded to v5+. You must be on v4.0.60 or later.";
                LOGGER.error(MarkerFactory.getMarker("FATAL"), message);
                throw new RuntimeException(message);
            }

            return flyway;
    }

    private void migrateDatabase(final Flyway flyway) {
        LOGGER.info("Applying Flyway migrations to {} in {} from {}", MODULE, FLYWAY_TABLE, FLYWAY_LOCATIONS);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating {} database", MODULE, e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for {} in {}", MODULE, FLYWAY_TABLE);
    }

    @Override
    public DataSource get() {
        if (dataSource == null) {
            synchronized (this) {
                if (dataSource == null) {
                    // Create a data source.
                    final DataSource ds = dataSource();
                    // Run flyway migrations.
                    flyway(ds);

                    // Assign.
                    dataSource = ds;
                }
            }
        }

        return dataSource;
    }

//    private C3P0Config getC3P0Config() {
//        return new C3P0Config("stroom.db.connectionPool.", propertyService);
//    }
}
