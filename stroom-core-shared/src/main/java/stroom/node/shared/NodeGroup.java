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

package stroom.node.shared;

import stroom.util.shared.AuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasIntegerId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class NodeGroup implements HasAuditInfoGetters, HasIntegerId {

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
    private final boolean enabled;

    @JsonCreator
    public NodeGroup(@JsonProperty("id") final Integer id,
                     @JsonProperty("version") final Integer version,
                     @JsonProperty("createTimeMs") final Long createTimeMs,
                     @JsonProperty("createUser") final String createUser,
                     @JsonProperty("updateTimeMs") final Long updateTimeMs,
                     @JsonProperty("updateUser") final String updateUser,
                     @JsonProperty("name") final String name,
                     @JsonProperty("enabled") final Boolean enabled) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
        this.enabled = enabled;
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

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeGroup nodeGroup = (NodeGroup) o;
        return enabled == nodeGroup.enabled &&
               Objects.equals(id, nodeGroup.id) &&
               Objects.equals(version, nodeGroup.version) &&
               Objects.equals(createTimeMs, nodeGroup.createTimeMs) &&
               Objects.equals(createUser, nodeGroup.createUser) &&
               Objects.equals(updateTimeMs, nodeGroup.updateTimeMs) &&
               Objects.equals(updateUser, nodeGroup.updateUser) &&
               Objects.equals(name, nodeGroup.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, createTimeMs, createUser, updateTimeMs, updateUser, name, enabled);
    }

    @Override
    public String toString() {
        return "NodeGroup{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", name='" + name + '\'' +
               ", enabled=" + enabled +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AuditInfoBuilder<NodeGroup, Builder> {

        private Integer id;
        private Integer version;
        private String name;
        private boolean enabled;

        private Builder() {
        }

        private Builder(final NodeGroup nodeGroup) {
            this.id = nodeGroup.id;
            this.version = nodeGroup.version;
            this.createTimeMs = nodeGroup.createTimeMs;
            this.createUser = nodeGroup.createUser;
            this.updateTimeMs = nodeGroup.updateTimeMs;
            this.updateUser = nodeGroup.updateUser;
            this.name = nodeGroup.name;
            this.enabled = nodeGroup.enabled;
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

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public NodeGroup build() {
            return new NodeGroup(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    name,
                    enabled);
        }
    }
}
