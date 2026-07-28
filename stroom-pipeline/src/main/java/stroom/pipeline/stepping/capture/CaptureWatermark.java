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

package stroom.pipeline.stepping.capture;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.store.StepDataStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * How far a producer has got, and how a reader waits for it to get further.
 * <p>
 * A producer commits each record atomically to the {@link StepDataStore} and then calls
 * {@link #recordCaptured}, so a record becomes visible here only once it is fully written and a reader never
 * sees a torn record. Readers take {@link #getVersion()} <em>before</em> reading the store and then
 * {@link #awaitChangeSince} on that version, which closes the lost-wake-up window where a record lands between
 * the read and the wait.
 * <p>
 * <b>The rule this type exists to enforce: every way a producer can stop must wake its waiters.</b> There are
 * three - it captured everything ({@link #markFullyCaptured}), it failed ({@link #markError}), or it was
 * terminated (which the owner reports as an error). All three end in a signal. A producer that stops without
 * signalling does not produce a wrong answer, it produces a <em>hang</em>: readers wait out their whole
 * timeout for records that are never coming. That is why {@code fullyCaptured} is set by the error path too -
 * "finished" here means "stopped producing", not "succeeded", and {@link #isSuccessfullyCaptured()} is the
 * separate question a caller must ask before reusing what was captured.
 * <p>
 * Held per producer. Today that is one whole-stream sweep ({@link StreamSweep}); the reason it is separable is
 * that a per-element stage is the same shape - it publishes progress and terminal state exactly like this, and
 * its consumer waits on it exactly like this.
 */
public class CaptureWatermark {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();

    private long version;
    private boolean fullyCaptured;
    private Throwable error;
    private StepLocation lastCapturedLocation;

    // The per-part record range THIS producer has captured, which is what a reader may navigate. It matters
    // because a reprocess writes into a store that already holds the reused upstream at the full range: the
    // store's range would let a reader reach a record before the reprocess has written its changed element,
    // whereas this range advances only as this producer captures. For a full sweep it equals the store's
    // range (the sweep writes everything), so gating on it changes nothing there.
    private final Map<Long, Long> partCapturedMin = new HashMap<>();
    private final Map<Long, Long> partCapturedMax = new HashMap<>();

    /**
     * Signal that a record has been fully committed to the store (advances the progress version).
     */
    public void recordCaptured(final StepLocation location) {
        lock.lock();
        try {
            version++;
            lastCapturedLocation = location;
            final long part = location.getPartIndex();
            final long record = location.getRecordIndex();
            partCapturedMin.merge(part, record, Math::min);
            partCapturedMax.merge(part, record, Math::max);
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the first (lowest) record index captured for the part, or -1 if none yet.
     */
    public long getCapturedFirstRecordIndex(final long partIndex) {
        lock.lock();
        try {
            return partCapturedMin.getOrDefault(partIndex, -1L);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the last (highest) record index captured for the part, or -1 if none yet.
     */
    public long getCapturedLastRecordIndex(final long partIndex) {
        lock.lock();
        try {
            return partCapturedMax.getOrDefault(partIndex, -1L);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the part indices that have at least one captured record, ascending. A producer works parts in
     * ascending order, so the appearance of a higher part index is what tells a consumer the previous part is
     * final - short of the producer stopping altogether.
     */
    public List<Long> getCapturedPartIndices() {
        lock.lock();
        try {
            return partCapturedMin.keySet().stream().sorted().toList();
        } finally {
            lock.unlock();
        }
    }

    public void markFullyCaptured() {
        lock.lock();
        try {
            fullyCaptured = true;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void markError(final Throwable t) {
        lock.lock();
        try {
            if (error == null) {
                error = t;
            }
            fullyCaptured = true;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public long getVersion() {
        lock.lock();
        try {
            return version;
        } finally {
            lock.unlock();
        }
    }

    public boolean isFullyCaptured() {
        lock.lock();
        try {
            return fullyCaptured;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return true only if everything was captured <b>without error</b>. {@link #markError} also sets
     * {@code fullyCaptured} (so readers stop waiting), so {@link #isFullyCaptured()} alone does not mean the
     * store holds the complete record stream - a caller deciding whether the captured chunks can be reused
     * (e.g. to reprocess from them) must use this.
     */
    public boolean isSuccessfullyCaptured() {
        lock.lock();
        try {
            return fullyCaptured && error == null;
        } finally {
            lock.unlock();
        }
    }

    public Throwable getError() {
        lock.lock();
        try {
            return error;
        } finally {
            lock.unlock();
        }
    }

    public StepLocation getLastCapturedLocation() {
        lock.lock();
        try {
            return lastCapturedLocation;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wait until progress is made past {@code knownVersion}, the producer stops, or the timeout elapses.
     *
     * @param knownVersion the version observed before reading the store.
     * @param timeoutMs    the maximum time to wait.
     * @return true if progress/completion occurred, false if the timeout elapsed or the wait was interrupted.
     */
    public boolean awaitChangeSince(final long knownVersion, final long timeoutMs) {
        lock.lock();
        try {
            long remainingNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
            while (version == knownVersion && !fullyCaptured) {
                if (remainingNanos <= 0) {
                    return false;
                }
                remainingNanos = changed.awaitNanos(remainingNanos);
            }
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            // No progress was observed. Reporting progress here would send the caller round its resolve loop
            // again, where the still-set interrupt flag makes the next await throw immediately - spinning a
            // full store re-scan until its deadline. Consistent with awaitFullyCaptured, which returns the flag.
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wait until the producer has stopped (captured everything, or errored), or the timeout elapses.
     *
     * @return true if it stopped, false on timeout.
     */
    public boolean awaitFullyCaptured(final long timeoutMs) {
        lock.lock();
        try {
            long remainingNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
            while (!fullyCaptured) {
                if (remainingNanos <= 0) {
                    return false;
                }
                remainingNanos = changed.awaitNanos(remainingNanos);
            }
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return fullyCaptured;
        } finally {
            lock.unlock();
        }
    }
}
