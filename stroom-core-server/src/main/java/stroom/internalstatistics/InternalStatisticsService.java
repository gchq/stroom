package stroom.internalstatistics;

import java.util.Collections;
import java.util.List;

public interface InternalStatisticsService {

    default void putEvent(InternalStatisticEvent internalStatisticEvent) {
        putEvents(Collections.singletonList(internalStatisticEvent));
    }

    void putEvents(List<InternalStatisticEvent> internalStatisticEvents);
}
