/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqldatasource.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.sql.SQLException;
import java.sql.Statement;

/// Lets a search that is closed or terminated stop its SQL in the database, not just in Stroom.
///
/// The running query registers how to stop it here: a JDBC statement, which is stopped with
/// `Statement.cancel()`, or any other action, such as stopping an Amazon Athena query execution.
/// Something registered after cancelling is stopped at once.
public class QueryCancellation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueryCancellation.class);

    private Runnable stopAction;
    private boolean cancelled;

    /// Stops the running query, if any, and marks the query as cancelled.
    public void cancel() {
        final Runnable toRun;
        synchronized (this) {
            cancelled = true;
            toRun = stopAction;
        }
        runQuietly(toRun);
    }

    /// @return True if the query has been cancelled.
    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /// Records the statement now running, cancelling it at once if the query is already cancelled.
    ///
    /// @param statement The statement.
    public void register(final Statement statement) {
        register(() -> {
            try {
                statement.cancel();
            } catch (final SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    /// Records how to stop the query now running, stopping it at once if the query is already
    /// cancelled.
    ///
    /// @param stopAction Stops the query. It may throw; the failure is only logged.
    public void register(final Runnable stopAction) {
        final boolean alreadyCancelled;
        synchronized (this) {
            this.stopAction = stopAction;
            alreadyCancelled = cancelled;
        }
        if (alreadyCancelled) {
            runQuietly(stopAction);
        }
    }

    /// Forgets the running query once it has finished, so it is not stopped after that.
    public synchronized void unregister() {
        stopAction = null;
    }

    private static void runQuietly(final Runnable stopAction) {
        if (stopAction != null) {
            try {
                stopAction.run();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Unable to stop the query: {}", e.getMessage()), e);
            }
        }
    }
}
