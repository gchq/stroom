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

import stroom.cluster.lock.impl.db.jooq.tables.records.ClusterLockRecord;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.time.StroomDuration;

import com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectForUpdateOfStep;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static stroom.cluster.lock.impl.db.jooq.tables.ClusterLock.CLUSTER_LOCK;

@Singleton
class DbClusterLock implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbClusterLock.class);
    public static final int VERSION = 0;
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
        lockResult(lockName, true, () -> {
            runnable.run();
            return null;
        });
    }

    public void tryLock(final String lockName, final Runnable runnable) {
        lockResult(lockName, false, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T lockResult(final String lockName, final Supplier<T> supplier) {
        return lockResult(lockName, true, supplier);
    }

    private <T> T lockResult(final String lockName, final boolean waitForLock, final Supplier<T> supplier) {
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
            Optional<Record> optional = Optional.empty();
            int loopCount = 0;

            while (!Thread.currentThread().isInterrupted()) {
                if (Instant.now().isAfter(timeoutTime)) {
                    throw new RuntimeException(LogUtil.message(
                            "Gave up waiting for lock {} after {}. Current configured lockTimeout is {}",
                            lockName, Duration.between(startTime, Instant.now()), lockTimeout));
                }
                loopCount++;
                try {
                    // This may timeout on the DB
                    optional = getRecordLock(lockName, context, waitForLock);

                    // Show info if we go beyond the db timeout, else a debug msg.
                    if (optional.isPresent()) {
                        if (loopCount > 1) {
                            LOGGER.info("Acquired lock {}, waited {}",
                                    lockName, Duration.between(startTime, Instant.now()));
                        } else {
                            LOGGER.debug("Acquired lock {}, waited {}",
                                    lockName, Duration.between(startTime, Instant.now()));
                        }
                    } else {
                        LOGGER.debug("Did not acquire lock {}, waited {}",
                                lockName, Duration.between(startTime, Instant.now()));
                    }
                    // We have the lock OR (waitForLock==false AND we didn't get the lock)
                    break;
                } catch (final Exception e) {
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

            LOGGER.debug("lock({}) - <<< {}", lockName, logExecutionTime);
            if (optional.isEmpty()) {
                if (waitForLock) {
                    // This should never happen as checkLockCreated() is called at the top of this method
                    // and will ensure the lock record is present. We never delete them.
                    throw new IllegalStateException("No cluster lock has been found or created: " + lockName);
                } else {
                    // This is a tryLock that didn't get the lock, so just return
                    return null;
                }
            } else {
                return supplier.get();
            }
        });
    }

    private Optional<Record> getRecordLock(final String lockName,
                                           final DSLContext context,
                                           final boolean waitForLock) {
        final Optional<Record> optRecord;
        // Get the lock, waiting if not available, but this may time out
        final SelectForUpdateOfStep<Record> selectStep = context
                .select()
                .from(CLUSTER_LOCK)
                .where(CLUSTER_LOCK.NAME.eq(lockName))
                .forUpdate();

        if (waitForLock) {
            optRecord = selectStep.fetchOptional();
        } else {
            optRecord = selectStep.skipLocked()
                    .fetchOptional();
        }

        LOGGER.debug("getRecordLock() - optRecord: {}", optRecord);
        return optRecord;
    }

    private void checkLockCreated(final String name) {
        LOGGER.debug("Getting cluster lock: {}", name);

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

    private void create(final String name) {
        // Another node may also be doing this
        final ClusterLockRecord record = new ClusterLockRecord(null, VERSION, name);
        JooqUtil.tryCreate(clusterLockDbConnProvider, record);
    }

    @Override
    public void clear() {
        registeredLockSet.clear();
    }
}
