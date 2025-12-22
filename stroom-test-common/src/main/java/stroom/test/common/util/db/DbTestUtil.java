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

package stroom.test.common.util.db;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceKey;
import stroom.db.util.DbUrl;
import stroom.db.util.HikariUtil;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;

public class DbTestUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbTestUtil.class);

    private static final String FIND_TABLES = """
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = database()
            AND TABLE_TYPE LIKE '%BASE TABLE%'
            AND TABLE_NAME NOT LIKE '%schema%'""";

    private static final ThreadLocal<AbstractDbConfig> THREAD_LOCAL_SHARED_DB_CONFIG = new ThreadLocal<>();
    private static final ThreadLocal<String> THREAD_LOCAL_SHARED_DB_NAME = new ThreadLocal<>();
    private static final ThreadLocal<AbstractDbConfig> THREAD_LOCAL_INDEPENDENT_DB_CONFIG = new ThreadLocal<>();
    private static final ThreadLocal<String> THREAD_LOCAL_INDEPENDENT_DB_NAME = new ThreadLocal<>();

    private static final ThreadLocal<Set<DataSource>> LOCAL_DATA_SOURCES = new ThreadLocal<>();

    private static final ConcurrentMap<DataSourceKey, DataSource> DATA_SOURCE_MAP = new ConcurrentHashMap<>();
    private static final Set<String> DB_NAMES_IN_USE = new ConcurrentSkipListSet<>();
    // See createTestDbName
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^test_[0-9]+_[0-9]+_[0-9a-zA-Z]+$");

    private DbTestUtil() {
    }

    /**
     * Gets an embedded DB datasource for use in tests that don't use guice injection
     */
    public static <T_CONFIG extends AbstractDbConfig, T_CONN_PROV extends DataSource> T_CONN_PROV getTestDbDatasource(
            final AbstractFlyWayDbModule<T_CONFIG, T_CONN_PROV> dbModule,
            final T_CONFIG config) {

        // We are only running one module so just pass in any empty ForceCoreMigration
        return dbModule.getConnectionProvider(
                () -> config,
                new TestDataSourceFactory(CommonDbConfig::new),
                null);
    }

    public static String getGradleWorker() {
        // This is the name of the forked gradle worker, i.e. a name for the jvm
        return Objects.requireNonNullElse(
                System.getProperty("org.gradle.test.worker"),
                "0");
    }

    private static String createTestDbName() {
        String dbName;
        do {
            final String uuid = UUID.randomUUID()
                    .toString()
                    .toLowerCase()
                    .replace("-", "");

            // Include the gradle worker id (unique to the JVM) and the thread ID to ensure there
            // is no clash between JVMs or threads
            dbName = String.join("_",
                    "test",
                    getGradleWorker(),
                    Long.toString(Thread.currentThread().threadId()),
                    uuid);

            // Truncate to max 64 chars to ensure we don't blow db name limit
            if (dbName.length() > 64) {
                dbName = dbName.substring(0, 64);
            }

            // We use the pattern to drop dbs after all tests have run, so ensure the name matches the pattern now.
            if (!DB_NAME_PATTERN.asMatchPredicate().test(dbName)) {
                throw new RuntimeException(LogUtil.message("dbName '{}' does not match pattern {}",
                        dbName, DB_NAME_PATTERN));
            }

            // Loop in case of name clash
        } while (DB_NAMES_IN_USE.contains(dbName));

        DB_NAMES_IN_USE.add(dbName);
        return dbName;
    }

    public static DataSource createTestDataSource() {
        return createTestDataSource(new CommonDbConfig(), "test", false);
    }

    private static <T> T getValueOrOverride(final String envVarName,
                                            final String propName,
                                            final Supplier<T> valueSupplier,
                                            final Function<String, T> typeMapper) {
        return Optional.ofNullable(System.getenv(envVarName))
                .map(envVarVal -> {
                    LOGGER.info("Overriding prop {} with value [{}] from {}",
                            propName,
                            envVarVal,
                            envVarName);
                    return typeMapper.apply(envVarVal);
                })
                .orElseGet(valueSupplier);
    }

    /**
     * Verify there is a connection to the database on the root account.
     */
    public static boolean isDbAvailable() {
        final ConnectionConfig connectionConfig = createConnectionConfig(new CommonDbConfig());
        final ConnectionConfig rootConnectionConfig = createRootConnectionConfig(connectionConfig);

        // Create new db.
        final Properties connectionProps = new Properties();
        connectionProps.put("user", rootConnectionConfig.getUser());
        connectionProps.put("password", rootConnectionConfig.getPassword());

        try (final Connection connection = DriverManager.getConnection(rootConnectionConfig.getUrl(),
                connectionProps)) {
            return connection.isValid(5);
        } catch (final SQLException e) {
            return false;
        }
    }

    public static void dropUnusedTestDatabases() {
        final ConnectionConfig connectionConfig = createConnectionConfig(new CommonDbConfig());
        final ConnectionConfig rootConnectionConfig = createRootConnectionConfig(connectionConfig);

        // Create new db.
        final Properties connectionProps = new Properties();
        connectionProps.put("user", rootConnectionConfig.getUser());
        connectionProps.put("password", rootConnectionConfig.getPassword());

        // Only match dbs with this gradle worker in the name
        final Pattern dbNamePatternThisWorker = Pattern.compile(
                "^test_" + Pattern.quote(getGradleWorker()) + "_.*$");
        LOGGER.info("Dropping databases matching pattern '{}'", dbNamePatternThisWorker);

        final Predicate<String> dbNameMatchPredicate = dbNamePatternThisWorker.asMatchPredicate();

        try (final Connection connection = DriverManager.getConnection(rootConnectionConfig.getUrl(),
                connectionProps)) {
            try (final Statement statement = connection.createStatement()) {
                final ResultSet resultSet = statement.executeQuery("SHOW DATABASES;");
                final List<String> dbNames = new ArrayList<>();
                while (resultSet.next()) {
                    final String dbName = resultSet.getString(1);
                    if (dbNameMatchPredicate.test(dbName)) {
                        dbNames.add(dbName);
                    }
                }

                for (final String dbName : dbNames) {
                    LOGGER.info("Dropping test database {}", dbName);
                    statement.executeUpdate("DROP DATABASE " + dbName + ";");
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static void doWithRootConnection(final Consumer<Connection> connectionConsumer) {
        if (connectionConsumer != null) {
            getWithRootConnection(connection -> {
                connectionConsumer.accept(connection);
                // Result ignored
                return null;
            });
        }
    }

    public static <T> T getWithRootConnection(final Function<Connection, T> conectionFunction) {
        Objects.requireNonNull(conectionFunction);
        final ConnectionConfig connectionConfig = createConnectionConfig(new CommonDbConfig());
        final ConnectionConfig rootConnectionConfig = createRootConnectionConfig(connectionConfig);

        ensureJdbcDriver(connectionConfig);
        try (final Connection connection = DriverManager.getConnection(rootConnectionConfig.getUrl(),
                rootConnectionConfig.getUser(),
                rootConnectionConfig.getPassword())) {
            return conectionFunction.apply(connection);
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static void ensureJdbcDriver(final ConnectionConfig connectionConfig) {
        try {
            Class.forName(connectionConfig.getClassName());
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static void dropAllTestDatabases() {
        final Predicate<String> dbNameMatchPredicate = DB_NAME_PATTERN.asMatchPredicate();
        doWithRootConnection(ThrowingConsumer.unchecked(connection -> {
            try (final Statement statement = connection.createStatement()) {
                final ResultSet resultSet = statement.executeQuery("SHOW DATABASES;");
                final List<String> dbNames = new ArrayList<>();
                while (resultSet.next()) {
                    final String dbName = resultSet.getString(1);
                    if (dbNameMatchPredicate.test(dbName)) {
                        dbNames.add(dbName);
                    }
                }

                for (final String dbName : dbNames) {
                    LOGGER.info("Dropping test database {}", dbName);
                    statement.executeUpdate("DROP DATABASE " + dbName + ";");
                }
            }
        }));
    }

    public static void dropDatabase(final String dbName) {
        doWithRootConnection(ThrowingConsumer.unchecked(rootConn -> {
            try (final Statement statement = rootConn.createStatement()) {
                statement.executeUpdate("DROP DATABASE `" + dbName + "`;");
            }
        }));


    }

    private static ThreadLocal<String> getDbNameThreadLocal(final boolean isSharedDatabase) {
        return isSharedDatabase
                ? THREAD_LOCAL_SHARED_DB_NAME
                : THREAD_LOCAL_INDEPENDENT_DB_NAME;
    }

    private static ThreadLocal<AbstractDbConfig> getDbConfigThreadLocal(final boolean isSharedDatabase) {
        return isSharedDatabase
                ? THREAD_LOCAL_SHARED_DB_CONFIG
                : THREAD_LOCAL_INDEPENDENT_DB_CONFIG;
    }

    /**
     * Drop the database used by the current thread
     *
     * @param isSharedDatabase True if you want to drop the current shared database or
     *                         false if you want to drop the current independent database.
     */
    public static void dropThreadTestDatabase(final boolean isSharedDatabase) {
        final String dbName = getDbNameThreadLocal(isSharedDatabase).get();

        LOGGER.info("dropThreadTestDatabase - isSharedDatabase: {}, testDbConfig: {}, dbName: {}",
                isSharedDatabase,
                NullSafe.get(getDbConfigThreadLocal(isSharedDatabase), ThreadLocal::get, Objects::toIdentityString),
                dbName);

        if (dbName != null) {
            final ConnectionConfig connectionConfig = createConnectionConfig(new CommonDbConfig());
            final ConnectionConfig rootConnectionConfig = createRootConnectionConfig(connectionConfig);

            // Create new db.
            final Properties connectionProps = new Properties();
            connectionProps.put("user", rootConnectionConfig.getUser());
            connectionProps.put("password", rootConnectionConfig.getPassword());

            try (final Connection connection = DriverManager.getConnection(rootConnectionConfig.getUrl(),
                    connectionProps)) {
                try (final Statement statement = connection.createStatement()) {
                    LOGGER.info(LogUtil.inBoxOnNewLine(
                            "Dropping {} test database: {} on thread: {}",
                            (isSharedDatabase
                                    ? "shared"
                                    : "independent"),
                            dbName,
                            Thread.currentThread().getName()));

                    statement.executeUpdate("DROP DATABASE IF EXISTS `" + dbName + "`;");
                    DB_NAMES_IN_USE.remove(dbName);
                    getDbNameThreadLocal(isSharedDatabase).remove();
                    // Clear out the config so a new DB gets built next time if required on this thread
                    getDbConfigThreadLocal(isSharedDatabase).remove();
                }
            } catch (final SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        } else {
            LOGGER.info("Thread has no database to drop");
        }
    }

    public static DataSource createTestDataSource(final AbstractDbConfig dbConfig,
                                                  final String name,
                                                  final boolean unique) {
        return createTestDataSource(dbConfig, name, unique, true);
    }

    /**
     * @param dbConfig         The config to use
     * @param name             The name of the datasource, i.e. the module name
     * @param unique           True if we want our own dedicated connection pool
     * @param isSharedDatabase True if we want to re-use the DB between tests. We normally do
     *                         to save on the cost of running migrations, but not for
     *                         migration tests as for those we want a blank db.
     */
    public static DataSource createTestDataSource(final AbstractDbConfig dbConfig,
                                                  final String name,
                                                  final boolean unique,
                                                  final boolean isSharedDatabase) {

        if ("org.sqlite.JDBC".equals(dbConfig.getConnectionConfig().getClassName())) {
            final HikariConfig hikariConfig = HikariUtil.createConfig(dbConfig);
            return new HikariDataSource(hikariConfig);
        }


        // See if we have a local data source.
        AbstractDbConfig testDbConfig = getDbConfigThreadLocal(isSharedDatabase).get();
        String dbName = getDbNameThreadLocal(isSharedDatabase).get();

        LOGGER.info("createDataSource - isSharedDatabase: {}, testDbConfig: {}, dbName: {}",
                isSharedDatabase,
                NullSafe.get(testDbConfig, Objects::toIdentityString),
                dbName);
        if (testDbConfig == null || dbName == null || !isSharedDatabase) {
            // Create a merged config using the common db config as a base.
            final ConnectionConfig connectionConfig = createConnectionConfig(dbConfig);
            final ConnectionConfig rootConnectionConfig = createRootConnectionConfig(connectionConfig);

            // Create new db.
            final Properties connectionProps = new Properties();
            connectionProps.put("user", rootConnectionConfig.getUser());
            connectionProps.put("password", rootConnectionConfig.getPassword());

            LOGGER.debug("Connecting to DB as root connection with URL: {}",
                    rootConnectionConfig.getUrl());

            if (dbName == null || !isSharedDatabase) {
                dbName = createTestDbName();
                LOGGER.info("Created new database name '{}'", dbName);
//                dbName = Objects.requireNonNullElseGet(
//                        getDbNameThreadLocal(isSharedDatabase).get(),
//                        DbTestUtil::createTestDbName);
                getDbNameThreadLocal(isSharedDatabase).set(dbName);
            }

            LOGGER.info(LogUtil.inBoxOnNewLine("Creating {} test database: {} for thread: {}",
                    (isSharedDatabase
                            ? "shared"
                            : "independent"),
                    dbName,
                    Thread.currentThread().getName()));

            // ********************************************************************************
            // NOTE: the test database created here for this thread will be dropped once
            // all tests (on this JVM) have finished. See:
            // stroom.test.common.util.test.StroomTestExecutionListener
            // See also stroom.test.DatabaseCommonTestControl#main for a manual way of deleting
            // all these test DBs.
            // ********************************************************************************

            try (final Connection connection = DriverManager.getConnection(rootConnectionConfig.getUrl(),
                    connectionProps)) {
                final String username = connectionConfig.getUser();
                final String password = connectionConfig.getPassword();
                createStroomDatabaseAndUser(connection, dbName, username, password);
            } catch (final SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }

            // Create a URL for connecting to the new DB.
            final String url = DbUrl
                    .builder()
                    .parse(rootConnectionConfig.getUrl())
                    .dbName(dbName)
                    .build()
                    .toString();

            final ConnectionConfig newConnectionConfig = connectionConfig
                    .copy()
                    .url(url)
                    .build();
            LOGGER.info("Using DB connection url: {}", url);
            testDbConfig = new AbstractDbConfig(
                    newConnectionConfig,
                    dbConfig.getConnectionPoolConfig()) {
            };

            getDbConfigThreadLocal(isSharedDatabase).set(testDbConfig);
        } else {
            LOGGER.info(LogUtil.inBoxOnNewLine("Reusing shared database: {}, on thread: {}",
                    getDbNameThreadLocal(true).get(),
                    Thread.currentThread().getName()));
        }

        final DataSource dataSource = createDataSource(
                name,
                (unique || !isSharedDatabase),
                testDbConfig);

        Set<DataSource> dataSources = LOCAL_DATA_SOURCES.get();
        if (dataSources == null) {
            dataSources = new HashSet<>();
            LOCAL_DATA_SOURCES.set(dataSources);
        }
        dataSources.add(dataSource);

        return dataSource;
    }

    public static void createStroomDatabaseAndUser(final Connection connection,
                                                   final String dbName,
                                                   final String username,
                                                   final String password) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            LOGGER.debug("Creating database '{}'", dbName);
            statement.executeUpdate("CREATE DATABASE `" + dbName +
                                    "` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;");

            LOGGER.debug("Creating DB user '{}'", username);
            statement.executeUpdate("CREATE USER IF NOT EXISTS '" +
                                    username + "'@'%' IDENTIFIED BY '" +
                                    password + "';");

            LOGGER.debug("Granting privileges to user '{}'", username);
            statement.executeUpdate("GRANT ALL PRIVILEGES ON *.* TO '" +
                                    username + "'@'%' WITH GRANT OPTION;");
        }
    }

    private static DataSource createDataSource(final String name,
                                               final boolean unique,
                                               final AbstractDbConfig testDbConfig) {
        // Get a data source from a map to limit connections where connection details are common.
        LOGGER.debug("Creating datasource for name: {}, unique: {}, testDbConfig: {}", name, unique, testDbConfig);
        final DataSourceKey dataSourceKey = new DataSourceKey(testDbConfig, name, unique);
        return DATA_SOURCE_MAP.computeIfAbsent(dataSourceKey, k -> {
            LOGGER.debug("Creating new HikariConfig for name: {}", name);
            final HikariConfig hikariConfig = HikariUtil.createConfig(
                    k.getConfig(), null, null, null);
            return new HikariDataSource(hikariConfig);
        });
    }

    private static ConnectionConfig createRootConnectionConfig(final ConnectionConfig connectionConfig) {
        final DbUrl dbUrl = DbUrl.parse(connectionConfig.getUrl());

        // Allow the db conn details to be overridden with env vars, e.g. when we want to run tests
        // from within a container, so we need a different host to localhost
        final String effectiveHost = getValueOrOverride(
                "STROOM_JDBC_DRIVER_HOST",
                "JDBC hostname",
                dbUrl::getHost,
                Function.identity());
        final int effectivePort = getValueOrOverride(
                "STROOM_JDBC_DRIVER_PORT",
                "JDBC port",
                dbUrl::getPort,
                Integer::parseInt);

        final String url = DbUrl
                .builder()
                .scheme(dbUrl.getScheme())
                .host(effectiveHost)
                .port(effectivePort)
                .query(dbUrl.getQuery())
                .build()
                .toString();
        return connectionConfig
                .copy()
                .url(url)
                .user("root")
                .password("my-secret-pw")
                .build();
    }

    private static ConnectionConfig createConnectionConfig(final AbstractDbConfig dbConfig) {
        return dbConfig.getConnectionConfig();
    }

    public static void clear() {
        final Set<DataSource> dataSources = LOCAL_DATA_SOURCES.get();
        if (dataSources != null) {
            for (final DataSource dataSource : dataSources) {
                // Clear the database.
                LOGGER.info("Clearing all tables in DB {}", THREAD_LOCAL_SHARED_DB_NAME.get());
                try (final Connection connection = dataSource.getConnection()) {
                    DbTestUtil.clearAllTables(connection);
                } catch (final SQLException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }
    }

    private static void clearAllTables(final Connection connection) {
        final List<String> tables = new ArrayList<>();

        try (final PreparedStatement statement = connection.prepareStatement(FIND_TABLES)) {
            try (final ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String name = resultSet.getString(1);
                    // `permission_doc_id` is a table of static reference values so leave it alone.
                    if (!"permission_doc_id".equalsIgnoreCase(name)) {
                        tables.add(name);
                    }
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        clearTables(connection, tables);
    }

    public static void clearTables(final Connection connection, final List<String> tableNames) {
        final List<String> deleteStatements = tableNames.stream()
                .map(tableName -> "DELETE FROM " + tableName)
                .toList();

        executeStatementsWithNoConstraints(connection, deleteStatements);
    }

    public static void truncateTables(final Connection connection, final List<String> tableNames) {
        final List<String> deleteStatements = tableNames.stream()
                .map(tableName -> "TRUNCATE TABLE " + tableName)
                .collect(Collectors.toList());

        executeStatementsWithNoConstraints(connection, deleteStatements);
    }


    private static void executeStatementsWithNoConstraints(final Connection connection,
                                                           final List<String> statements) {
        final List<String> allStatements = new ArrayList<>();
        allStatements.add("SET FOREIGN_KEY_CHECKS=0");
        allStatements.addAll(statements);
        allStatements.add("SET FOREIGN_KEY_CHECKS=1");

        executeStatements(connection, allStatements);
    }

    private static void executeStatements(final Connection connection, final List<String> sqlStatements) {
        LOGGER.debug(() -> ">>> " + sqlStatements);
//        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final Statement statement = connection.createStatement()) {
            sqlStatements.forEach(sql -> {
                try {
                    statement.addBatch(sql);
                } catch (final SQLException e) {
                    throw new RuntimeException(String.format("Error adding sql [%s] to batch", sql), e);
                }
            });
            final int[] results = statement.executeBatch();
            final boolean isFailure = Arrays.stream(results)
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
            LOGGER.error(() -> "executeStatement() - " + sqlStatements, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
