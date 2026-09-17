/*
 * Copyright 2026 Crown Copyright
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

import java.util.List;
import java.util.Objects;

/**
 * One subject's live access, as shown on the administrative list: the sessions they hold and the tokens the
 * internal IdP has issued them, side by side.
 * <p>
 * Keyed on the subject rather than on a session, because a subject can hold live tokens while having <b>no</b>
 * session at all. On a session-keyed list those subjects would be invisible, and therefore impossible to find in
 * order to revoke.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAccessRow {

    @JsonProperty
    private final String subjectId;
    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final String displayName;
    @JsonProperty
    private final int sessionCount;
    @JsonProperty
    private final List<String> nodeNames;
    @JsonProperty
    private final Long lastAccessedMs;
    @JsonProperty
    private final int tokenCount;
    @JsonProperty
    private final Long nextTokenExpiryMs;
    @JsonProperty
    private final Long lastTokenExpiryMs;

    @JsonCreator
    public UserAccessRow(@JsonProperty("subjectId") final String subjectId,
                         @JsonProperty("userRef") final UserRef userRef,
                         @JsonProperty("displayName") final String displayName,
                         @JsonProperty("sessionCount") final int sessionCount,
                         @JsonProperty("nodeNames") final List<String> nodeNames,
                         @JsonProperty("lastAccessedMs") final Long lastAccessedMs,
                         @JsonProperty("tokenCount") final int tokenCount,
                         @JsonProperty("nextTokenExpiryMs") final Long nextTokenExpiryMs,
                         @JsonProperty("lastTokenExpiryMs") final Long lastTokenExpiryMs) {
        this.subjectId = subjectId;
        this.userRef = userRef;
        this.displayName = displayName;
        this.sessionCount = sessionCount;
        this.nodeNames = nodeNames;
        this.lastAccessedMs = lastAccessedMs;
        this.tokenCount = tokenCount;
        this.nextTokenExpiryMs = nextTokenExpiryMs;
        this.lastTokenExpiryMs = lastTokenExpiryMs;
    }

    /**
     * The IdP subject. Always present - it is the only identifier guaranteed to exist for every row, which is
     * why it, and not the user uuid, is the key.
     */
    public String getSubjectId() {
        return subjectId;
    }

    /**
     * The stroom user, when there is one.
     * <p>
     * Null for subjects with no {@code stroom_user} row - service and external subjects can hold tokens without
     * ever having been made a stroom user. Such rows are deliberately kept rather than dropped: they are exactly
     * the accounts most worth being able to see and revoke.
     * </p>
     */
    public UserRef getUserRef() {
        return userRef;
    }

    /**
     * What to show in the UI. Never null: falls back to the subject id when the subject cannot be resolved to a
     * user, so that a row is always identifiable and always matchable by the quick filter.
     */
    public String getDisplayName() {
        return displayName;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    /**
     * The nodes holding this subject's sessions. Sessions are per-node in-memory state, so a user may appear on
     * several.
     */
    public List<String> getNodeNames() {
        return nodeNames;
    }

    /**
     * The most recent access across all of this subject's sessions, or null if they have none.
     */
    public Long getLastAccessedMs() {
        return lastAccessedMs;
    }

    /**
     * How many usable tokens the internal IdP has issued this subject. Always zero when an external IdP is in
     * use, since nothing is minted here.
     */
    public int getTokenCount() {
        return tokenCount;
    }

    public Long getNextTokenExpiryMs() {
        return nextTokenExpiryMs;
    }

    /**
     * When this subject's token-based access would lapse on its own, or null if they hold none. Usually driven
     * by a refresh token, so typically weeks rather than the access token's hour.
     */
    public Long getLastTokenExpiryMs() {
        return lastTokenExpiryMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final UserAccessRow that)) {
            return false;
        }
        return Objects.equals(subjectId, that.subjectId)
               && sessionCount == that.sessionCount
               && tokenCount == that.tokenCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, sessionCount, tokenCount);
    }

    @Override
    public String toString() {
        return "UserAccessRow{" +
               "subjectId='" + subjectId + '\'' +
               ", displayName='" + displayName + '\'' +
               ", sessionCount=" + sessionCount +
               ", tokenCount=" + tokenCount +
               '}';
    }
}
