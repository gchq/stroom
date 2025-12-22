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

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"id", "name"})
@JsonInclude(Include.NON_NULL)
public final class ColumnRef implements HasDisplayValue {

    @JsonPropertyDescription("The internal id of the field for equality purposes")
    @JsonProperty
    private final String id;

    @JsonPropertyDescription("The name of the field for display purposes")
    @JsonProperty
    private final String name;

    @JsonCreator
    public ColumnRef(@JsonProperty("id") final String id,
                     @JsonProperty("name") final String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return name;
    }

    public static boolean equalsId(final ColumnRef lhs, final ColumnRef rhs) {
        if (lhs == null && rhs == null) {
            return true;
        }
        if (lhs != null && rhs != null) {
            return Objects.equals(lhs.id, rhs.id);
        }
        return false;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ColumnRef field = (ColumnRef) o;
        return Objects.equals(id, field.id) &&
                Objects.equals(name, field.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Column{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link ColumnRef}
     */
    public static final class Builder {

        private String id;
        private String name;

        /**
         * No args constructor, allow all building using chained methods
         */
        private Builder() {
        }

        private Builder(final ColumnRef field) {
            this.id = field.id;
            this.name = field.name;
        }

        /**
         * @param value The internal id of the field for equality purposes
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder id(final String value) {
            this.id = value;
            return this;
        }

        /**
         * @param value The name of the field for display purposes
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder name(final String value) {
            this.name = value;
            return this;
        }

        public ColumnRef build() {
            return new ColumnRef(id, name);
        }
    }
}
