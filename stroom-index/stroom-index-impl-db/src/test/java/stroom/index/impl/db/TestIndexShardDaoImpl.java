package stroom.index.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import stroom.docref.DocRef;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.AuditUtil;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TestIndexShardDaoImpl {
    private static IndexVolumeDao indexVolumeDao;
    private static IndexVolumeGroupDao indexVolumeGroupDao;
    private static IndexShardDao indexShardDao;

    @BeforeAll
    static void beforeAll() {
        final Injector injector = Guice.createInjector(new IndexDbModule(), new TestModule());

        indexVolumeDao = injector.getInstance(IndexVolumeDao.class);
        indexVolumeGroupDao = injector.getInstance(IndexVolumeGroupDao.class);
        indexShardDao = injector.getInstance(IndexShardDao.class);
    }

//    @Test
//    void createAndGet() {
//        // Given
//        final String partitionName = "ALL";
//        final DocRef index = new DocRef.Builder()
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
//        final IndexShard createdIndexShard = indexShardDao.create(indexShardKey, volumeGroupName, nodeName, "1.0-test");
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
    void createShardEmptyGroup() {
        // Given
        final String partitionName = "ALL";
        final DocRef index = new DocRef.Builder()
                .uuid(UUID.randomUUID().toString())
                .name(TestData.createIndexName())
                .type(IndexDoc.DOCUMENT_TYPE)
                .build();
        final Long shardFrom = System.currentTimeMillis();
        final Long shardTo = shardFrom + 3600;
        final String nodeName = TestData.createNodeName();
        final String volumeGroupName = TestData.createVolumeGroupName();

        // When
        createGroup(volumeGroupName);
        final IndexShardKey indexShardKey = new IndexShardKey.Builder()
                .indexUuid(index.getUuid())
                .partition(partitionName)
                .shardNo(0)
                .partitionFromTime(shardFrom)
                .partitionToTime(shardTo)
                .build();

        // Then
        assertThrows(IndexException.class,
                () -> indexShardDao.create(indexShardKey, volumeGroupName, nodeName, "1.0-test"));

    }

    @Test
    void createShardNonExistentGroup() {
        // Given
        final String partitionName = "ALL";
        final DocRef index = new DocRef.Builder()
                .uuid(UUID.randomUUID().toString())
                .name(TestData.createIndexName())
                .type(IndexDoc.DOCUMENT_TYPE)
                .build();
        final Long shardFrom = System.currentTimeMillis();
        final Long shardTo = shardFrom + 3600;
        final String nodeName = TestData.createNodeName();
        final String volumeGroupName = TestData.createVolumeGroupName();

        // When
        final IndexShardKey indexShardKey = new IndexShardKey.Builder()
                .indexUuid(index.getUuid())
                .partition(partitionName)
                .shardNo(0)
                .partitionFromTime(shardFrom)
                .partitionToTime(shardTo)
                .build();

        // Then
        assertThrows(IndexException.class,
                () -> indexShardDao.create(indexShardKey, volumeGroupName, nodeName, "1.0-test"));
    }

    private IndexVolume createVolume(final String nodeName, final String path) {
        final IndexVolume indexVolume = new IndexVolume();
        indexVolume.setNodeName(nodeName);
        indexVolume.setPath(path);
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
