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
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;

/// An extraction's regular expression read as the generated XSLT runs it: in XPath's dialect, compiled by Saxon's
/// own regex engine, as `analyze-string()` compiles it. The generator asks how many capture groups it has, or, if it
/// doesn't compile, why, and reports either as a problem. Checked in the dialect that runs (asked for by the user:
/// it was checked in another dialect, so `\p{L}`, which XPath matches as a letter, was refused).
///
/// XPath's capture groups are simple to count: every `(` that isn't escaped, isn't inside a character class, and
/// doesn't open a non-capturing `(?:` group, is one. XPath has no named groups and no lookaround.
final class XPathRegex {

    // Compiling a regex reads nothing from the configuration but its defaults, so one is shared.
    private static final Configuration CONFIGURATION = new Configuration();
    private static final String HOST_LANGUAGE = "XP30";

    private XPathRegex() {
        // Static only.
    }

    /// @param regex A regular expression.
    /// @return How many capture groups it has.
    /// @throws XPathRegexException if it doesn't compile as XPath compiles it, with Saxon's reason.
    static int groups(final String regex) throws XPathRegexException {
        try {
            new ARegularExpression(regex, "", HOST_LANGUAGE, new ArrayList<>(), CONFIGURATION);
        } catch (final XPathException e) {
            throw new XPathRegexException(e.getMessage());
        }
        return countGroups(regex);
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
    static final class XPathRegexException extends Exception {

        /// @param message Saxon's reason.
        XPathRegexException(final String message) {
            super(message);
        }
    }
}
