/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.impl.session;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class SessionDetails {

    @JsonProperty
    private String userName;
    @JsonProperty
    private long createMs;
    @JsonProperty
    private long lastAccessedMs;
    @JsonProperty
    private String lastAccessedAgent;
    @JsonProperty
    private String nodeName;

    @JsonCreator
    public SessionDetails(@JsonProperty("username") final String userName,
                   @JsonProperty("createMs") final long createMs,
                   @JsonProperty("lastAccessedMss") final long lastAccessedMs,
                   @JsonProperty("lastAccessedAgent") final String lastAccessedAgent,
                   @JsonProperty("nodeName") final String nodeName) {
        this.userName = userName;
        this.createMs = createMs;
        this.lastAccessedMs = lastAccessedMs;
        this.lastAccessedAgent = lastAccessedAgent;
        this.nodeName = nodeName;
    }

    public String getUserName() {
        return userName;
    }

    public long getCreateMs() {
        return createMs;
    }

    public long getLastAccessedMs() {
        return lastAccessedMs;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getLastAccessedAgent() {
        return lastAccessedAgent;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SessionDetails that = (SessionDetails) o;
        return createMs == that.createMs &&
                lastAccessedMs == that.lastAccessedMs &&
                Objects.equals(userName, that.userName) &&
                Objects.equals(lastAccessedAgent, that.lastAccessedAgent) &&
                Objects.equals(nodeName, that.nodeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, createMs, lastAccessedMs, lastAccessedAgent, nodeName);
    }

    @Override
    public String toString() {
        return "SessionDetails{" +
                "userName='" + userName + '\'' +
                ", createMs=" + createMs +
                ", lastAccessedMs=" + lastAccessedMs +
                ", lastAccessedAgent='" + lastAccessedAgent + '\'' +
                ", nodeName='" + nodeName + '\'' +
                '}';
    }
}
