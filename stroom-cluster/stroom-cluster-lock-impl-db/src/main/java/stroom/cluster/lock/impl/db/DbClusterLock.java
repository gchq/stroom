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

package stroom.cluster.lock.impl.db;

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
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static stroom.cluster.lock.impl.db.jooq.tables.ClusterLock.CLUSTER_LOCK;

@Singleton
class DbClusterLock implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbClusterLock.class);
    public static final int VERSION = 0;
    private final Set<String> registeredLockSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ClusterLockDbConnProvider clusterLockDbConnProvider;
    private final Provider<ClusterLockConfig> clusterLockConfigProvider;
    private final Provider<NodeInfo> nodeInfoProvider;

    private final AtomicInteger lockCreationCounter = new AtomicInteger(0);
    private final Random random = new Random();

    private final ScheduledExecutorService leaseExtensionExecutor =
            Executors.newScheduledThreadPool(4, runnable -> {
                final Thread thread = new Thread(runnable, "Stroom-ClusterLock-LeaseExtension");
                thread.setDaemon(true);
                return thread;
            });

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

        boolean acquired = false;
        int loopCount = 0;
        long currentSleepMs = 100;
        final long maxSleepMs = 2000;

        while (!Thread.currentThread().isInterrupted()) {
            loopCount++;
            acquired = tryAcquireLock(lockName, leaseMs);
            if (acquired) {
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

        if (!acquired) {
            if (waitForLock) {
                throw new RuntimeException("Failed to acquire lock: " + lockName);
            } else {
                // This is a tryLock that failed to acquire the lock
                final String ownerInfo = getOwnerInfoMessage(lockName);
                LOGGER.warn("Failed to acquire lock '{}'. {}", lockName, ownerInfo);
                return null;
            }
        }

        // Lock heartbeat scheduled rate task
        final String ownerNodeName = getNodeName();
        final String ownerThreadName = getThreadName();
        final Thread workerThread = Thread.currentThread();
        final ScheduledFuture<?> heartbeatTask;
        final long heartbeatInterval = Math.max(1000, leaseMs / 3);
        heartbeatTask = leaseExtensionExecutor.scheduleAtFixedRate(
                () -> extendLease(lockName, ownerNodeName, ownerThreadName, workerThread),
                heartbeatInterval,
                heartbeatInterval,
                TimeUnit.MILLISECONDS);

        // Run the supplier and release the lock in finally
        final LogExecutionTime holdExecutionTime = new LogExecutionTime();
        try {
            return supplier.get();
        } finally {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(true);
            }
            releaseLock(lockName);

            // Log hold duration metric
            if (holdExecutionTime.getDurationMs() > 60000) {
                LOGGER.warn("Released lock '{}' - held for a long duration of {}", lockName, holdExecutionTime);
            } else {
                LOGGER.info("Released lock '{}' - held for {}", lockName, holdExecutionTime);
            }

            LOGGER.debug("lock({}) - <<< {}", lockName, logExecutionTime);
        }
    }

    private boolean tryAcquireLock(final String lockName, final long leaseMs) {
        final String nodeName = getNodeName();
        final String threadName = getThreadName();
        final long now = System.currentTimeMillis();

        final int updated = JooqUtil.transactionResult(clusterLockDbConnProvider, context -> {
            return context.update(CLUSTER_LOCK)
                    .set(CLUSTER_LOCK.LOCK_TIME_MS, now)
                    .set(CLUSTER_LOCK.NODE_NAME, nodeName)
                    .set(CLUSTER_LOCK.THREAD_NAME, threadName)
                    .set(CLUSTER_LOCK.LEASE_MS, leaseMs)
                    .where(CLUSTER_LOCK.NAME.eq(lockName)
                            .and(CLUSTER_LOCK.LOCK_TIME_MS.isNull()
                                    .or(CLUSTER_LOCK.LOCK_TIME_MS
                                            .add(DSL.coalesce(CLUSTER_LOCK.LEASE_MS, leaseMs))
                                            .lt(now))))
                    .execute();
        });

        return updated == 1;
    }

    private void releaseLock(final String lockName) {
        final String nodeName = getNodeName();
        final String threadName = getThreadName();

        JooqUtil.transaction(clusterLockDbConnProvider, context -> {
            context.update(CLUSTER_LOCK)
                    .setNull(CLUSTER_LOCK.LOCK_TIME_MS)
                    .setNull(CLUSTER_LOCK.NODE_NAME)
                    .setNull(CLUSTER_LOCK.THREAD_NAME)
                    .setNull(CLUSTER_LOCK.LEASE_MS)
                    .where(CLUSTER_LOCK.NAME.eq(lockName)
                            .and(CLUSTER_LOCK.NODE_NAME.eq(nodeName))
                            .and(CLUSTER_LOCK.THREAD_NAME.eq(threadName)))
                    .execute();
        });
    }

    private void extendLease(final String lockName,
                             final String nodeName,
                             final String threadName,
                             final Thread workerThread) {
        final long now = System.currentTimeMillis();
        try {
            final int updated = JooqUtil.transactionResult(clusterLockDbConnProvider, context -> {
                return context.update(CLUSTER_LOCK)
                        .set(CLUSTER_LOCK.LOCK_TIME_MS, now)
                        .where(CLUSTER_LOCK.NAME.eq(lockName)
                                .and(CLUSTER_LOCK.NODE_NAME.eq(nodeName))
                                .and(CLUSTER_LOCK.THREAD_NAME.eq(threadName)))
                        .execute();
            });
            if (updated == 1) {
                LOGGER.debug("Extended lease for lock {} (node={}, thread={})", lockName, nodeName, threadName);
            } else {
                LOGGER.error("Lock lease lost for lock {} (node={}, thread={})! " +
                                "Active fencing triggered: interrupting worker thread.",
                        lockName, nodeName, threadName);
                workerThread.interrupt();
            }
        } catch (final Exception e) {
            LOGGER.warn("Failed to extend lease for lock {}: {}", lockName, e.getMessage());
        }
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
            final int deleted = JooqUtil.transactionResult(clusterLockDbConnProvider, context -> {
                return context.deleteFrom(CLUSTER_LOCK)
                        .where(CLUSTER_LOCK.NAME.like(prefix + "%"))
                        .execute();
            });
            if (deleted > 0) {
                LOGGER.info("Deleted {} locks with prefix '{}'", deleted, prefix);
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to delete locks with prefix '{}': {}", prefix, e.getMessage(), e);
        }
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


