package stroom.proxy.repo.dao;

import stroom.proxy.repo.ProxyRepoDbConfig;
import stroom.proxy.repo.ProxyRepoDbConnProvider;
import stroom.util.concurrent.ThreadUtil;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class SqliteJooqHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqliteJooqHelper.class);

    private static final Boolean RENDER_SCHEMA = false;

    private final DataSource dataSource;
    private final SQLDialect sqlDialect;

    private final ThreadLocal<StackTraceElement[]> currentTransaction = new ThreadLocal<>();
    private final AtomicInteger transactionCount = new AtomicInteger();
    private volatile long lastRunMaintenance;
    private final ReentrantLock lock = new ReentrantLock();
    private final java.util.concurrent.locks.Condition condition = lock.newCondition();
    private final List<String> maintenancePragma;
    private final long maintenancePragmaFrequencyMs;

    @Inject
    public SqliteJooqHelper(final ProxyRepoDbConnProvider connProvider,
                            final ProxyRepoDbConfig proxyRepoDbConfig) {
        this.sqlDialect = SQLDialect.SQLITE;
        this.dataSource = connProvider;
        this.maintenancePragma = proxyRepoDbConfig.getMaintenancePragma();
        this.maintenancePragmaFrequencyMs = proxyRepoDbConfig.getMaintenancePragmaFrequency().toMillis();

        // Start periodic report.
        CompletableFuture.runAsync(() -> {
            while (true) {
                ThreadUtil.sleep(10000);
                Metrics.report();
                printTableRecordCounts();
                System.out.println("");
            }
        });
    }

    public void readOnlyTransaction(final Consumer<DSLContext> consumer) {
        transactionCheck(() -> {
            final String method = getMethod();
            Metrics.measure(method, () -> {
                useConnection(connection -> {
//            try {
//                connection.createStatement().execute("BEGIN IMMEDIATE;");
                    final DSLContext context = createContext(connection);
                    consumer.accept(context);
//                connection.createStatement().execute("COMMIT;");
//            } catch (final SQLException e) {
//                LOGGER.error(e.getMessage(), e);
//                throw new RuntimeException(e.getMessage(), e);
//            }
                });
            });
        });

//        context(context -> context.transaction(nested -> consumer.accept(DSL.using(nested))));
    }

    public <R> R readOnlyTransactionResult(final Function<DSLContext, R> function) {
        return transactionCheck(() -> {
            final String method = getMethod();
            final R result = Metrics.measure(method, () -> {
                return useConnectionResult(connection -> {
//            try {
//                connection.createStatement().execute("BEGIN IMMEDIATE;");
                    final DSLContext context = createContext(connection);
                    return function.apply(context);
//                connection.createStatement().execute("COMMIT;");
//            } catch (final SQLException e) {
//                LOGGER.error(e.getMessage(), e);
//                throw new RuntimeException(e.getMessage(), e);
//            }
                });

            });

            return result;
        });
//        return contextResult(context -> context.transactionResult(nested -> function.apply(DSL.using(nested))));
    }

    public void transaction(final Consumer<DSLContext> consumer) {
        transactionCheck(() -> {
            final String method = getMethod();
            Metrics.measure(method, () -> {
                useConnection(connection -> {
                    try {
                        beginTransaction(connection);
                        boolean success = false;
                        try {
                            final DSLContext context = createContext(connection);
                            consumer.accept(context);
                            success = true;
                        } finally {
                            endTransaction(connection, success);
                        }
                    } catch (final SQLException e) {
                        LOGGER.error(e.getMessage(), e);
                        throw new RuntimeException(e.getMessage(), e);
                    }
                });
            });

//        context(context -> {
//            context.transaction(nested -> consumer.accept(DSL.using(nested)))
//        });
        });
    }

    public <R> R transactionResult(final Function<DSLContext, R> function) {
        return transactionCheck(() -> {
            final R result = Metrics.measure(getMethod(), () -> {
                return useConnectionResult(connection -> {
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
                    }
                });
            });

            return result;
        });
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

    private void transactionCheck(final Runnable runnable) {
        try {
            increment();
            try {
                final StackTraceElement[] stackTraceElements = currentTransaction.get();
                if (stackTraceElements != null) {
                    LOGGER.error("Current transaction exists: " + Arrays.toString(stackTraceElements));
                }
                currentTransaction.set(Thread.currentThread().getStackTrace());

                runnable.run();

            } finally {
                currentTransaction.set(null);
            }
        } finally {
            decrement();
        }
    }

    private <R> R transactionCheck(final Supplier<R> supplier) {
        increment();
        try {
            final StackTraceElement[] stackTraceElements = currentTransaction.get();
            if (stackTraceElements != null) {
                LOGGER.error("Current transaction exists: " + Arrays.toString(stackTraceElements));
            }
            currentTransaction.set(Thread.currentThread().getStackTrace());

            return supplier.get();

        } finally {
            currentTransaction.set(null);
            decrement();
        }
    }

    private void increment() {
        try {
            if (maintenancePragma.size() > 0) {
                lock.lockInterruptibly();
                try {
                    // Is it time to run maintenance pragmas?
                    if (lastRunMaintenance < System.currentTimeMillis() - maintenancePragmaFrequencyMs) {
                        // Wait until there are no active transactions.
                        while (transactionCount.get() != 0) {
                            condition.await();
                        }

                        // The first thread that gets the chance should run the maintenance pragmas.
                        final long now = System.currentTimeMillis();
                        if (lastRunMaintenance < now - maintenancePragmaFrequencyMs) {
                            lastRunMaintenance = now;
                            for (final String pragma : maintenancePragma) {
                                pragma(pragma);
                            }
                        }
                    }
                } finally {
                    transactionCount.incrementAndGet();
                    lock.unlock();
                }
            }
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
    }

    private void decrement() {
        try {
            if (maintenancePragma.size() > 0) {
                lock.lockInterruptibly();
                try {
                    transactionCount.decrementAndGet();
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
    }

    private void pragma(final String pragma) {
        useConnection(connection -> {
            try {
                LOGGER.info("Executing pragma: " + pragma);
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
        printRecordCount(SOURCE_ENTRY, null, "SOURCE_ENTRY");
        printRecordCount(AGGREGATE, null, "AGGREGATE");
        printRecordCount(AGGREGATE, AGGREGATE.NEW_POSITION.isNotNull(), "AGGREGATE NEW POSITION");
        printRecordCount(FORWARD_URL, null, "FORWARD_URL");
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
        printTable(SOURCE_ENTRY, null, "SOURCE_ENTRY");
        printTable(AGGREGATE, null, "AGGREGATE");
        printTable(FORWARD_URL, null, "FORWARD_URL");
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
            if (element.getClassName().equals(this.getClass().getName()) ||
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
