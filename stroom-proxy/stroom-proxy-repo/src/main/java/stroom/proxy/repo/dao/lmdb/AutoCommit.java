package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.dao.lmdb.LmdbEnv.WriteFunction;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;

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
    public Txn<ByteBuffer> apply(final Txn<ByteBuffer> txn) {
        count++;
        if (count > maxItems) {
            count = 0;
            lastCommit = Instant.now();
            txn.commit();
            txn.close();
            return null;
        } else {
            final Instant now = Instant.now();
            if (now.minus(maxTime).isAfter(lastCommit)) {
                count = 0;
                lastCommit = now;
                txn.commit();
                txn.close();
                return null;
            }
        }
        return txn;
    }
}
