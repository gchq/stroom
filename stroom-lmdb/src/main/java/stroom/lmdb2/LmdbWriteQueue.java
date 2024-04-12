package stroom.lmdb2;

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class LmdbWriteQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbWriteQueue.class);

    private final LmdbEnv2 env;
    private final ArrayBlockingQueue<WriteFunction> writeQueue = new ArrayBlockingQueue<>(1000);
    private volatile WriteFunction autoCommit = txn -> {
    };

    private volatile boolean writing;
    private volatile Thread writingThread;
    private volatile CompletableFuture<Void> completableFuture;

    public LmdbWriteQueue(final LmdbEnv2 env) {
        this.env = env;
    }

    public synchronized void start() {
        if (!writing) {
            writing = true;
            completableFuture = CompletableFuture.runAsync(() -> {
                try {
                    LOGGER.info(() -> "Starting write thread");
                    writingThread = Thread.currentThread();
                    final WriteTxn writeTxn = env.txnWrite();
                    try {
                        while (writing) {
                            try {
                                final WriteFunction writeCommand = writeQueue.take();
                                writeCommand.accept(writeTxn);
                                autoCommit.accept(writeTxn);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    } catch (final InterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        Thread.currentThread().interrupt();
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    } finally {
                        if (writeTxn != null) {
                            writeTxn.commit();
                        }
                        writing = false;
                    }
                    LOGGER.info(() -> "Finished write thread");
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            });
        }
    }

    public synchronized void stop() {
        if (writing) {
            writing = false;
            Thread writingThread = this.writingThread;
            if (writingThread != null) {
                writingThread.interrupt();
            }
            completableFuture.join();
            completableFuture = null;
        }
    }

    public void write(final Consumer<WriteTxn> consumer) {
        try {
            final TransferQueue<Boolean> txns = new LinkedTransferQueue<>();
            writeFunctionAsync(txn -> {
                consumer.accept(txn);

                try {
                    txns.put(true);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new UncheckedInterruptedException(e);
                }
            });
            txns.take();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public <R> R writeResult(final Function<WriteTxn, R> function) {
        try {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final AtomicReference<R> ref = new AtomicReference<>();
            writeFunctionAsync(txn -> {
                ref.set(function.apply(txn));
                countDownLatch.countDown();
            });
            countDownLatch.await();
            return ref.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public void writeAsync(final Consumer<WriteTxn> consumer) {
        writeFunctionAsync(consumer::accept);
    }

    public void writeFunctionAsync(final WriteFunction function) {
        try {
            writeQueue.put(function);
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public void sync() {
        try {
            final Sync sync = new Sync();
            writeFunctionAsync(sync);
            LOGGER.info("Synchronizing DB");
            sync.await();
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public void setAutoCommit(final AutoCommit autoCommit) {
        this.autoCommit = autoCommit;
    }

    private static class Sync implements WriteFunction {

        final CountDownLatch complete = new CountDownLatch(1);

        @Override
        public void accept(final WriteTxn writeTxn) {
            writeTxn.commit();
            complete.countDown();
        }

        public void await() throws InterruptedException {
            complete.await();
        }
    }

    public interface WriteFunction extends Consumer<WriteTxn> {

    }

    public class AutoCommit implements WriteFunction {

        private final long maxItems;
        private final Duration maxTime;
        private Instant lastCommit = Instant.now();
        private long count;

        public AutoCommit(final long maxItems, final Duration maxTime) {
            this.maxItems = maxItems;
            this.maxTime = maxTime;
        }

        @Override
        public void accept(final WriteTxn writeTxn) {
            count++;
            if (count > maxItems) {
                count = 0;
                lastCommit = Instant.now();
                writeTxn.commit();
            } else {
                final Instant now = Instant.now();
                if (now.minus(maxTime).isAfter(lastCommit)) {
                    count = 0;
                    lastCommit = now;
                    writeTxn.commit();
                }
            }
        }
    }
}
