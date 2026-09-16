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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestHttpDestination {

    private StreamDestination sender;
    private HttpDestination destination;
    private Path sourcesDir;

    @BeforeEach
    void setUp(@TempDir final Path baseDir) {
        sender = Mockito.mock(StreamDestination.class);
        destination = new HttpDestination("TestDest", sender, "http://downstream/datafeed");
        sourcesDir = baseDir.resolve("sources");
    }

    @Test
    void testAnAcceptedPostReturnsAndLeavesTheGroupToTheCaller() throws Exception {
        final Path group = createGroup(1, Map.of("Feed", "TEST_FEED"));
        final ArgumentCaptor<AttributeMap> headers = ArgumentCaptor.forClass(AttributeMap.class);

        destination.deliver(group);

        Mockito.verify(sender).send(headers.capture(), Mockito.any(InputStream.class));
        assertThat(headers.getValue().get(StandardHeaderArguments.FEED)).isEqualTo("TEST_FEED");
        assertThat(headers.getValue().get(StandardHeaderArguments.COMPRESSION))
                .as("the downstream is always told it is receiving a zip")
                .isEqualTo(StandardHeaderArguments.COMPRESSION_ZIP);
        assertThat(group).as("the destination deletes nothing; that is the stage's").isDirectory();
    }

    @Test
    void testADownstreamRefusalIsRefused() throws Exception {
        final Path group = createGroup(1, Map.of("Feed", "TEST_FEED"));
        Mockito.doThrow(ForwardException.nonRecoverable(
                        StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, new AttributeMap(), "not set", null))
                .when(sender).send(Mockito.any(), Mockito.any());

        assertThatThrownBy(() -> destination.deliver(group))
                .isInstanceOf(Refused.class)
                .satisfies(e -> assertThat(((Refused) e).getStatus())
                        .isEqualTo(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA));
        assertThat(group).isDirectory();
    }

    @Test
    void testARecoverableFailureIsTransient() throws Exception {
        final Path group = createGroup(1, Map.of("Feed", "TEST_FEED"));
        Mockito.doThrow(ForwardException.recoverable(
                        StroomStatusCode.UNKNOWN_ERROR, new AttributeMap(), "connection refused", null))
                .when(sender).send(Mockito.any(), Mockito.any());

        assertThatThrownBy(() -> destination.deliver(group))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(Refused.class)
                .hasMessageContaining("connection refused");
        assertThat(group).isDirectory();
    }

    @Test
    void testAnUnclassifiedExceptionIsTransient() throws Exception {
        final Path group = createGroup(1, Map.of("Feed", "TEST_FEED"));
        Mockito.doThrow(new RuntimeException("boom"))
                .when(sender).send(Mockito.any(), Mockito.any());

        assertThatThrownBy(() -> destination.deliver(group))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(Refused.class);
    }

    @Test
    void testNoLivenessCheckWithoutAUrl() {
        Mockito.when(sender.hasLivenessCheck()).thenReturn(false);
        assertThat(destination.livenessCheck()).isEmpty();
    }

    @Test
    void testTheLivenessCheckPassesWhenTheSenderSaysLive() throws Exception {
        Mockito.when(sender.hasLivenessCheck()).thenReturn(true);
        Mockito.when(sender.performLivenessCheck()).thenReturn(true);
        destination.livenessCheck().orElseThrow().check();
    }

    @Test
    void testTheLivenessCheckFailsWhenTheSenderSaysNotLive() throws Exception {
        Mockito.when(sender.hasLivenessCheck()).thenReturn(true);
        Mockito.when(sender.performLivenessCheck()).thenThrow(new Exception("not live"));
        assertThatThrownBy(() -> destination.livenessCheck().orElseThrow().check())
                .hasMessageContaining("not live");
    }

    private Path createGroup(final int num, final Map<String, String> attrs) throws IOException {
        final Path sourceDir = sourcesDir.resolve("source_" + num);
        FileUtil.ensureDirExists(sourceDir);
        final FileGroup fileGroup = new FileGroup(sourceDir);
        Files.writeString(fileGroup.getZip(), "zip");
        Files.writeString(fileGroup.getEntries(), "");
        AttributeMapUtil.write(new AttributeMap(attrs), fileGroup.getMeta());
        return sourceDir;
    }
}
