package stroom.util.cache;

import stroom.util.shared.IsConfig;

import java.util.concurrent.TimeUnit;

public class CacheConfig implements IsConfig {
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

    public Long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(final Long maximumSize) {
        this.maximumSize = maximumSize;
    }

    public Long getExpireAfterAccess() {
        return expireAfterAccess;
    }

    public void setExpireAfterAccess(final Long expireAfterAccess) {
        this.expireAfterAccess = expireAfterAccess;
    }

    public Long getExpireAfterWrite() {
        return expireAfterWrite;
    }

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
