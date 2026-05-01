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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the idempotency infrastructure in LocalFileStore:
 * .complete marker, isComplete(), and deterministic writes.
 */
class TestFileStoreIdempotency extends StroomUnitTest {

    private static final String STORE_NAME = "testStore";

    // --- .complete marker tests ---

    @Test
    void testCommitWritesCompleteMarker() throws IOException {
        final LocalFileStore store = createStore("marker-test");

        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "hello");
            location = write.commit();
        }

        final Path resolved = store.resolve(location);
        assertThat(resolved.resolve(".complete")).exists();
    }

    @Test
    void testIsCompleteReturnsTrueForCommittedWrite() throws IOException {
        final LocalFileStore store = createStore("complete-true");

        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "hello");
            location = write.commit();
        }

        assertThat(store.isComplete(location)).isTrue();
    }

    @Test
    void testIsCompleteReturnsFalseForMissingLocation() throws IOException {
        final LocalFileStore store = createStore("complete-missing");

        // Create a location pointing to a non-existent directory.
        final Path fakePath = store.getRoot().resolve("writer-1").resolve("9999999999");
        Files.createDirectories(fakePath.getParent());
        final FileStoreLocation location = FileStoreLocation.localFileSystem(STORE_NAME, fakePath);

        assertThat(store.isComplete(location)).isFalse();
    }

    @Test
    void testIsCompleteReturnsFalseForPartialWrite() throws IOException {
        final LocalFileStore store = createStore("complete-partial");

        // Manually create a directory without a .complete marker
        // (simulates a crash mid-write).
        final Path partialPath = store.getRoot().resolve("writer-1").resolve("partial-dir");
        Files.createDirectories(partialPath);
        Files.writeString(partialPath.resolve("data.txt"), "partial");
        final FileStoreLocation location = FileStoreLocation.localFileSystem(STORE_NAME, partialPath);

        assertThat(store.isComplete(location)).isFalse();
    }

    @Test
    void testUncommittedWriteHasNoMarker() throws IOException {
        final LocalFileStore store = createStore("uncommitted-test");

        // Create a write but do NOT commit — close should clean up.
        final Path writePath;
        try (final FileStoreWrite write = store.newWrite()) {
            writePath = write.getPath();
            Files.writeString(writePath.resolve("data.txt"), "hello");
            // No commit — close should clean up.
        }

        // The temp path should be cleaned up.
        assertThat(writePath).doesNotExist();
    }

    // --- Deterministic write tests ---

    @Test
    void testDeterministicWriteNewFileGroup() throws IOException {
        final LocalFileStore store = createStore("det-new");

        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newDeterministicWrite("fg-abc-123")) {
            assertThat(write.isCommitted()).isFalse();
            Files.writeString(write.getPath().resolve("data.txt"), "deterministic");
            location = write.commit();
        }

        assertThat(store.isComplete(location)).isTrue();
        final Path resolved = store.resolve(location);
        assertThat(resolved.resolve("data.txt")).hasContent("deterministic");
        assertThat(resolved.getFileName().toString()).isEqualTo("fg-abc-123");
    }

    @Test
    void testDeterministicWriteReturnsSamePathForSameId() throws IOException {
        final LocalFileStore store = createStore("det-same");

        // First write.
        final FileStoreLocation location1;
        try (final FileStoreWrite write = store.newDeterministicWrite("fg-replay")) {
            Files.writeString(write.getPath().resolve("data.txt"), "first");
            location1 = write.commit();
        }

        // Second write with same ID — should return pre-committed handle.
        final FileStoreLocation location2;
        try (final FileStoreWrite write = store.newDeterministicWrite("fg-replay")) {
            assertThat(write.isCommitted()).isTrue();
            location2 = write.commit();
        }

        // Both should point to the same location.
        assertThat(location1.uri()).isEqualTo(location2.uri());

        // Original content should be preserved.
        final Path resolved = store.resolve(location2);
        assertThat(resolved.resolve("data.txt")).hasContent("first");
    }

    @Test
    void testDeterministicWriteCleansUpPartialOnRetry() throws IOException {
        final LocalFileStore store = createStore("det-partial");

        // Simulate a partial write (directory exists but no .complete marker).
        final Path stablePath = store.getRoot().resolve("writer-1").resolve("fg-partial");
        Files.createDirectories(stablePath);
        Files.writeString(stablePath.resolve("stale.txt"), "stale-data");

        // Now do a deterministic write — should clean up the partial dir.
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newDeterministicWrite("fg-partial")) {
            assertThat(write.isCommitted()).isFalse();
            Files.writeString(write.getPath().resolve("fresh.txt"), "fresh-data");
            location = write.commit();
        }

        final Path resolved = store.resolve(location);
        assertThat(resolved.resolve("fresh.txt")).hasContent("fresh-data");
        assertThat(resolved.resolve("stale.txt")).doesNotExist();
        assertThat(store.isComplete(location)).isTrue();
    }

    @Test
    void testDeterministicWriteUncommittedCleansUp() throws IOException {
        final LocalFileStore store = createStore("det-cleanup");

        final Path tempPath;
        try (final FileStoreWrite write = store.newDeterministicWrite("fg-abandon")) {
            tempPath = write.getPath();
            Files.writeString(tempPath.resolve("data.txt"), "abandoned");
            // No commit — close should clean up the temp directory.
        }

        assertThat(tempPath).doesNotExist();

        // The stable path should not exist either.
        final Path stablePath = store.getRoot().resolve("writer-1").resolve("fg-abandon");
        assertThat(stablePath).doesNotExist();
    }

    @Test
    void testDeleteRemovesCompleteMarker() throws IOException {
        final LocalFileStore store = createStore("del-marker");

        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "hello");
            location = write.commit();
        }

        assertThat(store.isComplete(location)).isTrue();

        store.delete(location);

        assertThat(store.isComplete(location)).isFalse();
    }

    @Test
    void testDeterministicWriteAndDeleteCycle() throws IOException {
        final LocalFileStore store = createStore("det-delete");

        // Write with deterministic path.
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newDeterministicWrite("fg-cycle")) {
            Files.writeString(write.getPath().resolve("data.txt"), "v1");
            location = write.commit();
        }
        assertThat(store.isComplete(location)).isTrue();

        // Delete.
        store.delete(location);
        assertThat(store.isComplete(location)).isFalse();

        // Re-write with same ID.
        final FileStoreLocation location2;
        try (final FileStoreWrite write = store.newDeterministicWrite("fg-cycle")) {
            assertThat(write.isCommitted()).isFalse();
            Files.writeString(write.getPath().resolve("data.txt"), "v2");
            location2 = write.commit();
        }

        final Path resolved = store.resolve(location2);
        assertThat(resolved.resolve("data.txt")).hasContent("v2");
        assertThat(store.isComplete(location2)).isTrue();
    }

    // --- helpers ---

    private LocalFileStore createStore(final String dirName) {
        return new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve(dirName),
                "writer-1");
    }
}
