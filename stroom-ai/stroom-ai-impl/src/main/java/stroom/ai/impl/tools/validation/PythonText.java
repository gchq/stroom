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

package stroom.ai.impl.tools.validation;

import java.util.Collection;
import java.util.stream.Collectors;

/// Values written into messages as the Stroom MCP server wrote them, with Python's `repr`: a list of names as
/// `['Authenticate', 'Process']`, a string as `'2026-09-28 10:00'`. Agents act on the wording of the messages, and
/// the ported messages keep it.
public final class PythonText {

    private PythonText() {
        // Static only.
    }

    /// @param text A string, or null.
    /// @return It as Python's `repr` writes it, e.g. `'x'`, or `"it's"` when it holds a single quote; `None` for
    /// null.
    public static String repr(final String text) {
        if (text == null) {
            return "None";
        }
        final boolean doubled = text.contains("'") && !text.contains("\"");
        final char quote = doubled
                ? '"'
                : '\'';
        final StringBuilder sb = new StringBuilder().append(quote);
        for (final char ch : text.toCharArray()) {
            if (ch == '\\') {
                sb.append("\\\\");
            } else if (ch == quote) {
                sb.append('\\').append(ch);
            } else if (ch == '\n') {
                sb.append("\\n");
            } else if (ch == '\r') {
                sb.append("\\r");
            } else if (ch == '\t') {
                sb.append("\\t");
            } else {
                sb.append(ch);
            }
        }
        return sb.append(quote).toString();
    }

    /// @param items Strings.
    /// @return Them as Python writes a list of strings, e.g. `['a', 'b']`.
    public static String list(final Collection<String> items) {
        return items.stream()
                .map(PythonText::repr)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /// @param items Numbers.
    /// @return Them as Python writes a list of numbers, e.g. `[0, 3]`.
    public static String numbers(final Collection<? extends Number> items) {
        return items.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
