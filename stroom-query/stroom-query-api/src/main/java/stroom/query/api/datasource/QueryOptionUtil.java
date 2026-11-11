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

package stroom.query.api.datasource;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/// Works out the effective values of field-level query options from the values stored in an
/// expression term, applying defaults and the rules that disable options.
public final class QueryOptionUtil {

    private QueryOptionUtil() {
        // Utility class.
    }

    /// Resolves the effective value of every option in a set.
    ///
    /// Options are resolved in the order the set lists them. An unset, unknown or invalid value takes the
    /// option's default. An option disabled by one of its rules also takes its default. A rule that refers
    /// to an option listed earlier tests that option's effective value; a rule that refers to an option
    /// listed later tests its stored value. So when two options disable each other and both are set, the
    /// option listed later wins.
    ///
    /// @param queryOptionSet The set of options the field supports, or null if it supports none.
    /// @param options        The option values stored in the term, or null.
    /// @return The effective value of every option in the set, keyed by option id, in the set's order.
    public static Map<String, String> resolve(final QueryOptionSet queryOptionSet,
                                              final Map<String, String> options) {
        if (queryOptionSet == null) {
            return Collections.emptyMap();
        }
        return resolve(queryOptionSet, options, new HashSet<>());
    }

    /// Finds the options that are disabled by their rules, given the values stored in a term.
    ///
    /// @param queryOptionSet The set of options the field supports, or null if it supports none.
    /// @param options        The option values stored in the term, or null.
    /// @return The ids of the disabled options.
    public static Set<String> getDisabledOptionIds(final QueryOptionSet queryOptionSet,
                                                   final Map<String, String> options) {
        if (queryOptionSet == null) {
            return Collections.emptySet();
        }
        final Set<String> disabled = new HashSet<>();
        resolve(queryOptionSet, options, disabled);
        return disabled;
    }

    /// Reduces option values to those worth storing in a term: the options whose effective value
    /// differs from the default.
    ///
    /// @param queryOptionSet The set of options the field supports, or null if it supports none.
    /// @param options        The option values to reduce, or null.
    /// @return The non-default effective values, or null if every option has its default value.
    public static Map<String, String> normalise(final QueryOptionSet queryOptionSet,
                                                final Map<String, String> options) {
        final Map<String, String> resolved = resolve(queryOptionSet, options);
        final Map<String, String> nonDefault = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : resolved.entrySet()) {
            final QueryOptionDefinition definition = queryOptionSet.getDefinition(entry.getKey());
            if (!definition.getDefaultValue().equals(entry.getValue())) {
                nonDefault.put(entry.getKey(), entry.getValue());
            }
        }
        return nonDefault.isEmpty()
                ? null
                : nonDefault;
    }

    /// Checks whether any option has an effective value other than its default.
    ///
    /// @param queryOptionSet The set of options the field supports, or null if it supports none.
    /// @param options        The option values stored in the term, or null.
    /// @return True if at least one option is not at its default.
    public static boolean hasNonDefaultOptions(final QueryOptionSet queryOptionSet,
                                               final Map<String, String> options) {
        return normalise(queryOptionSet, options) != null;
    }

    /// Reads a {@link QueryOptionType#BOOLEAN} option from resolved values.
    ///
    /// @param resolved The values returned by {@link #resolve(QueryOptionSet, Map)}.
    /// @param id       The option id.
    /// @return The option's value, or false if the option is not in the values.
    public static boolean getBoolean(final Map<String, String> resolved, final String id) {
        return "true".equals(resolved.get(id));
    }

    /// Reads a {@link QueryOptionType#INTEGER} option from resolved values.
    ///
    /// @param resolved The values returned by {@link #resolve(QueryOptionSet, Map)}.
    /// @param id       The option id.
    /// @return The option's value.
    /// @throws IllegalArgumentException If the option is not in the values.
    public static int getInteger(final Map<String, String> resolved, final String id) {
        final String value = resolved.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Query option not found: " + id);
        }
        return Integer.parseInt(value);
    }

    private static Map<String, String> resolve(final QueryOptionSet queryOptionSet,
                                               final Map<String, String> options,
                                               final Set<String> disabledOut) {
        final Map<String, String> resolved = new LinkedHashMap<>();
        for (final QueryOptionDefinition definition : queryOptionSet.getDefinitions()) {
            final String value;
            if (isDisabled(queryOptionSet, definition, options, resolved)) {
                disabledOut.add(definition.getId());
                value = definition.getDefaultValue();
            } else {
                value = getStoredOrDefault(definition, options);
            }
            resolved.put(definition.getId(), value);
        }
        return resolved;
    }

    private static boolean isDisabled(final QueryOptionSet queryOptionSet,
                                      final QueryOptionDefinition definition,
                                      final Map<String, String> options,
                                      final Map<String, String> resolvedSoFar) {
        for (final QueryOptionRule rule : definition.getRules()) {
            final String otherValue;
            if (resolvedSoFar.containsKey(rule.getOptionId())) {
                otherValue = resolvedSoFar.get(rule.getOptionId());
            } else {
                final QueryOptionDefinition otherDefinition = queryOptionSet.getDefinition(rule.getOptionId());
                otherValue = otherDefinition == null
                        ? null
                        : getStoredOrDefault(otherDefinition, options);
            }
            if (otherValue != null && rule.appliesTo(otherValue)) {
                return true;
            }
        }
        return false;
    }

    private static String getStoredOrDefault(final QueryOptionDefinition definition,
                                             final Map<String, String> options) {
        final String stored = options == null
                ? null
                : options.get(definition.getId());
        return definition.isValid(stored)
                ? stored
                : definition.getDefaultValue();
    }
}
