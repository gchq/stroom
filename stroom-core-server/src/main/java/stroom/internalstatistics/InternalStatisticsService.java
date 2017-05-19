package stroom.internalstatistics;

import stroom.statistics.common.StatisticEvent;

import java.util.Collections;
import java.util.List;

public interface InternalStatisticsService {

    default void putEvent(StatisticEvent internalStatisticEvent) {
        putEvents(Collections.singletonList(internalStatisticEvent));
    }

    void putEvents(List<StatisticEvent> internalStatisticEvents);

    String getName();
}
