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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.jobsystem.shared.ClusterLock;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Transactional
@Component
public class ClusterLockServiceTransactionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterLockServiceTransactionHelper.class);
    private final Set<String> registeredLockSet = new HashSet<>();
    @Resource
    private StroomEntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkLockCreated(final String name) {
        LOGGER.debug("Getting cluster lock: " + name);

        if (registeredLockSet.contains(name)) {
            return;
        }

        // I've done this as we should at least only create a lock at a time
        // within the JVM.
        synchronized (this) {
            // Try and get the cluster lock for the job system.
            ClusterLock clusterLock = find(name);
            if (clusterLock == null) {
                clusterLock = new ClusterLock();
                clusterLock.setName(name);
                try {
                    save(clusterLock);
                } catch (final Exception e) {
                    LOGGER.warn("checkLockCreated() - {} {}", name, e.getMessage());
                }
            }
            registeredLockSet.add(name);
        }
    }

    @SuppressWarnings("unchecked")
    private ClusterLock find(final String name) {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("select cl from ");
        sql.append(ClusterLock.class.getName());
        sql.append(" cl where cl.name = ");
        sql.arg(name);

        final List<ClusterLock> locks = entityManager.executeQueryResultList(sql);

        // If we haven't got a lock then return null.
        if (locks == null || locks.size() == 0) {
            return null;
        }

        // We must have a lock object at this point.
        assert locks.size() == 1;
        return locks.get(0);
    }

    private ClusterLock save(final ClusterLock lock) {
        return entityManager.saveEntity(lock);
    }
}
