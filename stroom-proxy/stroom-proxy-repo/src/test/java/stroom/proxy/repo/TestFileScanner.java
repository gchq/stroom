package stroom.proxy.repo;

import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.store.Entries;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileScanner {

    @Test
    void testScanner() throws Exception {
        final Path root = Files.createTempDirectory("proxy");

        final Path sourcePath = Files.createDirectories(root.resolve("old-store"));
        final SequentialFileStore sourceFileStore = new SequentialFileStore(() -> sourcePath);

        final Path targetPath1 = Files.createDirectories(root.resolve("new-store1"));
        final SequentialFileStore targetFileStore1 = new SequentialFileStore(() -> targetPath1);

        final Path targetPath2 = Files.createDirectories(root.resolve("new-store2"));
        final SequentialFileStore targetFileStore2 = new SequentialFileStore(() -> targetPath2);

        assertThat(FileUtil.countNested(sourcePath)).isEqualTo(3);
        assertThat(FileUtil.countNested(targetPath1)).isEqualTo(3);
        assertThat(FileUtil.countNested(targetPath2)).isEqualTo(3);

        final FileScanner fileScanner1 = new FileScanner(sourcePath, targetFileStore1);
        createStore(sourceFileStore);
        assertThat(FileUtil.countNested(sourcePath)).isEqualTo(204);
        assertThat(FileUtil.countNested(targetPath1)).isEqualTo(3);
        assertThat(FileUtil.countNested(targetPath2)).isEqualTo(3);
        fileScanner1.scan();
        assertThat(FileUtil.countNested(sourcePath)).isEqualTo(1);
        assertThat(FileUtil.countNested(targetPath1)).isEqualTo(204);
        assertThat(FileUtil.countNested(targetPath2)).isEqualTo(3);
        fileScanner1.scan();
        assertThat(FileUtil.countNested(sourcePath)).isEqualTo(1);
        assertThat(FileUtil.countNested(targetPath1)).isEqualTo(204);
        assertThat(FileUtil.countNested(targetPath2)).isEqualTo(3);

        final FileScanner fileScanner2 = new FileScanner(targetPath1, targetFileStore2);
        fileScanner2.scan();
        assertThat(FileUtil.countNested(sourcePath)).isEqualTo(1);
        assertThat(FileUtil.countNested(targetPath1)).isEqualTo(1);
        assertThat(FileUtil.countNested(targetPath2)).isEqualTo(204);

        createStore(sourceFileStore);
        assertThat(FileUtil.countNested(sourcePath)).isEqualTo(204);
        assertThat(FileUtil.countNested(targetPath1)).isEqualTo(1);
        assertThat(FileUtil.countNested(targetPath2)).isEqualTo(204);
        fileScanner1.scan();
        assertThat(FileUtil.countNested(sourcePath)).isEqualTo(1);
        assertThat(FileUtil.countNested(targetPath1)).isEqualTo(204);
        assertThat(FileUtil.countNested(targetPath2)).isEqualTo(204);
        fileScanner2.scan();
        assertThat(FileUtil.countNested(sourcePath)).isEqualTo(1);
        assertThat(FileUtil.countNested(targetPath1)).isEqualTo(1);
        assertThat(FileUtil.countNested(targetPath2)).isEqualTo(404);

        FileUtil.deleteDir(root);
    }

    private void createStore(final SequentialFileStore sequentialFileStore) throws Exception {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, "test");
        attributeMap.put(StandardHeaderArguments.TYPE, "test");
        final byte[] metaBytes = AttributeMapUtil.toByteArray(attributeMap);
        final byte[] dataBytes = "test".getBytes(StandardCharsets.UTF_8);

        for (int fileCount = 0; fileCount < 100; fileCount++) {
            try (final Entries entries = sequentialFileStore.getEntries(attributeMap)) {
                for (int entryCount = 0; entryCount < 10; entryCount++) {
                    final StroomZipEntry metaEntry = StroomZipEntry.createFromBaseName(
                            String.valueOf(entryCount),
                            StroomZipFileType.META);
                    try (final OutputStream outputStream = entries.addEntry(metaEntry.getFullName())) {
                        StreamUtil.streamToStream(new ByteArrayInputStream(metaBytes), outputStream);
                    }

                    final StroomZipEntry dataEntry = StroomZipEntry.createFromBaseName(
                            String.valueOf(entryCount),
                            StroomZipFileType.DATA);
                    try (final OutputStream outputStream = entries.addEntry(dataEntry.getFullName())) {
                        StreamUtil.streamToStream(new ByteArrayInputStream(dataBytes), outputStream);
                    }
                }
            }
        }
    }
}
