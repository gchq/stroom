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

package stroom.dataconnection.impl;

import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// What the connectors share in reading settings and checking them against the administrator's
/// controls.
final class ConnectionRules {

    private ConnectionRules() {
        // Static methods only.
    }

    /// Finds whether a value matches any of the patterns an administrator allows.
    ///
    /// @param value       The value, e.g. a URL.
    /// @param patterns    The allowed regular expressions, of which blank ones are ignored.
    /// @param patternName What the patterns are, for a problem with one, e.g. `URL pattern`.
    /// @param problems    Takes a problem for each pattern that is not a valid regular expression.
    /// @return True if the value matches a pattern.
    static boolean matchesAny(final String value,
                              final List<String> patterns,
                              final String patternName,
                              final List<String> problems) {
        for (final String pattern : NullSafe.list(patterns)) {
            if (NullSafe.isNonBlankString(pattern)) {
                try {
                    if (Pattern.compile(pattern).matcher(value).matches()) {
                        return true;
                    }
                } catch (final PatternSyntaxException e) {
                    problems.add("The allowed " + patternName + " '" + pattern
                                 + "' is not a valid regular expression: " + e.getDescription());
                }
            }
        }
        return false;
    }

    /// Reads a setting that can't be negative.
    ///
    /// @param value        The setting, or `null` if it is not set.
    /// @param defaultValue The value to use if it is not set.
    /// @return The setting, or the default, and 0 if either is negative.
    static int nonNegative(final Integer value, final int defaultValue) {
        return Math.max(0, value == null
                ? defaultValue
                : value);
    }

    /// Throws if any problem was found.
    ///
    /// @param problems The problems found.
    /// @throws IllegalArgumentException If there is any; its message lists them all.
    static void throwIfAny(final List<String> problems) {
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", problems));
        }
    }
}
