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

package stroom.search;

import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardPendingStreamRecovery;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.index.impl.db.jooq.tables.IndexShardPendingStream.INDEX_SHARD_PENDING_STREAM;

/// Tests that streams indexed by real processor tasks running the indexing pipeline are recorded while
/// they are uncommitted and that nothing is left once their shards have committed.
class TestIndexingPendingStreams extends AbstractCoreIntegrationTest {

    @Inject
    private CommonIndexingTestHelper commonIndexingTestHelper;
    @Inject
    private IndexShardWriterCache indexShardWriterCache;
    @Inject
    private IndexShardPendingStreamRecovery recovery;
    @Inject
    private IndexDbConnProvider indexDbConnProvider;
    @Inject
    private IndexShardDao indexShardDao;

    @Test
    void testProcessedStreams_leaveNoRowsOnceTheirShardsHaveCommitted() {
        JooqUtil.context(indexDbConnProvider, context -> context
                .deleteFrom(INDEX_SHARD_PENDING_STREAM)
                .execute());

        // Translates and indexes the sample data with processor tasks.
        commonIndexingTestHelper.setup();

        indexShardWriterCache.flushAll();
        final int indexed = countDocuments();
        assertThat(indexed).isGreaterThan(0);
        assertThat(countRows()).isZero();

        // And recovery finds nothing to remove, so every document stays.
        recovery.exec();
        indexShardWriterCache.flushAll();
        assertThat(countRows()).isZero();
        assertThat(countDocuments()).isEqualTo(indexed);
    }

    private int countDocuments() {
        return indexShardDao.find(FindIndexShardCriteria.matchAll())
                .getValues()
                .stream()
                .mapToInt(IndexShard::getDocumentCount)
                .sum();
    }

    private int countRows() {
        return JooqUtil.contextResult(indexDbConnProvider, context ->
                context.fetchCount(INDEX_SHARD_PENDING_STREAM));
    }
}
