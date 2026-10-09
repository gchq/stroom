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

import stroom.docref.DocRef;
import stroom.index.mock.MockActiveShardsCache;
import stroom.index.mock.MockIndexShardCreator;
import stroom.index.mock.MockIndexShardDao;
import stroom.index.mock.MockIndexShardWriterCache;
import stroom.index.mock.MockLuceneIndexDocCache;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.query.language.functions.ValString;
import stroom.search.extraction.FieldValue;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.concurrent.SimpleExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexShardPoolImpl extends StroomUnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestIndexShardPoolImpl.class);

    private final AtomicInteger failedThreads = new AtomicInteger(0);

    @TempDir
    static Path tempDir;

    @Test
    void testOneIndex() throws InterruptedException {
        final long maxShardId = doTest(
                1,
                10,
                1,
                1,
                10000);
        // Make sure we only created one index shard.
        assertThat(maxShardId).isEqualTo(1);
    }

    @Test
    void testManyMoreThreadsThanIndex() throws InterruptedException {
        final long maxShardId = doTest(
                100,
                100,
                2,
                5,
                10000);
        // Because we are using many threads we should have created the maximum
        // number of indexes for each index name, e.g. 2 * 5.
        assertThat(maxShardId).isEqualTo(10);
    }

    @Test
    void testOneThreadWithManyIndex() throws InterruptedException {
        // 10 threads, 1000 jobs, 10 different indexes, max 3 shards per index.
        final long maxShardId = doTest(
                1,
                1000,
                10,
                3,
                10000);
        assertThat(maxShardId).as("Expected more than 30 but was " + maxShardId).isEqualTo(30);
    }

    @Test
    void testManyThreadWithManyIndexHittingMax() throws InterruptedException {
        final long maxShardId = doTest(
                10,
                995,
                1,
                5,
                50);
        assertThat(maxShardId >= 20 && maxShardId <= 22)
                .as("Expected 20 to 22 but was " + maxShardId).isTrue();
    }

    private long doTest(final int threadSize,
                        final int jobSize,
                        final int numberOfIndexes,
                        final int shardsPerPartition,
                        final int maxDocumentsPerIndexShard) throws InterruptedException {
        final LuceneIndexField indexField = LuceneIndexField.createField("test");
        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(indexField);

        final MockIndexShardDao indexShardDao = new MockIndexShardDao();
        final IndexShardCreator indexShardCreator = new MockIndexShardCreator(() -> tempDir, indexShardDao);
        final IndexShardWriterCache indexShardWriterCache =
                new MockIndexShardWriterCache(maxDocumentsPerIndexShard);
        final MockLuceneIndexDocCache luceneIndexDocCache = new MockLuceneIndexDocCache();
        final ActiveShardsCache activeShardsCache =
                new MockActiveShardsCache(() -> "test",
                        indexShardWriterCache,
                        indexShardDao,
                        indexShardCreator,
                        luceneIndexDocCache);
        final Indexer indexer = new IndexerImpl(activeShardsCache);

        failedThreads.set(0);

        final SimpleExecutor simpleExecutor = new SimpleExecutor(threadSize);

        for (int i = 0; i < numberOfIndexes; i++) {
            final LuceneIndexDoc index = LuceneIndexDoc.builder()
                    .uuid("uuid" + i)
                    .name("index " + i)
                    .fields(indexFields)
                    .maxDocsPerShard(maxDocumentsPerIndexShard)
                    .shardsPerPartition(shardsPerPartition)
                    .build();
            luceneIndexDocCache.put(new DocRef(LuceneIndexDoc.TYPE, "uuid" + i), index);

            for (int j = 0; j < jobSize; j++) {
                final IndexShardKey indexShardKey = IndexShardKey.createKey(index);
                simpleExecutor.execute(new IndexThread(indexer, indexShardKey, indexField, i));
            }
        }

        simpleExecutor.waitForComplete();
        simpleExecutor.stop(false);
//            indexShardManager.shutdown();

        assertThat(failedThreads.get()).as("Not expecting any errored threads").isEqualTo(0);
//        } catch (final RuntimeException e) {
//            throw new RuntimeException(e.getMessage(), e);
//        }

        return indexShardDao.getMaxId();
    }

    class IndexThread extends Thread {

        private final Indexer indexer;
        private final IndexShardKey indexShardKey;
        private final LuceneIndexField indexField;
        private final int testNumber;

        IndexThread(final Indexer indexer, final IndexShardKey indexShardKey,
                    final LuceneIndexField indexField, final int testNumber) {
            this.indexer = indexer;
            this.indexShardKey = indexShardKey;
            this.indexField = indexField;
            this.testNumber = testNumber;
        }

        @Override
        public void run() {
            try {
                // Do some work.
                final FieldValue field = new FieldValue(indexField, ValString.create("test"));
                final IndexDocument document = new IndexDocument();
                document.add(field);

                indexer.addDocument(indexShardKey, document);

//                // Delay returning the writer to the pool.
//                ThreadUtil.sleep(1);

                // // Return the writer to the pool.
                // indexShardManager.returnObject(poolItem, true);

            } catch (final RuntimeException e) {
                LOGGER.error("TEST ERROR " + testNumber, e);
                failedThreads.incrementAndGet();
            }
        }
    }
}
