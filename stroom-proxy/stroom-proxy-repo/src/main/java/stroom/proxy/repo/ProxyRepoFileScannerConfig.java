package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Duration;

@JsonPropertyOrder(alphabetic = true)
public class ProxyRepoFileScannerConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean scanningEnabled;
    private final StroomDuration scanFrequency;

    public ProxyRepoFileScannerConfig() {
        scanningEnabled = false;
        scanFrequency = StroomDuration.of(Duration.ofSeconds(10));
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProxyRepoFileScannerConfig(@JsonProperty("scanningEnabled") final boolean scanningEnabled,
                                      @JsonProperty("scanFrequency") final StroomDuration scanFrequency) {
        this.scanningEnabled = scanningEnabled;
        this.scanFrequency = scanFrequency;
    }

    @JsonProperty
    public boolean isScanningEnabled() {
        return scanningEnabled;
    }

    @JsonProperty
    public StroomDuration getScanFrequency() {
        return scanFrequency;
    }

    public ProxyRepoFileScannerConfig withScanningEnabled(final boolean scanningEnabled) {
        return new ProxyRepoFileScannerConfig(scanningEnabled, scanFrequency);
    }

    public ProxyRepoFileScannerConfig withScanFrequency(final StroomDuration scanFrequency) {
        return new ProxyRepoFileScannerConfig(scanningEnabled, scanFrequency);
    }
}
