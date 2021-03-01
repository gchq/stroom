package stroom.proxy.repo;

import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class CleanupConfig {

    private StroomDuration cleanupFrequency;

    @JsonProperty
    public StroomDuration getCleanupFrequency() {
        return cleanupFrequency;
    }

    public void setCleanupFrequency(final StroomDuration cleanupFrequency) {
        this.cleanupFrequency = cleanupFrequency;
    }
}
