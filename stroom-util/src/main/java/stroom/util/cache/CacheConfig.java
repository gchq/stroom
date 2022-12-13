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

import java.util.Objects;
import javax.validation.constraints.Min;

// The descriptions have mostly been taken from the Caffine javadoc
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class CacheConfig extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    public static final String PROP_NAME_MAXIMUM_SIZE = "maximumSize";
    public static final String PROP_NAME_EXPIRE_AFTER_ACCESS = "expireAfterAccess";
    public static final String PROP_NAME_EXPIRE_AFTER_WRITE = "expireAfterWrite";

    protected final Long maximumSize;
    protected final StroomDuration expireAfterAccess;
    protected final StroomDuration expireAfterWrite;

    public CacheConfig() {
        maximumSize = null;
        expireAfterAccess = null;
        expireAfterWrite = null;
    }

    @JsonCreator
    public CacheConfig(@JsonProperty(PROP_NAME_MAXIMUM_SIZE) final Long maximumSize,
                       @JsonProperty(PROP_NAME_EXPIRE_AFTER_ACCESS) final StroomDuration expireAfterAccess,
                       @JsonProperty(PROP_NAME_EXPIRE_AFTER_WRITE) final StroomDuration expireAfterWrite) {
        this.maximumSize = maximumSize;
        this.expireAfterAccess = expireAfterAccess;
        this.expireAfterWrite = expireAfterWrite;
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

    @Override
    public String toString() {
        return "CacheConfig{" +
                "maximumSize=" + maximumSize +
                ", expireAfterAccess=" + expireAfterAccess +
                ", expireAfterWrite=" + expireAfterWrite +
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
        return Objects.equals(maximumSize, that.maximumSize) && Objects.equals(expireAfterAccess,
                that.expireAfterAccess) && Objects.equals(expireAfterWrite, that.expireAfterWrite);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maximumSize, expireAfterAccess, expireAfterWrite);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static final class Builder {

        private Long maximumSize;
        private StroomDuration expireAfterAccess;
        private StroomDuration expireAfterWrite;
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

        public CacheConfig build() {
            final CacheConfig cacheConfig = new CacheConfig(maximumSize, expireAfterAccess, expireAfterWrite);
            NullSafe.consume(basePath, cacheConfig::setBasePath);
            return cacheConfig;
        }
    }
}
