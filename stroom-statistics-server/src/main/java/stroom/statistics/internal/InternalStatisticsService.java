package stroom.statistics.internal;

import stroom.query.api.v1.DocRef;

import java.util.List;
import java.util.Map;

/**
 * A service for recording internal statistics on the health of Stroom
 */
public interface InternalStatisticsService {

    /**
     * @param eventsMap A collection of internal statistic events grouped by their docRefs.
     *                  All docRef keys should have a type that matches that returned by getDocRefType()
     */
    void putEvents(final Map<DocRef, List<InternalStatisticEvent>> eventsMap);

    String getDocRefType();
}
