package stroom.query.common.v2;

import java.util.List;
import java.util.Objects;

/**
 * Settings to configure the behaviour of a data store.
 */
public class DataStoreSettings {

    private final boolean producePayloads;
    private final Sizes maxResults;
    private final Sizes storeSize;

    public DataStoreSettings(final boolean producePayloads,
                             final Sizes maxResults,
                             final Sizes storeSize) {
        this.producePayloads = producePayloads;
        this.maxResults = maxResults;
        this.storeSize = storeSize;
    }

    public static DataStoreSettings createAnalyticStoreSettings() {
        return DataStoreSettings
                .builder()
                .maxResults(Sizes.create(Integer.MAX_VALUE))
                .storeSize(Sizes.create(Integer.MAX_VALUE))
                .build();
    }

    public static DataStoreSettings createBasicSearchResultStoreSettings() {
        return DataStoreSettings.builder().build();
    }

    public static DataStoreSettings createPayloadProducerSearchResultStoreSettings() {
        return DataStoreSettings.builder().producePayloads(true).build();
    }

    public boolean isProducePayloads() {
        return producePayloads;
    }

    public Sizes getMaxResults() {
        return maxResults;
    }

    public Sizes getStoreSize() {
        return storeSize;
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
                Objects.equals(maxResults, that.maxResults) &&
                Objects.equals(storeSize, that.storeSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(producePayloads,
                maxResults,
                storeSize);
    }

    @Override
    public String toString() {
        return "DataStoreSettings{" +
                "producePayloads=" + producePayloads +
                ", maxResults=" + maxResults +
                ", storeSize=" + storeSize +
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
        private Sizes maxResults = Sizes.create(List.of(1000000, 100, 10, 1));
        private Sizes storeSize = Sizes.create(100);

        private Builder() {
        }

        private Builder(final DataStoreSettings dataStoreSettings) {
            this.producePayloads = dataStoreSettings.producePayloads;
            this.maxResults = dataStoreSettings.maxResults;
            this.storeSize = dataStoreSettings.storeSize;
        }

        public Builder producePayloads(final boolean producePayloads) {
            this.producePayloads = producePayloads;
            return this;
        }

        public Builder maxResults(final Sizes maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder storeSize(final Sizes storeSize) {
            this.storeSize = storeSize;
            return this;
        }

        public DataStoreSettings build() {
            return new DataStoreSettings(
                    producePayloads,
                    maxResults,
                    storeSize);
        }
    }
}
