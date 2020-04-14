/*
 * Copyright 2017 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.ToStringBuilder;

import java.util.Objects;

@JsonPropertyOrder({"order", "direction"})
@JsonInclude(Include.NON_NULL)
public class Sort {
    @JsonProperty
    private final int order;
    @JsonProperty
    private final SortDirection direction;

    @Deprecated // Only here for legacy XML (de)serialisation
    Sort() {
        this.order = 1;
        this.direction = SortDirection.ASCENDING;
    }

    @JsonCreator
    public Sort(@JsonProperty("order") final Integer order,
                @JsonProperty("direction") final SortDirection direction) {
        if (order != null) {
            this.order = order;
        } else {
            this.order = 1;
        }
        if (direction != null) {
            this.direction = direction;
        } else {
            this.direction = SortDirection.ASCENDING;
        }
    }

    public int getOrder() {
        return order;
    }

    public SortDirection getDirection() {
        return direction;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Sort sort = (Sort) o;
        return order == sort.order &&
                direction == sort.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, direction);
    }

    @Override
    public String toString() {
        final ToStringBuilder builder = new ToStringBuilder();
        builder.append("order", order);
        builder.append("direction", direction);
        return builder.toString();
    }

    public Sort copy() {
        return new Sort(order, direction);
    }

    public enum SortDirection implements HasDisplayValue {
        ASCENDING("Ascending"), DESCENDING("Descending");

        private final String displayValue;

        SortDirection(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
