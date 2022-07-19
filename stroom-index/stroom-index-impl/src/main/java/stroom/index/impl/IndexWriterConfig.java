package stroom.index.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.NotNull;


@JsonPropertyOrder(alphabetic = true)
public class IndexWriterConfig extends AbstractConfig implements IsStroomConfig {

    private final IndexCacheConfig indexCacheConfig;
    private final StroomDuration slowIndexWriteWarningThreshold;

    public IndexWriterConfig() {
        indexCacheConfig = IndexCacheConfig.builder()
                .withCoreItems(50)
                .withMaxItems(100)
                .build();
        slowIndexWriteWarningThreshold = StroomDuration.ofSeconds(1);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public IndexWriterConfig(
            @JsonProperty("cache") final IndexCacheConfig indexCacheConfig,
            @JsonProperty("slowIndexWriteWarningThreshold") final StroomDuration slowIndexWriteWarningThreshold) {
        this.indexCacheConfig = indexCacheConfig;
        this.slowIndexWriteWarningThreshold = slowIndexWriteWarningThreshold;
    }

    @JsonProperty("cache")
    public IndexCacheConfig getIndexCacheConfig() {
        return indexCacheConfig;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("A warning will be logged for any index shard writes that take longer than " +
            "this threshold to complete. A value of '0' or 'PT0' means no warnings will be logged at all.")
    public StroomDuration getSlowIndexWriteWarningThreshold() {
        return slowIndexWriteWarningThreshold;
    }

    @Override
    public String toString() {
        return "IndexWriterConfig{" +
                "indexCacheConfig=" + indexCacheConfig +
                ", slowIndexWriteWarningThreshold=" + slowIndexWriteWarningThreshold +
                '}';
    }
}
