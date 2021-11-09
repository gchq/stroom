package stroom.proxy.repo.dao;

import stroom.db.util.JooqHelper;
import stroom.proxy.repo.ProxyRepoDbConnProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
public class SqliteJooqHelper extends JooqHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqliteJooqHelper.class);

    private final Lock lock = new ReentrantLock();
    private final DataSource dataSource;

    @Inject
    public SqliteJooqHelper(final ProxyRepoDbConnProvider connProvider) {
        super(connProvider, SQLDialect.SQLITE);
        this.dataSource = connProvider;
    }

    public <T> T underLock(final Supplier<T> supplier) {
        try {
            lock.lockInterruptibly();
            try {
                return supplier.get();
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    protected <R> R useConnectionResult(final Function<Connection, R> function) {
        R result;

        try (final Connection connection = dataSource.getConnection()) {
            result = function.apply(connection);
        } catch (final SQLException e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }

        return result;
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
        context(context -> {
            final List<R> records = context.selectFrom(table).where(condition).fetch();
            printRecords(records, message);
        });
    }

    <R extends Record> void printRecords(final List<R> records, final String message) {
        System.out.println(message + ": \n" + records);
    }
}
