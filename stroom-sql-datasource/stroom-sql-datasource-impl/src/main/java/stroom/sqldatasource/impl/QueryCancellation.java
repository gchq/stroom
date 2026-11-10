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
/// The running query registers its statement here. Cancelling calls `Statement.cancel()` on it, and
/// a statement registered after cancelling is cancelled at once.
public class QueryCancellation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueryCancellation.class);

    private Statement statement;
    private boolean cancelled;

    /// Asks the running statement, if any, to stop, and marks the query as cancelled.
    public void cancel() {
        final Statement toCancel;
        synchronized (this) {
            cancelled = true;
            toCancel = statement;
        }
        cancelQuietly(toCancel);
    }

    /// @return True if the query has been cancelled.
    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /// Records the statement now running, cancelling it at once if the query is already cancelled.
    ///
    /// @param statement The statement.
    public void register(final Statement statement) {
        final boolean alreadyCancelled;
        synchronized (this) {
            this.statement = statement;
            alreadyCancelled = cancelled;
        }
        if (alreadyCancelled) {
            cancelQuietly(statement);
        }
    }

    /// Forgets the statement once it has finished, so it is not cancelled after being closed.
    public synchronized void unregister() {
        statement = null;
    }

    private static void cancelQuietly(final Statement statement) {
        if (statement != null) {
            try {
                statement.cancel();
            } catch (final SQLException | RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Unable to cancel statement: {}", e.getMessage()), e);
            }
        }
    }
}
