/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.fs;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.StateType;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Drives the merge cycle for every doc held on a shared file store.
 *
 * <p>Owns only what every store type shares: finding the docs and shards with work, choosing what is
 * ready to be worked, counting failed attempts against it, and deleting what was applied. What a store type does
 * with a shard once it is held — including whatever it does periodically — is its
 * {@link MergeStrategy}'s. Taking each shard's cluster lock and working the shards side by side is
 * {@link ShardPass}. What actually happens to a shard inside the lock is the
 * {@link MergeStrategy} bound for its {@link StateType} — a store type with no strategy is not
 * merged.
 */
@Singleton
public class SharedFileStoreMergeProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStoreMergeProcessor.class);

    /**
     * Attempts to allow one piece of waiting work before it is set aside. Retrying is normally right,
     * because most failures are transient or clear once space is reclaimed, but work that can
     * never merge must stop consuming a cycle's worth of copying and merging forever.
     */
    private static final int MAX_ATTEMPTS = 10;

    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ShardPass shardPass;
    private final PlanBDocCache planBDocCache;
    private final Map<StateType, MergeStrategy> mergeStrategies;

    @Inject
    public SharedFileStoreMergeProcessor(final ClusterLockService clusterLockService,
                                         final Provider<PlanBConfig> configProvider,
                                         final SecurityContext securityContext,
                                         final TaskContextFactory taskContextFactory,
                                         final PlanBDocCache planBDocCache,
                                         final Map<StateType, MergeStrategy> mergeStrategies) {
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.shardPass = new ShardPass(clusterLockService, securityContext,
                createMergeExecutor(configProvider.get().getShardMergeThreadCount()));
        this.planBDocCache = planBDocCache;
        this.mergeStrategies = mergeStrategies;
    }

    private static ExecutorService createMergeExecutor(final int threadCount) {
        final AtomicInteger threadNo = new AtomicInteger();
        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    final Thread thread = new Thread(
                            runnable,
                            "Plan B Shard Merge #" + threadNo.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(3);
                    return thread;
                });
    }

    public void merge() {
        securityContext.asProcessingUser(() -> {
            LOGGER.debug("Starting Plan B Shared FS Merge");
            for (final PlanBDocument doc : documents()) {
                try {
                    mergeDoc(doc);
                } catch (final RuntimeException e) {
                    // Documents are independent of each other, and they are read while the cycle runs
                    // so one may be mid-deletion. A cycle that gave up on the first bad one would
                    // leave every doc after it alone until somebody noticed.
                    LOGGER.error(() -> LogUtil.message("Error merging shards for {}: {}",
                            doc.getName(), e.getMessage()), e);
                }
            }
            LOGGER.debug("Finished Plan B Shared FS Merge");
        });
    }

    /**
     * Every doc held on a shared file store, in a different order each cycle.
     *
     * <p>Shuffled so that concurrent nodes scatter across docs as well as across the shards within
     * one. Taken in the same order, every node would work down the same list and spend its cycle
     * losing the same races.
     */
    private List<PlanBDocument> documents() {
        final List<PlanBDocument> planBDocs = new ArrayList<>(planBDocCache.getAll().stream()
                .filter(SharedFileStore::isConfigured)
                .toList());
        Collections.shuffle(planBDocs);
        return planBDocs;
    }

    private void mergeDoc(final PlanBDocument doc) {
        final MergeStrategy strategy = mergeStrategies.get(doc.getStateType());
        if (strategy == null) {
            LOGGER.debug(() -> "No merge strategy for " + doc.getStateType() + ", skipping " + doc.getName());
            return;
        }

        // Taken on this thread, where the job's own context is, so that each shard's work hangs under
        // the job rather than under whichever pool thread happens to run it.
        final TaskContext parentTaskContext = taskContextFactory.current();

        final Map<Integer, List<Path>> byShard = queueDirsByShard(doc, strategy);

        // Every shard worth locking: one with work waiting, one whose maintenance has fallen due, or
        // one with both — which is worked once, with its waiting work, because taking the shard is
        // the expensive part and what the strategy does once it has it is its own business.
        final Set<Integer> shardIndexes = new LinkedHashSet<>(byShard.keySet());
        if (shardIndexes.isEmpty()) {
            return;
        }

        final Set<Integer> busy = shardPass.run(shardIndexes,
                shardIndex -> strategy.lockName(doc, shardIndex),
                shardIndex -> taskContextFactory.childContext(parentTaskContext,
                        "Merge doc " + doc.getName()
                        + " shard " + PlanBConstants.formatShardIndex(shardIndex),
                        taskContext -> mergeShard(strategy, doc, shardIndex,
                                byShard.getOrDefault(shardIndex, List.of()))).run(),
                "merging " + doc.getName());

        if (!busy.isEmpty()) {
            // Said quietly, because it is the ordinary case rather than a fault: every node runs this,
            // so a shard one node is holding is a shard the others do not have to.
            LOGGER.debug(() -> LogUtil.message("{} shard(s) of {} were held elsewhere this cycle: {}",
                    busy.size(), doc.getName(), busy));
        }
    }

    /**
     * The shards of a document with something to do this cycle, and what each has waiting.
     *
     * <p>Worked out before any lock is taken, which is safe because it only reads: a shard that gains
     * work between here and the lock is worked next cycle, and one that loses its reason to be worked
     * costs a lock taken for nothing. Whether maintenance is due is asked again inside the
     * lock, where another node may have done it since.
     */
    private Map<Integer, List<Path>> queueDirsByShard(final PlanBDocument doc,
                                                   final MergeStrategy strategy) {
        final Path queueDocDir = SharedFileStore.rootOf(doc)
                .resolve(PlanBConstants.QUEUE_DIR_NAME)
                .resolve(doc.getUuid());

        final Map<Integer, List<Path>> withWork = new LinkedHashMap<>();
        for (int shardIndex = 0; shardIndex < SharedFileStore.shardCountOf(doc); shardIndex++) {
            final List<Path> queueDirs =
                    collectQueueDirs(queueDocDir, PlanBConstants.formatShardIndex(shardIndex));

            // Sorted by name, which is oldest first: the names are stamped with the time the work was
            // released. Two writes of one key then settle the same way everywhere, last-named winning,
            // and a trace is attributed to the one that really caused it.
            queueDirs.sort(Comparator.comparing(p -> p.getFileName().toString()));

            if (!queueDirs.isEmpty() || strategy.maintenanceDue(doc, shardIndex)) {
                withWork.put(shardIndex, queueDirs);
            }
        }
        return withWork;
    }

    // Called with the shard's cluster lock held, so nothing else is working this shard.
    private void mergeShard(final MergeStrategy strategy,
                            final PlanBDocument doc,
                            final int shardIndex,
                            final List<Path> queueDirs) {
        final String lockName = PlanBConstants.getMergeLockName(doc.getUuid(), shardIndex);
        try {
            LOGGER.debug("Holding lock {}, starting merge/maintenance", lockName);

            // Asked again now the lock is held: on a cluster every node decides this for itself,
            // so another may have done the work since.
            final MergeContext ctx = new MergeContext(doc, shardIndex, lockName,
                    strategy.maintenanceDue(doc, shardIndex));
            final MergeResult result = strategy.mergeShard(ctx, queueDirs);

            result.failures().forEach(this::recordFailedAttempt);
            deleteAppliedQueueDirs(result.appliedQueueDirs());

            if (!result.failures().isEmpty()) {
                LOGGER.error(() -> LogUtil.message(
                                "Completed merge/maintenance for {} with {} of {} failing",
                                lockName,
                                result.failures().size(),
                                queueDirs.size()),
                        result.firstFailure());
            } else {
                LOGGER.debug("Successfully completed merge/maintenance for {}", lockName);
            }
        } catch (final IOException e) {
            LOGGER.error("Error during merge/maintenance for {}", lockName, e);
            throw new UncheckedIOException(e);
        }
    }


    // -----------------------------------------------------------------------
    // Keeping track of what has been applied and what has failed
    // -----------------------------------------------------------------------

    /**
     * Records that a piece of waiting work failed, so that one which can never be applied is
     * quarantined by {@link #collectQueueDirs} instead of being retried on every cycle forever.
     */
    private void recordFailedAttempt(final Path queueDir, final Exception failure) {
        final Path failedFile = queueDir.resolve(PlanBConstants.FAILED_FILE_NAME);
        final int attempts = readFailedAttempts(queueDir) + 1;
        try {
            Files.writeString(failedFile, attempts + "\n" + Instant.now() + "\n" + failure);
        } catch (final IOException e) {
            LOGGER.error("Error writing .failed marker to {}", queueDir, e);
        }
    }

    private int readFailedAttempts(final Path queueDir) {
        final Path failedFile = queueDir.resolve(PlanBConstants.FAILED_FILE_NAME);
        if (!Files.isRegularFile(failedFile)) {
            return 0;
        }
        try {
            final String first = Files.readString(failedFile).lines().findFirst().orElse("");
            return Integer.parseInt(first.trim());
        } catch (final IOException | NumberFormatException e) {
            LOGGER.debug(() -> LogUtil.message("Unreadable .failed marker in {}", queueDir), e);
            return 0;
        }
    }

    /**
     * Writes {@code .merged} markers and immediately deletes the directories. Nothing downstream
     * reads one that has been applied, so none is kept.
     */
    private void deleteAppliedQueueDirs(final List<Path> queueDirs) {
        for (final Path queueDir : queueDirs) {
            try {
                Files.writeString(queueDir.resolve(PlanBConstants.MERGED_FILE_NAME),
                        Instant.now().toString());
            } catch (final IOException e) {
                LOGGER.error("Error writing .merged marker to {}", queueDir, e);
            }
            FileUtil.deleteDir(queueDir);
        }
    }

    /**
     * The work waiting for one shard that is finished being written and has not been applied yet.
     */
    private List<Path> collectQueueDirs(final Path queueDocDir,
                                        final String shardIndexStr) {
        final List<Path> queueDirs = new ArrayList<>();
        final Path shardQueueDir = queueDocDir.resolve(shardIndexStr);
        if (Files.exists(shardQueueDir)) {
            try (final Stream<Path> stream = Files.list(shardQueueDir)) {
                stream.forEach(queueDir -> {
                    // A directory still being written carries the temporary suffix, and its marker is
                    // written before the move that gives it its real name — so the marker alone is not
                    // enough to tell a finished one from one a writer is still inside.
                    if (queueDir.getFileName().toString().endsWith(PlanBConstants.TMP_DIR_SUFFIX)) {
                        return;
                    }
                    if (Files.exists(queueDir.resolve(PlanBConstants.VERSION_FILE_NAME))
                            && !Files.exists(queueDir.resolve(PlanBConstants.MERGED_FILE_NAME))) {
                        final int attempts = readFailedAttempts(queueDir);
                        if (attempts >= MAX_ATTEMPTS) {
                            LOGGER.warn(() -> LogUtil.message(
                                    "Setting aside {} after {} failed attempts. It will " +
                                    "not be retried until its {} marker is removed.",
                                    queueDir, attempts, PlanBConstants.FAILED_FILE_NAME));
                        } else {
                            queueDirs.add(queueDir);
                        }
                    }
                });
            } catch (final IOException e) {
                LOGGER.error("Error listing the queue in {}", shardQueueDir, e);
            }
        }
        return queueDirs;
    }
}
