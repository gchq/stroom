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

package stroom.processor.shared;

import stroom.query.api.UserTimeZone;
import stroom.util.shared.AuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasIntegerId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "version",
        "createTimeMs",
        "createUser",
        "updateTimeMs",
        "updateUser",
        "name",
        "nodeGroupName",
        "profilePeriods",
        "timeZone"})
public class ProcessorProfile implements HasIntegerId, HasAuditInfoGetters {

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
    private final String nodeGroupName;
    @JsonProperty
    private final List<ProfilePeriod> profilePeriods;
    @JsonProperty
    private final UserTimeZone timeZone;

    @JsonCreator
    public ProcessorProfile(@JsonProperty("id") final Integer id,
                            @JsonProperty("version") final Integer version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("name") final String name,
                            @JsonProperty("nodeGroupName") final String nodeGroupName,
                            @JsonProperty("profilePeriods") final List<ProfilePeriod> profilePeriods,
                            @JsonProperty("timeZone") final UserTimeZone timeZone) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
        this.nodeGroupName = nodeGroupName;
        this.profilePeriods = profilePeriods;
        this.timeZone = timeZone;
    }

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

    public String getName() {
        return name;
    }

    public String getNodeGroupName() {
        return nodeGroupName;
    }

    public List<ProfilePeriod> getProfilePeriods() {
        return profilePeriods;
    }

    public UserTimeZone getTimeZone() {
        return timeZone;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProcessorProfile that = (ProcessorProfile) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(version, that.version) &&
               Objects.equals(createTimeMs, that.createTimeMs) &&
               Objects.equals(createUser, that.createUser) &&
               Objects.equals(updateTimeMs, that.updateTimeMs) &&
               Objects.equals(updateUser, that.updateUser) &&
               Objects.equals(name, that.name) &&
               Objects.equals(nodeGroupName, that.nodeGroupName) &&
               Objects.equals(profilePeriods, that.profilePeriods) &&
               Objects.equals(timeZone, that.timeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id,
                version,
                createTimeMs,
                createUser,
                updateTimeMs,
                updateUser,
                name,
                nodeGroupName,
                profilePeriods,
                timeZone);
    }

    @Override
    public String toString() {
        return "ProcessorProfile{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", name='" + name + '\'' +
               ", nodeGroupName='" + nodeGroupName + '\'' +
               ", concurrencyTimeRanges=" + profilePeriods +
               ", timeZone=" + timeZone +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AuditInfoBuilder<ProcessorProfile, Builder> {

        private Integer id;
        private Integer version;
        private String name;
        private String nodeGroupName;
        private List<ProfilePeriod> profilePeriods;
        private UserTimeZone timeZone;

        private Builder() {
        }

        private Builder(final ProcessorProfile processorProfile) {
            this.id = processorProfile.id;
            this.version = processorProfile.version;
            this.createTimeMs = processorProfile.createTimeMs;
            this.createUser = processorProfile.createUser;
            this.updateTimeMs = processorProfile.updateTimeMs;
            this.updateUser = processorProfile.updateUser;
            this.name = processorProfile.name;
            this.nodeGroupName = processorProfile.nodeGroupName;
            this.profilePeriods = processorProfile.profilePeriods;
            this.timeZone = processorProfile.timeZone;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder nodeGroupName(final String nodeGroupName) {
            this.nodeGroupName = nodeGroupName;
            return self();
        }

        public Builder profilePeriods(final List<ProfilePeriod> profilePeriods) {
            this.profilePeriods = profilePeriods;
            return self();
        }

        public Builder timeZone(final UserTimeZone timeZone) {
            this.timeZone = timeZone;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ProcessorProfile build() {
            return new ProcessorProfile(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    name,
                    nodeGroupName,
                    profilePeriods,
                    timeZone);
        }
    }
}
