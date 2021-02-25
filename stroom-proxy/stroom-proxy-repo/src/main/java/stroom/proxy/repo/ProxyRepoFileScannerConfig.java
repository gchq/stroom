package stroom.proxy.repo;

import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import javax.inject.Singleton;

@Singleton
public class ProxyRepoFileScannerConfig {

    private boolean enabled;
    private StroomDuration scanFrequency = StroomDuration.of(Duration.ofSeconds(10));

    @JsonProperty
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @JsonProperty
    public StroomDuration getScanFrequency() {
        return scanFrequency;
    }

    public void setScanFrequency(final StroomDuration scanFrequency) {
        this.scanFrequency = scanFrequency;
    }
}
