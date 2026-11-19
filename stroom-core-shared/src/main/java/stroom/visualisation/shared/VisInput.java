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

package stroom.visualisation.shared;

import stroom.query.api.Format;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A data field a visualisation takes, declared in the optional `inputs` section of its settings,
/// e.g.
/// ```
/// {"id": "x", "label": "X axis", "description": "Time of each event",
///  "types": ["DATE_TIME"], "required": true, "multiple": false}
/// ```
/// It describes the settings' `field` control with the same id. Stroom offers only the columns
/// that can feed it, marks it if it is required, and converts its values to its type. A
/// visualisation without inputs takes any column in any of its fields, as before inputs existed.
@JsonPropertyOrder({"id", "label", "description", "types", "required", "multiple"})
@JsonInclude(Include.NON_NULL)
public final class VisInput {

    @JsonProperty
    private final String id;
    @JsonProperty
    private final String label;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final List<String> types;
    @JsonProperty
    private final Boolean required;
    @JsonProperty
    private final Boolean multiple;

    @JsonCreator
    public VisInput(@JsonProperty("id") final String id,
                    @JsonProperty("label") final String label,
                    @JsonProperty("description") final String description,
                    @JsonProperty("types") final List<String> types,
                    @JsonProperty("required") final Boolean required,
                    @JsonProperty("multiple") final Boolean multiple) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.types = types;
        this.required = required;
        this.multiple = multiple;
    }

    /// @return The id of the `field` control it describes.
    public String getId() {
        return id;
    }

    /// @return Its name, e.g. `X axis`.
    public String getLabel() {
        return label;
    }

    /// @return What it is for, e.g. `Time of each event`, or null.
    public String getDescription() {
        return description;
    }

    /// @return The names of the types it takes, as the settings give them, or null.
    public List<String> getTypes() {
        return types;
    }

    /// @return True if the visualisation can't draw without it.
    @JsonIgnore
    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }

    /// @return True if it takes more than one field.
    @JsonIgnore
    public boolean isMultiple() {
        return Boolean.TRUE.equals(multiple);
    }

    /// @return The types it takes, leaving out names that aren't types. Empty if it names none,
    /// in which case it takes any.
    @JsonIgnore
    public List<VisInputType> getInputTypes() {
        if (types == null) {
            return Collections.emptyList();
        }
        final List<VisInputType> list = new ArrayList<>();
        for (final String name : types) {
            final VisInputType type = VisInputType.parse(name);
            if (type != null && !list.contains(type)) {
                list.add(type);
            }
        }
        return list;
    }

    /// Whether a column of a type can feed it.
    ///
    /// @param columnType The type of the column's format, null for a column without one.
    /// @return True if one of its types accepts the column, or it names no types.
    public boolean accepts(final Format.Type columnType) {
        final List<VisInputType> inputTypes = getInputTypes();
        if (inputTypes.isEmpty()) {
            return true;
        }
        for (final VisInputType type : inputTypes) {
            if (type.accepts(columnType)) {
                return true;
            }
        }
        return false;
    }

    /// The format a column feeding it should be converted to, so that the visualisation gets the
    /// type it takes rather than whatever the values look like.
    ///
    /// @param columnType The type of the column's format, null for a column without one.
    /// @return The format type to convert to, or null to leave the column as it is: when it is
    /// already of a type the input takes, or the input takes more than one type and the column's
    /// type is general (so it is judged by its values).
    public Format.Type conversionFor(final Format.Type columnType) {
        final List<VisInputType> inputTypes = getInputTypes();
        if (inputTypes.isEmpty()) {
            return null;
        }
        for (final VisInputType type : inputTypes) {
            if (type.getFormatType() == columnType) {
                return null;
            }
        }
        if (inputTypes.size() == 1) {
            return inputTypes.get(0).getFormatType();
        }
        // Several types: a text input takes anything as text, otherwise it is judged by its values
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisInput that = (VisInput) o;
        return Objects.equals(id, that.id)
               && Objects.equals(label, that.label)
               && Objects.equals(description, that.description)
               && Objects.equals(types, that.types)
               && Objects.equals(required, that.required)
               && Objects.equals(multiple, that.multiple);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, description, types, required, multiple);
    }

    @Override
    public String toString() {
        return "VisInput{id='" + id + "', types=" + types + ", required=" + required + "}";
    }
}
