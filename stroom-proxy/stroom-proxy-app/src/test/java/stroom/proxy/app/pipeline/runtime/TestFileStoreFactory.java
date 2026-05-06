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

package stroom.proxy.app.pipeline.runtime;

import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.local.LocalFileStore;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFileStoreFactory extends StroomUnitTest {

    @Test
    void testCreatesLocalFileStoreForExplicitPath() {
        final FileStoreFactory factory = new FileStoreFactory(
                Map.of("receiveStore", new FileStoreDefinition("stores/receive")),
                new TestPathCreator(getCurrentTestDir()));

        final FileStore fileStore = factory.getFileStore("receiveStore");

        assertThat(fileStore).isInstanceOf(LocalFileStore.class);
        assertThat(fileStore.getName()).isEqualTo("receiveStore");
        assertThat(((LocalFileStore) fileStore).getRoot())
                .isEqualTo(getCurrentTestDir().resolve("stores/receive").normalize());
    }

    @Test
    void testCreatesLocalFileStoreForDefaultDerivedPath() {
        final FileStoreFactory factory = new FileStoreFactory(
                Map.of("aggregateStore", new FileStoreDefinition()),
                new TestPathCreator(getCurrentTestDir()));

        final FileStore fileStore = factory.getFileStore("aggregateStore");

        assertThat(fileStore).isInstanceOf(LocalFileStore.class);
        assertThat(fileStore.getName()).isEqualTo("aggregateStore");
        assertThat(((LocalFileStore) fileStore).getRoot())
                .isEqualTo(getCurrentTestDir().resolve("data/pipeline/file-stores/aggregateStore").normalize());
    }

    @Test
    void testCreatesFileStoresFromPipelineConfigDefaults() {
        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig();
        final FileStoreFactory factory = new FileStoreFactory(
                pipelineConfig,
                new TestPathCreator(getCurrentTestDir()));

        final FileStore receiveStore = factory.getFileStore(ProxyPipelineConfig.RECEIVE_STORE);
        final FileStore splitStore = factory.getFileStore(ProxyPipelineConfig.SPLIT_STORE);
        final FileStore preAggregateStore = factory.getFileStore(ProxyPipelineConfig.PRE_AGGREGATE_STORE);
        final FileStore aggregateStore = factory.getFileStore(ProxyPipelineConfig.AGGREGATE_STORE);

        assertThat(receiveStore).isInstanceOf(LocalFileStore.class);
        assertThat(splitStore).isInstanceOf(LocalFileStore.class);
        assertThat(preAggregateStore).isInstanceOf(LocalFileStore.class);
        assertThat(aggregateStore).isInstanceOf(LocalFileStore.class);

        assertThat(receiveStore.getName()).isEqualTo(ProxyPipelineConfig.RECEIVE_STORE);
        assertThat(splitStore.getName()).isEqualTo(ProxyPipelineConfig.SPLIT_STORE);
        assertThat(preAggregateStore.getName()).isEqualTo(ProxyPipelineConfig.PRE_AGGREGATE_STORE);
        assertThat(aggregateStore.getName()).isEqualTo(ProxyPipelineConfig.AGGREGATE_STORE);
    }

    @Test
    void testCachesFileStoreInstancesByLogicalName() {
        final FileStoreFactory factory = new FileStoreFactory(
                Map.of("receiveStore", new FileStoreDefinition("stores/receive")),
                new TestPathCreator(getCurrentTestDir()));

        final FileStore first = factory.getFileStore("receiveStore");
        final FileStore second = factory.getFileStore("receiveStore");

        assertThat(second).isSameAs(first);
    }

    @Test
    void testDifferentLogicalNamesCreateDifferentFileStores() {
        final FileStoreFactory factory = new FileStoreFactory(
                Map.of(
                        "receiveStore", new FileStoreDefinition("stores/receive"),
                        "aggregateStore", new FileStoreDefinition("stores/aggregate")),
                new TestPathCreator(getCurrentTestDir()));

        final FileStore receiveStore = factory.getFileStore("receiveStore");
        final FileStore aggregateStore = factory.getFileStore("aggregateStore");

        assertThat(receiveStore).isNotSameAs(aggregateStore);
        assertThat(receiveStore.getName()).isEqualTo("receiveStore");
        assertThat(aggregateStore.getName()).isEqualTo("aggregateStore");
        assertThat(((LocalFileStore) receiveStore).getRoot())
                .isEqualTo(getCurrentTestDir().resolve("stores/receive").normalize());
        assertThat(((LocalFileStore) aggregateStore).getRoot())
                .isEqualTo(getCurrentTestDir().resolve("stores/aggregate").normalize());
    }

    @Test
    void testHasFileStore() {
        final FileStoreFactory factory = new FileStoreFactory(
                Map.of("receiveStore", new FileStoreDefinition("stores/receive")),
                new TestPathCreator(getCurrentTestDir()));

        assertThat(factory.hasFileStore("receiveStore")).isTrue();
        assertThat(factory.hasFileStore("missingStore")).isFalse();
    }

    @Test
    void testGetFileStoreDefinitions() {
        final Map<String, FileStoreDefinition> definitions = Map.of(
                "receiveStore", new FileStoreDefinition("stores/receive"),
                "aggregateStore", new FileStoreDefinition("stores/aggregate"));
        final FileStoreFactory factory = new FileStoreFactory(
                definitions,
                new TestPathCreator(getCurrentTestDir()));

        assertThat(factory.getFileStoreDefinitions())
                .containsOnlyKeys("receiveStore", "aggregateStore");
        assertThat(factory.getFileStoreDefinitions().get("receiveStore").getPath())
                .isEqualTo("stores/receive");
        assertThat(factory.getFileStoreDefinitions().get("aggregateStore").getPath())
                .isEqualTo("stores/aggregate");
    }

    @Test
    void testRejectsUnknownFileStoreName() {
        final FileStoreFactory factory = new FileStoreFactory(
                Map.of("receiveStore", new FileStoreDefinition("stores/receive")),
                new TestPathCreator(getCurrentTestDir()));

        assertThatThrownBy(() -> factory.getFileStore("unknownStore"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknownStore");
    }

    @Test
    void testRejectsBlankFileStoreName() {
        final FileStoreFactory factory = new FileStoreFactory(
                Map.of("receiveStore", new FileStoreDefinition("stores/receive")),
                new TestPathCreator(getCurrentTestDir()));

        assertThatThrownBy(() -> factory.getFileStore(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileStoreName");
    }

    @Test
    void testCreatedStoreCanWriteAndResolveLocations() throws IOException {
        final FileStoreFactory factory = new FileStoreFactory(
                Map.of("receiveStore", new FileStoreDefinition("stores/receive")),
                new TestPathCreator(getCurrentTestDir()));
        final FileStore fileStore = factory.getFileStore("receiveStore");

        final FileStoreLocation location;
        try (final FileStoreWrite write = fileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta");
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip");
            Files.writeString(write.getPath().resolve("proxy.entries"), "entries");

            location = write.commit();
        }

        final Path resolvedPath = fileStore.resolve(location);

        assertThat(location.storeName()).isEqualTo("receiveStore");
        assertThat(location.locationType()).isEqualTo(FileStoreLocation.LocationType.LOCAL_FILESYSTEM);
        assertThat(resolvedPath).exists().isDirectory();
        assertThat(resolvedPath.resolve("proxy.meta")).hasContent("meta");
        assertThat(resolvedPath.resolve("proxy.zip")).hasContent("zip");
        assertThat(resolvedPath.resolve("proxy.entries")).hasContent("entries");
    }

    private static final class TestPathCreator implements PathCreator {

        private final Path root;

        private TestPathCreator(final Path root) {
            this.root = root;
        }

        @Override
        public String replaceTimeVars(final String path) {
            return path;
        }

        @Override
        public String replaceTimeVars(final String path,
                                      final ZonedDateTime dateTime) {
            return path;
        }

        @Override
        public String replaceSystemProperties(final String path) {
            return path;
        }

        @Override
        public Path toAppPath(final String pathString) {
            final Path path = Path.of(pathString);
            if (path.isAbsolute()) {
                return path.normalize();
            }
            return root.resolve(path).normalize();
        }

        @Override
        public String replaceUUIDVars(final String path) {
            return path;
        }

        @Override
        public String replaceFileName(final String path,
                                      final String fileName) {
            return path;
        }

        @Override
        public String[] findVars(final String path) {
            return new String[0];
        }

        @Override
        public boolean containsVars(final String path) {
            return false;
        }

        @Override
        public String replace(final String path,
                              final String var,
                              final LongSupplier replacementSupplier,
                              final int pad) {
            return path;
        }

        @Override
        public String replace(final String str,
                              final String var,
                              final Supplier<String> replacementSupplier) {
            return str;
        }

        @Override
        public String replaceAll(final String path) {
            return path;
        }

        @Override
        public String replaceContextVars(final String path) {
            return path;
        }
    }
}
