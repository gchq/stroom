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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.generation.ModelReader;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// One field of an index plan: its name in the index, its logical type, and the XPath in the event it comes from.
/// Ported from the Stroom MCP server's `PlannedField` model; its JSON is the server's, field for field.
///
/// @param name        Field name in the index, e.g. `UserId` or `user.name`.
/// @param type        Its logical type: one of [FieldPlans#LOGICAL_TYPES].
/// @param source      XPath from the Event element, e.g. `EventSource/User/Id` or `@StreamId`.
/// @param description What it holds (empty by default).
/// @param transform   `outcome`: an Outcome/Success (true/false) as ECS's event.outcome (success/failure); left out
///                    with no Outcome. Null for none.
/// @param multiple    Set by the tools: the source can hold several values in one event (a repeatable element on its
///                    path, by the event-logging schema, e.g. Data), so they are written as an array (Elasticsearch)
///                    or each as a value (Lucene).
public record PlannedField(String name,
                           String type,
                           String source,
                           String description,
                           String transform,
                           boolean multiple) {

    /// The transforms a field may have.
    public static final List<String> TRANSFORMS = List.of("outcome");

    /// Fills in the default description for a null one.
    public PlannedField {
        description = Objects.requireNonNullElse(description, "");
    }

    /// @param name   Field name in the index.
    /// @param type   Its logical type.
    /// @param source XPath from the Event element.
    /// @return The field, with no description, transform or several values.
    public static PlannedField of(final String name, final String type, final String source) {
        return new PlannedField(name, type, source, "", null, false);
    }

    /// @param json A planned field as JSON-like values.
    /// @return It validated, as the Python model validates it.
    /// @throws stroom.ai.impl.tools.generation.MappingValidationException if it doesn't validate.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PlannedField fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("PlannedField", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static PlannedField read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "PlannedField");
        if (fields == null) {
            return null;
        }
        final PlannedField field = new PlannedField(
                fields.requiredString("name"),
                fields.requiredLiteral("type", FieldPlans.LOGICAL_TYPES),
                fields.requiredString("source"),
                fields.string("description", ""),
                fields.optionalLiteral("transform", TRANSFORMS),
                fields.bool("multiple", false));
        return fields.ok()
                ? field
                : null;
    }

    /// @return It as JSON, as the Python model's `model_dump` writes it (and as Jackson writes it).
    @JsonValue
    public Map<String, Object> toJson() {
        final Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", name);
        json.put("type", type);
        json.put("source", source);
        json.put("description", description);
        json.put("transform", transform);
        json.put("multiple", multiple);
        return json;
    }

    /// @param at The source relative to a template's element, or null for the source itself.
    /// @return The XPath for the value written.
    public String value(final String at) {
        final String path = JsonValues.truthy(at)
                ? at
                : source;
        if ("outcome".equals(transform)) {
            return path + " ! (if (. = 'false') then 'failure' else 'success')";
        }
        return path;
    }

    /// @param multiple Whether the source can hold several values in one event.
    /// @return This field, marked so.
    public PlannedField withMultiple(final boolean multiple) {
        return new PlannedField(name, type, source, description, transform, multiple);
    }

    /// @param name A new name.
    /// @return This field, under that name.
    public PlannedField withName(final String name) {
        return new PlannedField(name, type, source, description, transform, multiple);
    }
}
