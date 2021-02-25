package stroom.proxy.repo;

import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class CleanupConfig {

    private StroomDuration cleanupFrequency;
    private int cleanupBatchSize = 1000;

    @JsonProperty
    public StroomDuration getCleanupFrequency() {
        return cleanupFrequency;
    }

    public void setCleanupFrequency(final StroomDuration cleanupFrequency) {
        this.cleanupFrequency = cleanupFrequency;
    }

    @JsonProperty
    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(final int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }
}
