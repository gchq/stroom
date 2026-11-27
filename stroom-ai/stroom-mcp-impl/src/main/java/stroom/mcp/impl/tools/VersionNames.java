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
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The names a new version of a promoted pipeline is proposed, from the version in the original's: the tools propose
/// them, the user confirms or edits them in copy_pipeline's form. Generic: a version token is a `v` (either case)
/// and numbers separated by dots, standing apart from the words around it (`Acme-Events-V1.0-Indexing`,
/// `acme-events-v1`, `Acme_v2`), never anything a product calls its versions. A number of more than
/// [#MAX_DIGITS] digits is no version's (a timestamp, say: `Acme-v20261117093000123456`).
///
/// A token inside a name the pipeline's name holds (its feed's, its source type group's: `CISCO-ASA-V9-EVENTS` of
/// feed `CISCO-ASA-V9`) is part of that name, not the pipeline's version (found in review, 2026-11-18: taken as V9,
/// the next version was V10, renaming the feed's part). The methods taking `held` names leave such tokens alone.
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

    /// The most digits a version token's number has.
    static final int MAX_DIGITS = 9;

    // What separates a name's words.
    private static final String SEPARATORS = "-_. ";
    // A version's numbers: each of at most MAX_DIGITS digits, separated by dots.
    private static final String NUMBERS = "\\d{1," + MAX_DIGITS + "}(?:\\.\\d{1," + MAX_DIGITS + "})*";
    // A version token: v or V, then its numbers, not inside a word or a longer number.
    private static final Pattern TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9])([vV])(" + NUMBERS + ")(?![A-Za-z0-9]|\\.\\d)");

    // A version token on its own: v or V, then its numbers.
    private static final Pattern VERSION_ALONE = Pattern.compile("([vV])(" + NUMBERS + ")");
    // Where a name with no version token would have one, marked to split it there.
    private static final char VERSION_MARK = '\u0000';

    // A shared module's version after its base name: a space, then a version token.
    private static final Pattern MODULE_VERSION = Pattern.compile(" ([vV])(" + NUMBERS + ")");
    // A shared module's version at the end of its name.
    private static final Pattern MODULE_VERSION_AT_END = Pattern.compile(" ([vV]" + NUMBERS + ")$");

    private VersionNames() {
        // Static methods only.
    }

    /// @param name A name, e.g. `Acme-Events-V1.0-Indexing`.
    /// @return Its last version token, e.g. `V1.0`; empty when it has none.
    static Optional<String> token(final String name) {
        return token(name, List.of());
    }

    /// @param name A pipeline's (or its document's) name, e.g. `CISCO-ASA-V9-EVENTS-V1.0`.
    /// @param held The names it may hold whose tokens are theirs, not its version: its feeds', its group's.
    /// @return Its last version token outside those, e.g. `V1.0`; empty when it has none.
    static Optional<String> token(final String name, final Collection<String> held) {
        return last(name, held).map(Matcher::group);
    }

    /// Two names' versions compared, number by number (`V1.10` after `V1.9`); a name with no version token before
    /// one with a token.
    ///
    /// @param left  A name, e.g. `ACME-V1.0-Events`.
    /// @param right Another.
    /// @param held  The names they may hold whose tokens are theirs, not their version: their feeds', say.
    /// @return Below zero when left's version is older, zero when the same (or neither has one), above when newer.
    static int compareVersions(final String left, final String right, final Collection<String> held) {
        final Optional<String> a = last(left, held).map(matcher -> matcher.group(2));
        final Optional<String> b = last(right, held).map(matcher -> matcher.group(2));
        if (a.isEmpty() || b.isEmpty()) {
            return Boolean.compare(a.isPresent(), b.isPresent());
        }
        return compare(parts(a.get()), parts(b.get()));
    }

    /// Whether a name is one for a newer version of another: the original's name with its version token (its last
    /// outside the names held) replaced by a newer one (`ACME-V1.0-Events` to `ACME-V1.1-Events` or
    /// `ACME-V2.0-Events`), or, where it has none, with `-V<n>` (either case) after it, before its first
    /// `{variable}` as [#next] puts it, newer than the [#FIRST_MODULE_VERSION] it is taken to be
    /// (`ACME-Events` to `ACME-Events-V1.1`). The version anywhere else (`ACME-V1.1-Events` or `ACME-Events V1.1`
    /// for `ACME-Events`) is no such name.
    ///
    /// @param original  The original's name, e.g. `ACME-Events`.
    /// @param candidate The name given for its new version.
    /// @param held      The names they may hold whose tokens are theirs, not their version: their feeds', say.
    /// @return True if the candidate names a newer version of the original, placed as the original's is.
    static boolean namesNewerVersion(final String original, final String candidate, final Collection<String> held) {
        if (original == null || candidate == null) {
            return false;
        }
        final Optional<Matcher> found = last(original, held);
        final String prefix;
        final String suffix;
        final List<Long> before;
        if (found.isPresent()) {
            prefix = original.substring(0, found.get().start());
            suffix = original.substring(found.get().end());
            before = parts(found.get().group(2));
        } else {
            // Where next would put a version, marked, and split there.
            final String marked = beforeVariables(original, "-" + VERSION_MARK);
            final int at = marked.indexOf(VERSION_MARK);
            prefix = marked.substring(0, at);
            suffix = marked.substring(at + 1);
            before = parts(FIRST_MODULE_VERSION.substring(1));
        }
        if (candidate.length() <= prefix.length() + suffix.length() || !candidate.startsWith(prefix)
            || !candidate.endsWith(suffix)) {
            return false;
        }
        final Matcher version = VERSION_ALONE.matcher(candidate.substring(prefix.length(),
                candidate.length() - suffix.length()));
        return version.matches() && compare(parts(version.group(2)), before) > 0;
    }

    /// The next version's name, its version token's last part one higher: `V1.0` to `V1.1`, `V1` to `V2`,
    /// `-v1.2` to `-v1.3`.
    ///
    /// @param name A name.
    /// @return The next version's name; with no version token, the name with [#NO_VERSION_SUFFIX] after it (before
    /// its first `{variable}`, if it has one).
    static String next(final String name) {
        return next(name, List.of());
    }

    /// As [#next(String)], a token inside a name held ([#token(String, Collection)]) left as it is.
    ///
    /// @param name A name.
    /// @param held The names it may hold whose tokens are theirs.
    /// @return The next version's name.
    static String next(final String name, final Collection<String> held) {
        return bumped(name, held, false, NO_VERSION_SUFFIX);
    }

    /// The next major version's name, its version token's first part one higher and the rest zero: `V1.0` to
    /// `V2.0`, `V1.2.3` to `V2.0.0`.
    ///
    /// @param name A name.
    /// @return The next major version's name; with no version token, the name with [#NO_MAJOR_VERSION_SUFFIX] after
    /// it (before its first `{variable}`).
    static String nextMajor(final String name) {
        return nextMajor(name, List.of());
    }

    /// As [#nextMajor(String)], a token inside a name held ([#token(String, Collection)]) left as it is.
    ///
    /// @param name A name.
    /// @param held The names it may hold whose tokens are theirs.
    /// @return The next major version's name.
    static String nextMajor(final String name, final Collection<String> held) {
        return bumped(name, held, true, NO_MAJOR_VERSION_SUFFIX);
    }

    /// The next version of an Elasticsearch index name: `acme-events-v1` to `acme-events-v2`, `acme_v3` to
    /// `acme_v4`.
    ///
    /// @param indexName An index name.
    /// @return The next version's index name; with no version token, the name with [#NO_INDEX_VERSION_SUFFIX] after
    /// it, before its first `{variable}` (`acme-events{_suffix}` to `acme-events-v2{_suffix}`).
    static String nextIndexName(final String indexName) {
        return nextIndexName(indexName, List.of());
    }

    /// As [#nextIndexName(String)], a token inside a name held (a feed's, any case) left as it is.
    ///
    /// @param indexName An index name.
    /// @param held      The names it may hold whose tokens are theirs.
    /// @return The next version's index name.
    static String nextIndexName(final String indexName, final Collection<String> held) {
        return bumped(indexName, held, false, NO_INDEX_VERSION_SUFFIX);
    }

    /// A document's name for the new version: the pipeline's version change where the name holds the same token,
    /// else its own token bumped ([#next]).
    ///
    /// @param name The document's name, e.g. `Acme-Events-V1.0-XSLT`.
    /// @param from The pipeline's version token, e.g. `V1.0`, or null.
    /// @param to   The new version's token, e.g. `V1.1`, or null.
    /// @return The name proposed.
    static String following(final String name, final String from, final String to) {
        return following(name, from, to, List.of());
    }

    /// As [#following(String, String, String)], a token inside a name held ([#token(String, Collection)]) never
    /// changed.
    ///
    /// @param name The document's name.
    /// @param from The pipeline's version token, or null.
    /// @param to   The new version's token, or null.
    /// @param held The names it may hold whose tokens are theirs.
    /// @return The name proposed.
    static String following(final String name, final String from, final String to, final Collection<String> held) {
        if (from != null && to != null && !from.equals(to)) {
            final Matcher matcher = TOKEN.matcher(name);
            final List<int[]> heldAt = heldRanges(name, held);
            final StringBuilder out = new StringBuilder();
            boolean replaced = false;
            while (matcher.find()) {
                if (matcher.group().equals(from) && !inside(heldAt, matcher.start(), matcher.end())) {
                    matcher.appendReplacement(out, Matcher.quoteReplacement(to));
                    replaced = true;
                }
            }
            matcher.appendTail(out);
            if (replaced) {
                return out.toString();
            }
        }
        return next(name, held);
    }

    /// A document's name for another version, where it is named for one version: its version token (its last
    /// outside the names held, as [#token(String, Collection)] finds it) where that is `from`, any case, replaced by
    /// `to`. Seen in chat 192: a group's XSLT saved under its pipeline's V1.0 name went with the pipeline's V1.1 into
    /// the V1.1 folder, still named V1.0.
    ///
    /// @param name A document's name, e.g. `Acme-V1.0-EVENTS Messaging V1.0`.
    /// @param from The version it is named for, e.g. `V1.0`.
    /// @param to   The version it is to be named for, e.g. `V1.1`.
    /// @param held The names it may hold whose tokens are theirs, never changed.
    /// @return The name for version `to` (`Acme-V1.0-EVENTS Messaging V1.1`); empty when its version token isn't
    /// `from`, or either version is missing or they are the same.
    static Optional<String> retokened(final String name,
                                      final String from,
                                      final String to,
                                      final Collection<String> held) {
        if (name == null || from == null || to == null || from.equalsIgnoreCase(to)) {
            return Optional.empty();
        }
        return last(name, held)
                .filter(matcher -> matcher.group().equalsIgnoreCase(from))
                .map(matcher -> name.substring(0, matcher.start()) + to + name.substring(matcher.end()));
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

    // The name with its last version token (outside the names held) bumped, or the suffix added when it has none.
    private static String bumped(final String name,
                                 final Collection<String> held,
                                 final boolean major,
                                 final String suffix) {
        final Optional<Matcher> found = last(name, held);
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

    // The last version token outside the names held.
    private static Optional<Matcher> last(final String name, final Collection<String> held) {
        if (name == null) {
            return Optional.empty();
        }
        final List<int[]> heldAt = heldRanges(name, held);
        final Matcher matcher = TOKEN.matcher(name);
        int start = -1;
        while (matcher.find()) {
            if (!inside(heldAt, matcher.start(), matcher.end())) {
                start = matcher.start();
            }
        }
        if (start < 0) {
            return Optional.empty();
        }
        final Matcher at = TOKEN.matcher(name);
        return at.find(start)
                ? Optional.of(at)
                : Optional.empty();
    }

    // Where each name held appears in the name, any case: [start, end) of each.
    private static List<int[]> heldRanges(final String name, final Collection<String> held) {
        final List<int[]> out = new ArrayList<>();
        final String lower = name.toLowerCase(Locale.ROOT);
        for (final String each : NullSafe.collection(held)) {
            if (NullSafe.isBlankString(each)) {
                continue;
            }
            final String sought = each.toLowerCase(Locale.ROOT);
            int at = lower.indexOf(sought);
            while (at >= 0) {
                out.add(new int[]{at, at + sought.length()});
                at = lower.indexOf(sought, at + 1);
            }
        }
        return out;
    }

    // Whether [start, end) is inside one of the ranges.
    private static boolean inside(final List<int[]> ranges, final int start, final int end) {
        return ranges.stream().anyMatch(range -> range[0] <= start && end <= range[1]);
    }
}
