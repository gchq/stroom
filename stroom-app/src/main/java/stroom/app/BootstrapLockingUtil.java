package stroom.app;

import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig.ClusterLockDbConfig;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.common.AbstractDbConfig;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.db.util.DbUtil;
import stroom.db.util.JooqUtil;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jetbrains.annotations.NotNull;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

public class BootstrapLockingUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BootstrapLockingUtil.class);

    private static final String BOOTSTRAP_LOCK_TABLE_NAME = "bootstrap_lock";
    private static final int BOOTSTRAP_LOCK_TABLE_ID = 1;
    private static final String INITIAL_VERSION = "NO_VERSION";

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
        final ConnectionConfig connectionConfig = getClusterLockConnectionConfig(config);

        // Wait till the DB is up
        DbUtil.waitForConnection(connectionConfig, "bootstrap-lock");

        ensureBootStrapLockTable(connectionConfig);

        T workOutput;

        try (Connection conn = DbUtil.getSingleConnection(connectionConfig)) {
            // Need read committed so that once we have acquired the lock we can see changes
            // committed by other nodes.
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            final DSLContext context = JooqUtil.createContext(conn);

            workOutput = context.transactionResult(txnConfig -> {
                String dbBuildVersion = getBuildVersionFromLockTable(txnConfig);
                boolean hasBootstrapBeenDone = dbBuildVersion.equals(buildVersion);

                final T output;
                if (hasBootstrapBeenDone) {
                    LOGGER.info("Found expected version {} in {} table, no lock required",
                            dbBuildVersion,
                            BOOTSTRAP_LOCK_TABLE_NAME);
                    output = work.get();
                } else {
                    LOGGER.info("Found old version {} in {} table.", dbBuildVersion, BOOTSTRAP_LOCK_TABLE_NAME);
                    acquireBootstrapLock(connectionConfig, txnConfig);

                    // We now hold a cluster wide lock
                    final Instant startTime = Instant.now();

                    // Re-check the lock table state now we are under lock
                    // For the first node these should be the same as when checked above
                    dbBuildVersion = getBuildVersionFromLockTable(txnConfig);
                    hasBootstrapBeenDone = dbBuildVersion.equals(buildVersion);

                    if (hasBootstrapBeenDone) {
                        // Another node has done the bootstrap so
                        LOGGER.info("Found expected version {} in {} table, releasing lock",
                                buildVersion, BOOTSTRAP_LOCK_TABLE_NAME);
                        // Rollback to release the row lock to allow other nodes to start checking
                        conn.rollback();
                    } else {
                        LOGGER.debug("dbBuildVersion: {}", dbBuildVersion);
                    }

                    // Now do the requested work (under lock if we are the first node for this build version)
                    output = work.get();

                    // If anything fails and we don't update/insert these it is fine, it will just get done on next
                    // successful boot
                    updateLockTableBuildVersion(buildVersion, txnConfig);

                    if (!hasBootstrapBeenDone) {
                        // We are the first node to get the lock for this build version so now release the lock
                        LOGGER.info(LogUtil.message("Completed work under bootstrap lock in {}. Releasing lock."
                                + Duration.between(startTime, Instant.now())));
                        // The row lock (if we have one) will be released as soon as we exit
                        // the try-with-resources block
                    }
                }
                return output;
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error obtaining bootstrap lock: " + e.getMessage(), e);
        }

        return workOutput;
    }

    private static void updateLockTableBuildVersion(final String buildVersion, final Configuration txnConfig) {
        LOGGER.info("Updating {} table with current build version: {}",
                BOOTSTRAP_LOCK_TABLE_NAME, buildVersion);
        DSL.using(txnConfig)
                .execute(LogUtil.message("""
                                UPDATE {}
                                SET build_version = ?
                                WHERE id = ?
                                """, BOOTSTRAP_LOCK_TABLE_NAME),
                        buildVersion, BOOTSTRAP_LOCK_TABLE_ID);
    }

    private static void releaseBootstrapLock(final Configuration txnConfig) {
        DSL.using(txnConfig)
                .execute("UNLOCK TABLES");
        LOGGER.info("Bootstrap lock released");
    }

    @NotNull
    private static String getBuildVersionFromLockTable(final Configuration txnConfig) {
        return DSL.using(txnConfig)
                .fetchOne(LogUtil.message("""
                                SELECT build_version
                                FROM {}
                                WHERE id = ?""", BOOTSTRAP_LOCK_TABLE_NAME),
                        BOOTSTRAP_LOCK_TABLE_ID)
                .get(0, String.class);
    }

    private static void acquireBootstrapLock(final ConnectionConfig connectionConfig,
                                             final Configuration txnConfig) {
        LOGGER.info("Waiting to acquire bootstrap lock on table: {}, user: {}, url: {}",
                BOOTSTRAP_LOCK_TABLE_NAME, connectionConfig.getUser(), connectionConfig.getUrl());
        Instant startTime = Instant.now();
//        DSL.using(txnConfig)
//                .execute(LogUtil.message("LOCK TABLES {} WRITE", BOOTSTRAP_LOCK_TABLE_NAME));

        final String sql = LogUtil.message("""
                SELECT *
                FROM {}
                WHERE id = ?
                FOR UPDATE""", BOOTSTRAP_LOCK_TABLE_NAME);
        DSL.using(txnConfig)
                .execute(sql, BOOTSTRAP_LOCK_TABLE_ID);

        LOGGER.info("Waited {} to acquire bootstrap lock",
                Duration.between(startTime, Instant.now()));
    }

    private static void ensureBootStrapLockTable(final ConnectionConfig connectionConfig) {
        try (Connection conn = DbUtil.getSingleConnection(connectionConfig)) {
            // Need read committed so that once we have acquired the lock we can see changes
            // committed by other nodes, e.g. we want to see if another node has inserted the
            // new record.
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            final DSLContext context = JooqUtil.createContext(conn);
            // Create a table that we can use to get a table lock on
            final String createTableSql = LogUtil.message("""
                            CREATE TABLE IF NOT EXISTS {} (
                                id INT NOT NULL,
                                build_version VARCHAR(255) NOT NULL,
                                PRIMARY KEY (id)
                            ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci""",
                    BOOTSTRAP_LOCK_TABLE_NAME);

            LOGGER.debug("Ensuring table {} exists", BOOTSTRAP_LOCK_TABLE_NAME);
            context
                    .execute(createTableSql);

            // Do a select first to avoid hitting the row lock with the insert stmt below
            final boolean isRecordPresent = context
                    .fetchOptional(LogUtil.message("""
                                    SELECT build_version
                                    FROM {}
                                    WHERE id = ?""", BOOTSTRAP_LOCK_TABLE_NAME),
                            BOOTSTRAP_LOCK_TABLE_ID)
                    .isPresent();

            if (!isRecordPresent) {
                // Ensure we have a record that we can get a lock on
                // Done like this in case another node gets in before us.
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
                        LIMIT 1""", BOOTSTRAP_LOCK_TABLE_NAME, BOOTSTRAP_LOCK_TABLE_NAME);

                context
                        .execute(
                                insertSql,
                                BOOTSTRAP_LOCK_TABLE_ID,
                                INITIAL_VERSION,
                                BOOTSTRAP_LOCK_TABLE_ID);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error ensuring table "
                    + BOOTSTRAP_LOCK_TABLE_NAME + ": "
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
