package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.ProxyRepoDbConnProvider;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;
import stroom.util.shared.ModelStringUtil;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.sqlite.SQLiteException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardDest.FORWARD_DEST;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class SqliteJooqHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqliteJooqHelper.class);

    private static final Boolean RENDER_SCHEMA = false;

    private final DataSource dataSource;
    private final SQLDialect sqlDialect;
    private final AtomicInteger transactionCount = new AtomicInteger();
    private final ReentrantLock transactionLock = new ReentrantLock(true);
    private volatile long lastRunMaintenance;
    private final ReentrantLock maintenanceLock = new ReentrantLock();
    private final java.util.concurrent.locks.Condition maintenanceCondition = maintenanceLock.newCondition();
    private final List<String> maintenancePragma;
    private final long maintenancePragmaFrequencyMs;

    @Inject
    public SqliteJooqHelper(final ProxyRepoDbConnProvider connProvider,
                            final ProxyDbConfig proxyProxyDbConfig) {
        this.sqlDialect = SQLDialect.SQLITE;
        this.dataSource = connProvider;
        this.maintenancePragma = proxyProxyDbConfig.getMaintenancePragma();
        this.maintenancePragmaFrequencyMs = proxyProxyDbConfig.getMaintenancePragmaFrequency().toMillis();

//        // Start periodic report.
//        CompletableFuture.runAsync(() -> {
//            while (true) {
//                ThreadUtil.sleep(10000);
//                Metrics.report();
//                printTableRecordCounts();
//                System.out.println("");
//            }
//        });
    }

    public void readOnlyTransaction(final Consumer<DSLContext> consumer) {
        readOnlyTransactionResult(context -> {
            consumer.accept(context);
            return null;
        });
    }

    public <R> R readOnlyTransactionResult(final Function<DSLContext, R> function) {
        return maintainDb(() -> {
            final String method = getMethod();
            return Metrics.measure(method, () ->
                    useConnectionResult(connection -> {
                        final DSLContext context = createContext(connection);
                        return function.apply(context);
                    }));
        });
    }

    public void transaction(final Consumer<DSLContext> consumer) {
        transactionResult(context -> {
            consumer.accept(context);
            return null;
        });
    }

    public <R> R transactionResult(final Function<DSLContext, R> function) {
        return maintainDb(() ->
                Metrics.measure(getMethod(), () ->
                        useConnectionResult(connection -> {
                            try {
                                transactionLock.lockInterruptibly();
                                try {
                                    beginTransaction(connection);
                                    boolean success = false;
                                    try {
                                        final DSLContext context = createContext(connection);
                                        R r = function.apply(context);
                                        success = true;
                                        return r;
                                    } finally {
                                        endTransaction(connection, success);
                                    }

                                } catch (final SQLException e) {
                                    LOGGER.error(e.getMessage(), e);
                                    throw new RuntimeException(e.getMessage(), e);

                                } finally {
                                    transactionLock.unlock();
                                }
                            } catch (final InterruptedException e) {
                                throw UncheckedInterruptedException.create(e);
                            }
                        })));
    }

    private void beginTransaction(final Connection connection) throws SQLException {
        final long startTime = System.currentTimeMillis();
        long lastReport = startTime;

        boolean success = false;
        while (!success) {
            try {
                connection.createStatement().execute("BEGIN IMMEDIATE;");
                success = true;
            } catch (final SQLiteException e) {
                LOGGER.debug(e.getMessage(), e);
                LOGGER.error(e.getMessage());
                if (!e.getMessage().contains("BUSY")) {
                    throw e;
                }
            }

            final long now = System.currentTimeMillis();
            if (lastReport < now - 10000) {
                LOGGER.info("TAKING A LONG TIME TO ACQUIRE LOCK: " +
                        getMethod() +
                        " " +
                        ModelStringUtil.formatDurationString(now - startTime));
                lastReport = now;
            }
        }
    }

    private void endTransaction(final Connection connection,
                                final boolean success) {
        try {
            if (success) {
                connection.createStatement().execute("COMMIT TRANSACTION;");
            } else {
                connection.createStatement().execute("ROLLBACK;");
            }
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private DSLContext createContext(final Connection connection) {
        Settings settings = new Settings();
        // Turn off fully qualified schemata.
        settings = settings.withRenderSchema(RENDER_SCHEMA);
        return DSL.using(connection, sqlDialect, settings);
    }

    private <R> R useConnectionResult(final Function<Connection, R> function) {
        try (final Connection connection = dataSource.getConnection()) {
            return function.apply(connection);
        } catch (final SQLException e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void useConnection(final Consumer<Connection> consumer) {
        useConnectionResult(connection -> {
            consumer.accept(connection);
            return null;
        });
    }

    private <R> R maintainDb(final Supplier<R> supplier) {
        try {

            if (maintenancePragma.size() > 0) {
                maintenanceLock.lockInterruptibly();
                try {
                    // Wait until there are no active transactions if maintenance is due.
                    while (maintenanceDue() && transactionCount.get() != 0) {
                        maintenanceCondition.await();
                    }

                    // The first thread that gets the chance should run the maintenance pragmas.
                    if (maintenanceDue()) {
                        lastRunMaintenance = System.currentTimeMillis();
                        LOGGER.info("Executing SQLLite pragmas: {}", maintenancePragma);
                        for (final String pragma : maintenancePragma) {
                            pragma(pragma);
                        }
                    }
                } finally {
                    // Increment transaction count.
                    transactionCount.incrementAndGet();
                    maintenanceLock.unlock();
                }
            }

            try {
                return supplier.get();

            } finally {

                if (maintenancePragma.size() > 0) {
                    maintenanceLock.lockInterruptibly();
                    try {
                        // Decrement transaction count.
                        transactionCount.decrementAndGet();
                        maintenanceCondition.signalAll();
                    } finally {
                        maintenanceLock.unlock();
                    }
                }
            }

        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    private boolean maintenanceDue() {
        return lastRunMaintenance < System.currentTimeMillis() - maintenancePragmaFrequencyMs;
    }

    private void pragma(final String pragma) {
        useConnection(connection -> {
            try {
                LOGGER.debug("Executing pragma: " + pragma);
                connection.createStatement().execute(pragma);
            } catch (final SQLException e) {
                LOGGER.error("Error executing " + pragma + ": " + e.getMessage(), e);
            }
        });
    }

    public void printTableRecordCounts() {
        printRecordCount(SOURCE, null, "SOURCE");
        printRecordCount(SOURCE, SOURCE.EXAMINED.eq(true), "SOURCE EXAMINED");
//        printRecordCount(SOURCE, SOURCE.NEW_POSITION.isNotNull(), "SOURCE NEW POSITION");
        printRecordCount(SOURCE_ITEM, null, "SOURCE_ITEM");
        printRecordCount(SOURCE_ITEM, SOURCE_ITEM.FK_AGGREGATE_ID.isNotNull(), "SOURCE_ITEM AGGREGATED");
        printRecordCount(SOURCE_ITEM, SOURCE_ITEM.NEW_POSITION.isNotNull(), "SOURCE_ITEM NEW POSITION");
        printRecordCount(AGGREGATE, null, "AGGREGATE");
        printRecordCount(AGGREGATE, AGGREGATE.NEW_POSITION.isNotNull(), "AGGREGATE NEW POSITION");
        printRecordCount(FORWARD_DEST, null, "FORWARD_DEST");
        printRecordCount(FORWARD_AGGREGATE, null, "FORWARD_AGGREGATE");
        printRecordCount(FORWARD_AGGREGATE,
                FORWARD_AGGREGATE.NEW_POSITION.isNotNull(),
                "FORWARD_AGGREGATE NEW POSITION");
    }

    <R extends Record, T extends Table<R>> void printRecordCount(final T table,
                                                                 final Condition condition,
                                                                 final String message) {
        readOnlyTransaction(context -> {
            final int count = context.fetchCount(table, condition);
            System.out.println(message + ": " + count);
        });
    }

    public void printAllTables() {
        printTable(SOURCE, null, "SOURCE");
        printTable(SOURCE_ITEM, null, "SOURCE_ITEM");
        printTable(AGGREGATE, null, "AGGREGATE");
        printTable(FORWARD_DEST, null, "FORWARD_DEST");
        printTable(FORWARD_AGGREGATE, null, "FORWARD_AGGREGATE");
    }

    <R extends Record, T extends Table<R>> void printTable(final T table,
                                                           final Condition condition,
                                                           final String message) {
        readOnlyTransaction(context -> {
            final List<R> records = context.selectFrom(table).where(condition).fetch();
            printRecords(records, message);
        });
    }

    <R extends Record> void printRecords(final List<R> records, final String message) {
        System.out.println(message + ": \n" + records);
    }

    private String getMethod() {
        final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        boolean next = false;
        for (final StackTraceElement element : stackTraceElements) {
            if (element.toString().contains("print")) {
                return element.toString();
            } else if (element.getClassName().equals(this.getClass().getName()) ||
                    element.getClassName().contains("logging")) {
                next = true;
            } else if (next) {
                return element.toString();
//                return element.getClassName() + " " + element.getMethodName();
            }
        }
        return null;
    }
}
