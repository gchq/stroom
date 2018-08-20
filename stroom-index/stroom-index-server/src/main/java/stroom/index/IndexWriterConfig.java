package stroom.index;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class IndexWriterConfig {
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
