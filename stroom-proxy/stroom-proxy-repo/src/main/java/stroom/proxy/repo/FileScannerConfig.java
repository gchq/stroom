package stroom.proxy.repo;

import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class FileScannerConfig {

    private final String path;
    private final StroomDuration scanFrequency;

    @SuppressWarnings("unused")
    @JsonCreator
    public FileScannerConfig(@JsonProperty("path") final String path,
                             @JsonProperty("scanFrequency") final StroomDuration scanFrequency) {
        this.path = path;
        this.scanFrequency = scanFrequency;
    }

    @JsonProperty
    public String getPath() {
        return path;
    }

    @JsonProperty
    public StroomDuration getScanFrequency() {
        return scanFrequency;
    }
}
