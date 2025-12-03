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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.ProxyConfig;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.FileUtil;
import stroom.util.shared.NullSafe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestForwarder {

    @Mock
    private ForwardHttpPostDestinationFactory mockForwardHttpDestFactory;
    @Mock
    private ForwardFileDestinationFactory mockForwardFileDestFactory;
    @Mock
    private CleanupDirQueue mockCleanupDirQueue;

    private Path dataDir = null;
    private Path sourcesDir = null;

    @BeforeEach
    void setUp(@TempDir final Path dataDir) {
        this.dataDir = dataDir;
        this.sourcesDir = dataDir.resolve("sources");
    }

    @Test
    void testAdd_singleFileForwarder(@TempDir final Path dataDir) {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .forwardFileDestinations(List.of(buildForwardFileConfig(dataDir, 1)))
                .forwardHttpDestinations(Collections.emptyList())
                .build();

        final ForwardFileDestination mockForwardFileDestination = Mockito.mock(ForwardFileDestination.class);

        Mockito.when(mockForwardFileDestFactory.create(Mockito.any()))
                .thenReturn(mockForwardFileDestination);

        final ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        Mockito.doNothing()
                .when(mockForwardFileDestination).add(pathCaptor.capture());

        final Forwarder forwarder = new Forwarder(
                () -> dataDir,
                () -> proxyConfig,
                mockForwardFileDestFactory,
                mockForwardHttpDestFactory,
                mockCleanupDirQueue);

        final Path sourceDir1 = createSourceDir(1);

        forwarder.add(sourceDir1);

        assertThat(pathCaptor.getAllValues())
                .hasSize(1);
        assertThat(pathCaptor.getValue())
                .isEqualTo(sourceDir1);
    }

    @Test
    void testAdd_multipleFileForwarders(@TempDir final Path dataDir) {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .forwardFileDestinations(List.of(
                        buildForwardFileConfig(dataDir, 1),
                        buildForwardFileConfig(dataDir, 2),
                        buildForwardFileConfig(dataDir, 3)))
                .forwardHttpDestinations(Collections.emptyList())
                .build();

        final List<ForwardFileDestination> forwardFileDestinations = new ArrayList<>();
        final List<ArgumentCaptor<Path>> pathCaptors = new ArrayList<>();
        final int destCount = 3;

        for (int i = 0; i < destCount; i++) {
            final ForwardFileDestination mockForwardFileDestination = Mockito.mock(ForwardFileDestination.class);
            forwardFileDestinations.add(mockForwardFileDestination);
            final ArgumentCaptor<Path> pathArgumentCaptor = ArgumentCaptor.forClass(Path.class);
            pathCaptors.add(pathArgumentCaptor);
        }

        Mockito.when(mockForwardFileDestFactory.create(Mockito.any()))
                .thenReturn(
                        forwardFileDestinations.get(0),
                        forwardFileDestinations.get(1),
                        forwardFileDestinations.get(2));

        final Forwarder forwarder = new Forwarder(
                () -> dataDir,
                () -> proxyConfig,
                mockForwardFileDestFactory,
                mockForwardHttpDestFactory,
                mockCleanupDirQueue);

        final Path sourceDir1 = createSourceDir(1);
        final Snapshot sourceDir1Snapshot = DirectorySnapshot.of(sourceDir1);
        final Path sourceDir2 = createSourceDir(2);
        final Snapshot sourceDir2Snapshot = DirectorySnapshot.of(sourceDir2);

        // Forward two source dirs. Each one should be sent to all forward destinations
        forwarder.add(sourceDir1);
        forwarder.add(sourceDir2);

        // Check what has been passed to each of the 3 destinations
        for (int i = 0; i < destCount; i++) {
            Mockito.verify(forwardFileDestinations.get(i), Mockito.times(2))
                    .add(pathCaptors.get(i).capture());

            final ArgumentCaptor<Path> pathCaptor = pathCaptors.get(i);
            assertThat(pathCaptor.getAllValues())
                    .hasSize(2);

            // Each dir passed to the forwarder should have exactly the same content as the source
            final Path destDir1 = pathCaptor.getAllValues().get(0);
            final Snapshot destDir1Snapshot = DirectorySnapshot.of(destDir1);
            final Path destDir2 = pathCaptor.getAllValues().get(1);
            final Snapshot destDir2Snapshot = DirectorySnapshot.of(destDir2);

            assertThat(destDir1Snapshot)
                    .isEqualTo(sourceDir1Snapshot);
            assertThat(destDir2Snapshot)
                    .isEqualTo(sourceDir2Snapshot);
        }
    }

    private Path createSourceDir(final int num) {
        return createSourceDir(num, null);
    }

    private Path createSourceDir(final int num, final Map<String, String> attrs) {
        final Path sourceDir = sourcesDir.resolve("source_" + num);
        FileUtil.ensureDirExists(sourceDir);
        assertThat(sourceDir)
                .isDirectory()
                .exists();

        final FileGroup fileGroup = new FileGroup(sourceDir);
        fileGroup.items()
                .forEach(ThrowingConsumer.unchecked(FileUtil::touch));

        try {
            if (NullSafe.hasEntries(attrs)) {
                final Path meta = fileGroup.getMeta();
                final AttributeMap attributeMap = new AttributeMap(attrs);
                AttributeMapUtil.write(attributeMap, meta);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return sourceDir;
    }

    private ForwardFileConfig buildForwardFileConfig(final Path dataDir, final int num) {
        return ForwardFileConfig.builder()
                .enabled()
                .withInstant(false)
                .withName("FileForwarder" + num)
                .withPath(dataDir.resolve("forward_dest_" + num).toString())
                .withSubPathTemplate(null)
                .build();
    }
}
