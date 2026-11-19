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

import net.sf.saxon.Configuration;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/// An extraction's regular expression read as the generated XSLT runs it: in XPath's dialect, compiled by Saxon's
/// own regex engine, as `analyze-string()` compiles it. The generator asks how many capture groups it has, or, if it
/// doesn't compile, why, and reports either as a problem. Checked in the dialect that runs (asked for by the user:
/// it was checked in another dialect, so `\p{L}`, which XPath matches as a letter, was refused).
///
/// XPath's capture groups are simple to count: every `(` that isn't escaped, isn't inside a character class, and
/// doesn't open a non-capturing `(?:` group, is one. XPath has no named groups and no lookaround.
public final class XPathRegex {

    // Compiling a regex reads nothing from the configuration but its defaults, so one is shared.
    private static final Configuration CONFIGURATION = new Configuration();
    private static final String HOST_LANGUAGE = "XP30";
    // Named groups and lookaround, which XPath has not.
    private static final Pattern LOOKAROUND = Pattern.compile(
            "\\(\\?(P?<[A-Za-z_]|<?[=!])");

    // The regexes finds() has compiled, kept as a sample's records are tested one by one.
    private static final int CACHED = 512;
    private static final Map<String, Compiled> COMPILED = new ConcurrentHashMap<>();

    private XPathRegex() {
        // Static only.
    }

    /// @param regex A regular expression.
    /// @return Why the generated XSLT can't run it (named groups or lookaround, which XPath has not, or Saxon's
    /// reason it doesn't compile); empty when it can.
    public static Optional<String> problem(final String regex) {
        if (LOOKAROUND.matcher(regex).find()) {
            return Optional.of("XPath regular expressions have no named groups and no lookaround; use "
                                         + "plain groups, (?:...) for ones that are not fields, and anchors");
        }
        try {
            groups(regex);
            return Optional.empty();
        } catch (final XPathRegexException e) {
            return Optional.of("it does not compile as XPath compiles it: " + e.getMessage());
        }
    }

    /// @param regex A regular expression.
    /// @return How many capture groups it has.
    /// @throws XPathRegexException if it doesn't compile as XPath compiles it, with Saxon's reason.
    public static int groups(final String regex) throws XPathRegexException {
        try {
            new ARegularExpression(regex, "", HOST_LANGUAGE, new ArrayList<>(), CONFIGURATION);
        } catch (final XPathException e) {
            throw new XPathRegexException(e.getMessage());
        }
        return countGroups(regex);
    }

    /// @param regex A regular expression, compiled as XPath's `matches()` compiles it.
    /// @param value A value.
    /// @return True if the regex finds a match in it, as `matches($value, $regex)` does (anywhere in it, unless
    /// anchored).
    /// @throws XPathRegexException if it doesn't compile as XPath compiles it, with Saxon's reason.
    public static boolean finds(final String regex, final String value) throws XPathRegexException {
        if (COMPILED.size() > CACHED) {
            COMPILED.clear();
        }
        final Compiled compiled = COMPILED.computeIfAbsent(regex, XPathRegex::compiled);
        if (compiled.regex() == null) {
            throw new XPathRegexException(compiled.error());
        }
        return compiled.regex().containsMatch(value);
    }

    /// As [#finds(String, String)], stopped once a deadline passes: Saxon's engine backtracks, and one regex over a
    /// value can take far longer than any check may (the event type catalogue's regex guard times each against the
    /// sample).
    ///
    /// @param regex    A regular expression, compiled as XPath's `matches()` compiles it.
    /// @param value    A value.
    /// @param deadline When to stop, as [System#nanoTime()] counts.
    /// @return True if the regex finds a match in it.
    /// @throws XPathRegexException if it doesn't compile as XPath compiles it, with Saxon's reason.
    /// @throws TimedOut            if the deadline passes before it is known.
    public static boolean findsWithin(final String regex, final String value, final long deadline)
            throws XPathRegexException {
        if (COMPILED.size() > CACHED) {
            COMPILED.clear();
        }
        final Compiled compiled = COMPILED.computeIfAbsent(regex, XPathRegex::compiled);
        if (compiled.regex() == null) {
            throw new XPathRegexException(compiled.error());
        }
        return compiled.regex().containsMatch(new DeadlineString(UnicodeString.makeUnicodeString(value), deadline));
    }

    private static Compiled compiled(final String regex) {
        try {
            return new Compiled(new ARegularExpression(regex, "", HOST_LANGUAGE, new ArrayList<>(), CONFIGURATION),
                    null);
        } catch (final XPathException e) {
            return new Compiled(null, e.getMessage());
        }
    }

    // The capture groups of a regex Saxon compiled: `(` outside a character class, unescaped, and not `(?:`.
    private static int countGroups(final String regex) {
        int groups = 0;
        int classDepth = 0;
        for (int i = 0; i < regex.length(); i++) {
            final char c = regex.charAt(i);
            if (c == '\\') {
                // The escaped character is skipped, whatever it is.
                i++;
            } else if (c == '[') {
                classDepth++;
            } else if (c == ']' && classDepth > 0) {
                classDepth--;
            } else if (c == '(' && classDepth == 0 && !regex.startsWith("?:", i + 1)) {
                groups++;
            }
        }
        return groups;
    }


    // --------------------------------------------------------------------------------


    /// A regular expression that doesn't compile in XPath's dialect.
    public static final class XPathRegexException extends Exception {

        /// @param message Saxon's reason.
        XPathRegexException(final String message) {
            super(message);
        }
    }


    // --------------------------------------------------------------------------------


    // A regex compiled, or why it doesn't compile.
    private record Compiled(ARegularExpression regex, String error) {

    }


    // --------------------------------------------------------------------------------


    /// A regex's matching stopped at its deadline ([#findsWithin(String, String, long)]).
    public static final class TimedOut extends RuntimeException {

        TimedOut() {
            super("the regular expression ran past its time", null, false, false);
        }
    }


    // --------------------------------------------------------------------------------


    // A value Saxon's matcher reads character by character, the deadline checked as it reads: Saxon takes a
    // UnicodeString as it is, so its backtracking is stopped where a thread can't be.
    private static final class DeadlineString extends UnicodeString {

        // How many characters are read between looks at the clock.
        private static final int READS_A_LOOK = 4096;

        private final UnicodeString text;
        private final long deadline;
        private int reads;

        private DeadlineString(final UnicodeString text, final long deadline) {
            this.text = text;
            this.deadline = deadline;
        }

        private void look() {
            if (++reads >= READS_A_LOOK) {
                reads = 0;
                if (System.nanoTime() - deadline > 0) {
                    throw new TimedOut();
                }
            }
        }

        @Override
        public UnicodeString uSubstring(final int start, final int end) {
            return text.uSubstring(start, end);
        }

        @Override
        public int uIndexOf(final int search, final int start) {
            look();
            return text.uIndexOf(search, start);
        }

        @Override
        public int uCharAt(final int pos) {
            look();
            return text.uCharAt(pos);
        }

        @Override
        public int uLength() {
            return text.uLength();
        }

        @Override
        public boolean isEnd(final int pos) {
            look();
            return text.isEnd(pos);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public char charAt(final int index) {
            look();
            return text.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return text.subSequence(start, end);
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
