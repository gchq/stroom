package stroom.internalstatistics;

import java.util.List;

/**
 * A service for recording internal statistics on the health of Stroom
 */
public interface InternalStatisticsService {

    /**
     * @param statisticEvents A list of statistic events for zero..many statistic DocRefs.
     *                        All DocRefs in the decorated events must have a type that matches getDocRefType()
     */
    void putEvents(List<DecoratedInternalStatisticEvent> statisticEvents);

    String getDocRefType();
}
