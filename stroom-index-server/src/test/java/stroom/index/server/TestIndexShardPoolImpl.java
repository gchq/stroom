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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import stroom.cache.CacheManagerAutoCloseable;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.node.server.NodeCache;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeType;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.concurrent.SimpleExecutor;
import stroom.util.logging.StroomLogger;
import stroom.util.test.StroomUnitTest;
import stroom.util.thread.ThreadUtil;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(MockitoJUnitRunner.class)
public class TestIndexShardPoolImpl extends StroomUnitTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestIndexShardPoolImpl.class);
    static AtomicLong indexShardId = new AtomicLong(0);
    AtomicInteger indexShardsCreated = new AtomicInteger(0);
    AtomicInteger failedThreads = new AtomicInteger(0);
    @Mock
    private NodeCache nodeCache;

    public static int getRandomNumber(final int size) {
        return (int) Math.floor((Math.random() * size));
    }

    @Before
    public void init() {
        FileSystemUtil.deleteContents(new File(getCurrentTestDir(), "index"));
    }

    @Test
    public void testOneIndex() throws InterruptedException {
        doTest(1, 10, 1, 1, 10000);
        // Make sure we only created one index shard.
        Assert.assertEquals(1, indexShardsCreated.get());
    }

    @Test
    public void testManyMoreThreadsThanIndex() throws InterruptedException {
        doTest(100, 100, 2, 5, 10000);
        // Because we are using many threads we should have created the maximum
        // number of indexes for each index name, e.g. 2 * 5.
        Assert.assertEquals(10, indexShardsCreated.get());
    }

    @Test
    public void testOneThreadWithManyIndex() throws InterruptedException {
        // 10 threads, 1000 jobs, 10 different indexes, max 3 shards per index.
        doTest(1, 1000, 10, 3, 10000);
        final int size = indexShardsCreated.get();
        Assert.assertEquals("Expected more than 30 but was " + size, 30, size);
    }

    @Test
    public void testManyThreadWithManyIndexHittingMax() throws InterruptedException {
        doTest(10, 995, 1, 5, 50);
        final int size = indexShardsCreated.get();

        Assert.assertTrue("Expected 20.22 but was " + size, size >= 20 && size <= 22);
    }

    private void doTest(final int threadSize, final int jobSize, final int numberOfIndexes,
                        final int shardsPerPartition, final int maxDocumentsPerIndexShard) throws InterruptedException {
        final IndexField indexField = IndexField.createField("test");
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(indexField);

        // final Set<IndexShard> closedSet = new HashSet<>();
        // final CheckedLimit checkedLimit = new CheckedLimit(shardsPerPartition
        // * numberOfIndexes);

        final Node defaultNode = new Node();
        defaultNode.setName("TEST");

        final MockIndexShardService mockIndexShardService = new MockIndexShardService() {
            @Override
            public IndexShard createIndexShard(final IndexShardKey indexShardKey, final Node node) {
                indexShardsCreated.incrementAndGet();
                // checkedLimit.increment();
                final IndexShard indexShard = new IndexShard();
                indexShard.setIndex(indexShardKey.getIndex());
                indexShard.setPartition(indexShardKey.getPartition());
                indexShard.setPartitionFromTime(indexShardKey.getPartitionFromTime());
                indexShard.setPartitionToTime(indexShardKey.getPartitionToTime());
                indexShard.setNode(node);
                indexShard.setId(indexShardId.incrementAndGet());
                indexShard.setVolume(
                        Volume.create(defaultNode, getCurrentTestDir().getAbsolutePath(), VolumeType.PUBLIC));
                indexShard.setIndexVersion(LuceneVersionUtil.getCurrentVersion());
                FileSystemUtil.deleteContents(IndexShardUtil.getIndexPath(indexShard));
                return indexShard;
            }
        };

        Mockito.when(nodeCache.getDefaultNode()).thenReturn(defaultNode);

        // final NodeCache nodeCache = new NodeCache();
        // nodeCache.setNodeService(new NodeServiceImpl() {
        // @Override
        // public Node getDefaultNode() {
        // return defaultNode;
        // }
        // });

        try (CacheManagerAutoCloseable cacheManager = CacheManagerAutoCloseable.create()) {
            final IndexShardWriterCacheImpl indexShardPool = new IndexShardWriterCacheImpl(cacheManager, null, null,
                    mockIndexShardService, nodeCache) {
                @Override
                protected void destroy(final IndexShardKey key, final IndexShardWriter writer) {
                    // // checkedLimit.decrement();
                    // synchronized (closedSet) {
                    // if (writer != null) {
                    // if (closedSet.contains(writer.getIndexShard())) {
                    // throw new RuntimeException("Closed already called on this
                    // item?");
                    // }
                    // closedSet.add(writer.getIndexShard());
                    // }
                    // }
                    super.destroy(key, writer);
                    if (writer != null && writer.isOpen()) {
                        throw new RuntimeException("Writer should have been closed on destroy!");
                    }
                }
            };

            indexShardsCreated.set(0);
            failedThreads.set(0);

            final SimpleExecutor simpleExecutor = new SimpleExecutor(threadSize);

            for (int i = 0; i < numberOfIndexes; i++) {
                final Index index = new Index();
                index.setName("Index " + i);
                index.setIndexFieldsObject(indexFields);
                index.setMaxDocsPerShard(maxDocumentsPerIndexShard);
                index.setShardsPerPartition(shardsPerPartition);

                for (int j = 0; j < jobSize; j++) {
                    final int testNumber = i;
                    final IndexShardKey indexShardKey = IndexShardKeyUtil.createTestKey(index);
                    simpleExecutor.execute(new IndexThread(indexShardPool, indexShardKey, indexField, testNumber));
                }
            }

            simpleExecutor.waitForComplete();
            simpleExecutor.stop(false);
            indexShardPool.shutdown();

            Assert.assertEquals("Not expecting any errored threads", 0, failedThreads.get());
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    class IndexThread extends Thread {
        private final IndexShardWriterCacheImpl indexShardPoolImpl;
        private final IndexShardKey indexShardKey;
        private final IndexField indexField;
        private final int testNumber;

        public IndexThread(final IndexShardWriterCacheImpl indexShardPoolImpl, final IndexShardKey indexShardKey,
                           final IndexField indexField, final int testNumber) {
            this.indexShardPoolImpl = indexShardPoolImpl;
            this.indexShardKey = indexShardKey;
            this.indexField = indexField;
            this.testNumber = testNumber;
        }

        @Override
        public void run() {
            try {
                // Get a writer from the pool.
                IndexShardWriter writer = indexShardPoolImpl.get(indexShardKey);

                // Do some work.
                final Field field = FieldFactory.create(indexField, "test");
                final Document document = new Document();
                document.add(field);
                while (!writer.addDocument(document)) {
                    indexShardPoolImpl.remove(indexShardKey);

                    // Ask the pool for another one and try again
                    writer = indexShardPoolImpl.get(indexShardKey);
                }

                // Delay returning the writer to the pool.
                ThreadUtil.sleep(1);

                // // Return the writer to the pool.
                // indexShardPoolImpl.returnObject(poolItem, true);

            } catch (final Throwable th) {
                LOGGER.error("TEST ERROR " + testNumber, th);
                failedThreads.incrementAndGet();
            }
        }
    }
}
