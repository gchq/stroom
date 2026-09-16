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

package stroom.proxy.app.handler;

import stroom.aws.s3.client.S3ClientPool;
import stroom.aws.s3.client.S3MetaKeysMapper;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.cache.api.TemplateCache;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.pipeline.config.PipelineMode;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.repo.store.FileStores;
import stroom.util.io.SimplePathCreator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFailureDestinationFactory {

    private static final String DESTINATION_NAME = "myDest";

    @Test
    void testOmittingTheConfigKeepsTheLocalFailureDirectory(@TempDir final Path baseDir) {
        final Path forwardingDir = baseDir.resolve("forwarding");
        final Destination destination = create(baseDir, forwardingDir, null);
        assertThat(destination).isInstanceOf(FileDestination.class);
        assertThat(((FileDestination) destination).getStoreDir())
                .as("unchanged for every deployment that does not configure this")
                .isEqualTo(forwardingDir.resolve("03_failure"));
    }

    @Test
    void testAFileFailureDestinationCanBePutSomewhereElse(@TempDir final Path baseDir) {
        final Path elsewhere = baseDir.resolve("shared").resolve("quarantine");
        final FailureDestinationConfig config = new FailureDestinationConfig(
                DestinationType.FILE, elsewhere.toString(), null, null, null, null);
        final Destination destination = create(baseDir, baseDir.resolve("forwarding"), config);
        assertThat(destination).isInstanceOf(FileDestination.class);
        assertThat(((FileDestination) destination).getStoreDir())
                .isEqualTo(elsewhere);
    }

    @Test
    void testAFileForwarderCanQuarantineToS3(@TempDir final Path baseDir) {
        final FailureDestinationConfig config = new FailureDestinationConfig(
                DestinationType.S3, null, null, S3ClientConfig.builder().build(), null, null);
        final Destination destination = create(baseDir, baseDir.resolve("forwarding"), config);
        assertThat(destination)
                .as("the failure destination's type is independent of the forwarder's")
                .isInstanceOf(S3Destination.class);
    }

    @Test
    void testS3RequiresClientConfigAndOnlyFileOrS3AreAccepted() {
        assertThatThrownBy(() -> new FailureDestinationConfig(
                DestinationType.S3, null, null, null, null, null))
                .as("an S3 failure destination with no bucket to write to is not a destination")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s3Client");
        assertThatThrownBy(() -> new FailureDestinationConfig(
                DestinationType.HTTP, null, null, null, null, null))
                .as("HTTP cannot hold data for an operator to come back to")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * In shared mode the give-up directory is required to be shared, so every node gives up into it
     * and each must number under a writer root of its own, as the file stores do. Two destinations
     * built for the same directory - two nodes, or one node restarted - never share a tree; the
     * configured directory is still what the validator sees.
     */
    @Test
    void testInSharedModeEachProcessGivesUpUnderItsOwnWriterRoot(@TempDir final Path baseDir) {
        final Path shared = baseDir.resolve("mount").resolve("give-up");
        final FailureDestinationConfig config = new FailureDestinationConfig(
                DestinationType.FILE, shared.toString(), null, null, null, null);

        final FileDestination first = (FileDestination) create(baseDir, baseDir.resolve("fwd"), config,
                PipelineMode.SHARED);
        final FileDestination second = (FileDestination) create(baseDir, baseDir.resolve("fwd"), config,
                PipelineMode.SHARED);

        assertThat(first.getStoreDir()).isEqualTo(shared);
        assertThat(first.getWriterRoot().getParent()).isEqualTo(shared);
        assertThat(second.getWriterRoot())
                .as("a fresh writer root per process start")
                .isNotEqualTo(first.getWriterRoot());
        assertThat(first.getWriterRoot()).isDirectory();
    }

    @Test
    void testInLocalModeTheProcessGivesUpStraightIntoTheDirectory(@TempDir final Path baseDir) {
        final Path dir = baseDir.resolve("quarantine");
        final FailureDestinationConfig config = new FailureDestinationConfig(
                DestinationType.FILE, dir.toString(), null, null, null, null);
        final FileDestination destination = (FileDestination) create(baseDir, baseDir.resolve("fwd"), config,
                PipelineMode.LOCAL);
        assertThat(destination.getWriterRoot())
                .as("one process is the only writer, so it counts in the directory itself")
                .isEqualTo(dir);
    }

    private Destination create(final Path baseDir,
                               final Path forwardingDir,
                               final FailureDestinationConfig config) {
        return create(baseDir, forwardingDir, config, PipelineMode.LOCAL);
    }

    private Destination create(final Path baseDir,
                               final Path forwardingDir,
                               final FailureDestinationConfig config,
                               final PipelineMode mode) {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .pipelineConfig(new ProxyPipelineConfig(mode, null, null, null))
                .build();
        final FailureDestinationFactory factory = new FailureDestinationFactory(
                Mockito.mock(S3ClientPool.class),
                Mockito.mock(TemplateCache.class),
                Mockito.mock(S3MetaKeysMapper.class),
                Mockito.mock(RemoteS3EventClient.class),
                () -> proxyConfig);
        return factory.create(
                DESTINATION_NAME, forwardingDir, config, Mockito.mock(FileStores.class), 1,
                new SimplePathCreator(() -> baseDir.resolve("home"), () -> baseDir.resolve("temp")));
    }
}
