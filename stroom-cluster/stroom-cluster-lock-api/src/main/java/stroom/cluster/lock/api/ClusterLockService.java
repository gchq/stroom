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

package stroom.cluster.lock.api;

import java.util.function.Supplier;

/**
 * tryLock and release lock utilises the cluster but the master node must be up
 * to work
 * <p>
 * lock uses the database
 */
public interface ClusterLockService {

    /**
     * Will attempt to get the database backed lock for lockName. If it gets the lock then will run
     * runnable under that lock. If not it will return without running runnable.
     */
    void tryLock(final String lockName, final Runnable runnable);

    /**
     * Will block until the database backed lock for lockName is obtained, then will run
     * runnable under that lock
     */
    void lock(final String lockName, final Runnable runnable);

    /**
     * Will block until the database backed lock for lockName is obtained, then will run
     * supplier under that lock, returning supplier's return value
     */
    <T> T lockResult(final String lockName, final Supplier<T> supplier);
}
