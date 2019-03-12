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
import stroom.docref.DocRef;
import stroom.index.service.IndexShardService;
import stroom.index.service.IndexVolumeGroupService;
import stroom.index.service.IndexVolumeService;

import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.util.shared.Range;
import stroom.util.shared.Sort.Direction;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexDoc.PartitionBy;
import stroom.index.shared.IndexShard;
import stroom.node.api.NodeInfo;
import stroom.node.shared.Node;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Override
    protected void onBefore() {
        clean();
    }

    /**
     * Test.
     */
    @Test
    void test() {
        // Create required volume groups
        final String volumeGroup = String.format("IndexShardTest_%s", UUID.randomUUID());
        indexVolumeGroupService.create(volumeGroup);

        final IndexVolume volume = indexVolumeService.getAll().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not get index volume"));
        indexVolumeService.addVolumeToGroup(volume.getId(), volumeGroup);

        final DocRef indexRef1 = indexStore.createDocument("Test Index 1");
        final IndexDoc index1 = indexStore.readDocument(indexRef1);
        index1.setVolumeGroupName(volumeGroup);
        indexStore.writeDocument(index1);

        final IndexShardKey indexShardKey1 = IndexShardKeyUtil.createTestKey(index1);

        final DocRef indexRef2 = indexStore.createDocument("Test Index 2");
        final IndexDoc index2 = indexStore.readDocument(indexRef2);
        index2.setVolumeGroupName(volumeGroup);
        indexStore.writeDocument(index2);
        final IndexShardKey indexShardKey2 = IndexShardKeyUtil.createTestKey(index2);

        final Node node = nodeInfo.getThisNode();

        final IndexShard call1 = indexShardService.createIndexShard(indexShardKey1, node.getName());
        final IndexShard call2 = indexShardService.createIndexShard(indexShardKey1, node.getName());
        final IndexShard call3 = indexShardService.createIndexShard(indexShardKey1, node.getName());
        final IndexShard call4 = indexShardService.createIndexShard(indexShardKey2, node.getName());

        assertThat(call1).isNotNull();
        assertThat(call2).isNotNull();
        assertThat(call3).isNotNull();
        assertThat(call4).isNotNull();

        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        // Find all index shards.
        assertThat(indexShardService.find(criteria).size()).isEqualTo(4);

        // Find shards for index 1
        criteria.getIndexUuidSet().clear();
        criteria.getIndexUuidSet().add(indexRef1.getUuid());
        assertThat(indexShardService.find(criteria).size()).isEqualTo(3);

        // Find shards for index 2
        criteria.getIndexUuidSet().clear();
        criteria.getIndexUuidSet().add(indexRef2.getUuid());
        assertThat(indexShardService.find(criteria).size()).isEqualTo(1);

        // Set all the filters
        criteria.setDocumentCountRange(new Range<>(Integer.MAX_VALUE, null));
        assertThat(indexShardService.find(criteria).size()).isEqualTo(0);
    }

    @Test
    void testOrderBy() {
        final String indexVolumeGroup = String.format("IndexShardTestOrderBy_%s", UUID.randomUUID());
        indexVolumeGroupService.create(indexVolumeGroup);

        final IndexVolume volume = indexVolumeService.getAll().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not get index volume"));
        indexVolumeService.addVolumeToGroup(volume.getId(), indexVolumeGroup);

        final DocRef indexRef = indexStore.createDocument("Test Index 1");
        final IndexDoc index = indexStore.readDocument(indexRef);
        index.setVolumeGroupName(indexVolumeGroup);
        index.setPartitionBy(PartitionBy.MONTH);
        index.setPartitionSize(1);
        indexStore.writeDocument(index);

        final Node node = nodeInfo.getThisNode();

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
        final List<IndexShard> list = indexShardService.find(findIndexShardCriteria);

        assertThat(list.size()).isEqualTo(10);

        list.forEach(i -> System.out.println(i.toString()));

        IndexShard lastShard = null;
        for (final IndexShard indexShard : list) {
            if (lastShard != null) {
                if (lastShard.getPartition().equals(indexShard.getPartition())) {
                    // Compare ids
                    assertThat(indexShard.getId() < lastShard.getId()).isTrue();
                } else {
                    assertThat(indexShard.getPartition().compareTo(lastShard.getPartition()) < 0).isTrue();
                }
            }

            lastShard = indexShard;
        }
    }

    private void createShard(final IndexDoc index,
                             final Node node,
                             final String dateTime,
                             final int shardNo) {
        final long timeMs = DateUtil.parseNormalDateTimeString(dateTime);
        final IndexShardKey key = IndexShardKeyUtil.createTimeBasedKey(index, timeMs, shardNo);
        indexShardService.createIndexShard(key, node.getName());
    }
}
