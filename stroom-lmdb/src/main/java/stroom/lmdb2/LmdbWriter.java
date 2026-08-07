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

package stroom.lmdb2;

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Provider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class LmdbWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbWriter.class);
    private static final long WAIT_WARN_FREQUENCY_MS = 30_000;

    private final LmdbEnv env;
    private final ReentrantLock lock;
    private final Condition notFull;
    private final Condition notEmpty;
    private final CompletableFuture<Void> transferFuture;
    // Volatile as the transfer loop's outer condition reads it without holding the lock, and
    // close() now waits uninterruptibly for that loop to notice it.
    private volatile boolean closed;
    // Set (under lock) when the transfer loop has exited, however it exited. Distinct from
    // closed, which is the client asking it to stop; terminated is it having stopped, and is
    // what stops a put() waiting forever on a loop that is no longer there to consume it.
    private boolean terminated;
    private Consumer<WriteTxn> consumer;

    /**
     * The supplied executor must either run or reject the transfer task; an executor that
     * silently drops a submitted task (e.g. {@code shutdownNow()} with the task still queued)
     * leaves {@link #close()} waiting forever — and that wait is uninterruptible — as it has
     * no way to distinguish a dropped task from one that has yet to run. Rejection at
     * submission propagates from this constructor.
     */
    public LmdbWriter(final Provider<Executor> executorProvider,
                      final LmdbEnv env) {
        this.env = env;
        lock = new ReentrantLock();
        notFull = lock.newCondition();
        notEmpty = lock.newCondition();

        // Start transfer loop. Holding the future lets close() wait for the loop to perform
        // its final commit and close its write txn before the caller closes the env.
        transferFuture = CompletableFuture.runAsync(this::transfer, executorProvider.get());
    }

    /**
     * Performs the write operation, but does not flush (i.e. commit).
     */
    public synchronized void write(final Consumer<WriteTxn> consumer) {
        put(consumer, false);
    }

    /**
     * Performs the write operation, and optionally flushes (i.e. commits).
     */
    public synchronized void write(final Consumer<WriteTxn> consumer, final boolean flush) {
        put(consumer, false);
        if (flush) {
            flush();
        }
    }

    public synchronized void flush() {
        put(WriteTxn::commit, false);
    }

    /**
     * Stops the transfer loop and waits for it to finish, i.e. to have performed its final
     * commit and closed its write txn. Once this returns — normally or by throwing — the
     * caller may, and should, close the env: the write txn is closed on every path.
     * <p>
     * The wait is uninterruptible by design. An interrupt here is routine rather than
     * exceptional (task threads are interrupted when work is terminated), and callers
     * typically delete the env dir straight after closing it, so abandoning the wait would
     * leak env dirs on disk. The interrupt flag is restored before returning. The wait is
     * bounded in practice because the stop request below cannot be lost.
     */
    public synchronized void close() {
        // Stop the transfer loop. Set directly under the lock rather than handed over via
        // put(), so that an interrupt cannot leave the loop running: the wait below is
        // uninterruptible, so the loop MUST be guaranteed to stop. Nothing can be queued at
        // this point — every other public method is synchronized, and put() never returns
        // with its item still queued — so stopping the loop cannot discard a write.
        lock.lock();
        try {
            closed = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }

        boolean interrupted = false;
        Throwable failure = null;
        try {
            while (true) {
                try {
                    transferFuture.get(WAIT_WARN_FREQUENCY_MS, TimeUnit.MILLISECONDS);
                    break;
                } catch (final TimeoutException e) {
                    // Still unbounded, but a transfer task wedged in native LMDB would
                    // otherwise be an unattributable hang.
                    LOGGER.warn(() -> "Still waiting for the writer to stop for env " + env.getDir());
                } catch (final InterruptedException e) {
                    LOGGER.debug(e.getMessage(), e);
                    interrupted = true;
                } catch (final ExecutionException e) {
                    // The transfer loop failed, but it has exited, so its write txn has been
                    // closed and the caller may still close the env. Surface the failure.
                    failure = e.getCause();
                    break;
                }
            }
        } finally {
            if (interrupted) {
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();
            }
        }

        if (failure != null) {
            throw new RuntimeException(LogUtil.message("Error closing writer: {}",
                    LogUtil.exceptionMessage(failure)), failure);
        }
    }

    private void put(final Consumer<WriteTxn> newConsumer,
                     final boolean newClosedState) {
        try {
            final ReentrantLock lock = this.lock;
            lock.lockInterruptibly();
            try {
                if (closed) {
                    throw new RuntimeException("Closed");
                }
                if (terminated) {
                    throw new RuntimeException("Writer terminated");
                }

                // Ensure transfer has finished consuming the previous item.
                while (consumer != null && !terminated) {
                    notFull.await();
                }
                if (terminated) {
                    // The transfer loop died (an earlier write failed); without this check we
                    // would wait forever below for a consumer that no longer exists.
                    throw new RuntimeException("Writer terminated");
                }

                consumer = newConsumer;
                closed = newClosedState;
                notEmpty.signal();

                // Wait for transfer to consume the item.
                while (consumer != null && !terminated) {
                    notFull.await();
                }
                if (consumer != null) {
                    // The transfer loop died before consuming our item.
                    consumer = null;
                    throw new RuntimeException("Writer terminated before the write was performed");
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            LOGGER.debug(e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        } catch (final Exception e) {
            LOGGER.error(() -> LogUtil.message("Error doing put: {}", LogUtil.exceptionMessage(e), e));
            throw e;
        }
    }

    private void transfer() {
        try {
            try (final WriteTxn writeTxn = env.writeTxn()) {
                try {
                    while (!closed) {
                        final ReentrantLock lock = this.lock;
                        lock.lockInterruptibly();
                        try {
                            while (!closed && consumer == null) {
                                notEmpty.await();
                            }
                            try {
                                if (consumer != null) {
                                    consumer.accept(writeTxn);
                                }
                            } finally {
                                consumer = null;
                                notFull.signal();
                            }
                        } finally {
                            lock.unlock();
                        }
                    }
                } finally {
                    LOGGER.debug("close called");
                    LOGGER.trace(() -> "close()", new RuntimeException("close"));
                    try {
                        // Final commit.
                        writeTxn.commit();
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }
            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
                throw new UncheckedInterruptedException(e);
            }
        } finally {
            // The write txn is closed by this point however we got here. Record that this
            // loop has exited and wake any thread waiting in put(), which would otherwise
            // wait forever for a consumer that will never come.
            lock.lock();
            try {
                terminated = true;
                notFull.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
