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

package stroom.ai.impl.tools.generation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/// One part of a reference map's value: text, or an element named `element` under `<value>`.
///
/// @param element Element name under the value, e.g. `department`; empty for a plain text value (a map has at most
///                one of those).
/// @param field   Input field (Data Splitter name or JSON key).
/// @param value   Or a constant.
/// @param xpath   Or an XPath relative to the record.
/// @param copy    With field or xpath, and an element: the nodes it selects copied whole into the element (a table of
///                entries, say, for a lookup's `path` to read, or mask_names' table), not their text.
public record ReferenceValue(String element,
                             String field,
                             String value,
                             String xpath,
                             boolean copy) {

    /// Fills in the default element (none: a text value).
    public ReferenceValue {
        element = Objects.requireNonNullElse(element, "");
    }

    /// A value part written as text.
    ///
    /// @param element Element name under the value, or empty for a text value.
    /// @param field   Input field.
    /// @param value   Or a constant.
    /// @param xpath   Or an XPath relative to the record.
    public ReferenceValue(final String element, final String field, final String value, final String xpath) {
        this(element, field, value, xpath, false);
    }

    /// @param json A value part as JSON-like values.
    /// @return It validated, with the model's own checks.
    /// @throws MappingValidationException if it doesn't validate.
    public static ReferenceValue fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("ReferenceValue", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static ReferenceValue read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "ReferenceValue");
        if (fields == null) {
            return null;
        }
        final ReferenceValue part = new ReferenceValue(fields.string("element", ""), fields.optionalString("field"),
                fields.optionalString("value"), fields.optionalString("xpath"), fields.bool("copy", false));
        if (!fields.ok()) {
            return null;
        }
        if (part.copy && (part.element.isEmpty() || part.value != null)) {
            reader.valueError(loc, "value '" + (part.element.isEmpty()
                    ? "text"
                    : part.element) + "': copy puts the nodes a field or xpath selects inside an element: give "
                                   + "element and field or xpath", input);
            return null;
        }
        if (Stream.of(part.field, part.value, part.xpath).filter(Objects::nonNull).count() != 1) {
            reader.valueError(loc, "value '" + (part.element.isEmpty()
                    ? "text"
                    : part.element) + "': give exactly one of field, value or xpath", input);
            return null;
        }
        return part;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("element", element, "")
                .put("field", field, null)
                .put("value", value, null)
                .put("xpath", xpath, null)
                .putIfSet("copy", copy
                        ? Boolean.TRUE
                        : null)
                .build();
    }
}
