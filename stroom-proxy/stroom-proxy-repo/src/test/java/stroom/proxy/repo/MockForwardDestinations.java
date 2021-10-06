package stroom.proxy.repo;

import stroom.receive.common.StreamHandlers;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Singleton;

@Singleton
public class MockForwardDestinations implements ForwarderDestinations {

    private final AtomicInteger forwardCount = new AtomicInteger();

    @Override
    public List<String> getDestinationNames() {
        return Collections.singletonList("test");
    }

    @Override
    public StreamHandlers getProvider(final String forwardUrl) {
        return (feeName, typeName, attributeMap, consumer) -> {
            forwardCount.incrementAndGet();
            consumer.accept((entry, inputStream, progressHandler) -> 0);
        };
    }

    public int getForwardCount() {
        return forwardCount.get();
    }

    public void clear() {
        forwardCount.set(0);
    }
}
