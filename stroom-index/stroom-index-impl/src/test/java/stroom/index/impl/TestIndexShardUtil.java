package stroom.index.impl;

import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexVolume;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class TestIndexShardUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestIndexShardUtil.class);

    private PathCreator pathCreator;
    private Path tempDir;

    @BeforeEach
    void setUp(@TempDir final Path tempDir) {
        this.tempDir = tempDir;
        pathCreator = new SimplePathCreator(
                () -> tempDir.resolve("home"),
                () -> tempDir);
    }

    @Test
    void getIndexPath() throws IOException {
        final Path path = tempDir.resolve("idxVol");
        Files.createDirectories(path);
        IndexVolume indexVolume = new IndexVolume();
        indexVolume.setPath(path.toString());
        IndexShard indexShard = new IndexShard();
        indexShard.setId(123L);
        indexShard.setIndexUuid(String.valueOf(UUID.randomUUID()));
        indexShard.setPartition("partition1");
        indexShard.setVolume(indexVolume);

        final Path indexPath = IndexShardUtil.getIndexPath(indexShard, pathCreator);
        LOGGER.info("indexPath: {}", indexPath);

        Assertions.assertThat(indexPath.toString())
                .startsWith(path.toString());
    }

    @Test
    void getIndexPath_relative() throws IOException {
        final String relPathStr = "relIdxVol";
        final Path path = Path.of(pathCreator.makeAbsolute(pathCreator.replaceSystemProperties(relPathStr)));
        Files.createDirectories(path);

        IndexVolume indexVolume = new IndexVolume();
        indexVolume.setPath(relPathStr);
        IndexShard indexShard = new IndexShard();
        indexShard.setId(123L);
        indexShard.setIndexUuid(String.valueOf(UUID.randomUUID()));
        indexShard.setPartition("partition1");
        indexShard.setVolume(indexVolume);

        final Path indexPath = IndexShardUtil.getIndexPath(indexShard, pathCreator);
        LOGGER.info("indexPath: {}", indexPath);

        Assertions.assertThat(indexPath.toString())
                .startsWith(path.toString());
    }
}
