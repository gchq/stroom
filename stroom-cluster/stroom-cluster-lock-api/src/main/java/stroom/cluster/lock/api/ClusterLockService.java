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
 * Cluster wide mutual exclusion, used to ensure that only one node at a time runs some piece of
 * work.
 * <p>
 * All methods are backed by a row lock on the {@code cluster_lock} table. <b>No master node and no
 * inter-node communication is involved</b>, so a lock can be taken by any node and works whether or
 * not a master has been elected. This makes a cluster lock the preferred way to say "only one node
 * should do this at a time" in preference to gating work on the node being the master.
 * <p>
 * The lock is held for as long as the supplied runnable/supplier takes to run and is released when
 * its transaction commits, so the work done under a lock should be bounded.
 * <p>
 * Locking is <b>not fair</b>. A node waiting on a lock that is held for longer than the database
 * lock wait timeout will rejoin the back of the queue, so waiters are not served in arrival order.
 * <p>
 * Lock rows are created on demand the first time a name is used and are never deleted.
 */
public interface ClusterLockService {

    /**
     * Will attempt to get the database backed lock for lockName. If it gets the lock then will run
     * runnable under that lock. If not it will return immediately without running runnable.
     * <p>
     * Use this for periodic work where missing a cycle is harmless, as a contended lock skips the
     * work rather than queueing a thread behind it.
     */
    void tryLock(final String lockName, final Runnable runnable);

    /**
     * Will block until the database backed lock for lockName is obtained, then will run
     * runnable under that lock.
     *
     * @throws RuntimeException if the lock could not be obtained within the configured lock
     *                          timeout.
     */
    void lock(final String lockName, final Runnable runnable);

    /**
     * Will block until the database backed lock for lockName is obtained, then will run
     * supplier under that lock, returning supplier's return value.
     *
     * @throws RuntimeException if the lock could not be obtained within the configured lock
     *                          timeout.
     */
    <T> T lockResult(final String lockName, final Supplier<T> supplier);
}
