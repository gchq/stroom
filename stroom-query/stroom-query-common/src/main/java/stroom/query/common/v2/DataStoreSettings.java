package stroom.query.common.v2;

import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource.SourceType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Settings to configure the behaviour of a data store.
 */
public class DataStoreSettings {

    private final boolean producePayloads;
    private final boolean requireTimeValue;
    private final boolean requireStreamIdValue;
    private final boolean requireEventIdValue;
    private final Sizes maxResults;
    private final Sizes storeSize;
    private final String subDirectory;

    public DataStoreSettings(final boolean producePayloads,
                             final boolean requireTimeValue,
                             final boolean requireStreamIdValue,
                             final boolean requireEventIdValue,
                             final Sizes maxResults,
                             final Sizes storeSize,
                             final String subDirectory) {
        this.producePayloads = producePayloads;
        this.requireTimeValue = requireTimeValue;
        this.requireStreamIdValue = requireStreamIdValue;
        this.requireEventIdValue = requireEventIdValue;
        this.maxResults = maxResults;
        this.storeSize = storeSize;
        this.subDirectory = subDirectory;
    }

    public static DataStoreSettings createAnalyticStoreSettings(final String subDirectory) {
        return DataStoreSettings
                .builder()
                .requireStreamIdValue(true)
                .requireEventIdValue(true)
                .requireTimeValue(true)
                .maxResults(Sizes.create(Integer.MAX_VALUE))
                .storeSize(Sizes.create(Integer.MAX_VALUE))
                .subDirectory(subDirectory)
                .build();
    }

    public static DataStoreSettings createBasicSearchResultStoreSettings(final SearchRequest searchRequest) {
        if (searchRequest != null && searchRequest.getSearchRequestSource() != null) {
            return createBasicSearchResultStoreSettings(searchRequest.getSearchRequestSource().getSourceType());
        } else {
            return createBasicSearchResultStoreSettings(SourceType.DASHBOARD_UI);
        }
    }

    public static DataStoreSettings createBasicSearchResultStoreSettings(final SourceType sourceType) {
        final Builder builder = DataStoreSettings.builder()
                .subDirectory(UUID.randomUUID().toString());
        if (SourceType.ANALYTIC_RULE_UI.equals(sourceType) ||
                SourceType.ANALYTIC_RULE.equals(sourceType)) {
            builder.requireStreamIdValue(true)
                    .requireEventIdValue(true)
                    .requireTimeValue(true);
        }
        return builder.build();
    }

    public static DataStoreSettings createPayloadProducerSearchResultStoreSettings() {
        return DataStoreSettings.builder().producePayloads(true).subDirectory(UUID.randomUUID().toString()).build();
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

    public Sizes getMaxResults() {
        return maxResults;
    }

    public Sizes getStoreSize() {
        return storeSize;
    }

    public String getSubDirectory() {
        return subDirectory;
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
                requireEventIdValue == that.requireEventIdValue &&
                Objects.equals(maxResults, that.maxResults) &&
                Objects.equals(storeSize, that.storeSize) &&
                Objects.equals(subDirectory, that.subDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(producePayloads,
                requireTimeValue,
                requireStreamIdValue,
                requireEventIdValue,
                maxResults,
                storeSize,
                subDirectory);
    }

    @Override
    public String toString() {
        return "DataStoreSettings{" +
                "producePayloads=" + producePayloads +
                ", requireTimeValue=" + requireTimeValue +
                ", requireStreamIdValue=" + requireStreamIdValue +
                ", requireEventIdValue=" + requireEventIdValue +
                ", maxResults=" + maxResults +
                ", storeSize=" + storeSize +
                ", subDirectory=" + subDirectory +
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
        private Sizes maxResults = Sizes.create(List.of(1000000, 100, 10, 1));
        private Sizes storeSize = Sizes.create(100);
        private String subDirectory;

        private Builder() {
        }

        private Builder(final DataStoreSettings dataStoreSettings) {
            this.producePayloads = dataStoreSettings.producePayloads;
            this.requireTimeValue = dataStoreSettings.requireTimeValue;
            this.requireStreamIdValue = dataStoreSettings.requireStreamIdValue;
            this.requireEventIdValue = dataStoreSettings.requireEventIdValue;
            this.maxResults = dataStoreSettings.maxResults;
            this.storeSize = dataStoreSettings.storeSize;
            this.subDirectory = dataStoreSettings.subDirectory;
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

        public Builder maxResults(final Sizes maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder storeSize(final Sizes storeSize) {
            this.storeSize = storeSize;
            return this;
        }

        public Builder subDirectory(final String subDirectory) {
            // Make safe for the file system.
            this.subDirectory = subDirectory.replaceAll("[^A-Za-z0-9]", "_");
            return this;
        }

        public DataStoreSettings build() {
            return new DataStoreSettings(
                    producePayloads,
                    requireTimeValue,
                    requireStreamIdValue,
                    requireEventIdValue,
                    maxResults,
                    storeSize,
                    subDirectory);
        }
    }
}
