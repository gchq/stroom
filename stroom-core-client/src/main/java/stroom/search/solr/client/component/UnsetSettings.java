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

package stroom.search.solr.client.component;

import stroom.query.api.ExpressionOperator;
import stroom.util.shared.NullSafe;

import java.util.List;

/// Writes the settings that aren't set as they were read, as a setting that isn't set is stored in
/// several ways (none, blank, a list of nothing or of one blank), and is shown as a blank. So showing
/// the settings doesn't change them.
final class UnsetSettings {

    private UnsetSettings() {
        // Static utility
    }

    /// @param shown The lines shown.
    /// @param read  The lines read, or null.
    /// @return The lines shown, or those read if neither has any that isn't blank.
    static List<String> linesAsRead(final List<String> shown, final List<String> read) {
        return isUnset(shown) && isUnset(read)
                ? read
                : shown;
    }

    /// @param shown The expression shown.
    /// @param read  The expression read, or null.
    /// @return The expression shown, or null if none was read and it has no terms.
    static ExpressionOperator expressionAsRead(final ExpressionOperator shown, final ExpressionOperator read) {
        return read == null && (shown == null || NullSafe.isEmptyCollection(shown.getChildren()))
                ? null
                : shown;
    }

    private static boolean isUnset(final List<String> lines) {
        return NullSafe.stream(lines).allMatch(NullSafe::isBlankString);
    }
}
