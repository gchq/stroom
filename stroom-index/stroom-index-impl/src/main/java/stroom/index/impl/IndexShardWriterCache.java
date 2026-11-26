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

import java.util.Optional;

public interface IndexShardWriterCache {

    Optional<IndexShardWriter> getIfPresent(long indexShardId);

    IndexShardWriter getOrOpenWriter(long indexShardId);

    void flush(long indexShardId);

    void flushAll();

    /// Marks a shard as deleted and closes any writer this node has for it. A shard another node is writing
    /// to is not deleted, as that node would not know.
    ///
    /// @param indexShardId The id of the shard.
    /// @return True if the shard has been marked as deleted.
    boolean delete(long indexShardId);

    void sweep();

    /// Takes a writer out of use and closes it. Does nothing if the writer is not the one in use for its
    /// shard, as it has then been closed already, and the one that replaced it must be left alone.
    ///
    /// @param indexShardWriter The writer.
    void close(IndexShardWriter indexShardWriter);

    void shutdown();
}
