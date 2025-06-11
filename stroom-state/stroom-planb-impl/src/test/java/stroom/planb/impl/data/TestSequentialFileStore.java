package stroom.planb.impl.data;

import stroom.planb.impl.db.StatePaths;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestSequentialFileStore {

    @Test
    void test() throws IOException {
        final Path rootDir = Files.createTempDirectory("root");
        try {
            final StatePaths statePaths = new StatePaths(rootDir);
            final StagingFileStore fileStore = new StagingFileStore(statePaths);
            assertThat(fileStore.getMinStoreId()).isEqualTo(-1);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(-1);

            int i = 0;
            addFile(rootDir, fileStore, i);

            assertThat(fileStore.getMinStoreId()).isEqualTo(0);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(0);

            for (; i < 9; i++) {
                addFile(rootDir, fileStore, i);
            }

            assertThat(fileStore.getMinStoreId()).isEqualTo(0);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(9);

            long currentId = fileStore.getMinStoreId();
            SequentialFile sequentialFile = fileStore.awaitNext(currentId);

            assertThat(fileStore.getMinStoreId()).isEqualTo(0);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(9);

            fileStore.delete(sequentialFile);

            assertThat(fileStore.getMinStoreId()).isEqualTo(1);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(9);

            for (; currentId < 10; currentId++) {
                assertThat(fileStore.getMaxStoreId()).isEqualTo(9);
                sequentialFile = fileStore.awaitNext(currentId);
                fileStore.delete(sequentialFile);
            }

            assertThat(fileStore.getMinStoreId()).isEqualTo(-1);
            assertThat(fileStore.getMaxStoreId()).isEqualTo(-1);

        } finally {
            FileUtil.deleteDir(rootDir);
        }
    }

    private void addFile(final Path rootDir, final StagingFileStore fileStore, final int i) throws IOException {
        final Path file = rootDir.resolve(i + ".txt");
        Files.writeString(file, "test");
        final String fileHash = FileHashUtil.hash(file);
        fileStore.add(new FileDescriptor(System.currentTimeMillis(), 1, fileHash), file);
    }
}
