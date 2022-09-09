package stroom.index.impl.db;

import stroom.docref.DocRef;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.AuditUtil;
import stroom.util.io.ByteSizeUnit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

class TestIndexShardDaoImpl {

    private static IndexVolumeDao indexVolumeDao;
    private static IndexVolumeGroupDao indexVolumeGroupDao;
    private static IndexShardDao indexShardDao;
    private static Path tempDir;

    @BeforeAll
    static void beforeAll(@TempDir final Path tempDir) {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());

        indexVolumeDao = injector.getInstance(IndexVolumeDao.class);
        indexVolumeGroupDao = injector.getInstance(IndexVolumeGroupDao.class);
        indexShardDao = injector.getInstance(IndexShardDao.class);
        TestIndexShardDaoImpl.tempDir = tempDir;
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
        final String partitionName = "ALL";
        final DocRef index = DocRef.builder()
                .uuid(UUID.randomUUID().toString())
                .name(TestData.createIndexName())
                .type(IndexDoc.DOCUMENT_TYPE)
                .build();
        final Long shardFrom = System.currentTimeMillis();
        final Long shardTo = shardFrom + 3600;
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
                .partition(partitionName)
                .shardNo(0)
                .partitionFromTime(shardFrom)
                .partitionToTime(shardTo)
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
        final IndexVolume indexVolume = new IndexVolume();
        indexVolume.setNodeName(nodeName);
        indexVolume.setPath(path);
        indexVolume.setIndexVolumeGroupId(indexVolumeGroup.getId());
        AuditUtil.stamp("test", indexVolume);
        return indexVolumeDao.create(indexVolume);
    }

    private IndexVolumeGroup createGroup(final String name) {
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        indexVolumeGroup.setName(name);
        AuditUtil.stamp("test", indexVolumeGroup);
        return indexVolumeGroupDao.getOrCreate(indexVolumeGroup);
    }
}
