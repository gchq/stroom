package stroom.test.common.util.db;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.HasDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.HikariConfigHolder;
import stroom.db.util.HikariConfigHolderImpl;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DbTestUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbTestUtil.class);

    private static final String TESTCONTAINERS_JDBC_CONTAINER_DATABASE_DRIVER = "org.testcontainers.jdbc.ContainerDatabaseDriver";

    private DbTestUtil() {
    }

    /**
     * Gets a TestContainers DB datasource for use in tests that don't use guice injection
     *
     */
    public static <T_Config extends HasDbConfig, T_ConnProvider extends DataSource> T_ConnProvider getTestDbDatasource(
            final AbstractFlyWayDbModule<T_Config, T_ConnProvider> dbModule,
            final T_Config config) {

        applyTestContainersConfig(config);
        return dbModule.getConnectionProvider(() -> config, new HikariConfigHolderImpl());
    }

    public static Injector overrideModuleWithTestDatabase(final AbstractModule sourceModule) {

        return Guice.createInjector(Modules.override(sourceModule)
                .with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        super.configure();
                        bind(HikariConfigHolder.class).toInstance(new TestContainersHikariConfigHolder());
                    }
                }));
    }

    public static void applyTestContainersConfig(final HasDbConfig hasDbConfig) {
        applyTestContainersConfig(hasDbConfig.getDbConfig().getConnectionConfig());
    }

    public static void applyTestContainersConfig(final ConnectionConfig connectionConfig) {
        try {
            final Class<?> clazz = Class.forName(TESTCONTAINERS_JDBC_CONTAINER_DATABASE_DRIVER);
            if (clazz != null) {
                LOGGER.info("Using test container DB connection config");

                connectionConfig.setJdbcDriverClassName("com.mysql.cj.jdbc.Driver");
                connectionConfig.setJdbcDriverUrl("jdbc:tc:mysql:5.5.52://localhost:3306/test");
//                    connectionConfig.setJdbcDriverUrl("jdbc:tc:mysql:5.6.43://localhost:3306/test");
//                    connectionConfig.setJdbcDriverUrl("jdbc:tc:mysql:5.7.25://localhost:3306/test");
//                    connectionConfig.setJdbcDriverUrl("jdbc:tc:mysql:8.0.15://localhost:3306/test");
                connectionConfig.setJdbcDriverPassword("test");
                connectionConfig.setJdbcDriverUsername("test");

            }
        } catch (final ClassNotFoundException e) {
            LOGGER.debug("Can't find class {} for Test Containers, falling back to standard DB connection",
                    TESTCONTAINERS_JDBC_CONTAINER_DATABASE_DRIVER);
        }
    }

    public static void clearAllTables(final Connection connection) {
        final List<String> tables = new ArrayList<>();

        try (final PreparedStatement statement = connection.prepareStatement("SELECT table_name FROM information_schema.tables where table_type like '%BASE TABLE%';")) {
            try (final ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String name = resultSet.getString(1);
                    if (!name.contains("schema")) {
                        tables.add(name);
                    }
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        clearTables(connection, tables);
    }

    private static void clearTables(final Connection connection, final List<String> tableNames) {
        List<String> deleteStatements = tableNames.stream()
                .map(tableName -> "DELETE FROM " + tableName)
                .collect(Collectors.toList());

        executeStatementsWithNoConstraints(connection, deleteStatements);
    }

    private static void executeStatementsWithNoConstraints(final Connection connection, final List<String> statements) {
        final List<String> allStatements = new ArrayList<>();
        allStatements.add("SET FOREIGN_KEY_CHECKS=0");
        allStatements.addAll(statements);
        allStatements.add("SET FOREIGN_KEY_CHECKS=1");

        executeStatements(connection, allStatements);
    }

    private static void executeStatements(final Connection connection, final List<String> sqlStatements) {
        LOGGER.debug(">>> %s", sqlStatements);
//        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final Statement statement = connection.createStatement()) {
            sqlStatements.forEach(sql -> {
                try {
                    statement.addBatch(sql);
                } catch (SQLException e) {
                    throw new RuntimeException(String.format("Error adding sql [%s] to batch", sql), e);
                }
            });
            int[] results = statement.executeBatch();
            boolean isFailure = Arrays.stream(results)
                    .anyMatch(val -> val == Statement.EXECUTE_FAILED);

            if (isFailure) {
                throw new RuntimeException(String.format("Got error code for batch %s", sqlStatements));
            }

//            log(logExecutionTime,
//                    () -> Arrays.stream(results)
//                            .mapToObj(Integer::toString)
//                            .collect(Collectors.joining(",")),
//                    sqlStatements::toString,
//                    Collections.emptyList());

        } catch (final SQLException e) {
            LOGGER.error("executeStatement() - " + sqlStatements, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
