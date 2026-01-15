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
import stroom.proxy.app.DownstreamHostConfig;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestForwardHttpPostDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestForwardHttpPostDestination.class);

    @Mock
    private StreamDestination mockStreamDestination;

    private Path dataDir;
    private Path sourcesDir;
    private CleanupDirQueue cleanupDirQueue;

    @BeforeEach
    void setUp(@TempDir final Path baseDir) {
        this.dataDir = baseDir.resolve("data");
        this.sourcesDir = baseDir.resolve("sources");
        this.cleanupDirQueue = new CleanupDirQueue(this::getDataDir);
    }

    @Test
    void test_success() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .build();
        final DownstreamHostConfig downstreamHostConfig = new DownstreamHostConfig();
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig,
                downstreamHostConfig);

        final CountDownLatch sentLatch = new CountDownLatch(1);
        Mockito.doAnswer(
                        invocation -> {
                            LOGGER.info("send called");
                            sentLatch.countDown();
                            return null;
                        })
                .when(mockStreamDestination).send(Mockito.any(), Mockito.any());

        final Path source1 = createSourceDir(1);
        forwardHttpPostDestination.add(source1);

        final boolean didCountDown = sentLatch.await(5, TimeUnit.SECONDS);
        assertThat(didCountDown)
                .isTrue();

        // Success so the source is gone
        Assertions.assertThat(source1)
                .doesNotExist();
        Assertions.assertThat(sourcesDir)
                .isEmptyDirectory();
    }

    @Test
    void test_Fail() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .build();
        final DownstreamHostConfig downstreamHostConfig = new DownstreamHostConfig();
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig,
                downstreamHostConfig);

        Mockito.doThrow(new RuntimeException("Send failed"))
                .when(mockStreamDestination).send(Mockito.any(), Mockito.any());

        final Path source1 = createSourceDir(1);

        Assertions.assertThatThrownBy(
                        () -> forwardHttpPostDestination.add(source1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Send failed");

        // Success so the source is gone
        Assertions.assertThat(source1)
                .exists()
                .isDirectory();
        Assertions.assertThat(sourcesDir)
                .isNotEmptyDirectory();
    }

    @Test
    void test_noLivenesscheck() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .livenessCheckUrl(null)
                .build();
        final DownstreamHostConfig downstreamHostConfig = new DownstreamHostConfig();
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig,
                downstreamHostConfig);

        Assertions.assertThat(forwardHttpPostDestination.hasLivenessCheck())
                .isFalse();
        assertLivenessCheck(forwardHttpPostDestination, true);
    }

    @Test
    void test_liveness_live() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .livenessCheckUrl("aUrl")
                .build();
        final DownstreamHostConfig downstreamHostConfig = new DownstreamHostConfig();
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig,
                downstreamHostConfig);

        Mockito.when(mockStreamDestination.hasLivenessCheck())
                .thenReturn(true);

        Assertions.assertThat(forwardHttpPostDestination.hasLivenessCheck())
                .isTrue();

        Mockito.when(mockStreamDestination.performLivenessCheck())
                .thenReturn(true);

        assertLivenessCheck(forwardHttpPostDestination, true);
    }

    @Test
    void test_liveness_notLive() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .livenessCheckUrl("aUrl")
                .build();
        final DownstreamHostConfig downstreamHostConfig = new DownstreamHostConfig();
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig,
                downstreamHostConfig);

        Mockito.when(mockStreamDestination.hasLivenessCheck())
                .thenReturn(true);

        Assertions.assertThat(forwardHttpPostDestination.hasLivenessCheck())
                .isTrue();

        Mockito.when(mockStreamDestination.performLivenessCheck())
                .thenThrow(new Exception("not live"));

        assertLivenessCheck(forwardHttpPostDestination, false);
    }

    private Path getDataDir() {
        return dataDir;
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

    private void assertLivenessCheck(final ForwardDestination forwardDestination, final boolean isLive) {
        try {
            Assertions.assertThat(forwardDestination.performLivenessCheck())
                    .isEqualTo(isLive);
        } catch (final Exception e) {
            if (isLive) {
                Assertions.fail(LogUtil.message("Expecting {} to be live", forwardDestination));
            }
        }
    }
}
