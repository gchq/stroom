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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.task.api.TaskContext;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The asynchronous capture of a single stream: it owns the stream's {@link StepDataStore} and a progress
 * signal that lets a reader wait until more records have been captured or the sweep finishes.
 * <p>
 * The capture thread commits each record atomically via {@link StepDataStore#putRecord} and then calls
 * {@link #recordCaptured}; a record therefore only becomes visible in the store once it is fully written,
 * so a reader never sees a torn record. Readers use {@link #getVersion()} + {@link #awaitChangeSince} to
 * block until progress is made (a version bump), the sweep completes, or a timeout elapses - the version
 * captured before reading the store avoids a lost wake-up if a record lands between the read and the wait.
 */
public class StreamSweep {

    private final long metaId;
    private final StepDataStore store;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();

    private long version;
    private boolean complete;
    private Throwable error;
    private StepLocation lastCapturedLocation;

    // Set when the async capture task is launched, so the owning session can terminate it on close.
    private volatile TaskContext taskContext;

    // Set by the session when it wants this sweep to stop. Read by the capture task once it has published
    // its task context, closing the window where a close() sees a null context and skips termination.
    private volatile boolean terminateRequested;

    public StreamSweep(final long metaId, final StepDataStore store) {
        this.metaId = metaId;
        this.store = store;
    }

    public long getMetaId() {
        return metaId;
    }

    public StepDataStore getStore() {
        return store;
    }

    /**
     * Signal that a record has been fully committed to the store (advances the progress version).
     */
    public void recordCaptured(final StepLocation location) {
        lock.lock();
        try {
            version++;
            lastCapturedLocation = location;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void markComplete() {
        lock.lock();
        try {
            complete = true;
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
            complete = true;
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

    public boolean isComplete() {
        lock.lock();
        try {
            return complete;
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
     * Wait until progress is made past {@code knownVersion}, the sweep completes, or the timeout elapses.
     *
     * @param knownVersion the version observed before reading the store.
     * @param timeoutMs     the maximum time to wait.
     * @return true if progress/completion occurred, false if the timeout elapsed or the wait was interrupted.
     */
    public boolean awaitChangeSince(final long knownVersion, final long timeoutMs) {
        lock.lock();
        try {
            long remainingNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
            while (version == knownVersion && !complete) {
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
            // full store re-scan until its deadline. Consistent with awaitComplete, which returns `complete`.
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wait until the sweep completes (or errors), or the timeout elapses.
     *
     * @return true if the sweep completed, false on timeout.
     */
    public boolean awaitComplete(final long timeoutMs) {
        lock.lock();
        try {
            long remainingNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
            while (!complete) {
                if (remainingNanos <= 0) {
                    return false;
                }
                remainingNanos = changed.awaitNanos(remainingNanos);
            }
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return complete;
        } finally {
            lock.unlock();
        }
    }

    void setTaskContext(final TaskContext taskContext) {
        this.taskContext = taskContext;
    }

    public TaskContext getTaskContext() {
        return taskContext;
    }

    /**
     * Ask this sweep to stop. The capture task may not have published its {@link TaskContext} yet, so this
     * flag is the other half of a handshake: the session sets it <em>before</em> reading
     * {@link #getTaskContext()}, and the capture task publishes its context <em>before</em> reading this
     * flag. Whichever order the two threads run in, at least one of them sees the other's write, so a sweep
     * can never start (or keep running) after its session has been closed.
     */
    public void requestTerminate() {
        terminateRequested = true;
    }

    public boolean isTerminateRequested() {
        return terminateRequested;
    }
}
