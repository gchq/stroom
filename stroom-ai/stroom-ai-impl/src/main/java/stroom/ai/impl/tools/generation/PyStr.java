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

import java.util.ArrayList;
import java.util.List;

/// Python's string methods where they differ from Java's: what `strip()` takes for white space (`str.isspace`),
/// `strip(chars)`, and `split(sep)` keeping empty parts.
final class PyStr {

    private PyStr() {
        // Static only.
    }

    /// @param cp A code point.
    /// @return True if Python's `str.isspace` takes it for white space.
    static boolean isSpace(final int cp) {
        return switch (cp) {
            case '\t', '\n', 0x0b, 0x0c, '\r', 0x1c, 0x1d, 0x1e, 0x1f, ' ', 0x85, 0xa0, 0x1680, 0x2028, 0x2029, 0x202f,
                 0x205f, 0x3000 -> true;
            default -> cp >= 0x2000 && cp <= 0x200a;
        };
    }

    /// @param text Text.
    /// @return It without leading and trailing white space, as Python's `strip()`.
    static String strip(final String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /// @param text Text.
    /// @return It without trailing white space, as Python's `rstrip()`.
    static String rstrip(final String text) {
        int end = text.length();
        while (end > 0 && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /// @param text  Text.
    /// @param chars The characters to strip.
    /// @return It without leading and trailing characters among chars, as Python's `strip(chars)`.
    static String strip(final String text, final String chars) {
        int start = 0;
        int end = text.length();
        while (start < end && chars.indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(start, end);
    }

    /// @param text  Text.
    /// @param chars The characters to strip.
    /// @return It without trailing characters among chars, as Python's `rstrip(chars)`.
    static String rstrip(final String text, final String chars) {
        int end = text.length();
        while (end > 0 && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(0, end);
    }

    /// @param text Text.
    /// @return True if it is empty or all white space (Python's `not text.strip()`).
    static boolean isBlank(final String text) {
        return strip(text).isEmpty();
    }

    /// @param text      Text.
    /// @param separator The separator.
    /// @return The parts between separators, empty ones included, as Python's `split(sep)`.
    static List<String> split(final String text, final String separator) {
        final List<String> out = new ArrayList<>();
        int from = 0;
        while (true) {
            final int at = text.indexOf(separator, from);
            if (at < 0) {
                out.add(text.substring(from));
                return out;
            }
            out.add(text.substring(from, at));
            from = at + separator.length();
        }
    }

    /// @param path A path.
    /// @return It without leading and trailing slashes (Python's `strip('/')`).
    static String stripSlashes(final String path) {
        return strip(path, "/");
    }
}
