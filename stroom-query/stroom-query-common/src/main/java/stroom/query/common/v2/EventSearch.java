package stroom.query.common.v2;

import stroom.query.api.Query;
import stroom.task.api.TaskContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public interface EventSearch {

    CompletableFuture<Void> search(TaskContext parentTaskContext,
                                   Query query,
                                   EventRef minEvent,
                                   EventRef maxEvent,
                                   long maxStreams,
                                   long maxEvents,
                                   long maxEventsPerStream,
                                   BiConsumer<EventRefs, Throwable> consumer);
}
