/*
 * Copyright 2019 Crown Copyright
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

package stroom.cluster.lock.impl.dao;

import stroom.cluster.lock.impl.db.ClusterLockDbConnProvider;
import stroom.cluster.lock.impl.db.jooq.tables.records.ClusterLockRecord;
import stroom.db.util.JooqUtil;
import stroom.node.api.NodeInfo;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static stroom.cluster.lock.impl.db.jooq.tables.ClusterLock.CLUSTER_LOCK;

@Singleton
class DbClusterLock implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbClusterLock.class);
    public static final int VERSION = 0;
    private static final char LIKE_ESCAPE = '!';
    private final Set<String> registeredLockSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ClusterLockDbConnProvider clusterLockDbConnProvider;
    private final Provider<ClusterLockConfig> clusterLockConfigProvider;
    private final Provider<NodeInfo> nodeInfoProvider;

    /**
     * The database's own clock, in milliseconds. Every comparison of a lease against the current time
     * uses this rather than the JVM clock, so that a node whose clock runs ahead of its peers cannot
     * decide another node's lease has expired when it has not.
     *
     * <p>The no-argument {@code UNIX_TIMESTAMP()} reads epoch time directly. Passing it a datetime
     * instead would convert through the session time zone, where a local time during a
     * daylight-saving fall-back names two different instants — and reading an hour ahead would make
     * every held lock look long expired.
     */
    private static final Field<Long> DB_NOW_MS =
            DSL.field("CAST(UNIX_TIMESTAMP() * 1000 AS SIGNED)", Long.class);

    private final Random random = new Random();

    // Timing only — never waits on the database, so one lock's slow round trip cannot delay another
    // lock's heartbeat.
    private final ScheduledExecutorService leaseScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    daemonThreadFactory("Stroom-ClusterLock-LeaseTimer"));

    // Runs the heartbeat round trips. Unbounded in principle, but a Heartbeat submits nothing while its
    // own previous round trip is still running, so this holds at most one thread per lock currently
    // held by this node, and trims them when they go idle.
    private final ExecutorService leaseExecutor =
            Executors.newCachedThreadPool(daemonThreadFactory("Stroom-ClusterLock-LeaseExtension"));

    private static ThreadFactory daemonThreadFactory(final String name) {
        return runnable -> {
            final Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    @Inject
    DbClusterLock(final ClusterLockDbConnProvider clusterLockDbConnProvider,
                  final Provider<ClusterLockConfig> clusterLockConfigProvider,
                  final Provider<NodeInfo> nodeInfoProvider) {
        this.clusterLockDbConnProvider = clusterLockDbConnProvider;
        this.clusterLockConfigProvider = clusterLockConfigProvider;
        this.nodeInfoProvider = nodeInfoProvider;
    }

    // Constructor for testing
    DbClusterLock(final ClusterLockDbConnProvider clusterLockDbConnProvider,
                  final Provider<ClusterLockConfig> clusterLockConfigProvider) {
        this.clusterLockDbConnProvider = clusterLockDbConnProvider;
        this.clusterLockConfigProvider = clusterLockConfigProvider;
        this.nodeInfoProvider = null;
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

        final Instant startTime = Instant.now();
        final StroomDuration lockTimeout = clusterLockConfigProvider.get().getLockTimeout();
        final long leaseMs = lockTimeout.toMillis();
        final Instant timeoutTime = startTime.plus(lockTimeout);

        String lockToken = null;
        int loopCount = 0;
        long currentSleepMs = 100;
        final long maxSleepMs = 2000;

        while (!Thread.currentThread().isInterrupted()) {
            loopCount++;
            lockToken = tryAcquireLock(lockName, leaseMs);
            if (lockToken != null) {
                if (loopCount > 1) {
                    LOGGER.info("Acquired lock {}, waited {}",
                            lockName, Duration.between(startTime, Instant.now()));
                } else {
                    LOGGER.debug("Acquired lock {}, waited {}",
                            lockName, Duration.between(startTime, Instant.now()));
                }
                break;
            }

            // If tryLock (waitForLock == false), we don't wait/loop
            if (!waitForLock) {
                break;
            }

            // Check if we timed out
            if (Instant.now().isAfter(timeoutTime)) {
                final String ownerInfo = getOwnerInfoMessage(lockName);
                throw new RuntimeException(LogUtil.message(
                        "Gave up waiting for lock {} after {}. Current configured lockTimeout is {}. {}",
                        lockName, Duration.between(startTime, Instant.now()), lockTimeout, ownerInfo));
            }

            // Wait with exponential backoff and randomized jitter
            try {
                final double jitterPercent = 0.8 + (random.nextDouble() * 0.4); // between 0.8 and 1.2
                final long sleepWithJitter = Math.round(currentSleepMs * jitterPercent);
                Thread.sleep(sleepWithJitter);
                currentSleepMs = Math.min(currentSleepMs * 2, maxSleepMs);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.info("Thread interrupted while waiting for lock {}", lockName);
                break;
            }
        }

        if (lockToken == null) {
            if (waitForLock) {
                throw new RuntimeException("Failed to acquire lock: " + lockName);
            } else {
                // This is a tryLock that failed to acquire the lock
                final String ownerInfo = getOwnerInfoMessage(lockName);
                LOGGER.warn("Failed to acquire lock '{}'. {}", lockName, ownerInfo);
                return null;
            }
        }

        final String token = lockToken;
        final long heartbeatInterval = Math.max(1000, leaseMs / 3);
        final Thread workerThread = Thread.currentThread();
        final LeaseHeartbeat heartbeat = new LeaseHeartbeat(
                lockName,
                leaseMs,
                heartbeatInterval,
                () -> renewLease(lockName, token),
                leaseExecutor,
                System::nanoTime,
                workerThread::interrupt);
        final ScheduledFuture<?> heartbeatTask = leaseScheduler.scheduleWithFixedDelay(
                heartbeat::tick,
                heartbeatInterval,
                heartbeatInterval,
                TimeUnit.MILLISECONDS);

        // Run the supplier and release the lock in finally
        final LogExecutionTime holdExecutionTime = new LogExecutionTime();
        final T result;
        try {
            result = supplier.get();
        } finally {
            // Stop first: releasing nulls the token, which an extension already in flight would
            // otherwise read as the lock having been taken by someone else and fence for.
            heartbeat.stop();
            // false — the scheduler thread is shared by every lock held on this node, and the round
            // trip it would interrupt does not run there anyway.
            heartbeatTask.cancel(false);
            releaseLock(lockName, token);

            if (heartbeat.isFenced()) {
                // Clear the interrupt we raised, so it does not carry over to whatever this thread
                // runs next — for a pooled thread that is unrelated work.
                Thread.interrupted();
            }

            // Log hold duration metric
            if (holdExecutionTime.getDurationMs() > 60000) {
                LOGGER.warn("Released lock '{}' - held for a long duration of {}", lockName, holdExecutionTime);
            } else {
                LOGGER.debug("Released lock '{}' - held for {}", lockName, holdExecutionTime);
            }

            LOGGER.debug("lock({}) - <<< {}", lockName, logExecutionTime);
        }

        // Outside the finally, so an exception from the supplier is not masked by this one. Work that
        // swallowed the interrupt would otherwise return as though it had held the lock throughout.
        if (heartbeat.isFenced()) {
            throw new RuntimeException(LogUtil.message(
                    "Lost the lease on cluster lock {} while the work was running, so it may have run "
                    + "on another node at the same time", lockName));
        }
        return result;
    }

    // Takes the lock if it is free or its lease has expired, and returns a token identifying this
    // acquisition, or null if someone else holds it. Releasing and extending match on that token:
    // without it a holder whose lease had expired could release or extend the lock a later holder
    // now owns, because node name and thread name are not unique to one acquisition.
    private String tryAcquireLock(final String lockName, final long leaseMs) {
        final String token = UUID.randomUUID().toString();
        final String nodeName = getNodeName();
        final String threadName = getThreadName();

        final int updated = JooqUtil.transactionResult(clusterLockDbConnProvider, context -> {
            return context.update(CLUSTER_LOCK)
                    .set(CLUSTER_LOCK.LOCK_TIME_MS, DB_NOW_MS)
                    .set(CLUSTER_LOCK.LOCK_TOKEN, token)
                    .set(CLUSTER_LOCK.NODE_NAME, nodeName)
                    .set(CLUSTER_LOCK.THREAD_NAME, threadName)
                    .set(CLUSTER_LOCK.LEASE_MS, leaseMs)
                    .where(CLUSTER_LOCK.NAME.eq(lockName)
                            .and(CLUSTER_LOCK.LOCK_TIME_MS.isNull()
                                    .or(CLUSTER_LOCK.LOCK_TIME_MS
                                            .add(DSL.coalesce(CLUSTER_LOCK.LEASE_MS, leaseMs))
                                            .lt(DB_NOW_MS))))
                    .execute();
        });

        return updated == 1 ? token : null;
    }

    private void releaseLock(final String lockName, final String token) {
        final int updated = JooqUtil.transactionResult(clusterLockDbConnProvider, context -> {
            return context.update(CLUSTER_LOCK)
                    .setNull(CLUSTER_LOCK.LOCK_TIME_MS)
                    .setNull(CLUSTER_LOCK.LOCK_TOKEN)
                    .setNull(CLUSTER_LOCK.NODE_NAME)
                    .setNull(CLUSTER_LOCK.THREAD_NAME)
                    .setNull(CLUSTER_LOCK.LEASE_MS)
                    .where(CLUSTER_LOCK.NAME.eq(lockName)
                            .and(CLUSTER_LOCK.LOCK_TOKEN.eq(token)))
                    .execute();
        });
        if (updated == 0) {
            LOGGER.warn("Lock {} was not released because this acquisition no longer holds it — the " +
                        "lease lapsed and it may since have been taken elsewhere", lockName);
        }
    }

    // Pushes this acquisition's lease forward, returning the number of lock rows it matched: 1 while
    // this acquisition still holds the lock, 0 once it does not.
    private int renewLease(final String lockName, final String token) {
        return JooqUtil.transactionResult(clusterLockDbConnProvider, context -> {
            return context.update(CLUSTER_LOCK)
                    .set(CLUSTER_LOCK.LOCK_TIME_MS, DB_NOW_MS)
                    .where(CLUSTER_LOCK.NAME.eq(lockName)
                            .and(CLUSTER_LOCK.LOCK_TOKEN.eq(token)))
                    .execute();
        });
    }


    private String getOwnerInfoMessage(final String lockName) {
        try {
            return JooqUtil.contextResult(clusterLockDbConnProvider, context -> {
                return context.select(CLUSTER_LOCK.NODE_NAME, CLUSTER_LOCK.THREAD_NAME, CLUSTER_LOCK.LOCK_TIME_MS)
                        .from(CLUSTER_LOCK)
                        .where(CLUSTER_LOCK.NAME.eq(lockName))
                        .fetchOptional();
            }).map(r -> {
                final String node = r.get(CLUSTER_LOCK.NODE_NAME);
                final String thread = r.get(CLUSTER_LOCK.THREAD_NAME);
                final Long timeMs = r.get(CLUSTER_LOCK.LOCK_TIME_MS);
                if (node == null && thread == null && timeMs == null) {
                    return "Current owner: none";
                }
                final String timeStr = timeMs != null ? Instant.ofEpochMilli(timeMs).toString() : "null";
                return LogUtil.message("Current owner: node={}, thread={}, acquired={}",
                        node != null ? node : "unknown",
                        thread != null ? thread : "unknown",
                        timeStr);
            }).orElse("Current owner: unknown (lock row not found)");
        } catch (final Exception e) {
            LOGGER.debug("Error retrieving owner info for lock " + lockName, e);
            return "Current owner: unknown (error retrieving)";
        }
    }

    public void deleteLocks(final String prefix) {
        try {
            final String pattern = escapeLikeLiteral(prefix) + "%";
            final int deleted = JooqUtil.transactionResult(clusterLockDbConnProvider, context -> {
                return context.deleteFrom(CLUSTER_LOCK)
                        .where(CLUSTER_LOCK.NAME.like(pattern, LIKE_ESCAPE))
                        .execute();
            });
            if (deleted > 0) {
                LOGGER.info("Deleted {} locks with prefix '{}'", deleted, prefix);
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to delete locks with prefix '{}': {}", prefix, e.getMessage(), e);
        }
    }

    // '%' and '_' are wildcards to LIKE, so a prefix containing either would match names it does not
    // start with. Nothing generates such a prefix today; escaping means nothing has to keep checking.
    static String escapeLikeLiteral(final String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private void checkLockCreated(final String name) {
        LOGGER.debug("Getting cluster lock: {}", name);

        if (registeredLockSet.contains(name)) {
            return;
        }

        synchronized (this) {
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
        final ClusterLockRecord record = new ClusterLockRecord();
        record.setVersion(VERSION);
        record.setName(name);
        JooqUtil.tryCreate(clusterLockDbConnProvider, record);
    }

    private String getNodeName() {
        if (nodeInfoProvider != null) {
            try {
                final NodeInfo nodeInfo = nodeInfoProvider.get();
                if (nodeInfo != null) {
                    return nodeInfo.getThisNodeName();
                }
            } catch (final Exception e) {
                LOGGER.debug("Could not get node name from provider", e);
            }
        }
        return "unknown";
    }

    private String getThreadName() {
        return Thread.currentThread().getName();
    }

    @Override
    public void clear() {
        registeredLockSet.clear();
    }
}


