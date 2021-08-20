package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class BuildInfo {

    @JsonProperty
    private final long upTime;
    @JsonProperty
    private final long buildTime;
    @JsonProperty
    private final String buildVersion;

    @JsonCreator
    public BuildInfo(@JsonProperty("upTime") final long upTime,
                     @JsonProperty("buildVersion") final String buildVersion,
                     @JsonProperty("buildTime") final long buildTime) {
        this.upTime = upTime;
        this.buildVersion = buildVersion;
        this.buildTime = buildTime;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public long getBuildTime() {
        return buildTime;
    }

    public long getUpTime() {
        return upTime;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BuildInfo)) {
            return false;
        }
        final BuildInfo buildInfo = (BuildInfo) o;
        return upTime == buildInfo.upTime && buildTime == buildInfo.buildTime && Objects.equals(buildVersion,
                buildInfo.buildVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(upTime, buildTime, buildVersion);
    }

    @Override
    public String toString() {
        return "BuildInfo{" +
                "upTime='" + upTime + '\'' +
                ", buildTime='" + buildTime + '\'' +
                ", buildVersion='" + buildVersion + '\'' +
                '}';
    }
}
