package stroom.statistics.internal;

import java.util.List;

public interface InternalStatistics {
    /**
     * @param event A statistic event to record.
     *                               For the statistic event to be record by an implementing service there must be
     *                               All exceptions will be swallowed and logged as errors
     */
    void putEvent(InternalStatisticEvent event);

    void putEvents(List<InternalStatisticEvent> events);
}
