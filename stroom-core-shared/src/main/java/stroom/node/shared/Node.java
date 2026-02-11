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


import stroom.util.shared.AbstractHasAuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasIntegerId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Represents a node for storage and processing.
 */
@JsonPropertyOrder({
        "id",
        "version",
        "createTimeMs",
        "createUser",
        "updateTimeMs",
        "updateUser",
        "name",
        "url",
        "priority",
        "enabled",
        "buildVersion",
        "lastBootMs"})
@JsonInclude(Include.NON_NULL)
public class Node implements HasAuditInfoGetters, HasIntegerId {

    public static final String ENTITY_TYPE = "Node";

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
    private final String url;

    /**
     * A number to imply how important that this node is from the POV of being a
     * master node. The bigger the number the better chance it will become a
     * master
     */
    @JsonProperty
    private final int priority;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final String buildVersion;
    @JsonProperty
    private final Long lastBootMs;

    @JsonCreator
    public Node(@JsonProperty("id") final Integer id,
                @JsonProperty("version") final Integer version,
                @JsonProperty("createTimeMs") final Long createTimeMs,
                @JsonProperty("createUser") final String createUser,
                @JsonProperty("updateTimeMs") final Long updateTimeMs,
                @JsonProperty("updateUser") final String updateUser,
                @JsonProperty("name") final String name,
                @JsonProperty("url") final String url,
                @JsonProperty("priority") final Integer priority,
                @JsonProperty("enabled") final Boolean enabled,
                @JsonProperty("buildVersion") final String buildVersion,
                @JsonProperty("lastBootMs") final Long lastBootMs) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
        this.url = url;
        this.priority = priority;
        this.enabled = enabled;
        this.buildVersion = buildVersion;
        this.lastBootMs = lastBootMs;
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

    public String getUrl() {
        return url;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public Long getLastBootMs() {
        return lastBootMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Node node = (Node) o;
        return priority == node.priority &&
               enabled == node.enabled &&
               Objects.equals(id, node.id) &&
               Objects.equals(version, node.version) &&
               Objects.equals(createTimeMs, node.createTimeMs) &&
               Objects.equals(createUser, node.createUser) &&
               Objects.equals(updateTimeMs, node.updateTimeMs) &&
               Objects.equals(updateUser, node.updateUser) &&
               Objects.equals(name, node.name) &&
               Objects.equals(url, node.url) &&
               Objects.equals(buildVersion, node.buildVersion) &&
               Objects.equals(lastBootMs, node.lastBootMs);
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
                url,
                priority,
                enabled,
                buildVersion,
                lastBootMs);
    }

    @Override
    public String toString() {
        return "Node{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", name='" + name + '\'' +
               ", url='" + url + '\'' +
               ", priority=" + priority +
               ", enabled=" + enabled +
               ", buildVersion=" + buildVersion +
               ", lastBootMs=" + lastBootMs +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder extends AbstractHasAuditInfoBuilder<Node, Node.Builder> {

        private Integer id;
        private Integer version;
        private String name;
        private String url;
        private int priority = 1;
        private boolean enabled = true;
        private String buildVersion;
        private Long lastBootMs;

        private Builder() {
        }

        private Builder(final Node node) {
            super(node);
            this.id = node.id;
            this.version = node.version;
            this.name = node.name;
            this.url = node.url;
            this.priority = node.priority;
            this.enabled = node.enabled;
            this.buildVersion = node.buildVersion;
            this.lastBootMs = node.lastBootMs;
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

        public Builder url(final String url) {
            this.url = url;
            return self();
        }

        public Builder priority(final int priority) {
            this.priority = priority;
            return self();
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        public Builder buildVersion(final String buildVersion) {
            this.buildVersion = buildVersion;
            return self();
        }

        public Builder lastBootMs(final Long lastBootMs) {
            this.lastBootMs = lastBootMs;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Node build() {
            return new Node(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    name,
                    url,
                    priority,
                    enabled,
                    buildVersion,
                    lastBootMs);
        }
    }
}
