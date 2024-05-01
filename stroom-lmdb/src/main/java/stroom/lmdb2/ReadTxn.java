package stroom.lmdb2;

import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class ReadTxn extends AbstractTxn {

    private Txn<ByteBuffer> txn;

    ReadTxn(final Env<ByteBuffer> env, final LmdbErrorHandler lmdbErrorHandler) {
        super(env, lmdbErrorHandler);
    }

    @Override
    synchronized Txn<ByteBuffer> get() {
        checkThread();
        try {
            if (txn == null) {
                txn = env.txnRead();
            }
            return txn;
        } catch (final RuntimeException e) {
            lmdbErrorHandler.error(e);
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        checkThread();
        try {
            if (txn != null) {
                txn.close();
            }
        } catch (final RuntimeException e) {
            lmdbErrorHandler.error(e);
            throw e;
        } finally {
            txn = null;
        }
    }
}
