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

package stroom.ai.impl.tools.catalogue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// The catalogue's order: more specific first (more conditions before fewer), stable otherwise, the catch-all last
/// (it isn't an event type, so it follows them all). Records seen only in processing that more than one way could
/// recognise go to the first.
public final class CatalogueOrder {

    private CatalogueOrder() {
        // Static only.
    }

    /// @param types Event types.
    /// @return Them ordered more specific first: by their most specific way of being recognised (most conditions),
    /// an event type recognised by every record last; ties kept in the order given.
    public static List<EventType> ordered(final List<EventType> types) {
        final List<EventType> out = new ArrayList<>(types);
        out.sort(Comparator.comparingInt(CatalogueOrder::mostConditions).reversed());
        return out;
    }

    /// @param catalogue A catalogue.
    /// @return Every way of recognising its event types, more specific first (more conditions before fewer), ties
    /// kept in the catalogue's order and each event type's: the rules' order, one a way, before the catch-all's.
    public static List<WayOf> ways(final Catalogue catalogue) {
        final List<WayOf> out = new ArrayList<>();
        for (final EventType type : catalogue.eventTypes()) {
            for (int n = 0; n < type.recognisedBy().size(); n++) {
                out.add(new WayOf(type, n, type.recognisedBy().get(n)));
            }
        }
        out.sort(Comparator.comparingInt((WayOf way) -> way.way().when().size()).reversed());
        return out;
    }

    // The most conditions of any of its ways; 0 for one recognised by every record.
    private static int mostConditions(final EventType type) {
        return type.recognisedBy().stream().mapToInt(way -> way.when().size()).max().orElse(0);
    }


    // --------------------------------------------------------------------------------


    /// One way of recognising an event type.
    ///
    /// @param type  The event type.
    /// @param index Which of its ways (from 0).
    /// @param way   The way.
    public record WayOf(EventType type, int index, Way way) {

    }
}
