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

package stroom.query.client.component;

import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StringUtil;

import java.util.ArrayList;
import java.util.List;

/// The values of an `in` term, as its value box holds them: separated by commas, with any space
/// around each ignored. Used by [TermEditor] to put values picked from the value picker in the box.
public final class TermValueList {

    private TermValueList() {
        // Static utility
    }

    /// @param text The value box's text, or null.
    /// @return The values in it, in order, without blank ones.
    public static List<String> parse(final String text) {
        final List<String> values = new ArrayList<>();
        final String trimmedText = StringUtil.trimWhitespace(text);
        if (NullSafe.isNonBlankString(trimmedText)) {
            for (final String item : trimmedText.split(Condition.IN_CONDITION_DELIMITER)) {
                final String trimmed = StringUtil.trimWhitespace(item);
                if (NullSafe.isNonBlankString(trimmed)) {
                    values.add(trimmed);
                }
            }
        }
        return values;
    }

    /// Adds a value to the list, or removes it if the list holds it.
    ///
    /// @param text  The value box's text, or null.
    /// @param value The value picked.
    /// @return The box's new text: the values separated by a comma and a space.
    public static String toggle(final String text, final String value) {
        final List<String> values = parse(text);
        if (!values.remove(value)) {
            values.add(value);
        }
        return String.join(Condition.IN_CONDITION_DELIMITER + " ", values);
    }
}
