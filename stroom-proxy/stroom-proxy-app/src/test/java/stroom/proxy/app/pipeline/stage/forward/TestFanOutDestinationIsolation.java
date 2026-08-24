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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.local.LocalFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One unreachable destination must not re-deliver to the destinations that already
 * succeeded.
 * <p>
 * The fan-out loop aborts when a destination fails, the worker fails the item, and
 * the message is redelivered - which previously restarted the loop from the top,
 * copying and publishing to every healthy destination again. Combined with the
 * absence of retry backoff that produced over a thousand duplicate deliveries per
 * second per stuck file group, and an orphaned copy in the failing destination's
 * store on every attempt.
 * </p>
 * <p>
 * Delivery remains at-least-once: a destination may still see duplicates across a
 * restart, because the record of what has been delivered is in memory only.
 * </p>
 */
class TestFanOutDestinationIsolation extends StroomUnitTest {

    private static final String FILE_GROUP_ID = "file-group-1";

    @Test
    void testRetryDoesNotRedeliverToDestinationsThatAlreadySucceeded() throws Exception {
        final Path base = getCurrentTestDir();

        final LocalFileStore sourceStore =
                new LocalFileStore("aggregateStore", base.resolve("src"), "w1");
        final FileStoreLocation location = writeGroup(sourceStore);
        final Path sourceDir = sourceStore.resolve(location);

        final LocalFileStore goodStore = new LocalFileStore("good", base.resolve("good"), "wg");
        final LocalFileGroupQueue goodQueue =
                new LocalFileGroupQueue("goodQueue", base.resolve("goodq"));

        final AtomicBoolean destinationDown = new AtomicBoolean(true);
        final LocalFileStore badStore = new LocalFileStore("bad", base.resolve("bad"), "wb");
        final LocalFileGroupQueue badQueue =
                new LocalFileGroupQueue("badQueue", base.resolve("badq")) {
                    @Override
                    public void publish(final FileGroupQueueMessage m) throws IOException {
                        if (destinationDown.get()) {
                            throw new IOException("destination is down");
                        }
                        super.publish(m);
                    }
                };

        final ForwardStageFanOutForwarder forwarder = new ForwardStageFanOutForwarder(
                List.of(
                        new ForwardStageFanOutForwarder.Destination("good", goodStore, goodQueue),
                        new ForwardStageFanOutForwarder.Destination("bad", badStore, badQueue)),
                "node-1");

        final FileGroupQueueMessage message = message(location);

        // Five attempts while the second destination is down.
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> forwarder.forward(message, sourceDir))
                    .isInstanceOf(IOException.class);
        }

        assertThat(goodQueue.getApproximatePendingCount())
                .as("healthy destination receives the file group exactly once, "
                    + "however many times the failing one is retried")
                .isEqualTo(1);
        assertThat(countGroups(base.resolve("good")))
                .as("and one copy in its store")
                .isEqualTo(1);
        assertThat(forwarder.getDeliveredDestinations(FILE_GROUP_ID)).containsExactly("good");

        // The destination recovers; the outstanding attempt completes it.
        destinationDown.set(false);
        forwarder.forward(message, sourceDir);

        assertThat(badQueue.getApproximatePendingCount())
                .as("the recovered destination still gets its delivery")
                .isEqualTo(1);
        assertThat(goodQueue.getApproximatePendingCount())
                .as("and the healthy one is still not re-delivered")
                .isEqualTo(1);
    }

    @Test
    void testTrackingIsClearedOnceEveryDestinationSucceeds() throws Exception {
        final Path base = getCurrentTestDir();

        final LocalFileStore sourceStore =
                new LocalFileStore("aggregateStore", base.resolve("src"), "w1");
        final FileStoreLocation location = writeGroup(sourceStore);
        final Path sourceDir = sourceStore.resolve(location);

        final LocalFileStore aStore = new LocalFileStore("a", base.resolve("a"), "wa");
        final LocalFileStore bStore = new LocalFileStore("b", base.resolve("b"), "wb");

        final ForwardStageFanOutForwarder forwarder = new ForwardStageFanOutForwarder(
                List.of(
                        new ForwardStageFanOutForwarder.Destination(
                                "a", aStore, new LocalFileGroupQueue("aq", base.resolve("aq"))),
                        new ForwardStageFanOutForwarder.Destination(
                                "b", bStore, new LocalFileGroupQueue("bq", base.resolve("bq")))),
                "node-1");

        forwarder.forward(message(location), sourceDir);

        // Nothing is retained for a file group that completed - the message is about
        // to be acknowledged and will not come back.
        assertThat(forwarder.getDeliveredDestinations(FILE_GROUP_ID)).isEmpty();
    }

    @Test
    void testDistinctFileGroupsAreTrackedIndependently() throws Exception {
        final Path base = getCurrentTestDir();

        final LocalFileStore sourceStore =
                new LocalFileStore("aggregateStore", base.resolve("src"), "w1");

        final LocalFileStore goodStore = new LocalFileStore("good", base.resolve("good"), "wg");
        final LocalFileGroupQueue goodQueue =
                new LocalFileGroupQueue("goodQueue", base.resolve("goodq"));
        final LocalFileStore badStore = new LocalFileStore("bad", base.resolve("bad"), "wb");
        final LocalFileGroupQueue badQueue =
                new LocalFileGroupQueue("badQueue", base.resolve("badq")) {
                    @Override
                    public void publish(final FileGroupQueueMessage m) throws IOException {
                        throw new IOException("destination is down");
                    }
                };

        final ForwardStageFanOutForwarder forwarder = new ForwardStageFanOutForwarder(
                List.of(
                        new ForwardStageFanOutForwarder.Destination("good", goodStore, goodQueue),
                        new ForwardStageFanOutForwarder.Destination("bad", badStore, badQueue)),
                "node-1");

        for (int i = 0; i < 3; i++) {
            final FileStoreLocation location = writeGroup(sourceStore);
            final Path sourceDir = sourceStore.resolve(location);
            final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                    "forwardingInput", "fg-" + i, location, "aggregate", "node-1", null, Map.of());

            // Two attempts each.
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThatThrownBy(() -> forwarder.forward(message, sourceDir))
                        .isInstanceOf(IOException.class);
            }
        }

        // Three distinct file groups, one delivery each to the healthy destination.
        assertThat(goodQueue.getApproximatePendingCount()).isEqualTo(3);
        assertThat(countGroups(base.resolve("good"))).isEqualTo(3);
    }

    private static FileGroupQueueMessage message(final FileStoreLocation location) {
        return FileGroupQueueMessage.create(
                "forwardingInput", FILE_GROUP_ID, location, "aggregate", "node-1", null, Map.of());
    }

    private static FileStoreLocation writeGroup(final LocalFileStore store) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta");
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip");
            Files.writeString(write.getPath().resolve("proxy.entries"), "entries");
            return write.commit();
        }
    }

    private static long countGroups(final Path storeRoot) throws IOException {
        long count = 0;
        try (final Stream<Path> writers = Files.list(storeRoot)) {
            for (final Path w : writers.filter(Files::isDirectory).toList()) {
                if ("writing".equals(w.getFileName().toString())) {
                    continue;
                }
                try (final Stream<Path> g = Files.list(w)) {
                    count += g.filter(Files::isDirectory).count();
                }
            }
        }
        return count;
    }
}
