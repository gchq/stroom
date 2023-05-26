package stroom.query.common.v2;

public class KeyFactoryConfigImpl implements KeyFactoryConfig {

    public static final String DEFAULT_TIME_FIELD_NAME = "__time__";
    public static final String FALLBACK_TIME_FIELD_NAME = "EventTime";

    private int timeFieldIndex = -1;
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
    }

    @Override
    public int getTimeFieldIndex() {
        return timeFieldIndex;
    }

    @Override
    public boolean addTimeToKey() {
        return addTimeToKey;
    }

    public void setAddTimeToKey(final boolean addTimeToKey) {
        this.addTimeToKey = addTimeToKey;
    }
}
