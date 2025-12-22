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

import stroom.util.shared.string.CaseType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Holds the display information for a user that may or may not be an active stroom
 * user, i.e. the user may have been deleted.
 */
@SuppressWarnings("ClassCanBeRecord") // Cos GWT
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public final class UserInfo {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String subjectId;
    @JsonProperty
    private final String displayName;
    @JsonProperty
    private final String fullName;
    @JsonProperty
    private final boolean group;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final boolean deleted;

    @JsonCreator
    public UserInfo(@JsonProperty("uuid") final String uuid,
                    @JsonProperty("subjectId") final String subjectId,
                    @JsonProperty("displayName") final String displayName,
                    @JsonProperty("fullName") final String fullName,
                    @JsonProperty("group") final boolean group,
                    @JsonProperty("enabled") final boolean enabled,
                    @JsonProperty("deleted") final boolean deleted) {

        if (group && !enabled) {
            throw new IllegalArgumentException("Groups cannot be disabled. uuid: " + uuid);
        } else if (enabled && deleted) {
            throw new IllegalArgumentException("User can't be both enabled and deleted. uuid: " + uuid);
        }

        this.uuid = Objects.requireNonNull(uuid, "Null uuid provided to UserInfo");
        this.subjectId = Objects.requireNonNull(subjectId, "Null subjectId provided to UserInfo");
        this.displayName = Objects.requireNonNull(displayName, "Null displayName provided to UserInfo");
        this.fullName = fullName;
        this.group = group;
        this.enabled = enabled;
        this.deleted = deleted;
    }

    /**
     * The stroom_user UUID.
     * No relation to any UUID on the IDP.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * The unique ID for the user.
     * Maps to the IDP claim defined by
     * stroom.security.authentication.openId.uniqueIdentityClaim
     */
    public String getSubjectId() {
        return subjectId;
    }

    /**
     * The friendly display name for the user
     * Maps to the IDP claim defined by
     * stroom.security.authentication.openId.userDisplayNameClaim
     */
    public String getDisplayName() {
        return displayName;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isGroup() {
        return group;
    }

    @JsonIgnore
    public boolean isUser() {
        return !group;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @JsonIgnore
    public boolean isDisabled() {
        return !enabled;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /**
     * @return "Group" or "User" depending on type.
     */
    @JsonIgnore
    public String getType() {
        return getType(CaseType.SENTENCE);
    }

    public String getType(final CaseType caseType) {
        Objects.requireNonNull(caseType);
        final String type = group
                ? "Group"
                : "User";
        if (CaseType.SENTENCE == caseType) {
            return type;
        } else if (CaseType.LOWER == caseType) {
            return type.toLowerCase();
        } else if (CaseType.UPPER == caseType) {
            return type.toUpperCase();
        } else {
            throw new IllegalArgumentException("Unknown caseType: " + caseType);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserInfo userRef = (UserInfo) o;
        return Objects.equals(uuid, userRef.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return toDisplayString();
    }

    public String toDebugString() {
        return "UserRef{" +
               "uuid='" + uuid + '\'' +
               ", subjectId='" + subjectId + '\'' +
               ", displayName='" + displayName + '\'' +
               ", fullName='" + fullName + '\'' +
               ", group=" + group +
               ", enabled=" + enabled +
               '}';
    }

    public String toDisplayString() {
        return displayName;
    }

    @SuppressWarnings("SizeReplaceableByIsEmpty")
    public String toInfoString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(displayName);
        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append("{");
        sb.append(uuid);
        sb.append("}");

        if (sb.length() > 0) {
            return sb.toString();
        }

        return toString();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private String uuid;
        private String subjectId;
        private String displayName;
        private String fullName;
        private boolean group = false;
        private boolean enabled = true;
        private boolean deleted = false;

        private Builder() {
        }

        private Builder(final UserInfo userInfo) {
            this.uuid = userInfo.uuid;
            this.subjectId = userInfo.subjectId;
            this.displayName = userInfo.displayName;
            this.fullName = userInfo.fullName;
            this.group = userInfo.group;
            this.enabled = userInfo.enabled;
            this.deleted = userInfo.deleted;
        }

        /**
         * The stroom_user UUID.
         * No relation to any UUID on the IDP.
         */
        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return this;
        }

        /**
         * The unique ID for the user.
         * Maps to the IDP claim defined by
         * stroom.security.authentication.openId.uniqueIdentityClaim
         */
        public Builder subjectId(final String subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        /**
         * The friendly display name for the user
         * Maps to the IDP claim defined by
         * stroom.security.authentication.openId.userDisplayNameClaim
         */
        public Builder displayName(final String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder fullName(final String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder group(final boolean group) {
            this.group = group;
            return this;
        }

        public Builder group() {
            this.group = true;
            return this;
        }

        public Builder user() {
            this.group = false;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder enabled() {
            this.enabled = true;
            return this;
        }

        public Builder disabled() {
            this.enabled = false;
            return this;
        }

        public Builder deleted(final boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public Builder deleted() {
            this.deleted = true;
            return this;
        }

        public Builder active() {
            this.deleted = false;
            return this;
        }

        public UserInfo build() {
            Objects.requireNonNull(uuid, "Null UUID");
            if (group && !enabled) {
                throw new IllegalArgumentException("Groups cannot be disabled");
            }
            return new UserInfo(uuid, subjectId, displayName, fullName, group, enabled, deleted);
        }
    }
}
