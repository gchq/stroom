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

package stroom.job.shared;


import stroom.util.shared.AuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasIntegerId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class Job implements HasAuditInfoGetters, HasIntegerId {

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
    @JsonProperty
    private final String description;
    @JsonProperty
    private final boolean advanced;

    @JsonCreator
    public Job(@JsonProperty("id") final Integer id,
               @JsonProperty("version") final Integer version,
               @JsonProperty("createTimeMs") final Long createTimeMs,
               @JsonProperty("createUser") final String createUser,
               @JsonProperty("updateTimeMs") final Long updateTimeMs,
               @JsonProperty("updateUser") final String updateUser,
               @JsonProperty("name") final String name,
               @JsonProperty("enabled") final boolean enabled,
               @JsonProperty("description") final String description,
               @JsonProperty("advanced") final boolean advanced) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.name = name;
        this.enabled = enabled;
        this.description = description;
        this.advanced = advanced;
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

    public String getDescription() {
        return description;
    }

    /**
     * This is used to distinguish jobs in the UI. The idea is that users are more likely to
     * need to change the schedules of non-advanced jobs so these jobs go at the top of the
     * list. 'Advanced' jobs are expected to be ones that are not typically changed by an admin.
     */
    public boolean isAdvanced() {
        return advanced;
    }

    @Override
    public String toString() {
        return "Job{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", name='" + name + '\'' +
               ", enabled=" + enabled +
               ", description='" + description + '\'' +
               ", advanced=" + advanced +
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
        final Job job = (Job) o;
        return id.equals(job.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder
            extends AuditInfoBuilder<Job, Builder> {

        private Integer id;
        private Integer version;
        private String name;
        private boolean enabled;
        private String description;
        private boolean advanced;

        private Builder() {
        }

        private Builder(final Job job) {
            this.id = job.id;
            this.version = job.version;
            this.createTimeMs = job.createTimeMs;
            this.createUser = job.createUser;
            this.updateTimeMs = job.updateTimeMs;
            this.updateUser = job.updateUser;
            this.name = job.name;
            this.enabled = job.enabled;
            this.description = job.description;
            this.advanced = job.advanced;
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

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder advanced(final boolean advanced) {
            this.advanced = advanced;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public Job build() {
            return new Job(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    name,
                    enabled,
                    description,
                    advanced);
        }
    }
}
