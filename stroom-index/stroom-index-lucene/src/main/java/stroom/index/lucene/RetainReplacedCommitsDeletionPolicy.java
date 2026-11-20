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

package stroom.index.lucene;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexDeletionPolicy;

import java.util.List;
import java.util.function.LongSupplier;

/// Keeps the commits of an index shard that have been replaced by a newer commit for a retention period
/// before deleting them.
///
/// A shard on a shared volume may be read by other nodes while this node writes to it. When a writer merges
/// segments and commits, Lucene normally deletes the files of the commit it replaced at once. A network
/// mount does not keep a deleted file alive for a reader on another node, so that reader would fail part way
/// through a search. Keeping a replaced commit, and so the files it refers to, for longer than a search
/// holds a shard open lets such readers finish. The cost is the disk space of files that would otherwise
/// have been deleted sooner.
///
/// The time a commit was replaced is only held in memory. A commit found when a writer is opened is treated
/// as replaced at that moment, which keeps it for longer than needed but never for less.
class RetainReplacedCommitsDeletionPolicy extends IndexDeletionPolicy {

    private final long retentionMs;
    private final LongSupplier clock;
    // When each replaced commit was first seen to have been replaced, by commit generation.
    private final Long2LongMap replacedTimes = new Long2LongOpenHashMap();
    // Set once every replaced commit is known to be old enough to delete.
    private boolean expireAll;

    /// Creates the policy.
    ///
    /// @param retentionMs How long to keep a commit after it has been replaced, in milliseconds.
    /// @param clock       Supplies the current time in milliseconds.
    RetainReplacedCommitsDeletionPolicy(final long retentionMs, final LongSupplier clock) {
        this.retentionMs = retentionMs;
        this.clock = clock;
    }

    @Override
    public synchronized void onInit(final List<? extends IndexCommit> commits) {
        deleteExpiredCommits(commits);
    }

    @Override
    public synchronized void onCommit(final List<? extends IndexCommit> commits) {
        deleteExpiredCommits(commits);
    }

    /// Says how many replaced commits are being kept.
    ///
    /// @return The number of commits, other than the latest, that have not yet been deleted.
    synchronized int getRetainedCommitCount() {
        return replacedTimes.size();
    }

    /// Makes the policy delete every replaced commit the next time Lucene consults it, whatever their
    /// age. For use on a shard whose retention period is known to have passed since it was last written.
    synchronized void expireAll() {
        expireAll = true;
    }

    // The commits are given oldest first, and the last one is the current commit, which is always kept.
    private void deleteExpiredCommits(final List<? extends IndexCommit> commits) {
        final long nowMs = clock.getAsLong();
        final Long2LongMap stillRetained = new Long2LongOpenHashMap();
        for (int i = 0; i < commits.size() - 1; i++) {
            final IndexCommit commit = commits.get(i);
            final long generation = commit.getGeneration();
            final long replacedTimeMs = replacedTimes.getOrDefault(generation, nowMs);
            if (expireAll || nowMs - replacedTimeMs >= retentionMs) {
                commit.delete();
            } else {
                stillRetained.put(generation, replacedTimeMs);
            }
        }
        replacedTimes.clear();
        replacedTimes.putAll(stillRetained);
    }
}
