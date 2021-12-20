package stroom.index.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class IndexCacheConfig extends AbstractConfig implements IsStroomConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexCacheConfig.class);
    protected static final StroomDuration TIME_TO_LIVE_DEFAULT = StroomDuration.ZERO;
    protected static final StroomDuration TIME_TO_IDLE_DEFAULT = StroomDuration.ZERO;
    protected static final int MIN_ITEMS_DEFAULT = 0;
    protected static final int CORE_ITEMS_DEFAULT = 10;
    protected static final int MAX_ITEMS_DEFAULT = 100;

    private final StroomDuration timeToLive;
    private final StroomDuration timeToIdle;
    private final long minItems;
    private final long coreItems;
    private final long maxItems;

    public IndexCacheConfig() {
        timeToLive = TIME_TO_LIVE_DEFAULT;
        timeToIdle = TIME_TO_IDLE_DEFAULT;
        minItems = MIN_ITEMS_DEFAULT;
        coreItems = CORE_ITEMS_DEFAULT;
        maxItems = MAX_ITEMS_DEFAULT;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public IndexCacheConfig(@JsonProperty("timeToLive") final StroomDuration timeToLive,
                            @JsonProperty("timeToIdle") final StroomDuration timeToIdle,
                            @JsonProperty("minItems") final long minItems,
                            @JsonProperty("coreItems") final long coreItems,
                            @JsonProperty("maxItems") final long maxItems) {
        this.timeToLive = timeToLive;
        this.timeToIdle = timeToIdle;
        this.minItems = minItems;
        this.coreItems = coreItems;
        this.maxItems = maxItems;
    }

    @NotNull
    @JsonPropertyDescription("How long a cache item can live before it is removed from the cache " +
            "during a sweep. A duration of zero means items will not be aged out of the cache.")
    public StroomDuration getTimeToLive() {
        return timeToLive;
    }

    @NotNull
    @JsonPropertyDescription("How long a cache item can idle before it is removed from the cache " +
            "during a sweep. A duration of zero means items will not be aged out of the cache.")
    public StroomDuration getTimeToIdle() {
        return timeToIdle;
    }

    @JsonPropertyDescription("The minimum number of items that will be left in the cache after a sweep")
    public long getMinItems() {
        return minItems;
    }

    @JsonPropertyDescription("The number of items that we hope to keep in the cache if items aren't " +
            "removed due to TTL or TTI constraints")
    public long getCoreItems() {
        return coreItems;
    }

    @JsonPropertyDescription("The maximum number of items that can be kept in the cache. LRU items are " +
            "removed to ensure we do not exceed this amount")
    public long getMaxItems() {
        return maxItems;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private StroomDuration timeToLive = TIME_TO_LIVE_DEFAULT;
        private StroomDuration timeToIdle = TIME_TO_IDLE_DEFAULT;
        private long minItems = MIN_ITEMS_DEFAULT;
        private long coreItems = CORE_ITEMS_DEFAULT;
        private long maxItems = MAX_ITEMS_DEFAULT;

        public Builder withTimeToLive(final StroomDuration timeToLive) {
            this.timeToLive = Objects.requireNonNull(timeToLive);
            return this;
        }

        public Builder withTimeToIdle(final StroomDuration timeToIdle) {
            this.timeToIdle = Objects.requireNonNull(timeToIdle);
            return this;
        }

        public Builder withMinItems(final long minItems) {
            this.minItems = minItems;
            return this;
        }

        public Builder withCoreItems(final long coreItems) {
            this.coreItems = coreItems;
            return this;
        }

        public Builder withMaxItems(final long maxItems) {
            this.maxItems = maxItems;
            return this;
        }

        public IndexCacheConfig build() {
            return new IndexCacheConfig(timeToLive, timeToIdle, minItems, coreItems, maxItems);
        }
    }
}
