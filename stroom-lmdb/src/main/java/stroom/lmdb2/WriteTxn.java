package stroom.lmdb2;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class WriteTxn implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WriteTxn.class);

    private final Env<ByteBuffer> env;
    private Txn<ByteBuffer> txn;

    WriteTxn(final Env<ByteBuffer> env) {
        this.env = env;
    }

    public Txn<ByteBuffer> get() {
        try {
            if (txn == null) {
                txn = env.txnWrite();
            }
            return txn;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void commit() {
        try {
            if (txn != null) {
                txn.commit();
                txn.close();
                txn = null;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    @Override
    public void close() {
        try {
            if (txn != null) {
                txn.close();
                txn = null;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }
}
