/*
 * Copyright 2026 Crown Copyright
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

package stroom.externaldatasource.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// A column of data stored in an [ExternalDataSourceDoc], such as CSV, which becomes a query field of
/// the same name.
@JsonPropertyOrder({"name", "type"})
@JsonInclude(Include.NON_NULL)
public class DataColumn {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final DataColumnType type;

    /// Creates a column.
    ///
    /// @param name The column's name, which becomes the field name.
    /// @param type How its values are read, or `null` for [DataColumnType#TEXT].
    @JsonCreator
    public DataColumn(@JsonProperty("name") final String name,
                      @JsonProperty("type") final DataColumnType type) {
        this.name = name;
        this.type = type;
    }

    /// @return The column's name, which becomes the field name.
    public String getName() {
        return name;
    }

    /// @return How the column's values are read, or `null` meaning [DataColumnType#TEXT].
    public DataColumnType getType() {
        return type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final DataColumn that)) {
            return false;
        }
        return Objects.equals(name, that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return name + ":" + type;
    }
}
