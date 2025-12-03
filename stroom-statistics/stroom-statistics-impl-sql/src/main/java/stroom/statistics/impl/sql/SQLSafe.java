/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.statistics.impl.sql;

import java.util.regex.Matcher;

class SQLSafe {
    private SQLSafe() {
        // class should never be instantiated
    }

    /**
     * Escapes unsafe characters for use in strings in SQL statements.
     * <p>
     * replace \ with \\
     * <p>
     * replace " with \"
     * <p>
     * replace ' with \'
     *
     * @param value The string to clean
     * @return The cleaned string
     */
    static String escapeChars(final String value) {
        // replace \ with \\
        // replace " with \"
        // replace ' with \'

        return value.replaceAll("\\\\", Matcher.quoteReplacement("\\\\"))
                .replaceAll("\\\"", Matcher.quoteReplacement("\\\""))
                .replaceAll("'", Matcher.quoteReplacement("\\'"));
    }

    static String cleanWhiteSpace(final String value) {
        return value.replaceAll("[\\t\\n\\r\\f]", " ");

    }

    /**
     * Cleans the passed string by escaping all characters deemed special in a MySQL REGEXP expression
     */
    static String cleanRegexpTerm(final String value) {
        if (value != null) {
            return value.replaceAll("([\\\\\\.\\[\\{\\(\\)\\*\\+\\?\\^\\$\\|])", "\\\\$1");
        } else {
            return value;
        }
    }

}
