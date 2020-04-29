package stroom.util.cache;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.validation.constraints.Min;

// The descriptions have mostly been taken from the Caffine javadoc
@NotInjectableConfig
public class CacheConfig extends AbstractConfig {

    public static final String PROP_NAME_MAXIMUM_SIZE = "maximumSize";
    public static final String PROP_NAME_EXPIRE_AFTER_ACCESS = "expireAfterAccess";
    public static final String PROP_NAME_EXPIRE_AFTER_WRITE = "expireAfterWrite";
    private Long maximumSize;
    private StroomDuration expireAfterAccess;
    private StroomDuration expireAfterWrite;

    public CacheConfig() {
    }

    private CacheConfig(final Long maximumSize,
                        final StroomDuration expireAfterAccess,
                        final StroomDuration expireAfterWrite) {
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
    public Long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(final Long maximumSize) {
        this.maximumSize = maximumSize;
    }

    @JsonPropertyDescription("Specifies that each entry should be automatically removed from the cache once " +
            "this duration has elapsed after the entry's creation, the most recent replacement of " +
            "its value, or its last read. In ISO-8601 duration format, e.g. 'PT10M'. If no value is set then " +
            " entries will not be aged out based these criteria.")
    @JsonProperty(PROP_NAME_EXPIRE_AFTER_ACCESS)
    public StroomDuration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    public void setExpireAfterAccess(final StroomDuration expireAfterAccess) {
        this.expireAfterAccess = expireAfterAccess;
    }

    @JsonPropertyDescription("Specifies that each entry should be automatically removed from the cache once " +
            "a fixed duration has elapsed after the entry's creation, or the most recent replacement of its value. " +
            "In ISO-8601 duration format, e.g. 'PT5M'. If no value is set then entries will not be aged out based on " +
            " these criteria.")
    @JsonProperty(PROP_NAME_EXPIRE_AFTER_WRITE)
    public StroomDuration getExpireAfterWrite() {
        return expireAfterWrite;
    }

    @SuppressWarnings("unused")
    public void setExpireAfterWrite(final StroomDuration expireAfterWrite) {
        this.expireAfterWrite = expireAfterWrite;
    }

    public static class Builder {
        private Long maximumSize;
        private StroomDuration expireAfterAccess;
        private StroomDuration expireAfterWrite;

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
            return new CacheConfig(maximumSize, expireAfterAccess, expireAfterWrite);
        }
    }
}
