/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.index.lucene553;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene553.store.Directory;
import org.apache.lucene553.store.FSDirectory;
import org.apache.lucene553.store.Lock;
import org.apache.lucene553.store.LockFactory;
import org.apache.lucene553.store.LockObtainFailedException;

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
