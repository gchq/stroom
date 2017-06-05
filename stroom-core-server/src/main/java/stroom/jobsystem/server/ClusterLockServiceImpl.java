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

import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;

import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.util.logging.StroomLogger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.shared.SQLNameConstants;
import stroom.jobsystem.shared.ClusterLock;
import stroom.node.server.NodeCache;
import stroom.task.server.TaskManager;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.SharedBoolean;
import stroom.util.spring.StroomFrequencySchedule;

@Component
public class ClusterLockServiceImpl implements ClusterLockService {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ClusterLockServiceImpl.class);

    @Resource
    private StroomEntityManager entityManager;
    @Resource
    private StroomDatabaseInfo stroomDatabaseInfo;
    @Resource
    private ClusterLockServiceTransactionHelper clusterLockServiceTransactionHelper;
    @Resource
    private TaskManager taskManager;
    @Resource
    private NodeCache nodeCache;

    private final ConcurrentHashMap<String, ClusterLockKey> lockMap = new ConcurrentHashMap<>();

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(final String lockName) {
        LOGGER.debug("lock(%s) - >>>", lockName);

        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        // This happens outside this transaction
        clusterLockServiceTransactionHelper.checkLockCreated(lockName);

        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT * FROM ");
        sql.append(ClusterLock.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(SQLNameConstants.NAME);
        sql.append(" = ");
        sql.arg(lockName);

        // Here we lock the records read until the transaction commits.
        if (stroomDatabaseInfo.isMysql()) {
            sql.append(" FOR UPDATE");
        }

        final List<ClusterLock> result = entityManager.executeNativeQueryResultList(sql, ClusterLock.class);
        if (result == null || result.size() != 1) {
            throw new IllegalStateException("No cluster lock has been found or created: " + lockName);
        }

        LOGGER.debug("lock(%s) - <<< %s", lockName, logExecutionTime);
    }

    @Override
    public boolean tryLock(final String lockName) {
        LOGGER.debug("tryLock(%s) - >>>", lockName);
        boolean success = false;

        // Don't bother the master node if we already hold the lock.
        ClusterLockKey clusterLockKey = lockMap.get(lockName);
        if (clusterLockKey == null) {
            clusterLockKey = new ClusterLockKey(lockName, nodeCache.getDefaultNode().getName(),
                    System.currentTimeMillis());
            final SharedBoolean didLock = taskManager.exec(new ClusterLockTask(clusterLockKey, ClusterLockStyle.Try));
            if (didLock != null) {
                success = Boolean.TRUE.equals(didLock.getBoolean());
            }

            if (success) {
                // We managed to acquire this cluster lock so add it to the lock
                // set for the purposes of keep alive.
                lockMap.put(lockName, clusterLockKey);
            }
        }

        LOGGER.debug("tryLock(%s) - <<< %s", lockName, success);
        return success;
    }

    @Override
    public void releaseLock(final String lockName) {
        LOGGER.debug("releaseLock(%s) - >>>", lockName);
        // Remove the lock name from the lock map.
        final ClusterLockKey clusterLockKey = lockMap.remove(lockName);

        boolean success = false;
        if (clusterLockKey == null) {
            LOGGER.error("releaseLock(%s) - Lock not found", lockName);
        } else {
            final SharedBoolean sharedBoolean = taskManager
                    .exec(new ClusterLockTask(clusterLockKey, ClusterLockStyle.Release));
            if (sharedBoolean != null) {
                success = sharedBoolean.getBoolean();
            }
        }

        LOGGER.debug("releaseLock(%s) - <<< %s", lockName, success);
    }

    @StroomFrequencySchedule("1m")
    public void keepAlive() {
        LOGGER.debug("keepAlive() - >>>");

        for (final Entry<String, ClusterLockKey> entry : lockMap.entrySet()) {
            final String lockName = entry.getKey();
            final ClusterLockKey clusterLockKey = entry.getValue();

            LOGGER.debug("keepAlive(%s) - >>>", lockName);
            final SharedBoolean success = taskManager
                    .exec(new ClusterLockTask(clusterLockKey, ClusterLockStyle.KeepAlive));
            LOGGER.debug("keepAlive(%s) - <<< %s", lockName, success);

            // We should only receive FALSE if the master node knows nothing
            // about the lock we are trying to keep alive. This should only ever
            // be the case if we have told master to release the lock, so double
            // check that we expected to keep this lock alive as the map might
            // have coincidentally had the lock removed in the releaseLock()
            // method.
            if (Boolean.FALSE.equals(success.getBoolean())) {
                final ClusterLockKey currentKey = lockMap.get(lockName);
                if (currentKey != null && currentKey.equals(clusterLockKey)) {
                    LOGGER.error("keepAlive() - Lock no longer exists - %s", clusterLockKey);
                }
            }
        }

        LOGGER.debug("keepAlive() - <<<");
    }
}
