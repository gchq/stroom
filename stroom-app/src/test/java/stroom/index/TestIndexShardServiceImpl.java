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

package stroom.index;


import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexVolumeService;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexVolume;
import stroom.node.api.NodeInfo;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TestIndexShardServiceImpl extends AbstractCoreIntegrationTest {

    @Inject
    private IndexStore indexStore;
    @Inject
    private IndexVolumeGroupService indexVolumeGroupService;
    @Inject
    private IndexShardService indexShardService;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private IndexVolumeService indexVolumeService;

    @Test
    void testSelectIndexVolume_unknownGroup() {
        assertThrows(IndexException.class,
                () -> indexVolumeService.selectVolume(
                        "unknown",
                        nodeInfo.getThisNodeName()));
    }

    @Test
    void testSelectIndexVolume_validGroup() {
        final String groupName = indexVolumeGroupService.getNames().get(0);

        final IndexVolume indexVolume = indexVolumeService.selectVolume(
                groupName,
                nodeInfo.getThisNodeName());

        Assertions.assertThat(indexVolume)
                .isNotNull();
        Assertions.assertThat(indexVolume.getCapacityInfo().isFull())
                .isFalse();
    }

    //    @Override
//    protected void onBefore() {
//        clean();
//    }

//    /**
//     * Test.
//     */
//    @Test
//    void test() {
//        // Create required volume groups
//        final String volumeGroup = String.format("IndexShardTest_%s", UUID.randomUUID());
//        indexVolumeGroupService.create(volumeGroup);
//
//        final IndexVolume volume = indexVolumeService.getAll().stream()
//                .findFirst()
//                .orElseThrow(() -> new AssertionError("Could not get index volume"));
//        indexVolumeService.addVolumeToGroup(volume.getId(), volumeGroup);
//
//        final DocRef indexRef1 = indexStore.createDocument("Test Index 1");
//        final IndexDoc index1 = indexStore.readDocument(indexRef1);
//        index1.setVolumeGroupName(volumeGroup);
//        indexStore.writeDocument(index1);
//
//        final IndexShardKey indexShardKey1 = IndexShardKey.createTestKey(index1);
//
//        final DocRef indexRef2 = indexStore.createDocument("Test Index 2");
//        final IndexDoc index2 = indexStore.readDocument(indexRef2);
//        index2.setVolumeGroupName(volumeGroup);
//        indexStore.writeDocument(index2);
//        final IndexShardKey indexShardKey2 = IndexShardKey.createTestKey(index2);
//
//        final String nodeName = nodeInfo.getThisNodeName();
//
//        final IndexShard call1 = indexShardService.createIndexShard(indexShardKey1, nodeName);
//        final IndexShard call2 = indexShardService.createIndexShard(indexShardKey1, nodeName);
//        final IndexShard call3 = indexShardService.createIndexShard(indexShardKey1, nodeName);
//        final IndexShard call4 = indexShardService.createIndexShard(indexShardKey2, nodeName);
//
//        assertThat(call1).isNotNull();
//        assertThat(call2).isNotNull();
//        assertThat(call3).isNotNull();
//        assertThat(call4).isNotNull();
//
//        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
//        // Find all index shards.
//        assertThat(indexShardService.find(criteria).size()).isEqualTo(4);
//
//        // Find shards for index 1
//        criteria.getIndexUuidSet().clear();
//        criteria.getIndexUuidSet().add(indexRef1.getUuid());
//        assertThat(indexShardService.find(criteria).size()).isEqualTo(3);
//
//        // Find shards for index 2
//        criteria.getIndexUuidSet().clear();
//        criteria.getIndexUuidSet().add(indexRef2.getUuid());
//        assertThat(indexShardService.find(criteria).size()).isEqualTo(1);
//
//        // Set all the filters
//        criteria.setDocumentCountRange(new Range<>(Integer.MAX_VALUE, null));
//        assertThat(indexShardService.find(criteria).size()).isEqualTo(0);
//    }
//
//    @Test
//    void testOrderBy() {
//        final String indexVolumeGroup = String.format("IndexShardTestOrderBy_%s", UUID.randomUUID());
//        indexVolumeGroupService.create(indexVolumeGroup);
//
//        final IndexVolume volume = indexVolumeService.getAll().stream()
//                .findFirst()
//                .orElseThrow(() -> new AssertionError("Could not get index volume"));
//        indexVolumeService.addVolumeToGroup(volume.getId(), indexVolumeGroup);
//
//        final DocRef indexRef = indexStore.createDocument("Test Index 1");
//        final IndexDoc index = indexStore.readDocument(indexRef);
//        index.setVolumeGroupName(indexVolumeGroup);
//        index.setPartitionBy(PartitionBy.MONTH);
//        index.setPartitionSize(1);
//        indexStore.writeDocument(index);
//
//        final String nodeName = nodeInfo.getThisNodeName();
//
//        createShard(index, nodeName, "2013-05-01T00:00:00.000Z", 1);
//        createShard(index, nodeName, "2013-05-01T00:00:00.000Z", 2);
//        createShard(index, nodeName, "2013-06-01T00:00:00.000Z", 3);
//        createShard(index, nodeName, "2013-02-01T00:00:00.000Z", 4);
//        createShard(index, nodeName, "2013-02-01T00:00:00.000Z", 5);
//        createShard(index, nodeName, "2012-01-01T00:00:00.000Z", 6);
//        createShard(index, nodeName, "2011-02-01T00:00:00.000Z", 7);
//        createShard(index, nodeName, "2014-08-01T00:00:00.000Z", 8);
//        createShard(index, nodeName, "2011-01-01T00:00:00.000Z", 9);
//        createShard(index, nodeName, "2011-02-01T00:00:00.000Z", 10);
//
//        final FindIndexShardCriteria findIndexShardCriteria = FindIndexShardCriteria.matchAll();
//        // Order by partition name and key.
//        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_PARTITION, true, false);
//        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_ID, true, false);
//
//        // Find data.
//        final List<IndexShard> list = indexShardService.find(findIndexShardCriteria);
//
//        assertThat(list.size()).isEqualTo(10);
//
//        list.forEach(i -> System.out.println(i.toString()));
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
//    }
//
//    private void createShard(final IndexDoc index,
//                             final String nodeName,
//                             final String dateTime,
//                             final int shardNo) {
//        final long timeMs = DateUtil.parseNormalDateTimeString(dateTime);
//        final IndexShardKey key = IndexShardKey.createTimeBasedKey(index, timeMs, shardNo);
//        indexShardService.createIndexShard(key, nodeName);
//    }
}
