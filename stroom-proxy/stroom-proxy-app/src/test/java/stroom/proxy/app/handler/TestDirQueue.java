/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.PathWithAttributes;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;

class TestDirQueue extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDirQueue.class);

    private static final long MAX = 10_000;

    @Test
    void test() {
        final Path dataDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final DirQueue dirQueue = new DirQueue(dataDir,
                new QueueMonitors(getMetrics()),
                new FileStores(getMetrics()),
                1,
                "test");

        assertThat(FileUtil.count(dataDir)).isZero();

        addFile(dirQueue);
        addFile(dirQueue);

        // Re open.
        final DirQueue reopenFileStore = new DirQueue(dataDir,
                new QueueMonitors(getMetrics()),
                new FileStores(getMetrics()),
                1,
                "test");

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

        assertThat(FileUtil.count(dataDir)).isZero();
        FileUtil.delete(dataDir);
    }

    private Path createIncompletePath(final Path rootDir, final long id) {
        final Path path = DirUtil.createPath(rootDir, id);
        FileUtil.ensureDirExists(path.getParent());
        return path;
    }

    private Path createDirWithContent(final Path rootDir, final long id) {
        final Path path = DirUtil.createPath(rootDir, id);
        FileUtil.ensureDirExists(path);
        final Path file = path.resolve("file_" + id + ".txt");
        try {
            Files.writeString(file, String.valueOf(id), StandardOpenOption.CREATE);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return path;
    }

    private Path createDirWithNoContent(final Path rootDir, final long id) {
        final Path path = DirUtil.createPath(rootDir, id);
        FileUtil.ensureDirExists(path);
        return path;
    }

    @Test
    void test2(@TempDir Path rootDir) {
        final Path path101 = createDirWithContent(rootDir, 101L);
        final Path path123 = createDirWithContent(rootDir, 123L);
        final Path path999 = createDirWithContent(rootDir, 999L);

        final Snapshot snapshot = DirectorySnapshot.of(rootDir);
        LOGGER.info("snapshot of {}\n{}", rootDir, snapshot);

        final DirQueue dirQueue = new DirQueue(
                rootDir,
                new QueueMonitors(getMetrics()),
                new FileStores(getMetrics()),
                1,
                "test");

        assertThat(dirQueue.getReadId())
                .isEqualTo(101);
        assertThat(dirQueue.getWriteId())
                .isEqualTo(999);

        try (final Dir next = dirQueue.next()) {
            assertThat(next.getPath())
                    .isEqualTo(path101);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next()) {
            assertThat(next.getPath())
                    .isEqualTo(path123);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next()) {
            assertThat(next.getPath())
                    .isEqualTo(path999);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next(10, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next)
                    .isNull();
        }

        assertThat(rootDir)
                .isEmptyDirectory();
    }

    @Test
    void test3(@TempDir Path rootDir) {
        final Path path1101 = createIncompletePath(rootDir, 1_101L);
        final Path path1102 = createDirWithContent(rootDir, 1_102L);
        final Path path1103 = createIncompletePath(rootDir, 1_103L);
        final Path path2104 = createIncompletePath(rootDir, 2_104L);
        final Path path3105 = createDirWithContent(rootDir, 3_105L);
        final Path path4106 = createIncompletePath(rootDir, 4_106L);

        LOGGER.info("snapshot of {}\n{}", rootDir, DirectorySnapshot.of(rootDir));

        final DirQueue dirQueue = new DirQueue(
                rootDir,
                new QueueMonitors(getMetrics()),
                new FileStores(getMetrics()),
                1,
                "test");

        assertThat(dirQueue.getReadId())
                .isEqualTo(1102);
        assertThat(dirQueue.getWriteId())
                .isEqualTo(4999);

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next.getPath())
                    .isEqualTo(path1102);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next.getPath())
                    .isEqualTo(path3105);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next)
                    .isNull();
        }

        LOGGER.info("snapshot of {}\n{}", rootDir, DirectorySnapshot.of(rootDir));

        // All the incomplete branches are cleaned out
        assertThat(rootDir)
                .isEmptyDirectory();
    }

    @Test
    void test4(@TempDir Path rootDir) {
        final Path path1101 = createIncompletePath(rootDir, 1_101L);
        final Path path1102 = createDirWithContent(rootDir, 1_102L);
        final Path path1103 = createIncompletePath(rootDir, 1_103L);
        final Path path2104 = createIncompletePath(rootDir, 2_000_104L);
        final Path path3105 = createDirWithContent(rootDir, 2_032_001L);
        final Path path4106 = createIncompletePath(rootDir, 2_103_106L);

        Snapshot snapshot = DirectorySnapshot.of(rootDir);
        LOGGER.info("snapshot of {}\n{}", rootDir, snapshot);

        final DirQueue dirQueue = new DirQueue(
                rootDir,
                new QueueMonitors(getMetrics()),
                new FileStores(getMetrics()),
                1,
                "test");

        assertThat(dirQueue.getReadId())
                .isEqualTo(1102);
        assertThat(dirQueue.getWriteId())
                .isEqualTo(2_103_999L);

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next.getPath())
                    .isEqualTo(path1102);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next.getPath())
                    .isEqualTo(path3105);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next)
                    .isNull();
        }

        snapshot = DirectorySnapshot.of(rootDir);
        LOGGER.info("snapshot of {}\n{}", rootDir, snapshot);

        // All the incomplete branches are cleaned out
        assertThat(rootDir)
                .isEmptyDirectory();
    }

    @Test
    void test5(@TempDir Path rootDir) {
        final Path path2000104 = createDirWithContent(rootDir, 2_000_104);
        final Path path2000900 = createDirWithContent(rootDir, 2_000_900);
        final Path path2103100 = createIncompletePath(rootDir, 2_103_100);
        final Path path2204100 = createIncompletePath(rootDir, 2_204_100);
        final Path path2305100 = createIncompletePath(rootDir, 2_305_100);
        final Path path2406100 = createDirWithContent(rootDir, 2_406_100);
        final Path path2507100 = createIncompletePath(rootDir, 2_507_100);
        final Path path2608100 = createDirWithContent(rootDir, 2_608_100);
        final Path path123123123 = createIncompletePath(rootDir, 123_123_123);

        Snapshot snapshot = DirectorySnapshot.of(rootDir);
        LOGGER.info("snapshot of {}\n{}", rootDir, snapshot);

        final DirQueue dirQueue = new DirQueue(
                rootDir,
                new QueueMonitors(getMetrics()),
                new FileStores(getMetrics()),
                1,
                "test");

        assertThat(dirQueue.getReadId())
                .isEqualTo(2_000_104);
        assertThat(dirQueue.getWriteId())
                .isEqualTo(123_123_999); // Incomplete path

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next.getPath())
                    .isEqualTo(path2000104);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next.getPath())
                    .isEqualTo(path2000900);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next.getPath())
                    .isEqualTo(path2406100);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next.getPath())
                    .isEqualTo(path2608100);
            consumeDir(next);
        }

        try (final Dir next = dirQueue.next(0L, TimeUnit.MILLISECONDS).orElse(null)) {
            assertThat(next)
                    .isNull();
        }

        snapshot = DirectorySnapshot.of(rootDir);
        LOGGER.info("snapshot of {}\n{}", rootDir, snapshot);

        // All the incomplete branches are cleaned out
        assertThat(rootDir)
                .isEmptyDirectory();
    }

    private void consumeDir(final Dir dir) {
        final Path path = dir.getPath();
        // Simulate moving it somewhere else
        FileUtil.deleteDir(path);
    }

    @Test
    void testPerformance() {
        final Path dataDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final DirQueue dirQueue = new DirQueue(
                dataDir,
                new QueueMonitors(getMetrics()),
                new FileStores(getMetrics()),
                1,
                "test");

        for (int i = 0; i < MAX; i++) {
            addTempDir(dirQueue);
        }

        long maxId = DirUtil.getMaxDirId(dataDir);
        assertThat(maxId).isEqualTo(MAX);

        for (int i = 0; i < 5; i++) {
            addTempDir(dirQueue);
        }

        maxId = DirUtil.getMaxDirId(dataDir);
        assertThat(maxId).isEqualTo(MAX + 5);
    }

    @Test
    void testMultiThreadedPerformance() {
        final int threads = 10;

        final Path dataDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        // Multiple rounds to simulate a proxy rebooting and picking up from where it left off.
        for (int round = 1; round <= 3; round++) {
            final DirQueue dirQueue = new DirQueue(
                    dataDir,
                    new QueueMonitors(getMetrics()),
                    new FileStores(getMetrics()),
                    1,
                    "test");
            LOGGER.info("round: {}, readId: {}, writeId: {}", round, dirQueue.getReadId(), dirQueue.getWriteId());

            final ExecutorService executorService = Executors.newCachedThreadPool();
            try {
                final AtomicInteger produced = new AtomicInteger(0);
                final AtomicInteger consumed = new AtomicInteger(0);
                final LongAdder addCount = new LongAdder();
                final LongAdder delCount = new LongAdder();
                // Make sure all threads pile in at the same time for max contention
                final CountDownLatch startLatch = new CountDownLatch(threads * 2);

                // Producer.
                final CompletableFuture<?>[] producers = new CompletableFuture[threads];
                for (int i = 0; i < threads; i++) {
                    final int finalI = i;
                    final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        startLatch.countDown();
                        ThreadUtil.await(startLatch, 20, TimeUnit.SECONDS);
                        while (true) {
                            if (finalI == 3) {
                                // One thread is a slower than the others, just to add some variety
                                ThreadUtil.sleepIgnoringInterrupts(50);
                            }
                            final int produceId = produced.updateAndGet(currVal -> {
                                if (currVal != -1 && currVal < MAX) {
                                    return currVal + 1;
                                } else {
                                    return -1;
                                }
                            });
                            if (produceId == -1) {
                                break;
                            } else {
                                final Path path = addTempDir(dirQueue);
                                LOGGER.trace("produceId: {}, path: {}", produceId, path);
                                addCount.increment();
                            }
                        }
                    }, executorService);
                    producers[i] = future;
                }
                // Consumers leave 2 behind, so we have queue items on disk when we start a new round
                final long consumeCount = MAX - 2;

                final CompletableFuture<?>[] consumers = new CompletableFuture[threads];
                for (int i = 0; i < threads; i++) {
                    final int finalI = i;
                    final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        startLatch.countDown();
                        ThreadUtil.await(startLatch, 20, TimeUnit.SECONDS);
                        while (true) {
                            if (finalI == 3) {
                                // One thread is a slower than the others, just to add some variety
                                ThreadUtil.sleepIgnoringInterrupts(50);
                            }
                            final long consumeId = consumed.updateAndGet(currVal -> {
                                if (currVal != -1 && currVal < consumeCount) {
                                    return currVal + 1;
                                } else {
                                    return -1;
                                }
                            });
                            if (consumeId == -1) {
                                break;
                            } else {
                                final Optional<Dir> optional = dirQueue.next(10, TimeUnit.SECONDS);
                                if (optional.isPresent()) {
                                    try (final Dir dir = optional.get()) {
                                        FileUtil.deleteDir(dir.getPath());
                                        LOGGER.trace("consumeId: {}, path: {}", consumeId, dir.getPath());
                                        delCount.increment();
                                    }
                                }
                            }
                        }
                    }, executorService);
                    consumers[i] = future;
                }

                CompletableFuture.allOf(producers).join();
                CompletableFuture.allOf(consumers).join();

                LOGGER.info("finished, snapshot of dataDir {}:\n{}", dataDir, DirectorySnapshot.of(dataDir));

                assertThat(produced)
                        .hasValue(-1);
                assertThat(consumed)
                        .hasValue(-1);
                assertThat(addCount)
                        .hasValue(MAX);
                assertThat(delCount)
                        .hasValue(consumeCount);

                // Each round leaves two un-consumed
                assertThat(FileUtil.deepListContents(dataDir, false)
                        .stream()
                        .map(PathWithAttributes::path)
                        .filter(Files::isDirectory)
                        .filter(DirUtil::isValidLeafPath)
                        .count())
                        .isEqualTo(2L * round);

                LOGGER.info("readId: {}, writeId: {}", dirQueue.getReadId(), dirQueue.getWriteId());
                assertThat(dirQueue.getWriteId())
                        .isEqualTo(MAX * round);
                assertThat(dirQueue.getReadId())
                        .isEqualTo(((MAX - 2) * round) + 1);

            } finally {
                executorService.shutdown();
            }
        }
    }

    @Test
    void testPerformanceWithData() {
        final Path dataDir = FileUtil.createTempDirectory("stroom").resolve("repo1");
        final DirQueue dirQueue = new DirQueue(
                dataDir,
                new QueueMonitors(getMetrics()),
                new FileStores(getMetrics()),
                1,
                "test");

        for (int i = 0; i < MAX; i++) {
            addFile(dirQueue);
        }

        long maxId = DirUtil.getMaxDirId(dataDir);
        assertThat(maxId).isEqualTo(MAX);

        for (int i = 0; i < 5; i++) {
            addFile(dirQueue);
        }

        maxId = DirUtil.getMaxDirId(dataDir);
        assertThat(maxId).isEqualTo(MAX + 5);
    }

    @Test
    void testPruneEmptyParts(@TempDir Path rootDir) {
        final Path path1101 = createIncompletePath(rootDir, 1_101L);
        final Path path1102 = createDirWithContent(rootDir, 1_102L);
        final Path path1103 = createIncompletePath(rootDir, 1_103L);
        final Path path2000104 = createIncompletePath(rootDir, 2_000_104L);
        final Path path2000105 = createIncompletePath(rootDir, 2_000_105L);
        final Path path2032001 = createDirWithContent(rootDir, 2_032_001L);
        final Path path2103106 = createIncompletePath(rootDir, 2_103_106L);
        final Path path2103107 = createDirWithNoContent(rootDir, 2_103_107L);
        final Path path5123103106 = createIncompletePath(rootDir, 5_123_103_106L);

        LOGGER.info("snapshot of {}\n{}", rootDir, DirectorySnapshot.of(rootDir));

        final DirQueue dirQueue = new DirQueue(
                rootDir,
                new QueueMonitors(getMetrics()),
                new FileStores(getMetrics()),
                1,
                "test");

        final Set<Path> allDeletedPaths = new HashSet<>();

        assertThat(dirQueue.pruneEmptyParts(path1101, allDeletedPaths))
                .isEmpty();
        assertThat(dirQueue.pruneEmptyParts(path1102, allDeletedPaths))
                .isEmpty();
        assertThat(dirQueue.pruneEmptyParts(path1103, allDeletedPaths))
                .isEmpty();
        assertThat(dirQueue.pruneEmptyParts(path2000104, allDeletedPaths))
                .containsExactlyInAnyOrder(rootDir.resolve("2/002/000"));
        assertThat(dirQueue.pruneEmptyParts(path2000105, allDeletedPaths))
                .isEmpty(); // Parent already deleted
        assertThat(dirQueue.pruneEmptyParts(path2032001, allDeletedPaths))
                .isEmpty();

        assertThat(dirQueue.pruneEmptyParts(path2103106, allDeletedPaths))
                .isEmpty();
        assertThat(dirQueue.pruneEmptyParts(path2103107, allDeletedPaths))
                .containsExactlyInAnyOrder(
                        rootDir.resolve("2/002/103/002103107"),
                        rootDir.resolve("2/002/103"));

        assertThat(dirQueue.pruneEmptyParts(path5123103106, allDeletedPaths))
                .containsExactlyInAnyOrder(
                        rootDir.resolve("3/005/123/103"),
                        rootDir.resolve("3/005/123"),
                        rootDir.resolve("3/005"),
                        rootDir.resolve("3"));

        LOGGER.info("snapshot of {}\n{}", rootDir, DirectorySnapshot.of(rootDir));
    }

    private Path addTempDir(final DirQueue dirQueue) {
        try {
            final Path path = Files.createTempDirectory("test");
            dirQueue.add(path);
            return path;
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
