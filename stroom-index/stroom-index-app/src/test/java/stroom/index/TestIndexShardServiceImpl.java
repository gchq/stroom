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


import org.junit.jupiter.api.Test;
import stroom.index.service.IndexShardService;
import stroom.index.service.IndexVolumeService;

import stroom.node.api.NodeInfo;

import javax.inject.Inject;

class TestIndexShardServiceImpl {
    @Inject
    private IndexStore indexStore;
    @Inject
    private IndexShardService indexShardService;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private IndexVolumeService indexVolumeService;

    /**
     * Test.
     */
    @Test
    void test() {
        // TODO Re-instate test once service has shaken out
//        final VolumeEntity volume = volumeService.find(new FindVolumeCriteria()).getFirst();
//
//        final DocRef indexRef1 = indexStore.createDocument("Test Index 1");
//        final IndexDoc index1 = indexStore.readDocument(indexRef1);
//        indexVolumeService.setVolumesForIndex(indexRef1, Collections.singleton(volume));
//        final IndexShardKey indexShardKey1 = IndexShardKeyUtil.createTestKey(index1);
//
//        final DocRef indexRef2 = indexStore.createDocument("Test Index 2");
//        final IndexDoc index2 = indexStore.readDocument(indexRef2);
//        indexVolumeService.setVolumesForIndex(indexRef2, Collections.singleton(volume));
//        final IndexShardKey indexShardKey2 = IndexShardKeyUtil.createTestKey(index2);
//
//        final Node node = nodeInfo.getThisNode();
//
//        final IndexShard call1 = indexShardService.createIndexShard(indexShardKey1, node);
//        final IndexShard call2 = indexShardService.createIndexShard(indexShardKey1, node);
//        final IndexShard call3 = indexShardService.createIndexShard(indexShardKey1, node);
//        final IndexShard call4 = indexShardService.createIndexShard(indexShardKey2, node);
//
//        assertThat(call1).isNotNull();
//        assertThat(call2).isNotNull();
//        assertThat(call3).isNotNull();
//        assertThat(call4).isNotNull();
//
//        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
//        // Find all index shards.
//        assertThat(indexShardService.find(criteria).size()).isEqualTo(4);
//
//        // Find shards for index 1
//        criteria.getIndexSet().clear();
//        criteria.getIndexSet().add(indexRef1);
//        assertThat(indexShardService.find(criteria).size()).isEqualTo(3);
//
//        // Find shards for index 2
//        criteria.getIndexSet().clear();
//        criteria.getIndexSet().add(indexRef2);
//        assertThat(indexShardService.find(criteria).size()).isEqualTo(1);
//
//        // Set all the filters
//        criteria.setDocumentCountRange(new Range<>(Integer.MAX_VALUE, null));
//        assertThat(indexShardService.find(criteria).size()).isEqualTo(0);
    }

    @Test
    void testOrderBy() {
        // TODO Re-instate test once service has shaken out
//        final VolumeEntity volume = volumeService.find(new FindVolumeCriteria()).getFirst();
//
//        final DocRef indexRef = indexStore.createDocument("Test Index 1");
//        final IndexDoc index = indexStore.readDocument(indexRef);
//        indexVolumeService.setVolumesForIndex(indexRef, Collections.singleton(volume));
//        index.setPartitionBy(PartitionBy.MONTH);
//        index.setPartitionSize(1);
//        indexStore.writeDocument(index);
//
//        final Node node = nodeInfo.getThisNode();
//
//        createShard(index, node, "2013-05-01T00:00:00.000Z", 1);
//        createShard(index, node, "2013-05-01T00:00:00.000Z", 2);
//        createShard(index, node, "2013-06-01T00:00:00.000Z", 3);
//        createShard(index, node, "2013-02-01T00:00:00.000Z", 4);
//        createShard(index, node, "2013-02-01T00:00:00.000Z", 5);
//        createShard(index, node, "2012-01-01T00:00:00.000Z", 6);
//        createShard(index, node, "2011-02-01T00:00:00.000Z", 7);
//        createShard(index, node, "2014-08-01T00:00:00.000Z", 8);
//        createShard(index, node, "2011-01-01T00:00:00.000Z", 9);
//        createShard(index, node, "2011-02-01T00:00:00.000Z", 10);
//
//        final FindIndexShardCriteria findIndexShardCriteria = new FindIndexShardCriteria();
//        // Order by partition name and key.
//        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_PARTITION, Direction.DESCENDING, false);
//        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_ID, Direction.DESCENDING, false);
//
//        // Find data.
//        final BaseResultList<IndexShard> list = indexShardService.find(findIndexShardCriteria);
//
//        assertThat(list.size()).isEqualTo(10);
//
//        IndexShard lastShard = null;
//        for (final IndexShard indexShard : list) {
//            if (lastShard != null) {
//                if (lastShard.getPartition().equals(indexShard.getPartition())) {
//                    // Compare ids
//                    assertThat(indexShard.getId() < lastShard.getId()).isTrue();
//                } else {
//                    assertThat(indexShard.getPartition().compareTo(lastShard.getPartition()) < 0).isTrue();
//                }
//            }
//
//            lastShard = indexShard;
//        }
    }
//
//    private void createShard(final IndexDoc index, final Node node, final String dateTime, final int shardNo) {
//        final long timeMs = DateUtil.parseNormalDateTimeString(dateTime);
//        final IndexShardKey key = IndexShardKeyUtil.createTimeBasedKey(index, timeMs, shardNo);
//        indexShardService.createIndexShard(key, node.getName());
//    }
}
