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


import stroom.util.shared.AbstractHasAuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a user or a named group of users.
 */
@JsonInclude(Include.NON_NULL)
public class User implements HasAuditInfoGetters, HasIntegerId, HasUserRef {

    /**
     * The unique subjectId of the default Admin user that gets created if
     * auth is disabled.
     */
    public static final String ADMIN_USER_SUBJECT_ID = "admin";
    /**
     * The unique subjectId of the default Administrators group that
     * {@link User#ADMIN_USER_SUBJECT_ID}
     * is a member of. It is also the default group for content auto import on boot.
     */
    public static final String ADMINISTRATORS_GROUP_SUBJECT_ID = "Administrators";

    @JsonProperty
    private final Integer id;
    @JsonProperty
    private final Integer version;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String updateUser;
    @JsonProperty
    private final String subjectId;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String displayName;
    @JsonProperty
    private final String fullName;
    @JsonProperty
    private final boolean enabled;

    /**
     * Is this user a user group or a regular user?
     */
    @JsonProperty
    private final boolean group;

    @JsonCreator
    public User(@JsonProperty("id") final Integer id,
                @JsonProperty("version") final Integer version,
                @JsonProperty("createTimeMs") final Long createTimeMs,
                @JsonProperty("createUser") final String createUser,
                @JsonProperty("updateTimeMs") final Long updateTimeMs,
                @JsonProperty("updateUser") final String updateUser,
                @JsonProperty("subjectId") final String subjectId,
                @JsonProperty("uuid") final String uuid,
                @JsonProperty("group") final boolean group,
                @JsonProperty("displayName") final String displayName,
                @JsonProperty("fullName") final String fullName,
                @JsonProperty("enabled") final boolean enabled) {
        // Ensure we always have trimmed user identity values
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.subjectId = NullSafe.get(subjectId, String::trim);
        this.uuid = uuid;
        this.group = group;
        this.displayName = NullSafe.get(displayName, String::trim);
        this.fullName = NullSafe.get(fullName, String::trim);
        this.enabled = enabled;
    }

    /**
     * @return The primary key for the user in the database. Un-related to any IDP value.
     * Not used in the UI.
     */
    @Override
    public Integer getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    /**
     * <p>If this is a user then {@code subjectId} is also the unique identifier for the user on the
     * OpenIdConnect IDP, i.e. the subject. The value may be a UUID or a more human friendly form
     * depending on the IDP in use (internal/external).</p>
     *
     * <p>If {@code isGroup} is {@code true} then this is the unique identifier of the group.
     * A group identifier is defined by the user so is likely to be human friendly.
     * A user and a group can share the same name.</p>
     *
     * @return The unique identifier for this user or group.
     */
    public String getSubjectId() {
        return subjectId;
    }

    /**
     * @return An optional, potentially non-unique, more human friendly username for the user.
     * Will be null if this is a group or the IDP does not provide one
     * or one has not been set for the user.
     * Intended for display purposes only or to aid in identifying the user where {@code name}
     * is an unfriendly UUID.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return An optional, non-unique, full name in displayable form including all name parts,
     * possibly including titles and suffixes, ordered according to the End-User's locale and
     * preferences.
     * Will be null if this is a group or the IDP does not provide a full-name
     * or one has not been set for the user.
     * Intended for display purposes only or to aid in identifying the user where {@code name}
     * is an unfriendly UUID.
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * @return A globally unique identifier for identifying this user in other areas of stroom code.
     * Unrelated to any subjectId that an IDP may use to identify the user.
     * Unique across both users and groups, unlike {@code name}.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * @return True if this object represents a named user-group instead of a user.
     */
    public boolean isGroup() {
        return group;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UserRef asRef() {
        return new UserRef(uuid, subjectId, displayName, fullName, group, enabled);
    }

    @JsonIgnore
    @Override
    public UserRef getUserRef() {
        return asRef();
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
    public String toString() {
        return "User{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", name='" + subjectId + '\'' +
               ", uuid='" + uuid + '\'' +
               ", displayName='" + displayName + '\'' +
               ", fullName='" + fullName + '\'' +
               ", group=" + group +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final User user = (User) o;
        return uuid.equals(user.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // -------------------------------------------------------------------------


    public static final class Builder extends AbstractHasAuditInfoBuilder<User, User.Builder> {

        private Integer id;
        private Integer version;
        private String subjectId;
        private String uuid;
        private boolean group;
        private String displayName;
        private String fullName;
        private boolean enabled = true;

        private Builder() {
        }

        private Builder(final User user) {
            super(user);
            this.id = user.id;
            this.version = user.version;
            this.subjectId = user.subjectId;
            this.uuid = user.uuid;
            this.group = user.group;
            this.displayName = user.displayName;
            this.fullName = user.fullName;
            this.enabled = user.enabled;
        }

        public Builder id(final int value) {
            id = value;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        /**
         * A globally unique identifier for identifying this user in other areas of stroom code.
         */
        public Builder uuid(final String value) {
            uuid = value;
            return self();
        }

        /**
         * The unique identifier for this user on the IDP, i.e. the subject. May not be unique within stroom as
         * we may be using an external IDP but still using the internal IDP for processing user.
         * If isGroup is true then this is the name of the group.
         */
        public Builder subjectId(final String subjectId) {
            this.subjectId = NullSafe.get(subjectId, String::trim);
            return self();
        }

        public Builder displayName(final String displayName) {
            this.displayName = NullSafe.get(displayName, String::trim);
            return self();
        }

        public Builder fullName(final String fullName) {
            this.fullName = NullSafe.get(fullName, String::trim);
            return self();
        }

        /**
         * If value is true marks this {@link User} as a named user-group.
         */
        public Builder group(final boolean value) {
            group = value;
            return self();
        }

        public Builder enabled(final boolean value) {
            enabled = value;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public User build() {
            return new User(id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    subjectId,
                    uuid,
                    group,
                    displayName,
                    fullName,
                    enabled);
        }
    }
}
