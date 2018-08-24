package stroom.index;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

public class CacheConfig {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheConfig.class);

    private String timeToLive;
    private String timeToIdle;
    private volatile long minItems;
    private volatile long coreItems = 10;
    private volatile long maxItems = 100;

    @JsonPropertyDescription("How long a cache item can live before it is removed from the cache during a sweep")
    public String getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(final String timeToLive) {
        this.timeToLive = timeToLive;
    }

    @JsonPropertyDescription("How long a cache item can idle before it is removed from the cache during a sweep")
    public String getTimeToIdle() {
        return timeToIdle;
    }

    public void setTimeToIdle(final String timeToIdle) {
        this.timeToIdle = timeToIdle;
    }

    @JsonPropertyDescription("The minimum number of items that will be left in the cache after a sweep")
    public long getMinItems() {
        return minItems;
    }

    public void setMinItems(final long minItems) {
        this.minItems = minItems;
    }

    @JsonPropertyDescription("The number of items that we hope to keep in the cache if items aren't removed due to TTL or TTI constraints")
    public long getCoreItems() {
        return coreItems;
    }

    public void setCoreItems(final long coreItems) {
        this.coreItems = coreItems;
    }

    @JsonPropertyDescription("The maximum number of items that can be kept in the cache. LRU items are removed to ensure we do not exceed this amount")
    public long getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(final long maxItems) {
        this.maxItems = maxItems;
    }

    @JsonIgnore
    public long getTimeToLiveMs() {
        return getDuration("timeToLive", timeToLive, 0);
    }

    @JsonIgnore
    public long getTimeToIdleMs() {
        return getDuration("timeToIdle", timeToIdle, 0);
    }

    private long getDuration(final String name, final String value, final long defaultValue) {
        Long duration;
        try {
            duration = ModelStringUtil.parseDurationString(value);
            if (duration == null) {
                duration = defaultValue;
            }
        } catch (final NumberFormatException e) {
            LOGGER.error(() -> "Unable to parse property '" + name + "' value '" + value + "', using default of '" + defaultValue + "' instead", e);
            duration = defaultValue;
        }

        return duration;
    }
}
