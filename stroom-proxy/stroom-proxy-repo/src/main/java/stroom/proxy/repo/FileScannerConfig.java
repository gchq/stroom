package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.validation.ValidDirectoryPath;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class FileScannerConfig {

    private final String path;
    private final StroomDuration scanFrequency;

    @SuppressWarnings("unused")
    public FileScannerConfig() {
        path = null;
        scanFrequency = StroomDuration.ofMinutes(1);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public FileScannerConfig(@JsonProperty("path") final String path,
                             @JsonProperty("scanFrequency") final StroomDuration scanFrequency) {
        this.path = path;
        this.scanFrequency = scanFrequency;
    }

    @RequiresProxyRestart
    @NotNull
    @ValidDirectoryPath
    @JsonProperty
    public String getPath() {
        return path;
    }

    @RequiresProxyRestart
    @NotNull
    @JsonProperty
    public StroomDuration getScanFrequency() {
        return scanFrequency;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FileScannerConfig that = (FileScannerConfig) o;
        return Objects.equals(path, that.path) && Objects.equals(scanFrequency, that.scanFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, scanFrequency);
    }

    @Override
    public String toString() {
        return "FileScannerConfig{" +
                "path='" + path + '\'' +
                ", scanFrequency=" + scanFrequency +
                '}';
    }
}
