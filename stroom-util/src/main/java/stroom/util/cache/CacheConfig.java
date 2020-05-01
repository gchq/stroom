package stroom.util.cache;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import javax.validation.constraints.Min;

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

    @JsonPropertyDescription("The maximum number of entries in the cache")
    @JsonProperty(PROP_NAME_MAXIMUM_SIZE)
    @Min(0)
    public Long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(final Long maximumSize) {
        this.maximumSize = maximumSize;
    }

    @JsonPropertyDescription("Specifies that each entry should be automatically removed from the cache once " +
        "a this duration has elapsed after the entry's creation, the most recent replacement of " +
        "its value, or its last read. In ISO-8601 duration format, e.g. 'PT10M'")
    @JsonProperty(PROP_NAME_EXPIRE_AFTER_ACCESS)
    public StroomDuration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    public void setExpireAfterAccess(final StroomDuration expireAfterAccess) {
        this.expireAfterAccess = expireAfterAccess;
    }

    @JsonPropertyDescription("Specifies that each entry should be automatically removed from the cache once " +
        "a fixed duration has elapsed after the entry's creation, or the most recent replacement of its value. " +
        "In ISO-8601 duration format, e.g. 'PT5M'")
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
