package stroom.pipeline.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.xml.CacheConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class XmlSchemaConfig implements IsConfig {
    private CacheConfig cacheConfig = new CacheConfig(1000);

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
