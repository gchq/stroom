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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
/// @param items       Elasticsearch, with type `object`: the fields of each object of an array of them, one object
///                    per element the source selects (each item's source read from that element), e.g. `users` from
///                    `EventSource/User | EventDetail//User` with `login` from `Id`. Not in the Stroom MCP server;
///                    asked for by the user: one field of every user an event names. Empty for a field of values.
/// @param distinctBy  `distinct_by`: with items, the XPath, from each element, of the key the objects are made
///                    distinct by: one object per key, the first element with it, in the order first met (an element
///                    without one is left out), e.g. `Id` for each user once by login name. Null for every element.
public record PlannedField(String name,
                           String type,
                           String source,
                           String description,
                           String transform,
                           boolean multiple,
                           List<PlannedField> items,
                           String distinctBy) {

    /// The transforms a field may have.
    public static final List<String> TRANSFORMS = List.of("outcome");
    /// The type of a field whose values are objects ([#items()]).
    public static final String OBJECT = "object";

    /// Fills in the defaults for nulls.
    public PlannedField {
        description = Objects.requireNonNullElse(description, "");
        items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
    }

    /// A field of values, not objects.
    ///
    /// @param name        Field name in the index.
    /// @param type        Its logical type.
    /// @param source      XPath from the Event element.
    /// @param description What it holds.
    /// @param transform   Its transform, or null.
    /// @param multiple    Whether the source can hold several values in one event.
    public PlannedField(final String name,
                        final String type,
                        final String source,
                        final String description,
                        final String transform,
                        final boolean multiple) {
        this(name, type, source, description, transform, multiple, List.of(), null);
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
        // object only as given: any other type is one of the logical types, refused in the Stroom MCP server's words.
        final List<String> types = OBJECT.equals(fields.input().get("type"))
                ? List.of(OBJECT)
                : FieldPlans.LOGICAL_TYPES;
        final PlannedField field = new PlannedField(
                fields.requiredString("name"),
                fields.requiredLiteral("type", types),
                fields.requiredString("source"),
                fields.string("description", ""),
                fields.optionalLiteral("transform", TRANSFORMS),
                fields.bool("multiple", false),
                fields.models("items", (value, at) -> read(reader, value, at)),
                fields.optionalString("distinct_by"));
        if (fields.ok()) {
            final String wrong = objectProblem(field);
            if (wrong != null) {
                fields.valueError(wrong);
            }
        }
        return fields.ok()
                ? field
                : null;
    }

    // What is wrong with a field's items, type and key together, or null.
    private static String objectProblem(final PlannedField field) {
        if (OBJECT.equals(field.type()) != !field.items().isEmpty()) {
            return "a field of type object has items (its objects' fields), and one with items is of type object";
        }
        if (field.distinctBy() != null && field.items().isEmpty()) {
            return "distinct_by is for a field with items: the key its objects are made distinct by";
        }
        final Set<String> names = new HashSet<>();
        for (final PlannedField item : field.items()) {
            if (!item.items().isEmpty()) {
                return "an item can't have items of its own: " + item.name();
            }
            if (!names.add(item.name())) {
                return "items " + item.name() + " is given twice";
            }
        }
        return null;
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
        // Only for a field of objects: a plan without any is written as the Stroom MCP server wrote it.
        if (!items.isEmpty()) {
            json.put("items", items.stream().map(PlannedField::toJson).toList());
        }
        if (distinctBy != null) {
            json.put("distinct_by", distinctBy);
        }
        return json;
    }

    /// @return True if its values are objects ([#items()]).
    public boolean isObjects() {
        return !items.isEmpty();
    }

    /// Each item as a field of the index on its own, as an index template maps it and a search reads it: its name
    /// under this field's (`users.login`), from each element this field selects (`EventSource/User/Id |
    /// EventDetail//User/Id`), with several values. For a field of values, itself.
    ///
    /// @return The fields.
    public List<PlannedField> leaves() {
        if (items.isEmpty()) {
            return List.of(this);
        }
        final List<String> branches = FieldPlans.branches(source);
        return items.stream()
                .map(item -> new PlannedField(name + "." + item.name(), item.type(), String.join(" | ",
                        branches.stream().map(branch -> branch + "/" + item.source()).toList()),
                        item.description(), item.transform(), true))
                .toList();
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
        return new PlannedField(name, type, source, description, transform, multiple, items, distinctBy);
    }

    /// @param name A new name.
    /// @return This field, under that name.
    public PlannedField withName(final String name) {
        return new PlannedField(name, type, source, description, transform, multiple, items, distinctBy);
    }
}
