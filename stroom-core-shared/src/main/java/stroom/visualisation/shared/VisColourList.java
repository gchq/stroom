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

package stroom.visualisation.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The colours of the values a dashboard's visualisations colour by, e.g. each host of a Host
/// field, so that a value has the same colour in every one of them. A value is a field's value: 200
/// of a Status field and 200 of a Count field are different values.
///
/// A value gets its preferred colour, from a hash of its field and value, if no other value has it;
/// otherwise the next colour no value has, the palette's own colours first, then their shades (see
/// [VisPalette#getColours(boolean)]). A value keeps its colour while the list lasts, whatever
/// values come after it. When every colour has been given out, a value gets its preferred colour,
/// shared. Values get the same colours in a new list too, in almost every case, as most get their
/// preferred colour.
///
/// A value's colour is kept as a position in the palette, so that a change of palette or theme
/// recolours every value without moving any.
///
/// The content pack's `src/shared/colour.js` gives colours in exactly the same way, for a
/// visualisation shown by an older Stroom, which has no list.
public class VisColourList {

    // Each value's position in the palette's colours, by its key (see key())
    private final Map<String, Integer> positions = new HashMap<>();
    private final Set<Integer> used = new HashSet<>();

    /// The colours of some values of a field, giving colours to those that have none.
    ///
    /// @param field     The field the values are of, e.g. `Host`.
    /// @param values    The values, e.g. `web-01`, `web-02`.
    /// @param palette   The user's palette.
    /// @param darkTheme Whether the dashboard has the dark theme.
    /// @return The colour of each value, `#rrggbb`, in the order given.
    public List<String> colours(final String field,
                                final List<String> values,
                                final VisPalette palette,
                                final boolean darkTheme) {
        final List<String> colours = palette.getColours(darkTheme);
        final int baseSize = palette.getBaseColours(darkTheme).size();
        final List<String> result = new ArrayList<>(values.size());
        for (final String value : values) {
            result.add(colours.get(position(key(field, value), baseSize, colours.size()) % colours.size()));
        }
        return result;
    }

    // The position of a value's colour, giving it one if it has none
    private int position(final String key, final int baseSize, final int size) {
        final Integer known = positions.get(key);
        if (known != null) {
            return known;
        }
        final int preferred = preferredPosition(key, baseSize);
        int position = preferred;
        if (used.contains(position)) {
            position = -1;
            for (int i = 0; i < size; i++) {
                if (!used.contains(i)) {
                    position = i;
                    break;
                }
            }
            if (position < 0) {
                // Every colour has been given out
                position = preferred;
            }
        }
        positions.put(key, position);
        used.add(position);
        return position;
    }

    /// @param field A field, e.g. `Host`.
    /// @param value One of its values, e.g. `web-01`.
    /// @return The key the list knows the value by.
    static String key(final String field, final String value) {
        return (field == null
                ? ""
                : field) + "\u0000" + (value == null
                ? ""
                : value);
    }

    /// @param key      A value's key.
    /// @param baseSize The number of the palette's own colours.
    /// @return The position of the colour the value prefers, one of the palette's own.
    static int preferredPosition(final String key, final int baseSize) {
        return (int) (VisColourMaths.hash(key) % baseSize);
    }
}
