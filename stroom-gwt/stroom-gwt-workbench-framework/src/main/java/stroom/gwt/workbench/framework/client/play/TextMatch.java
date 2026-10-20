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

package stroom.gwt.workbench.framework.client.play;

import com.google.gwt.regexp.shared.RegExp;

import java.util.Objects;

/// How a query or expectation matches text, the equivalent of the string or regular expression
/// Testing Library's queries and Jest's `toMatch` take. A plain `String` passed to a query is an
/// [#exact(String)] match.
///
/// Mapping from the forms used in React plays:
///
/// | JavaScript                         | Java                                       |
/// |------------------------------------|--------------------------------------------|
/// | `'Save'`                           | `"Save"` or `TextMatch.exact("Save")`      |
/// | `/Save/`                           | `TextMatch.containing("Save")`             |
/// | `/save/i` or `{ exact: false }`    | `TextMatch.containingIgnoreCase("save")`   |
/// | `/^ok$/i`                          | `TextMatch.exactIgnoreCase("ok")`          |
/// | `/^Locked/`                        | `TextMatch.startingWith("Locked")`         |
/// | `/- Click for help$/`              | `TextMatch.endingWith("- Click for help")` |
/// | anything else, e.g. `/close\|ok/i` | `TextMatch.regex("close\|ok", "i")`        |
///
/// [#regex(String, String)] uses the browser's own `RegExp`, so in the workbench a JavaScript
/// regular expression can be copied as it is (with its flags; `g` is dropped, as Testing Library
/// resets it anyway). In JVM tests GWT's `RegExp` is emulated with `java.util.regex`, which only
/// accepts the `g`, `i` and `m` flags (so `s`, `u` and `y` throw) and ignores case only for ASCII
/// letters; expressions using JavaScript-only syntax may also behave differently there.
public final class TextMatch implements ValueMatcher {

    private static final String REGEX_SPECIALS = "\\^$.|?*+()[]{}/";

    private final Kind kind;
    private final String text;
    private final String flags;
    // Only for Kind.REGEX, compiled lazily
    private RegExp regExp;

    private TextMatch(final Kind kind, final String text, final String flags) {
        this.kind = kind;
        this.text = Objects.requireNonNull(text, "text");
        this.flags = flags;
    }

    /// @param text The text.
    /// @return A match for text that is exactly the text (after Testing Library's whitespace
    /// normalisation when used by a query).
    public static TextMatch exact(final String text) {
        return new TextMatch(Kind.EXACT, text, "");
    }

    /// @param text The text.
    /// @return A match for text that is the text, ignoring case, e.g. `/^ok$/i`.
    public static TextMatch exactIgnoreCase(final String text) {
        return new TextMatch(Kind.EXACT_IGNORE_CASE, text, "i");
    }

    /// @param text The text.
    /// @return A match for text that contains the text, e.g. `/Save/`.
    public static TextMatch containing(final String text) {
        return new TextMatch(Kind.CONTAINING, text, "");
    }

    /// @param text The text.
    /// @return A match for text that contains the text ignoring case, e.g. `/save/i` or Testing
    /// Library's `{ exact: false }`.
    public static TextMatch containingIgnoreCase(final String text) {
        return new TextMatch(Kind.CONTAINING_IGNORE_CASE, text, "i");
    }

    /// @param text The text.
    /// @return A match for text that starts with the text, e.g. `/^Locked/`.
    public static TextMatch startingWith(final String text) {
        return new TextMatch(Kind.STARTING_WITH, text, "");
    }

    /// @param text The text.
    /// @return A match for text that ends with the text, e.g. `/help$/`.
    public static TextMatch endingWith(final String text) {
        return new TextMatch(Kind.ENDING_WITH, text, "");
    }

    /// @param pattern A JavaScript regular expression without the slashes, e.g. `^(geo|reference)$`.
    /// @return A match for text the regular expression matches (anywhere, as `RegExp.test` does).
    public static TextMatch regex(final String pattern) {
        return regex(pattern, "");
    }

    /// @param pattern A JavaScript regular expression without the slashes, e.g. `close|ok`.
    /// @param flags   The regular expression's flags, e.g. `i` for case insensitive.
    /// @return A match for text the regular expression matches (anywhere, as `RegExp.test` does).
    public static TextMatch regex(final String pattern, final String flags) {
        return new TextMatch(Kind.REGEX, pattern, flags != null
                ? flags.replace("g", "")
                : "");
    }

    /// @param candidate The text to test, may be null.
    /// @return True if the text matches; null never matches.
    public boolean matches(final String candidate) {
        if (candidate == null) {
            return false;
        }
        switch (kind) {
            case EXACT:
                return candidate.equals(text);
            case EXACT_IGNORE_CASE:
                return candidate.toLowerCase().equals(text.toLowerCase());
            case CONTAINING:
                return candidate.contains(text);
            case CONTAINING_IGNORE_CASE:
                return candidate.toLowerCase().contains(text.toLowerCase());
            case STARTING_WITH:
                return candidate.startsWith(text);
            case ENDING_WITH:
                return candidate.endsWith(text);
            case REGEX:
            default:
                if (regExp == null) {
                    regExp = RegExp.compile(text, flags);
                }
                return regExp.test(candidate);
        }
    }

    /// @param value The value to test.
    /// @return True if the value is a string the text matches.
    @Override
    public boolean matchesValue(final Object value) {
        return value instanceof String && matches((String) value);
    }

    /// @return The match as JavaScript, as shown in the Interactions addon, e.g. `"Save"` or
    /// `/^ok$/i`.
    @Override
    public String describe() {
        switch (kind) {
            case EXACT:
                return Expectation.quote(text);
            case EXACT_IGNORE_CASE:
                return "/^" + escape(text) + "$/i";
            case CONTAINING:
                return "/" + escape(text) + "/";
            case CONTAINING_IGNORE_CASE:
                return "/" + escape(text) + "/i";
            case STARTING_WITH:
                return "/^" + escape(text) + "/";
            case ENDING_WITH:
                return "/" + escape(text) + "$/";
            case REGEX:
            default:
                return "/" + text + "/" + flags;
        }
    }

    /// @return True if the match is a plain exact string, which descriptions show quoted.
    boolean isExact() {
        return kind == Kind.EXACT;
    }

    @Override
    public String toString() {
        return describe();
    }

    /// Escapes the characters that have a meaning in a regular expression, so text can be shown
    /// as one, e.g. `a.b` => `a\.b`.
    ///
    /// @param text The text.
    /// @return The escaped text.
    static String escape(final String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char chr = text.charAt(i);
            if (REGEX_SPECIALS.indexOf(chr) >= 0) {
                sb.append('\\');
            }
            sb.append(chr);
        }
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    private enum Kind {
        EXACT,
        EXACT_IGNORE_CASE,
        CONTAINING,
        CONTAINING_IGNORE_CASE,
        STARTING_WITH,
        ENDING_WITH,
        REGEX
    }
}
