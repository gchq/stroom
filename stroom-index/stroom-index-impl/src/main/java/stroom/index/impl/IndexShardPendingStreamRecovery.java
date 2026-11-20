/*
 * Copyright 2026 Crown Copyright
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


package stroom.index.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.processor.api.ProcessorTaskRecovery;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/// Recovers streams whose documents were lost from, or only part written to, the index. Recovery is the
/// same whatever went wrong: the stream's documents are removed from every shard it touched, and then its
/// processor task is put back to run again. Doing it twice gives the same result as doing it once.
///
/// Run as a job on every node. Each node removes documents from the shards it is writing to or looks
/// after. Putting tasks back, and the tidying of rows, is done by one node at a time.
@Singleton
public class IndexShardPendingStreamRecovery {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardPendingStreamRecovery.class);

    /// The number of failed attempts to index a stream after which its task is set to failed and left.
    static final int MAX_ATTEMPTS = 3;

    private static final String LOCK_NAME = "IndexShardPendingStreamRecovery";
    private static final int BATCH_SIZE = 1000;
    // How long a row must have said its documents are being written before its task is checked to see
    // whether it is in fact still running.
    private static final Duration STALE_ATTEMPT_AGE = Duration.ofMinutes(10);

    private final IndexShardPendingStreamDao pendingStreamDao;
    private final IndexShardDao indexShardDao;
    private final IndexShardWriterCache indexShardWriterCache;
    private final IndexShardResponsibility indexShardResponsibility;
    private final Optional<ProcessorTaskRecovery> processorTaskRecovery;
    private final ClusterLockService clusterLockService;
    private final SecurityContext securityContext;
    private final Provider<IndexWriterConfig> indexWriterConfigProvider;
    // Stops documents being removed by two threads of this node at once.
    private final ReentrantLock removing = new ReentrantLock();

    @Inject
    IndexShardPendingStreamRecovery(final IndexShardPendingStreamDao pendingStreamDao,
                                    final IndexShardDao indexShardDao,
                                    final IndexShardWriterCache indexShardWriterCache,
                                    final IndexShardResponsibility indexShardResponsibility,
                                    final Optional<ProcessorTaskRecovery> processorTaskRecovery,
                                    final ClusterLockService clusterLockService,
                                    final SecurityContext securityContext,
                                    final Provider<IndexWriterConfig> indexWriterConfigProvider) {
        this.pendingStreamDao = pendingStreamDao;
        this.indexShardDao = indexShardDao;
        this.indexShardWriterCache = indexShardWriterCache;
        this.indexShardResponsibility = indexShardResponsibility;
        this.processorTaskRecovery = processorTaskRecovery;
        this.clusterLockService = clusterLockService;
        this.securityContext = securityContext;
        this.indexWriterConfigProvider = indexWriterConfigProvider;
    }

    /// Runs every part of recovery once. The entry point of the job.
    public void exec() {
        securityContext.asProcessingUser(() -> {
            run("releaseLapsedLeases", this::releaseLapsedLeases);
            run("recoverClosedShards", this::recoverClosedShards);
            run("removeMarkedDocuments", this::removeMarkedDocuments);
            run("recoverStreams", () -> clusterLockService.tryLock(LOCK_NAME, () -> {
                run("tidy", this::tidy);
                run("failStaleAttempts", () -> failStaleAttempts(System.currentTimeMillis()));
                run("finishRetries", this::finishRetries);
            }));
        });
    }

    private void run(final String name, final Runnable runnable) {
        try {
            LOGGER.logDurationIfDebugEnabled(runnable, name);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error in index shard pending stream recovery (" + name + "): " + e.getMessage(), e);
        }
    }

    /// Takes shards on shared volumes from nodes that stopped renewing their leases long enough ago, e.g.
    /// because they died. The shards are left closed, for any node to write to, and marked so that
    /// [#recoverClosedShards()] recovers the streams their old holders had not committed.
    void releaseLapsedLeases() {
        final IndexWriterConfig config = indexWriterConfigProvider.get();
        final int count = indexShardDao.releaseLapsedLeases(
                config.getShardLeaseTakeoverGrace().toMillis(),
                config.getReplacedCommitRetention().toMillis());
        if (count > 0) {
            LOGGER.warn(() -> LogUtil.message(
                    "Took {} index shard(s) from nodes whose leases on them expired more than {} ago. The " +
                    "streams those nodes had not committed will be removed from the index and indexed again.",
                    count, config.getShardLeaseTakeoverGrace()));
        }
    }

    /// Deals with closed shards whose last writer stopped without a final commit, e.g. because its node
    /// died. The streams such a shard may hold part of are marked to be removed and indexed again. Without
    /// this that would wait until something next wrote to the shard, which may be never.
    void recoverClosedShards() {
        final Predicate<IndexShard> isResponsible = indexShardResponsibility.createTest();
        // Not limited, as the shards of a node that has gone would otherwise crowd out the rest. There
        // are only ever as many as were open when their nodes stopped.
        for (final IndexShard indexShard :
                indexShardDao.findClosedShardsNeedingPendingStreamRecovery(Integer.MAX_VALUE)) {
            if (isResponsible.test(indexShard)) {
                try {
                    indexShardWriterCache.recoverPendingStreams(indexShard);
                } catch (final RuntimeException e) {
                    LOGGER.error(() -> "Unable to recover the pending streams of index shard "
                                       + indexShard.getId() + ": " + e.getMessage(), e);
                }
            }
        }
    }

    /// Removes from the index the documents of streams that are marked to be removed, in the shards this
    /// node is writing to or looks after. A shard that another node is writing to is left for that node.
    public void removeMarkedDocuments() {
        if (!removing.tryLock()) {
            return;
        }
        try {
            securityContext.asProcessingUser(() -> {
                final Predicate<IndexShard> isResponsible = indexShardResponsibility.createTest();
                for (final Long shardId : pendingStreamDao.findShardIdsWithRowsToDelete()) {
                    try {
                        removeMarkedDocuments(shardId, isResponsible);
                    } catch (final RuntimeException e) {
                        LOGGER.error(() -> "Unable to remove the documents of failed streams from index shard "
                                           + shardId + ": " + e.getMessage(), e);
                    }
                }
            });
        } finally {
            removing.unlock();
        }
    }

    private void removeMarkedDocuments(final long shardId, final Predicate<IndexShard> isResponsible) {
        final List<PendingStream> rows = pendingStreamDao.findRowsToDelete(shardId);
        if (rows.isEmpty()) {
            return;
        }
        final List<Long> rowIds = rows.stream().map(PendingStream::id).toList();
        final Optional<IndexShard> optional = indexShardDao.fetch(shardId);
        if (optional.isEmpty()
            || IndexShardStatus.DELETED.equals(optional.get().getStatus())
            || IndexShardStatus.CORRUPT.equals(optional.get().getStatus())) {
            // The documents have gone with the shard, or can't be reached in it.
            pendingStreamDao.deleteRows(rowIds);
            return;
        }

        final boolean openHere = indexShardWriterCache.getIfPresent(shardId).isPresent();
        if (!openHere && !isResponsible.test(optional.get())) {
            return;
        }

        final Set<Long> streamIds = new HashSet<>();
        rows.forEach(row -> streamIds.add(row.streamId()));
        // Fails if another node has the shard, in which case that node will do this.
        final IndexShardWriter writer = indexShardWriterCache.getOrOpenWriter(shardId);
        writer.deleteStreams(streamIds);
        // Only once the removal is committed, so that a failure in between leaves the rows to try again.
        pendingStreamDao.deleteRows(rowIds);
        LOGGER.info(() -> LogUtil.message(
                "Removed the documents of {} failed stream(s) from index shard {}", streamIds.size(), shardId));
    }

    /// Clears rows that are finished with.
    void tidy() {
        // A shard that is closed cleanly has committed everything it was given.
        pendingStreamDao.markCommittedInClosedShards();
        pendingStreamDao.deleteRowsOfDeletedShards();
        pendingStreamDao.deleteCommittedStreams(BATCH_SIZE);
    }

    /// Fails attempts whose rows still say documents are being written although the task is no longer
    /// running, e.g. because the database could not be reached when the task ended.
    ///
    /// @param nowMs The time now.
    void failStaleAttempts(final long nowMs) {
        if (processorTaskRecovery.isEmpty()) {
            return;
        }
        final long notChangedSince = nowMs - STALE_ATTEMPT_AGE.toMillis();
        final Set<String> seen = new HashSet<>();
        for (final PendingStream row : pendingStreamDao.findStaleAttempts(notChangedSince, BATCH_SIZE)) {
            if (seen.add(row.streamId() + ":" + row.taskId())) {
                final Optional<TaskStatus> status = processorTaskRecovery.get().getStatus(row.taskId());
                if (status.isEmpty() || !TaskStatus.PROCESSING.equals(status.get())) {
                    if (pendingStreamDao.failStaleAttempt(row.streamId(), row.taskId(), notChangedSince)) {
                        LOGGER.warn(() -> LogUtil.message(
                                "Task {} stopped indexing stream {} without finishing. The stream's " +
                                "documents will be removed from the index and it will be indexed again.",
                                row.taskId(), row.streamId()));
                    }
                }
            }
        }
    }

    /// Puts back to run again the tasks of failed streams whose documents have now been removed from every
    /// shard, or sets them to failed if they have been tried too often.
    void finishRetries() {
        if (processorTaskRecovery.isEmpty()) {
            LOGGER.debug("No processor task recovery is available");
            return;
        }
        final ProcessorTaskRecovery recovery = processorTaskRecovery.get();
        for (final PendingStream row : pendingStreamDao.findRetriesToFinish(BATCH_SIZE)) {
            try {
                final Optional<TaskStatus> status = recovery.getStatus(row.taskId());
                if (status.isEmpty() || TaskStatus.DELETED.equals(status.get())) {
                    // There is no task to run again or to write an error stream for.
                    LOGGER.warn(() -> LogUtil.message(
                            "The documents indexed from stream {} by processor task {} were removed from " +
                            "the index because they were not all committed, but the task no longer exists " +
                            "so the stream will not be indexed again. Reprocess it if it is still wanted.",
                            row.streamId(), row.taskId()));
                    pendingStreamDao.deleteStream(row.streamId(), row.taskId());

                } else if (row.attempt() >= MAX_ATTEMPTS) {
                    final String message = LogUtil.message(
                            "The documents indexed from this stream were removed from the index because an " +
                            "index shard failed to commit them or was lost before it could, or the stream " +
                            "was only part indexed. This has now happened {} times so the stream will not " +
                            "be indexed again. Reprocess it once the cause has been dealt with.",
                            row.attempt());
                    if (recovery.fail(row.taskId(), message)) {
                        pendingStreamDao.deleteStream(row.streamId(), row.taskId());
                    }

                } else {
                    final String message = LogUtil.message(
                            "The documents indexed from this stream were removed from the index because an " +
                            "index shard failed to commit them or was lost before it could, or the stream " +
                            "was only part indexed. The stream will be indexed again (failed attempt {} of {}).",
                            row.attempt(),
                            MAX_ATTEMPTS);
                    if (recovery.retry(row.taskId(), message)) {
                        pendingStreamDao.setRetrying(row.id());
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(() -> LogUtil.message(
                        "Unable to have stream {} indexed again by task {}: {}",
                        row.streamId(), row.taskId(), e.getMessage()), e);
            }
        }
    }
}
