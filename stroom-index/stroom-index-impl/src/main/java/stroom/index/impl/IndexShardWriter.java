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

package stroom.index.impl;

import stroom.index.shared.IndexException;

import java.util.function.BooleanSupplier;

public interface IndexShardWriter {

    void addDocument(IndexDocument document) throws IndexException;

    void setMaxDocumentCount(int maxDocumentCount);

    void flush();

    void close();

    int getDocumentCount();

    long getIndexShardId();

    long getCreationTime();

    /// Says whether this writer can never be used again, because it has been closed, has given up its shard
    /// or has been closed by Lucene after an error it could not recover from. Such a writer must be taken
    /// out of use, or its shard can't be opened by anything else.
    ///
    /// @return True if the writer can never be used again.
    default boolean isUnusable() {
        return false;
    }

    /// Gives the writer a way to check that this node still holds the lease on the shard. The writer must
    /// then refuse to add documents or commit once the lease is no longer held, and discard what it has not
    /// committed, because another node may by then be writing to the shard. A writer that is never given a
    /// check, as for a shard on a local volume, always writes.
    ///
    /// @param leaseHeld Returns true while this node holds the lease on the shard.
    default void setLeaseCheck(final BooleanSupplier leaseHeld) {
        // Writers that can't be used on shared volumes need no lease.
    }

    /// Says whether the shard holds commits that this writer has replaced but is keeping for a while, for
    /// the sake of readers on other nodes that may still be using them.
    ///
    /// @return True if there are replaced commits still to be removed.
    default boolean hasReplacedCommits() {
        return false;
    }

    /// Removes any commits this writer is keeping that have been replaced, whatever their age. Only for use
    /// once the period they had to be kept for is known to have passed.
    default void pruneReplacedCommits() {
        // Writers that keep no replaced commits have nothing to remove.
    }
}
