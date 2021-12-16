package stroom.util.xml;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ParserConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig cacheConfig;

    private final boolean secureProcessing;

    public ParserConfig() {
        cacheConfig = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();

        secureProcessing = true;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ParserConfig(@JsonProperty("cache") final CacheConfig cacheConfig,
                        @JsonProperty("secureProcessing") final boolean secureProcessing) {
        this.cacheConfig = cacheConfig;
        this.secureProcessing = secureProcessing;
    }

    @JsonProperty("cache")
    @JsonPropertyDescription("The cache config for the parser pool.")
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("Instructs the implementation to process XML securely. This may set limits on XML " +
            "constructs to avoid conditions such as denial of service attacks.")
    public boolean isSecureProcessing() {
        return secureProcessing;
    }

    @Override
    public String toString() {
        return "ParserConfig{" +
                "secureProcessing=" + secureProcessing +
                '}';
    }
}
