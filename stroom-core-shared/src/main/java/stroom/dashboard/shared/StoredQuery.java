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
import stroom.util.shared.AuditInfoBuilder;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoredQuery implements HasIntegerId {

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
    private final String uuid;
    @JsonProperty
    private final String dashboardUuid;
    @JsonProperty
    private final String componentId;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final UserRef owner;
    @JsonProperty
    private final boolean favourite;
    @JsonProperty
    private final Query query;

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

    public Integer getVersion() {
        return version;
    }

    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public String getCreateUser() {
        return createUser;
    }

    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public String getUuid() {
        return uuid;
    }

    public String getDashboardUuid() {
        return dashboardUuid;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getName() {
        return name;
    }

    public UserRef getOwner() {
        return owner;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public Query getQuery() {
        return query;
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AuditInfoBuilder<StoredQuery, Builder> {

        private Integer id;
        private Integer version;
        private String uuid;
        private String dashboardUuid;
        private String componentId;
        private String name;
        private UserRef owner;
        private boolean favourite;
        private Query query;

        private Builder() {
        }

        private Builder(final StoredQuery storedQuery) {
            this.id = storedQuery.id;
            this.version = storedQuery.version;
            this.createTimeMs = storedQuery.createTimeMs;
            this.createUser = storedQuery.createUser;
            this.updateTimeMs = storedQuery.updateTimeMs;
            this.updateUser = storedQuery.updateUser;
            this.uuid = storedQuery.uuid;
            this.dashboardUuid = storedQuery.dashboardUuid;
            this.componentId = storedQuery.componentId;
            this.name = storedQuery.name;
            this.owner = storedQuery.owner;
            this.favourite = storedQuery.favourite;
            this.query = storedQuery.query;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return self();
        }

        public Builder dashboardUuid(final String dashboardUuid) {
            this.dashboardUuid = dashboardUuid;
            return self();
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder owner(final UserRef owner) {
            this.owner = owner;
            return self();
        }

        public Builder favourite(final boolean favourite) {
            this.favourite = favourite;
            return self();
        }

        public Builder query(final Query query) {
            this.query = query;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public StoredQuery build() {
            return new StoredQuery(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    uuid,
                    dashboardUuid,
                    componentId,
                    name,
                    owner,
                    favourite,
                    query);
        }
    }
}
