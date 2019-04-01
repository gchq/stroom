package stroom.index.impl.db;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import stroom.docref.DocRef;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndexShardDaoImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardDaoImplTest.class);

    private static MySQLContainer dbContainer = null;
    // = new MySQLContainer().withDatabaseName(Stroom.STROOM.getName());
    // = null;//

    private static IndexVolumeDao indexVolumeDao;
    private static IndexVolumeGroupDao indexVolumeGroupDao;
    private static IndexShardDao indexShardDao;

    @BeforeAll
    static void beforeAll() {
        LOGGER.info(() -> "Before All - Start Database");
        Optional.ofNullable(dbContainer).ifPresent(MySQLContainer::start);

        final Injector injector = Guice.createInjector(new IndexDbModule(), new TestModule(dbContainer));

        indexVolumeDao = injector.getInstance(IndexVolumeDao.class);
        indexVolumeGroupDao = injector.getInstance(IndexVolumeGroupDao.class);
        indexShardDao = injector.getInstance(IndexShardDao.class);
    }

    @Test
    void createAndGet() {
        // Given
        final String partitionName = "ALL";
        final DocRef index = new DocRef.Builder()
                .uuid(UUID.randomUUID().toString())
                .name(TestData.createIndexName())
                .type(IndexDoc.DOCUMENT_TYPE)
                .build();
        final String nodeName = TestData.createNodeName();
        final String path = TestData.createPath();
        final String volumeGroupName = TestData.createVolumeGroupName();
        final Long shardFrom = System.currentTimeMillis();
        final Long shardTo = shardFrom + 3600;

        // When
        final IndexVolume createdVolume = indexVolumeDao.create(nodeName, path);
        indexVolumeGroupDao.create(volumeGroupName);
        indexVolumeDao.addVolumeToGroup(createdVolume.getId(), volumeGroupName);
        final IndexShardKey indexShardKey = new IndexShardKey.Builder()
                .indexUuid(index.getUuid())
                .partition(partitionName)
                .shardNo(0)
                .partitionFromTime(shardFrom)
                .partitionToTime(shardTo)
                .build();
        final IndexShard createdIndexShard = indexShardDao.create(indexShardKey, volumeGroupName, nodeName, "1.0-test");
        final IndexShard byIdIndexShard = indexShardDao.loadById(createdIndexShard.getId());

        // Then
        assertThat(createdIndexShard).isNotNull();
        assertThat(byIdIndexShard).isNotNull();
    }

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
        indexVolumeGroupDao.create(volumeGroupName);
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
}
