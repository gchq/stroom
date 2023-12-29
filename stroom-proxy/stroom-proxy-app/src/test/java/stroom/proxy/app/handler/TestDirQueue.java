package stroom.proxy.app.handler;

import stroom.data.zip.CharsetConstants;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestDirQueue extends StroomUnitTest {

    @Test
    void test() {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final DirQueue dirQueue = new DirQueue(repoDir, new QueueMonitors(), new FileStores(), 1, "test");

        assertThat(FileUtil.count(repoDir)).isZero();

        addFile(dirQueue);
        addFile(dirQueue);

        // Re open.
        final DirQueue reopenFileStore = new DirQueue(repoDir, new QueueMonitors(), new FileStores(), 1, "test");

        try (final Dir dir = reopenFileStore.next()) {
            final FileGroup fileGroup = new FileGroup(dir.getPath());
            assertThat(Files.exists(fileGroup.getZip())).isTrue();
            FileUtil.deleteDir(dir.getPath());
        }
        try (final Dir dir = reopenFileStore.next()) {
            final FileGroup fileGroup = new FileGroup(dir.getPath());
            assertThat(Files.exists(fileGroup.getZip())).isTrue();
            FileUtil.deleteDir(dir.getPath());
        }

        assertThat(FileUtil.count(repoDir)).isZero();
        FileUtil.delete(repoDir);
    }

    @Test
    void testPerformance() {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final DirQueue dirQueue = new DirQueue(repoDir, new QueueMonitors(), new FileStores(), 1, "test");

        for (int i = 0; i < 100000; i++) {
            addFile(dirQueue);
        }

        long maxId = NumericFileNameUtil.getMaxId(repoDir);
        assertThat(maxId).isEqualTo(100000);

        for (int i = 0; i < 5; i++) {
            addFile(dirQueue);
        }

        maxId = NumericFileNameUtil.getMaxId(repoDir);
        assertThat(maxId).isEqualTo(100005);
    }

    private void addFile(final DirQueue dirQueue) {
        try {
            // Create a temp dir.
            final Path tempDir = Files.createTempDirectory("test");
            final FileGroup fileGroup = new FileGroup(tempDir);

            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.addFeedAndType(attributeMap, "test", null);

            // Write a meta file.
            final Path metaFile = fileGroup.getMeta();
            try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(metaFile))) {
                AttributeMapUtil.write(attributeMap, outputStream);
            }

            // Write a zip file.
            final Path zipFile = fileGroup.getZip();
            try (final ZipOutputStream zipOutputStream =
                    new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
                zipOutputStream.putNextEntry(new ZipEntry("file"));
                zipOutputStream.write("SOME_DATA".getBytes(CharsetConstants.DEFAULT_CHARSET));
            }

            // Transfer.
            assertThat(Files.isDirectory(tempDir)).isTrue();
            dirQueue.add(tempDir);
            assertThat(Files.isDirectory(tempDir)).isFalse();

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
