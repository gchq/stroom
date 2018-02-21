/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.index;

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Range;
import stroom.entity.shared.Sort.Direction;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.Index.PartitionBy;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.node.NodeCache;
import stroom.node.VolumeService;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.query.api.v2.DocRef;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;

import javax.annotation.Resource;

public class TestIndexShardServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private IndexService indexService;
    @Resource
    private IndexShardService indexShardService;
    @Resource
    private NodeCache nodeCache;
    @Resource
    private VolumeService volumeService;

    @Override
    protected void onBefore() {
        clean();
    }

    /**
     * Test.
     */
    @Test
    public void test() {
        final Volume volume = volumeService.find(new FindVolumeCriteria()).getFirst();

        Index index1 = indexService.create("Test Index 1");
        index1.getVolumes().add(volume);
        index1 = indexService.save(index1);
        final IndexShardKey indexShardKey1 = IndexShardKeyUtil.createTestKey(index1);

        Index index2 = indexService.create("Test Index 2");
        index2.getVolumes().add(volume);
        index2 = indexService.save(index2);
        final IndexShardKey indexShardKey2 = IndexShardKeyUtil.createTestKey(index2);

        final Node node = nodeCache.getDefaultNode();

        final IndexShard call1 = indexShardService.createIndexShard(indexShardKey1, node);
        final IndexShard call2 = indexShardService.createIndexShard(indexShardKey1, node);
        final IndexShard call3 = indexShardService.createIndexShard(indexShardKey1, node);
        final IndexShard call4 = indexShardService.createIndexShard(indexShardKey2, node);

        Assert.assertNotNull(call1);
        Assert.assertNotNull(call2);
        Assert.assertNotNull(call3);
        Assert.assertNotNull(call4);

        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        // Find all index shards.
        Assert.assertEquals(4, indexShardService.find(criteria).size());

        // Find shards for index 1
        criteria.getIndexSet().clear();
        criteria.getIndexSet().add(DocRefUtil.create(index1));
        Assert.assertEquals(3, indexShardService.find(criteria).size());

        // Find shards for index 2
        criteria.getIndexSet().clear();
        criteria.getIndexSet().add(DocRefUtil.create(index2));
        Assert.assertEquals(1, indexShardService.find(criteria).size());

        // Set all the filters
        criteria.setDocumentCountRange(new Range<>(Integer.MAX_VALUE, null));
        Assert.assertEquals(0, indexShardService.find(criteria).size());
    }

    @Test
    public void testOrderBy() {
        final Volume volume = volumeService.find(new FindVolumeCriteria()).getFirst();

        Index index = indexService.create("Test Index 1");
        index.getVolumes().add(volume);
        index.setPartitionBy(PartitionBy.MONTH);
        index.setPartitionSize(1);
        index = indexService.save(index);

        final Node node = nodeCache.getDefaultNode();

        createShard(index, node, "2013-05-01T00:00:00.000Z", 1);
        createShard(index, node, "2013-05-01T00:00:00.000Z", 2);
        createShard(index, node, "2013-06-01T00:00:00.000Z", 3);
        createShard(index, node, "2013-02-01T00:00:00.000Z", 4);
        createShard(index, node, "2013-02-01T00:00:00.000Z", 5);
        createShard(index, node, "2012-01-01T00:00:00.000Z", 6);
        createShard(index, node, "2011-02-01T00:00:00.000Z", 7);
        createShard(index, node, "2014-08-01T00:00:00.000Z", 8);
        createShard(index, node, "2011-01-01T00:00:00.000Z", 9);
        createShard(index, node, "2011-02-01T00:00:00.000Z", 10);

        final FindIndexShardCriteria findIndexShardCriteria = new FindIndexShardCriteria();
        // Order by partition name and key.
        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_PARTITION, Direction.DESCENDING, false);
        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_ID, Direction.DESCENDING, false);

        // Find data.
        final BaseResultList<IndexShard> list = indexShardService.find(findIndexShardCriteria);

        Assert.assertEquals(10, list.size());

        IndexShard lastShard = null;
        for (final IndexShard indexShard : list) {
            if (lastShard != null) {
                if (lastShard.getPartition().equals(indexShard.getPartition())) {
                    // Compare ids
                    Assert.assertTrue(indexShard.getId() < lastShard.getId());
                } else {
                    Assert.assertTrue(indexShard.getPartition().compareTo(lastShard.getPartition()) < 0);
                }
            }

            lastShard = indexShard;
        }
    }

    private void createShard(final Index index, final Node node, final String dateTime, final int shardNo) {
        final long timeMs = DateUtil.parseNormalDateTimeString(dateTime);
        final IndexShardKey key = IndexShardKeyUtil.createTimeBasedKey(index, timeMs, shardNo);
        indexShardService.createIndexShard(key, node);
    }
}
