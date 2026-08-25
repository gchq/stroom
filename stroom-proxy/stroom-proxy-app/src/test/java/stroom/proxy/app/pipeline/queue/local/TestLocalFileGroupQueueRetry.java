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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A message the pipeline cannot process must not stop the ones behind it.
 * <p>
 * {@code findNextPendingFile()} always takes the lowest id. Returning a failed
 * message to {@code pending/} under its original id therefore put it back at the
 * <em>head</em>, so it was handed straight out again and everything behind it
 * waited. One unprocessable message was enough to stop a queue completely - and
 * at-least-once delivery makes unprocessable messages normal, because a message
 * can reference a file group that an earlier duplicate already consumed.
 * </p>
 * <p>
 * Failing a message now re-queues it at the back under a fresh id, carrying a
 * delivery-attempt count, and quarantines it once that count reaches
 * {@code maxDeliveryAttempts}.
 * </p>
 */
class TestLocalFileGroupQueueRetry extends StroomUnitTest {

    private LocalFileGroupQueue queue(final String name, final int maxDeliveryAttempts) throws IOException {
        return new LocalFileGroupQueue(
                name,
                getCurrentTestDir().resolve(name),
                new FileGroupQueueMessageCodec(),
                Duration.ofHours(1),
                maxDeliveryAttempts);
    }

    private static FileGroupQueueMessage message(final LocalFileGroupQueue queue, final String fileGroupId) {
        return FileGroupQueueMessage.create(
                queue.getName(),
                fileGroupId,
                FileStoreLocation.localFileSystem("store", Path.of("/tmp", fileGroupId)),
                "test",
                "test-node",
                null,
                Map.of());
    }

    @Test
    void testAFailedMessageGoesToTheBackOfTheQueue() throws Exception {
        final LocalFileGroupQueue queue = queue("back-of-queue", 100);
        queue.publish(message(queue, "fg-1"));
        queue.publish(message(queue, "fg-2"));

        try (final FileGroupQueueItem first = queue.next().orElseThrow()) {
            assertThat(first.getMessage().fileGroupId()).isEqualTo("fg-1");
            first.fail(new IllegalStateException("cannot process this one"));
        }

        try (final FileGroupQueueItem second = queue.next().orElseThrow()) {
            assertThat(second.getMessage().fileGroupId())
                    .as("the message behind the failure must not have to wait for it")
                    .isEqualTo("fg-2");
            second.acknowledge();
        }

        try (final FileGroupQueueItem retried = queue.next().orElseThrow()) {
            assertThat(retried.getMessage().fileGroupId())
                    .as("and the failed one comes back afterwards")
                    .isEqualTo("fg-1");
            retried.acknowledge();
        }
    }

    @Test
    void testDeliveryAttemptsAreCarriedInTheMessage() throws Exception {
        final LocalFileGroupQueue queue = queue("attempts", 100);
        queue.publish(message(queue, "fg-1"));

        for (int expected = 1; expected <= 3; expected++) {
            try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
                assertThat(LocalFileGroupQueue.deliveryAttempts(item.getMessage()))
                        .isEqualTo(expected - 1);
                item.fail(new IllegalStateException("attempt " + expected));
            }
        }

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            assertThat(LocalFileGroupQueue.deliveryAttempts(item.getMessage())).isEqualTo(3);
            item.acknowledge();
        }
    }

    @Test
    void testAMessageIsQuarantinedOnceItHasBeenTriedTooOften() throws Exception {
        final LocalFileGroupQueue queue = queue("quarantine", 3);
        queue.publish(message(queue, "fg-1"));

        for (int i = 0; i < 3; i++) {
            try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
                item.fail(new IllegalStateException("attempt " + i));
            }
        }

        assertThat(queue.next())
                .as("a message that can never succeed must stop circulating")
                .isEmpty();
        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(queue.getApproximateFailedCount()).isEqualTo(1);
    }

    @Test
    void testQuarantineKeepsTheMessageAndTheLastError() throws Exception {
        final LocalFileGroupQueue queue = queue("quarantine-detail", 1);
        queue.publish(message(queue, "fg-1"));

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            item.fail(new IllegalStateException("the reason it could not be processed"));
        }

        final List<String> failedFiles = new ArrayList<>();
        try (final Stream<Path> stream = Files.list(queue.getFailedDir())) {
            stream.forEach(path -> failedFiles.add(path.getFileName().toString()));
        }

        // Quarantine is somewhere an operator can look, not a deletion.
        assertThat(failedFiles).anyMatch(f -> f.contains("max-delivery-attempts") && f.endsWith(".json"));
        assertThat(failedFiles).anyMatch(f -> f.endsWith(".error.txt"));

        final Path errorFile = queue.getFailedDir().resolve(
                failedFiles.stream().filter(f -> f.endsWith(".error.txt")).findFirst().orElseThrow());
        assertThat(Files.readString(errorFile)).contains("the reason it could not be processed");
    }

    @Test
    void testOnePoisonMessageDoesNotStopTheQueue() throws Exception {
        final LocalFileGroupQueue queue = queue("poison", 5);

        // The poison message is published first, so it holds the lowest id - the
        // worst case for a queue that always takes the lowest.
        queue.publish(message(queue, "poison"));
        for (int i = 0; i < 20; i++) {
            queue.publish(message(queue, "good-" + i));
        }

        final List<String> processed = new ArrayList<>();
        Optional<FileGroupQueueItem> next;

        // Bounded so a regression fails the test rather than hanging the build.
        for (int i = 0; i < 200 && (next = queue.next()).isPresent(); i++) {
            try (final FileGroupQueueItem item = next.get()) {
                if ("poison".equals(item.getMessage().fileGroupId())) {
                    item.fail(new IllegalStateException("this one can never succeed"));
                } else {
                    processed.add(item.getMessage().fileGroupId());
                    item.acknowledge();
                }
            }
        }

        assertThat(processed)
                .as("every healthy message should have been delivered exactly once")
                .hasSize(20)
                .doesNotHaveDuplicates();
        assertThat(queue.getApproximateFailedCount())
                .as("and the poison one should have been quarantined")
                .isEqualTo(1);
        assertThat(queue.getApproximatePendingCount()).isZero();
    }

    @Test
    void testRequeuingPreservesMessageIdentity() throws Exception {
        final LocalFileGroupQueue queue = queue("identity", 100);
        final FileGroupQueueMessage original = message(queue, "fg-1");
        queue.publish(original);

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            item.fail(new IllegalStateException("retry me"));
        }

        try (final FileGroupQueueItem retried = queue.next().orElseThrow()) {
            final FileGroupQueueMessage message = retried.getMessage();

            // A retry is the same message, not a new arrival - anything correlating
            // on messageId or createdTime has to keep working across it.
            assertThat(message.messageId()).isEqualTo(original.messageId());
            assertThat(message.createdTime()).isEqualTo(original.createdTime());
            assertThat(message.fileGroupId()).isEqualTo(original.fileGroupId());
            assertThat(message.fileStoreLocation()).isEqualTo(original.fileStoreLocation());
            assertThat(message.producingStage()).isEqualTo(original.producingStage());
            retried.acknowledge();
        }
    }
}
