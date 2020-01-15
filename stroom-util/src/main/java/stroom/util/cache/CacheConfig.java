package stroom.util.cache;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.IsConfig;

import javax.validation.constraints.Min;
import java.util.concurrent.TimeUnit;

public class CacheConfig extends IsConfig {

    public static final String PROP_NAME_MAXIMUM_SIZE = "maximumSize";
    public static final String PROP_NAME_EXPIRE_AFTER_ACCESS = "expireAfterAccess";
    public static final String PROP_NAME_EXPIRE_AFTER_WRITE = "expireAfterWrite";
    private Long maximumSize;
    private Long expireAfterAccess; // Milliseconds
    private Long expireAfterWrite; // Milliseconds

    public CacheConfig() {
    }

    private CacheConfig(final Long maximumSize, final Long expireAfterAccess, final Long expireAfterWrite) {
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
        "its value, or its last read. Value is in milliseconds.")
    @JsonProperty(PROP_NAME_EXPIRE_AFTER_ACCESS)
    public Long getExpireAfterAccess() {
        return expireAfterAccess;
    }

    public void setExpireAfterAccess(final Long expireAfterAccess) {
        this.expireAfterAccess = expireAfterAccess;
    }

    @JsonPropertyDescription("Specifies that each entry should be automatically removed from the cache once " +
        "a fixed duration has elapsed after the entry's creation, or the most recent replacement of its value. " +
        "Value is in milliseconds.")
    @JsonProperty(PROP_NAME_EXPIRE_AFTER_WRITE)
    public Long getExpireAfterWrite() {
        return expireAfterWrite;
    }

    @SuppressWarnings("unused")
    public void setExpireAfterWrite(final Long expireAfterWrite) {
        this.expireAfterWrite = expireAfterWrite;
    }

    public static class Builder {
        private Long maximumSize;
        private Long expireAfterAccess; // Milliseconds
        private Long expireAfterWrite; // Milliseconds

        public Builder maximumSize(final Long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        public Builder expireAfterAccess(final Long expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
            return this;
        }

        public Builder expireAfterAccess(final int expireAfterAccess, final TimeUnit timeUnit) {
            this.expireAfterAccess = timeUnit.toMillis(expireAfterAccess);
            return this;
        }

        public Builder expireAfterWrite(final Long expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
            return this;
        }

        public Builder expireAfterWrite(final int expireAfterWrite, final TimeUnit timeUnit) {
            this.expireAfterWrite = timeUnit.toMillis(expireAfterWrite);
            return this;
        }

        public CacheConfig build() {
            return new CacheConfig(maximumSize, expireAfterAccess, expireAfterWrite);
        }
    }
}
