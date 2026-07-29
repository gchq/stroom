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

package stroom.security.shared;

import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDetails {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final long createMs;
    @JsonProperty
    private final long lastAccessedMs;
    @JsonProperty
    private final String lastAccessedAgent;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final String sessionHandle;

    @JsonCreator
    public SessionDetails(@JsonProperty("userRef") final UserRef userRef,
                          @JsonProperty("createMs") final long createMs,
                          @JsonProperty("lastAccessedMs") final long lastAccessedMs,
                          @JsonProperty("lastAccessedAgent") final String lastAccessedAgent,
                          @JsonProperty("nodeName") final String nodeName,
                          @JsonProperty("sessionHandle") final String sessionHandle) {
        this.userRef = userRef;
        this.createMs = createMs;
        this.lastAccessedMs = lastAccessedMs;
        this.lastAccessedAgent = lastAccessedAgent;
        this.nodeName = nodeName;
        this.sessionHandle = sessionHandle;
    }

    public UserRef getUserRef() {
        return userRef;
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

    /**
     * An opaque handle identifying this session, for use with
     * {@link SessionResource#terminateSession(String, String)}.
     * <p>
     * <b>Deliberately not the session id.</b> A session id is the value of the session cookie, so returning one
     * would hand the caller a credential they could replay to impersonate that user - turning the Manage Users
     * permission into the ability to become anybody. This is a one-way hash of the id instead: it is stable
     * enough to name a session across a list-then-terminate round trip, and useless as a credential.
     * </p>
     * <p>
     * Guessing a handle gains nothing either. The only thing it can be used for is termination, which is already
     * gated on owning the session or holding Manage Users, and the worst outcome is signing somebody out.
     * </p>
     */
    public String getSessionHandle() {
        return sessionHandle;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SessionDetails that = (SessionDetails) o;
        return createMs == that.createMs &&
                lastAccessedMs == that.lastAccessedMs &&
                Objects.equals(userRef, that.userRef) &&
                Objects.equals(lastAccessedAgent, that.lastAccessedAgent) &&
                Objects.equals(nodeName, that.nodeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userRef, createMs, lastAccessedMs, lastAccessedAgent, nodeName);
    }

    @Override
    public String toString() {
        return "SessionDetails{" +
                "userRef='" + userRef + '\'' +
                ", createMs=" + createMs +
                ", lastAccessedMs=" + lastAccessedMs +
                ", lastAccessedAgent='" + lastAccessedAgent + '\'' +
                ", nodeName='" + nodeName + '\'' +
                '}';
    }
}
