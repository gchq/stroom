package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_DEFAULT)
public class BuildInfo extends AbstractConfig {
    @JsonProperty
    private String upDate;
    @JsonProperty
    private String buildDate = "TBD";
    @JsonProperty
    private String buildVersion = "TBD";

    public BuildInfo() {
        // Default constructor necessary for GWT serialisation.
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

    public void setBuildVersion(final String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public String getBuildDate() {
        return buildDate;
    }

    public void setBuildDate(final String buildDate) {
        this.buildDate = buildDate;
    }

    public String getUpDate() {
        return upDate;
    }

    public void setUpDate(final String upDate) {
        this.upDate = upDate;
    }
}
