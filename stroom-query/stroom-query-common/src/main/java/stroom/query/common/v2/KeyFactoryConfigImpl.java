package stroom.query.common.v2;

public class KeyFactoryConfigImpl implements KeyFactoryConfig {

    private static final String DEFAULT_TIME_FIELD_NAME = "__time__";
    private static final String DEFAULT_STREAM_ID_FIELD_NAME = "__stream_id__";
    private static final String DEFAULT_EVENT_ID_FIELD_NAME = "__event_id__";
    private static final String FALLBACK_TIME_FIELD_NAME = "EventTime";
    private static final String FALLBACK_STREAM_ID_FIELD_NAME = "StreamId";
    private static final String FALLBACK_EVENT_ID_FIELD_NAME = "EventId";

    private int timeFieldIndex = -1;
    private int streamIdFieldIndex = -1;
    private int eventIdFieldIndex = -1;
    private boolean addTimeToKey;

    public KeyFactoryConfigImpl(final CompiledField[] compiledFields,
                                final CompiledDepths compiledDepths,
                                final DataStoreSettings dataStoreSettings) {
        boolean timeGrouped = false;

        for (int i = 0; i < compiledFields.length; i++) {
            final CompiledField field = compiledFields[i];
            if (dataStoreSettings.isRequireTimeValue() &&
                    DEFAULT_TIME_FIELD_NAME.equalsIgnoreCase(field.getField().getName())) {
                timeFieldIndex = i;
                if (field.getGroupDepth() >= 0) {
                    timeGrouped = true;
                }
            } else if (dataStoreSettings.isRequireStreamIdValue() &&
                    DEFAULT_STREAM_ID_FIELD_NAME.equalsIgnoreCase(field.getField().getName())) {
                streamIdFieldIndex = i;
            } else if (dataStoreSettings.isRequireEventIdValue() &&
                    DEFAULT_EVENT_ID_FIELD_NAME.equalsIgnoreCase(field.getField().getName())) {
                eventIdFieldIndex = i;
            }
        }

        for (int i = 0; i < compiledFields.length; i++) {
            final CompiledField field = compiledFields[i];
            if (dataStoreSettings.isRequireTimeValue() &&
                    FALLBACK_TIME_FIELD_NAME.equalsIgnoreCase(field.getField().getName())) {
                if (field.getGroupDepth() >= 0) {
                    if (!timeGrouped) {
                        timeFieldIndex = i;
                        timeGrouped = true;
                    }
                } else if (timeFieldIndex == -1) {
                    timeFieldIndex = i;
                }
            } else if (dataStoreSettings.isRequireStreamIdValue() &&
                    streamIdFieldIndex == -1 &&
                    FALLBACK_STREAM_ID_FIELD_NAME.equalsIgnoreCase(field.getField().getName())) {
                streamIdFieldIndex = i;
            } else if (dataStoreSettings.isRequireEventIdValue() &&
                    eventIdFieldIndex == -1 &&
                    FALLBACK_EVENT_ID_FIELD_NAME.equalsIgnoreCase(field.getField().getName())) {
                eventIdFieldIndex = i;
            }
        }

        if ((!compiledDepths.hasGroup() && timeFieldIndex != -1) || (compiledDepths.hasGroup() && timeGrouped)) {
            addTimeToKey = true;
        } else {
            timeFieldIndex = -1;
        }

        if (dataStoreSettings.isRequireTimeValue() && timeFieldIndex == -1) {
            throw new RuntimeException("Time field required but not found.");
        }
        if (dataStoreSettings.isRequireStreamIdValue() && streamIdFieldIndex == -1) {
            throw new RuntimeException("Stream id field required but not found.");
        }
        if (dataStoreSettings.isRequireEventIdValue() && eventIdFieldIndex == -1) {
            throw new RuntimeException("Event id field required but not found.");
        }
    }

    @Override
    public int getTimeFieldIndex() {
        return timeFieldIndex;
    }

    @Override
    public int getStreamIdFieldIndex() {
        return streamIdFieldIndex;
    }

    @Override
    public int getEventIdFieldIndex() {
        return eventIdFieldIndex;
    }

    @Override
    public boolean addTimeToKey() {
        return addTimeToKey;
    }

    public void setAddTimeToKey(final boolean addTimeToKey) {
        this.addTimeToKey = addTimeToKey;
    }
}
