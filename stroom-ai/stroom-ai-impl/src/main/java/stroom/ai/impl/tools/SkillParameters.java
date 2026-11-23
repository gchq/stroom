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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.SkillDoc.Parameter;
import stroom.ai.impl.tools.SkillDoc.Skill;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/// A skill's parameters given values for a run (a trial run of a draft, or a skill run by name): the values given,
/// the defaults for those not given, and the required ones still missing, with the skill's steps and output with
/// each `{name}` replaced by its value.
final class SkillParameters {

    /// The longest a parameter's value may be.
    static final int MAX_VALUE_CHARS = 500;

    private SkillParameters() {
        // Static only.
    }

    /// @param skill  The skill.
    /// @param given  The values given, by parameter name (others are reported as unknown); null for none.
    /// @return The parameters bound.
    static Bound bind(final Skill skill, final Map<?, ?> given) {
        final Map<String, String> values = new LinkedHashMap<>();
        final List<String> defaulted = new ArrayList<>();
        final List<Parameter> missing = new ArrayList<>();
        final List<String> unknown = new ArrayList<>();
        NullSafe.map(given).forEach((key, value) -> {
            final String name = String.valueOf(key).strip();
            if (skill.parameter(name).isEmpty()) {
                unknown.add(name);
            }
        });
        for (final Parameter parameter : skill.parameters()) {
            final Object value = NullSafe.map(given).get(parameter.name());
            final String text = value == null
                    ? null
                    : String.valueOf(value).strip();
            if (NullSafe.isNonBlankString(text)) {
                values.put(parameter.name(), text.length() > MAX_VALUE_CHARS
                        ? text.substring(0, MAX_VALUE_CHARS)
                        : text);
            } else if (parameter.defaultValue() != null) {
                values.put(parameter.name(), parameter.defaultValue());
                defaulted.add(parameter.name());
            } else if (parameter.required()) {
                missing.add(parameter);
            }
        }
        return new Bound(skill, Map.copyOf(values), List.copyOf(defaulted), List.copyOf(missing),
                List.copyOf(unknown));
    }

    /// @param text   A skill's text.
    /// @param values Parameters' values, by name.
    /// @return The text with each `{name}` of a parameter with a value replaced by it; others left as they are.
    static String substitute(final String text, final Map<String, String> values) {
        final Matcher matcher = SkillDoc.PARAMETER_USE.matcher(NullSafe.string(text));
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            final String value = values.get(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null
                    ? matcher.group()
                    : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    /// A skill's parameters given values.
    ///
    /// @param skill     The skill.
    /// @param values    Each parameter's value, given or its default, by name.
    /// @param defaulted The parameters given their default.
    /// @param missing   The required parameters not given.
    /// @param unknown   The values given for parameters the skill doesn't list.
    record Bound(Skill skill,
                 Map<String, String> values,
                 List<String> defaulted,
                 List<Parameter> missing,
                 List<String> unknown) {

        /// @return The skill's steps, each `{name}` replaced by its value.
        String steps() {
            return substitute(NullSafe.string(skill.sections().get(SkillDoc.STEPS)), values);
        }

        /// @return The skill's output, each `{name}` replaced by its value.
        String output() {
            return substitute(skill.output(), values);
        }

        /// @return The values, as a line, e.g. `user=jsmith, days=7 (default)`.
        String describe() {
            final List<String> parts = new ArrayList<>();
            skill.parameters().forEach(parameter -> NullSafe.consume(values.get(parameter.name()), value ->
                    parts.add(parameter.name() + "=" + value + (defaulted.contains(parameter.name())
                            ? " (default)"
                            : ""))));
            return parts.isEmpty()
                    ? "no parameters"
                    : String.join(", ", parts);
        }
    }
}
