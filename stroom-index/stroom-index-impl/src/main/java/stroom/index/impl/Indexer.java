/*
 * Copyright 2018 Crown Copyright
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

import stroom.index.shared.IndexShardKey;


public interface Indexer {

    void addDocument(IndexShardKey key, IndexDocument document);

    /// Adds a document to a shard of a partition of an index, on behalf of a stream whose uncommitted
    /// documents are being recorded.
    ///
    /// @param key      Says which index and partition.
    /// @param document The document.
    /// @param tracker  Told which shard the document goes to before it is added, or null if no record is
    ///                 being kept.
    default void addDocument(final IndexShardKey key,
                             final IndexDocument document,
                             final PendingStreamTracker tracker) {
        addDocument(key, document);
    }
}
