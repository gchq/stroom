package stroom.query.common.v2;

import stroom.query.api.v2.Query;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface EventSearch {

    CompletableFuture<EventRefs> search(Query query,
                                        EventRef minEvent,
                                        EventRef maxEvent,
                                        long maxStreams,
                                        long maxEvents,
                                        long maxEventsPerStream,
                                        int resultSendFrequency,
                                        Consumer<EventRefs> consumer);
}
