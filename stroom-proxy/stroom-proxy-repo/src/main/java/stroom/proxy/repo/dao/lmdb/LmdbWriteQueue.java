package stroom.proxy.repo.dao.lmdb;

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class LmdbWriteQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbWriteQueue.class);
    private static final Commit COMMIT = new Commit();

    private final ArrayBlockingQueue<WriteCommand> writeQueue = new ArrayBlockingQueue<>(1000);

    public LmdbWriteQueue(final Env<ByteBuffer> env) {
        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info(() -> "Starting write thread");

                Txn<ByteBuffer> writeTxn = env.txnWrite();
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            final WriteCommand writeCommand = writeQueue.take();
                            writeCommand.accept(writeTxn);

                            if (writeCommand.commit()) {
                                writeTxn.commit();
                                writeTxn.close();
                                writeTxn = env.txnWrite();
                            }
                        } catch (final InterruptedException e) {
                            throw new UncheckedInterruptedException(e);
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                        }
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                } finally {
                    writeTxn.commit();
                    writeTxn.close();
                }
                LOGGER.info(() -> "Finished write thread");
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    public void write(final WriteCommand writeCommand) {
        try {
            writeQueue.put(writeCommand);
        } catch (final InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        }
    }

    public void sync() {
        try {
            final Sync sync = new Sync();
            write(sync);
            sync.await();
        } catch (final InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        }
    }

    public void commit() {
        write(COMMIT);
    }

    private static class Sync implements WriteCommand {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Sync.class);

        final CountDownLatch complete = new CountDownLatch(1);

        @Override
        public void accept(final Txn<ByteBuffer> txn) {
            LOGGER.info(() -> "sync countdown before");
            complete.countDown();
            LOGGER.info(() -> "sync countdown after");
        }

        public void await() throws InterruptedException {
            LOGGER.info(() -> "sync await before");
            complete.await();
            LOGGER.info(() -> "sync await after");
        }

        @Override
        public boolean commit() {
            return true;
        }
    }

    private static class Commit implements WriteCommand {

        @Override
        public void accept(final Txn<ByteBuffer> txn) {

        }

        @Override
        public boolean commit() {
            return true;
        }
    }

    public interface WriteCommand {

        void accept(Txn<ByteBuffer> txn);

        default boolean commit() {
            return false;
        }
    }
}
