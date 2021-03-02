package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import javax.inject.Singleton;

@Singleton
public class CleanupConfig extends AbstractConfig {

    private StroomDuration cleanupFrequency = StroomDuration.of(Duration.ofHours(1));

    @JsonProperty
    public StroomDuration getCleanupFrequency() {
        return cleanupFrequency;
    }

    public void setCleanupFrequency(final StroomDuration cleanupFrequency) {
        this.cleanupFrequency = cleanupFrequency;
    }
}
