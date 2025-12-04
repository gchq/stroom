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
import stroom.test.common.MockMetrics;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestDirQueueTransfer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDirQueueTransfer.class);

    private Path baseDir;
    private Path intputDir;
    private Path sourceQueueDir;
    private Path destQueueDir;
    private MockMetrics metrics = new MockMetrics();
    private final QueueMonitors queueMonitors = new QueueMonitors(metrics);
    private final FileStores fileStores = new FileStores(metrics);
    private DirQueue sourceQueue;
    private DirQueue destQueue;

    @BeforeEach
    void setUp(@TempDir final Path baseDir) {
        this.baseDir = baseDir;
        this.intputDir = FileUtil.ensureDirExists(baseDir.resolve("input"));
        this.sourceQueueDir = FileUtil.ensureDirExists(baseDir.resolve("sourceQueue"));
        this.destQueueDir = FileUtil.ensureDirExists(baseDir.resolve("destQueue"));
    }

    @Test
    void test_success() {
        sourceQueue = new DirQueue(sourceQueueDir, queueMonitors, fileStores, 1, "source");
        destQueue = new DirQueue(destQueueDir, queueMonitors, fileStores, 2, "dest");

        final DirQueueTransfer dirQueueTransfer = new DirQueueTransfer(sourceQueue::next, destQueue::add);

        final Path sourceDir1 = createSourceDir(1);
        assertQueuePositions(0, 1, 0, 1);
        sourceQueue.add(sourceDir1);
        assertQueuePositions(1, 1, 0, 1);

        assertThat(intputDir)
                .isEmptyDirectory();
        assertThat(sourceQueueDir)
                .isNotEmptyDirectory();
        assertThat(destQueueDir)
                .isEmptyDirectory();

        dirQueueTransfer.run();

        assertQueuePositions(1, 2, 1, 1);

        dumpContents(sourceQueueDir);
        dumpContents(destQueueDir);

        assertThat(intputDir)
                .isEmptyDirectory();
        assertThat(sourceQueueDir)
                .isEmptyDirectory();
        assertThat(destQueueDir)
                .isNotEmptyDirectory();
    }

    @Test
    void test_destinationFailure() {
        sourceQueue = new DirQueue(sourceQueueDir, queueMonitors, fileStores, 1, "source");
        destQueue = new DirQueue(destQueueDir, queueMonitors, fileStores, 2, "dest");

        final DirQueueTransfer dirQueueTransfer = new DirQueueTransfer(
                sourceQueue::next,
                path -> {
                    throw new RuntimeException("Failed to consume " + path);
                });

        final Path sourceDir1 = createSourceDir(1);
        assertQueuePositions(0, 1, 0, 1);
        sourceQueue.add(sourceDir1);
        assertQueuePositions(1, 1, 0, 1);

        assertThat(intputDir)
                .isEmptyDirectory();
        assertThat(sourceQueueDir)
                .isNotEmptyDirectory();
        assertThat(destQueueDir)
                .isEmptyDirectory();

        try {
            dirQueueTransfer.run();
        } catch (final Exception e) {
            LOGGER.debug("Swallow error: {}", e.getMessage());
        }

        // It is assumed that if queue to queue transfer fails then we have a fundamental issue, e.g. disk full,
        // which will affect everything. The dir will be left on the source queue but the read pos will have
        // advanced. It will get retried on a reboot.

        assertQueuePositions(1, 2, 0, 1);

        dumpContents(sourceQueueDir);
        dumpContents(destQueueDir);

        assertThat(intputDir)
                .isEmptyDirectory();
        assertThat(sourceQueueDir)
                .isNotEmptyDirectory();
        assertThat(destQueueDir)
                .isEmptyDirectory();
    }

    private void assertQueuePositions(
            final long sourceWriteId,
            final long sourceReadId,
            final long destWriteId,
            final long destReadId) {

        LOGGER.debug("sourceQueue: {}, destQueue: {}", sourceQueue, destQueue);
        assertThat(sourceQueue.getWriteId())
                .isEqualTo(sourceWriteId);
        assertThat(sourceQueue.getReadId())
                .isEqualTo(sourceReadId);
        assertThat(destQueue.getWriteId())
                .isEqualTo(destWriteId);
        assertThat(destQueue.getReadId())
                .isEqualTo(destReadId);
    }

    private Path createSourceDir(final int num) {
        return createSourceDir(num, null);
    }

    private Path createSourceDir(final int num, final Map<String, String> attrs) {
        final Path sourceDir = intputDir.resolve("source_" + num);
        FileUtil.ensureDirExists(sourceDir);
        assertThat(sourceDir)
                .isDirectory()
                .exists();

        final FileGroup fileGroup = new FileGroup(sourceDir);
        fileGroup.items()
                .forEach(ThrowingConsumer.unchecked(FileUtil::touch));

        try {
            if (NullSafe.hasEntries(attrs)) {
                final Path meta = fileGroup.getMeta();
                final AttributeMap attributeMap = new AttributeMap(attrs);
                AttributeMapUtil.write(attributeMap, meta);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return sourceDir;
    }

    private void dumpContents(final Path path) {
        LOGGER.debug("Contents of {}\n{}",
                path,
                FileUtil.deepListContents(path, false)
                        .stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining("\n")));
    }
}
