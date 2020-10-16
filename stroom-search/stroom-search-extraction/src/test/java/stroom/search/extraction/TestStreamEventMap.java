package stroom.search.extraction;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import stroom.util.shared.HasTerminate;
import stroom.util.task.MonitorImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Ignore
public class TestStreamEventMap {
    private static final int TOTAL_EVENTS = 1000000;

    @Test
    public void test() {
        final HasTerminate hasTerminate = new MonitorImpl();
        final StreamEventMap streamEventMap = new StreamEventMap(hasTerminate, 100000);
        final CountDownLatch complete = new CountDownLatch(1);
        final List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Start a producer.
        CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
//            try {
            for (int i = 0; i < TOTAL_EVENTS; i++) {
//                    Thread.sleep(1);
                int streamId = (int) (Math.random() * 10);
                streamEventMap.add(new Event(streamId, i, null));
            }
            complete.countDown();
//            } catch (final InterruptedException e) {
//                // Ignore.
//            }
        });
        futures.add(producer);

        // Start 5 consumers
        final AtomicInteger total = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> {
                try {
                    boolean done = false;
                    while (!done) {
                        final boolean state = complete.await(100, TimeUnit.MILLISECONDS);

                        final Optional<Map.Entry<Long, List<Event>>> optional = streamEventMap.get();
                        if (optional.isPresent()) {
                            optional.ifPresent(entry -> {
                                final long streamId = entry.getKey();
                                final List<Event> events = entry.getValue();
                                total.addAndGet(events.size());
                                System.out.println(total.get() + " - " + streamId + ":" + events.size());
                            });
                        } else if (state) {
                            done = true;
                        }
                    }
                } catch (final InterruptedException e) {
                    // Ignore.
                }
            });
            futures.add(consumer);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Make sure we get all the events back.
        Assert.assertEquals(TOTAL_EVENTS, total.get());
    }
}
