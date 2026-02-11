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

package stroom.index.shared;

import stroom.util.shared.AbstractHasAuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasIntegerId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class IndexVolumeGroup implements HasAuditInfoGetters, HasIntegerId {

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

    @JsonCreator
    public IndexVolumeGroup(@JsonProperty("id") final Integer id,
                            @JsonProperty("version") final Integer version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("name") final String name) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
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

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IndexVolumeGroup that = (IndexVolumeGroup) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(version, that.version) &&
               Objects.equals(createTimeMs, that.createTimeMs) &&
               Objects.equals(createUser, that.createUser) &&
               Objects.equals(updateTimeMs, that.updateTimeMs) &&
               Objects.equals(updateUser, that.updateUser) &&
               Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, createTimeMs, createUser, updateTimeMs, updateUser, name);
    }

    @Override
    public String toString() {
        return "IndexVolumeGroup{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", name='" + name + '\'' +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractHasAuditInfoBuilder<IndexVolumeGroup, IndexVolumeGroup.Builder> {

        private Integer id;
        private Integer version;
        private String name;

        private Builder() {
        }

        private Builder(final IndexVolumeGroup indexVolumeGroup) {
            super(indexVolumeGroup);
            this.id = indexVolumeGroup.id;
            this.version = indexVolumeGroup.version;
            this.name = indexVolumeGroup.name;
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

        @Override
        protected Builder self() {
            return this;
        }

        public IndexVolumeGroup build() {
            return new IndexVolumeGroup(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    name);
        }
    }
}
