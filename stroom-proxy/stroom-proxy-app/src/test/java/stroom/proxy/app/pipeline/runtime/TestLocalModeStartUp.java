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

package stroom.proxy.app.pipeline.runtime;

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The local-mode start-up sweep: a committed group is live if any message in any local queue -
 * pending, in flight or quarantined - names it, and an orphan otherwise.
 */
class TestLocalModeStartUp extends StroomUnitTest {

    private static final String STORE = "receiveStore";
    private static final String QUEUE = "preAggregateInput";

    @Test
    void testGroupsNamedByAnyMessageSurviveAndTheRestAreDeleted() throws IOException {
        final FilesystemFileStore store = new FilesystemFileStore(STORE, getCurrentTestDir().resolve(STORE));
        final LocalFileGroupQueue queue = new LocalFileGroupQueue(
                QUEUE, getCurrentTestDir().resolve(QUEUE), new FileGroupQueueMessageCodec(), 1);

        final FileStoreLocation pending = commit(store, "pending");
        final FileStoreLocation quarantined = commit(store, "quarantined");
        final FileStoreLocation orphan = commit(store, "orphan");
        queue.publish(message(quarantined));
        final FileGroupQueueItem item = queue.next().orElseThrow();
        item.fail(new RuntimeException("cannot process"));
        queue.publish(message(pending));
        assertThat(queue.getApproximateFailedCount()).isEqualTo(1);
        assertThat(queue.getApproximatePendingCount()).isEqualTo(1);

        LocalModeStartUp.sweepOrphans(List.of(queue), List.of(store));

        assertThat(store.resolve(pending).resolve("proxy.zip")).hasContent("pending");
        assertThat(store.resolve(quarantined).resolve("proxy.zip")).hasContent("quarantined");
        assertThatThrownBy(() -> store.resolve(orphan)).isInstanceOf(FileGroupNotFoundException.class);
    }

    @Test
    void testNothingIsSweptIfAQueueMessageCannotBeRead() throws IOException {
        final FilesystemFileStore store = new FilesystemFileStore(STORE, getCurrentTestDir().resolve(STORE));
        final Path queueRoot = getCurrentTestDir().resolve(QUEUE);
        final LocalFileGroupQueue queue = new LocalFileGroupQueue(queueRoot.getFileName().toString(), queueRoot,
                new FileGroupQueueMessageCodec());
        final FileStoreLocation unreferenced = commit(store, "unreferenced");
        Files.writeString(queue.getPendingDir().resolve("00000000000000000099.json"), "{ not a message");

        LocalModeStartUp.sweepOrphans(List.of(queue), List.of(store));

        assertThat(store.resolve(unreferenced).resolve("proxy.zip"))
                .as("a group whose message might be the unreadable one must not be deleted")
                .hasContent("unreferenced");
    }

    /**
     * A message the queue itself quarantined as undecodable never named a group this proxy could
     * resolve, and it stays in {@code failed/} until an operator removes it. It used to be read like
     * any other message, fail to decode, and stop the sweep on every start for as long as it sat
     * there - orphans then accumulated without bound.
     */
    @Test
    void testAMessageQuarantinedAsUndecodableDoesNotStopTheSweep() throws IOException {
        final FilesystemFileStore store = new FilesystemFileStore(STORE, getCurrentTestDir().resolve(STORE));
        final Path queueRoot = getCurrentTestDir().resolve(QUEUE);
        final LocalFileGroupQueue queue = new LocalFileGroupQueue(queueRoot.getFileName().toString(), queueRoot,
                new FileGroupQueueMessageCodec());
        final FileStoreLocation referenced = commit(store, "referenced");
        final FileStoreLocation unreferenced = commit(store, "unreferenced");
        queue.publish(message(referenced));
        Files.writeString(queue.getFailedDir().resolve("00000000000000000099.invalid-message.1.json"),
                "{ not a message");

        LocalModeStartUp.sweepOrphans(List.of(queue), List.of(store));

        assertThat(store.resolve(referenced).resolve("proxy.zip")).hasContent("referenced");
        assertThatThrownBy(() -> store.resolve(unreferenced))
                .as("the sweep ran")
                .isInstanceOf(FileGroupNotFoundException.class);
    }

    private static FileStoreLocation commit(final FilesystemFileStore store, final String content) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), content);
            return write.commit();
        }
    }

    private static FileGroupQueueMessage message(final FileStoreLocation location) {
        return new FileGroupQueueMessage(FileGroupQueueMessage.CURRENT_SCHEMA_VERSION, "group", location,
                null, null, "receive", "node-1", Instant.now(), null, Map.of());
    }
}
