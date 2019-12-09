package stroom.test.common.util.db;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import com.wix.mysql.EmbeddedMysql;
import com.wix.mysql.config.Charset;
import com.wix.mysql.config.DownloadConfig;
import com.wix.mysql.config.MysqldConfig;
import com.wix.mysql.config.SchemaConfig;
import com.wix.mysql.distribution.Version;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.HasDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.HikariConfigHolder;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
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

    private static final String USE_TEST_CONTAINERS_ENV_VAR = "USE_TEST_CONTAINERS";
    private static final String TEST_CONTAINERS_DB_PASSWORD = "test";
    private static final String TEST_CONTAINERS_DB_USERNAME = "test";

    private static ConnectionConfig currentConfig;

    private DbTestUtil() {
    }

    /**
     * Gets aan embedded DB datasource for use in tests that don't use guice injection
     *
     */
    public static <T_Config extends HasDbConfig, T_ConnProvider extends DataSource> T_ConnProvider getTestDbDatasource(
            final AbstractFlyWayDbModule<T_Config, T_ConnProvider> dbModule,
            final T_Config config) {
        return dbModule.getConnectionProvider(() -> config, new EmbeddedDbHikariConfigHolder());
    }

    public static Injector overrideModuleWithTestDatabase(final AbstractModule sourceModule) {
        return Guice.createInjector(Modules.override(sourceModule)
                .with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        super.configure();
                        bind(HikariConfigHolder.class).toInstance(new EmbeddedDbHikariConfigHolder());
                    }
                }));
    }

    public static void applyEmbeddedDbConfig(final ConnectionConfig connectionConfig) {
        final ConnectionConfig config = getOrCreateConfig();
        if (config != null) {
            applyConfig(config, connectionConfig);
        }
    }

    public static void applyConfig(final ConnectionConfig from, final ConnectionConfig to) {
        to.setJdbcDriverClassName(from.getJdbcDriverClassName());
        to.setJdbcDriverUrl(from.getJdbcDriverUrl());
        to.setJdbcDriverUsername(from.getJdbcDriverUsername());
        to.setJdbcDriverPassword(from.getJdbcDriverPassword());
    }

    public static synchronized ConnectionConfig getOrCreateConfig() {
        if (currentConfig == null) {
            if (isUseEmbeddedDb()) {
                currentConfig = createEmbeddedDbConfig();
            }
        }

        return currentConfig;
    }

    public static boolean isUseEmbeddedDb() {
        String useTestContainersEnvVarVal = System.getenv(USE_TEST_CONTAINERS_ENV_VAR);
        // This env var allows a dev to use their own stroom-resources DB instead of test containers.
        // This is useful when debugging a test that needs to access the DB as test containers has to
        // migrate the DB from scratch on each run which is very time consuming
        return (useTestContainersEnvVarVal == null || !useTestContainersEnvVarVal.toLowerCase().equals("false"));
    }

    public static ConnectionConfig createEmbeddedDbConfig() {
        final ConnectionConfig connectionConfig = new ConnectionConfig();
        try {
            final String schemaName = "stroom";

            Path path = Paths.get(getClassContainer(DbTestUtil.class));

            LOGGER.info(LambdaLogUtil.message("Current class path = {}", path.toAbsolutePath().toString()));

            while (!path.getFileName().toString().equals("stroom-test-common")) {
                path = path.getParent();
            }
            path = path.getParent().resolve("embedmysql");
            path = path.toAbsolutePath();

            final String dir = path.toString();
            LOGGER.info(LambdaLogUtil.message("Embedded MySQL dir = {}", dir));

            final DownloadConfig downloadConfig = DownloadConfig.aDownloadConfig()
//                        .withProxy(aHttpProxy("remote.host", 8080))
                    .withCacheDir(dir)
                    .build();

            final MysqldConfig config = MysqldConfig.aMysqldConfig(Version.v5_5_52)
                    .withCharset(Charset.UTF8)
                    .withFreePort()
                    .withUser(TEST_CONTAINERS_DB_USERNAME, TEST_CONTAINERS_DB_PASSWORD)
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
