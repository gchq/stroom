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

import stroom.index.shared.IndexException;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;

@Singleton
class IndexShardPendingStreamsImpl implements IndexShardPendingStreams {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardPendingStreamsImpl.class);

    // How long a task waits for an earlier attempt's documents to be removed before it gives up.
    private static final Duration DEFAULT_DELETE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

    private final IndexShardPendingStreamDao pendingStreamDao;
    private final Provider<IndexShardPendingStreamRecovery> recoveryProvider;
    private final Duration deleteTimeout;
    private final Duration pollInterval;

    @Inject
    IndexShardPendingStreamsImpl(final IndexShardPendingStreamDao pendingStreamDao,
                                 final Provider<IndexShardPendingStreamRecovery> recoveryProvider) {
        this(pendingStreamDao, recoveryProvider, DEFAULT_DELETE_TIMEOUT, DEFAULT_POLL_INTERVAL);
    }

    /// Creates the service with given waiting times.
    ///
    /// @param pendingStreamDao Where the rows are kept.
    /// @param recoveryProvider Removes documents that are marked to be removed.
    /// @param deleteTimeout    How long to wait for an earlier attempt's documents to be removed.
    /// @param pollInterval     How long to wait between looking.
    IndexShardPendingStreamsImpl(final IndexShardPendingStreamDao pendingStreamDao,
                                 final Provider<IndexShardPendingStreamRecovery> recoveryProvider,
                                 final Duration deleteTimeout,
                                 final Duration pollInterval) {
        this.pendingStreamDao = pendingStreamDao;
        this.recoveryProvider = recoveryProvider;
        this.deleteTimeout = deleteTimeout;
        this.pollInterval = pollInterval;
    }

    @Override
    public PendingStreamTracker begin(final long streamId, final long taskId) {
        int toDelete = pendingStreamDao.beginAttempt(streamId, taskId);
        if (toDelete > 0) {
            LOGGER.info(() -> LogUtil.message(
                    "Waiting for the documents an earlier attempt to index stream {} left in the index to be " +
                    "removed before indexing it again (task {})", streamId, taskId));
            final long deadline = System.nanoTime() + deleteTimeout.toNanos();
            while (toDelete > 0) {
                // Does what this node can. Shards that other nodes are writing to are for them to do.
                try {
                    recoveryProvider.get().removeMarkedDocuments();
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                }
                toDelete = pendingStreamDao.countRowsToDelete(streamId, taskId);
                if (toDelete > 0) {
                    if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) {
                        // Leaves the task to be run again once the documents have gone.
                        pendingStreamDao.failAttempt(streamId, taskId);
                        throw new IndexException(LogUtil.message(
                                "Unable to index stream {} as the documents an earlier attempt left in {} " +
                                "index shard(s) have not been removed. The stream will be indexed again " +
                                "once they have.", streamId, toDelete));
                    }
                    ThreadUtil.sleep(pollInterval.toMillis());
                }
            }
        }
        return new PendingStreamTracker(pendingStreamDao, streamId, taskId);
    }
}
