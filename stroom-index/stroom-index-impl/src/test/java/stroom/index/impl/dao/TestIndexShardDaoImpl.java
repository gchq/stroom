/*
 * Copyright 2019 Crown Copyright
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

package stroom.index.impl.dao;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.db.IndexDbModule;
import stroom.index.shared.AllPartition;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardFields;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexDoc.PartitionBy;
import stroom.index.shared.Partition;
import stroom.index.shared.TimePartition;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.FieldIndex;
import stroom.util.io.ByteSizeUnit;
import stroom.util.shared.Clearable;
import stroom.util.shared.Range;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexShardDaoImpl {

    @Inject
    private IndexVolumeDao indexVolumeDao;
    @Inject
    private IndexVolumeGroupDao indexVolumeGroupDao;
    @Inject
    private IndexShardDao indexShardDao;
    @Inject
    private Set<Clearable> clearables;
    private Path tempDir;

    @BeforeEach
    void beforeEach(@TempDir final Path tempDir) {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());
        injector.injectMembers(this);
        clearables.forEach(Clearable::clear);
        this.tempDir = tempDir;
    }

//    @Test
//    void createAndGet() {
//        // Given
//        final String partitionName = "ALL";
//        final DocRef index = DocRef.builder()
//                .uuid(UUID.randomUUID().toString())
//                .name(TestData.createIndexName())
//                .type(IndexDoc.DOCUMENT_TYPE)
//                .build();
//        final String nodeName = TestData.createNodeName();
//        final String path = TestData.createPath();
//        final String volumeGroupName = TestData.createVolumeGroupName();
//        final Long shardFrom = System.currentTimeMillis();
//        final Long shardTo = shardFrom + 3600;
//
//        // When
//        final IndexVolume createdVolume = createVolume(nodeName, path);
//        createGroup(volumeGroupName);
//        indexVolumeDao.addVolumeToGroup(createdVolume.getId(), volumeGroupName);
//        final IndexShardKey indexShardKey = new IndexShardKey.Builder()
//                .indexUuid(index.getUuid())
//                .partition(partitionName)
//                .shardNo(0)
//                .partitionFromTime(shardFrom)
//                .partitionToTime(shardTo)
//                .build();
//        final IndexShard createdIndexShard = indexShardDao.create(
//        indexShardKey, volumeGroupName, nodeName, "1.0-test");
//        final IndexShard byIdIndexShard = indexShardDao.fetch(createdIndexShard.getId()).orElse(null);
//
//        // Then
//        assertThat(createdIndexShard).isNotNull();
//        assertThat(byIdIndexShard).isNotNull();
//
//        assertThat(createdIndexShard.getVolume()).isNotNull();
//        assertThat(byIdIndexShard.getVolume()).isNotNull();
//    }

    @Test
    void createAndUpdateShard() {
        // Given
        final DocRef index = DocRef.builder()
                .uuid(UUID.randomUUID().toString())
                .name(TestData.createIndexName())
                .type(LuceneIndexDoc.TYPE)
                .build();
        final String nodeName = TestData.createNodeName();

        final String volumeGroupName = TestData.createVolumeGroupName();
        final IndexVolumeGroup indexVolumeGroup = createGroup(volumeGroupName);

        final IndexVolume indexVolume = createVolume(
                nodeName, tempDir.resolve("my_vol1").toString(), indexVolumeGroup);

        // When
        createGroup(volumeGroupName);
        final IndexShardKey indexShardKey = IndexShardKey
                .builder()
                .indexUuid(index.getUuid())
                .partition(AllPartition.INSTANCE)
                .build();

        final IndexShard indexShard = indexShardDao.create(
                indexShardKey, indexVolume, nodeName, "1.0-test");

        Assertions.assertThat(indexShard)
                .isNotNull();
        Assertions.assertThat(indexShard.getDocumentCount())
                .isEqualTo(0);
        Assertions.assertThat(indexShard.getCommitDurationMs())
                .isNull();
        Assertions.assertThat(indexShard.getCommitMs())
                .isNull();

        final long nowMs = Instant.now().toEpochMilli();
        indexShardDao.update(
                indexShard.getId(),
                123,
                456L,
                nowMs,
                ByteSizeUnit.GIBIBYTE.longBytes(5));

        final IndexShard indexShard2 = indexShardDao.fetch(indexShard.getId()).get();

        Assertions.assertThat(indexShard2.getDocumentCount())
                .isEqualTo(123);
        Assertions.assertThat(indexShard2.getCommitDurationMs())
                .isEqualTo(456);
        Assertions.assertThat(indexShard2.getCommitMs())
                .isEqualTo(nowMs);
    }

    private IndexVolume createVolume(final String nodeName,
                                     final String path,
                                     final IndexVolumeGroup indexVolumeGroup) {
        final IndexVolume indexVolume = IndexVolume
                .builder()
                .nodeName(nodeName)
                .path(path)
                .indexVolumeGroupId(indexVolumeGroup.getId())
                .stampAudit("test")
                .build();
        return indexVolumeDao.create(indexVolume);
    }

    private IndexVolumeGroup createGroup(final String name) {
        final IndexVolumeGroup indexVolumeGroup = IndexVolumeGroup
                .builder()
                .name(name)
                .stampAudit("test")
                .build();
        return indexVolumeGroupDao.getOrCreate(indexVolumeGroup);
    }

    @Test
    void testSearch() {
        // Given
        final DocRef index = DocRef.builder()
                .uuid(UUID.randomUUID().toString())
                .name(TestData.createIndexName())
                .type(LuceneIndexDoc.TYPE)
                .build();
        final String nodeName = TestData.createNodeName();

        final String volumeGroupName = TestData.createVolumeGroupName();
        final IndexVolumeGroup indexVolumeGroup = createGroup(volumeGroupName);

        final IndexVolume indexVolume = createVolume(
                nodeName, tempDir.resolve("my_vol1").toString(), indexVolumeGroup);

        // When
        createGroup(volumeGroupName);
        final IndexShardKey indexShardKey = IndexShardKey
                .builder()
                .indexUuid(index.getUuid())
                .partition(AllPartition.INSTANCE)
                .build();

        indexShardDao.create(indexShardKey, indexVolume, nodeName, "1.0-test");
        indexShardDao.create(indexShardKey, indexVolume, nodeName, "1.0-test");

        final List<QueryField> fields = IndexShardFields.getFields();
        assertThat(fields.size()).isEqualTo(12);

        for (final QueryField field : fields) {
            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(field.getFldName());

            final AtomicInteger count = new AtomicInteger();
            indexShardDao.search(new ExpressionCriteria(), fieldIndex, values -> {
                count.incrementAndGet();
                assertThat(values.length).isEqualTo(1);
            });
            assertThat(count.get()).isEqualTo(2);
        }
    }

    @Test
    void testFind_partitionTimeRange_bothBounds() {
        // Mid to late March finds March's month shard, the year shard and the unpartitioned one
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, new Range<>(ms("2024-03-15"), ms("2024-03-31"))))
                .containsExactlyInAnyOrder("all", "2024", "2024-03");
    }

    @Test
    void testFind_partitionTimeRange_fromOnly() {
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, new Range<>(ms("2024-04-15"), null)))
                .containsExactlyInAnyOrder("all", "2024", "2024-05");
    }

    @Test
    void testFind_partitionTimeRange_toOnly() {
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, new Range<>(null, ms("2024-02-15"))))
                .containsExactlyInAnyOrder("all", "2024", "2024-01");
    }

    @Test
    void testFind_partitionTimeRange_partitionEndIsExclusive() {
        // January's partition ends at the start of February, so a range from then leaves it out
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, new Range<>(ms("2024-02-01"), ms("2024-02-15"))))
                .containsExactlyInAnyOrder("all", "2024");
    }

    @Test
    void testFind_noPartitionTimeRange() {
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, null))
                .containsExactlyInAnyOrder("all", "2024", "2024-01", "2024-03", "2024-05");
    }

    // Creates the shards of an index whose partitioning has changed: one written with No partition
    // (no partition times), one for the year 2024 and month shards for January, March and May 2024.
    private String createMixedShards() {
        final String indexUuid = UUID.randomUUID().toString();
        final String nodeName = TestData.createNodeName();
        final IndexVolumeGroup indexVolumeGroup = createGroup(TestData.createVolumeGroupName());
        final IndexVolume indexVolume = createVolume(
                nodeName, tempDir.resolve("my_vol1").toString(), indexVolumeGroup);
        createShard(indexUuid, AllPartition.INSTANCE, indexVolume, nodeName);
        createShard(indexUuid,
                new TimePartition(PartitionBy.YEAR, 1, ms("2024-01-01"), ms("2025-01-01"), "2024"),
                indexVolume,
                nodeName);
        createMonthShard(indexUuid, "2024-01", "2024-02", indexVolume, nodeName);
        createMonthShard(indexUuid, "2024-03", "2024-04", indexVolume, nodeName);
        createMonthShard(indexUuid, "2024-05", "2024-06", indexVolume, nodeName);
        return indexUuid;
    }

    private void createMonthShard(final String indexUuid,
                                  final String month,
                                  final String nextMonth,
                                  final IndexVolume indexVolume,
                                  final String nodeName) {
        final TimePartition partition = new TimePartition(
                PartitionBy.MONTH, 1, ms(month + "-01"), ms(nextMonth + "-01"), month);
        createShard(indexUuid, partition, indexVolume, nodeName);
    }

    private List<String> findPartitions(final String indexUuid, final Range<Long> partitionTimeRange) {
        final FindIndexShardCriteria criteria = FindIndexShardCriteria
                .builder()
                .partitionTimeRange(partitionTimeRange)
                .build();
        criteria.getIndexUuidSet().add(indexUuid);
        return indexShardDao.find(criteria)
                .getValues()
                .stream()
                .map(IndexShard::getPartition)
                .toList();
    }

    private static long ms(final String date) {
        return Instant.parse(date + "T00:00:00Z").toEpochMilli();
    }

    private void createShard(final String indexUuid,
                             final Partition partition,
                             final IndexVolume indexVolume,
                             final String nodeName) {
        final IndexShardKey indexShardKey = IndexShardKey
                .builder()
                .indexUuid(indexUuid)
                .partition(partition)
                .build();
        indexShardDao.create(indexShardKey, indexVolume, nodeName, "1.0-test");
    }
}
