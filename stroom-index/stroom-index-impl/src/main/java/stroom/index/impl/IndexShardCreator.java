/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneVersion;

public interface IndexShardCreator {

    /**
     * Create a new Lucene index shard on the file system and record it in the DB.
     *
     * @param indexShardKey Info about the index and partition to create the shard for.
     * @param ownerNodeName The node that will own the shard.
     * @return The newly created index shard.
     */

    IndexShard createIndexShard(IndexShardKey indexShardKey, String ownerNodeName);

    /**
     * Set the Lucene version we want to create index shards for.
     *
     * @param indexVersion
     */
    void setIndexVersion(LuceneVersion indexVersion);
}
