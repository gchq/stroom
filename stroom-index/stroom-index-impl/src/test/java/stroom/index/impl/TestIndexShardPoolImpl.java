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

import stroom.index.mock.MockIndexShardService;
import stroom.index.mock.MockIndexShardWriterCache;
import stroom.index.shared.IndexFields;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexShardPoolImpl extends StroomUnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestIndexShardPoolImpl.class);

    private final AtomicInteger indexShardsCreated = new AtomicInteger(0);
    private static final AtomicLong indexShardId = new AtomicLong(0);
    private final AtomicInteger failedThreads = new AtomicInteger(0);

//    public static int getRandomNumber(final int size) {
//        return (int) Math.floor((Math.random() * size));
//    }
//
//    @Mock
//    private NodeInfo nodeInfo;
//
//    @BeforeEach
//    public void init() {
//        FileUtil.deleteContents(getCurrentTestDir().resolve("index"));
//    }

    @TempDir
    static Path tempDir;

    @Test
    void testOneIndex() throws InterruptedException {
        doTest(1, 10, 1, 1, 10000);
        // Make sure we only created one index shard.
        assertThat(indexShardsCreated.get()).isEqualTo(1);
    }

    @Test
    void testManyMoreThreadsThanIndex() throws InterruptedException {
        doTest(100, 100, 2, 5, 10000);
        // Because we are using many threads we should have created the maximum
        // number of indexes for each index name, e.g. 2 * 5.
        assertThat(indexShardsCreated.get()).isEqualTo(10);
    }

    @Test
    void testOneThreadWithManyIndex() throws InterruptedException {
        // 10 threads, 1000 jobs, 10 different indexes, max 3 shards per index.
        doTest(1, 1000, 10, 3, 10000);
        final int size = indexShardsCreated.get();
        assertThat(size).as("Expected more than 30 but was " + size).isEqualTo(30);
    }

    @Test
    void testManyThreadWithManyIndexHittingMax() throws InterruptedException {
        doTest(10, 995, 1, 5, 50);
        final int size = indexShardsCreated.get();

        assertThat(size >= 20 && size <= 22).as("Expected 20 to 22 but was " + size).isTrue();
    }

    private void doTest(final int threadSize, final int jobSize, final int numberOfIndexes,
                        final int shardsPerPartition, final int maxDocumentsPerIndexShard) throws InterruptedException {
        final LuceneIndexField indexField = LuceneIndexField.createField("test");
        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(indexField);

        final IndexShardService mockIndexShardService = new MockIndexShardService(indexShardsCreated,
                indexShardId,
                () -> tempDir);

        final IndexShardWriterCache indexShardWriterCache =
                new MockIndexShardWriterCache(mockIndexShardService, maxDocumentsPerIndexShard);
        final Indexer indexer = new IndexerImpl(indexShardWriterCache, null);

        indexShardsCreated.set(0);
        failedThreads.set(0);

        final SimpleExecutor simpleExecutor = new SimpleExecutor(threadSize);

        for (int i = 0; i < numberOfIndexes; i++) {
            final LuceneIndexDoc index = new LuceneIndexDoc();
            index.setUuid("uuid" + i);
            index.setName("index " + i);
            index.setFields(indexFields);
            index.setMaxDocsPerShard(maxDocumentsPerIndexShard);
            index.setShardsPerPartition(shardsPerPartition);

            for (int j = 0; j < jobSize; j++) {
                final IndexShardKey indexShardKey = IndexShardKeyUtil.createTestKey(index);
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
