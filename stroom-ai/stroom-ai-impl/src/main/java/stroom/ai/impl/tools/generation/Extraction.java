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

/// Fields parsed out of one text field with a regular expression: a message string holding a time, a user, an
/// action and a description, say. Each capture group becomes a field (names, in group order) that common, events
/// and when use like any input field. Records the pattern does not match get no values from it, so the elements
/// are left out, which a rule's conditions can test with present.
///
/// @param field The input field holding the text (or a name from an earlier extraction).
/// @param xpath Or an XPath expression giving the text.
/// @param regex XPath regular expression with one capture group per field.
/// @param names A field name per capture group, in order (at least one); `""` skips a group.
/// @param flags XPath regex flags: i, m, s, x.
/// @param scope With for_each: `record` when the text is the record's, not the item's.
public record Extraction(String field,
                         String xpath,
                         String regex,
                         List<String> names,
                         String flags,
                         String scope) {

    /// Fills in the defaults for nulls.
    public Extraction {
        names = List.copyOf(Objects.requireNonNullElse(names, List.of()));
        flags = Objects.requireNonNullElse(flags, "");
    }

    /// @param json An extraction as JSON-like values.
    /// @return It validated, with the model's own checks.
    /// @throws MappingValidationException if it doesn't validate.
    public static Extraction fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("Extraction", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static Extraction read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "Extraction");
        if (fields == null) {
            return null;
        }
        final Extraction extraction = new Extraction(fields.optionalString("field"), fields.optionalString("xpath"),
                fields.requiredString("regex"), fields.requiredStrings("names", 1), fields.string("flags", ""),
                fields.optionalLiteral("scope", Condition.SCOPES));
        if (!fields.ok()) {
            return null;
        }
        if ((extraction.field == null) == (extraction.xpath == null)) {
            fields.valueError("An extraction needs exactly one of field or xpath");
            return null;
        }
        return extraction;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("field", field, null)
                .put("xpath", xpath, null)
                .put("regex", regex, null)
                .put("names", names, null)
                .put("flags", flags, "")
                .put("scope", scope, null)
                .build();
    }
}
