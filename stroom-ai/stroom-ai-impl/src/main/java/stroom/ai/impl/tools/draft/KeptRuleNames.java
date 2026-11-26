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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.Condition;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// The names a build's existing mapping gives its rules, kept when the build's translation is drafted again from the
/// catalogue (decided by the user, 2026-11-26): a rule new to the build is named as [CatalogueTranslation#ruleName]
/// says, but one the build already has keeps the name it has there, old style or new (`searchquery_2`), so its
/// template and documentation keep theirs too.
///
/// A rule is matched to an existing one by what it is for, never by its name: the same TypeId and the same
/// conditions (in any order); else, where the type's conditions changed, the same TypeId and the same place among
/// the type's rules, only where the type has as many rules left unmatched in each (nothing added or taken away).
/// The catch-all keeps the existing mapping's last rule's name where that rule has no conditions and no event type's
/// rule took it.
final class KeptRuleNames {

    private static final String TYPE_ID = "EventDetail/TypeId";

    private final List<String> names;
    private final String catchAll;

    private KeptRuleNames(final List<String> names, final String catchAll) {
        this.names = names;
        this.catchAll = catchAll;
    }

    /// @return Kept names for none of the rules (a build drafted for the first time).
    static KeptRuleNames none() {
        return new KeptRuleNames(List.of(), null);
    }

    /// Matches the rules wanted to the existing mapping's.
    ///
    /// @param existing The existing mapping's `events` rules, in its order; none for a build with no mapping yet.
    /// @param wanted   The rules the catalogue wants, in the order they are written.
    /// @return The kept names, by the wanted rules' places.
    static KeptRuleNames of(final List<Map<String, Object>> existing, final List<Wanted> wanted) {
        final List<Existing> rules = existingRules(existing);
        final List<String> out = new ArrayList<>();
        wanted.forEach(rule -> out.add(null));
        final boolean[] used = new boolean[rules.size()];
        // The same TypeId and the same conditions.
        for (int w = 0; w < wanted.size(); w++) {
            final Set<Condition> when = new HashSet<>(wanted.get(w).when());
            for (int e = 0; e < rules.size(); e++) {
                final Existing rule = rules.get(e);
                if (!used[e] && Objects.equals(rule.typeId(), wanted.get(w).typeId()) && when.equals(rule.when())) {
                    out.set(w, rule.name());
                    used[e] = true;
                    break;
                }
            }
        }
        // The type's conditions changed: the same place among the type's rules, where as many are left each side.
        final Map<String, List<Integer>> wantedByType = new LinkedHashMap<>();
        for (int w = 0; w < wanted.size(); w++) {
            wantedByType.computeIfAbsent(wanted.get(w).typeId(), key -> new ArrayList<>()).add(w);
        }
        wantedByType.forEach((typeId, places) -> {
            final List<Integer> existingOfType = new ArrayList<>();
            for (int e = 0; e < rules.size(); e++) {
                if (typeId.equals(rules.get(e).typeId())) {
                    existingOfType.add(e);
                }
            }
            if (typeId == null) {
                // A rule with no fixed TypeId is matched only by its conditions: by place it could take another's.
                return;
            }
            final long wantedLeft = places.stream().filter(w -> out.get(w) == null).count();
            final long existingLeft = existingOfType.stream().filter(e -> !used[e]).count();
            if (wantedLeft == 0 || wantedLeft != existingLeft) {
                return;
            }
            for (int p = 0; p < places.size() && p < existingOfType.size(); p++) {
                final int w = places.get(p);
                final int e = existingOfType.get(p);
                if (out.get(w) == null && !used[e]) {
                    out.set(w, rules.get(e).name());
                    used[e] = true;
                }
            }
        });
        String catchAll = null;
        if (!rules.isEmpty()) {
            final int last = rules.size() - 1;
            if (!used[last] && rules.get(last).conditionless()) {
                catchAll = rules.get(last).name();
            }
        }
        return new KeptRuleNames(out, catchAll);
    }

    /// @param place A wanted rule's place.
    /// @return The name the existing mapping gives that rule, or null where it is new to the build.
    String nameOf(final int place) {
        return place >= 0 && place < names.size()
                ? names.get(place)
                : null;
    }

    /// @return The existing mapping's catch-all's name, or null where it has none.
    String catchAll() {
        return catchAll;
    }

    /// @return Every name kept, the catch-all's too: none of them is given to a rule new to the build.
    Set<String> all() {
        final Set<String> out = new HashSet<>();
        names.stream().filter(Objects::nonNull).forEach(out::add);
        if (catchAll != null) {
            out.add(catchAll);
        }
        return out;
    }

    // The existing rules that write events (not those dropping their records) and have a name, each with the TypeId
    // it writes (null when read from a field) and its conditions.
    private static List<Existing> existingRules(final List<Map<String, Object>> existing) {
        final List<Existing> out = new ArrayList<>();
        for (final Map<String, Object> rule : NullSafe.list(existing)) {
            final String name = ToolText.str(rule.get("name"));
            if (JsonValues.truthy(rule.get("drop")) || NullSafe.isBlankString(name)) {
                continue;
            }
            final List<Condition> when = WayCheck.conditions(rule.get("when"));
            out.add(new Existing(name, typeIdOf(rule), when == null
                    ? null
                    : new HashSet<>(when), !JsonValues.truthy(rule.get("when"))));
        }
        return out;
    }

    private static String typeIdOf(final Map<String, Object> rule) {
        for (final Map<String, Object> field : JsonValues.maps(rule.get("fields"))) {
            if (TYPE_ID.equals(field.get("path")) && field.get("value") instanceof final String value) {
                return value;
            }
        }
        return null;
    }

    /// A rule the catalogue wants.
    ///
    /// @param typeId The TypeId it writes.
    /// @param when   Its conditions.
    record Wanted(String typeId, List<Condition> when) {

    }

    // An existing rule: its conditions null where they don't read; conditionless where it has none.
    private record Existing(String name, String typeId, Set<Condition> when, boolean conditionless) {

    }
}
