package stroom.index.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class IndexWriterConfig extends AbstractConfig implements IsStroomConfig {

    private final IndexCacheConfig indexCacheConfig;

    public IndexWriterConfig() {
        indexCacheConfig = IndexCacheConfig.builder()
                .withCoreItems(50)
                .withMaxItems(100)
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public IndexWriterConfig(@JsonProperty("cache") final IndexCacheConfig indexCacheConfig) {
        this.indexCacheConfig = indexCacheConfig;
    }

    @JsonProperty("cache")
    public IndexCacheConfig getIndexCacheConfig() {
        return indexCacheConfig;
    }

}
