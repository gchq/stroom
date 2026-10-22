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

package stroom.ai.impl.tools.generation;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/// Values written into messages as Python's `repr` (and `str` of a list) writes them, as the Stroom MCP server's
/// generator wrote them: `'x'`, `"it's"`, `['a', 'b']`, `{'ok': 'true'}`, `None`, `True`. Agents act on the
/// wording of the problems and warnings, and the ported ones keep it.
public final class PythonRepr {

    private PythonRepr() {
        // Static only.
    }

    /// @param value A string, list, map, boolean, number or null.
    /// @return It as Python's `repr` writes it.
    public static String repr(final Object value) {
        return switch (value) {
            case null -> "None";
            case final String text -> repr(text);
            case final Boolean bool -> bool
                    ? "True"
                    : "False";
            case final Map<?, ?> map -> map.entrySet().stream()
                    .map(entry -> repr(entry.getKey()) + ": " + repr(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
            case final Collection<?> items -> items.stream()
                    .map(PythonRepr::repr)
                    .collect(Collectors.joining(", ", "[", "]"));
            default -> String.valueOf(value);
        };
    }

    /// @param text A string, or null.
    /// @return It as Python's `repr` writes it: in single quotes, or double ones when it holds a single quote and
    /// no double one; backslashes, the quote, and characters that don't print escaped.
    public static String repr(final String text) {
        if (text == null) {
            return "None";
        }
        final boolean doubled = text.indexOf('\'') >= 0 && text.indexOf('"') < 0;
        final char quote = doubled
                ? '"'
                : '\'';
        final StringBuilder sb = new StringBuilder().append(quote);
        text.codePoints().forEach(cp -> {
            if (cp == '\\') {
                sb.append("\\\\");
            } else if (cp == quote) {
                sb.append('\\').appendCodePoint(cp);
            } else if (cp == '\n') {
                sb.append("\\n");
            } else if (cp == '\r') {
                sb.append("\\r");
            } else if (cp == '\t') {
                sb.append("\\t");
            } else if (printable(cp)) {
                sb.appendCodePoint(cp);
            } else if (cp < 0x100) {
                sb.append(String.format("\\x%02x", cp));
            } else if (cp < 0x10000) {
                sb.append(String.format("\\u%04x", cp));
            } else {
                sb.append(String.format("\\U%08x", cp));
            }
        });
        return sb.append(quote).toString();
    }

    /// @param items Strings.
    /// @return Them as Python writes a list of them, e.g. `['a', 'b']`.
    public static String list(final Collection<String> items) {
        return items.stream().map(PythonRepr::repr).collect(Collectors.joining(", ", "[", "]"));
    }

    // Python's str.isprintable for one character: not a control, format, surrogate, private-use, unassigned or
    // separator character, the space aside.
    private static boolean printable(final int cp) {
        if (cp == ' ') {
            return true;
        }
        final int type = Character.getType(cp);
        return type != Character.CONTROL
               && type != Character.FORMAT
               && type != Character.SURROGATE
               && type != Character.PRIVATE_USE
               && type != Character.UNASSIGNED
               && type != Character.SPACE_SEPARATOR
               && type != Character.LINE_SEPARATOR
               && type != Character.PARAGRAPH_SEPARATOR;
    }
}
