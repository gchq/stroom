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

package stroom.query.api;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class ColumnValueSelection {

    @JsonProperty
    @JsonPropertyDescription("Selected values to include unless inverted in which case these are the values to exclude")
    private final Set<String> values;
    @JsonProperty
    @JsonPropertyDescription("Is the selection inverted, " +
                             "i.e. are all values to be included except for the selected ones")
    private final boolean invert;

    @JsonCreator
    public ColumnValueSelection(@JsonProperty("values") final Set<String> values,
                                @JsonProperty("invert") final boolean invert) {
        this.values = values;
        this.invert = invert;
    }

    public Set<String> getValues() {
        return values;
    }

    public boolean isInvert() {
        return invert;
    }

    /**
     * This filter is enabled if any values are selected or if the filter is inverted so only the selected values are
     * included.
     *
     * @return True if this filter is enabled.
     */
    @JsonIgnore
    public boolean isEnabled() {
        return !invert || (values != null && !values.isEmpty());
    }

    @Override
    public String toString() {
        return "ColumnValueSelection{" +
               "values=" + values +
               ", invert=" + invert +
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
        final ColumnValueSelection that = (ColumnValueSelection) o;
        return invert == that.invert && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, invert);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<ColumnValueSelection, Builder> {

        private Set<String> values;
        private boolean invert = true;

        private Builder() {
            values = new HashSet<>();
        }

        private Builder(final ColumnValueSelection columnValueSelection) {
            values(columnValueSelection.values).invert(columnValueSelection.invert);
        }

        public Builder values(final Set<String> values) {
            if (values != null) {
                this.values = new HashSet<>(values);
            } else {
                this.values = new HashSet<>();
            }
            return self();
        }

        public Builder add(final String value) {
            this.values.add(value);
            return self();
        }

        public Builder remove(final String value) {
            this.values.remove(value);
            return self();
        }

        public Builder invert(final boolean invert) {
            this.invert = invert;
            return self();
        }

        public Builder clear() {
            values.clear();
            return self();
        }

        public Builder toggle(final String value) {
            if (value != null) {
                if (values.contains(value)) {
                    values.remove(value);
                } else {
                    values.add(value);
                }
            }
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ColumnValueSelection build() {
            return new ColumnValueSelection(
                    values == null || values.isEmpty()
                            ? Collections.emptySet()
                            : new HashSet<>(values),
                    invert
            );
        }
    }
}
