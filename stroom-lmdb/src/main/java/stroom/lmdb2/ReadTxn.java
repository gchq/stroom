package stroom.lmdb2;

import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class ReadTxn implements AutoCloseable {

    private final Env<ByteBuffer> env;
    private Txn<ByteBuffer> txn;

    public ReadTxn(final Env<ByteBuffer> env) {
        this.env = env;
    }

    Txn<ByteBuffer> get() {
        if (txn == null) {
            txn = env.txnRead();
        }
        return txn;
    }

    @Override
    public void close() {
        if (txn != null) {
            txn.close();
            txn = null;
        }
    }
}
