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

public interface IndexShardWriter {

    void addDocument(IndexDocument document) throws IndexException;

    void setMaxDocumentCount(int maxDocumentCount);

    void flush();

    void close();

    int getDocumentCount();

    long getIndexShardId();

    long getCreationTime();

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
