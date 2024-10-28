/*
 * Copyright 2024 Crown Copyright
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

import stroom.docref.HasDisplayValue;
import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;
import java.util.Optional;

@JsonPropertyOrder({"order", "direction"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "Describes the sorting applied to a field")
public final class Sort {

    @Schema(description = "Where multiple fields are sorted this value describes the sort order, with 0 being the " +
            "first field to sort on",
            example = "0",
            required = true)
    @JsonProperty
    private final Integer order;

    @Schema(description = "The direction to sort in, ASCENDING or DESCENDING",
            example = "ASCENDING",
            required = true)
    @JsonProperty
    private final SortDirection direction;

    @JsonCreator
    public Sort(@JsonProperty("order") final Integer order,
                @JsonProperty("direction") final SortDirection direction) {
        this.order = order;
        this.direction = direction;
    }

    public Integer getOrder() {
        return order;
    }

    public SortDirection getDirection() {
        return direction;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Sort sort = (Sort) o;
        return Objects.equals(order, sort.order) &&
                direction == sort.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, direction);
    }

    @Override
    public String toString() {
        return "Sort{" +
                "order=" + order +
                ", direction=" + direction +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public enum SortDirection implements HasDisplayValue {
        ASCENDING("Ascending", "asc"),
        DESCENDING("Descending", "desc"),
        ;

        private final String longForm;
        private final String shortForm;

        SortDirection(final String longForm, String shortForm) {
            this.longForm = longForm;
            this.shortForm = shortForm;
        }

        /**
         * 'Ascending' or 'Descending'
         * Equivalent to calling {@link SortDirection#getLongForm()}
         */
        @Override
        public String getDisplayValue() {
            return longForm;
        }

        /**
         * 'Ascending' or 'Descending'
         */
        public String getLongForm() {
            return longForm;
        }

        /**
         * 'asc' or 'desc'
         */
        public String getShortForm() {
            return shortForm;
        }

        /**
         * Parse a {@link SortDirection} from a short form string ('asc' or 'desc') ignoring case.
         */
        public static Optional<SortDirection> fromString(final String str) {
            if (!GwtNullSafe.isBlankString(str)) {
                for (final SortDirection sortDirection : SortDirection.values()) {
                    if (sortDirection.shortForm.equalsIgnoreCase(str)
                            || sortDirection.longForm.equalsIgnoreCase(str)) {
                        return Optional.of(sortDirection);
                    }
                }
            }
            return Optional.empty();
        }

        /**
         * Parse a {@link SortDirection} from a short form string ('asc' or 'desc') ignoring case.
         */
        public static Optional<SortDirection> fromShortForm(final String str) {
            if (!GwtNullSafe.isBlankString(str)) {
                for (final SortDirection sortDirection : SortDirection.values()) {
                    if (sortDirection.shortForm.equalsIgnoreCase(str)) {
                        return Optional.of(sortDirection);
                    }
                }
            }
            return Optional.empty();
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Builder for constructing a {@link Sort sort}
     */
    public static final class Builder {

        private Integer order;
        private SortDirection direction;

        private Builder() {
        }

        private Builder(final Sort sort) {
            this.order = sort.order;
            this.direction = sort.direction;
        }

        /**
         * @param value Where multiple fields are sorted this value describes the sort order,
         *              with 0 being the first field to sort on
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder order(final Integer value) {
            this.order = value;
            return this;
        }

        /**
         * @param value The direction to sort in, ASCENDING or DESCENDING
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder direction(final SortDirection value) {
            this.direction = value;
            return this;
        }

        public Sort build() {
            return new Sort(order, direction);
        }
    }
}
