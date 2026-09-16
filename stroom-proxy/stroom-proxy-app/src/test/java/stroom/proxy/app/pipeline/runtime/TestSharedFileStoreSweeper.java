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

import stroom.proxy.app.handler.Durability;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreType;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSharedFileStoreSweeper extends StroomUnitTest {

    @Test
    void testOnlySharedFilesystemStoresAreSwept() throws IOException {
        final FilesystemFileStore local = new FilesystemFileStore("local", getCurrentTestDir().resolve("local"));
        final FilesystemFileStore shared = new FilesystemFileStore("shared", getCurrentTestDir().resolve("shared"),
                FileStoreType.SHARED_FILESYSTEM, Durability.FILESYSTEM, Duration.ofHours(1));
        final FileStoreLocation localGroup = commit(local);
        final FileStoreLocation sharedGroup = commit(shared);
        ageTree(local.getRoot());
        ageTree(shared.getRoot());

        final SharedFileStoreSweeper sweeper = SharedFileStoreSweeper.forStores(List.of(local, shared));
        assertThat(sweeper.hasStores()).isTrue();
        sweeper.sweepAll();

        assertThat(local.resolve(localGroup)).as("a local store is never swept by age").exists();
        assertThatThrownBy(() -> shared.resolve(sharedGroup)).isInstanceOf(FileGroupNotFoundException.class);
        assertThat(SharedFileStoreSweeper.forStores(List.of(local)).hasStores()).isFalse();
    }

    private static FileStoreLocation commit(final FilesystemFileStore store) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), "x");
            return write.commit();
        }
    }

    private static void ageTree(final Path root) throws IOException {
        try (final Stream<Path> walk = Files.walk(root)) {
            for (final Path path : walk.toList()) {
                Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
            }
        }
    }
}
