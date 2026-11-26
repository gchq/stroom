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

import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Records what each writer's commits make safe, and what its close makes safe, and then completes the tasks
/// that have nothing left to commit.
@Singleton
public class IndexShardCommitRecorder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardCommitRecorder.class);

    private final IndexShardTaskDao taskDao;
    private final IndexShardPurgeDao purgeDao;
    private final IndexShardTaskCompletion completion;
    private final SecurityContext securityContext;

    /// Creates the recorder.
    ///
    /// @param taskDao         Reads and marks the rows.
    /// @param purgeDao        Reads the purges.
    /// @param completion      Completes the tasks that may be finished.
    /// @param securityContext Records as the processing user.
    @Inject
    IndexShardCommitRecorder(final IndexShardTaskDao taskDao,
                             final IndexShardPurgeDao purgeDao,
                             final IndexShardTaskCompletion completion,
                             final SecurityContext securityContext) {
        this.taskDao = taskDao;
        this.purgeDao = purgeDao;
        this.completion = completion;
        this.securityContext = securityContext;
    }

    /// Makes what a writer tells of its commits. Listing before the commit is what makes marking after it
    /// safe: a *written* row's run has ended, so all its documents were in the writer before the commit began.
    ///
    /// @param indexShardId The id of the shard.
    /// @param writerId     The writer.
    /// @return What the writer is to tell.
    public IndexShardCommitListener createListener(final long indexShardId, final String writerId) {
        Objects.requireNonNull(writerId);
        return () -> securityContext.asProcessingUserResult(() -> {
            final List<IndexShardTaskRow> rows = taskDao.findWritten(indexShardId, writerId);
            final List<IndexShardPurge> purges = purgeDao.findByShard(indexShardId)
                    .stream()
                    .filter(purge -> writerId.equals(purge.writerId()))
                    .toList();
            if (rows.isEmpty() && purges.isEmpty()) {
                return () -> {
                };
            }
            return () -> securityContext.asProcessingUser(() -> {
                if (taskDao.markCommitted(indexShardId, writerId, rows, purges)) {
                    completion.check(taskIds(rows));
                } else {
                    // The shard is no longer this writer's, so what it had is counted as lost.
                    LOGGER.debug("Commit of index shard {} not recorded as its writer {} has gone",
                            indexShardId, writerId);
                }
            });
        });
    }

    /// Completes the tasks whose rows a writer's clean close has marked *committed*.
    ///
    /// @param taskIds The ids of the tasks.
    public void closed(final Collection<Long> taskIds) {
        completion.check(taskIds);
    }

    private static Set<Long> taskIds(final List<IndexShardTaskRow> rows) {
        return rows.stream()
                .map(IndexShardTaskRow::taskId)
                .collect(Collectors.toSet());
    }
}
