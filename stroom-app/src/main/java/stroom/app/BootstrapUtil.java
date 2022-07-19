package stroom.app;

import stroom.app.guice.BootStrapModule;
import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig.ClusterLockDbConfig;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.common.AbstractDbConfig;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.db.util.DataSourceProxy;
import stroom.db.util.DbUtil;
import stroom.db.util.JooqUtil;
import stroom.util.BuildInfoProvider;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.db.DbMigrationState;
import stroom.util.guice.GuiceUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException;
import io.dropwizard.setup.Environment;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;

public class BootstrapUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BootstrapUtil.class);

    private static final String BUILD_VERSION_TABLE_NAME = "build_version";
    private static final int BUILD_VERSION_TABLE_ID = 1;
    private static final String INITIAL_VERSION = "UNKNOWN_VERSION";
    private static final String SNAPSHOT_VERSION = "SNAPSHOT";

    private BootstrapUtil() {
    }

    /**
     * Creates an injector with the bare minimum to initialise the DB connections and configuration.
     * The initialisation of the DB datasources will trigger the FlyWay DB migration to run.
     * You should call {@link Injector#createChildInjector} to build a fully formed injector from it.
     */
    public static Injector bootstrapApplication(final Config configuration,
                                                final Environment environment,
                                                final Path configFile) {
        return bootstrapApplication(
                configuration,
                () -> new BootStrapModule(configuration, environment, configFile));
    }

    /**
     * Creates an injector with the bare minimum to initialise the DB connections and configuration.
     * The initialisation of the DB datasources will trigger the FlyWay DB migration to run.
     * You should call {@link Injector#createChildInjector} to build a fully formed injector from it.
     */
    public static Injector bootstrapApplication(final Config configuration,
                                                final Path configFile) {
        return bootstrapApplication(
                configuration,
                () -> new BootStrapModule(configuration, configFile));
    }

    private static Injector bootstrapApplication(
            final Config configuration,
            final Supplier<BootStrapModule> bootStrapModuleSupplier) {

        Objects.requireNonNull(configuration);
        Objects.requireNonNull(bootStrapModuleSupplier);

        // Create a minimalist injector with just the BuildInfo so we can determine
        // what version the system is at and what we need to do before creating
        // the bootstrap injector
        final BuildInfo buildInfo = BuildInfoProvider.getBuildInfo();
        showBuildInfo(buildInfo);

        // In dev the build ver will always be SNAPSHOT so append the now time to make
        // it different to force the migrations to always run in dev.
        String buildVersion = Objects.requireNonNullElse(buildInfo.getBuildVersion(), SNAPSHOT_VERSION);
        buildVersion = SNAPSHOT_VERSION.equals(buildVersion)
                ? SNAPSHOT_VERSION + "_" + DateUtil.createNormalDateTimeString()
                : buildVersion;

        LOGGER.debug("buildVersion: '{}'", buildVersion);

        return BootstrapUtil.doWithBootstrapLock(
                configuration,
                buildVersion, () -> {
                    LOGGER.info("Initialising database connections and configuration properties");

                    final BootStrapModule bootstrapModule = bootStrapModuleSupplier.get();

                    final Injector injector = Guice.createInjector(bootstrapModule);

                    // Force all data sources to be created so we can force migrations to run.
                    final Set<DataSource> dataSources = injector.getInstance(
                            Key.get(GuiceUtil.setOf(DataSource.class)));

                    LOGGER.debug(() -> LogUtil.message("Used {} data sources:\n{}",
                            dataSources.size(),
                            dataSources.stream()
                                    .map(dataSource -> {
                                        final String prefix = dataSource instanceof DataSourceProxy
                                                ? ((DataSourceProxy) dataSource).getName() + " - "
                                                : "";
                                        return prefix + dataSource.getClass().getName();
                                    })
                                    .map(name -> "  " + name)
                                    .sorted()
                                    .collect(Collectors.joining("\n"))));

                    LOGGER.info("""

                            -----------------------------------------------------------
                              Completed database migrations
                            -----------------------------------------------------------""");

                    return injector;
                });
    }

    private static void showBuildInfo(final BuildInfo buildInfo) {
        Objects.requireNonNull(buildInfo);
        LOGGER.info(""
                + "\n********************************************************************************"
                + "\n  Build version: " + buildInfo.getBuildVersion()
                + "\n  Build date:    " + DateUtil.createNormalDateTimeString(buildInfo.getBuildTime())
                + "\n********************************************************************************");
    }

    /**
     * Use a table lock on a noddy table created just for this purpose to enforce
     * a cluster wide lock so we can do all the flyway migrations in isolation.
     * Flyway migrations should work under lock but we have lots of independent flyway
     * migrations so it is safer to do it this way.
     * Can't user the cluster lock service as that is not a thing at this point.
     */
    static <T> T doWithBootstrapLock(final Config config,
                                     final String buildVersion,
                                     final Supplier<T> work) {

        Objects.requireNonNull(work);
        // We need to use one of the
        final ConnectionConfig connectionConfig = getClusterLockConnectionConfig(config);

        DbUtil.waitForConnection(connectionConfig);

        final AtomicBoolean doneWork = new AtomicBoolean(false);
        T workOutput;

        try (Connection conn = DbUtil.getSingleConnection(connectionConfig)) {
            // Need read committed so that once we have acquired the lock we can see changes
            // committed by other nodes.
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            final DSLContext context = JooqUtil.createContext(conn);

            workOutput = context.transactionResult(txnConfig -> {
                // make sure we have a populated build_version table to check and lock on
                ensureBuildVersionTable(txnConfig, conn);

                String dbBuildVersion = getBuildVersionFromDb(txnConfig);
                boolean isDbBuildVersionUpToDate = dbBuildVersion.equals(buildVersion);

                T output = null;
                if (isDbBuildVersionUpToDate) {
                    LOGGER.info("Found required build version '{}' in {} table, no lock or DB migration required.",
                            dbBuildVersion,
                            BUILD_VERSION_TABLE_NAME);
                } else {
                    LOGGER.info("Found old build version '{}' in {} table. Bootstrap lock and DB migration required.",
                            dbBuildVersion, BUILD_VERSION_TABLE_NAME);
                    acquireBootstrapLock(connectionConfig, txnConfig);

                    // We now hold a cluster wide lock
                    final Instant startTime = Instant.now();

                    // Re-check the lock table state now we are under lock
                    // For the first node these should be the same as when checked above
                    dbBuildVersion = getBuildVersionFromDb(txnConfig);
                    isDbBuildVersionUpToDate = dbBuildVersion.equals(buildVersion);

                    if (isDbBuildVersionUpToDate) {
                        // Another node has done the bootstrap so we can just drop out of the txn/connection to
                        // free up the lock
                        LOGGER.info("Found required build version '{}' in {} table, releasing lock. " +
                                        "No DB migration required.",
                                buildVersion, BUILD_VERSION_TABLE_NAME);
                    } else {
                        LOGGER.info("Upgrading stroom from '{}' to '{}' under lock", dbBuildVersion, buildVersion);

                        // We hold the lock and the db version is out of date so perform work under lock
                        // including doing all the flyway migrations
                        output = work.get();
                        doneWork.set(true);

                        // If anything fails and we don't update/insert these it is fine, it will just
                        // get done on next successful boot
                        updateDbBuildVersion(buildVersion, txnConfig);
                    }
                    // We are the first node to get the lock for this build version so now release the lock
                    LOGGER.info(LogUtil.message("Releasing bootstrap lock after {}",
                            Duration.between(startTime, Instant.now())));
                }
                // Set local state so the db modules know not to run flyway when work.get() is called
                // below
                DbMigrationState.markBootstrapMigrationsComplete();
                return output;
            });
            LOGGER.debug("Closed connection");
        } catch (SQLException e) {
            throw new RuntimeException("Error obtaining bootstrap lock: " + e.getMessage(), e);
        }

        // If we didn't do it under lock then we need to do it now
        if (!doneWork.get()) {
            workOutput = work.get();
        }
        return workOutput;
    }

    private static void updateDbBuildVersion(final String buildVersion,
                                             final Configuration txnConfig) {
        LOGGER.info("Updating {} table with current build version: {}",
                BUILD_VERSION_TABLE_NAME, buildVersion);
        DSL.using(txnConfig)
                .execute(LogUtil.message("""
                                UPDATE {}
                                SET build_version = ?
                                WHERE id = ?
                                """, BUILD_VERSION_TABLE_NAME),
                        buildVersion, BUILD_VERSION_TABLE_ID);
    }

    private static String getBuildVersionFromDb(final Configuration txnConfig) {
        return DSL.using(txnConfig)
                .fetchOne(LogUtil.message("""
                                SELECT build_version
                                FROM {}
                                WHERE id = ?""", BUILD_VERSION_TABLE_NAME),
                        BUILD_VERSION_TABLE_ID)
                .get(0, String.class);
    }

    private static void acquireBootstrapLock(final ConnectionConfig connectionConfig,
                                             final Configuration txnConfig) {
        LOGGER.info("Waiting to acquire bootstrap lock on table: {}, id: {}, user: {}, url: {}",
                BUILD_VERSION_TABLE_NAME,
                BUILD_VERSION_TABLE_ID,
                connectionConfig.getUser(),
                connectionConfig.getUrl());
        final Instant startTime = Instant.now();

        final String sql = LogUtil.message("""
                SELECT *
                FROM {}
                WHERE id = ?
                FOR UPDATE""", BUILD_VERSION_TABLE_NAME);

        boolean acquiredLock = false;
        while (!acquiredLock) {
            try {
                // Wait to get a row lock on the one record in the table
                DSL.using(txnConfig)
                        .execute(sql, BUILD_VERSION_TABLE_ID);
                LOGGER.info("Waited {} to acquire bootstrap lock",
                        Duration.between(startTime, Instant.now()));
                acquiredLock = true;
            } catch (Exception e) {
                // If the node that gets the lock has to run lengthy db= migrations it is almost certain
                // that we will get a lock timeout error so need to handle that and keep trying to get the lock
                if (e.getCause() != null
                        && e.getCause() instanceof MySQLTransactionRollbackException
                        && e.getCause().getMessage().contains("Lock wait timeout exceeded")) {
                    LOGGER.info("Still waiting for bootstrap lock, waited {} so far.",
                            Duration.between(startTime, Instant.now()));
                } else {
                    LOGGER.error("Error getting bootstrap lock: {}", e.getMessage(), e);
                    throw e;
                }
            }
        }
    }

    private static void ensureBuildVersionTable(final Configuration txnConfig, final Connection connection) {
        try {
            // Need read committed so that once we have acquired the lock we can see changes
            // committed by other nodes, e.g. we want to see if another node has inserted the
            // new record.
            // Create a table that we can use to get a table lock on
            final String createTableSql = LogUtil.message("""
                            CREATE TABLE IF NOT EXISTS {} (
                                id INT NOT NULL,
                                build_version VARCHAR(255) NOT NULL,
                                PRIMARY KEY (id)
                            ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci""",
                    BUILD_VERSION_TABLE_NAME);

            LOGGER.debug("Ensuring table {} exists", BUILD_VERSION_TABLE_NAME);
            // Causes implicit commit
            DSL.using(txnConfig)
                    .execute(createTableSql);

            // Do a select first to avoid being blocked by the row lock with the insert stmt below
            final boolean isRecordPresent = DSL.using(txnConfig)
                    .fetchOptional(LogUtil.message("""
                                    SELECT build_version
                                    FROM {}
                                    WHERE id = ?""", BUILD_VERSION_TABLE_NAME),
                            BUILD_VERSION_TABLE_ID)
                    .isPresent();

            if (!isRecordPresent) {
                // Ensure we have a record that we can get a lock on
                // Done like this in case another node gets in before us.
                // This may block if another node beat us to it and locked the row but
                // that is fine.
                final String insertSql = LogUtil.message("""
                                INSERT INTO {} (
                                    id,
                                    build_version)
                                SELECT ?, ?
                                FROM DUAL
                                WHERE NOT EXISTS (
                                    SELECT NULL
                                    FROM {}
                                    WHERE ID = ?)
                                LIMIT 1""",
                        BUILD_VERSION_TABLE_NAME,
                        BUILD_VERSION_TABLE_NAME);

                final int result = DSL.using(txnConfig)
                        .execute(insertSql,
                                BUILD_VERSION_TABLE_ID,
                                INITIAL_VERSION,
                                BUILD_VERSION_TABLE_ID);
                // Make sure other nodes can see it
                if (result > 0) {
                    LOGGER.info("Committing new {} row", BUILD_VERSION_TABLE_NAME);
                    connection.commit();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error ensuring table "
                    + BUILD_VERSION_TABLE_NAME + ": "
                    + e.getMessage(), e);
        }
    }

    private static ConnectionConfig getClusterLockConnectionConfig(final Config config) {
        final CommonDbConfig yamlCommonDbConfig = Objects.requireNonNullElse(
                config.getYamlAppConfig().getCommonDbConfig(),
                new CommonDbConfig());
        final ClusterLockDbConfig yamlClusterLockDbConfig = NullSafe.getAsOptional(
                config.getYamlAppConfig(),
                AppConfig::getClusterLockConfig,
                ClusterLockConfig::getDbConfig)
                .orElse(new ClusterLockDbConfig());

        final AbstractDbConfig mergedClusterLockDbConfig = yamlCommonDbConfig.mergeConfig(yamlClusterLockDbConfig);
        final ConnectionConfig connectionConfig = mergedClusterLockDbConfig.getConnectionConfig();

        LOGGER.debug(() -> LogUtil.message("Using connection user: {}, url: {}, class: {}",
                connectionConfig.getUser(),
                connectionConfig.getUrl(),
                connectionConfig.getClassName()));

        DbUtil.validate(connectionConfig);

        return connectionConfig;
    }
}
