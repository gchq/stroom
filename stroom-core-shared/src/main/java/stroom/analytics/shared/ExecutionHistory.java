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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "executionSchedule",
        "executionTimeMs",
        "effectiveExecutionTimeMs",
        "status",
        "message"
})
public class ExecutionHistory {

    @JsonProperty
    private final long id;
    @JsonProperty
    private final ExecutionSchedule executionSchedule;
    @JsonProperty
    private final long executionTimeMs;
    @JsonProperty
    private final long effectiveExecutionTimeMs;
    @JsonProperty
    private final String status;
    @JsonProperty
    private final String message;

    @JsonCreator
    public ExecutionHistory(@JsonProperty("id") final long id,
                            @JsonProperty("executionSchedule") final ExecutionSchedule executionSchedule,
                            @JsonProperty("executionTimeMs") final long executionTimeMs,
                            @JsonProperty("effectiveExecutionTimeMs") final long effectiveExecutionTimeMs,
                            @JsonProperty("status") final String status,
                            @JsonProperty("message") final String message) {
        this.id = id;
        this.executionSchedule = executionSchedule;
        this.executionTimeMs = executionTimeMs;
        this.effectiveExecutionTimeMs = effectiveExecutionTimeMs;
        this.status = status;
        this.message = message;
    }

    public long getId() {
        return id;
    }

    public ExecutionSchedule getExecutionSchedule() {
        return executionSchedule;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public long getEffectiveExecutionTimeMs() {
        return effectiveExecutionTimeMs;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExecutionHistory that = (ExecutionHistory) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "HistoricExecution{" +
                "id=" + id +
                ", executionSchedule=" + executionSchedule +
                ", executionTimeMs=" + executionTimeMs +
                ", effectiveExecutionTimeMs=" + effectiveExecutionTimeMs +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                '}';
    }


    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private long id;
        private ExecutionSchedule executionSchedule;
        private long executionTimeMs;
        private long effectiveExecutionTimeMs;
        private String status;
        private String message;

        private Builder() {
        }

        private Builder(final ExecutionHistory executionHistory) {
            this.id = executionHistory.id;
            this.executionSchedule = executionHistory.executionSchedule;
            this.executionTimeMs = executionHistory.executionTimeMs;
            this.effectiveExecutionTimeMs = executionHistory.effectiveExecutionTimeMs;
            this.status = executionHistory.status;
            this.message = executionHistory.message;
        }


        public Builder id(final long id) {
            this.id = id;
            return this;
        }

        public Builder executionSchedule(final ExecutionSchedule executionSchedule) {
            this.executionSchedule = executionSchedule;
            return this;
        }

        public Builder executionTimeMs(final long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder effectiveExecutionTimeMs(final long effectiveExecutionTimeMs) {
            this.effectiveExecutionTimeMs = effectiveExecutionTimeMs;
            return this;
        }

        public Builder status(final String status) {
            this.status = status;
            return this;
        }

        public Builder message(final String message) {
            this.message = message;
            return this;
        }

        public ExecutionHistory build() {
            return new ExecutionHistory(
                    id,
                    executionSchedule,
                    executionTimeMs,
                    effectiveExecutionTimeMs,
                    status,
                    message);
        }
    }
}
