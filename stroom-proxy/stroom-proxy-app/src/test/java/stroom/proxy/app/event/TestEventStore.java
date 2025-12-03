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

package stroom.proxy.app.event;

import stroom.cache.impl.CacheManagerImpl;
import stroom.meta.api.AttributeMap;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.repo.store.FileStores;
import stroom.test.common.MockMetrics;
import stroom.util.concurrent.UniqueId;
import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.metrics.Metrics;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class TestEventStore {

    @Test
    void test() throws IOException {
        final Path dir = Files.createTempDirectory("stroom");
        final Path eventDir = dir.resolve("event");
        final FeedKey feedKey = new FeedKey("Test", "Raw Events");
        final EventStoreConfig eventStoreConfig = new EventStoreConfig();
        final ReceiverFactory receiveStreamHandlers = Mockito.mock(ReceiverFactory.class);
        final DataDirProvider dataDirProvider = () -> dir;
        final Metrics metrics = new MockMetrics();
        final EventStore eventStore = new EventStore(
                receiveStreamHandlers,
                () -> eventStoreConfig,
                dataDirProvider,
                new FileStores(metrics),
                new CacheManagerImpl(() -> metrics),
                metrics);

        for (int i = 0; i < 10; i++) {
            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put("Feed", feedKey.feed());
            attributeMap.put("Type", feedKey.type());
            final UniqueId receiptId = new UniqueId(
                    Instant.now().toEpochMilli(),
                    i,
                    NodeType.PROXY,
                    "test-proxy");

            eventStore.consume(attributeMap, receiptId, "test");
        }

        final String expected =
                "\"headers\":[{\"name\":\"Feed\",\"value\":\"Test\"},{\"name\":\"Type\",\"value\":\"Raw Events\"}]," +
                "\"detail\":\"test\"}";
        assertThat(EventStoreTestUtil.read(eventDir, feedKey))
                .contains(expected);
    }
}
