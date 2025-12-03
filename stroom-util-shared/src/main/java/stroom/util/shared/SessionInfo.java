/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SessionInfo {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final BuildInfo buildInfo;

    @JsonCreator
    public SessionInfo(@JsonProperty("userRef") final UserRef userRef,
                       @JsonProperty("nodeName") final String nodeName,
                       @JsonProperty("buildInfo") final BuildInfo buildInfo) {
        this.userRef = userRef;
        this.nodeName = nodeName;
        this.buildInfo = buildInfo;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public String getNodeName() {
        return nodeName;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SessionInfo that = (SessionInfo) o;
        return Objects.equals(userRef, that.userRef) &&
                Objects.equals(nodeName, that.nodeName) &&
                Objects.equals(buildInfo, that.buildInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userRef, nodeName, buildInfo);
    }

    @Override
    public String toString() {
        return "SessionInfo{" +
                "userRef='" + userRef + '\'' +
                ", nodeName='" + nodeName + '\'' +
                ", buildInfo=" + buildInfo +
                '}';
    }
}
