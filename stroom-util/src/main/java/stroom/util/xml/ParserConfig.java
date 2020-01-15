package stroom.util.xml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class ParserConfig extends AbstractConfig {
    private CacheConfig cacheConfig = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    static boolean secureProcessing = true;

    @JsonProperty("cache")
    @JsonPropertyDescription("The cache config for the parser pool.")
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    public void setCacheConfig(final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    @JsonPropertyDescription("Instructs the implementation to process XML securely. This may set limits on XML constructs to avoid conditions such as denial of service attacks.")
    public boolean isSecureProcessing() {
        return secureProcessing;
    }

    public void setSecureProcessing(final boolean secureProcessing) {
        this.secureProcessing = secureProcessing;
    }

    @Override
    public String toString() {
        return "ParserConfig{" +
                "secureProcessing=" + secureProcessing +
                '}';
    }
}
