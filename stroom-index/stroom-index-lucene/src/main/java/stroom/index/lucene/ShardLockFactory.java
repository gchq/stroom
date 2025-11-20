package stroom.index.lucene;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.LockObtainFailedException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ShardLockFactory extends LockFactory {

    private static final LambdaLogger LOGGER;

    static {
        LOGGER = LambdaLoggerFactory.getLogger(ShardLockFactory.class);
    }

    private final Map<ShardLockKey, Lock> lockMap = new ConcurrentHashMap<>();

    @Override
    public Lock obtainLock(final Directory dir, final String lockName) throws IOException {
        final FSDirectory fsDirectory = (FSDirectory) dir;
        final String canonicalPath = FileUtil.getCanonicalPath(fsDirectory.getDirectory());
        final ShardLockKey lockKey = new ShardLockKey(canonicalPath, lockName);

        try {
            LOGGER.trace(() -> "obtainLock() - " + lockKey);
            return lockMap.compute(lockKey, (k, v) -> {
                if (v != null) {
                    LOGGER.debug(() -> "Lock instance already obtained: " + k);
                    throw new RuntimeException("Lock instance already obtained: " + k);
                }

                return new ShardLock(ShardLockFactory.this, k);
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            throw new LockObtainFailedException(e.getMessage());
        }
    }

    boolean containsKey(final ShardLockKey lockKey) {
        LOGGER.trace(() -> "containsKey() - " + lockKey);
        return lockMap.containsKey(lockKey);
    }

    Lock remove(final ShardLockKey lockKey) {
        LOGGER.trace(() -> "remove() - " + lockKey);
        return lockMap.remove(lockKey);
    }
}
