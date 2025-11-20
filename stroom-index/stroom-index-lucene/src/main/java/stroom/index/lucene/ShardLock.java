package stroom.index.lucene;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Lock;

class ShardLock extends Lock {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardLock.class);

    private final ShardLockFactory factory;
    private final ShardLockKey lockKey;
    private volatile boolean closed;

    ShardLock(final ShardLockFactory factory, final ShardLockKey lockKey) {
        this.factory = factory;
        this.lockKey = lockKey;
    }

    @Override
    public void ensureValid() {
        LOGGER.trace(() -> "ensureValid() - " + lockKey.toString());

        try {
            if (closed) {
                throw new AlreadyClosedException("Lock instance already released: " + this);
            }
            // check we are still in the locks map (some debugger or something crazy didn't remove us)
            if (!factory.containsKey(lockKey)) {
                throw new AlreadyClosedException("Lock instance is not present in map: " + this);
            }
        } catch (final AlreadyClosedException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        LOGGER.trace(() -> "close() - " + lockKey.toString());

        try {
            if (closed) {
                return;
            }
            try {
                final Lock existing = factory.remove(lockKey);
                if (existing == null) {
                    throw new AlreadyClosedException("Lock was already released: " + this);
                } else if (existing != this) {
                    throw new AlreadyClosedException("Unexpected lock removed for key: " + this);
                }
            } finally {
                closed = true;
            }
        } catch (final AlreadyClosedException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    @Override
    public String toString() {
        return super.toString() + ": " + lockKey.toString();
    }
}
