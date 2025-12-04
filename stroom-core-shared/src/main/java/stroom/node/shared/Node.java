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


import stroom.util.shared.HasAuditInfo;
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
public class Node implements HasAuditInfo, HasIntegerId {

    public static final String ENTITY_TYPE = "Node";

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
    private String name;
    @JsonProperty
    private String url;

    /**
     * A number to imply how important that this node is from the POV of being a
     * master node. The bigger the number the better chance it will become a
     * master
     */
    @JsonProperty
    private int priority;
    @JsonProperty
    private boolean enabled;
    @JsonProperty
    private String buildVersion;
    @JsonProperty
    private Long lastBootMs;

    public Node() {
        priority = 1;
        enabled = true;
    }

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

    /**
     * Utility to create a node.
     */
    public static Node create(final String name) {
        final Node node = new Node();
        node.setName(name);
        return node;
    }

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

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(final int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(final String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public Long getLastBootMs() {
        return lastBootMs;
    }

    public void setLastBootMs(final Long lastBootMs) {
        this.lastBootMs = lastBootMs;
    }

    //    @Override
//    protected void toString(final StringBuilder sb) {
//        super.toString(sb);
//        sb.append(", name=");
//        sb.append(getName());
//        sb.append(", clusterCallUrl=");
//        sb.append(url);
//        sb.append(", priority=");
//        sb.append(priority);
//    }
//
//    @Transient
//    @Override
//    public final String getType() {
//        return ENTITY_TYPE;
//    }
//
//    public Node copy() {
//        final Node node = new Node();
//        node.setName(getName());
//        node.priority = priority;
//        node.enabled = enabled;
//        return node;
//    }


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
}
