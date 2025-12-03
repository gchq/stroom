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
import stroom.node.impl.NodeConfig;
import stroom.util.BuildInfoProvider;
import stroom.util.date.DateUtil;
import stroom.util.db.DbMigrationState;
import stroom.util.guice.GuiceUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.NullSafe;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException;
import io.dropwizard.core.setup.Environment;
import jakarta.validation.constraints.NotNull;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;

public class BootstrapUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BootstrapUtil.class);

    private static final String BUILD_VERSION_TABLE_NAME = "build_version";
    private static final String BUILD_VERSION_COL_NAME = "build_version";
    private static final String ID_COL_NAME = "id";
    private static final String OUTCOME_COL_NAME = "outcome";
    private static final String EXECUTING_NODE_COL_NAME = "executing_node";
    private static final List<String> ALL_COLUMNS = List.of(
            ID_COL_NAME,
            BUILD_VERSION_COL_NAME,
            EXECUTING_NODE_COL_NAME,
            OUTCOME_COL_NAME);

    private static final int BUILD_VERSION_TABLE_ROW_ID = 1;
    private static final String INITIAL_BUILD_VERSION = "UNKNOWN_VERSION";
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
                () -> new BootStrapModule(configuration, new Environment("Dummy Environment"), configFile));
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
        // Comment out this statement if you want dev to behave like prod and respect the version value
        // but you will need to clear the build version in the db to make a mig happen
        buildVersion = SNAPSHOT_VERSION.equals(buildVersion)
                ? SNAPSHOT_VERSION + "_" + DateUtil.createNormalDateTimeString()
                : buildVersion;

        LOGGER.debug("buildVersion: '{}'", buildVersion);

        LOGGER.debug(() -> LogUtil.message("node: {}", NullSafe.getOrElse(
                configuration,
                Config::getYamlAppConfig,
                AppConfig::getNodeConfig,
                NodeConfig::getNodeName,
                "UNKNOWN NODE")));

        return BootstrapUtil.doWithBootstrapLock(
                configuration,
                buildVersion, () -> {
                    LOGGER.info("Initialising database connections and configuration properties");

                    final BootStrapModule bootstrapModule = bootStrapModuleSupplier.get();

                    // This will trigger the migrations to happen as part of the guice
                    // binding initialisation
                    final Injector injector = Guice.createInjector(bootstrapModule);

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

                    return injector;
                });
    }

    private static void showBuildInfo(final BuildInfo buildInfo) {
        Objects.requireNonNull(buildInfo);
        LOGGER.info(LogUtil.inBoxOnNewLine(
                """
                        Build version: {}
                        Build date:    {}
                        """,
                buildInfo.getBuildVersion(),
                DateUtil.createNormalDateTimeString(buildInfo.getBuildTime())));
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

        final String thisNodeName = NullSafe.getOrElse(
                config,
                Config::getYamlAppConfig,
                AppConfig::getNodeConfig,
                NodeConfig::getNodeName,
                "UNKNOWN NODE");

        try (final Connection conn = DbUtil.getSingleConnection(connectionConfig)) {
            // Need read committed so that once we have acquired the lock we can see changes
            // committed by other nodes.
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            final DSLContext context = JooqUtil.createContext(conn);

            workOutput = context.transactionResult(txnConfig -> {
                // make sure we have a populated build_version table to check and lock on
                ensureBuildVersionTable(txnConfig, conn, thisNodeName);

                BootstrapInfo bootstrapInfo = getBootstrapInfoFromDb(txnConfig);

                T output = null;
                if (!isUpgradeRequired(bootstrapInfo, buildVersion)) {
                    handleCorrectBuildVersion(bootstrapInfo, "no lock or DB migration required.");
                    // Set local state so the db modules know not to run flyway when work.get() is called
                    // below
                    DbMigrationState.markBootstrapMigrationsComplete();
                } else {
                    if (bootstrapInfo.getUpgradeOutcome().isPresent()) {
                        LOGGER.info("Found old build version '{}' in table '{}' with upgrade outcome {}. " +
                                    "Bootstrap lock required to upgrade the version.",
                                bootstrapInfo.getBuildVersion(),
                                BUILD_VERSION_TABLE_NAME,
                                bootstrapInfo.getUpgradeOutcome().get());
                    } else {
                        LOGGER.info("Found build version '{}' in table '{}' with empty upgrade outcome. " +
                                    "Bootstrap lock required to re-run the version upgrade.",
                                bootstrapInfo.getBuildVersion(),
                                BUILD_VERSION_TABLE_NAME);
                    }
                    acquireBootstrapLock(connectionConfig, txnConfig);

                    // We now hold a cluster wide lock
                    final Instant startTime = Instant.now();

                    // Re-check the lock table state now we are under lock
                    // For the first node these should be the same as when checked above
                    bootstrapInfo = getBootstrapInfoFromDb(txnConfig);

                    if (!isUpgradeRequired(bootstrapInfo, buildVersion)) {
                        handleCorrectBuildVersion(bootstrapInfo, "releasing lock. No DB migration required.");
                        // Set local state so the db modules know not to run flyway when work.get() is called
                        // below
                        DbMigrationState.markBootstrapMigrationsComplete();
                    } else {
                        if (Objects.equals(bootstrapInfo.getBuildVersion(), buildVersion)) {
                            LOGGER.info("Re-running the upgrade of stroom to version '{}' under cluster wide lock",
                                    buildVersion);
                        } else {
                            LOGGER.info("Upgrading stroom from '{}' to '{}' under cluster wide lock",
                                    bootstrapInfo.getBuildVersion(), buildVersion);
                        }

                        // We hold the lock and the db version is out of date so perform work under lock
                        // including doing all the flyway migrations
                        try {
                            output = work.get();
                        } catch (final Exception e) {
                            final String msg = LogUtil.message(
                                    "Error upgrading stroom to {}: {}", buildVersion, e.getMessage(), e);
                            LOGGER.error(msg);
                            // Failure in the migration so need to indicate to other nodes that it has failed
                            // so they can stop
                            updateDbBuildVersion(txnConfig, buildVersion, thisNodeName, UpgradeOutcome.FAILED);
                            LOGGER.info(LogUtil.message("Releasing bootstrap lock after {}",
                                    Duration.between(startTime, Instant.now())));
                            // This will release the bootstrap lock
                            conn.commit();
                            throw new BootstrapFailureException(msg, e);
                        }
                        doneWork.set(true);

                        LOGGER.info(LogUtil.inBoxOnNewLine("Completed all database migrations"));

                        // If anything fails and we don't update/insert these it is fine, it will just
                        // get done on next successful boot
                        updateDbBuildVersion(txnConfig, buildVersion, thisNodeName, UpgradeOutcome.SUCCESS);
                        // Set local state so the db modules know not to run flyway when work.get() is called
                        // below
                        DbMigrationState.markBootstrapMigrationsComplete();
                    }
                    // We are the first node to get the lock for this build version so now release the lock
                    LOGGER.info(LogUtil.message("Releasing bootstrap lock after {}",
                            Duration.between(startTime, Instant.now())));
                }
                return output;
            });
            LOGGER.debug("Closed connection");
        } catch (final SQLException e) {
            throw new RuntimeException("Error obtaining bootstrap lock: " + e.getMessage(), e);
        }

        // We didn't execute work under lock, i.e. the db was found to be at the right version, but
        // we still need to set up all the db modules, but each node can now do it concurrently.
        // Flyway will discover that each db module is at the right version so won't do anything either.
        if (!doneWork.get()) {
            workOutput = work.get();
        }
        return workOutput;
    }

    private static boolean isUpgradeRequired(final BootstrapInfo bootstrapInfo,
                                             final String buildVersion) {
        // Only need to upgrade if the build ver in the db is not the same as the
        // ver being run.
        // Also, empty outcome means we haven't yet attempted to boot with this version
        // or, it has been cleared by an admin after a failed boot.
        return !Objects.equals(bootstrapInfo.getBuildVersion(), buildVersion)
               || bootstrapInfo.getUpgradeOutcome().isEmpty();
    }

    private static void handleCorrectBuildVersion(final BootstrapInfo bootstrapInfo,
                                                  final String msgSuffix) {
        final UpgradeOutcome upgradeOutcome = bootstrapInfo.getUpgradeOutcome()
                .orElseThrow(() -> new RuntimeException("Null upgradeOutcome, should not have got here."));
        switch (upgradeOutcome) {
            case SUCCESS -> {
                LOGGER.info("Found required build version '{}' in table '{}' with upgrade outcome {}, " +
                            msgSuffix,
                        bootstrapInfo.getBuildVersion(),
                        BUILD_VERSION_TABLE_NAME,
                        upgradeOutcome);

                // Quickly drop out of the lock so the other waiting nodes can find this out.
            }
            case FAILED -> {
                // Another node has failed when upgrading to this ver
                logFailureAndThrow(bootstrapInfo);
            }
            default -> throw new RuntimeException("Null upgradeOutcome, should never happen");
        }
    }

    private static void logFailureAndThrow(final BootstrapInfo bootstrapInfo) {
        final String outcome = bootstrapInfo.getUpgradeOutcome()
                .map(UpgradeOutcome::toString)
                .orElse("empty");
        final String msg = LogUtil.message("Build version '{}' has an upgrade outcome of {} on node {}. " +
                                           "Either deploy a new version; or fix the issue, set column {}.{} to null " +
                                           "then re-try this version. Aborting application startup.",
                bootstrapInfo.getBuildVersion(),
                outcome,
                bootstrapInfo.getExecutingNode(),
                BUILD_VERSION_TABLE_NAME,
                OUTCOME_COL_NAME);

        LOGGER.error(msg);
        throw new BootstrapFailureException(msg);
    }

    private static void updateDbBuildVersion(final Configuration txnConfig,
                                             final String buildVersion,
                                             final String thisNodeName,
                                             final UpgradeOutcome upgradeOutcome) {
        LOGGER.info("Updating {} table with current build version: {} and outcome: {}",
                BUILD_VERSION_TABLE_NAME, buildVersion, upgradeOutcome);
        DSL.using(txnConfig)
                .execute(LogUtil.message("""
                                        UPDATE {}
                                        SET
                                          {} = ?,
                                          {} = ?,
                                          {} = ?
                                        WHERE id = ?
                                        """,
                                BUILD_VERSION_TABLE_NAME,
                                BUILD_VERSION_COL_NAME,
                                EXECUTING_NODE_COL_NAME,
                                OUTCOME_COL_NAME),
                        buildVersion, thisNodeName, upgradeOutcome.toString(), BUILD_VERSION_TABLE_ROW_ID);
    }

    @NotNull
    private static BootstrapInfo getBootstrapInfoFromDb(final Configuration txnConfig) {
        return DSL.using(txnConfig)
                .fetchOptional(LogUtil.message("""
                                        SELECT {}, {}, {}
                                        FROM {}
                                        WHERE id = ?""",
                                BUILD_VERSION_COL_NAME,
                                EXECUTING_NODE_COL_NAME,
                                OUTCOME_COL_NAME,
                                BUILD_VERSION_TABLE_NAME),
                        BUILD_VERSION_TABLE_ROW_ID)
                .map(record -> {
                    final String outcomeStr = record.get(OUTCOME_COL_NAME, String.class);
                    final UpgradeOutcome upgradeOutcome = outcomeStr != null
                            ? UpgradeOutcome.valueOf(outcomeStr)
                            : null;
                    return new BootstrapInfo(
                            record.get(BUILD_VERSION_COL_NAME, String.class),
                            record.get(EXECUTING_NODE_COL_NAME, String.class),
                            upgradeOutcome);
                })
                .orElseThrow(() ->
                        new RuntimeException(LogUtil.message("Row with id {} not found in {}",
                                BUILD_VERSION_TABLE_ROW_ID, BUILD_VERSION_TABLE_NAME)));
    }

    private static void acquireBootstrapLock(final ConnectionConfig connectionConfig,
                                             final Configuration txnConfig) {
        LOGGER.info("Waiting to acquire bootstrap lock on table: {}, id: {}, user: {}, url: {}",
                BUILD_VERSION_TABLE_NAME,
                BUILD_VERSION_TABLE_ROW_ID,
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
                        .execute(sql, BUILD_VERSION_TABLE_ROW_ID);
                LOGGER.info("Waited {} to acquire bootstrap lock",
                        Duration.between(startTime, Instant.now()));
                acquiredLock = true;
            } catch (final Exception e) {
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

    private static void ensureBuildVersionTable(final Configuration txnConfig,
                                                final Connection connection,
                                                final String thisNodeName) {
        try {
            // Try to create it in case this is an old version without it
            createBuildVersionTableIfNotExists(txnConfig);

            // Now check whatever flavour of the table is there has the right cols
            final String listColsSql = """
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_schema = schema()
                    AND table_name = ?""";

            final Set<Object> columnNames = DSL.using(txnConfig)
                    .fetch(listColsSql, BUILD_VERSION_TABLE_NAME)
                    .stream()
                    .map(record -> record.get(0))
                    .collect(Collectors.toSet());

            if (!columnNames.containsAll(ALL_COLUMNS)) {
                // This is an old version of the table, so we need to re-create it with the right cols

                // Another node may have already dropped it so allow for it to not exist
                final String dropTableSql = LogUtil.message("""
                                DROP TABLE IF EXISTS {}""",
                        BUILD_VERSION_TABLE_NAME);
                // Causes implicit commit
                DSL.using(txnConfig)
                        .execute(dropTableSql);

                // Another node my beat us to this
                createBuildVersionTableIfNotExists(txnConfig);
            }

            // Do a select first to avoid being blocked by the row lock with the insert stmt below
            final boolean isRecordPresent = DSL.using(txnConfig)
                    .fetchOptional(LogUtil.message("""
                                            SELECT {}
                                            FROM {}
                                            WHERE id = ?""",
                                    BUILD_VERSION_COL_NAME,
                                    BUILD_VERSION_TABLE_NAME),
                            BUILD_VERSION_TABLE_ROW_ID)
                    .isPresent();

            if (!isRecordPresent) {
                // Ensure we have a record that we can get a lock on
                // Done like this in case another node gets in before us.
                // This may block if another node beat us to it and locked the row but
                // that is fine.
                final String insertSql = LogUtil.message("""
                                INSERT INTO {} (
                                    {},
                                    {},
                                    {})
                                SELECT ?, ?, ?
                                FROM DUAL
                                WHERE NOT EXISTS (
                                    SELECT NULL
                                    FROM {}
                                    WHERE ID = ?)
                                LIMIT 1""",
                        BUILD_VERSION_TABLE_NAME,
                        ID_COL_NAME,
                        BUILD_VERSION_COL_NAME,
                        EXECUTING_NODE_COL_NAME,
                        BUILD_VERSION_TABLE_NAME);

                final int result = DSL.using(txnConfig)
                        .execute(insertSql,
                                BUILD_VERSION_TABLE_ROW_ID,
                                INITIAL_BUILD_VERSION,
                                thisNodeName,
                                BUILD_VERSION_TABLE_ROW_ID);
                // Make sure other nodes can see it
                if (result > 0) {
                    LOGGER.info("Committing new {} row", BUILD_VERSION_TABLE_NAME);
                    connection.commit();
                }
            }
        } catch (final Exception e) {
            throw new RuntimeException("Error ensuring table "
                                       + BUILD_VERSION_TABLE_NAME + ": "
                                       + e.getMessage(), e);
        }
    }

    private static void createBuildVersionTableIfNotExists(final Configuration txnConfig) {
        // Need read committed so that once we have acquired the lock we can see changes
        // committed by other nodes, e.g. we want to see if another node has inserted the
        // new record.
        // Create a table that we can use to get a table lock on
        final String createTableSql = LogUtil.message("""
                        CREATE TABLE IF NOT EXISTS {} (
                            {} INT NOT NULL,
                            {} VARCHAR(255) NOT NULL,
                            {} VARCHAR(255) NOT NULL,
                            {} VARCHAR(10),
                            PRIMARY KEY (id)
                        ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci""",
                BUILD_VERSION_TABLE_NAME,
                ID_COL_NAME,
                BUILD_VERSION_COL_NAME,
                EXECUTING_NODE_COL_NAME,
                OUTCOME_COL_NAME);

        LOGGER.debug("Ensuring table {} exists", BUILD_VERSION_TABLE_NAME);
        // Causes implicit commit
        DSL.using(txnConfig)
                .execute(createTableSql);
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


    // --------------------------------------------------------------------------------


    private static class BootstrapInfo {

        private final String buildVersion;
        private final String executingNode;
        private final UpgradeOutcome upgradeOutcome;

        public BootstrapInfo(final String buildVersion,
                             final String executingNode,
                             final UpgradeOutcome upgradeOutcome) {
            this.buildVersion = buildVersion;
            this.executingNode = executingNode;
            this.upgradeOutcome = upgradeOutcome;
        }

        public String getBuildVersion() {
            return buildVersion;
        }

        public String getExecutingNode() {
            return executingNode;
        }

        public Optional<UpgradeOutcome> getUpgradeOutcome() {
            return Optional.ofNullable(upgradeOutcome);
        }

        public boolean isSuccess() {
            return UpgradeOutcome.SUCCESS.equals(upgradeOutcome);
        }

        public boolean isFailed() {
            return UpgradeOutcome.FAILED.equals(upgradeOutcome);
        }

        public boolean hasOutcome() {
            return upgradeOutcome != null;
        }

        @Override
        public String toString() {
            return "BootstrapInfo{" +
                   "buildVersion='" + buildVersion + '\'' +
                   ", executingNode='" + executingNode + '\'' +
                   ", upgradeOutcome=" + upgradeOutcome +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    private enum UpgradeOutcome {
        SUCCESS,
        FAILED
    }


    // --------------------------------------------------------------------------------


    public static class BootstrapFailureException extends RuntimeException {

        public BootstrapFailureException(final String message) {
            super(message);
        }

        public BootstrapFailureException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
