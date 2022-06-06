package stroom.search.extraction;

import stroom.search.extraction.StreamEventMap.EventSet;
import stroom.util.concurrent.CompleteException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class TestStreamEventMap {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStreamEventMap.class);
    private static final int TOTAL_EVENTS = 1000000;

    @Test
    void test() {
        final StreamEventMap streamEventMap = new StreamEventMap(100000);
        final List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Start a producer.
        CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < TOTAL_EVENTS; i++) {
                int streamId = (int) (Math.random() * 10);
                streamEventMap.put(new Event(streamId, i, null));
            }
            streamEventMap.complete();
        });
        futures.add(producer);

        // Start 5 consumers
        final AtomicInteger total = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> {
                try {
                    while (true) {
                        final EventSet eventSet = streamEventMap.take();
                        if (eventSet != null) {
                            total.addAndGet(eventSet.size());
                        }
                    }
                } catch (final CompleteException e) {
                    LOGGER.debug(() -> "Complete");
                    LOGGER.trace(e::getMessage, e);
                }
            });
            futures.add(consumer);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Make sure we get all the events back.
        assertThat(total.get()).isEqualTo(TOTAL_EVENTS);
    }
}
