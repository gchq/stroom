package stroom.pipeline.filter;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


@JsonPropertyOrder(alphabetic = true)
public class XmlSchemaConfig extends AbstractConfig {

    private final CacheConfig cacheConfig;

    public XmlSchemaConfig() {
        cacheConfig = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @JsonCreator
    public XmlSchemaConfig(@JsonProperty("cache") final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    @JsonProperty("cache")
    @JsonPropertyDescription("The cache config for the XML schema pool.")
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    @Override
    public String toString() {
        return "XmlSchemaConfig{" +
                "cacheConfig=" + cacheConfig +
                '}';
    }
}
