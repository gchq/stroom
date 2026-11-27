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

import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// `fill_template`: the entry's value is a template with numbered placeholders (a message text such as `Account %1
/// logged on from %2`), filled from an ordered list of the record's values. The placeholder pattern is a regular
/// expression whose first capturing group is the number, e.g. `%(\d+)`; numbers count from 1, so `%10` is the tenth
/// value (the pattern's `\d+` takes every digit) and not the first followed by `0`.
///
/// A placeholder whose number has no value (fewer values than it needs) is left as written, and stepping reports it
/// (a warning naming the record). A match whose first group took no part, or isn't a number, is left as written too:
/// a pattern such as `%%|%(\d+)` keeps an escaped `%%` as it is.
///
/// @param pattern The placeholder pattern (an XPath regular expression), its first group the number.
/// @param field   Input field giving the values in order (an XML path giving several elements, say), or null.
/// @param xpath   Or an XPath relative to the record giving them in order, e.g. `Args/Arg`.
public record FillTemplate(String pattern,
                           String field,
                           String xpath) {

    /// Reads one from JSON-like values: lax types and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static FillTemplate read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "FillTemplate");
        if (fields == null) {
            return null;
        }
        final FillTemplate fill = new FillTemplate(fields.requiredString("pattern"), fields.optionalString("field"),
                fields.optionalString("xpath"));
        if (!fields.ok()) {
            return null;
        }
        final String problem = fill.problem();
        if (problem != null) {
            fields.valueError(problem);
            return null;
        }
        return fill;
    }

    // The model's own checks: one source of values, and a pattern XPath runs with a group and no empty match.
    private String problem() {
        if ((field == null) == (xpath == null) || ToolText.isBlank(field == null
                ? xpath
                : field)) {
            return "fill_template takes the values that fill the placeholders from one field or one xpath (giving "
                   + "them in order), e.g. {\"pattern\": \"%(\\\\d+)\", \"xpath\": \"Args/Arg\"}";
        }
        final Optional<String> invalid = XPathRegex.problem(pattern);
        if (invalid.isPresent()) {
            return "fill_template pattern " + ToolText.repr(pattern) + ": " + invalid.get();
        }
        try {
            if (XPathRegex.groups(pattern) < 1) {
                return "fill_template pattern " + ToolText.repr(pattern) + " needs a capturing group holding the "
                       + "placeholder's number, e.g. %(\\d+)";
            }
            if (XPathRegex.finds("^(?:" + pattern + ")$", "")) {
                return "fill_template pattern " + ToolText.repr(pattern) + " matches empty text, which XSLT refuses: "
                       + "make it match the placeholder only, e.g. %(\\d+)";
            }
        } catch (final XPathRegex.XPathRegexException e) {
            return "fill_template pattern " + ToolText.repr(pattern) + ": " + e.getMessage();
        }
        return null;
    }

    /// @return Where the values come from: the field, else the xpath.
    public String valuesSource() {
        return field != null
                ? field
                : xpath;
    }

    /// @return The input field giving the values, or null for an xpath.
    public String valuesField() {
        return field;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("pattern", pattern, null)
                .put("field", field, null)
                .put("xpath", xpath, null)
                .build();
    }

    /// The template filled as the generated XSLT fills it (`mcp:fill-template`), for a preview outside Stroom (the
    /// agent's proposal shown to the user). Java's regular expressions stand in for XPath's: the same for the
    /// patterns placeholders take.
    ///
    /// @param template The template, or null.
    /// @param pattern  The placeholder pattern.
    /// @param values   The values, in order.
    /// @return The filled text and the placeholders left as written; null text for a null template.
    public static Filled fill(final String template, final String pattern, final List<String> values) {
        if (template == null) {
            return new Filled(null, List.of());
        }
        final Pattern compiled;
        try {
            compiled = Pattern.compile(pattern);
        } catch (final PatternSyntaxException e) {
            return new Filled(template, List.of());
        }
        final Matcher matcher = compiled.matcher(template);
        final StringBuilder out = new StringBuilder();
        final List<String> unfilled = new ArrayList<>();
        int last = 0;
        while (matcher.find()) {
            out.append(template, last, matcher.start());
            final String number = matcher.groupCount() >= 1
                    ? matcher.group(1)
                    : null;
            final int at = number == null || !number.matches("\\d{1,9}")
                    ? -1
                    : Integer.parseInt(number);
            if (at >= 1 && at <= values.size()) {
                out.append(values.get(at - 1));
            } else {
                out.append(matcher.group());
                if (at >= 0) {
                    unfilled.add(matcher.group());
                }
            }
            last = matcher.end();
        }
        out.append(template, last, template.length());
        return new Filled(out.toString(), List.copyOf(unfilled));
    }


    // --------------------------------------------------------------------------------


    /// A template filled.
    ///
    /// @param text     The text, or null when there was no template.
    /// @param unfilled The placeholders with no value, as written, in order.
    public record Filled(String text, List<String> unfilled) {

    }
}
