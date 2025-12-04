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


import stroom.util.shared.HasAuditInfo;
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
public class User implements HasAuditInfo, HasIntegerId, HasUserRef {

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
    private Integer id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private Long updateTimeMs;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private String subjectId;
    @JsonProperty
    private String uuid;
    @JsonProperty
    private String displayName;
    @JsonProperty
    private String fullName;
    @JsonProperty
    private boolean enabled;

    /**
     * Is this user a user group or a regular user?
     */
    @JsonProperty
    private boolean group;

    public User() {
    }

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

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
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
     * See {@link User#getSubjectId()}
     */
    public void setSubjectId(final String subjectId) {
        this.subjectId = NullSafe.get(subjectId, String::trim);
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
     * See {@link User#getDisplayName()}
     */
    public void setDisplayName(final String displayName) {
        this.displayName = NullSafe.get(displayName, String::trim);
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
     * See {@link User#getFullName()}
     */
    public void setFullName(final String fullName) {
        this.fullName = NullSafe.get(fullName, String::trim);
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
     * See {@link User#getUuid()}
     */
    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    /**
     * @return True if this object represents a named user-group instead of a user.
     */
    public boolean isGroup() {
        return group;
    }

    public void setGroup(final boolean group) {
        this.group = group;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
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


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private Integer id;
        private Integer version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private String subjectId;
        private String uuid;
        private boolean group;
        private String displayName;
        private String fullName;
        private boolean enabled = true;

        private Builder() {
        }

        private Builder(final User user) {
            this.id = user.id;
            this.version = user.version;
            this.createTimeMs = user.createTimeMs;
            this.createUser = user.createUser;
            this.updateTimeMs = user.updateTimeMs;
            this.updateUser = user.updateUser;
            this.subjectId = user.subjectId;
            this.uuid = user.uuid;
            this.group = user.group;
            this.displayName = user.displayName;
            this.fullName = user.fullName;
            this.enabled = user.enabled;
        }

        public Builder id(final int value) {
            id = value;
            return this;
        }

        /**
         * A globally unique identifier for identifying this user in other areas of stroom code.
         */
        public Builder uuid(final String value) {
            uuid = value;
            return this;
        }

        /**
         * The unique identifier for this user on the IDP, i.e. the subject. May not be unique within stroom as
         * we may be using an external IDP but still using the internal IDP for processing user.
         * If isGroup is true then this is the name of the group.
         */
        public Builder subjectId(final String subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        public Builder displayName(final String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder fullName(final String fullName) {
            this.fullName = fullName;
            return this;
        }

        /**
         * If value is true marks this {@link User} as a named user-group.
         */
        public Builder group(final boolean value) {
            group = value;
            return this;
        }

        public Builder enabled(final boolean value) {
            enabled = value;
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
