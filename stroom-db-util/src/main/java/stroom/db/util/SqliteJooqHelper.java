package stroom.db.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.SQLDialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import javax.sql.DataSource;

public class SqliteJooqHelper extends JooqHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqliteJooqHelper.class);
    private static final Lock DB_LOCK = new ReentrantLock();
//    private static final Map<String, Invocation> invocations = new ConcurrentHashMap<>();
//    private static final AtomicLong time = new AtomicLong();

    private final DataSource dataSource;

    public SqliteJooqHelper(final DataSource dataSource) {
        super(dataSource, SQLDialect.SQLITE);
        this.dataSource = dataSource;
    }

    @Override
    <R> R useConnectionResult(final Function<Connection, R> function) {
        R result;

        DB_LOCK.lock();
        try {
            try (final Connection connection = dataSource.getConnection()) {
                result = function.apply(connection);
            } catch (final SQLException e) {
                LOGGER.error(e::getMessage, e);
                throw new RuntimeException(e.getMessage(), e);
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        } finally {
            DB_LOCK.unlock();
        }

//        final StackTraceElement[] stackTrace;
//        final String source;
//        try {
//            throw new RuntimeException("test");
//        } catch (final RuntimeException e) {
//            stackTrace = e.getStackTrace();
//            source = Arrays
//                    .stream(stackTrace)
//                    .map(StackTraceElement::toString)
//                    .collect(Collectors.joining(">"));
//
//        }
//
//        long beforeLock = System.nanoTime();
//


//            long afterLock = System.nanoTime();
//
//            try {
//                checkDataSource(dataSource);

//        final AtomicInteger tries = new AtomicInteger(1);
//        boolean tryAgain = true;
//        while (tryAgain) {
//            tryAgain = false;
//            try (final Connection connection = dataSource.getConnection()) {
//                result = function.apply(connection);
//            } catch (final RuntimeException | SQLException e) {
//                if (e.getMessage() != null && e.getMessage().contains("SQLITE_BUSY")) {
//                    tryAgain = true;
//                    final int count = tries.incrementAndGet();
//                    LOGGER.debug(() -> count + " tries");
//
//                } else {
//                    LOGGER.error(e::getMessage, e);
//                    if (e instanceof RuntimeException) {
//                        throw (RuntimeException) e;
//                    } else {
//                        throw new RuntimeException(e.getMessage(), e);
//                    }
//                }
//            }
//        }

//            long now = System.nanoTime();
//            long elapsed = now - afterLock;
//            long waiting = afterLock - beforeLock;
//
//            final Invocation invocation = invocations.computeIfAbsent(source, k -> new Invocation(stackTrace));
//            invocation.count.incrementAndGet();
//            invocation.time.addAndGet(elapsed);
//            invocation.timeWaiting.addAndGet(waiting);


//            } finally {
////                releaseDataSource();
//            }

//        // Every 10 seconds log.
//        final long now = System.currentTimeMillis();
//        final long oldTime = time.get();
//        if (oldTime < now - 10000) {
//            if (time.compareAndSet(oldTime, now)) {
//                final StringBuilder sb = new StringBuilder();
//                invocations
//                        .entrySet()
//                        .stream()
//                        .sorted(Comparator.comparing(e -> e.getValue().time.get()))
//                        .forEach(e -> {
//                            sb.append(e.getValue().toString());
//                            sb.append(" ");
//                            sb.append(e.getKey());
//                            sb.append("\n");
//                        });
//                LOGGER.info(sb.toString());
//            }
//        }

        return result;
    }

//    private static class Invocation {
//
//        private final StackTraceElement[] stack;
//        private final AtomicLong count = new AtomicLong();
//        private final AtomicLong time = new AtomicLong();
//        private final AtomicLong timeWaiting = new AtomicLong();
//
//        public Invocation(final StackTraceElement[] stack) {
//            this.stack = stack;
//        }
//
//        @Override
//        public String toString() {
//            return "count=" + count +
//                    ", time=" + ModelStringUtil.formatDurationString(time.get() / 1000000) +
//                    ", timeWaiting=" + ModelStringUtil.formatDurationString(timeWaiting.get() / 1000000);
//        }
//    }
}
