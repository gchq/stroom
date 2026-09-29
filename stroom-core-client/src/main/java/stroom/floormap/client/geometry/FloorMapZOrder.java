/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.geometry;

import stroom.floormap.client.model.Fact;
import stroom.floormap.shared.TypeStyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Applies the per-type paint order (see [TypeStyle] and the redesign §6
/// "Type settings") to a list of facts.
///
/// Facts paint in the order their type appears in the configured
/// `typeStyles` list — earlier types behind later ones. A type *not*
/// in the configured order (e.g. one seen in the data but not yet discovered)
/// sorts **last**, so it paints on top and stays visible. Within a
/// single type, the original list order is preserved (a stable sort), which
/// carries the parser's entry order.
///
/// Pure logic with no GWT dependencies, so it can be unit-tested directly.
public final class FloorMapZOrder {

    private FloorMapZOrder() {
        // Utility class
    }

    /// The index of `type` within the configured order, or
    /// [Integer#MAX_VALUE] if it is not configured (so it paints on top).
    public static int indexOf(final String type, final List<TypeStyle> order) {
        if (order != null && type != null) {
            for (int i = 0; i < order.size(); i++) {
                final TypeStyle style = order.get(i);
                if (style != null && type.equals(style.getType())) {
                    return i;
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    /// Returns a new list of the given facts, stably ordered back-to-front by
    /// their type's position in `order`; unconfigured types come last.
    ///
    /// @param facts the facts to order (unchanged; a new list is returned)
    /// @param order the configured type styles, or `null`/empty (then the
    ///         input order is preserved)
    /// @return a new, paint-ordered list; never `null`
    public static List<Fact> sort(final List<Fact> facts, final List<TypeStyle> order) {
        final List<Fact> result = new ArrayList<>();
        if (facts != null) {
            result.addAll(facts);
        }
        // Resolve type -> index ONCE. Comparator.comparingInt re-invokes its key
        // extractor on every comparison, so scanning `order` inside the comparator
        // made this O(n log n * types) rather than O(n log n + types) — and this
        // runs on every redraw, including every animation frame.
        final Map<String, Integer> indexByType = indexByType(order);
        // List.sort is stable, so facts of the same type keep their input order.
        result.sort(Comparator.comparingInt(fact -> {
            final Integer index = fact.getType() == null
                    ? null
                    : indexByType.get(fact.getType());
            return index != null
                    ? index
                    : Integer.MAX_VALUE;
        }));
        return result;
    }

    /// Maps each configured type to its position in `order`.
    ///
    /// Uses the *first* occurrence of a duplicated type, matching
    /// [#indexOf].
    private static Map<String, Integer> indexByType(final List<TypeStyle> order) {
        final Map<String, Integer> indexByType = new HashMap<>();
        if (order != null) {
            for (int i = 0; i < order.size(); i++) {
                final TypeStyle style = order.get(i);
                if (style != null && style.getType() != null) {
                    indexByType.putIfAbsent(style.getType(), i);
                }
            }
        }
        return indexByType;
    }
}
