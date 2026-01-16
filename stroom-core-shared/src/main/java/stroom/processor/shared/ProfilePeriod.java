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

package stroom.processor.shared;

import stroom.util.shared.time.Days;
import stroom.util.shared.time.Time;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "days",
        "startTime",
        "endTime",
        "limitNodeThreads",
        "maxNodeThreads",
        "limitClusterThreads",
        "maxClusterThreads"})
public class ProfilePeriod {

    @JsonProperty
    private final Days days;
    @JsonProperty
    private final Time startTime;
    @JsonProperty
    private final Time endTime;
    @JsonProperty
    private final boolean limitNodeThreads;
    @JsonProperty
    private final int maxNodeThreads;
    @JsonProperty
    private final boolean limitClusterThreads;
    @JsonProperty
    private final int maxClusterThreads;

    @JsonCreator
    public ProfilePeriod(
            @JsonProperty("days") final Days days,
            @JsonProperty("startTime") final Time startTime,
            @JsonProperty("endTime") final Time endTime,
            @JsonProperty("limitNodeThreads") final boolean limitNodeThreads,
            @JsonProperty("maxNodeThreads") final int maxNodeThreads,
            @JsonProperty("limitClusterThreads") final boolean limitClusterThreads,
            @JsonProperty("maxClusterThreads") final int maxClusterThreads) {
        this.days = days;
        this.startTime = startTime;
        this.endTime = endTime;
        this.limitNodeThreads = limitNodeThreads;
        this.maxNodeThreads = maxNodeThreads;
        this.limitClusterThreads = limitClusterThreads;
        this.maxClusterThreads = maxClusterThreads;
    }

    public Days getDays() {
        return days;
    }

    public Time getStartTime() {
        return startTime;
    }

    public Time getEndTime() {
        return endTime;
    }

    public boolean isLimitNodeThreads() {
        return limitNodeThreads;
    }

    public int getMaxNodeThreads() {
        return maxNodeThreads;
    }

    public boolean isLimitClusterThreads() {
        return limitClusterThreads;
    }

    public int getMaxClusterThreads() {
        return maxClusterThreads;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProfilePeriod that = (ProfilePeriod) o;
        return limitNodeThreads == that.limitNodeThreads &&
               maxNodeThreads == that.maxNodeThreads &&
               limitClusterThreads == that.limitClusterThreads &&
               maxClusterThreads == that.maxClusterThreads &&
               Objects.equals(days, that.days) &&
               Objects.equals(startTime, that.startTime) &&
               Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(days,
                startTime,
                endTime,
                limitNodeThreads,
                maxNodeThreads,
                limitClusterThreads,
                maxClusterThreads);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Days days;
        private Time startTime;
        private Time endTime;
        private boolean limitNodeThreads;
        private int maxNodeThreads;
        private boolean limitClusterThreads;
        private int maxClusterThreads;

        private Builder() {
        }

        private Builder(final ProfilePeriod profilePeriod) {
            this.days = profilePeriod.days;
            this.startTime = profilePeriod.startTime;
            this.endTime = profilePeriod.endTime;
            this.limitNodeThreads = profilePeriod.limitNodeThreads;
            this.maxNodeThreads = profilePeriod.maxNodeThreads;
            this.limitClusterThreads = profilePeriod.limitClusterThreads;
            this.maxClusterThreads = profilePeriod.maxClusterThreads;
        }

        public Builder days(final Days days) {
            this.days = days;
            return this;
        }

        public Builder startTime(final Time startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(final Time endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder limitNodeThreads(final boolean limitNodeThreads) {
            this.limitNodeThreads = limitNodeThreads;
            return this;
        }

        public Builder maxNodeThreads(final int maxNodeThreads) {
            this.maxNodeThreads = maxNodeThreads;
            return this;
        }

        public Builder limitClusterThreads(final boolean limitClusterThreads) {
            this.limitClusterThreads = limitClusterThreads;
            return this;
        }

        public Builder maxClusterThreads(final int maxClusterThreads) {
            this.maxClusterThreads = maxClusterThreads;
            return this;
        }

        public ProfilePeriod build() {
            return new ProfilePeriod(
                    days,
                    startTime,
                    endTime,
                    limitNodeThreads,
                    maxNodeThreads,
                    limitClusterThreads,
                    maxClusterThreads);
        }
    }
}
