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

package stroom.db.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.ExecuteContext;
import org.jooq.Query;
import org.jooq.impl.DefaultExecuteListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Logs any sql statements that take longer than 2s to execute.
 * Enable this by setting {@link SlowQueryExecuteListener} to DEBUG.
 */
public class SlowQueryExecuteListener extends DefaultExecuteListener {

    public static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SlowQueryExecuteListener.class);

    private static final Duration DEFAULT_THRESHOLD = Duration.ofSeconds(2);
    private static final int MAX_QUERY_COUNT = 5;

    private Instant startTime;
    private static Duration SLOW_QUERY_DURATION_THRESHOLD = DEFAULT_THRESHOLD;

    @Override
    public void executeStart(final ExecuteContext ctx) {
        super.executeStart(ctx);
        startTime = Instant.now();
    }

    @Override
    public void executeEnd(final ExecuteContext ctx) {
        super.executeEnd(ctx);
        if (LOGGER.isDebugEnabled() && ctx != null) {
            final Duration duration = Duration.between(startTime, Instant.now());
            logQueryDuration(ctx, duration);
        }
    }

    private void logQueryDuration(final ExecuteContext ctx, final Duration duration) {
        // Could make this configurable but would have to move it out of util
        if (duration.compareTo(SLOW_QUERY_DURATION_THRESHOLD) >= 0) {
            if (ctx.query() != null) {
                LOGGER.debug("""
                                SQL of type {} took longer than {} to execute {}
                                {}
                                rows: {}""",
                        ctx.type(),
                        SLOW_QUERY_DURATION_THRESHOLD,
                        duration,
                        ctx.query(),
                        ctx.rows());
            } else if (ctx.batchQueries() != null) {
                // This is where you have multiple distinct queries in the batch,
                // e.g. an insert and an update
                final Query[] batchQueries = ctx.batchQueries();
                final String allSql = batchQueries.length == 0
                        ? "[NO BATCH QUERIES]"
                        : IntStream.range(0, batchQueries.length)
                                .limit(MAX_QUERY_COUNT)
                                .boxed()
                                .map(i -> {
                                    final Query batchQuery = batchQueries[i];
                                    final String sql = batchQuery.toString();
                                    final int rows = ctx.batchRows()[i];
                                    final int queryNo = i + 1;
                                    return "Query "
                                            + queryNo
                                            + ":\n"
                                            + sql
                                            + ";\nrows:"
                                            + rows;
                                })
                                .collect(Collectors.joining("\n---\n"));
                final String limitText = batchQueries.length > MAX_QUERY_COUNT
                        ? " (only showing first " + MAX_QUERY_COUNT + " queries)"
                        : "";

                LOGGER.debug("""
                                Batch of {} SQL statements took longer than {} to execute {}{}
                                {}""",
                        batchQueries.length,
                        SLOW_QUERY_DURATION_THRESHOLD,
                        duration,
                        limitText,
                        allSql);
            } else {
                LOGGER.debug("Unknown SQL of type {} took longer than 2s to execute {}",
                        ctx.type(),
                        duration);
            }
        }
    }

    /**
     * For testing only
     */
    public static void setSlowQueryDurationThreshold(final Duration slowQueryDurationThreshold) {
        SLOW_QUERY_DURATION_THRESHOLD = Objects.requireNonNull(slowQueryDurationThreshold);
    }

    public static void resetSlowQueryDurationThreshold() {
        SLOW_QUERY_DURATION_THRESHOLD = DEFAULT_THRESHOLD;
    }
}
