/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stage.splitzip;

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.local.LocalFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Split output must be staged under the proxy's <em>configured</em> temp directory,
 * not {@code java.io.tmpdir} directly.
 * <p>
 * Temp is the right home for this data - transient, deleted when the split completes,
 * and cleared at startup - so the fix is not to avoid temp but to resolve it through
 * {@code TempDirProvider} so it honours {@code path.temp}. Staging in the raw system
 * temp directory put it outside the proxy's configured paths, where nothing cleaned
 * up after a hard kill and no operator would think to look.
 * </p>
 */
class TestSplitZipTempRoot extends StroomUnitTest {

    @Test
    void testSplitOutputIsStagedUnderTheConfiguredTempRoot() throws Exception {
        final Path base = getCurrentTestDir();
        final Path tempRoot = base.resolve("pipeline-tmp");

        final LocalFileStore inputStore = new LocalFileStore("splitZipIn", base.resolve("in"), "w1");
        final LocalFileStore outputStore = new LocalFileStore("splitStore", base.resolve("out"), "w2");
        final LocalFileGroupQueue outputQueue =
                new LocalFileGroupQueue("preAggregateInput", base.resolve("q"));

        final Path[] observed = new Path[1];
        final SplitZipStageProcessor processor = new SplitZipStageProcessor(
                new FileStoreRegistry().register(inputStore).register(outputStore),
                outputStore,
                outputQueue,
                "test-node",
                (sourceDir, outputParentDir) -> {
                    observed[0] = outputParentDir;
                    writeGroupInto(outputParentDir.resolve("feed-a"));
                },
                tempRoot);

        processor.process(item(writeGroup(inputStore)));

        assertThat(observed[0])
                .as("split staging directory")
                .isNotNull()
                .startsWithRaw(tempRoot);
        assertThat(observed[0])
                .as("must not be under the system temp directory")
                .doesNotExist();  // cleaned up in the finally block
        assertThat(processor.getTempRoot()).isEqualTo(tempRoot.toAbsolutePath().normalize());
    }

    @Test
    void testStaleStagingDirectoriesAreClearedAtStartup() throws Exception {
        final Path base = getCurrentTestDir();
        final Path tempRoot = base.resolve("pipeline-tmp");

        // Simulate what a hard kill leaves behind - the finally block in process()
        // only covers an orderly failure.
        Files.createDirectories(tempRoot.resolve("split-zip-123/feed-a"));
        Files.writeString(tempRoot.resolve("split-zip-123/feed-a/proxy.zip"), "orphan");
        Files.createDirectories(tempRoot.resolve("split-zip-456"));
        assertThat(childCount(tempRoot)).isEqualTo(2);

        newProcessor(base, tempRoot);

        assertThat(tempRoot).exists();
        assertThat(childCount(tempRoot))
                .as("stale staging directories are cleared when the stage starts")
                .isZero();
    }

    @Test
    void testTempRootIsCreatedIfMissing() throws Exception {
        final Path base = getCurrentTestDir();
        final Path tempRoot = base.resolve("does/not/exist/yet");

        newProcessor(base, tempRoot);

        assertThat(tempRoot).exists().isDirectory();
    }

    @Test
    void testStagingIsCleanedUpWhenSplittingFails() throws Exception {
        final Path base = getCurrentTestDir();
        final Path tempRoot = base.resolve("pipeline-tmp");

        final LocalFileStore inputStore = new LocalFileStore("splitZipIn", base.resolve("in"), "w1");
        final LocalFileStore outputStore = new LocalFileStore("splitStore", base.resolve("out"), "w2");
        final LocalFileGroupQueue outputQueue =
                new LocalFileGroupQueue("preAggregateInput", base.resolve("q"));

        final SplitZipStageProcessor processor = new SplitZipStageProcessor(
                new FileStoreRegistry().register(inputStore).register(outputStore),
                outputStore,
                outputQueue,
                "test-node",
                (sourceDir, outputParentDir) -> {
                    writeGroupInto(outputParentDir.resolve("feed-a"));
                    throw new IOException("split failed half way");
                },
                tempRoot);

        assertThatThrownBy(() -> processor.process(item(writeGroup(inputStore))))
                .isInstanceOf(IOException.class);

        assertThat(childCount(tempRoot))
                .as("a failed split leaves nothing staged")
                .isZero();
    }

    private SplitZipStageProcessor newProcessor(final Path base, final Path tempRoot) throws IOException {
        final LocalFileStore inputStore = new LocalFileStore("splitZipIn", base.resolve("in"), "w1");
        final LocalFileStore outputStore = new LocalFileStore("splitStore", base.resolve("out"), "w2");
        return new SplitZipStageProcessor(
                new FileStoreRegistry().register(inputStore).register(outputStore),
                outputStore,
                new LocalFileGroupQueue("preAggregateInput", base.resolve("q")),
                "test-node",
                (sourceDir, outputParentDir) -> {
                },
                tempRoot);
    }

    private static long childCount(final Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (final Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    private static FileStoreLocation writeGroup(final LocalFileStore store) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            writeGroupInto(write.getPath());
            return write.commit();
        }
    }

    private static void writeGroupInto(final Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("proxy.meta"), "Feed:TEST\n");
        Files.writeString(dir.resolve("proxy.zip"), "zip");
        Files.writeString(dir.resolve("proxy.entries"), "TEST,RAW_EVENTS,1\n");
    }

    private static FileGroupQueueItem item(final FileStoreLocation location) {
        final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                "splitZipInput", "fg-1", location, "receive", "test-node", null, Map.of());

        return new FileGroupQueueItem() {
            @Override
            public String getId() {
                return "item-1";
            }

            @Override
            public FileGroupQueueMessage getMessage() {
                return message;
            }

            @Override
            public Map<String, String> getMetadata() {
                return Map.of();
            }

            @Override
            public void acknowledge() {
            }

            @Override
            public void fail(final Throwable error) {
            }

            @Override
            public void close() {
            }
        };
    }
}
