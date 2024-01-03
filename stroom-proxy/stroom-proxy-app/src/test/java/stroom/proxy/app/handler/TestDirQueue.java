package stroom.proxy.app.handler;

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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestDirQueue extends StroomUnitTest {

    private static final long MAX = 100;

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

        for (int i = 0; i < MAX; i++) {
            addTempDir(dirQueue);
        }

        long maxId = DirUtil.getMaxDirId(repoDir);
        assertThat(maxId).isEqualTo(MAX);

        for (int i = 0; i < 5; i++) {
            addTempDir(dirQueue);
        }

        maxId = DirUtil.getMaxDirId(repoDir);
        assertThat(maxId).isEqualTo(MAX + 5);
    }

    @Test
    void testMultiThreadedPerformance() {
        final int threads = 10;
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final DirQueue dirQueue = new DirQueue(repoDir, new QueueMonitors(), new FileStores(), 1, "test");

        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final AtomicInteger produced = new AtomicInteger();
            final AtomicInteger consumed = new AtomicInteger();

            // Producer.
            final CompletableFuture<?>[] producers = new CompletableFuture[threads];
            for (int i = 0; i < threads; i++) {
                final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    boolean run = true;
                    while (run) {
                        final int id = produced.incrementAndGet();
                        if (id > MAX) {
                            run = false;
                        } else {
                            addTempDir(dirQueue);
                        }
                    }
                }, executorService);
                producers[i] = future;
            }

            final CompletableFuture<?>[] consumers = new CompletableFuture[threads];
            for (int i = 0; i < threads; i++) {
                final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    while (consumed.get() < MAX) {
                        final Optional<Dir> optional = dirQueue.next(1, TimeUnit.SECONDS);
                        if (optional.isPresent()) {
                            try (final Dir dir = optional.get()) {
                                consumed.incrementAndGet();
                                FileUtil.deleteDir(dir.getPath());
                            }
                        }
                    }
                }, executorService);
                consumers[i] = future;
            }

            CompletableFuture.allOf(producers).join();
            CompletableFuture.allOf(consumers).join();

            assertThat(consumed.get()).isEqualTo(MAX);
            assertThat(FileUtil.count(repoDir)).isZero();

        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void testPerformanceWithData() {
        final Path repoDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final DirQueue dirQueue = new DirQueue(repoDir, new QueueMonitors(), new FileStores(), 1, "test");

        for (int i = 0; i < MAX; i++) {
            addFile(dirQueue);
        }

        long maxId = DirUtil.getMaxDirId(repoDir);
        assertThat(maxId).isEqualTo(MAX);

        for (int i = 0; i < 5; i++) {
            addFile(dirQueue);
        }

        maxId = DirUtil.getMaxDirId(repoDir);
        assertThat(maxId).isEqualTo(MAX + 5);
    }

    private void addTempDir(final DirQueue dirQueue) {
        try {
            dirQueue.add(Files.createTempDirectory("test"));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
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
            try (final ZipWriter zipWriter = new ZipWriter(zipFile, LocalByteBuffer.get())) {
                zipWriter.writeString("file", "SOME_DATA");
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
