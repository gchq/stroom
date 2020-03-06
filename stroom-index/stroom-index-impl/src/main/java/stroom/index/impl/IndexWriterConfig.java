package stroom.index.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
public class IndexWriterConfig extends AbstractConfig {
    private IndexCacheConfig indexCacheConfig;

    public IndexWriterConfig() {
        this.indexCacheConfig = new IndexCacheConfig();
        indexCacheConfig.setCoreItems(50);
        indexCacheConfig.setMaxItems(100);
    }

    @JsonProperty("cache")
    public IndexCacheConfig getIndexCacheConfig() {
        return indexCacheConfig;
    }

    public void setIndexCacheConfig(final IndexCacheConfig indexCacheConfig) {
        this.indexCacheConfig = indexCacheConfig;
    }
}
