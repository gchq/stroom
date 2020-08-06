package stroom.test.common.util.db;

import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.HasDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.util.ConsoleColour;
import stroom.util.db.ForceCoreMigration;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.wix.mysql.EmbeddedMysql;
import com.wix.mysql.config.Charset;
import com.wix.mysql.config.DownloadConfig;
import com.wix.mysql.config.MysqldConfig;
import com.wix.mysql.config.SchemaConfig;
import com.wix.mysql.distribution.Version;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DbTestUtil {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbTestUtil.class);

    private static final String USE_EMBEDDED_MYSQL_PROP_NAME = "useEmbeddedMySql";
    private static final String EMBEDDED_MYSQL_DB_PASSWORD = "test";
    private static final String EMBEDDED_MYSQL_DB_USERNAME = "test";

    private static ConnectionConfig currentConfig;

    private static volatile boolean HAVE_ALREADY_SHOWN_DB_MSG = false;

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
            new EmbeddedDbDataSourceFactory(new CommonDbConfig()),
            new ForceCoreMigration() { });
    }

    public static synchronized ConnectionConfig getOrCreateEmbeddedConnectionConfig() {
        if (currentConfig == null) {
            currentConfig = createEmbeddedDbConfig();
        }

        return currentConfig;
    }

    static boolean isUseEmbeddedDb() {
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

    private static ConnectionConfig createEmbeddedDbConfig() {
        final ConnectionConfig connectionConfig = new ConnectionConfig();
        try {
            final String schemaName = "stroom";

            Path cacheDir = Paths.get(getClassContainer(DbTestUtil.class));

            LOGGER.info(LambdaLogUtil.message("Current class path = {}", cacheDir.toAbsolutePath().toString()));

            while (!cacheDir.getFileName().toString().equals("stroom-test-common")) {
                cacheDir = cacheDir.getParent();
            }
            cacheDir = cacheDir.getParent().resolve("embedmysql");
            cacheDir = cacheDir.toAbsolutePath();
            final Path destinationCacheDir = cacheDir;

            // If the cache directory doesn't exist then create a temp directory to download embedded MySQL into.
            // Doing this avoids conflicts with other test threads that might be trying to download MySQL.
            if (!Files.isDirectory(destinationCacheDir)) {
                cacheDir = Files.createTempDirectory("embedmysql");
            }

            LOGGER.info(LambdaLogUtil.message("Embedded MySQL dir = {}", cacheDir.toString()));

            final DownloadConfig downloadConfig = DownloadConfig.aDownloadConfig()
//                        .withProxy(aHttpProxy("remote.host", 8080))
                    .withCacheDir(cacheDir.toString())
                    .build();

            final MysqldConfig config = MysqldConfig.aMysqldConfig(Version.v5_5_52)
                    .withCharset(Charset.UTF8)
                    .withFreePort()
                    .withUser(EMBEDDED_MYSQL_DB_USERNAME, EMBEDDED_MYSQL_DB_PASSWORD)
//                        .withTimeZone("Europe/London")
                    .withTimeout(2, TimeUnit.MINUTES)
                    .withServerVariable("max_connect_errors", 666)
                    .build();

            final SchemaConfig schemaConfig = SchemaConfig.aSchemaConfig(schemaName).build();

            final EmbeddedMysql mysqld = EmbeddedMysql.anEmbeddedMysql(config, downloadConfig)
//                        .addSchema("aschema", ScriptResolver.classPathScript("db/001_init.sql"))
//                        .addSchema("aschema2", ScriptResolver.classPathScripts("db/*.sql"))
                    .addSchema(schemaConfig)
                    .start();

            // Copy download over to the cache dir so it is available for further use.
            if (!Files.isDirectory(destinationCacheDir)) {
                Files.move(cacheDir, destinationCacheDir);
            }

            final String url = "jdbc:mysql://localhost:" +
                    config.getPort() +
                    "/" +
                    schemaConfig.getName() +
                    "?useUnicode=yes&characterEncoding=UTF-8";

            connectionConfig.setJdbcDriverUrl(url);
            connectionConfig.setJdbcDriverUsername(config.getUsername());
            connectionConfig.setJdbcDriverPassword(config.getPassword());

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }

        return connectionConfig;
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
