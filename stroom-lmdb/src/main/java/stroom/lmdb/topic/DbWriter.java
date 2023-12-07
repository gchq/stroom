package stroom.lmdb.topic;

import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.WriteTxn;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

class DbWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbWriter.class);
    private static final int MAX_ACTIONS_UNCOMMITTED = 1_000;
    private static final long MAX_TIME_BETWEEN_COMMITS_MS = 1_000L;

    private final LmdbEnv lmdbEnv;
    private final TransferQueue<TxnAction<?>> actionTransferQueue;
    private final ExecutorService executorService;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private WriteTxn writeTxn;
    private Long nextCommitTimeEpochMs = null;
    private int actionsSinceLastCommit = 0;

    public DbWriter(final LmdbEnv lmdbEnv) {
        this.lmdbEnv = lmdbEnv;
        this.actionTransferQueue = new LinkedTransferQueue<>();
        this.executorService = Executors.newSingleThreadExecutor();
        // We are the only writer so can hold the txn open indefinitely
//        this.writeTxn = lmdbEnv.openWriteTxn();

        executorService.submit(this::consumeTxnActions);
    }

    private void consumeTxnActions() {
        // Keep on looping checking for things
        TxnAction<?> txnAction;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    txnAction = actionTransferQueue.poll(2L, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Thread interrupted");
                    break;
                }
                if (txnAction != null) {
                    final TxnAction<?> txnActionFinal = txnAction;
                    LOGGER.info("Got action to execute");
                    try {
                        checkTxn();
                        // Any exception should get captured by the future in txnAction
                        txnAction.execute(writeTxn.getTxn());
                        actionsSinceLastCommit++;
                        if (txnAction.shouldCommit()
                                || actionsSinceLastCommit > MAX_ACTIONS_UNCOMMITTED
                                || (nextCommitTimeEpochMs != null
                                && System.currentTimeMillis() > nextCommitTimeEpochMs)) {
                            LOGGER.info(() -> LogUtil.message(
                                    "Committing, shouldCommit: {}, actionsSinceLastCommit: {}, nextCommitTimeEpoch: {}",
                                    txnActionFinal.shouldCommit(),
                                    actionsSinceLastCommit,
                                    NullSafe.get(nextCommitTimeEpochMs, Instant::ofEpochMilli)));
                            commitAndCloseTxn();
                            nextCommitTimeEpochMs = null;
                            actionsSinceLastCommit = 0;
                        } else {
                            if (nextCommitTimeEpochMs == null) {
                                nextCommitTimeEpochMs = System.currentTimeMillis() + MAX_TIME_BETWEEN_COMMITS_MS;
                            }
                        }
                    } catch (Exception e) {
                        // Likely an exception committing or closing
                        LOGGER.error("Error running action: {}", e.getMessage(), e);
                        NullSafe.consume(writeTxn, WriteTxn::abort);
                        NullSafe.consume(writeTxn, WriteTxn::close);
                        // Need to swallow and carry on round
                    }
                }
            }
        } finally {
            NullSafe.consume(writeTxn, WriteTxn::close);
        }
    }

    private void checkTxn() {
        if (writeTxn == null) {
            writeTxn = lmdbEnv.openWriteTxn();
            LOGGER.info("Opened txn");
        }
    }

    private void commitAndCloseTxn() {
        if (writeTxn != null) {
            writeTxn.commit();
            writeTxn.close();
            writeTxn = null;
            LOGGER.info("Committed and closed txn");
        }
    }

    public void putSync(final boolean commit,
                        final Consumer<Txn<ByteBuffer>> action)
            throws InterruptedException {
        LOGGER.info("putSync called, commit: {}", commit);
        checkShutdown();
        if (action != null) {
            actionTransferQueue.transfer(TxnAction.create(action, commit));
        }
    }

    // TODO may want to return a Future or accept a callback
    public void putAsync(final boolean commit,
                         final Consumer<Txn<ByteBuffer>> action) {
        LOGGER.info("putAsync called, commit: {}", commit);
        checkShutdown();
        if (action != null) {
            try {
                final TxnAction<Void> txnAction = TxnAction.create(action, commit);
                actionTransferQueue.put(txnAction);
            } catch (InterruptedException e) {
                // Not a bounded queue so will should never block and therefore never throw this
                Thread.currentThread().interrupt();
                throw new RuntimeException("Should never get here");
            }
        }
    }

    /**
     * Issue a commit and wait for it to complete.
     */
    public void commitSync() throws InterruptedException {
        final TxnAction<Void> txnAction = TxnAction.commitOnly();
        actionTransferQueue.transfer(txnAction);
    }

    /**
     * Issue a commit, returning immediately.
     */
    public CompletableFuture<Void> commitAsync() {
        final TxnAction<Void> txnAction = TxnAction.commitOnly();
        try {
            actionTransferQueue.put(txnAction);
            return txnAction.geFuture();
        } catch (InterruptedException e) {
            // Not a bounded queue so will never block and therefore never throw this
            Thread.currentThread().interrupt();
            throw new RuntimeException("Should never get here");
        }
    }


    public void shutdown() {
        while (!actionTransferQueue.isEmpty()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                LOGGER.error("Orderly shutdown aborted, queue size: {}",
                        actionTransferQueue.size());
                Thread.currentThread().interrupt();
                break;
            }
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error("Orderly shutdown aborted");
            throw new RuntimeException(e);
        }
        NullSafe.consume(writeTxn, WriteTxn::commit);
        NullSafe.consume(writeTxn, WriteTxn::close);
    }

    private void checkShutdown() {
        if (isShuttingDown.get()) {
            throw new RuntimeException("Shutting down");
        }
    }
}
