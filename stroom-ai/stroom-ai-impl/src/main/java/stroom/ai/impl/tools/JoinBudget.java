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

package stroom.ai.impl.tools;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/// What one streaming join may read, shared by its workers: the rows and keys read so far against their caps, the
/// call's time limit, and the user's stop. A worker asks before each page ([#check()]); once a cap or the time limit
/// is reached every worker stops at its next page, keeping what it has found, and the join says why it ended early.
final class JoinBudget {

    private final long maxRows;
    private final long maxKeys;
    private final Instant deadline;
    private final BooleanSupplier stopped;
    private final AtomicLong rows = new AtomicLong();
    private final AtomicLong keys = new AtomicLong();
    private final AtomicLong pages = new AtomicLong();
    private final AtomicReference<Limit> reached = new AtomicReference<>();

    /// @param maxRows  The most rows (events, and keys read as keys) the join may read.
    /// @param maxKeys  The most keys it may merge.
    /// @param deadline When its time limit ends.
    /// @param stopped  True once the user has stopped the call.
    JoinBudget(final long maxRows, final long maxKeys, final Instant deadline, final BooleanSupplier stopped) {
        this.maxRows = maxRows;
        this.maxKeys = maxKeys;
        this.deadline = deadline;
        this.stopped = stopped == null
                ? () -> false
                : stopped;
    }

    /// @return True if a cap or the time limit was reached, or the user stopped the call, so reading ends.
    boolean check() {
        if (reached.get() != null) {
            return true;
        }
        if (stopped.getAsBoolean()) {
            reached.compareAndSet(null, Limit.STOPPED);
        } else if (!Instant.now().isBefore(deadline)) {
            reached.compareAndSet(null, Limit.TIME);
        } else if (rows.get() >= maxRows) {
            reached.compareAndSet(null, Limit.ROWS);
        } else if (keys.get() >= maxKeys) {
            reached.compareAndSet(null, Limit.KEYS);
        }
        return reached.get() != null;
    }

    /// @param count Rows just read.
    void readRows(final long count) {
        rows.addAndGet(count);
        pages.incrementAndGet();
    }

    /// Counts one key merged.
    void mergedKey() {
        keys.incrementAndGet();
    }

    /// @return How long is left of the time limit (at least a millisecond).
    Duration remaining() {
        final Duration left = Duration.between(Instant.now(), deadline);
        return left.isNegative() || left.isZero()
                ? Duration.ofMillis(1)
                : left;
    }

    /// @return The rows read so far.
    long rows() {
        return rows.get();
    }

    /// @return The keys merged so far.
    long keys() {
        return keys.get();
    }

    /// @return The pages read so far.
    long pages() {
        return pages.get();
    }

    /// @return Why reading ended early, or null if it hasn't.
    Limit reached() {
        return reached.get();
    }

    /// @return True if the user stopped the call.
    boolean stoppedByUser() {
        return reached.get() == Limit.STOPPED;
    }

    /// @return The most rows.
    long maxRows() {
        return maxRows;
    }

    /// @return The most keys.
    long maxKeys() {
        return maxKeys;
    }


    // --------------------------------------------------------------------------------


    /// Why a join ended early.
    enum Limit {
        /// The rows read reached their cap.
        ROWS,
        /// The keys merged reached their cap.
        KEYS,
        /// The time limit passed.
        TIME,
        /// The user stopped the call.
        STOPPED
    }
}
