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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeType;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.concurrent.SimpleExecutor;
import stroom.util.io.FileUtil;
import stroom.util.test.CheckedLimit;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestIndexShardPoolImpl2 extends StroomUnitTest {
    public static int getRandomNumber(final int size) {
        return (int) Math.floor((Math.random() * size));
    }

    @Before
    public void before() {
        FileUtil.deleteContents(getCurrentTestDir().resolve( "index"));
    }

    @Test
    public void testThreadingLikeTheRealThing() throws InterruptedException {
        final IndexField indexField = IndexField.createField("test");
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(indexField);

        // 10 threads, 1000 jobs, 10 different indexes, max 3 shards per index.
        final CheckedLimit checkedLimit = new CheckedLimit(1);
        final AtomicLong indexShardId = new AtomicLong(0);
        final AtomicInteger indexShardsCreated = new AtomicInteger(0);

        final Node defaultNode = new Node();
        defaultNode.setName("TEST");

        final MockIndexShardService mockIndexShardService = new MockIndexShardService() {
            @Override
            public IndexShard createIndexShard(final IndexShardKey indexShardKey, final Node ownerNode) {
                checkedLimit.increment();
                indexShardsCreated.incrementAndGet();
                final IndexShard indexShard = new IndexShard();
                indexShard.setIndex(indexShardKey.getIndex());
                indexShard.setPartition(indexShardKey.getPartition());
                indexShard.setPartitionFromTime(indexShardKey.getPartitionFromTime());
                indexShard.setPartitionToTime(indexShardKey.getPartitionToTime());
                indexShard.setNode(ownerNode);
                indexShard.setId(indexShardId.incrementAndGet());
                indexShard.setVolume(
                        Volume.create(defaultNode, FileUtil.getCanonicalPath(getCurrentTestDir()), VolumeType.PUBLIC));
                indexShard.setIndexVersion(LuceneVersionUtil.getCurrentVersion());
                FileUtil.deleteContents(IndexShardUtil.getIndexPath(indexShard));
                return indexShard;
            }
        };

        try {
            final Indexer indexer = new MockIndexer();

            final Index index = new Index();
            index.setId(1);
            index.setIndexFieldsObject(indexFields);
            index.setMaxDocsPerShard(1000);

            final IndexShardKey indexShardKey = IndexShardKeyUtil.createTestKey(index);
            final SimpleExecutor simpleExecutor = new SimpleExecutor(10);

            for (int i = 0; i < 1000; i++) {
                simpleExecutor.execute(() -> {
                    for (int i1 = 0; i1 < 100; i1++) {
                        // Do some work.
                        final Field field = FieldFactory.create(indexField, "test");
                        final Document document = new Document();
                        document.add(field);
                        indexer.addDocument(indexShardKey, document);
                    }
                });
            }

            simpleExecutor.stop(false);
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
