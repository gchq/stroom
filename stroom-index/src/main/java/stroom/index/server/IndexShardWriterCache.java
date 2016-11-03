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

package stroom.index.server;

import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.cache.CacheBean;
import stroom.entity.shared.FindCloseService;
import stroom.entity.shared.FindDeleteService;
import stroom.entity.shared.FindFlushService;

/**
 * API into our index shard cache.
 */
public interface IndexShardWriterCache
        extends CacheBean<IndexShardKey, IndexShardWriter>, FindDeleteService<FindIndexShardCriteria>,
        FindCloseService<FindIndexShardCriteria>, FindFlushService<FindIndexShardCriteria> {
    IndexShardWriter getWriter(final IndexShard indexShard);

    void shutdown();

    void flushAll();
}
