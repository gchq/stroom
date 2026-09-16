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

package stroom.proxy.app.pipeline.queue;

import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
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
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"));

        final FileStoreLocation location;
        final Path writePath;

        try (final FileStoreWrite write = fileStore.newWrite()) {
            writePath = write.getPath();

            Files.writeString(writePath.resolve("proxy.meta"), "meta");
            Files.writeString(writePath.resolve("proxy.zip"), "zip");
            Files.writeString(writePath.resolve("proxy.entries"), "entries");

            location = write.commit();

        }

        final Path stablePath = fileStore.resolve(location);

        assertThat(location.storeName()).isEqualTo("receiveStore");
        assertThat(location.isFilesystem()).isTrue();
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
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"));

        final Path writePath;

        try (final FileStoreWrite write = fileStore.newWrite()) {
            writePath = write.getPath();
            Files.writeString(writePath.resolve("proxy.meta"), "meta");

            assertThat(writePath).exists().isDirectory();
        }

        assertThat(writePath).doesNotExist();
    }

    @Test
    void testLocalFileStoreCommitIsIdempotent() throws IOException {
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"));

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
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"));
        final FileStoreLocation location = FileStoreLocation.filesystem(
                "aggregateStore",
                getCurrentTestDir().resolve("receive-store/writer-1/0000000001"));

        assertThatThrownBy(() -> fileStore.resolve(location))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("aggregateStore")
                .hasMessageContaining("receiveStore");
    }

    @Test
    void testQueueMessageCodecRoundTrip() throws IOException {
        final FileStoreLocation location = FileStoreLocation.filesystem(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store/writer-1/0000000001"));
        final FileGroupQueueMessage message = new FileGroupQueueMessage(
                FileGroupQueueMessage.CURRENT_SCHEMA_VERSION,
                "message-1",
                location,
                null,
                null,
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
        assertThat(json).contains("\"schemaVersion\":2");
        assertThat(json).contains("\"fileStoreLocation\"");
    }

    @Test
    void testQueueMessageValidationRejectsMissingRequiredFields() {
        final FileStoreLocation location = FileStoreLocation.filesystem(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store/writer-1/0000000001"));

        assertThatThrownBy(() -> new FileGroupQueueMessage(
                FileGroupQueueMessage.CURRENT_SCHEMA_VERSION,
                "message-1",
                location,
                null,
                null,
                null,
                "proxy-node-1",
                Instant.parse("2025-01-02T03:04:05Z"),
                "trace-1",
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("producingStage");
    }

    @Test
    void testFileStoreLocationValidationRejectsAnUnknownScheme() {
        assertThatThrownBy(() -> new FileStoreLocation("receiveStore", "ftp://host/key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with");
    }

    @Test
    void testDeleteRemovesCommittedFileGroup() throws IOException {
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"));

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
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"));

        final FileStoreLocation location;
        try (final FileStoreWrite write = fileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta");
            location = write.commit();
        }

        // Delete once.
        fileStore.delete(location);
        // R10/§3.3: a consumed group is now reported absent rather than handed back as a path
        // to something that is not there - which is what this assertion used to rely on.
        assertThatThrownBy(() -> fileStore.resolve(location))
                .isInstanceOf(FileGroupNotFoundException.class);

        // Delete again — should be a no-op, not an error.
        fileStore.delete(location);
    }

    @Test
    void testDeleteRejectsLocationForDifferentStore() {
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"));
        final FileStoreLocation otherStoreLocation = FileStoreLocation.filesystem(
                "aggregateStore",
                getCurrentTestDir().resolve("receive-store/writer-1/0000000001"));

        assertThatThrownBy(() -> fileStore.delete(otherStoreLocation))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("aggregateStore")
                .hasMessageContaining("receiveStore");
    }

    @Test
    void testDeleteDoesNotAffectOtherLocations() throws IOException {
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                "receiveStore",
                getCurrentTestDir().resolve("receive-store"));

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

        // R10/§3.3: a consumed group is now reported absent rather than handed back as a path
        // to something that is not there - which is what this assertion used to rely on.
        assertThatThrownBy(() -> fileStore.resolve(locationA))
                .isInstanceOf(FileGroupNotFoundException.class);
        assertThat(fileStore.resolve(locationB)).exists().isDirectory();
        assertThat(fileStore.resolve(locationB).resolve("proxy.meta")).hasContent("meta-B");
    }

    @Test
    void testDeleteRefusesAnythingThatIsNotAGroupPath() throws IOException {
        final Path storeRoot = getCurrentTestDir().resolve("receive-store");
        final FilesystemFileStore fileStore = new FilesystemFileStore("receiveStore", storeRoot);
        final FileStoreLocation group;
        try (final FileStoreWrite write = fileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip");
            group = write.commit();
        }
        final Path numberingDir = fileStore.resolve(group).getParent();

        for (final Path notAGroup : java.util.List.of(storeRoot, numberingDir)) {
            assertThatThrownBy(() -> fileStore.delete(FileStoreLocation.filesystem("receiveStore", notAGroup)))
                    .isInstanceOf(IOException.class)
                    .hasMessageFindingMatch("not a file group path|outside store root");
        }

        assertThat(storeRoot).exists().isDirectory();
        assertThat(numberingDir).exists().isDirectory();
        assertThat(fileStore.resolve(group).resolve("proxy.zip")).hasContent("zip");
    }
}
