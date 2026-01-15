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
import java.util.function.Function;

@SuppressWarnings("ClassCanBeRecord") // Cos GWT
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "uuid",
        "subjectId",
        "displayName",
        "fullName",
        "group",
        "enabled"
})
public final class UserRef {

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

    @JsonCreator
    public UserRef(@JsonProperty("uuid") final String uuid,
                   @JsonProperty("subjectId") final String subjectId,
                   @JsonProperty("displayName") final String displayName,
                   @JsonProperty("fullName") final String fullName,
                   @JsonProperty("group") final boolean group,
                   @JsonProperty("enabled") final boolean enabled) {
        if (group && !enabled) {
            throw new IllegalArgumentException("Groups cannot be disabled");
        }

        this.uuid = uuid;
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.fullName = fullName;
        this.group = group;
        this.enabled = enabled;
    }

    /**
     * Creates a {@link UserRef} representing a user (not a group).
     */
    public static UserRef forUserUuid(final String userUuid) {
        return new UserRef(userUuid, null, null, null, false, true);
    }

    /**
     * Creates a {@link UserRef} representing a group.
     */
    public static UserRef forGroupUuid(final String groupUuid) {
        return new UserRef(groupUuid, null, null, null, true, true);
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
        final UserRef userRef = (UserRef) o;
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

    public String toDisplayString(final DisplayType displayType) {
        final Function<UserRef, String> displayTextFunc = NullSafe.requireNonNullElse(
                        displayType, DisplayType.AUTO)
                .getDisplayTextFunc();
        return displayTextFunc.apply(this);
    }

    public String toDisplayString() {
        if (displayName != null) {
            return displayName;
        } else if (subjectId != null) {
            return subjectId;
        } else {
            return NullSafe.requireNonNullElseGet(fullName, () ->
                    "{" + uuid + "}");
        }
    }

    @SuppressWarnings("SizeReplaceableByIsEmpty")
    public String toInfoString() {
        final StringBuilder sb = new StringBuilder();
        if (displayName != null) {
            sb.append(displayName);
        } else if (subjectId != null) {
            sb.append(subjectId);
        } else if (fullName != null) {
            sb.append(fullName);
        }
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


    public enum DisplayType {
        /**
         * Displays the first non-null value in this order:
         * <p>displayName</p>
         * <p>SubjectId</p>
         * <p>UUID surrounded in curly braces</p>
         * <p></p>
         * <p>This displayType is equivalent to calling {@link UserRef#toDisplayString()}</p>
         */
        AUTO(UserRef::toDisplayString, "value"),
        UUID(UserRef::getUuid, "UUID"),
        SUBJECT_ID(UserRef::getSubjectId, "unique ID"),
        DISPLAY_NAME(UserRef::getDisplayName, "display name"),
        FULL_NAME(UserRef::getFullName, "full name");

        private final Function<UserRef, String> displayTextFunc;
        private final String typeName;

        DisplayType(final Function<UserRef, String> displayTextFunc,
                    final String typeName) {
            this.displayTextFunc = displayTextFunc;
            this.typeName = typeName;
        }

        public Function<UserRef, String> getDisplayTextFunc() {
            return displayTextFunc;
        }

        /**
         * @return The name of the display type for use in UI text.
         */
        public String getTypeName() {
            return typeName;
        }
    }

    // --------------------------------------------------------------------------------


    public static class Builder {

        private String uuid;
        private String subjectId;
        private String displayName;
        private String fullName;
        private boolean group = false;
        private boolean enabled = true;

        private Builder() {
        }

        private Builder(final UserRef userRef) {
            this.uuid = userRef.uuid;
            this.subjectId = userRef.subjectId;
            this.displayName = userRef.displayName;
            this.fullName = userRef.fullName;
            this.group = userRef.group;
            this.enabled = userRef.enabled;
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

        public UserRef build() {
            if (group && !enabled) {
                throw new IllegalArgumentException("Groups cannot be disabled");
            }
            return new UserRef(uuid, subjectId, displayName, fullName, group, enabled);
        }
    }
}
