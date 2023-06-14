package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.query.api.v2.SearchRequestSource.SourceType;

import java.util.Optional;

public class CurrentDbStateFactory {

    private final boolean storeLatestEventReference;
    private final int streamIdFieldIndex;
    private final int eventIdFieldIndex;
    private final int timeFieldIndex;

    public CurrentDbStateFactory(final SourceType sourceType,
                                 final FieldIndex fieldIndex,
                                 final DataStoreSettings dataStoreSettings) {
        if (sourceType.isRequireStreamIdValue() &&
                sourceType.isRequireEventIdValue()) {
            streamIdFieldIndex = fieldIndex.getStreamIdFieldIndex();
            eventIdFieldIndex = fieldIndex.getEventIdFieldIndex();
            timeFieldIndex = fieldIndex.getTimeFieldIndex();
            storeLatestEventReference = dataStoreSettings.isStoreLatestEventReference();
        } else {
            streamIdFieldIndex = -1;
            eventIdFieldIndex = -1;
            timeFieldIndex = -1;
            storeLatestEventReference = false;
        }
    }

    public CurrentDbState createCurrentDbState(final Val[] values) {
        if (storeLatestEventReference) {
            if (streamIdFieldIndex >= 0 && streamIdFieldIndex < values.length &&
                    eventIdFieldIndex >= 0 && eventIdFieldIndex < values.length) {
                final Val streamId = values[streamIdFieldIndex];
                final Val eventId = values[eventIdFieldIndex];

                if (streamId != null && eventId != null) {
                    // Get optional event time field.
                    long time = 0;
                    if (timeFieldIndex >= 0 && timeFieldIndex < values.length) {
                        final Val eventTime = values[timeFieldIndex];
                        time = Optional.ofNullable(eventTime).map(Val::toLong).orElse(0L);
                    }

                    final long streamIdLong = Optional.ofNullable(streamId.toLong()).orElseThrow(() ->
                            new RuntimeException("Unable to get stream id"));
                    final long eventIdLong = Optional.ofNullable(eventId.toLong()).orElseThrow(() ->
                            new RuntimeException("Unable to get event id"));

                    return new CurrentDbState(streamIdLong, eventIdLong, time);
                }
            }
        }
        return null;
    }

    public boolean isStoreLatestEventReference() {
        return storeLatestEventReference;
    }
}
