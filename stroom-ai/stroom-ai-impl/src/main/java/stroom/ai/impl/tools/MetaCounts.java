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

import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The fatal error, error and warning counts from the meta of some (Error) streams.
///
/// They are meta values, which Stroom deletes after a while, so a stream may have none; such a stream is counted as
/// unknown rather than as having no errors.
///
/// @param fatal      The fatal errors, of the streams with counts.
/// @param error      The errors.
/// @param warning    The warnings.
/// @param withCounts How many streams had counts.
/// @param unknown    How many streams had none.
record MetaCounts(long fatal, long error, long warning, int withCounts, int unknown) {

    /// @param rows Streams, with their meta values.
    /// @return Their counts.
    static MetaCounts of(final List<MetaRow> rows) {
        return ofAttributes(rows.stream().map(row -> NullSafe.map(row.getAttributes())).toList());
    }

    /// @param attributes The meta values of each stream.
    /// @return Their counts.
    static MetaCounts ofAttributes(final List<Map<String, String>> attributes) {
        long fatal = 0;
        long error = 0;
        long warning = 0;
        int withCounts = 0;
        int unknown = 0;
        for (final Map<String, String> map : attributes) {
            final Long fatalCount = number(map.get(MetaFields.REC_FATAL.getFldName()));
            final Long errorCount = number(map.get(MetaFields.REC_ERROR.getFldName()));
            final Long warningCount = number(map.get(MetaFields.REC_WARN.getFldName()));
            if (fatalCount == null && errorCount == null && warningCount == null) {
                unknown++;
            } else {
                withCounts++;
                fatal += Objects.requireNonNullElse(fatalCount, 0L);
                error += Objects.requireNonNullElse(errorCount, 0L);
                warning += Objects.requireNonNullElse(warningCount, 0L);
            }
        }
        return new MetaCounts(fatal, error, warning, withCounts, unknown);
    }

    /// @return True if any of the streams had counts.
    boolean known() {
        return withCounts > 0;
    }

    /// @return The counts in words, e.g. `1 fatal, 3 error(s), 0 warning(s)`, or `counts unavailable`.
    String describe() {
        return known()
                ? fatal + " fatal, " + error + " error(s), " + warning + " warning(s)"
                : "counts unavailable";
    }

    private static Long number(final String value) {
        if (NullSafe.isBlankString(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
