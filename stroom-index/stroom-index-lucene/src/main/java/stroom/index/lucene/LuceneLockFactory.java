/*
 * Copyright 2017 Crown Copyright
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

package stroom.index.lucene;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene.store.LockFactory;

public final class LuceneLockFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneLockFactory.class);
    private static final LockFactory INSTANCE = new ShardLockFactory();

    LuceneLockFactory() {
    }

    /**
     * Get a lock factory for the supplied directory.
     *
     * @param dir The directory to get the lock factory for.
     * @return A lock factory for the supplied directory.
     */
    public static LockFactory get() {
        LOGGER.trace(() -> "get()");
        return INSTANCE;

        // Old impl
        //return SimpleFSLockFactory.INSTANCE;
    }
}
