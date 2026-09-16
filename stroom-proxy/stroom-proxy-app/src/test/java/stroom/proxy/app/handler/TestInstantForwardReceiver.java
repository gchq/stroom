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
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.StroomStreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestInstantForwardReceiver {

    private static final byte[] BODY = "hello".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    @Test
    void testAnHttpDestinationGetsTheBodyStreamedAndTheReceiptIsLogged() throws Exception {
        final HttpSender httpSender = Mockito.mock(HttpSender.class);
        final AtomicReference<byte[]> sent = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            sent.set(invocation.getArgument(1, InputStream.class).readAllBytes());
            return null;
        }).when(httpSender).send(ArgumentMatchers.any(), ArgumentMatchers.any());
        final LogStream logStream = Mockito.mock(LogStream.class);
        final InstantForwardReceiver receiver = InstantForwardReceiver.toHttp(httpSender, filter(true), logStream);

        receiver.receive(Instant.now(), headers(), "test", () -> new ByteArrayInputStream(BODY));

        assertThat(sent.get()).isEqualTo(BODY);
        verifyLogged(logStream, EventType.RECEIVE, BODY.length);
    }

    @Test
    void testADownstreamRefusalReachesTheSenderWithTheDownstreamsCode() throws Exception {
        final HttpSender httpSender = Mockito.mock(HttpSender.class);
        Mockito.doThrow(ForwardException.nonRecoverable(
                        StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, headers(), "refused", null))
                .when(httpSender).send(ArgumentMatchers.any(), ArgumentMatchers.any());
        final InstantForwardReceiver receiver = InstantForwardReceiver.toHttp(
                httpSender, filter(true), Mockito.mock(LogStream.class));

        assertThatThrownBy(() -> receiver.receive(Instant.now(), headers(), "test",
                () -> new ByteArrayInputStream(BODY)))
                .isInstanceOf(StroomStreamException.class)
                .extracting(e -> ((StroomStreamException) e).getStroomStatusCode())
                .isEqualTo(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA);
    }

    @Test
    void testADroppedBodyIsDrainedNotForwarded() throws Exception {
        final HttpSender httpSender = Mockito.mock(HttpSender.class);
        final LogStream logStream = Mockito.mock(LogStream.class);
        final InstantForwardReceiver receiver = InstantForwardReceiver.toHttp(httpSender, filter(false), logStream);

        receiver.receive(Instant.now(), headers(), "test", () -> new ByteArrayInputStream(BODY));

        Mockito.verify(httpSender, Mockito.never()).send(ArgumentMatchers.any(), ArgumentMatchers.any());
        verifyLogged(logStream, EventType.DROP, BODY.length);
    }

    @Test
    void testAFileDestinationIsGivenADirectoryWithTheBodyAndItsHeaders() throws Exception {
        final Destination destination = Mockito.mock(Destination.class);
        final ArgumentCaptor<Path> added = ArgumentCaptor.forClass(Path.class);
        final AtomicReference<AttributeMap> metaSeen = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            final Path given = invocation.getArgument(0, Path.class);
            assertThat(given.resolve("data.dat")).hasBinaryContent(BODY);
            final AttributeMap meta = new AttributeMap();
            AttributeMapUtil.read(given.resolve("meta.meta"), meta);
            metaSeen.set(meta);
            return null;
        }).when(destination).deliver(added.capture());
        final Path receivingDir = dir.resolve("receiving");
        final InstantForwardReceiver receiver = InstantForwardReceiver.toFile(
                destination, receivingDir, filter(true), Mockito.mock(LogStream.class));

        receiver.receive(Instant.now(), headers(), "test", () -> new ByteArrayInputStream(BODY));

        assertThat(added.getValue()).hasParent(receivingDir);
        assertThat(metaSeen.get().get(StandardHeaderArguments.FEED)).isEqualTo("FEED_A");
    }

    @Test
    void testADirectoryTheDestinationRefusesIsRemovedAndTheSenderToldWhy() throws Exception {
        final Destination destination = Mockito.mock(Destination.class);
        Mockito.doThrow(new RuntimeException("destination full")).when(destination).deliver(ArgumentMatchers.any());
        final Path receivingDir = dir.resolve("receiving");
        final InstantForwardReceiver receiver = InstantForwardReceiver.toFile(
                destination, receivingDir, filter(true), Mockito.mock(LogStream.class));

        assertThatThrownBy(() -> receiver.receive(Instant.now(), headers(), "test",
                () -> new ByteArrayInputStream(BODY)))
                .isInstanceOf(StroomStreamException.class)
                .hasMessageContaining("destination full");

        assertThat(receivingDir).isEmptyDirectory();
    }

    @Test
    void testAZipOnDiskIsRelayedAsAZip() throws Exception {
        final Path zipFile = dir.resolve("scanned.zip");
        Files.write(zipFile, BODY);
        final Destination destination = Mockito.mock(Destination.class);
        final AtomicReference<String> compression = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            final Path given = invocation.getArgument(0, Path.class);
            assertThat(given.resolve("data.zip")).hasBinaryContent(BODY);
            final AttributeMap meta = new AttributeMap();
            AttributeMapUtil.read(given.resolve("meta.meta"), meta);
            compression.set(meta.get(StandardHeaderArguments.COMPRESSION));
            return null;
        }).when(destination).deliver(ArgumentMatchers.any());
        final InstantForwardReceiver receiver = InstantForwardReceiver.toFile(
                destination, dir.resolve("receiving"), filter(true), Mockito.mock(LogStream.class));

        receiver.receiveZip(Instant.now(), headers(), "file://x", zipFile);

        assertThat(compression.get()).isEqualTo(StandardHeaderArguments.COMPRESSION_ZIP);
        assertThat(zipFile).as("the caller keeps its file").exists();
    }

    private static AttributeMapFilterFactory filter(final boolean allow) {
        final AttributeMapFilterFactory factory = Mockito.mock(AttributeMapFilterFactory.class);
        final AttributeMapFilter filter = attributeMap -> allow;
        Mockito.when(factory.create()).thenReturn(filter);
        return factory;
    }

    private static AttributeMap headers() {
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, "FEED_A", "Raw Events");
        attributeMap.put(StandardHeaderArguments.RECEIPT_ID, "rid-1");
        return attributeMap;
    }

    private static void verifyLogged(final LogStream logStream, final EventType eventType, final long bytes) {
        Mockito.verify(logStream).log(
                ArgumentMatchers.any(Logger.class),
                ArgumentMatchers.any(AttributeMap.class),
                ArgumentMatchers.eq(eventType),
                ArgumentMatchers.eq("test"),
                ArgumentMatchers.eq(StroomStatusCode.OK),
                ArgumentMatchers.eq("rid-1"),
                ArgumentMatchers.eq(bytes),
                ArgumentMatchers.anyLong());
    }
}
