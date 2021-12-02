package stroom.index.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class IndexWriterConfig extends AbstractConfig {

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
