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

package stroom.dashboard.shared;

import stroom.query.api.Query;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoredQuery implements HasAuditInfo, HasIntegerId {

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
    private String uuid;
    @JsonProperty
    private String dashboardUuid;
    @JsonProperty
    private String componentId;
    @JsonProperty
    private String name;
    @JsonProperty
    private UserRef owner;
    @JsonProperty
    private boolean favourite;
    @JsonProperty
    private Query query;

    public StoredQuery() {
    }

    @JsonCreator
    public StoredQuery(@JsonProperty("id") final Integer id,
                       @JsonProperty("version") final Integer version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("updateUser") final String updateUser,
                       @JsonProperty("uuid") final String uuid,
                       @JsonProperty("dashboardUuid") final String dashboardUuid,
                       @JsonProperty("componentId") final String componentId,
                       @JsonProperty("name") final String name,
                       @JsonProperty("owner") final UserRef owner,
                       @JsonProperty("favourite") final boolean favourite,
                       @JsonProperty("query") final Query query) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.uuid = uuid;
        this.dashboardUuid = dashboardUuid;
        this.componentId = componentId;
        this.name = name;
        this.owner = owner;
        this.favourite = favourite;
        this.query = query;
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

    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public String getDashboardUuid() {
        return dashboardUuid;
    }

    public void setDashboardUuid(final String dashboardUuid) {
        this.dashboardUuid = dashboardUuid;
    }

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(final String componentId) {
        this.componentId = componentId;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public UserRef getOwner() {
        return owner;
    }

    public void setOwner(final UserRef owner) {
        this.owner = owner;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public void setFavourite(final boolean favourite) {
        this.favourite = favourite;
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(final Query query) {
        this.query = query;
    }

    @Override
    public String toString() {
        return "StoredQuery{" +
                "id=" + id +
                ", version=" + version +
                ", createTimeMs=" + createTimeMs +
                ", createUser='" + createUser + '\'' +
                ", updateTimeMs=" + updateTimeMs +
                ", updateUser='" + updateUser + '\'' +
                ", uuid='" + uuid + '\'' +
                ", dashboardUuid='" + dashboardUuid + '\'' +
                ", componentId='" + componentId + '\'' +
                ", name='" + name + '\'' +
                ", owner='" + owner + '\'' +
                ", favourite=" + favourite +
                ", query=" + query +
                '}';
    }
}
