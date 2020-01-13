package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.search.api.EventRef;
import stroom.search.api.EventRefs;
import stroom.search.api.EventSearch;
import stroom.task.api.TaskCallbackAdaptor;
import stroom.task.api.TaskManager;

import javax.inject.Inject;
import java.util.function.Consumer;

class EventSearchImpl implements EventSearch {
    private static final int POLL_INTERVAL_MS = 10000;

    private final TaskManager taskManager;

    @Inject
    public EventSearchImpl(final TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public void search(final Query query, final EventRef minEvent, final EventRef maxEvent, final long maxStreams, final long maxEvents, final long maxEventsPerStream, final int resultSendFrequency, final Consumer<EventRefs> consumer) {
        final EventSearchTask eventSearchTask = new EventSearchTask(query,
                minEvent, maxEvent, maxStreams, maxEvents, maxEventsPerStream, POLL_INTERVAL_MS);
        taskManager.execAsync(eventSearchTask, new TaskCallbackAdaptor<>() {
            @Override
            public void onSuccess(final EventRefs result) {
                consumer.accept(result);
            }

            @Override
            public void onFailure(final Throwable t) {
                consumer.accept(null);
            }
        });
    }
}
