package stroom.util.xml;

import stroom.util.shared.IsConfig;

public class CacheConfig implements IsConfig {
    private long maximumSize = 1000;

    public CacheConfig() {
    }

    public CacheConfig(final long maximumSize) {
        this.maximumSize = maximumSize;
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(final long maximumSize) {
        this.maximumSize = maximumSize;
    }

    @Override
    public String toString() {
        return "CacheConfig{" +
                "maximumSize=" + maximumSize +
                '}';
    }
}
