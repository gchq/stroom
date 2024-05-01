package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.lmdb2.WriteTxn;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.AnalyticResultStoreConfig;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.LmdbKV;
import stroom.query.common.v2.TransferState;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.PutFlags;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Deprecated
@Singleton
public class DuplicateCheckFactoryImpl implements DuplicateCheckFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckFactoryImpl.class);

    private static final long COMMIT_FREQUENCY_MS = 10000;

    private final LmdbEnv lmdbEnv;
    private final LmdbDb db;
    private final ArrayBlockingQueue<WriteOperation> queue;
    private final ByteBufferFactory byteBufferFactory;
    private final int maxPutsBeforeCommit = 100;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final TransferState transferState = new TransferState();
    private final CountDownLatch transferring = new CountDownLatch(1);

    @Inject
    public DuplicateCheckFactoryImpl(final LmdbEnvDirFactory lmdbEnvDirFactory,
                                     final Provider<Executor> executorProvider,
                                     final ByteBufferFactory byteBufferFactory,
                                     final AnalyticResultStoreConfig analyticResultStoreConfig) {
        final LmdbEnvDir lmdbEnvDir = lmdbEnvDirFactory
                .builder()
                .config(analyticResultStoreConfig.getLmdbConfig())
                .subDir("duplicate-check")
                .build();
        this.lmdbEnv = LmdbEnv
                .builder()
                .config(analyticResultStoreConfig.getLmdbConfig())
                .lmdbEnvDir(lmdbEnvDir)
                .maxDbs(1)
                .maxReaders(1)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();
        this.db = lmdbEnv.openDb("duplicate-check", DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT);
        queue = new ArrayBlockingQueue<>(10);
        this.byteBufferFactory = byteBufferFactory;

        // Start transfer loop.
        executorProvider.get().execute(this::transfer);
    }

    @Override
    public DuplicateCheck create(final AnalyticRuleDoc analyticRuleDoc, final CompiledColumns compiledColumns) {
        final DuplicateKeyFactory duplicateKeyFactory = new DuplicateKeyFactory(
                byteBufferFactory,
                analyticRuleDoc,
                compiledColumns);
        return new DuplicateCheckImpl(duplicateKeyFactory, byteBufferFactory, queue);
    }

    private void transfer() {
        Metrics.measure("Transfer", () -> {
            transferState.setThread(Thread.currentThread());

            lmdbEnv.write(writeTxn -> {
                try {
                    long lastCommitMs = System.currentTimeMillis();
                    long uncommittedCount = 0;

                    try {
                        while (!transferState.isTerminated()) {
                            LOGGER.trace(() -> "Transferring");
                            final WriteOperation queueItem = queue.poll(1, TimeUnit.SECONDS);

                            if (queueItem != null) {
                                queueItem.apply(db, writeTxn);
                                uncommittedCount++;
                            }

                            if (uncommittedCount > 0) {
                                final long count = uncommittedCount;
                                if (count >= maxPutsBeforeCommit ||
                                        lastCommitMs < System.currentTimeMillis() - COMMIT_FREQUENCY_MS) {

                                    // Commit
                                    LOGGER.trace(() -> {
                                        if (count >= maxPutsBeforeCommit) {
                                            return "Committing for max puts " + maxPutsBeforeCommit;
                                        } else {
                                            return "Committing for elapsed time";
                                        }
                                    });
                                    writeTxn.commit();
                                    lastCommitMs = System.currentTimeMillis();
                                    uncommittedCount = 0;
                                }
                            }
                        }
                    } catch (final InterruptedException e) {
                        LOGGER.trace(e::getMessage, e);
                        // Keep interrupting this thread.
                        Thread.currentThread().interrupt();
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }

                    if (uncommittedCount > 0) {
                        LOGGER.debug(() -> "Final commit");
                        writeTxn.commit();
                    }

                } catch (final Throwable e) {
                    LOGGER.error(e::getMessage, e);
                } finally {
                    // Ensure we complete.
                    LOGGER.debug(() -> "Finished transfer while loop");
                    transferState.setThread(null);
                    transferring.countDown();
                }
            });
        });
    }

    public synchronized void close() {
        LOGGER.debug(() -> "close called");
        LOGGER.trace(() -> "close()", new RuntimeException("close"));
        if (shutdown.compareAndSet(false, true)) {

            // Let the transfer loop know it should stop ASAP.
            transferState.terminate();

            // Wait for transferring to stop.
            try {
                LOGGER.debug(() -> "Waiting for transfer to stop");
                transferring.await();
            } catch (final InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();
            }

            try {
                lmdbEnv.close();
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    public interface WriteOperation {

        void apply(LmdbDb db, WriteTxn writeTxn);
    }

    private static class DuplicateCheckImpl implements DuplicateCheck {

        private final DuplicateKeyFactory duplicateKeyFactory;
        private final ByteBufferFactory byteBufferFactory;
        private final ArrayBlockingQueue<WriteOperation> queue;

        public DuplicateCheckImpl(final DuplicateKeyFactory duplicateKeyFactory,
                                  final ByteBufferFactory byteBufferFactory,
                                  final ArrayBlockingQueue<WriteOperation> queue) {
            this.duplicateKeyFactory = duplicateKeyFactory;
            this.byteBufferFactory = byteBufferFactory;
            this.queue = queue;
        }

        @Override
        public boolean check(final Row row) {
            final LmdbKV lmdbKV = duplicateKeyFactory.createRow(row);
            boolean result = false;

            try {
                final SynchronousQueue<Boolean> transferQueue = new SynchronousQueue<>();
                final WriteOperation writeOperation = (dbi, writeTxn) -> {
                    boolean success = false;
                    try {
                        success = dbi.put(writeTxn,
                                lmdbKV.getRowKey(),
                                lmdbKV.getRowValue(),
                                PutFlags.MDB_NODUPDATA);
                    } finally {
                        try {
                            transferQueue.put(success);
                        } catch (final InterruptedException e) {
                            LOGGER.error(e::getMessage, e);
                            Thread.currentThread().interrupt();
                        }

                        byteBufferFactory.release(lmdbKV.getRowKey());
                        byteBufferFactory.release(lmdbKV.getRowValue());
                    }
                };
                queue.put(writeOperation);
                result = transferQueue.take();

            } catch (final InterruptedException e) {
                LOGGER.error(e::getMessage, e);
                Thread.currentThread().interrupt();
            }

            return result;
        }

        @Override
        public void close() {

        }
    }
}
