package stroom.lmdb.topic;

import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.WriteTxn;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import java.util.function.Consumer;

class DbWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbWriter.class);
    private static final int MAX_ACTIONS_UNCOMMITTED = 1_000;
    private static final long MAX_TIME_BETWEEN_COMMITS_MS = 1_000L;

    private final LmdbEnv lmdbEnv;
    private final TransferQueue<TxnAction> actionTransferQueue;
    private final ExecutorService executorService;
    private WriteTxn writeTxn;
    private Long nextCommitTimeEpochMs = System.currentTimeMillis();
    private int actionsSinceLastCommit = 0;

    public DbWriter(final LmdbEnv lmdbEnv) {
        this.lmdbEnv = lmdbEnv;
        this.actionTransferQueue = new LinkedTransferQueue<>();
        this.executorService = Executors.newSingleThreadExecutor();
        // We are the only writer so can hold the txn open indefinitely
        this.writeTxn = lmdbEnv.openWriteTxn();
        executorService.submit(this::consumeActions);
    }

    private void consumeActions() {
        while (!Thread.currentThread().isInterrupted()) {
            final TxnAction txnAction = actionTransferQueue.poll(2, TimeUnit);
            if (txnAction != null) {
                try {
                    if (writeTxn == null) {
                        writeTxn = lmdbEnv.openWriteTxn();
                    }
                    txnAction.run(writeTxn.getTxn());
                    if (txnAction.shouldCommit()
                            || actionsSinceLastCommit > MAX_ACTIONS_UNCOMMITTED
                            || (nextCommitTimeEpochMs != null
                            && System.currentTimeMillis() > nextCommitTimeEpochMs)) {
                        writeTxn.commit();
                        writeTxn.close();
                        writeTxn = null;
                        nextCommitTimeEpochMs = null;
                        actionsSinceLastCommit = 0;
                    } else {
                        if (nextCommitTimeEpochMs == null) {
                            nextCommitTimeEpochMs = System.currentTimeMillis() + MAX_TIME_BETWEEN_COMMITS_MS;
                        }
                        actionsSinceLastCommit++;
                    }
                } catch (Exception e) {
                    LOGGER.error("Error running action: {}", e.getMessage(), e);
                    // TODO Can't throw, need to put ex in a future or similar
                } finally {
                    NullSafe.consume(writeTxn, WriteTxn::close);
                }
            }
        }
    }

    public void putSync(final boolean commit, final Consumer<Txn<ByteBuffer>> action) {
        if (action != null) {
            try {
                actionTransferQueue.transfer(new TxnAction(action, commit));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }
        }
    }

    // TODO may want to return a Future or accept a callback
    public void putAsync(final boolean commit, final Consumer<Txn<ByteBuffer>> action) {
        if (action != null) {
            try {
                actionTransferQueue.put(new TxnAction(action, commit));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }
        }
    }
}
