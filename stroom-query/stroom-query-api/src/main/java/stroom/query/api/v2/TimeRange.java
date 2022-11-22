/*
 * Copyright 2022 Crown Copyright
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

package stroom.query.api.v2;

import stroom.query.api.v2.ExpressionTerm.Condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"name", "condition", "from", "to"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRange {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final Condition condition;
    @JsonProperty
    private final String from;
    @JsonProperty
    private final String to;

    public TimeRange(final String name,
                     final String from,
                     final String to) {
        this.name = name;
        this.condition = Condition.BETWEEN;
        this.from = from;
        this.to = to;
    }

    @JsonCreator
    public TimeRange(@JsonProperty("name") final String name,
                     @JsonProperty("condition") final Condition condition,
                     @JsonProperty("from") final String from,
                     @JsonProperty("to") final String to) {
        this.name = name;
        this.condition = condition;
        this.from = from;
        this.to = to;
    }

    public String getName() {
        return name;
    }

    public Condition getCondition() {
        return condition;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimeRange timeRange = (TimeRange) o;
        return condition == timeRange.condition && Objects.equals(from,
                timeRange.from) && Objects.equals(to, timeRange.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, from, to);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(condition.getDisplayValue());
        sb.append(" ");
        if (from != null) {
            sb.append(from);
            if (to != null) {
                sb.append(",");
                sb.append(to);
            }
        } else if (to != null) {
            sb.append(to);
        }
        return sb.toString();
    }
}
