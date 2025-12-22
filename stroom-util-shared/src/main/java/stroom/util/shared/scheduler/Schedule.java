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

package stroom.util.shared.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "type",
        "expression"
})
@JsonInclude(Include.NON_NULL)
public class Schedule {

    @JsonProperty
    private final ScheduleType type;
    @JsonProperty
    private final String expression;

    @JsonCreator
    public Schedule(@JsonProperty("type") final ScheduleType type,
                    @JsonProperty("expression") final String expression) {
        this.type = type;
        this.expression = expression;
    }

    public ScheduleType getType() {
        return type;
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Schedule schedule = (Schedule) o;
        return type == schedule.type &&
               Objects.equals(expression, schedule.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, expression);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (type != null) {
            sb.append(type.getDisplayValue());
        }
        if ((ScheduleType.FREQUENCY.equals(type) || ScheduleType.CRON.equals(type))
            && expression != null) {
            sb.append(" ");
            sb.append(expression);
        }
        return sb.toString();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private ScheduleType type;
        private String expression;

        private Builder() {
        }

        private Builder(final Schedule schedule) {
            this.type = schedule.type;
            this.expression = schedule.expression;
        }


        public Builder type(final ScheduleType type) {
            this.type = type;
            return this;
        }

        public Builder expression(final String expression) {
            this.expression = expression;
            return this;
        }

        public Schedule build() {
            return new Schedule(type, expression);
        }
    }
}
