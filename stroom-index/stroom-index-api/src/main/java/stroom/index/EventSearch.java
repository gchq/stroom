package stroom.index;

import stroom.query.api.v2.Query;

import java.util.function.Consumer;

public interface EventSearch {
    void search(String user, Query query, EventRef minEvent, EventRef maxEvent, long maxStreams, long maxEvents, long maxEventsPerStream, int resultSendFrequency, Consumer<EventRefs> consumer);
}
