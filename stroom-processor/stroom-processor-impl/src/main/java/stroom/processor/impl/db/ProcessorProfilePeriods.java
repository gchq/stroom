/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.processor.impl.db;

import stroom.processor.shared.ProfilePeriod;
import stroom.query.api.UserTimeZone;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "profilePeriods",
        "timeZone"})
public class ProcessorProfilePeriods {

    @JsonProperty
    private final List<ProfilePeriod> profilePeriods;
    @JsonProperty
    private final UserTimeZone timeZone;

    @JsonCreator
    public ProcessorProfilePeriods(@JsonProperty("profilePeriods") final List<ProfilePeriod> profilePeriods,
                                   @JsonProperty("timeZone") final UserTimeZone timeZone) {
        this.profilePeriods = profilePeriods;
        this.timeZone = timeZone;
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
        final ProcessorProfilePeriods that = (ProcessorProfilePeriods) o;
        return Objects.equals(profilePeriods, that.profilePeriods) &&
               Objects.equals(timeZone, that.timeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profilePeriods,
                timeZone);
    }

    @Override
    public String toString() {
        return "ProcessorProfileData{" +
               "profilePeriods=" + profilePeriods +
               ", timeZone=" + timeZone +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<ProcessorProfilePeriods, ProcessorProfilePeriods.Builder> {

        private List<ProfilePeriod> profilePeriods;
        private UserTimeZone timeZone;

        private Builder() {
        }

        private Builder(final ProcessorProfilePeriods processorProfile) {
            this.profilePeriods = processorProfile.profilePeriods;
            this.timeZone = processorProfile.timeZone;
        }

        public Builder profilePeriods(final List<ProfilePeriod> profilePeriods) {
            this.profilePeriods = profilePeriods;
            return this;
        }

        public Builder timeZone(final UserTimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ProcessorProfilePeriods build() {
            return new ProcessorProfilePeriods(
                    profilePeriods,
                    timeZone);
        }
    }
}
