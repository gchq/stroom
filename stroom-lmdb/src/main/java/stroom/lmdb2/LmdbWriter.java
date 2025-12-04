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
    private boolean closed;
    private Consumer<WriteTxn> consumer;

    public LmdbWriter(final Provider<Executor> executorProvider,
                      final LmdbEnv env) {
        this.env = env;
        lock = new ReentrantLock();
        notFull = lock.newCondition();
        notEmpty = lock.newCondition();

        // Start transfer loop.
        executorProvider.get().execute(this::transfer);
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

    public synchronized void close() {
        put(null, true);
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

                // Ensure transfer has finished consuming the previous item.
                while (consumer != null) {
                    notFull.await();
                }

                consumer = newConsumer;
                closed = newClosedState;
                notEmpty.signal();

                // Wait for transfer to consume the item.
                while (consumer != null) {
                    notFull.await();
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new UncheckedInterruptedException(e);
        } catch (final Exception e) {
            LOGGER.error(() -> LogUtil.message("Error doing put: {}", LogUtil.exceptionMessage(e), e));
            throw e;
        }
    }

    private void transfer() {
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
    }
}
