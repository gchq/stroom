package stroom.db.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.ExecuteContext;
import org.jooq.Query;
import org.jooq.impl.DefaultExecuteListener;

import java.time.Duration;

public class SlowQueryExecuteListener extends DefaultExecuteListener {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SlowQueryExecuteListener.class);

    long startTimeMs;

    @Override
    public void executeStart(final ExecuteContext ctx) {
        super.executeStart(ctx);
        startTimeMs = System.currentTimeMillis();
    }

    @Override
    public void executeEnd(final ExecuteContext ctx) {
        super.executeEnd(ctx);
        if (LOGGER.isDebugEnabled()) {
            long durationMs = System.currentTimeMillis() - startTimeMs;

            // Could make this configurable but would have to move it out of util
            if (durationMs > 2_000) {
                final Duration duration = Duration.ofMillis(durationMs);
                if (ctx.query() != null) {
                    LOGGER.debug("SQL query took longer than 2s to execute {}\n{}\nrows: {}",
                            duration, ctx.query(), ctx.rows());
                } else if (ctx.batchQueries() != null) {
                    final Query[] batchQueries = ctx.batchQueries();
                    LOGGER.debug("Batch of SQL queries (size: {}) took longer than " +
                            "2s to execute {}, showing 1st query only\n{}\nrows: {}",
                            batchQueries.length, duration, ctx.query(), ctx.batchRows());
                } else {
                    LOGGER.debug("Unknown SQL query took longer than 2s to execute {}", duration);
                }
            }
        }
    }
}
