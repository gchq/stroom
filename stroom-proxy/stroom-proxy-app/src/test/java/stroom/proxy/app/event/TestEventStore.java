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

package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.InputStreamSupplier;
import stroom.proxy.app.handler.Receiver;
import stroom.proxy.app.handler.RefusingReceiver;
import stroom.proxy.repo.store.FileStores;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.CommonSecurityContext;
import stroom.security.mock.MockCommonSecurityContext;
import stroom.test.common.MockMetrics;
import stroom.util.concurrent.UniqueId;
import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.io.ByteSize;
import stroom.util.shared.FeedKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestEventStore {

    private static final FeedKey FEED_KEY = FeedKey.of("Test", "Raw Events");

    @TempDir
    Path dir;

    private Path eventDir;
    private Receiver receiver;
    private Predicate<AttributeMap> policy;
    private CommonSecurityContext securityContext;
    private ReceiveDataConfig receiveDataConfig;

    @BeforeEach
    void setUp() {
        eventDir = dir.resolve("event");
        receiver = Mockito.mock(Receiver.class);
        policy = attributeMap -> true;
        securityContext = MockCommonSecurityContext.getInstance();
        receiveDataConfig = ReceiveDataConfig.builder().build();
    }

    @Test
    void testAnAcceptedEventIsAppendedSerialised() throws IOException {
        final EventStore eventStore = eventStore(new EventStoreConfig());

        for (int i = 0; i < 10; i++) {
            assertThat(eventStore.accept(headers(), receiptId(i), "test")).isTrue();
        }
        eventStore.close();

        final String expected =
                "\"headers\":[{\"name\":\"Feed\",\"value\":\"Test\"},{\"name\":\"Type\",\"value\":\"Raw Events\"}],"
                + "\"detail\":\"test\"}";
        final String content = EventStoreTestUtil.read(eventDir, FEED_KEY);
        assertThat(content.lines()).hasSize(10);
        assertThat(content.lines()).allSatisfy(line -> assertThat(line).endsWith(expected));
    }

    @Test
    void testADroppedEventIsNotAppendedAndARejectedOneThrows() throws IOException {
        policy = attributeMap -> false;
        final EventStore dropping = eventStore(new EventStoreConfig());
        assertThat(dropping.accept(headers(), receiptId(0), "test")).isFalse();
        assertThat(files()).isEmpty();

        policy = attributeMap -> {
            throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, attributeMap);
        };
        final EventStore rejecting = eventStore(new EventStoreConfig());
        assertThatThrownBy(() -> rejecting.accept(headers(), receiptId(0), "test"))
                .isInstanceOf(StroomStreamException.class);
        assertThat(files()).isEmpty();
    }

    @Test
    void testADueFileIsReceivedAsTheProcessingUserWithTheFirstEventsHeadersThenDeleted() throws IOException {
        final AtomicBoolean inProcessingUser = new AtomicBoolean(false);
        final AtomicBoolean receivedAsProcessingUser = new AtomicBoolean(false);
        securityContext = Mockito.mock(CommonSecurityContext.class);
        Mockito.doAnswer(invocation -> {
            inProcessingUser.set(true);
            try {
                invocation.getArgument(0, Runnable.class).run();
            } finally {
                inProcessingUser.set(false);
            }
            return null;
        }).when(securityContext).asProcessingUser(Mockito.any(Runnable.class));
        final List<AttributeMap> seen = new java.util.ArrayList<>();
        final List<String> bodies = new java.util.ArrayList<>();
        Mockito.doAnswer(invocation -> {
            receivedAsProcessingUser.set(inProcessingUser.get());
            seen.add(invocation.getArgument(1, AttributeMap.class));
            try (final InputStream in = invocation.getArgument(3, InputStreamSupplier.class).get()) {
                bodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            return null;
        }).when(receiver).receive(Mockito.any(), Mockito.any(), Mockito.eq(EventStore.SOURCE), Mockito.any());

        // Every event fills its file, so the next accept closes it and the roll receives it.
        final EventStore eventStore = eventStore(new EventStoreConfig(null, null, 1L, null));
        final AttributeMap sent = headers();
        sent.put("Environment", "PROD");
        eventStore.accept(sent, receiptId(0), "first");
        eventStore.accept(headers(), receiptId(1), "second");

        eventStore.tryRoll();

        assertThat(receivedAsProcessingUser).isTrue();
        assertThat(seen).as("the older file first").hasSize(2);
        assertThat(seen.getFirst())
                .containsEntry("Environment", "PROD")
                .containsEntry(StandardHeaderArguments.FEED, FEED_KEY.feed())
                .containsEntry(StandardHeaderArguments.RECEIPT_ID, receiptId(0).toString());
        assertThat(bodies.getFirst()).contains("\"detail\":\"first\"");
        assertThat(files()).as("both files were due and received").isEmpty();
        Mockito.verify(receiver, Mockito.times(2)).receive(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testAFileAPreviousRunLeftBehindIsReceivedOnTheFirstTickAfterItsPartialLineIsTrimmed()
            throws IOException {
        Files.createDirectories(eventDir);
        final String whole = new EventSerialiser().serialise(receiptId(0), FEED_KEY, headers(), "whole") + "\n";
        final Path leftover = EventStoreFile.createNew(eventDir, FEED_KEY, Instant.now());
        Files.writeString(leftover, whole + "{\"version\":0,\"eventId\":\"torn");
        final AtomicReference<String> body = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            try (final InputStream in = invocation.getArgument(3, InputStreamSupplier.class).get()) {
                body.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            return null;
        }).when(receiver).receive(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        final EventStore eventStore = eventStore(new EventStoreConfig());
        assertThat(leftover).as("nothing is received at construction").exists();
        eventStore.tryRoll();

        assertThat(body.get()).isEqualTo(whole);
        assertThat(leftover).doesNotExist();
    }

    @Test
    void testAFileTheReceiverRefusesIsLeftUntilTheThirdRefusalQuarantinesIt() throws IOException {
        Mockito.doThrow(new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, headers()))
                .when(receiver).receive(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        final EventStore eventStore = eventStore(new EventStoreConfig(null, null, 1L, null));
        eventStore.accept(headers(), receiptId(0), "test");

        eventStore.tryRoll();
        eventStore.tryRoll();
        assertThat(files()).hasSize(1);
        assertThat(dir.resolve("event").resolve(EventStore.FAILED_DIR_NAME)).doesNotExist();

        eventStore.tryRoll();
        assertThat(files()).isEmpty();
        try (final Stream<Path> failed = Files.list(eventDir.resolve(EventStore.FAILED_DIR_NAME))) {
            assertThat(failed).hasSize(1);
        }
    }

    @Test
    void testAFileTheStoreCannotTakeWaitsForItRatherThanBeingQuarantined() throws IOException {
        Mockito.doThrow(new RuntimeException("store down"))
                .when(receiver).receive(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        final EventStore eventStore = eventStore(new EventStoreConfig(null, null, 1L, null));
        eventStore.accept(headers(), receiptId(0), "test");

        for (int i = 0; i < EventStore.MAX_REFUSALS + 2; i++) {
            eventStore.tryRoll();
        }
        assertThat(files()).as("still waiting").hasSize(1);
        assertThat(eventDir.resolve(EventStore.FAILED_DIR_NAME)).doesNotExist();

        Mockito.doNothing().when(receiver).receive(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        eventStore.tryRoll();
        assertThat(files()).isEmpty();
    }

    @Test
    void testAnOpenFileIsNeverSeenAsClosedAndCloseRenamesIt() throws IOException {
        final EventStore eventStore = eventStore(new EventStoreConfig());
        eventStore.accept(headers(), receiptId(0), "test");

        final List<Path> open = files();
        assertThat(open).hasSize(1);
        assertThat(open.getFirst().getFileName().toString()).endsWith(EventAppender.OPEN_SUFFIX);

        eventStore.tryRoll();
        Mockito.verifyNoInteractions(receiver);
        assertThat(files()).containsExactlyElementsOf(open);

        eventStore.close();
        assertThat(files()).hasSize(1);
        assertThat(files().getFirst().getFileName().toString()).endsWith(EventStoreFile.LOG_EXTENSION);
    }

    @Test
    void testAFileAPreviousRunLeftOpenIsClosedAtConstruction() throws IOException {
        Files.createDirectories(eventDir);
        final Path file = EventStoreFile.createNew(eventDir, FEED_KEY, Instant.now());
        final Path leftOpen = EventAppender.openFileOf(file);
        Files.writeString(leftOpen, new EventSerialiser().serialise(receiptId(0), FEED_KEY, headers(), "x") + "\n");

        final EventStore eventStore = eventStore(new EventStoreConfig());
        assertThat(leftOpen).doesNotExist();
        assertThat(file).exists();

        eventStore.tryRoll();
        assertThat(file).doesNotExist();
        Mockito.verify(receiver).receive(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testAClosedFileNeverExceedsWhatTheReceiverWillTake() throws IOException {
        receiveDataConfig = ReceiveDataConfig.builder().withMaxRequestSize(ByteSize.ofBytes(600)).build();
        final EventStore eventStore = eventStore(new EventStoreConfig());

        // Each serialised event is a couple of hundred bytes, so 600 holds two.
        for (int i = 0; i < 5; i++) {
            eventStore.accept(headers(), receiptId(i), "event");
        }

        assertThat(files()).hasSizeGreaterThanOrEqualTo(3);
        for (final Path file : files()) {
            assertThat(Files.size(file)).isLessThanOrEqualTo(600);
        }
    }

    @Test
    void testANodeThatDoesNotReceiveRefusesEvents() {
        receiver = new RefusingReceiver();
        final EventStore eventStore = eventStore(new EventStoreConfig());

        assertThatThrownBy(() -> eventStore.accept(headers(), receiptId(0), "test"))
                .isInstanceOf(StroomStreamException.class)
                .hasMessageContaining(RefusingReceiver.MESSAGE);
    }

    @Test
    void testAFileThatIsNotAnEventFileIsQuarantinedNotReplayedForEver() throws IOException {
        Files.createDirectories(eventDir);
        final Path bad = eventDir.resolve("not-an-event-file=20260908.log");
        Files.writeString(bad, "some events\n");
        final EventStore eventStore = eventStore(new EventStoreConfig());

        for (int i = 0; i < EventStore.MAX_REFUSALS; i++) {
            eventStore.tryRoll();
        }

        assertThat(bad).doesNotExist();
        assertThat(eventDir.resolve(EventStore.FAILED_DIR_NAME).resolve(bad.getFileName())).exists();
        Mockito.verifyNoInteractions(receiver);
    }

    @Test
    void testAnEmptyFileIsRemovedNotReceived() throws IOException {
        Files.createDirectories(eventDir);
        final Path empty = EventStoreFile.createNew(eventDir, FEED_KEY, Instant.now());
        Files.createFile(empty);
        final EventStore eventStore = eventStore(new EventStoreConfig());

        eventStore.tryRoll();

        assertThat(empty).doesNotExist();
        Mockito.verifyNoInteractions(receiver);
    }

    @Test
    void testCloseRefusesALateEvent() {
        final EventStore eventStore = eventStore(new EventStoreConfig());
        eventStore.accept(headers(), receiptId(0), "test");

        eventStore.close();

        assertThatThrownBy(() -> eventStore.accept(headers(), receiptId(1), "late"))
                .isInstanceOf(IllegalStateException.class);
    }

    private EventStore eventStore(final EventStoreConfig config) {
        final AttributeMapFilterFactory filterFactory = Mockito.mock(AttributeMapFilterFactory.class);
        final AttributeMapFilter filter = attributeMap -> policy.test(attributeMap);
        Mockito.when(filterFactory.create()).thenReturn(filter);
        return new EventStore(
                receiver,
                securityContext,
                filterFactory,
                () -> config,
                () -> receiveDataConfig,
                () -> ProxyConfig.builder().build(),
                () -> dir,
                new FileStores(new MockMetrics()));
    }

    private static AttributeMap headers() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, FEED_KEY.feed());
        attributeMap.put(StandardHeaderArguments.TYPE, FEED_KEY.type());
        return attributeMap;
    }

    private static UniqueId receiptId(final int sequence) {
        return new UniqueId(1_700_000_000_000L, sequence, NodeType.PROXY, "test-proxy");
    }

    private List<Path> files() throws IOException {
        try (final Stream<Path> stream = Files.list(eventDir)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }
}
