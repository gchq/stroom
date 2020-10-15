/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression.v1;

final class StringUtil {
    private StringUtil() {
        // Utility class
    }

    static String escape(final String string) {
        return "'" + string.replaceAll("'", "''") + "'";
    }

    static String unescape(final String string) {
        // Trim off containing quotes if the slice represents a single string.
        //
        // In some circumstances a string might contain two single quotes as the
        // first is used to escape a second. If this is the case then we want to
        // remove the escaping quote.
        return string.substring(1, string.length() - 1).replaceAll("''", "'");
    }
}
