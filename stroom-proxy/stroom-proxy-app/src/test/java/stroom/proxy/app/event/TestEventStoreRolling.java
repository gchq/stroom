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
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.Receiver;
import stroom.proxy.repo.store.FileStores;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiveAllAttributeMapFilter;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.mock.MockCommonSecurityContext;
import stroom.test.common.MockMetrics;
import stroom.util.concurrent.UniqueId;
import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.shared.FeedKey;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When an open file is due, and what the roll does about it.
 */
class TestEventStoreRolling {

    @TempDir
    Path dir;

    @Test
    void testAFileRollsAtMaxEventCount() throws IOException {
        final Receiver receiver = Mockito.mock(Receiver.class);
        final EventStore eventStore = eventStore(receiver, new EventStoreConfig(null, null, 3L, null));
        final FeedKey feedKey = FeedKey.of("Test", "Raw Events");

        for (int i = 0; i < 7; i++) {
            accept(eventStore, feedKey, i);
        }
        assertThat(files()).as("two full files closed, one open with the seventh event").hasSize(3);

        eventStore.tryRoll();

        assertThat(files()).as("the open file is not due").hasSize(1);
        Mockito.verify(receiver, Mockito.times(2)).receive(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testAFileRollsAtMaxByteCount() throws IOException {
        final Receiver receiver = Mockito.mock(Receiver.class);
        // One serialised event is a few hundred bytes, so this holds one.
        final EventStore eventStore = eventStore(receiver, new EventStoreConfig(null, null, null, 300L));
        final FeedKey feedKey = FeedKey.of("Test", "Raw Events");

        accept(eventStore, feedKey, 0);
        accept(eventStore, feedKey, 1);

        assertThat(files()).hasSize(2);
    }

    @Test
    void testAFileRollsAtMaxAgeAndOneFilePerFeedIsReceived() throws IOException, InterruptedException {
        final Receiver receiver = Mockito.mock(Receiver.class);
        final EventStore eventStore = eventStore(receiver,
                new EventStoreConfig(null, StroomDuration.ofMillis(50), null, null));

        for (int f = 0; f < 4; f++) {
            accept(eventStore, FeedKey.of("Feed" + f, "Raw Events"), f);
        }
        eventStore.tryRoll();
        assertThat(files()).as("nothing is due yet").hasSize(4);
        Mockito.verifyNoInteractions(receiver);

        Thread.sleep(100);
        eventStore.tryRoll();

        assertThat(files()).isEmpty();
        Mockito.verify(receiver, Mockito.times(4)).receive(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private EventStore eventStore(final Receiver receiver, final EventStoreConfig config) {
        final AttributeMapFilterFactory filterFactory = Mockito.mock(AttributeMapFilterFactory.class);
        Mockito.when(filterFactory.create()).thenReturn(ReceiveAllAttributeMapFilter.getInstance());
        return new EventStore(
                receiver,
                MockCommonSecurityContext.getInstance(),
                filterFactory,
                () -> config,
                () -> ReceiveDataConfig.builder().build(),
                () -> ProxyConfig.builder().build(),
                () -> dir,
                new FileStores(new MockMetrics()));
    }

    private static void accept(final EventStore eventStore, final FeedKey feedKey, final int sequence) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedKey.feed());
        attributeMap.put(StandardHeaderArguments.TYPE, feedKey.type());
        eventStore.accept(
                attributeMap,
                new UniqueId(System.currentTimeMillis(), sequence, NodeType.PROXY, "test-proxy"),
                "event-" + sequence);
    }

    private List<Path> files() throws IOException {
        try (final Stream<Path> stream = Files.list(dir.resolve("event"))) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }
}
