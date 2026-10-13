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

package stroom.gwt.workbench.framework.client.story;

import java.util.Locale;

/// Generates ids and names for stories using the same rules as React Storybook, so that a GWT
/// story has the same URL as the React story it corresponds to, e.g.
/// `Widgets/Buttons/Button` + `WithIcons` => `widgets-buttons-button--with-icons`.
///
/// The rules are ports of Storybook's regular expressions, written with explicit character
/// checks because GWT doesn't emulate `java.util.regex` and its `Character` methods only know
/// ASCII, so the JVM and the browser give the same results.
///
/// The rules are derived from Storybook, Copyright (c) 2024 Storybook, MIT licence (see
/// NOTICE.md).
public final class StoryIds {

    /// Characters that Storybook replaces with `-` when sanitising.
    private static final String SEPARATOR_CHARS = " ’–—―′¿'`~!@#$%^&*()_|+-=?;:\",.<>{}[]\\/";
    private static final String ID_SEPARATOR = "--";

    private StoryIds() {
        // Static utility
    }

    /// Converts a title or name into the form Storybook uses in ids, i.e. lower case with runs of
    /// punctuation/spaces replaced by a single `-` and no leading/trailing `-`. Other characters,
    /// including non-ASCII letters, are kept, as in Storybook.
    ///
    /// @param value The value to sanitise, e.g. `Editors & Viewers`.
    /// @return The sanitised value, e.g. `editors-viewers`, which is empty if the value is null
    /// or only has punctuation.
    public static String sanitise(final String value) {
        if (value == null) {
            return "";
        }
        final String lowerCase = value.toLowerCase(Locale.ROOT);
        final StringBuilder sb = new StringBuilder(lowerCase.length());
        boolean lastWasDash = true;
        for (int i = 0; i < lowerCase.length(); i++) {
            final char chr = lowerCase.charAt(i);
            if (SEPARATOR_CHARS.indexOf(chr) >= 0) {
                if (!lastWasDash) {
                    sb.append('-');
                    lastWasDash = true;
                }
            } else {
                sb.append(chr);
                lastWasDash = false;
            }
        }
        // Remove any trailing dash
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /// Creates the id of a story, as Storybook's `toId` does.
    ///
    /// @param title The title of the component the story belongs to, e.g. `Widgets/Buttons/Button`.
    /// @param name  The name of the story, e.g. `With Icons`. If null or empty the id is just the
    ///              sanitised title, as in Storybook.
    /// @return The story id, e.g. `widgets-buttons-button--with-icons`.
    /// @throws IllegalArgumentException If the title, or the name if given, sanitises to nothing,
    ///                                  e.g. `$`, as Storybook does.
    public static String storyId(final String title, final String name) {
        final String id = sanitiseSafe(title, "title");
        return name == null || name.isEmpty()
                ? id
                : id + ID_SEPARATOR + sanitiseSafe(name, "name");
    }

    private static String sanitiseSafe(final String value, final String part) {
        final String sanitised = sanitise(value);
        if (sanitised.isEmpty()) {
            throw new IllegalArgumentException("Invalid story " + part + " '" + value
                                               + "', it must include alphanumeric characters");
        }
        return sanitised;
    }

    /// Converts the name of a story's export into its display name in the same way as Storybook's
    /// `toStartCaseStr`, e.g. `WithIcons` => `With Icons`, `S3Config` => `S 3 Config`. `_`, `-`
    /// and `.` separate words; all other characters, including non-ASCII letters and symbols such
    /// as `$`, are kept.
    ///
    /// @param exportName The export name.
    /// @return The display name.
    public static String storyNameFromExport(final String exportName) {
        if (exportName == null || exportName.isEmpty()) {
            return "";
        }
        // str.replace(/_/g, " ").replace(/-/g, " ").replace(/\./g, " ")
        String text = exportName.replace('_', ' ').replace('-', ' ').replace('.', ' ');
        text = splitBeforeCapitalisedWord(text);
        text = splitPairs(text, StoryIds::isLower, StoryIds::isUpper);
        text = splitPairs(text, StoryIds::isLetter, StoryIds::isDigit);
        text = splitPairs(text, StoryIds::isDigit, StoryIds::isLetter);
        text = capitaliseWords(text);
        text = collapseSpaces(text);
        return trim(text);
    }

    /// `str.replace(/([^\n])([A-Z])([a-z])/g, "$1 $2$3")`, e.g. `XMLSchema` => `XML Schema`.
    private static String splitBeforeCapitalisedWord(final String text) {
        final StringBuilder sb = new StringBuilder(text.length() + 8);
        int i = 0;
        while (i < text.length()) {
            final char chr = text.charAt(i);
            if (i + 2 < text.length()
                && chr != '\n'
                && isUpper(text.charAt(i + 1))
                && isLower(text.charAt(i + 2))) {
                sb.append(chr).append(' ').append(text.charAt(i + 1)).append(text.charAt(i + 2));
                i += 3;
            } else {
                sb.append(chr);
                i++;
            }
        }
        return sb.toString();
    }

    /// `str.replace(/(first)(second)/g, "$1 $2")` where each group is a single character.
    private static String splitPairs(final String text, final CharTest first, final CharTest second) {
        final StringBuilder sb = new StringBuilder(text.length() + 8);
        int i = 0;
        while (i < text.length()) {
            final char chr = text.charAt(i);
            if (i + 1 < text.length() && first.test(chr) && second.test(text.charAt(i + 1))) {
                sb.append(chr).append(' ').append(text.charAt(i + 1));
                i += 2;
            } else {
                sb.append(chr);
                i++;
            }
        }
        return sb.toString();
    }

    /// `str.replace(/(\s|^)(\w)/g, (m, $1, $2) => $1 + $2.toUpperCase())`.
    private static String capitaliseWords(final String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            final char chr = text.charAt(i);
            if (i + 1 < text.length() && isJsWhitespace(chr) && isWordChar(text.charAt(i + 1))) {
                sb.append(chr).append(toUpper(text.charAt(i + 1)));
                i += 2;
            } else if (i == 0 && isWordChar(chr)) {
                sb.append(toUpper(chr));
                i++;
            } else {
                sb.append(chr);
                i++;
            }
        }
        return sb.toString();
    }

    /// `str.replace(/ +/g, " ")`.
    private static String collapseSpaces(final String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char chr = text.charAt(i);
            if (chr != ' ' || i == 0 || text.charAt(i - 1) != ' ') {
                sb.append(chr);
            }
        }
        return sb.toString();
    }

    /// JavaScript's `trim()`.
    private static String trim(final String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isJsWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && isJsWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isUpper(final char chr) {
        return chr >= 'A' && chr <= 'Z';
    }

    private static boolean isLower(final char chr) {
        return chr >= 'a' && chr <= 'z';
    }

    /// `[a-z]` with the `i` flag, which only matches ASCII letters.
    private static boolean isLetter(final char chr) {
        return isUpper(chr) || isLower(chr);
    }

    private static boolean isDigit(final char chr) {
        return chr >= '0' && chr <= '9';
    }

    /// JavaScript's `\w`.
    private static boolean isWordChar(final char chr) {
        return isLetter(chr) || isDigit(chr) || chr == '_';
    }

    private static char toUpper(final char chr) {
        return isLower(chr)
                ? (char) (chr - ('a' - 'A'))
                : chr;
    }

    /// JavaScript's `\s`.
    private static boolean isJsWhitespace(final char chr) {
        switch (chr) {
            case '\t':
            case '\n':
            case '\u000B':
            case '\f':
            case '\r':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case '　':
            case '﻿':
                return true;
            default:
                return chr >= ' ' && chr <= ' ';
        }
    }


    // --------------------------------------------------------------------------------


    /// A test of a single character, standing in for a regular expression character class.
    private interface CharTest {

        boolean test(char chr);
    }
}
