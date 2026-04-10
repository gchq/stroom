/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.index.shared.IndexShard;
import stroom.query.api.QueryKey;
import stroom.task.api.TaskContext;
import stroom.util.shared.NullSafe;

import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;

import java.util.Collection;
import java.util.concurrent.atomic.LongAdder;

class IndexShardHitCollectorManager implements CollectorManager<IndexShardHitCollector, Long> {

    private final TaskContext taskContext;
    private final QueryKey queryKey;
    private final IndexShard indexShard;
    private final Query query;
    private final DocIdQueue docIdQueue;
    private final LongAdder totalHitCount;
    private final ScoreMode scoreMode;

    public IndexShardHitCollectorManager(final TaskContext taskContext,
                                         final QueryKey queryKey,
                                         final IndexShard indexShard,
                                         final Query query,
                                         final DocIdQueue docIdQueue,
                                         final LongAdder totalHitCount,
                                         final ScoreMode scoreMode) {
        this.taskContext = taskContext;
        this.queryKey = queryKey;
        this.indexShard = indexShard;
        this.query = query;
        this.docIdQueue = docIdQueue;
        this.totalHitCount = totalHitCount;
        this.scoreMode = scoreMode;
    }

    @Override
    public IndexShardHitCollector newCollector() {
        // Create a new collector instance for a segment
        return new IndexShardHitCollector(
                taskContext,
                queryKey,
                indexShard,
                query,
                docIdQueue,
                totalHitCount,
                scoreMode);
    }

    @Override
    public Long reduce(final Collection<IndexShardHitCollector> collectors) {
        long totalHits = 0;
        for (final IndexShardHitCollector collector : collectors) {
            totalHits += collector.getLocalHitCount();
        }
        return totalHits;
    }

    @Override
    public String toString() {
        return "Query key: " + queryKey
               + ", shard: " + NullSafe.get(indexShard, IndexShard::getId)
               + ", total hits: " + NullSafe.getOrElse(totalHitCount, LongAdder::sum, -1);
    }
}
