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

import stroom.db.util.JooqUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.time.StroomDuration;

import com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import static stroom.cluster.lock.impl.db.jooq.tables.ClusterLock.CLUSTER_LOCK;

@Singleton
class DbClusterLock implements Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbClusterLock.class);
    private final Set<String> registeredLockSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ClusterLockDbConnProvider clusterLockDbConnProvider;
    private final Provider<ClusterLockConfig> clusterLockConfigProvider;

    @Inject
    DbClusterLock(final ClusterLockDbConnProvider clusterLockDbConnProvider,
                  final Provider<ClusterLockConfig> clusterLockConfigProvider) {
        this.clusterLockDbConnProvider = clusterLockDbConnProvider;
        this.clusterLockConfigProvider = clusterLockConfigProvider;
    }

    public void lock(final String lockName, final Runnable runnable) {
        lockResult(lockName, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T lockResult(final String lockName, final Supplier<T> supplier) {
        LOGGER.debug("lock({}) - >>>", lockName);

        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        // This happens outside this transaction
        checkLockCreated(lockName);

        return JooqUtil.transactionResult(clusterLockDbConnProvider, context -> {
            final Instant startTime = Instant.now();
            final StroomDuration lockTimeout = clusterLockConfigProvider.get().getLockTimeout();
            // This is not exact as we are at the mercy of the db timeout once we have passed this value,
            // so could be 30s after this.
            final Instant timeoutTime = startTime.plus(lockTimeout);
            boolean acquiredLock = false;
            Optional<Record> optional = Optional.empty();
            int loopCount = 0;

            while (!acquiredLock && !Thread.currentThread().isInterrupted()) {
                if (Instant.now().isAfter(timeoutTime)) {
                    throw new RuntimeException(LogUtil.message(
                            "Gave up waiting for lock {} after {}. Current configured lockTimeout is {}",
                            lockName, Duration.between(startTime, Instant.now()), lockTimeout));
                }
                loopCount++;
                try {
                    // This may timeout on the DB
                    optional = getRecordLock(lockName, context);

                    // Show info if we go beyond the db timeout, else a debug msg.
                    if (loopCount > 1) {
                        LOGGER.info("Acquired lock {}, waited {}",
                                lockName, Duration.between(startTime, Instant.now()));
                    } else {
                        LOGGER.debug("Acquired lock {}, waited {}",
                                lockName, Duration.between(startTime, Instant.now()));
                    }
                    acquiredLock = true;
                } catch (Exception e) {
                    // If the supplier takes a long time to run, especially if it has to run on multiple nodes
                    // than we will get a lock timeout error from the DB so need to handle that and keep
                    // trying to get the lock. This means this thread/node will join the back of the queue
                    // so this lock mechanism does not ensure fairness.
                    if (e.getCause() != null
                            && e.getCause() instanceof MySQLTransactionRollbackException
                            && e.getCause().getMessage().contains("Lock wait timeout exceeded")) {
                        LOGGER.info("Still waiting for lock {}, waited {} so far. Will give up shortly after {}. " +
                                        "Current configured lockTimeout is {}",
                                lockName, Duration.between(startTime, Instant.now()), timeoutTime, lockTimeout);
                    } else {
                        LOGGER.error("Error getting lock {}: {}", lockName, e.getMessage(), e);
                        throw e;
                    }
                }
            }

            if (optional.isEmpty()) {
                throw new IllegalStateException("No cluster lock has been found or created: " + lockName);
            }

            LOGGER.debug("lock({}) - <<< {}", lockName, logExecutionTime);

            return supplier.get();
        });
    }

    private Optional<Record> getRecordLock(final String lockName, final DSLContext context) {
        Optional<Record> optional;
        // Get the lock, waiting if not available, but this may time out
        optional = context
                .select()
                .from(CLUSTER_LOCK)
                .where(CLUSTER_LOCK.NAME.eq(lockName))
                .forUpdate()
                .fetchOptional();
        return optional;
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
        return JooqUtil.contextResult(clusterLockDbConnProvider, context -> context
                        .select(CLUSTER_LOCK.ID)
                        .from(CLUSTER_LOCK)
                        .where(CLUSTER_LOCK.NAME.eq(name))
                        .fetchOptional())
                .map(r -> r.get(CLUSTER_LOCK.ID))
                .orElse(null);
    }

    private Integer create(final String name) {
        return JooqUtil.contextResult(clusterLockDbConnProvider, context -> context
                        .insertInto(CLUSTER_LOCK, CLUSTER_LOCK.NAME)
                        .values(name)
                        .onDuplicateKeyIgnore()
                        .returning(CLUSTER_LOCK.ID)
                        .fetchOptional())
                .map(r -> r.get(CLUSTER_LOCK.ID))
                .orElseGet(() -> get(name));
    }

    @Override
    public void clear() {
        registeredLockSet.clear();
    }
}
