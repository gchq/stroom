package stroom.jooq.codegen;

import com.wix.mysql.EmbeddedMysql;
import com.wix.mysql.config.Charset;
import com.wix.mysql.config.DownloadConfig;
import com.wix.mysql.config.MysqldConfig;
import com.wix.mysql.distribution.Version;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Logging;
import org.jooq.meta.jaxb.Target;
import org.jooq.tools.jdbc.SingleConnectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

public class JooqGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JooqGenerator.class);

    private static final String DEFAULT_JDBC_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
    private static final String EMBEDDED_MYSQL_DB_PASSWORD = "test";
    private static final String EMBEDDED_MYSQL_DB_USERNAME = "test";
    private static volatile EmbeddedMysql EMBEDDED_MYSQL;

//    private static final String MODULE = "stroom-meta";
//    private static final String FLYWAY_LOCATIONS = "stroom/meta/impl/db/migration";
//    private static final String FLYWAY_TABLE = "meta_schema_history";

    private static class Config {

        private final String module;
        private final String flywayLocations;
        private final String flywayTable;
        private final String includes;
        private final String excludes;
        private final String packageName;
        private final String directory;

        public Config(final String module,
                      final String flywayLocations,
                      final String flywayTable,
                      final String includes,
                      final String excludes,
                      final String packageName,
                      final String directory) {
            this.module = module;
            this.flywayLocations = flywayLocations;
            this.flywayTable = flywayTable;
            this.includes = includes;
            this.excludes = excludes;
            this.packageName = packageName;
            this.directory = directory;
        }

        public static Config parse(final String[] args) throws ParseException {
            final Map<String, String> map = new HashMap<>();

            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("-")) {
                    String key = args[i].substring(1);
                    String value = null;
                    int valIndex = i + 1;
                    if (valIndex < args.length && !args[valIndex].startsWith("-")) {
                        value = args[valIndex];
                        i = valIndex;
                    }
                    map.put(key, value);
                } else {
                    throw new ParseException("Unexpected arg: " + args[i], -1);
                }
            }

            return new Config(
                    get(map, "module"),
                    get(map, "flywayLocations"),
                    get(map, "flywayTable"),
                    get(map, "includes"),
                    get(map, "excludes"),
                    get(map, "packageName"),
                    get(map, "directory"));
        }

        private static String get(final Map<String, String> map, final String key) throws ParseException {
            final String val = map.get(key);
            if (val == null) {
                throw new ParseException("Expected " + key + " argument", -1);
            }
            return val;
        }
    }

    public static void main(final String[] args) throws Exception {
        try {
            final Config config = Config.parse(args);
            final ConnectionConfig connectionConfig = createConnectionConfig();
            final Properties testConnectionProps = getConnectionProperties(connectionConfig);
            try (final Connection connection =
                    DriverManager.getConnection(connectionConfig.url(), testConnectionProps)) {
                LOGGER.info("GOT CONNECTION");
                final DataSource dataSource = new SingleConnectionDataSource(connection);
                FlywayUtil.migrate(dataSource, config.flywayLocations, config.flywayTable, config.module);
            }

            GenerationTool.generate(getJooqConfig(config, connectionConfig));

            EMBEDDED_MYSQL.stop();
        } catch (final ParseException e) {
            LOGGER.info("Usage = -module <MODULE> " +
                    "-flywayLocations <LOCATIONS> " +
                    "-flywayTable <FLYWAY_TABLE> " +
                    "-includes <INCLUDES> " +
                    "-excludes <EXCLUDES> " +
                    "-packageName <PACKAGE_NAME> " +
                    "-directory <DIRECTORY>");
            throw e;
        }
    }

    private static Configuration getJooqConfig(final Config config,
                                               final ConnectionConfig connectionConfig) {
        return new org.jooq.meta.jaxb.Configuration()
                .withLogging(Logging.INFO)
                // Configure the database connection here
                .withJdbc(new Jdbc()
                        .withDriver(connectionConfig.className())
                        .withUrl(connectionConfig.url())
                        .withUser(connectionConfig.user())
                        .withPassword(connectionConfig.password())
                )
                .withGenerator(new Generator()
                        .withName("org.jooq.codegen.JavaGenerator")
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.mysql.MySQLDatabase")
                                .withInputSchema(connectionConfig.dbName())
                                // Add anything you want included in generation below, whitespace ignored
                                // and comments allowed. Each one is a java regex
                                .withIncludes(config.includes)
                                // We don't want to include flyway versioning
                                .withExcludes(config.excludes)
                                // Specify 'version' for use in optimistic concurrency control
                                .withRecordVersionFields("version")
                        )
                        .withTarget(new Target()
                                .withPackageName(config.packageName)
                                .withDirectory(config.directory)
                        )
                );
    }

    public static ConnectionConfig createConnectionConfig() throws SQLException {
        final String dbName = "stroom";

        // Create a merged config using the common db config as a base.
        final ConnectionConfig connectionConfig = createEmbeddedMySqlInstance(dbName);
        final ConnectionConfig rootConnectionConfig = new ConnectionConfig(
                connectionConfig.className,
                connectionConfig.url,
                "root",
                "",
                connectionConfig.dbName);

        // Create new db.
        LOGGER.info("Connecting to DB as root connection with URL: " + rootConnectionConfig.url());

        final Properties rootConnectionProps = getConnectionProperties(rootConnectionConfig);
        try (final Connection connection = DriverManager.getConnection(rootConnectionConfig.url(),
                rootConnectionProps)) {
            try (final Statement statement = connection.createStatement()) {
                int result = 0;
                result = statement.executeUpdate("CREATE DATABASE `" + dbName +
                        "` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;");
                result = statement.executeUpdate("CREATE USER IF NOT EXISTS '" +
                        connectionConfig.user() + "'@'%' IDENTIFIED BY '" +
                        connectionConfig.password() + "';");
                result = statement.executeUpdate("GRANT ALL PRIVILEGES ON *.* TO '" +
                        connectionConfig.user() + "'@'%' WITH GRANT OPTION;");
            }
        }

        // Create a URL for connecting to the new DB.
        final String url = DbUrl
                .builder()
                .parse(rootConnectionConfig.url())
                .dbName(dbName)
                .build()
                .toString();

        return new ConnectionConfig(
                connectionConfig.className,
                url,
                connectionConfig.user,
                connectionConfig.password,
                connectionConfig.dbName);
    }

    private static Properties getConnectionProperties(final ConnectionConfig connectionConfig) {
        final Properties properties = new Properties();
        properties.put("user", connectionConfig.user());
        properties.put("password", connectionConfig.password());
        return properties;
    }

    public static ConnectionConfig createEmbeddedMySqlInstance(final String dbName) {
        EmbeddedMysql embeddedMysql = EMBEDDED_MYSQL;
        if (embeddedMysql == null) {
            embeddedMysql = createEmbeddedMysql();
        }

        final MysqldConfig mysqlConfig = embeddedMysql.getConfig();
        final String url = DbUrl
                .builder()
                .port(mysqlConfig.getPort())
                .query("useUnicode=yes&characterEncoding=UTF-8")
                .build()
                .toString();

        return new ConnectionConfig(
                DEFAULT_JDBC_DRIVER_CLASS_NAME,
                url,
                mysqlConfig.getUsername(),
                mysqlConfig.getPassword(),
                dbName);
    }

    private static synchronized EmbeddedMysql createEmbeddedMysql() {
        final String systemTempDir = System.getProperty("java.io.tmpdir");
        final Path parentDir = Paths.get(systemTempDir);
        final Path lockFile = parentDir.resolve("embedmysql.lock");
        final Path cacheDir = parentDir.resolve("embedmysql");

        EmbeddedMysql embeddedMysql = EMBEDDED_MYSQL;

        if (embeddedMysql == null) {
            embeddedMysql = FileUtil.getUnderFileLock(lockFile, () -> {
                try {
                    return doCreateEmbeddedMysql(cacheDir);
                } catch (Exception e) {
                    throw new RuntimeException("Error creating embedded mysql", e);
                }
            });
        }

        EMBEDDED_MYSQL = embeddedMysql;

        return embeddedMysql;
    }

    private static EmbeddedMysql doCreateEmbeddedMysql(final Path cacheDir) {
        try {
            final Path tempDir = Files.createTempDirectory("embedmysql_");

            LOGGER.info("Embedded MySQL cache dir = " + cacheDir.toString());
            LOGGER.info("Embedded MySQL temp dir = " + tempDir.toString());

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

            return EmbeddedMysql
                    .anEmbeddedMysql(config, downloadConfig)
                    .start();

        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    private record ConnectionConfig(String className, String url, String user, String password, String dbName) {

    }
}
