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
