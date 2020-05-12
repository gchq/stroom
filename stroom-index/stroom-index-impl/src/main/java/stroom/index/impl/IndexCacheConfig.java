package stroom.index.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class IndexCacheConfig extends AbstractConfig {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexCacheConfig.class);

    private StroomDuration timeToLive = StroomDuration.ZERO;
    private StroomDuration timeToIdle = StroomDuration.ZERO;
    private volatile long minItems = 0;
    private volatile long coreItems = 10;
    private volatile long maxItems = 100;

    @NotNull
    @JsonPropertyDescription("How long a cache item can live before it is removed from the cache " +
            "during a sweep. A duration of zero means items will not be aged out of the cache.")
    public StroomDuration getTimeToLive() {
        return timeToLive;
    }

    @SuppressWarnings("unused")
    public void setTimeToLive(final StroomDuration timeToLive) {
        this.timeToLive = timeToLive;
    }

    @NotNull
    @JsonPropertyDescription("How long a cache item can idle before it is removed from the cache " +
            "during a sweep. A duration of zero means items will not be aged out of the cache.")
    public StroomDuration getTimeToIdle() {
        return timeToIdle;
    }

    @SuppressWarnings("unused")
    public void setTimeToIdle(final StroomDuration timeToIdle) {
        this.timeToIdle = timeToIdle;
    }

    @JsonPropertyDescription("The minimum number of items that will be left in the cache after a sweep")
    public long getMinItems() {
        return minItems;
    }

    @SuppressWarnings("unused")
    public void setMinItems(final long minItems) {
        this.minItems = minItems;
    }

    @JsonPropertyDescription("The number of items that we hope to keep in the cache if items aren't " +
            "removed due to TTL or TTI constraints")
    public long getCoreItems() {
        return coreItems;
    }

    @SuppressWarnings("unused")
    public void setCoreItems(final long coreItems) {
        this.coreItems = coreItems;
    }

    @JsonPropertyDescription("The maximum number of items that can be kept in the cache. LRU items are " +
            "removed to ensure we do not exceed this amount")
    public long getMaxItems() {
        return maxItems;
    }

    @SuppressWarnings("unused")
    public void setMaxItems(final long maxItems) {
        this.maxItems = maxItems;
    }
}
