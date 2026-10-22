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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// A test on the record (or, with `for_each`, the item): give field or xpath and one of equals, one_of, matches,
/// present or in_dictionary. Ported from the Stroom MCP server's `Condition` model.
///
/// @param field        An input field, or a name from extract.
/// @param xpath        Or an XPath expression.
/// @param equalTo      `equals`: the value it is.
/// @param oneOf        `one_of`: the values it is one of.
/// @param matches      Regular expression (XPath flavour) it matches.
/// @param present      True: the field has a non-empty value; false: it does not.
/// @param inDictionary `in_dictionary`: the value is one of the lines of the Dictionary doc of this name (a list,
///                     one entry per line).
/// @param scope        With for_each: `record` to test the record rather than the item.
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"field", "xpath", "equals", "one_of", "matches", "present", "in_dictionary", "scope"})
public record Condition(@JsonProperty("field") String field,
                        @JsonProperty("xpath") String xpath,
                        @JsonProperty("equals") String equalTo,
                        @JsonProperty("one_of") List<String> oneOf,
                        @JsonProperty("matches") String matches,
                        @JsonProperty("present") Boolean present,
                        @JsonProperty("in_dictionary") String inDictionary,
                        @JsonProperty("scope") String scope) {

    /// The scopes a field may have with `for_each`.
    public static final List<String> SCOPES = List.of("record", "item");

    private static final List<String> TESTS = List.of("equals", "one_of", "matches", "in_dictionary");

    /// @param json A condition as JSON-like values.
    /// @return It validated, as the Python model validates it.
    /// @throws MappingValidationException if it doesn't validate.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Condition fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("Condition", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static Condition read(final ModelReader reader, final Object input, final List<Object> loc) {
        final Object data = spread(input);
        final ModelReader.Fields fields = reader.fields(data, loc, "Condition");
        if (fields == null) {
            return null;
        }
        final Condition condition = new Condition(fields.optionalString("field"), fields.optionalString("xpath"),
                fields.optionalString("equals"), fields.optionalStrings("one_of"), fields.optionalString("matches"),
                fields.optionalBool("present"), fields.optionalString("in_dictionary"),
                fields.optionalLiteral("scope", SCOPES));
        if (!fields.ok()) {
            return null;
        }
        if ((condition.field == null) == (condition.xpath == null)) {
            reader.valueError(loc, "A condition needs exactly one of field or xpath", input);
            return null;
        }
        final String called = MappingFacts.functionCall(condition.field == null
                ? ""
                : condition.field);
        if (called != null) {
            reader.valueError(loc, "A condition's field " + PythonRepr.repr(condition.field) + " " + called, input);
            return null;
        }
        final long tests = Stream.of(condition.equalTo, condition.oneOf, condition.matches, condition.present,
                condition.inDictionary).filter(test -> test != null).count();
        if (tests != 1) {
            reader.valueError(loc, "A condition needs exactly one of equals, one_of, matches, present or "
                                   + "in_dictionary", input);
            return null;
        }
        return condition;
    }

    // Seen: "matches": {"value": "Event: \\[User\\]", "xpath": "/Event/EventData/Data"}. The test's value and its
    // input given together: the same condition, spread out.
    @SuppressWarnings("unchecked")
    private static Object spread(final Object input) {
        if (!(input instanceof Map)) {
            return input;
        }
        Map<String, Object> data = (Map<String, Object>) input;
        for (final String test : TESTS) {
            if (data.get(test) instanceof final Map<?, ?> given) {
                Object value = null;
                for (final String key : List.of("value", "values", "regex", "pattern", test)) {
                    if (given.containsKey(key)) {
                        value = given.get(key);
                        break;
                    }
                }
                final Map<String, Object> spread = new LinkedHashMap<>(data);
                spread.put(test, value);
                for (final String key : List.of("field", "xpath", "scope")) {
                    if (given.containsKey(key) && data.get(key) == null) {
                        spread.put(key, given.get(key));
                    }
                }
                data = spread;
            }
        }
        return data;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the Python model's `model_dump` writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("field", field, null)
                .put("xpath", xpath, null)
                .put("equals", equalTo, null)
                .put("one_of", oneOf, null)
                .put("matches", matches, null)
                .put("present", present, null)
                .put("in_dictionary", inDictionary, null)
                .put("scope", scope, null)
                .build();
    }
}
