package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class BuildInfo extends AbstractConfig {
    @JsonProperty
    private final String upDate;
    @JsonProperty
    private final String buildDate;
    @JsonProperty
    private final String buildVersion;

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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final BuildInfo buildInfo = (BuildInfo) o;
        return Objects.equals(upDate, buildInfo.upDate) &&
                Objects.equals(buildDate, buildInfo.buildDate) &&
                Objects.equals(buildVersion, buildInfo.buildVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(upDate, buildDate, buildVersion);
    }

    @Override
    public String toString() {
        return "BuildInfo{" +
                "upDate='" + upDate + '\'' +
                ", buildDate='" + buildDate + '\'' +
                ", buildVersion='" + buildVersion + '\'' +
                '}';
    }
}
