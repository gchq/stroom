package stroom.util.xml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class ParserConfig implements IsConfig {
    private CacheConfig cacheConfig = new CacheConfig(1000);
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
