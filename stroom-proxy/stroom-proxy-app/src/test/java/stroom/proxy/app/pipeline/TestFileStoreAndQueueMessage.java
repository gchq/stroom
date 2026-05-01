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

package stroom.proxy.app.pipeline;

import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFileStoreAndQueueMessage extends StroomUnitTest {

    @Test
    void testLocalFileStoreCommitCreatesStableLocation() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"),
                "writer-1");

        final FileStoreLocation location;
        final Path writePath;

        try (final FileStoreWrite write = fileStore.newWrite()) {
            writePath = write.getPath();

            Files.writeString(writePath.resolve("proxy.meta"), "meta");
            Files.writeString(writePath.resolve("proxy.zip"), "zip");
            Files.writeString(writePath.resolve("proxy.entries"), "entries");

            location = write.commit();

            assertThat(write.isCommitted()).isTrue();
        }

        final Path stablePath = fileStore.resolve(location);

        assertThat(location.storeName()).isEqualTo("receiveStore");
        assertThat(location.locationType()).isEqualTo(FileStoreLocation.LocationType.LOCAL_FILESYSTEM);
        assertThat(location.uri()).startsWith("file:");
        assertThat(stablePath).exists().isDirectory();
        assertThat(stablePath).isNotEqualTo(writePath);
        assertThat(stablePath.resolve("proxy.meta")).hasContent("meta");
        assertThat(stablePath.resolve("proxy.zip")).hasContent("zip");
        assertThat(stablePath.resolve("proxy.entries")).hasContent("entries");
        assertThat(writePath).doesNotExist();
    }

    @Test
    void testLocalFileStoreCloseRemovesUncommittedWrite() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"),
                "writer-1");

        final Path writePath;

        try (final FileStoreWrite write = fileStore.newWrite()) {
            writePath = write.getPath();
            Files.writeString(writePath.resolve("proxy.meta"), "meta");

            assertThat(write.isCommitted()).isFalse();
            assertThat(writePath).exists().isDirectory();
        }

        assertThat(writePath).doesNotExist();
    }

    @Test
    void testLocalFileStoreCommitIsIdempotent() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"),
                "writer-1");

        try (final FileStoreWrite write = fileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta");

            final FileStoreLocation firstLocation = write.commit();
            final FileStoreLocation secondLocation = write.commit();

            assertThat(secondLocation).isEqualTo(firstLocation);
            assertThat(fileStore.resolve(firstLocation).resolve("proxy.meta")).hasContent("meta");
        }
    }

    @Test
    void testLocalFileStoreRejectsLocationForDifferentStore() {
        final LocalFileStore fileStore = new LocalFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"),
                "writer-1");
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "aggregateStore",
                getCurrentTestDir().resolve("receive-store/writer-1/0000000001"));

        assertThatThrownBy(() -> fileStore.resolve(location))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("aggregateStore")
                .hasMessageContaining("receiveStore");
    }

    @Test
    void testQueueMessageCodecRoundTrip() throws IOException {
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store/writer-1/0000000001"));
        final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                "message-1",
                "preAggregateInput",
                "file-group-1",
                location,
                "receive",
                "proxy-node-1",
                Instant.parse("2025-01-02T03:04:05Z"),
                "trace-1",
                Map.of(
                        "feed", "TEST_FEED",
                        "type", "Raw Events"));

        final FileGroupQueueMessageCodec codec = new FileGroupQueueMessageCodec();

        final String json = codec.toJson(message);
        final FileGroupQueueMessage roundTripped = codec.fromJson(json);

        assertThat(roundTripped).isEqualTo(message);
        assertThat(json).contains("\"schemaVersion\":1");
        assertThat(json).contains("\"queueName\":\"preAggregateInput\"");
        assertThat(json).contains("\"fileStoreLocation\"");
    }

    @Test
    void testQueueMessageValidationRejectsMissingRequiredFields() {
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store/writer-1/0000000001"));

        assertThatThrownBy(() -> FileGroupQueueMessage.create(
                "message-1",
                " ",
                "file-group-1",
                location,
                "receive",
                "proxy-node-1",
                Instant.parse("2025-01-02T03:04:05Z"),
                "trace-1",
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queueName");
    }

    @Test
    void testFileStoreLocationValidationRejectsNonFileUriForLocalFilesystem() {
        assertThatThrownBy(() -> new FileStoreLocation(
                "receiveStore",
                FileStoreLocation.LocationType.LOCAL_FILESYSTEM,
                "s3://bucket/key",
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file URI");
    }

    @Test
    void testDeleteRemovesCommittedFileGroup() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"),
                "writer-1");

        final FileStoreLocation location;
        try (final FileStoreWrite write = fileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta");
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip");
            location = write.commit();
        }

        final Path stablePath = fileStore.resolve(location);
        assertThat(stablePath).exists().isDirectory();

        fileStore.delete(location);

        assertThat(stablePath).doesNotExist();
    }

    @Test
    void testDeleteIsIdempotentForAlreadyDeletedLocation() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"),
                "writer-1");

        final FileStoreLocation location;
        try (final FileStoreWrite write = fileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta");
            location = write.commit();
        }

        // Delete once.
        fileStore.delete(location);
        assertThat(fileStore.resolve(location)).doesNotExist();

        // Delete again — should be a no-op, not an error.
        fileStore.delete(location);
    }

    @Test
    void testDeleteRejectsLocationForDifferentStore() {
        final LocalFileStore fileStore = new LocalFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"),
                "writer-1");
        final FileStoreLocation otherStoreLocation = FileStoreLocation.localFileSystem(
                "aggregateStore",
                getCurrentTestDir().resolve("receive-store/writer-1/0000000001"));

        assertThatThrownBy(() -> fileStore.delete(otherStoreLocation))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("aggregateStore")
                .hasMessageContaining("receiveStore");
    }

    @Test
    void testDeleteDoesNotAffectOtherLocations() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"),
                "writer-1");

        // Commit two separate file groups.
        final FileStoreLocation locationA;
        try (final FileStoreWrite write = fileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta-A");
            locationA = write.commit();
        }

        final FileStoreLocation locationB;
        try (final FileStoreWrite write = fileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta-B");
            locationB = write.commit();
        }

        // Delete only A.
        fileStore.delete(locationA);

        assertThat(fileStore.resolve(locationA)).doesNotExist();
        assertThat(fileStore.resolve(locationB)).exists().isDirectory();
        assertThat(fileStore.resolve(locationB).resolve("proxy.meta")).hasContent("meta-B");
    }

    @Test
    void testDeleteDoesNotRemoveWriterRootOrStoreRoot() throws IOException {
        final Path storeRoot = getCurrentTestDir().resolve("receive-store");
        final LocalFileStore fileStore = new LocalFileStore(
                "receiveStore",
                storeRoot,
                "writer-1");

        // Craft a location that points directly at the store root.
        final FileStoreLocation storeRootLocation = FileStoreLocation.localFileSystem(
                "receiveStore",
                storeRoot);

        assertThatThrownBy(() -> fileStore.delete(storeRootLocation))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("store-level directory");

        // Craft a location that points at the writer root.
        final FileStoreLocation writerRootLocation = FileStoreLocation.localFileSystem(
                "receiveStore",
                storeRoot.resolve("writer-1"));

        assertThatThrownBy(() -> fileStore.delete(writerRootLocation))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("store-level directory");

        // Both directories should still exist.
        assertThat(storeRoot).exists().isDirectory();
        assertThat(storeRoot.resolve("writer-1")).exists().isDirectory();
    }
}
