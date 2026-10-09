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

package stroom.floormap.client.cluster;

import stroom.floormap.shared.TypeStyle;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/// Wording for a cluster: `"10 users"` rather than a bare `"10"`.
///
/// A count on its own leaves "ten of *what*?" to the reader, so the
/// caption always names the thing being counted. The noun is the entity's type,
/// which comes from the **data** and not from a fixed vocabulary —
/// so pluralising it is a guess, made here with a small irregular table plus the
/// usual English rules.
///
/// ## The already-plural problem
///
/// Nothing stops a document's types being named `"users"` rather than
/// `"user"`, and `"users" + "es"` is nonsense. A noun ending in a
/// single `s` (as opposed to `ss`) is therefore assumed to be plural
/// already and left alone. The cost is that a genuine singular like `"bus"`
/// stays `"bus"` — accepted, because plural type names are far likelier in
/// real data than singular ones ending in `s`, and both readings are
/// comprehensible. The durable fix is a user-set plural on [TypeStyle],
/// which is deliberately not in this iteration.
///
/// Holds no GWT or DOM types so it can be unit-tested on the JVM. Note that
/// `String.format` is unavailable under GWT, hence the concatenation.
public final class FloorMapClusterLabel {

    /// The noun used when the entities carry no type at all. Matches the
    /// tracking panel's union term — "objects" would collide with the
    /// `object` fact type and read as excluding people.
    private static final String NO_TYPE_SINGULAR = "entity";
    private static final String NO_TYPE_PLURAL = "entities";

    /// Plurals the rules below cannot derive. Kept deliberately short: only words
    /// plausible as a floor-map entity type.
    private static final Map<String, String> IRREGULAR = new HashMap<>();

    static {
        IRREGULAR.put("person", "people");
        IRREGULAR.put("man", "men");
        IRREGULAR.put("woman", "women");
        IRREGULAR.put("child", "children");
        IRREGULAR.put("mouse", "mice");
    }

    private FloorMapClusterLabel() {
        // Utility class.
    }

    /// The member names to list on hover, capped so a large cluster cannot grow a
    /// tooltip taller than the screen.
    ///
    /// When the list is cut short, the last entry says *how many* were
    /// left out rather than trailing off — a reader who can see "and 380 more"
    /// knows both that there is more and roughly how much. Nothing is hidden
    /// behind an opaque marker.
    ///
    /// An exact fit is never truncated: capping 21 names to 20 to add a line
    /// saying "and 1 more" would cost a name to say nothing.
    ///
    /// @param names    the member display names, in the order to show them
    /// @param maxNames the most names to list; below 1 is treated as 1
    /// @return the lines to render, ending in a summary line when capped
    public static List<String> hoverNames(final List<String> names, final int maxNames) {
        if (NullSafe.isEmptyCollection(names)) {
            return Collections.emptyList();
        }
        final int cap = Math.max(1, maxNames);
        // Cutting one name to say "and 1 more" is a strictly worse tooltip.
        if (names.size() <= cap + 1) {
            return Collections.unmodifiableList(new ArrayList<>(names));
        }
        final List<String> lines = new ArrayList<>(names.subList(0, cap));
        // Says where the rest are, not just that there are more: the click opens
        // the full list, and a reader who cannot see that has no way to find it.
        lines.add("…and " + (names.size() - cap) + " more — click to see all");
        return Collections.unmodifiableList(lines);
    }

    /// The caption for a cluster — the one entry point callers should use, since it
    /// picks the right wording for whether the cluster holds the focus.
    ///
    /// A cluster containing the tracked or selected entity is drawn *as*
    /// that entity, so its caption names them ("Alice + 9 others") rather than
    /// counting them anonymously ("10 users"). Without the name, a user following
    /// someone into a crowd could not tell whether the glyph was still theirs.
    ///
    /// Lives here rather than in the renderer because both the canvas caption and
    /// the hover tooltip need it, and they must not word the same cluster
    /// differently.
    ///
    /// @param cluster      the cluster to caption
    /// @param nameResolver resolves the focused member's id to its display name;
    ///         may be `null`, and may return `null`, in
    ///         which case the id is used
    /// @return the caption
    public static String captionFor(final FloorMapCluster cluster,
                                    final Function<String, String> nameResolver) {
        final String focusedId = cluster.getFocusedMemberId();
        if (focusedId == null) {
            return describe(cluster.size(), cluster.getType());
        }
        final String resolved = nameResolver != null
                ? nameResolver.apply(focusedId)
                : null;
        return describeWithFocus(cluster.size(), !NullSafe.isBlankString(resolved)
                ? resolved
                : focusedId);
    }

    /// Names the focused member and counts the rest, e.g.
    /// `"Alice + 9 others"`.
    ///
    /// Deliberately terser than [#describe]: the type is already carried by
    /// the glyph's own shape and colour, and this caption's job is to answer "is that
    /// still the person I am following?" at a glance. The count pill beside it still
    /// gives the total.
    ///
    /// @param total       how many entities the cluster holds, including the focused
    ///         one
    /// @param focusedName the focused member's display name
    /// @return the caption
    public static String describeWithFocus(final int total, final String focusedName) {
        final int others = total - 1;
        if (others <= 0) {
            // Not reachable for a real cluster (never fewer than two members), but
            // a caption reading "Alice + 0 others" would be worse than the name.
            return focusedName;
        }
        return others == 1
                ? focusedName + " + 1 other"
                : focusedName + " + " + others + " others";
    }

    /// Describes a count of entities of one type, e.g. `"10 users"` or
    /// `"1 person"`.
    ///
    /// @param count the number of entities
    /// @param type  the shared entity type; `null` or blank falls back to
    ///         the union term ("3 entities")
    /// @return the caption
    public static String describe(final int count, final String type) {
        return count + " " + noun(count, type);
    }

    /// The noun alone for a given count — the plural unless the count is exactly
    /// one.
    ///
    /// @param count the number of entities
    /// @param type  the shared entity type; `null` or blank falls back to
    ///         the union term
    /// @return the noun, without the count
    public static String noun(final int count, final String type) {
        if (NullSafe.isBlankString(type)) {
            return count == 1
                    ? NO_TYPE_SINGULAR
                    : NO_TYPE_PLURAL;
        }
        return count == 1
                ? type
                : plural(type);
    }

    /// Pluralises an entity type name.
    ///
    /// Order matters: irregulars first, then the already-plural assumption
    /// (see the class javadoc), then the `es` / `ies` / `s`
    /// rules.
    ///
    /// @param type the type name; `null` or blank returns the union term
    /// @return the plural form
    public static String plural(final String type) {
        if (NullSafe.isBlankString(type)) {
            return NO_TYPE_PLURAL;
        }
        final String lower = type.toLowerCase();

        final String irregular = IRREGULAR.get(lower);
        if (irregular != null) {
            return matchCase(type, irregular);
        }
        // Already an irregular plural — "people" must not become "peoples", and
        // the trailing-s rule below would not catch it. Checking the table's
        // values keeps both directions working as irregulars are added.
        if (IRREGULAR.containsValue(lower)) {
            return type;
        }

        // Already plural — "users" must not become "userses".
        if (lower.endsWith("s") && !lower.endsWith("ss")) {
            return type;
        }
        // Sibilants take "es": pass → passes, box → boxes, batch → batches.
        if (lower.endsWith("ss")
                || lower.endsWith("sh")
                || lower.endsWith("ch")
                || lower.endsWith("x")
                || lower.endsWith("z")) {
            return type + "es";
        }
        // Consonant + y takes "ies": trolley keeps its y, entity does not.
        if (lower.length() >= 2
                && lower.endsWith("y")
                && !isVowel(lower.charAt(lower.length() - 2))) {
            return type.substring(0, type.length() - 1) + "ies";
        }
        return type + "s";
    }

    /// Applies `original`'s leading capitalisation to a replacement word,
    /// so a type written `"Person"` pluralises to `"People"`.
    private static String matchCase(final String original, final String replacement) {
        if (!original.isEmpty()
                && Character.isUpperCase(original.charAt(0))
                && !replacement.isEmpty()) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return replacement;
    }

    private static boolean isVowel(final char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }
}
