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

package stroom.analytics.shared;

import stroom.docref.DocRef;
import stroom.util.shared.UserRef;
import stroom.util.shared.scheduler.Schedule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "uuid",
        "name",
        "enabled",
        "nodeName",
        "schedule",
        "contiguous",
        "scheduleBounds",
        "owningDoc",
        "runAsUser"
})
public class ExecutionSchedule {

    public static final String ENTITY_TYPE = "ExecutionSchedule";

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final Schedule schedule;
    @JsonProperty
    private final boolean contiguous;
    @JsonProperty
    private final ScheduleBounds scheduleBounds;
    @JsonProperty
    private final DocRef owningDoc;
    @JsonProperty
    private final UserRef runAsUser;

    @JsonCreator
    public ExecutionSchedule(@JsonProperty("uuid") final String uuid,
                             @JsonProperty("name") final String name,
                             @JsonProperty("enabled") final boolean enabled,
                             @JsonProperty("nodeName") final String nodeName,
                             @JsonProperty("schedule") final Schedule schedule,
                             @JsonProperty("contiguous") final boolean contiguous,
                             @JsonProperty("scheduleBounds") final ScheduleBounds scheduleBounds,
                             @JsonProperty("owningDoc") final DocRef owningDoc,
                             @JsonProperty("runAsUser") final UserRef runAsUser) {
        this.uuid = uuid;
        this.name = name;
        this.enabled = enabled;
        this.nodeName = nodeName;
        this.schedule = schedule;
        this.contiguous = contiguous;
        this.scheduleBounds = scheduleBounds;
        this.owningDoc = owningDoc;
        this.runAsUser = runAsUser;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public boolean isContiguous() {
        return contiguous;
    }

    public ScheduleBounds getScheduleBounds() {
        return scheduleBounds;
    }

    public DocRef getOwningDoc() {
        return owningDoc;
    }

    public UserRef getRunAsUser() {
        return runAsUser;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExecutionSchedule that = (ExecutionSchedule) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "ExecutionSchedule{" +
               "uuid=" + uuid +
               ", name=" + name +
               ", enabled=" + enabled +
               ", nodeName='" + nodeName + '\'' +
               ", schedule=" + schedule +
               ", contiguous=" + contiguous +
               ", scheduleBounds=" + scheduleBounds +
               ", owningDoc=" + owningDoc +
               ", runAsUser=" + runAsUser +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private String uuid;
        private String name;
        private boolean enabled;
        private String nodeName;
        private Schedule schedule;
        private boolean contiguous = true;
        private ScheduleBounds scheduleBounds;
        private DocRef owningDoc;
        private UserRef runAsUser;

        private Builder() {
        }

        private Builder(final ExecutionSchedule executionSchedule) {
            this.uuid = executionSchedule.uuid;
            this.name = executionSchedule.name;
            this.enabled = executionSchedule.enabled;
            this.nodeName = executionSchedule.nodeName;
            this.schedule = executionSchedule.schedule;
            this.contiguous = executionSchedule.contiguous;
            this.scheduleBounds = executionSchedule.scheduleBounds;
            this.owningDoc = executionSchedule.owningDoc;
            this.runAsUser = executionSchedule.runAsUser;
        }


        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder nodeName(final String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public Builder schedule(final Schedule schedule) {
            this.schedule = schedule;
            return this;
        }

        public Builder contiguous(final boolean contiguous) {
            this.contiguous = contiguous;
            return this;
        }

        public Builder scheduleBounds(final ScheduleBounds scheduleBounds) {
            this.scheduleBounds = scheduleBounds;
            return this;
        }

        public Builder owningDoc(final DocRef owningDoc) {
            this.owningDoc = owningDoc;
            return this;
        }

        public Builder runAsUser(final UserRef runAsUser) {
            this.runAsUser = runAsUser;
            return this;
        }

        public ExecutionSchedule build() {
            return new ExecutionSchedule(
                    uuid,
                    name,
                    enabled,
                    nodeName,
                    schedule,
                    contiguous,
                    scheduleBounds,
                    owningDoc,
                    runAsUser);
        }
    }
}
