package stroom.index.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class IndexWriterConfig implements IsConfig {
    private CacheConfig cacheConfig;

    public IndexWriterConfig() {
        this.cacheConfig = new CacheConfig();
        cacheConfig.setCoreItems(50);
        cacheConfig.setMaxItems(100);
    }

    @JsonProperty("cache")
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    public void setCacheConfig(final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }
}
