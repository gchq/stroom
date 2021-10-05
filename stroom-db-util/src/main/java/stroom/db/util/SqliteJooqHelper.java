package stroom.db.util;

import org.jooq.SQLDialect;

import java.sql.Connection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import javax.sql.DataSource;

public class SqliteJooqHelper extends JooqHelper {

    private static final Lock DB_LOCK = new ReentrantLock();

    public SqliteJooqHelper(final DataSource dataSource) {
        super(dataSource, SQLDialect.SQLITE);
    }

    @Override
    <R> R useConnectionResult(final Function<Connection, R> function) {
        DB_LOCK.lock();
        try {
            return super.useConnectionResult(function);
        } finally {
            DB_LOCK.unlock();
        }
    }
}
