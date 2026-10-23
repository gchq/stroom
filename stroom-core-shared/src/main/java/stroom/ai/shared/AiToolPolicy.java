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

package stroom.ai.shared;

import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A tool policy: which categories of Stroom's tools the users and groups it covers may use, in the "Ask Stroom AI"
/// chat and over MCP, and for each, whether only the tools (and calls) that cannot change anything, or all of them.
/// A user gets the union of the enabled policies that name them, a group they are in, or all users; with none, they
/// get no tools. A policy only narrows: each tool still checks the user's other permissions.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiToolPolicy {

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
    private final String name;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final boolean allUsers;
    @JsonProperty
    private final List<UserRef> members;
    @JsonProperty
    private final List<AiToolCategoryGrant> grants;

    /// @param id           The policy's id, or null for one not yet saved.
    /// @param version      Its version, for optimistic locking, or null for one not yet saved.
    /// @param createTimeMs When it was made.
    /// @param createUser   Who made it.
    /// @param updateTimeMs When it was last changed.
    /// @param updateUser   Who last changed it.
    /// @param name         Its name, unique among policies.
    /// @param description  What it is for, or null.
    /// @param enabled      False if it grants nothing for now.
    /// @param allUsers     True if it covers every user, whatever its members.
    /// @param members      The users and groups it covers.
    /// @param grants       The categories of tools it grants, each with how much of it.
    @JsonCreator
    public AiToolPolicy(@JsonProperty("id") final Integer id,
                        @JsonProperty("version") final Integer version,
                        @JsonProperty("createTimeMs") final Long createTimeMs,
                        @JsonProperty("createUser") final String createUser,
                        @JsonProperty("updateTimeMs") final Long updateTimeMs,
                        @JsonProperty("updateUser") final String updateUser,
                        @JsonProperty("name") final String name,
                        @JsonProperty("description") final String description,
                        @JsonProperty("enabled") final Boolean enabled,
                        @JsonProperty("allUsers") final Boolean allUsers,
                        @JsonProperty("members") final List<UserRef> members,
                        @JsonProperty("grants") final List<AiToolCategoryGrant> grants) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
        this.description = description;
        this.enabled = enabled == null || enabled;
        this.allUsers = allUsers != null && allUsers;
        this.members = NullSafe.list(members);
        this.grants = NullSafe.list(grants);
    }

    /// @return A builder for a new policy.
    public static Builder builder() {
        return new Builder();
    }

    /// @return A builder that starts from this policy.
    public Builder copy() {
        return new Builder(this);
    }

    /// @return The policy's id, or null for one not yet saved.
    public Integer getId() {
        return id;
    }

    /// @return Its version, for optimistic locking, or null for one not yet saved.
    public Integer getVersion() {
        return version;
    }

    /// @return When it was made.
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    /// @return Who made it.
    public String getCreateUser() {
        return createUser;
    }

    /// @return When it was last changed.
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    /// @return Who last changed it.
    public String getUpdateUser() {
        return updateUser;
    }

    /// @return Its name, unique among policies.
    public String getName() {
        return name;
    }

    /// @return What it is for, or null.
    public String getDescription() {
        return description;
    }

    /// @return False if it grants nothing for now.
    public boolean isEnabled() {
        return enabled;
    }

    /// @return True if it covers every user, whatever its members.
    public boolean isAllUsers() {
        return allUsers;
    }

    /// @return The users and groups it covers.
    public List<UserRef> getMembers() {
        return members;
    }

    /// @return The categories of tools it grants, each with how much of it.
    public List<AiToolCategoryGrant> getGrants() {
        return grants;
    }

    /// @param category A category of tools.
    /// @return How much of it the policy grants, or null if it doesn't grant it.
    public AiToolAccess accessTo(final AiToolCategory category) {
        return grants.stream()
                .filter(grant -> grant.getCategory() == category)
                .map(AiToolCategoryGrant::getAccess)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiToolPolicy that = (AiToolPolicy) o;
        return enabled == that.enabled
               && allUsers == that.allUsers
               && Objects.equals(id, that.id)
               && Objects.equals(version, that.version)
               && Objects.equals(name, that.name)
               && Objects.equals(description, that.description)
               && Objects.equals(members, that.members)
               && Objects.equals(grants, that.grants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, name, description, enabled, allUsers, members, grants);
    }

    @Override
    public String toString() {
        return "AiToolPolicy{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", enabled=" + enabled +
               ", allUsers=" + allUsers +
               ", members=" + members +
               ", grants=" + grants +
               '}';
    }


    // --------------------------------------------------------------------------------


    /// Builds an [AiToolPolicy].
    public static final class Builder {

        private Integer id;
        private Integer version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private String name;
        private String description;
        private boolean enabled = true;
        private boolean allUsers;
        private List<UserRef> members = new ArrayList<>();
        private List<AiToolCategoryGrant> grants = new ArrayList<>();

        private Builder() {
        }

        private Builder(final AiToolPolicy policy) {
            this.id = policy.id;
            this.version = policy.version;
            this.createTimeMs = policy.createTimeMs;
            this.createUser = policy.createUser;
            this.updateTimeMs = policy.updateTimeMs;
            this.updateUser = policy.updateUser;
            this.name = policy.name;
            this.description = policy.description;
            this.enabled = policy.enabled;
            this.allUsers = policy.allUsers;
            this.members = new ArrayList<>(policy.members);
            this.grants = new ArrayList<>(policy.grants);
        }

        /// @param id See [AiToolPolicy#getId()].
        /// @return This builder.
        public Builder id(final Integer id) {
            this.id = id;
            return this;
        }

        /// @param version See [AiToolPolicy#getVersion()].
        /// @return This builder.
        public Builder version(final Integer version) {
            this.version = version;
            return this;
        }

        /// @param createTimeMs See [AiToolPolicy#getCreateTimeMs()].
        /// @return This builder.
        public Builder createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return this;
        }

        /// @param createUser See [AiToolPolicy#getCreateUser()].
        /// @return This builder.
        public Builder createUser(final String createUser) {
            this.createUser = createUser;
            return this;
        }

        /// @param updateTimeMs See [AiToolPolicy#getUpdateTimeMs()].
        /// @return This builder.
        public Builder updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return this;
        }

        /// @param updateUser See [AiToolPolicy#getUpdateUser()].
        /// @return This builder.
        public Builder updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return this;
        }

        /// @param name See [AiToolPolicy#getName()].
        /// @return This builder.
        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        /// @param description See [AiToolPolicy#getDescription()].
        /// @return This builder.
        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        /// @param enabled See [AiToolPolicy#isEnabled()].
        /// @return This builder.
        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /// @param allUsers See [AiToolPolicy#isAllUsers()].
        /// @return This builder.
        public Builder allUsers(final boolean allUsers) {
            this.allUsers = allUsers;
            return this;
        }

        /// @param members See [AiToolPolicy#getMembers()].
        /// @return This builder.
        public Builder members(final List<UserRef> members) {
            this.members = new ArrayList<>(NullSafe.list(members));
            return this;
        }

        /// @param grants See [AiToolPolicy#getGrants()].
        /// @return This builder.
        public Builder grants(final List<AiToolCategoryGrant> grants) {
            this.grants = new ArrayList<>(NullSafe.list(grants));
            return this;
        }

        /// @return The policy.
        public AiToolPolicy build() {
            return new AiToolPolicy(id, version, createTimeMs, createUser, updateTimeMs, updateUser, name,
                    description, enabled, allUsers, members, grants);
        }
    }
}
