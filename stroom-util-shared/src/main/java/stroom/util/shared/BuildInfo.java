package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_DEFAULT)
public class BuildInfo extends AbstractConfig {
    @JsonProperty
    private final String upDate;
    @JsonProperty
    private final String buildDate;
    @JsonProperty
    private final String buildVersion;

    public BuildInfo() {
        upDate = null;
        buildDate = null;
        buildVersion = null;
    }

    @JsonCreator
    public BuildInfo(@JsonProperty("upDate") final String upDate,
                     @JsonProperty("buildVersion") final String buildVersion,
                     @JsonProperty("buildDate") final String buildDate) {
        this.upDate = upDate;
        this.buildVersion = buildVersion;
        this.buildDate = buildDate;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public String getBuildDate() {
        return buildDate;
    }

    public String getUpDate() {
        return upDate;
    }
}
