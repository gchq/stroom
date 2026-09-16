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

package stroom.proxy.app.pipeline.queue.local;

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The single-owner model and what it buys: one process owns the directory, a claim is held exactly
 * while a consumer holds the item, and closing an item without completing it releases the claim.
 */
class TestLocalFileGroupQueueOwnershipAndRelease extends StroomUnitTest {

    @Test
    void testASecondQueueCannotOpenTheSameDirectory() throws Exception {
        final Path root = getCurrentTestDir().resolve("contended");
        try (LocalFileGroupQueue first = queue("first", root)) {
            assertThatThrownBy(() -> queue("second", root))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("already in use");
        }
    }

    @Test
    void testARefusedSecondQueueHasNotTouchedTheOwnersInFlightWork() throws Exception {
        final Path root = getCurrentTestDir().resolve("no-steal");
        try (LocalFileGroupQueue first = queue("first", root)) {
            first.publish(message("in-flight"));
            final FileGroupQueueItem held = first.next().orElseThrow();
            assertThat(held.getMessage().messageId()).isEqualTo("in-flight");

            assertThatThrownBy(() -> queue("second", root)).isInstanceOf(IOException.class);

            try (Stream<Path> inFlight = Files.list(root.resolve("in-flight"))) {
                assertThat(inFlight.count()).as("not recovered by the refused process").isEqualTo(1);
            }
            try (Stream<Path> pending = Files.list(root.resolve("pending"))) {
                assertThat(pending.count()).isZero();
            }
            held.acknowledge();
        }
    }

    @Test
    void testTheDirectoryIsUsableAgainOnceTheOwnerHasGone() throws Exception {
        final Path root = getCurrentTestDir().resolve("released");
        final LocalFileGroupQueue first = queue("first", root);
        first.publish(message("survives"));
        first.simulateProcessDeath();

        try (LocalFileGroupQueue restarted = queue("first", root)) {
            assertThat(restarted.next().orElseThrow().getMessage().messageId()).isEqualTo("survives");
        }
    }

    /**
     * Exactly what the worker does when {@code acknowledge()} throws: log, rethrow, and close the
     * item in a {@code finally}. The close is the release; nothing else is needed.
     */
    @Test
    void testClosingAnUncompletedItemReleasesItWithTheAttemptCounted() throws Exception {
        try (LocalFileGroupQueue queue = queue("ack-fails", getCurrentTestDir().resolve("ack-fails"))) {
            queue.publish(message("m1"));

            final FileGroupQueueItem item = queue.next().orElseThrow();
            assertThat(item.getDeliveryAttempt()).isEqualTo(1);
            item.close();

            assertThat(queue.getApproximateInFlightCount()).isZero();
            assertThat(queue.getApproximatePendingCount()).isEqualTo(1);
            final Optional<FileGroupQueueItem> redelivered = queue.next();
            assertThat(redelivered).isPresent();
            assertThat(redelivered.orElseThrow().getMessage().messageId()).isEqualTo("m1");
            assertThat(redelivered.orElseThrow().getDeliveryAttempt()).isEqualTo(2);
            redelivered.orElseThrow().acknowledge();
        }
    }

    @Test
    void testClosingACompletedItemChangesNothing() throws Exception {
        try (LocalFileGroupQueue queue = queue("done", getCurrentTestDir().resolve("done"))) {
            queue.publish(message("m1"));
            final FileGroupQueueItem item = queue.next().orElseThrow();
            item.acknowledge();
            item.close();

            assertThat(queue.next()).isEmpty();
            assertThat(queue.getApproximateInFlightCount()).isZero();
        }
    }

    @Test
    void testReleasingBeyondTheBoundGivesUp() throws Exception {
        try (LocalFileGroupQueue queue = new LocalFileGroupQueue(
                "bounded", getCurrentTestDir().resolve("bounded"), new FileGroupQueueMessageCodec(), 2)) {
            queue.publish(message("m1"));

            queue.next().orElseThrow().close();          // attempt 1 released -> count 1
            queue.next().orElseThrow().close();          // attempt 2 released -> at the bound

            assertThat(queue.next()).isEmpty();
            assertThat(queue.getApproximateFailedCount()).isEqualTo(1);
        }
    }

    /**
     * A read failure says nothing about the message, so it must not be condemned: it goes back to
     * pending and the failure is reported. Only a message that was read and cannot be decoded is
     * given up on.
     */
    @Test
    void testAnUnreadableInFlightFileIsGivenBackNotQuarantined() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                && !"root".equals(System.getProperty("user.name")));
        final Path root = getCurrentTestDir().resolve("unreadable");
        try (LocalFileGroupQueue queue = queue("unreadable", root)) {
            queue.publish(message("m1"));
            final Path pendingFile = root.resolve("pending").resolve("00000000000000000001.json");
            Files.setPosixFilePermissions(pendingFile, java.util.Set.of());
            try {
                assertThatThrownBy(queue::next).isInstanceOf(IOException.class);
                assertThat(queue.getApproximatePendingCount()).as("given back").isEqualTo(1);
                assertThat(queue.getApproximateFailedCount()).isZero();
            } finally {
                Files.setPosixFilePermissions(pendingFile,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
            }
            assertThat(queue.next().orElseThrow().getMessage().messageId()).isEqualTo("m1");
        }
    }

    @Test
    void testAnUnreadableInFlightFileIsGivenBackAtStartUpNotQuarantined() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                && !"root".equals(System.getProperty("user.name")));
        final Path root = getCurrentTestDir().resolve("unreadable-recovery");
        final LocalFileGroupQueue first = queue("q", root);
        first.publish(message("m1"));
        first.next().orElseThrow();
        first.simulateProcessDeath();
        final Path inFlightFile = root.resolve("in-flight").resolve("00000000000000000001.json");
        Files.setPosixFilePermissions(inFlightFile, java.util.Set.of());
        try {
            try (LocalFileGroupQueue restarted = queue("q", root)) {
                assertThat(restarted.getApproximatePendingCount()).as("given back unread").isEqualTo(1);
                assertThat(restarted.getApproximateFailedCount()).isZero();
            }
        } finally {
            Files.setPosixFilePermissions(root.resolve("pending").resolve("00000000000000000001.json"),
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    private static LocalFileGroupQueue queue(final String name, final Path root) throws IOException {
        return new LocalFileGroupQueue(name, root, new FileGroupQueueMessageCodec());
    }

    private static FileGroupQueueMessage message(final String id) {
        return new FileGroupQueueMessage(
                FileGroupQueueMessage.CURRENT_SCHEMA_VERSION,
                id,
                FileStoreLocation.filesystem("store", Path.of("/tmp", id)),
                null,
                null,
                "test",
                "test-node",
                Instant.now(),
                null,
                Map.of());
    }
}
