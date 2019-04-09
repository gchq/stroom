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

package stroom.cluster.lock.impl.db;

import org.jooq.Record;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.cluster.lock.impl.db.jooq.tables.ClusterLock.CLUSTER_LOCK;

@Singleton
class DbClusterLock implements Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbClusterLock.class);
    private final Set<String> registeredLockSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ConnectionProvider connectionProvider;

    @Inject
    DbClusterLock(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void lock(final String lockName, final Runnable runnable) {
            LOGGER.debug("lock({}) - >>>", lockName);

            final LogExecutionTime logExecutionTime = new LogExecutionTime();

            // This happens outside this transaction
        checkLockCreated(lockName);

        JooqUtil.transaction(connectionProvider, context -> {
            final Optional<Record> optional = context
                    .select()
                    .from(CLUSTER_LOCK)
                    .where(CLUSTER_LOCK.NAME.eq(lockName))
                    .forUpdate()
                    .fetchOptional();

            if (!optional.isPresent()) {
                throw new IllegalStateException("No cluster lock has been found or created: " + lockName);
            }

            LOGGER.debug("lock({}) - <<< {}", lockName, logExecutionTime);

            runnable.run();
        });
    }

    private void checkLockCreated(final String name) {
        LOGGER.debug("Getting cluster lock: " + name);

        if (registeredLockSet.contains(name)) {
            return;
        }

        // I've done this as we should at least only create a lock at a time
        // within the JVM.
        synchronized (this) {
            // Try and get the cluster lock for the job system.
            final Integer id = get(name);
            if (id == null) {
                create(name);
            }
            registeredLockSet.add(name);
        }
    }

    private Integer get(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(CLUSTER_LOCK.ID)
                .from(CLUSTER_LOCK)
                .where(CLUSTER_LOCK.NAME.eq(name))
                .fetchOptional()
                .map(r -> r.get(CLUSTER_LOCK.ID))
                .orElse(null));
    }

    private Integer create(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(CLUSTER_LOCK, CLUSTER_LOCK.NAME)
                .values(name)
                .onDuplicateKeyIgnore()
                .returning(CLUSTER_LOCK.ID)
                .fetchOptional()
                .map(r -> r.get(CLUSTER_LOCK.ID))
                .orElseGet(() -> get(name))
        );
    }

    @Override
    public void clear() {
        registeredLockSet.clear();
    }
}
