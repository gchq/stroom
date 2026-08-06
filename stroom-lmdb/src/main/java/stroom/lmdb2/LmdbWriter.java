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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class LmdbWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbWriter.class);

    private final LmdbEnv env;
    private final ReentrantLock lock;
    private final Condition notFull;
    private final Condition notEmpty;
    private final CompletableFuture<Void> transferFuture;
    private boolean closed;
    // Set (under lock) when the transfer loop has exited, however it exited. Distinct from
    // closed, which is the client asking it to stop; terminated is it having stopped, and is
    // what stops a put() waiting forever on a loop that is no longer there to consume it.
    private boolean terminated;
    private Consumer<WriteTxn> consumer;

    /**
     * The supplied executor must either run or reject the transfer task; an executor that
     * silently drops a submitted task (e.g. {@code shutdownNow()} with the task still queued)
     * leaves {@link #close()} waiting forever, as it has no way to distinguish a dropped task
     * from one that has yet to run. Rejection at submission propagates from this constructor.
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
     * Asks the transfer loop to stop then waits for it to finish, i.e. to have performed its
     * final commit and closed its write txn. Only once this has returned normally (or thrown
     * anything other than {@link UncheckedInterruptedException}) is it safe for the caller to
     * close the env; on interrupt the transfer loop may still be inside its write txn, so the
     * caller must NOT close the env — closing an env under a live txn is undefined behaviour
     * in LMDB.
     * <p>
     * Every blocking point here is interrupt responsive, which is what makes shutdown prompt:
     * threads running under managed task contexts are interrupted at shutdown, escape whatever
     * wait they are in, and the caller then leaks the env (dying with the process) rather than
     * waiting for a writer task that may never finish.
     */
    public synchronized void close() {
        try {
            put(null, true);
        } catch (final UncheckedInterruptedException e) {
            // We can't know the transfer loop has seen the close request, so we can't wait
            // for it either. Propagate; the caller must not close the env.
            throw e;
        } catch (final RuntimeException e) {
            // Already closed, or the transfer loop has already terminated (e.g. an earlier
            // write failed). Either way we still wait for it below.
            LOGGER.debug(e::getMessage, e);
        }
        try {
            transferFuture.get();
        } catch (final InterruptedException e) {
            LOGGER.debug(e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        } catch (final ExecutionException e) {
            // The transfer loop failed, but it has exited, so its write txn has been closed
            // and the caller may safely close the env. Surface the failure.
            throw new RuntimeException(LogUtil.message("Error closing writer: {}",
                    LogUtil.exceptionMessage(e.getCause())), e.getCause());
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
