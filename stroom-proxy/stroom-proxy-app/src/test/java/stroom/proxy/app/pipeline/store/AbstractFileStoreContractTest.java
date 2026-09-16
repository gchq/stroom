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

package stroom.proxy.app.pipeline.store;

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
 * The {@link FileStore} contract, run against every implementation. Each test is one clause of
 * {@code designs/infrastructure/file-stores.md} §3 that can be observed from outside the store.
 */
public abstract class AbstractFileStoreContractTest extends StroomUnitTest {

    private static final String STORE_NAME = "contractStore";

    private FileStore store;

    protected abstract FileStore createFileStore(String storeName, Path testRoot) throws IOException;

    @BeforeEach
    protected void setUpStore() throws IOException {
        store = createFileStore(STORE_NAME, getCurrentTestDir());
    }

    protected FileStore getStore() {
        return store;
    }

    @Test
    protected void contractCommitProducesAResolvableLocationCarryingTheStoreName() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "Feed:CONTRACT_TEST");
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip-data");
            location = write.commit();
        }

        assertThat(location.storeName()).isEqualTo(STORE_NAME);
        final Path resolved = store.resolve(location);
        assertThat(resolved).isDirectory();
        assertThat(resolved.resolve("proxy.meta")).hasContent("Feed:CONTRACT_TEST");
        assertThat(resolved.resolve("proxy.zip")).hasContent("zip-data");
    }

    /**
     * A store holds directory trees. The pre-aggregate stage writes a directory per part, and every
     * backend must round-trip that.
     */
    @Test
    protected void contractANestedGroupRoundTrips() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.createDirectories(write.getPath().resolve("001"));
            Files.createDirectories(write.getPath().resolve("002"));
            Files.writeString(write.getPath().resolve("001").resolve("proxy.zip"), "part-1");
            Files.writeString(write.getPath().resolve("002").resolve("proxy.zip"), "part-2");
            Files.writeString(write.getPath().resolve("proxy.meta"), "Feed:NESTED");
            location = write.commit();
        }

        final Path resolved = store.resolve(location);
        assertThat(resolved.resolve("001").resolve("proxy.zip")).hasContent("part-1");
        assertThat(resolved.resolve("002").resolve("proxy.zip")).hasContent("part-2");
        assertThat(resolved.resolve("proxy.meta")).hasContent("Feed:NESTED");
    }

    @Test
    protected void contractEachWriteGetsItsOwnLocation() throws IOException {
        final FileStoreLocation first;
        final FileStoreLocation second;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), "one");
            first = write.commit();
        }
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), "two");
            second = write.commit();
        }
        assertThat(first).isNotEqualTo(second);
        assertThat(store.resolve(first).resolve("proxy.zip")).hasContent("one");
        assertThat(store.resolve(second).resolve("proxy.zip")).hasContent("two");
    }

    @Test
    protected void contractCommitIsIdempotent() throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "commit-twice");
            final FileStoreLocation loc1 = write.commit();
            final FileStoreLocation loc2 = write.commit();
            assertThat(loc1).isEqualTo(loc2);
        }
    }

    @Test
    protected void contractAnUncommittedWriteIsDiscardedOnClose() throws IOException {
        final Path stagingPath;
        try (final FileStoreWrite write = store.newWrite()) {
            stagingPath = write.getPath();
            Files.writeString(stagingPath.resolve("data.txt"), "uncommitted");
        }
        assertThat(stagingPath).doesNotExist();
    }

    /**
     * Absence is reported, never simulated: after a delete the store must throw rather than hand
     * back an empty directory, because a consumer reads absence as proof the work was done.
     */
    @Test
    protected void contractDeleteRemovesTheGroupAndResolveThenReportsAbsence() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "to-delete");
            location = write.commit();
        }
        assertThat(store.resolve(location)).isDirectory();

        store.delete(location);

        assertThatThrownBy(() -> store.resolve(location))
                .isInstanceOf(FileGroupNotFoundException.class);
    }

    @Test
    protected void contractDeleteIsIdempotent() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("data.txt"), "delete-twice");
            location = write.commit();
        }
        store.delete(location);
        store.delete(location);
    }

    @Test
    protected void contractDeleteOfOneGroupLeavesAnotherIntact() throws IOException {
        final FileStoreLocation keep;
        final FileStoreLocation drop;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), "keep");
            keep = write.commit();
        }
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), "drop");
            drop = write.commit();
        }

        store.delete(drop);

        assertThat(store.resolve(keep).resolve("proxy.zip")).hasContent("keep");
    }

    @Test
    protected void contractAStoreRejectsALocationForAnotherStore() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), "mine");
            location = write.commit();
        }
        final FileStoreLocation other = new FileStoreLocation("someOtherStore", location.uri());

        assertThatThrownBy(() -> store.resolve(other)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> store.delete(other)).isInstanceOf(IOException.class);
    }

    @Test
    protected void contractHealthCheckIsHealthy() {
        final HealthCheck.Result result = store.healthCheck();
        assertThat(result.isHealthy()).as(result.getMessage()).isTrue();
    }
}
