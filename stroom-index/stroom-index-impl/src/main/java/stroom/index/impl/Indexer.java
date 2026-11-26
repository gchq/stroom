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

    /// Adds a document to a shard of a partition of an index, for a run of a processor task that keeps a
    /// record of the shards it writes to.
    ///
    /// @param key      Says which index and partition.
    /// @param document The document.
    /// @param tracker  Told of the shard and writer the document goes to, before and after it is added.
    default void addDocument(final IndexShardKey key, final IndexDocument document, final IndexRunTracker tracker) {
        addDocument(key, document);
    }
}
