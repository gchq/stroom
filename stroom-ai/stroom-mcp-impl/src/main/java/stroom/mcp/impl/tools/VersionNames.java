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

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The names a new version of a promoted pipeline is proposed, from the version in the original's: the tools propose
/// them, the user confirms or edits them in copy_pipeline's form. Generic: a version token is a `v` (either case)
/// and numbers separated by dots, standing apart from the words around it (`Acme-Events-V1.0-Indexing`,
/// `acme-events-v1`, `Acme_v2`), never anything a product calls its versions.
final class VersionNames {

    /// What a name with no version token of its own is given for its new version: promotion files such a pipeline
    /// in V1.0 ([PromotionLayout#FIRST_VERSION]), so its next version is V1.1, as a name holding V1.0 would be.
    static final String NO_VERSION_SUFFIX = "-V1.1";
    /// What a name with no version token of its own is given for its next major version.
    static final String NO_MAJOR_VERSION_SUFFIX = "-V2.0";
    /// What an Elasticsearch index name with no version token is given (index names are lower case).
    static final String NO_INDEX_VERSION_SUFFIX = "-v2";

    /// The version a promoted aggregated feed's shared module with no version of its own is taken to be.
    static final String FIRST_MODULE_VERSION = "V1.0";

    // What separates a name's words.
    private static final String SEPARATORS = "-_. ";
    // A version token: v or V, then numbers separated by dots, not inside a word or a longer number.
    private static final Pattern TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9])([vV])(\\d+(?:\\.\\d+)*)(?![A-Za-z0-9]|\\.\\d)");

    // A shared module's version after its base name: a space, then a version token.
    private static final Pattern MODULE_VERSION = Pattern.compile(" ([vV])(\\d+(?:\\.\\d+)*)");
    // A shared module's version at the end of its name.
    private static final Pattern MODULE_VERSION_AT_END = Pattern.compile(" ([vV]\\d+(?:\\.\\d+)*)$");

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
    /// @return The next major version's name; with no version token, the name with [#NO_MAJOR_VERSION_SUFFIX] after
    /// it (before its first `{variable}`).
    static String nextMajor(final String name) {
        return bumped(name, true, NO_MAJOR_VERSION_SUFFIX);
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

    /// A shared module's version, from its name: the version token after its last space (`ACME common V1.1`), else
    /// [#FIRST_MODULE_VERSION] (the first version's name has none).
    ///
    /// @param name A module's name.
    /// @return Its version, e.g. `V1.1`.
    static String moduleVersion(final String name) {
        final Matcher matcher = MODULE_VERSION_AT_END.matcher(NullSafe.string(name));
        return matcher.find()
                ? matcher.group(1)
                : FIRST_MODULE_VERSION;
    }

    /// Whether a name is an aggregated feed's shared module's of that base name: the base itself (its first
    /// version), or the base and a version token after a space (`ACME common V1.1`). The base may hold version
    /// tokens of its own (a feed named `ACME-V2`): only what follows it is the module's version.
    ///
    /// @param base The module's name with no version, e.g. `ACME common`.
    /// @param name A name.
    /// @return True if it is a version of that module's name.
    static boolean isModuleName(final String base, final String name) {
        return moduleVersion(base, name).isPresent();
    }

    /// The name of a new version of an aggregated feed's shared module (its common module or helpers): the base
    /// name and the highest version of those given with its last part one higher. A name that is the base alone
    /// is [#FIRST_MODULE_VERSION], so the first new version of `ACME common` is `ACME common V1.1` (decided by the
    /// user, 2026-11-17: a promoted module changes only by a new version).
    ///
    /// @param base  The module's name with no version, e.g. `ACME common`.
    /// @param names The module's names known (the version changed, the feed's newest): those not the module's
    ///              are passed over.
    /// @return The new version's name, e.g. `ACME common V1.2` after `ACME common V1.1`.
    static String nextModule(final String base, final Collection<String> names) {
        List<Long> highest = parts(FIRST_MODULE_VERSION.substring(1));
        String letter = "V";
        for (final String name : names) {
            final Optional<Matcher> version = moduleVersion(base, name);
            if (version.isPresent()) {
                final List<Long> found = parts(version.get().group(2));
                if (compare(found, highest) > 0) {
                    highest = found;
                    letter = version.get().group(1);
                }
            }
        }
        final List<String> next = new ArrayList<>();
        for (int i = 0; i < highest.size(); i++) {
            next.add(String.valueOf(i == highest.size() - 1
                    ? highest.get(i) + 1
                    : highest.get(i)));
        }
        return base + " " + letter + String.join(".", next);
    }

    // The version after a module's base name: the base's own first version (a matcher of the first version), or
    // the token after it; empty for a name that isn't the module's.
    private static Optional<Matcher> moduleVersion(final String base, final String name) {
        if (base == null || name == null) {
            return Optional.empty();
        }
        if (name.equals(base)) {
            final Matcher first = MODULE_VERSION.matcher(" " + FIRST_MODULE_VERSION);
            return first.matches()
                    ? Optional.of(first)
                    : Optional.empty();
        }
        if (!name.startsWith(base)) {
            return Optional.empty();
        }
        final Matcher matcher = MODULE_VERSION.matcher(name.substring(base.length()));
        return matcher.matches()
                ? Optional.of(matcher)
                : Optional.empty();
    }

    // A version's numbers.
    private static List<Long> parts(final String numbers) {
        final List<Long> out = new ArrayList<>();
        for (final String part : numbers.split("\\.")) {
            out.add(Long.parseLong(part));
        }
        return out;
    }

    // Versions compared number by number, a missing number as 0.
    private static int compare(final List<Long> left, final List<Long> right) {
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            final long a = i < left.size()
                    ? left.get(i)
                    : 0;
            final long b = i < right.size()
                    ? right.get(i)
                    : 0;
            if (a != b) {
                return Long.compare(a, b);
            }
        }
        return 0;
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
    // part before it, the version goes there with the separator after it (`Acme-{feed}` to `Acme-V1.1-{feed}`); a name
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
