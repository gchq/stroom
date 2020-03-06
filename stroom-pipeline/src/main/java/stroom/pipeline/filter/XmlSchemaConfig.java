package stroom.pipeline.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class XmlSchemaConfig extends AbstractConfig {
    private CacheConfig cacheConfig = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    @JsonProperty("cache")
    @JsonPropertyDescription("The cache config for the XML schema pool.")
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    public void setCacheConfig(final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    @Override
    public String toString() {
        return "XmlSchemaConfig{" +
                "cacheConfig=" + cacheConfig +
                '}';
    }
}
