/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.proxy.app.pipeline.store;

import stroom.proxy.app.pipeline.queue.AbstractFileGroupQueueContractTest;
import stroom.test.common.util.test.StroomUnitTest;

import com.codahale.metrics.health.HealthCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shared contract test suite that every {@link FileStore} implementation
 * must satisfy.
 * <p>
 * Subclasses provide the concrete file store via
 * {@link #createFileStore(String, Path)}. Each test method validates one
 * aspect of the {@link FileStore} interface contract.
 * </p>
 * <p>
 * This mirrors the pattern established by
 * {@link AbstractFileGroupQueueContractTest} for queue implementations.
 * </p>
 */
public abstract class AbstractFileStoreContractTest extends StroomUnitTest {

    private static final String STORE_NAME = "contractStore";

    private FileStore store;

    /**
     * Create a fresh file store for the given logical name.
     *
     * @param storeName The logical store name.
     * @param testRoot  A unique temporary directory for this test.
     * @return A new file store instance.
     * @throws IOException If initialisation fails.
     */
    protected abstract FileStore createFileStore(String storeName, Path testRoot)
            throws IOException;

    @BeforeEach
    protected void setUpStore() throws IOException {
        store = createFileStore(STORE_NAME, getCurrentTestDir());
    }

    // ------------------------------------------------------------------
    // Write + Commit + Resolve
    // ------------------------------------------------------------------

    @Test
    protected void contractWriteCommitProducesResolvableLocation() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "Feed:CONTRACT_TEST");
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip-data");
            location = write.commit();
        }

        assertThat(location).isNotNull();
        assertThat(location.storeName()).isEqualTo(STORE_NAME);

        final Path resolved = store.resolve(location);
        assertThat(resolved).isDirectory();
        assertThat(Files.readString(resolved.resolve("proxy.meta"))).isEqualTo("Feed:CONTRACT_TEST");
        assertThat(Files.readString(resolved.resolve("proxy.zip"))).isEqualTo("zip-data");
    }

    // ------------------------------------------------------------------
    // isComplete
    // ------------------------------------------------------------------

    @Test
    protected void contractIsCompleteReturnsFalseBeforeCommit() throws IOException {
        // Construct a location that was never committed.
        // Use a deterministic write so we know the path, then abandon it.
        final Path tempPath;
        try (final FileStoreWrite write = store.newDeterministicWrite("never-committed")) {
            tempPath = write.getPath();
            Files.writeString(tempPath.resolve("data.txt"), "abandoned");
            // Don't commit — close cleans up.
        }

        // Create a location as if it had been committed to verify isComplete
        // returns false for any non-existent location.
        final FileStoreLocation fakeLoc = createFakeLocation("never-committed-check");
        assertThat(store.isComplete(fakeLoc)).isFalse();
    }

    @Test
    protected void contractIsCompleteReturnsTrueAfterCommit() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "committed");
            location = write.commit();
        }

        assertThat(store.isComplete(location)).isTrue();
    }

    // ------------------------------------------------------------------
    // Uncommitted cleanup
    // ------------------------------------------------------------------

    @Test
    protected void contractUncommittedWriteCleanedUpOnClose() throws IOException {
        final Path stagingPath;
        try (final FileStoreWrite write = store.newWrite()) {
            stagingPath = write.getPath();
            Files.writeString(stagingPath.resolve("data.txt"), "uncommitted");
            // Don't commit — close should clean up.
        }

        assertThat(stagingPath).doesNotExist();
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Test
    protected void contractDeleteMakesIsCompleteReturnFalse() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "to-delete");
            location = write.commit();
        }

        assertThat(store.isComplete(location)).isTrue();

        store.delete(location);

        assertThat(store.isComplete(location)).isFalse();
    }

    @Test
    protected void contractDeleteIsIdempotent() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "delete-twice");
            location = write.commit();
        }

        store.delete(location);
        // Second delete must not throw.
        store.delete(location);
    }

    // ------------------------------------------------------------------
    // Deterministic writes
    // ------------------------------------------------------------------

    @Test
    protected void contractDeterministicWriteNewFileGroup() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newDeterministicWrite("det-new-001")) {
            assertThat(write.isCommitted()).isFalse();
            Files.writeString(write.getPath().resolve("data.txt"), "deterministic");
            location = write.commit();
        }

        assertThat(store.isComplete(location)).isTrue();
        final Path resolved = store.resolve(location);
        assertThat(Files.readString(resolved.resolve("data.txt"))).isEqualTo("deterministic");
    }

    @Test
    protected void contractDeterministicWriteSkipsWhenAlreadyComplete() throws IOException {
        final String fileGroupId = "det-skip-001";

        // First write.
        final FileStoreLocation location1;
        try (final FileStoreWrite write = store.newDeterministicWrite(fileGroupId)) {
            Files.writeString(write.getPath().resolve("data.txt"), "first");
            location1 = write.commit();
        }

        // Second write with same ID — should be pre-committed.
        final FileStoreLocation location2;
        try (final FileStoreWrite write = store.newDeterministicWrite(fileGroupId)) {
            assertThat(write.isCommitted())
                    .as("Second deterministic write should be pre-committed")
                    .isTrue();
            location2 = write.commit();
        }

        assertThat(location1.uri()).isEqualTo(location2.uri());

        // Original content should be preserved.
        final Path resolved = store.resolve(location2);
        assertThat(Files.readString(resolved.resolve("data.txt"))).isEqualTo("first");
    }

    @Test
    protected void contractDeterministicWriteCleansUpPartialOnRetry() throws IOException {
        final String fileGroupId = "det-partial-001";

        // Simulate a partial write that left a directory without a completion marker.
        // First, do a deterministic write but don't commit.
        try (final FileStoreWrite write = store.newDeterministicWrite(fileGroupId)) {
            Files.writeString(write.getPath().resolve("stale.txt"), "stale-data");
            // Don't commit — close cleans up.
        }

        // Now do a proper deterministic write — should NOT be pre-committed.
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newDeterministicWrite(fileGroupId)) {
            assertThat(write.isCommitted()).isFalse();
            Files.writeString(write.getPath().resolve("fresh.txt"), "fresh-data");
            location = write.commit();
        }

        final Path resolved = store.resolve(location);
        assertThat(Files.readString(resolved.resolve("fresh.txt"))).isEqualTo("fresh-data");
        assertThat(store.isComplete(location)).isTrue();
    }

    @Test
    protected void contractDeterministicWriteUncommittedCleansUp() throws IOException {
        final Path tempPath;
        try (final FileStoreWrite write = store.newDeterministicWrite("det-abandon-001")) {
            tempPath = write.getPath();
            Files.writeString(tempPath.resolve("data.txt"), "abandoned");
            // Don't commit.
        }

        assertThat(tempPath).doesNotExist();
    }

    // ------------------------------------------------------------------
    // Commit idempotency
    // ------------------------------------------------------------------

    @Test
    protected void contractCommitIsIdempotent() throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "commit-twice");
            final FileStoreLocation loc1 = write.commit();
            final FileStoreLocation loc2 = write.commit();

            assertThat(loc1).isEqualTo(loc2);
        }
    }

    // ------------------------------------------------------------------
    // Resolve validation
    // ------------------------------------------------------------------

    @Test
    protected void contractResolveRejectsMismatchedStoreName() {
        // Create a location referencing a different store.
        final FileStoreLocation badLocation = FileStoreLocation.localFileSystem(
                "wrongStoreName",
                Path.of("/tmp/fake/path"));

        assertThatThrownBy(() -> store.resolve(badLocation))
                .isInstanceOf(IOException.class);
    }

    // ------------------------------------------------------------------
    // Delete + re-write cycle
    // ------------------------------------------------------------------

    @Test
    protected void contractDeleteAndRewriteCycle() throws IOException {
        final String fileGroupId = "det-cycle-001";

        // Write and commit.
        final FileStoreLocation location1;
        try (final FileStoreWrite write = store.newDeterministicWrite(fileGroupId)) {
            Files.writeString(write.getPath().resolve("data.txt"), "v1");
            location1 = write.commit();
        }
        assertThat(store.isComplete(location1)).isTrue();

        // Delete.
        store.delete(location1);
        assertThat(store.isComplete(location1)).isFalse();

        // Re-write with same ID — should NOT be pre-committed.
        final FileStoreLocation location2;
        try (final FileStoreWrite write = store.newDeterministicWrite(fileGroupId)) {
            assertThat(write.isCommitted()).isFalse();
            Files.writeString(write.getPath().resolve("data.txt"), "v2");
            location2 = write.commit();
        }

        final Path resolved = store.resolve(location2);
        assertThat(Files.readString(resolved.resolve("data.txt"))).isEqualTo("v2");
        assertThat(store.isComplete(location2)).isTrue();
    }

    // ------------------------------------------------------------------
    // Health check
    // ------------------------------------------------------------------

    @Test
    protected void contractHealthCheckReturnsResult() {
        final HealthCheck.Result result = store.healthCheck();
        assertThat(result).isNotNull();
        assertThat(result.isHealthy()).isTrue();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Create a fake {@link FileStoreLocation} for testing isComplete on
     * non-existent locations. Subclasses can override if their store type
     * requires a specific location format.
     */
    protected FileStoreLocation createFakeLocation(final String id) {
        return FileStoreLocation.localFileSystem(
                STORE_NAME,
                getCurrentTestDir().resolve("nonexistent").resolve(id));
    }
}
