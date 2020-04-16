package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SessionInfo {
    @JsonProperty
    private final String userName;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final BuildInfo buildInfo;

    @JsonCreator
    public SessionInfo(@JsonProperty("userName") final String userName,
                       @JsonProperty("nodeName") final String nodeName,
                       @JsonProperty("buildInfo") final BuildInfo buildInfo) {
        this.userName = userName;
        this.nodeName = nodeName;
        this.buildInfo = buildInfo;
    }

    public String getUserName() {
        return userName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SessionInfo that = (SessionInfo) o;
        return Objects.equals(userName, that.userName) &&
                Objects.equals(nodeName, that.nodeName) &&
                Objects.equals(buildInfo, that.buildInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, nodeName, buildInfo);
    }

    @Override
    public String toString() {
        return "SessionInfo{" +
                "userName='" + userName + '\'' +
                ", nodeName='" + nodeName + '\'' +
                ", buildInfo=" + buildInfo +
                '}';
    }
}
