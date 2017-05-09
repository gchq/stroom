/*
 * Copyright 2016 Crown Copyright
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

package stroom.jobsystem.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.SharedBoolean;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomScope;

import java.util.concurrent.ConcurrentHashMap;

@TaskHandlerBean(task = ClusterLockClusterTask.class)
@Scope(value = StroomScope.SINGLETON)
public class ClusterLockClusterHandler extends AbstractTaskHandler<ClusterLockClusterTask, SharedBoolean> {
    private static class Lock {
        private final ClusterLockKey clusterLockKey;
        private volatile long refreshTime;

        public Lock(final ClusterLockKey clusterLockKey) {
            this.clusterLockKey = clusterLockKey;
            refresh();
        }

        public void refresh() {
            this.refreshTime = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            append(sb);
            return sb.toString();
        }

        public void append(final StringBuilder sb) {
            final long age = System.currentTimeMillis() - refreshTime;

            clusterLockKey.append(sb);
            sb.append(" age=");
            sb.append(ModelStringUtil.formatDurationString(age));
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterLockClusterHandler.class);

    // 10 min
    public static final long TEN_MINUTES = 10 * 60 * 1000;

    private final ConcurrentHashMap<String, Lock> lockMap = new ConcurrentHashMap<>();

    @Override
    public SharedBoolean exec(final ClusterLockClusterTask task) {
        boolean success = false;

        final ClusterLockKey clusterLockKey = task.getKey();
        switch (task.getLockStyle()) {
        case Try:
            success = tryLock(clusterLockKey);
            break;
        case Release:
            success = release(clusterLockKey);
            break;
        case KeepAlive:
            success = keepAlive(clusterLockKey);
            break;
        }

        return new SharedBoolean(success);
    }

    /**
     * Try and lock with the supplied key.
     *
     * @param clusterLockKey
     *            The key to try and lock with.
     * @return Return true if we managed to obtain a lock with the supplied key,
     *         return false if this lock is already owned by another
     *         node/process.
     */
    private boolean tryLock(final ClusterLockKey clusterLockKey) {
        boolean success = false;

        try {
            final String lockName = clusterLockKey.getName();
            final Lock currentLock = lockMap.get(lockName);
            if (currentLock == null) {
                final Lock newLock = new Lock(clusterLockKey);
                // Another node might have concurrently put a lock so only put
                // this new lock if no lock is present. This method will return
                // the lock we are trying to put if one does not already exist
                // and we successfully add this one.
                lockMap.putIfAbsent(lockName, newLock);
                final Lock lock = lockMap.get(lockName);
                // Check that the current lock is EXACTLY (hence ==) the one we
                // have just tried to put.
                if (lock == newLock) {
                    success = true;
                }
            }

            debug("lock()", clusterLockKey, currentLock, success);
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    /**
     * Try and release a lock with the supplied key.
     *
     * @param clusterLockKey
     *            The key to try and release an associated lock with.
     * @return Return true if we managed to release a lock with the supplied
     *         key, return false if no lock could be found for this key or if
     *         the lock is owned by another node/process.
     */
    private boolean release(final ClusterLockKey clusterLockKey) {
        boolean success = false;

        try {
            final String lockName = clusterLockKey.getName();
            final Lock currentLock = lockMap.get(lockName);
            // We should know about a lock that a node is trying to release. If
            // we don't then we must have removed it in unlockOld() which should
            // not happen if the node that holds the lock has been able to keep
            // the lock alive.
            if (currentLock != null) {
                // Make sure the node trying to release the lock is the node
                // that we think actually holds it. If unlockOld() has
                // previously removed the lock and so allowed another node to
                // obtain the lock then we should not allow the previous owner
                // to release it.
                if (currentLock.clusterLockKey.equals(clusterLockKey)) {
                    success = lockMap.remove(lockName, currentLock);
                } else {
                    error("unlock() - Attempt to unlock with a different key to the owner", clusterLockKey,
                            currentLock);
                }
            } else {
                error("unlock() - Attempt to unlock when was not locked", clusterLockKey, currentLock);
            }

            debug("unlock()", clusterLockKey, currentLock, success);
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    /**
     * Try and keep alive a lock with the supplied key.
     *
     * @param clusterLockKey
     *            The key to try and keep alive an associated lock with.
     * @return Return true if we managed to keep alive a lock with the supplied
     *         key, return false if no lock could be found for this key or if
     *         the lock is owned by another node/process.
     */
    private boolean keepAlive(final ClusterLockKey clusterLockKey) {
        boolean success = false;

        try {
            final String lockName = clusterLockKey.getName();
            final Lock currentLock = lockMap.get(lockName);
            // We should know about a lock that a node is trying to keep alive,
            // but it is possible due to the asynchronous nature of server calls
            // that the owner has already released the lock.
            if (currentLock != null) {
                // Only refresh the lock if it is the owner that is trying to
                // keep it alive. See previous comment as to why this might not
                // be the case.
                if (currentLock.clusterLockKey.equals(clusterLockKey)) {
                    currentLock.refresh();
                    success = true;
                } else {
                    // The owning node might have unlocked this key at the same
                    // time as trying to keep the lock alive - another node
                    // might have then grabbed the lock. As the calls are
                    // asynchronous this situation is quite possible/probable
                    // and should not be considered an error - hence debug.
                    debug("keepAlive() - Attempt to keep alive with a different key to the owner", clusterLockKey,
                            currentLock, null);
                }
            } else {
                // The owning node might have unlocked this key at the same time
                // as trying to keep the lock alive. As the calls are
                // asynchronous this situation is quite possible/probable and
                // should not be considered an error - hence debug.
                debug("keepAlive() - Attempt to keep alive when was not locked", clusterLockKey, currentLock, null);
            }

            debug("keepAlive()", clusterLockKey, currentLock, success);
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    /**
     * Every 10 minutes try and unlock/remove any locks that we hold that have
     * not been refreshed by their owner for 10 minutes.
     */
    @StroomFrequencySchedule("10m")
    public void unlockOldLocks() {
        final long oldTime = System.currentTimeMillis() - TEN_MINUTES;
        for (final Lock lock : lockMap.values()) {
            if (lock.refreshTime < oldTime) {
                // This will only remove the lock if it is still the same lock
                // instance. It should be but there is a slim chance that the
                // owner has released the lock in the mean time which is
                // acceptable.
                final boolean success = lockMap.remove(lock.clusterLockKey.getName(), lock);
                if (success) {
                    error("unlockOld() - Removed old lock", null, lock);
                }
            }
        }
    }

    private void debug(final String message, final ClusterLockKey clusterLockKey, final Lock lock,
            final Boolean success) {
        if (LOGGER.isDebugEnabled()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(message);
            sb.append(" -");
            appendStatus(sb, clusterLockKey, lock, success);
            LOGGER.debug(sb.toString());
        }
    }

    private void error(final String message, final ClusterLockKey clusterLockKey, final Lock lock) {
        final StringBuilder sb = new StringBuilder();
        sb.append(message);
        sb.append(" -");
        appendStatus(sb, clusterLockKey, lock, null);
        LOGGER.error(sb.toString());
    }

    private void appendStatus(final StringBuilder sb, final ClusterLockKey clusterLockKey, final Lock lock,
            final Boolean success) {
        if (clusterLockKey != null) {
            sb.append(" key=(");
            clusterLockKey.append(sb);
            sb.append(")");
        }
        if (lock != null) {
            sb.append(" lock=(");
            lock.append(sb);
            sb.append(")");
        }
        if (success != null) {
            sb.append(" success=");
            sb.append(success);
        }
    }
}
