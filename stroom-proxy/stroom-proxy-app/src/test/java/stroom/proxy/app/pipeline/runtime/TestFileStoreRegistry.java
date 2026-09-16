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

import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFileStoreRegistry extends StroomUnitTest {

    private static final String STORE_NAME = "aggregateStore";

    @Test
    void testResolvesAMessageLocation() throws IOException {
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                STORE_NAME, getCurrentTestDir().resolve("aggregate-store"));
        final FileStoreLocation location = writeFileGroup(fileStore);
        final FileGroupQueueMessage message = createMessage(location);
        final FileStoreRegistry registry = new FileStoreRegistry().register(fileStore);

        final Path resolvedPath = registry.resolve(message);

        assertThat(resolvedPath).isEqualTo(fileStore.resolve(location));
        assertThat(resolvedPath).exists().isDirectory();
        assertThat(resolvedPath.resolve("proxy.meta")).hasContent("meta");
        assertThat(resolvedPath.resolve("proxy.zip")).hasContent("zip");
        assertThat(resolvedPath.resolve("proxy.entries")).hasContent("entries");
    }

    @Test
    void testRejectsAnUnknownStore() throws IOException {
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                STORE_NAME, getCurrentTestDir().resolve("aggregate-store"));
        final FileStoreLocation location = writeFileGroup(fileStore);
        final FileGroupQueueMessage message = createMessage(location);
        final FileStoreRegistry registry = new FileStoreRegistry();

        assertThatThrownBy(() -> registry.resolve(message))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(STORE_NAME);
    }

    @Test
    void testRejectsAMismatchedMapKey() {
        final FilesystemFileStore fileStore = new FilesystemFileStore(
                STORE_NAME, getCurrentTestDir().resolve("aggregate-store"));

        assertThatThrownBy(() -> new FileStoreRegistry(Map.of("wrongStore", fileStore)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrongStore")
                .hasMessageContaining(STORE_NAME);
    }

    private static FileStoreLocation writeFileGroup(final FileStore fileStore) throws IOException {
        try (final FileStoreWrite write = fileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta");
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip");
            Files.writeString(write.getPath().resolve("proxy.entries"), "entries");
            return write.commit();
        }
    }

    private static FileGroupQueueMessage createMessage(final FileStoreLocation location) {
        return FileGroupQueueMessage.create(location, "TEST_FEED", null, "aggregate", "proxy-node-1", "trace",
                Map.of());
    }
}
