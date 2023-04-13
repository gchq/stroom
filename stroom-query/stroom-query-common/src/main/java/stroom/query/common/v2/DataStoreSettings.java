package stroom.query.common.v2;

import java.util.Objects;

/**
 * Settings to configure the behaviour of a data store.
 */
public class DataStoreSettings {

    public static DataStoreSettings BASIC_SETTINGS =
            DataStoreSettings.builder().build();
    public static DataStoreSettings PAYLOAD_PRODUCER_SETTINGS =
            DataStoreSettings.builder().producePayloads(true).build();

    private final boolean producePayloads;
    private final boolean requireTimeValue;
    private final boolean requireStreamIdValue;
    private final boolean requireEventIdValue;

    public DataStoreSettings(final boolean producePayloads,
                             final boolean requireTimeValue,
                             final boolean requireStreamIdValue,
                             final boolean requireEventIdValue) {
        this.producePayloads = producePayloads;
        this.requireTimeValue = requireTimeValue;
        this.requireStreamIdValue = requireStreamIdValue;
        this.requireEventIdValue = requireEventIdValue;
    }

    public boolean isProducePayloads() {
        return producePayloads;
    }

    public boolean isRequireTimeValue() {
        return requireTimeValue;
    }

    public boolean isRequireStreamIdValue() {
        return requireStreamIdValue;
    }

    public boolean isRequireEventIdValue() {
        return requireEventIdValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataStoreSettings that = (DataStoreSettings) o;
        return producePayloads == that.producePayloads &&
                requireTimeValue == that.requireTimeValue &&
                requireStreamIdValue == that.requireStreamIdValue &&
                requireEventIdValue == that.requireEventIdValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(producePayloads, requireTimeValue, requireStreamIdValue, requireEventIdValue);
    }

    @Override
    public String toString() {
        return "DataStoreSettings{" +
                "producePayloads=" + producePayloads +
                ", requireTimeValue=" + requireTimeValue +
                ", requireStreamIdValue=" + requireStreamIdValue +
                ", requireEventIdValue=" + requireEventIdValue +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean producePayloads;
        private boolean requireTimeValue;
        private boolean requireStreamIdValue;
        private boolean requireEventIdValue;

        private Builder() {
        }

        private Builder(final DataStoreSettings dataStoreSettings) {
            this.producePayloads = dataStoreSettings.producePayloads;
            this.requireTimeValue = dataStoreSettings.requireTimeValue;
            this.requireStreamIdValue = dataStoreSettings.requireStreamIdValue;
            this.requireEventIdValue = dataStoreSettings.requireEventIdValue;
        }

        public Builder producePayloads(final boolean producePayloads) {
            this.producePayloads = producePayloads;
            return this;
        }

        public Builder requireTimeValue(final boolean requireTimeValue) {
            this.requireTimeValue = requireTimeValue;
            return this;
        }

        public Builder requireStreamIdValue(final boolean requireStreamIdValue) {
            this.requireStreamIdValue = requireStreamIdValue;
            return this;
        }

        public Builder requireEventIdValue(final boolean requireEventIdValue) {
            this.requireEventIdValue = requireEventIdValue;
            return this;
        }

        public DataStoreSettings build() {
            return new DataStoreSettings(producePayloads, requireTimeValue, requireStreamIdValue, requireEventIdValue);
        }
    }
}
