package stroom.persist;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.properties.GlobalProperties;
import stroom.properties.StroomPropertyService;
import stroom.util.config.StroomProperties;
import stroom.util.shared.Version;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.beans.PropertyVetoException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Singleton
public class DataSourceProvider implements Provider<DataSource> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceProvider.class);

    private final StroomPropertyService stroomPropertyService;
    private volatile DataSource dataSource;

    @Inject
    DataSourceProvider(final GlobalProperties globalProperties, final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    private ComboPooledDataSource dataSource() {
        try {
            final ComboPooledDataSource dataSource = new ComboPooledDataSource();
            dataSource.setDataSourceName("stroom");
            dataSource.setDescription("Stroom data source");

            final DataSourceConfig dataSourceConfig = getDataSourceConfig();
            dataSource.setDriverClass(dataSourceConfig.getJdbcDriverClassName());
            dataSource.setJdbcUrl(dataSourceConfig.getJdbcDriverUrl());
            dataSource.setUser(dataSourceConfig.getJdbcDriverUsername());
            dataSource.setPassword(dataSourceConfig.getJdbcDriverPassword());

            final C3P0Config config = getC3P0Config();
            dataSource.setMaxStatements(config.getMaxStatements());
            dataSource.setMaxStatementsPerConnection(config.getMaxStatementsPerConnection());
            dataSource.setInitialPoolSize(config.getInitialPoolSize());
            dataSource.setMinPoolSize(config.getMinPoolSize());
            dataSource.setMaxPoolSize(config.getMaxPoolSize());
            dataSource.setIdleConnectionTestPeriod(config.getIdleConnectionTestPeriod());
            dataSource.setMaxIdleTime(config.getMaxIdleTime());
            dataSource.setAcquireIncrement(config.getAcquireIncrement());
            dataSource.setAcquireRetryAttempts(config.getAcquireRetryAttempts());
            dataSource.setAcquireRetryDelay(config.getAcquireRetryDelay());
            dataSource.setCheckoutTimeout(config.getCheckoutTimeout());
            dataSource.setMaxAdministrativeTaskTime(config.getMaxAdministrativeTaskTime());
            dataSource.setMaxIdleTimeExcessConnections(config.getMaxIdleTimeExcessConnections());
            dataSource.setMaxConnectionAge(config.getMaxConnectionAge());
            dataSource.setUnreturnedConnectionTimeout(config.getUnreturnedConnectionTimeout());
            dataSource.setStatementCacheNumDeferredCloseThreads(config.getStatementCacheNumDeferredCloseThreads());
            dataSource.setNumHelperThreads(config.getNumHelperThreads());

            dataSource.setPreferredTestQuery("select 1");
            dataSource.setConnectionTesterClassName(StroomProperties.getProperty("stroom.connectionTesterClassName"));

            return dataSource;
        } catch (final PropertyVetoException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Flyway flyway(final DataSource dataSource) {
        final String jpaHbm2DdlAuto = StroomProperties.getProperty("stroom.jpaHbm2DdlAuto", "validate");
        if (!"update".equals(jpaHbm2DdlAuto)) {
            final Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.setLocations("stroom/db/migration/mysql");

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
                } catch (final Exception e) {
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
                    } catch (final Exception e) {
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
                    } catch (final Exception e) {
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
                    } catch (final Exception e) {
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
                flyway.migrate();
            } else if (usingFlyWay) {
                // If we are already using FlyWay then allow FlyWay to attempt migration.
                flyway.migrate();
            } else if (version.getMajor() == 4 && version.getMinor() == 0 && version.getPatch() >= 60) {
                // If Stroom is currently at v4.0.60+ then tell FlyWay to baseline at that version.
                flyway.setBaselineVersionAsString("4.0.60");
                flyway.baseline();
                flyway.migrate();
            } else {
                final String message = "The current Stroom version cannot be upgraded to v5+. You must be on v4.0.60 or later.";
                LOGGER.error(MarkerFactory.getMarker("FATAL"), message);
                throw new RuntimeException(message);
            }

            return flyway;

        }

        return null;
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

    private DataSourceConfig getDataSourceConfig() {
        return new DataSourceConfig("stroom.", stroomPropertyService);
    }

    private C3P0Config getC3P0Config() {
        return new C3P0Config("stroom.db.connectionPool.", stroomPropertyService);
    }
}
