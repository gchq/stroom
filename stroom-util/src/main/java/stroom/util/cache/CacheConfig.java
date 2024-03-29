package stroom.util.cache;

import stroom.util.NullSafe;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

// The descriptions have mostly been taken from the Caffine javadoc
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class CacheConfig extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    public static final String PROP_NAME_MAXIMUM_SIZE = "maximumSize";
    public static final String PROP_NAME_EXPIRE_AFTER_ACCESS = "expireAfterAccess";
    public static final String PROP_NAME_EXPIRE_AFTER_WRITE = "expireAfterWrite";
    public static final String PROP_NAME_REFRESH_AFTER_WRITE = "refreshAfterWrite";

    protected final Long maximumSize;
    protected final StroomDuration expireAfterAccess;
    protected final StroomDuration expireAfterWrite;
    protected final StroomDuration refreshAfterWrite;

    public CacheConfig() {
        maximumSize = null;
        expireAfterAccess = null;
        expireAfterWrite = null;
        refreshAfterWrite = null;
    }

    @JsonCreator
    public CacheConfig(@JsonProperty(PROP_NAME_MAXIMUM_SIZE) final Long maximumSize,
                       @JsonProperty(PROP_NAME_EXPIRE_AFTER_ACCESS) final StroomDuration expireAfterAccess,
                       @JsonProperty(PROP_NAME_EXPIRE_AFTER_WRITE) final StroomDuration expireAfterWrite,
                       @JsonProperty(PROP_NAME_REFRESH_AFTER_WRITE) final StroomDuration refreshAfterWrite) {
        this.maximumSize = maximumSize;
        this.expireAfterAccess = expireAfterAccess;
        this.expireAfterWrite = expireAfterWrite;
        this.refreshAfterWrite = refreshAfterWrite;
    }

    @JsonPropertyDescription("Specifies the maximum number of entries the cache may contain. Note that the cache " +
            "may evict an entry before this limit is exceeded or temporarily exceed the threshold while evicting. " +
            "As the cache size grows close to the maximum, the cache evicts entries that are less likely to be used " +
            "again. For example, the cache may evict an entry because it hasn't been used recently or very often. " +
            "When size is zero, elements will be evicted immediately after being loaded into the cache. This can " +
            "be useful in testing, or to disable caching temporarily without a code change. If no value is set then " +
            "no size limit will be applied.")
    @JsonProperty(PROP_NAME_MAXIMUM_SIZE)
    @Min(0)
    @RequiresRestart(RestartScope.SYSTEM)
    public Long getMaximumSize() {
        return maximumSize;
    }

    @JsonPropertyDescription("Specifies that each entry should be automatically removed from the cache once " +
            "this duration has elapsed after the entry's creation, the most recent replacement of " +
            "its value, or its last read. In ISO-8601 duration format, e.g. 'PT10M'. If no value is set then " +
            " entries will not be aged out based these criteria.")
    @JsonProperty(PROP_NAME_EXPIRE_AFTER_ACCESS)
    @RequiresRestart(RestartScope.SYSTEM)
    public StroomDuration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    @JsonPropertyDescription("Specifies that each entry should be automatically removed from the cache once " +
            "a fixed duration has elapsed after the entry's creation, or the most recent replacement of its value. " +
            "In ISO-8601 duration format, e.g. 'PT5M'. If no value is set then entries will not be aged out based on " +
            " these criteria.")
    @JsonProperty(PROP_NAME_EXPIRE_AFTER_WRITE)
    @RequiresRestart(RestartScope.SYSTEM)
    public StroomDuration getExpireAfterWrite() {
        return expireAfterWrite;
    }

    @JsonPropertyDescription("Specifies that each entry should be automatically refreshed in the cache after " +
            "a fixed duration has elapsed after the entry's creation, or the most recent replacement of its value. " +
            "In ISO-8601 duration format, e.g. 'PT5M'. Refreshing is performed asynchronously and the current value " +
            "provided until the refresh has occurred. This mechanism allows the cache to update values without any " +
            "impact on performance.")
    @JsonProperty(PROP_NAME_REFRESH_AFTER_WRITE)
    @RequiresRestart(RestartScope.SYSTEM)
    public StroomDuration getRefreshAfterWrite() {
        return refreshAfterWrite;
    }

    @Override
    public String toString() {
        return "CacheConfig{" +
                "maximumSize=" + maximumSize +
                ", expireAfterAccess=" + expireAfterAccess +
                ", expireAfterWrite=" + expireAfterWrite +
                ", refreshAfterWrite=" + refreshAfterWrite +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CacheConfig that = (CacheConfig) o;
        return Objects.equals(maximumSize, that.maximumSize) &&
                Objects.equals(expireAfterAccess, that.expireAfterAccess) &&
                Objects.equals(expireAfterWrite, that.expireAfterWrite) &&
                Objects.equals(refreshAfterWrite, that.refreshAfterWrite);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maximumSize, expireAfterAccess, expireAfterWrite, refreshAfterWrite);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private Long maximumSize;
        private StroomDuration expireAfterAccess;
        private StroomDuration expireAfterWrite;
        private StroomDuration refreshAfterWrite;
        public PropertyPath basePath;

        private Builder() {
        }

        private Builder(final CacheConfig cacheConfig) {
            maximumSize = cacheConfig.maximumSize;
            expireAfterAccess = cacheConfig.expireAfterAccess;
            expireAfterWrite = cacheConfig.expireAfterWrite;
            basePath = cacheConfig.getBasePath();
        }

        public Builder maximumSize(final Long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        public Builder expireAfterAccess(final StroomDuration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
            return this;
        }

        public Builder expireAfterWrite(final StroomDuration expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
            return this;
        }

        public Builder refreshAfterWrite(final StroomDuration refreshAfterWrite) {
            this.refreshAfterWrite = refreshAfterWrite;
            return this;
        }

        public CacheConfig build() {
            final CacheConfig cacheConfig = new CacheConfig(
                    maximumSize,
                    expireAfterAccess,
                    expireAfterWrite,
                    refreshAfterWrite);
            NullSafe.consume(basePath, cacheConfig::setBasePath);
            return cacheConfig;
        }
    }
}
