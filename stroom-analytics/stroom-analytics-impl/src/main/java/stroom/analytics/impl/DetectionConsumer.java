package stroom.analytics.impl;

import java.time.Instant;
import java.util.List;

public interface DetectionConsumer extends ProcessLifecycleAware {

    void accept(Detection detection);

    record Detection(Instant detectTime,
                     String detectorName,
                     String detectorUuid,
                     String detectorVersion,
                     String detectorEnvironment,
                     String headline,
                     String detailedDescription,
                     String fullDescription,
                     String detectionUniqueId,
                     Integer detectionRevision,
                     Boolean defunct,
                     List<Value> values,
                     List<LinkedEvent> linedEvents) {

    }

    record Value(String name, String value) {

    }

    record LinkedEvent(String stroom, Long streamId, Long eventId) {

    }
}
