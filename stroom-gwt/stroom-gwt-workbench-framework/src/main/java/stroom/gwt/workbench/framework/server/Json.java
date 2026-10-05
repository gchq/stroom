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

package stroom.gwt.workbench.framework.server;

import java.util.List;
import java.util.stream.Collectors;

/// The little JSON writing the workbench server needs, to avoid a JSON library dependency.
final class Json {

    private Json() {
        // Static utility
    }

    /// @param value A string.
    /// @return The string as a JSON string literal.
    static String quote(final String value) {
        if (value == null) {
            return "null";
        }
        final StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            switch (chr) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (chr < ' ' || chr == '<' || chr == '>') {
                        sb.append(String.format("\\u%04x", (int) chr));
                    } else {
                        sb.append(chr);
                    }
                    break;
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /// @param values Strings.
    /// @return The strings as a JSON array.
    static String array(final List<String> values) {
        return values.stream()
                .map(Json::quote)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
