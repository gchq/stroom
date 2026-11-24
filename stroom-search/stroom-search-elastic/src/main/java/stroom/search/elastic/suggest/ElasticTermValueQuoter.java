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

package stroom.search.elastic.suggest;

import stroom.query.api.datasource.FieldType;

/// Formats a value picked from a field's most common values so that an Elasticsearch term matches it exactly.
public final class ElasticTermValueQuoter {

    private static final String QUOTE = "\"";

    private ElasticTermValueQuoter() {
        // Utility class.
    }

    /// Formats a picked value for a term's value box.
    ///
    /// A text field's value is searched with Elasticsearch query string syntax, so it is always quoted as a
    /// phrase, with any quotes and backslashes in it escaped. Otherwise characters such as `:` and `/` would
    /// be read as query syntax. Other fields' values are quoted when they contain a space or a wildcard
    /// character, or are already in quotes, so that they are matched as they are rather than as a pattern.
    ///
    /// @param fieldType The type of the term's field.
    /// @param value     The picked value.
    /// @return The value as it should appear in the value box.
    public static String quote(final FieldType fieldType, final String value) {
        if (FieldType.TEXT.equals(fieldType)) {
            return QUOTE + value.replace("\\", "\\\\").replace(QUOTE, "\\\"") + QUOTE;
        } else if (value.contains(" ")
                   || value.contains("*")
                   || value.contains("?")
                   || (value.length() > 1 && value.startsWith(QUOTE) && value.endsWith(QUOTE))) {
            return QUOTE + value + QUOTE;
        } else {
            return value;
        }
    }
}
