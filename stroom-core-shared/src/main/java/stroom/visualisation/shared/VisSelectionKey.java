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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// A key a visualisation gives Stroom when something in it is selected, declared in the optional
/// `selection` section of its settings, e.g.
/// ```
/// {"key": "series", "label": "Series", "type": "TEXT"}
/// ```
/// Other panes' selection handlers use it as `${component.<id>.selection.series}`, so Stroom offers
/// it for them to insert. A visualisation that declares no keys is taken to give `name` and
/// `value`, as all did before keys were declared.
@JsonPropertyOrder({"key", "label", "type"})
@JsonInclude(Include.NON_NULL)
public final class VisSelectionKey {

    @JsonProperty
    private final String key;
    @JsonProperty
    private final String label;
    @JsonProperty
    private final String type;

    /// @param key   The key its selections' values are under, e.g. `series`.
    /// @param label What to call the key, e.g. `Series`, or null to use the key.
    /// @param type  The type of its values, a [VisInputType] name such as `NUMBER`, or null.
    @JsonCreator
    public VisSelectionKey(@JsonProperty("key") final String key,
                           @JsonProperty("label") final String label,
                           @JsonProperty("type") final String type) {
        this.key = key;
        this.label = label;
        this.type = type;
    }

    /// @return The key, e.g. `series`.
    public String getKey() {
        return key;
    }

    /// @return Its name for people to read, e.g. `Series`, or null.
    public String getLabel() {
        return label;
    }

    /// @return The name of its values' type, as the settings give it, or null.
    public String getType() {
        return type;
    }

    /// @return The type of its values, or null if the settings name none or a name that isn't one.
    @JsonIgnore
    public VisInputType getValueType() {
        return VisInputType.parse(type);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisSelectionKey that = (VisSelectionKey) o;
        return Objects.equals(key, that.key)
               && Objects.equals(label, that.label)
               && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, label, type);
    }

    @Override
    public String toString() {
        return "VisSelectionKey{key='" + key + "', type=" + type + "}";
    }
}
