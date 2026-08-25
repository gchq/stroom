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

package stroom.proxy.app.pipeline.stage.receive;

import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.store.local.LocalFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Receiving must not leak a file handle per received group.
 * <p>
 * {@code requiresSplitting} inspects {@code proxy.entries} with
 * {@link Files#lines}, which holds the file open until the stream is closed. It
 * was not closed. Every receive leaked one descriptor, released only if and when
 * the garbage collector happened to reclaim the channel, so a proxy under
 * sustained load would eventually fail with "Too many open files" somewhere
 * completely unrelated to the receive path.
 * </p>
 * <p>
 * The check counts descriptors still pointing at a {@code proxy.entries} file
 * after a batch of receives. That is a leak <em>detector</em>: a garbage
 * collection during the batch could hide a real leak, so this test can pass
 * spuriously but never fail spuriously. Verified against the unfixed code, where
 * it fails.
 * </p>
 */
@EnabledOnOs(OS.LINUX)
class TestReceiveStagePublisherFileHandles extends StroomUnitTest {

    private static final int RECEIVE_COUNT = 200;

    @Test
    void testReceivingDoesNotLeakEntriesFileHandles() throws Exception {
        final Path base = getCurrentTestDir();

        final LocalFileStore receiveStore = new LocalFileStore("receiveStore", base.resolve("store"), "w1");
        final LocalFileGroupQueue outputQueue =
                new LocalFileGroupQueue("preAggregateInput", base.resolve("out"));
        final LocalFileGroupQueue splitZipQueue =
                new LocalFileGroupQueue("splitZipInput", base.resolve("split"));

        // The entries file is only read when a split-zip queue is wired up.
        final ReceiveStagePublisher publisher = new ReceiveStagePublisher(
                receiveStore, outputQueue, splitZipQueue, "test-node");

        assertThat(openEntriesHandles())
                .as("nothing should be holding an entries file before we start")
                .isZero();

        long peakOpen = 0;

        for (int i = 0; i < RECEIVE_COUNT; i++) {
            final Path incoming = base.resolve("incoming").resolve("group-" + i);
            Files.createDirectories(incoming);
            Files.writeString(incoming.resolve("proxy.meta"), "Feed:TEST\n", StandardCharsets.UTF_8);
            Files.writeString(incoming.resolve("proxy.zip"), "zip", StandardCharsets.UTF_8);
            // Two feeds, so the entries file is actually parsed to the end.
            Files.writeString(
                    incoming.resolve("proxy.entries"),
                    "FEED_A,RAW_EVENTS,1\nFEED_B,RAW_EVENTS,1\n",
                    StandardCharsets.UTF_8);

            publisher.accept(incoming);

            // Look now, before anything can collect a leaked channel for us.
            peakOpen = Math.max(peakOpen, openEntriesHandles());
        }

        assertThat(peakOpen)
                .as("an entries file was still open after a receive returned")
                .isZero();
    }

    /**
     * @return How many of this process's descriptors still point at a
     * {@code proxy.entries} file.
     * <p>
     * The receive stage deletes its source directory once it has published, so a
     * descriptor leaked during that receive points at an unlinked file and the
     * kernel renders the link target as "{@code /path/proxy.entries (deleted)}".
     * Matching on the bare name misses exactly the case this test exists to
     * catch.
     * </p>
     */
    private static long openEntriesHandles() throws IOException {
        final Path fdDir = Path.of("/proc/self/fd");
        try (final Stream<Path> stream = Files.list(fdDir)) {
            return stream
                    .map(TestReceiveStagePublisherFileHandles::readLinkQuietly)
                    .filter(target -> target != null && target.contains("proxy.entries"))
                    .count();
        }
    }

    private static String readLinkQuietly(final Path fd) {
        try {
            return Files.readSymbolicLink(fd).toString();
        } catch (final IOException e) {
            // The descriptor closed between listing and reading it.
            return null;
        }
    }
}
