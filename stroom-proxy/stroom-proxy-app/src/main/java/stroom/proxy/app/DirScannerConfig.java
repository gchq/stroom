package stroom.proxy.app;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class DirScannerConfig extends AbstractConfig implements IsProxyConfig {

    public static final StroomDuration DEFAULT_SCAN_FREQUENCY = StroomDuration.ofSeconds(10);
    public static final boolean DEFAULT_ENABLED_STATE = false;
    public static final List<String> DEFAULT_DIRS = List.of("file_ingest");
    public static final String DEFAULT_FAILURE_DIR = "file_ingest_failed";

    private final List<String> dirs;
    private final String failureDir;
    private final boolean enabled;
    private final StroomDuration scanFrequency;

    public DirScannerConfig() {
        this.enabled = DEFAULT_ENABLED_STATE;
        this.dirs = null;
        this.failureDir = DEFAULT_FAILURE_DIR;
        this.scanFrequency = DEFAULT_SCAN_FREQUENCY;
    }

    @JsonCreator
    public DirScannerConfig(@JsonProperty("dirs") final List<String> dirs,
                            @JsonProperty("failureDir") final String failureDir,
                            @JsonProperty("enabled") final Boolean enabled,
                            @JsonProperty("scanFrequency") final StroomDuration scanFrequency) {
        this.dirs = Objects.requireNonNullElse(dirs, DEFAULT_DIRS);
        this.failureDir = Objects.requireNonNullElse(failureDir, DEFAULT_FAILURE_DIR);
        this.enabled = Objects.requireNonNullElse(enabled, DEFAULT_ENABLED_STATE);
        this.scanFrequency = Objects.requireNonNullElse(scanFrequency, DEFAULT_SCAN_FREQUENCY);
    }

    @JsonProperty
    @JsonPropertyDescription("The list of directories to scan for proxy format ZIP files.")
    public List<String> getDirs() {
        return NullSafe.list(dirs);
    }

    @JsonProperty
    @JsonPropertyDescription("The directory where ZIPs will be moved to if they could not be ingested.")
    public String getFailureDir() {
        return failureDir;
    }

    @JsonProperty
    @JsonPropertyDescription("Whether scanning of the directories for proxy format ZIP files is enabled or not.")
    public boolean isEnabled() {
        return enabled;
    }

    @RequiresProxyRestart
    @JsonProperty
    @JsonPropertyDescription("The frequency at which scans of the directories will occur. All directories will " +
                             "be scanned on each run.")
    public StroomDuration getScanFrequency() {
        return scanFrequency;
    }

    @Override
    public String toString() {
        return "DirScannerConfig{" +
               "dirs=" + dirs +
               ", enabled=" + enabled +
               ", scanFrequency=" + scanFrequency +
               '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final DirScannerConfig that = (DirScannerConfig) object;
        return enabled == that.enabled && Objects.equals(dirs, that.dirs) && Objects.equals(
                scanFrequency,
                that.scanFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dirs, enabled, scanFrequency);
    }
}
