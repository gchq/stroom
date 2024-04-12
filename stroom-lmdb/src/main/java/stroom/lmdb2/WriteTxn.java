package stroom.lmdb2;

import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class WriteTxn implements AutoCloseable {

    private final Env<ByteBuffer> env;
    private Txn<ByteBuffer> txn;

    public WriteTxn(final Env<ByteBuffer> env) {
        this.env = env;
    }

    public Txn<ByteBuffer> get() {
        if (txn == null) {
            txn = env.txnWrite();
        }
        return txn;
    }

    public void commit() {
        if (txn != null) {
            txn.commit();
            txn.close();
            txn = null;
        }
    }

    @Override
    public void close() {
        if (txn != null) {
            txn.close();
            txn = null;
        }
    }
}
