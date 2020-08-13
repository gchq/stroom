package stroom.test.common.util.db;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DbUrl;
import stroom.db.util.DbUrl.Builder;
import stroom.db.util.HikariUtil;
import stroom.util.ConsoleColour;
import stroom.util.db.ForceCoreMigration;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.wix.mysql.EmbeddedMysql;
import com.wix.mysql.config.Charset;
import com.wix.mysql.config.DownloadConfig;
import com.wix.mysql.config.MysqldConfig;
import com.wix.mysql.distribution.Version;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DbTestUtil {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbTestUtil.class);

    private static final String DEFAULT_JDBC_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
    private static final String USE_EMBEDDED_MYSQL_PROP_NAME = "useEmbeddedMySql";
    private static final String EMBEDDED_MYSQL_DB_PASSWORD = "test";
    private static final String EMBEDDED_MYSQL_DB_USERNAME = "test";
    private static final String FIND_TABLES = "" +
            "SELECT TABLE_NAME " +
            "FROM INFORMATION_SCHEMA.TABLES " +
            "WHERE TABLE_SCHEMA = database() " +
            "AND TABLE_TYPE LIKE '%BASE TABLE%' " +
            "AND TABLE_NAME NOT LIKE '%schema%';";

    private static volatile EmbeddedMysql EMBEDDED_MYSQL;

    private static volatile boolean HAVE_ALREADY_SHOWN_DB_MSG = false;

    private static final ThreadLocal<DataSource> THREAD_LOCAL = new ThreadLocal<>();

    private DbTestUtil() {
    }

    /**
     * Gets an embedded DB datasource for use in tests that don't use guice injection
     */
    public static <T_Config extends HasDbConfig, T_ConnProvider extends DataSource> T_ConnProvider getTestDbDatasource(
            final AbstractFlyWayDbModule<T_Config, T_ConnProvider> dbModule,
            final T_Config config) {

        // We are only running one module so just pass in any empty ForceCoreMigration
        return dbModule.getConnectionProvider(
                () -> config,
                new TestDataSourceFactory(new CommonDbConfig()),
                new ForceCoreMigration() {
                });
    }

    private static String createTestDbName() {
        String uuid = UUID.randomUUID().toString();
        int index = uuid.indexOf("-");
        if (index != -1) {
            uuid = uuid.substring(0, index);
        }

        return "test_" + Thread.currentThread().getId() + "_" + uuid;
    }

    public static DataSource createTestDataSource() {
        return createTestDataSource(new CommonDbConfig());
    }

    public static DataSource createTestDataSource(final DbConfig dbConfig) {
        // See if we have a local data source.
        DataSource dataSource = THREAD_LOCAL.get();
        if (dataSource == null) {
            // Create a merged config using the common db config as a base.
            ConnectionConfig connectionConfig = dbConfig.getConnectionConfig();
            ConnectionConfig rootConnectionConfig;

            if (isUseEmbeddedDb()) {
                connectionConfig = DbTestUtil.createEmbeddedMySqlInstance();

                rootConnectionConfig = new ConnectionConfig.Builder(connectionConfig)
                        .user("root")
                        .password("")
                        .build();

            } else {
                final DbUrl dbUrl = DbUrl.parse(connectionConfig.getUrl());
                final String url = new Builder()
                        .scheme(dbUrl.getScheme())
                        .host(dbUrl.getHost())
                        .port(dbUrl.getPort())
                        .build()
                        .toString();

                rootConnectionConfig = new ConnectionConfig.Builder(connectionConfig)
                        .url(url)
//                        .user("root")
//                        .password("my-secret-pw")
                        .build();
            }

            // Create new db.
            final Properties connectionProps = new Properties();
            connectionProps.put("user", rootConnectionConfig.getUser());
            connectionProps.put("password", rootConnectionConfig.getPassword());

            final String dbName = DbTestUtil.createTestDbName();
            try (final Connection connection = DriverManager.getConnection(rootConnectionConfig.getUrl(), connectionProps)) {
                try (final Statement statement = connection.createStatement()) {
                    int result = 0;
                    result = statement.executeUpdate("CREATE DATABASE `" + dbName + "` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;");
//                        result = statement.executeUpdate("CREATE USER IF NOT EXISTS '" + connectionConfig.getJdbcDriverUsername() + "'@'%';");// IDENTIFIED BY '" + connectionConfig.getJdbcDriverUsername() + "';");
                    result = statement.executeUpdate("GRANT ALL PRIVILEGES ON *.* TO '" + connectionConfig.getUser() + "'@'%' WITH GRANT OPTION;");
                }
            } catch (final SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }

            // Create a URL for connecting to the new DB.
            final String url = new Builder()
                    .parse(rootConnectionConfig.getUrl())
                    .dbName(dbName)
                    .build()
                    .toString();

            final ConnectionConfig newConnectionConfig = new ConnectionConfig.Builder(connectionConfig)
                    .url(url)
                    .build();
            dbConfig.setConnectionConfig(newConnectionConfig);

            final HikariConfig hikariConfig = HikariUtil.createConfig(dbConfig);
            dataSource = new HikariDataSource(hikariConfig);

            THREAD_LOCAL.set(dataSource);
        }

        return dataSource;
    }

    private static boolean isUseEmbeddedDb() {
        String useTestContainersEnvVarVal = System.getProperty(USE_EMBEDDED_MYSQL_PROP_NAME);
        // This system prop allows a dev to use their own stroom-resources DB instead of test containers.
        // This is useful when debugging a test that needs to access the DB as the embedded DB has to
        // migrate the DB from scratch on each run which is very time consuming
        boolean useEmbeddedDb = (useTestContainersEnvVarVal == null
                || !useTestContainersEnvVarVal.toLowerCase().equals("false"));

        if (!HAVE_ALREADY_SHOWN_DB_MSG) {
            if (useEmbeddedDb) {
                String msg = "" +
                        "\n---------------------------------------------------------------" +
                        "\n                    Using embedded MySQL" +
                        "\n To use an external DB for better performance add the following" +
                        "\n   -D{}=false" +
                        "\n to Run Configurations -> Templates -> Junit -> VM Options" +
                        "\n You many need to restart IntelliJ for this to work" +
                        "\n---------------------------------------------------------------";
                LOGGER.info(ConsoleColour.green(msg), USE_EMBEDDED_MYSQL_PROP_NAME);
            } else {
                String msg = "" +
                        "\n---------------------------------------------------------------" +
                        "\n                    Using external MySQL" +
                        "\n---------------------------------------------------------------";
                LOGGER.info(ConsoleColour.cyan(msg));
            }
            HAVE_ALREADY_SHOWN_DB_MSG = true;
        }
        return useEmbeddedDb;
    }

    public static ConnectionConfig createEmbeddedMySqlInstance() {
        EmbeddedMysql embeddedMysql = EMBEDDED_MYSQL;
        if (embeddedMysql == null) {
            embeddedMysql = createEmbeddedMysql();
        }

        final MysqldConfig mysqlConfig = embeddedMysql.getConfig();
        final String url = new DbUrl
                .Builder()
                .port(mysqlConfig.getPort())
                .query("useUnicode=yes&characterEncoding=UTF-8")
                .build()
                .toString();

        final ConnectionConfig connectionConfig = new ConnectionConfig();
        connectionConfig.setClassName(DEFAULT_JDBC_DRIVER_CLASS_NAME);
        connectionConfig.setUrl(url);
        connectionConfig.setUser(mysqlConfig.getUsername());
        connectionConfig.setPassword(mysqlConfig.getPassword());

        return connectionConfig;


//        Properties connectionProps = new Properties();
//        connectionProps.put("user", EMBEDDED_MYSQL_DB_USERNAME);
//        connectionProps.put("password", EMBEDDED_MYSQL_DB_PASSWORD);
//
//        // Try and create a new schema.
//        try (final Connection connection = DriverManager.getConnection(url, connectionProps)) {
//            try (final Statement statement = connection.createStatement()) {
//                int result = 0;
//                result = statement.executeUpdate("CREATE DATABASE `" + dbName + "` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;");
////                        result = statement.executeUpdate("CREATE USER IF NOT EXISTS '" + connectionConfig.getJdbcDriverUsername() + "'@'%';");// IDENTIFIED BY '" + connectionConfig.getJdbcDriverUsername() + "';");
//                result = statement.executeUpdate("GRANT ALL PRIVILEGES ON *.* TO '" + EMBEDDED_MYSQL_DB_USERNAME + "'@'%' WITH GRANT OPTION;");
//            }
//        } catch (final SQLException e) {
//            throw new RuntimeException(e.getMessage(), e);
//        }
//
//
//
////        final SchemaConfig schemaConfig = SchemaConfig.aSchemaConfig(dbName).build();
////        embeddedMysql.addSchema(schemaConfig);
//
//
//        return createConnectionConfig(dbName, mysqlConfig);
    }

    private static ConnectionConfig createConnectionConfig(final String dbName, final MysqldConfig mysqlConfig) {
        final String url = new DbUrl
                .Builder()
                .port(mysqlConfig.getPort())
                .dbName(dbName)
                .query("useUnicode=yes&characterEncoding=UTF-8")
                .build()
                .toString();

        final ConnectionConfig connectionConfig = new ConnectionConfig();
        connectionConfig.setUrl(url);
        connectionConfig.setUser(mysqlConfig.getUsername());
        connectionConfig.setPassword(mysqlConfig.getPassword());

        return connectionConfig;
    }

    private static synchronized EmbeddedMysql createEmbeddedMysql() {
        final String systemTempDir = System.getProperty("java.io.tmpdir");
        final Path parentDir = Paths.get(systemTempDir);
        final Path lockFile = parentDir.resolve("embedmysql.lock");
        final Path cacheDir = parentDir.resolve("embedmysql");

        EmbeddedMysql embeddedMysql = EMBEDDED_MYSQL;
        while (embeddedMysql == null) {
            // Add file locking to synchronise across JVM processes.
            try (final FileOutputStream fileOutputStream = new FileOutputStream(lockFile.toFile())) {
                FileChannel channel = fileOutputStream.getChannel();
                channel.lock();
                embeddedMysql = doCreateEmbeddedMysql(cacheDir);
            } catch (final IOException e) {
                LOGGER.trace(e.getMessage(), e);
            }

            try {
                Thread.sleep(500);
            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        EMBEDDED_MYSQL = embeddedMysql;
        return embeddedMysql;
    }

    private static EmbeddedMysql doCreateEmbeddedMysql(final Path cacheDir) {
        try {
            final Path tempDir = Files.createTempDirectory("embedmysql_");

            LOGGER.info(LambdaLogUtil.message("Embedded MySQL cache dir = {}", cacheDir.toString()));
            LOGGER.info(LambdaLogUtil.message("Embedded MySQL temp dir = {}", tempDir.toString()));

            final DownloadConfig downloadConfig = DownloadConfig.aDownloadConfig()
//                        .withProxy(aHttpProxy("remote.host", 8080))
                    .withCacheDir(cacheDir.toString())
                    .build();

            final MysqldConfig config = MysqldConfig.aMysqldConfig(Version.v8_0_17)
                    .withCharset(Charset.UTF8)
                    .withFreePort()
                    .withUser(EMBEDDED_MYSQL_DB_USERNAME, EMBEDDED_MYSQL_DB_PASSWORD)
                    .withTimeZone("Europe/London")
                    .withTimeout(2, TimeUnit.MINUTES)
                    .withServerVariable("max_connect_errors", 666)
                    .withTempDir(tempDir.toAbsolutePath().toString())
                    .build();

            final EmbeddedMysql embeddedMysql = EmbeddedMysql.anEmbeddedMysql(config, downloadConfig)
//                        .addSchema("aschema", ScriptResolver.classPathScript("db/001_init.sql"))
//                        .addSchema("aschema2", ScriptResolver.classPathScripts("db/*.sql"))
//                    .addSchema(schemaConfig)
                    .start();

            return embeddedMysql;

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private static String getClassContainer(Class c) {
        if (c == null) {
            throw new NullPointerException("The Class passed to this method may not be null");
        }
        try {
            while (c.isMemberClass() || c.isAnonymousClass()) {
                c = c.getEnclosingClass();
            }
            if (c.getProtectionDomain().getCodeSource() == null) {
                return null;
            }
            String packageRoot;
            try {
                final String thisClass = c.getResource(c.getSimpleName() + ".class").toString();
                final String classPath = Pattern.quote(c.getName().replaceAll("\\.", "/") + ".class");
                packageRoot = thisClass.replaceAll(classPath, "");
                packageRoot = packageRoot.replaceAll("!/$", "");
                packageRoot = packageRoot.replaceAll("[^/\\\\]*$", "");
                packageRoot = packageRoot.replaceAll("^[^/\\\\]*", "");
            } catch (Exception e) {
                packageRoot = Paths.get(c.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .toAbsolutePath().toString();
            }
            return packageRoot;
        } catch (final Exception e) {
            throw new RuntimeException("While interrogating " + c.getName() + ", an unexpected exception was thrown.", e);
        }
    }

    public static void clear() {
        final DataSource dataSource = THREAD_LOCAL.get();
        if (dataSource != null) {
            // Clear the database.
            try (final Connection connection = dataSource.getConnection()) {
                DbTestUtil.clearAllTables(connection);
            } catch (final SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    private static void clearAllTables(final Connection connection) {
        final List<String> tables = new ArrayList<>();

        try (final PreparedStatement statement = connection.prepareStatement(FIND_TABLES)) {
            try (final ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String name = resultSet.getString(1);
                    tables.add(name);
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
        LOGGER.debug(() -> ">>> " + sqlStatements);
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
            LOGGER.error(() -> "executeStatement() - " + sqlStatements, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
