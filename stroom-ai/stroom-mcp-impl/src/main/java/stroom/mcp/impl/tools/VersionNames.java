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

package stroom.mcp.impl.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The names a new version of a promoted pipeline is proposed, from the version in the original's: the tools propose
/// them, the user confirms or edits them in copy_pipeline's form. Generic: a version token is a `v` (either case)
/// and numbers separated by dots, standing apart from the words around it (`Acme-Events-V1.0-Indexing`,
/// `acme-events-v1`, `Acme_v2`), never anything a product calls its versions.
final class VersionNames {

    /// What a name with no version token of its own is given for its new version.
    static final String NO_VERSION_SUFFIX = "-V2";
    /// What an Elasticsearch index name with no version token is given (index names are lower case).
    static final String NO_INDEX_VERSION_SUFFIX = "-v2";

    // What separates a name's words.
    private static final String SEPARATORS = "-_. ";
    // A version token: v or V, then numbers separated by dots, not inside a word or a longer number.
    private static final Pattern TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9])([vV])(\\d+(?:\\.\\d+)*)(?![A-Za-z0-9]|\\.\\d)");

    private VersionNames() {
        // Static methods only.
    }

    /// @param name A name, e.g. `Acme-Events-V1.0-Indexing`.
    /// @return Its last version token, e.g. `V1.0`; empty when it has none.
    static Optional<String> token(final String name) {
        return last(name).map(Matcher::group);
    }

    /// The next version's name, its version token's last part one higher: `V1.0` to `V1.1`, `V1` to `V2`,
    /// `-v1.2` to `-v1.3`.
    ///
    /// @param name A name.
    /// @return The next version's name; with no version token, the name with [#NO_VERSION_SUFFIX] after it (before
    /// its first `{variable}`, if it has one).
    static String next(final String name) {
        return bumped(name, false, NO_VERSION_SUFFIX);
    }

    /// The next major version's name, its version token's first part one higher and the rest zero: `V1.0` to
    /// `V2.0`, `V1.2.3` to `V2.0.0`.
    ///
    /// @param name A name.
    /// @return The next major version's name; with no version token, the name with [#NO_VERSION_SUFFIX] after it
    /// (before its first `{variable}`).
    static String nextMajor(final String name) {
        return bumped(name, true, NO_VERSION_SUFFIX);
    }

    /// The next version of an Elasticsearch index name: `acme-events-v1` to `acme-events-v2`, `acme_v3` to
    /// `acme_v4`.
    ///
    /// @param indexName An index name.
    /// @return The next version's index name; with no version token, the name with [#NO_INDEX_VERSION_SUFFIX] after
    /// it, before its first `{variable}` (`acme-events{_suffix}` to `acme-events-v2{_suffix}`).
    static String nextIndexName(final String indexName) {
        return bumped(indexName, false, NO_INDEX_VERSION_SUFFIX);
    }

    /// A document's name for the new version: the pipeline's version change where the name holds the same token,
    /// else its own token bumped ([#next]).
    ///
    /// @param name The document's name, e.g. `Acme-Events-V1.0-XSLT`.
    /// @param from The pipeline's version token, e.g. `V1.0`, or null.
    /// @param to   The new version's token, e.g. `V1.1`, or null.
    /// @return The name proposed.
    static String following(final String name, final String from, final String to) {
        if (from != null && to != null && !from.equals(to)) {
            final Matcher matcher = TOKEN.matcher(name);
            final StringBuilder out = new StringBuilder();
            boolean replaced = false;
            while (matcher.find()) {
                if (matcher.group().equals(from)) {
                    matcher.appendReplacement(out, Matcher.quoteReplacement(to));
                    replaced = true;
                }
            }
            matcher.appendTail(out);
            if (replaced) {
                return out.toString();
            }
        }
        return next(name);
    }

    // The name with its last version token bumped, or the suffix added when it has none.
    private static String bumped(final String name, final boolean major, final String suffix) {
        final Optional<Matcher> found = last(name);
        if (found.isEmpty()) {
            // Before the first variable, e.g. `acme-events{_suffix}` to `acme-events-v2{_suffix}`: what a variable
            // stands for is filled per document after the name's own part.
            return beforeVariables(name, suffix);
        }
        final Matcher matcher = found.get();
        final String[] parts = matcher.group(2).split("\\.");
        final List<String> changed = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            final long number = Long.parseLong(parts[i]);
            if (major) {
                changed.add(String.valueOf(i == 0
                        ? number + 1
                        : 0));
            } else {
                changed.add(String.valueOf(i == parts.length - 1
                        ? number + 1
                        : number));
            }
        }
        return name.substring(0, matcher.start()) + matcher.group(1) + String.join(".", changed)
               + name.substring(matcher.end());
    }

    // The suffix (a separator, then the version) added before the name's first variable: after a separator ending the
    // part before it, the version goes there with the separator after it (`Acme-{feed}` to `Acme-V2-{feed}`); a name
    // starting with a variable, or with none, has it at the end.
    private static String beforeVariables(final String name, final String suffix) {
        final int variable = name.indexOf('{');
        if (variable <= 0) {
            return name + suffix;
        }
        final String head = name.substring(0, variable);
        final char last = head.charAt(head.length() - 1);
        return SEPARATORS.indexOf(last) >= 0
                ? head + suffix.substring(1) + last + name.substring(variable)
                : head + suffix + name.substring(variable);
    }

    private static Optional<Matcher> last(final String name) {
        if (name == null) {
            return Optional.empty();
        }
        final Matcher matcher = TOKEN.matcher(name);
        int start = -1;
        while (matcher.find()) {
            start = matcher.start();
        }
        if (start < 0) {
            return Optional.empty();
        }
        final Matcher at = TOKEN.matcher(name);
        return at.find(start)
                ? Optional.of(at)
                : Optional.empty();
    }
}
