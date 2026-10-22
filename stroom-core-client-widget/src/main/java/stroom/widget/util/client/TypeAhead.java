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

package stroom.widget.util.client;

import com.google.gwt.core.client.Duration;

import java.util.Locale;
import java.util.function.IntFunction;

/// Finds the row that what is typed goes to, as typing in a WAI-ARIA list or menu does: the next row
/// whose label starts with what has been typed. Characters typed within half a second of each other
/// are taken together (e.g. "ex" for "Export"), and the same character typed again (e.g. "ee") goes on
/// to the next row starting with it. Matching ignores case, and wraps round to the first row.
public final class TypeAhead {

    // How long a pause in typing starts what is typed again
    static final int PAUSE_MILLIS = 500;

    private String typed = "";
    private double lastTypedMillis = -PAUSE_MILLIS;

    /// Takes a typed character and finds the row it goes to.
    ///
    /// @param text    The text typed, e.g. a character.
    /// @param current The row with the focus, or -1 for none.
    /// @param count   How many rows there are.
    /// @param labelOf Gives a row's label, or null for a row that can't be gone to (e.g. a separator).
    /// @return The row to go to, or -1 if none matches (the focus stays where it is).
    public int next(final String text, final int current, final int count, final IntFunction<String> labelOf) {
        return next(text, Duration.currentTimeMillis(), current, count, labelOf);
    }

    /// As [#next(String, int, int, IntFunction)], at a given time (for tests).
    ///
    /// @param text       The text typed, e.g. a character.
    /// @param nowMillis  The time it was typed, in milliseconds.
    /// @param current    The row with the focus, or -1 for none.
    /// @param count      How many rows there are.
    /// @param labelOf    Gives a row's label, or null for a row that can't be gone to.
    /// @return The row to go to, or -1 if none matches.
    int next(final String text,
             final double nowMillis,
             final int current,
             final int count,
             final IntFunction<String> labelOf) {
        typed = nowMillis - lastTypedMillis < PAUSE_MILLIS
                ? typed + text
                : text;
        lastTypedMillis = nowMillis;
        if (count <= 0) {
            return -1;
        }

        // One character (or the same one again) moves on from the row with the focus; more characters
        // keep to it while it still matches
        final boolean repeated = isOneCharacterRepeated(typed);
        final String prefix = (repeated
                ? typed.substring(0, 1)
                : typed).toLowerCase(Locale.ROOT);
        final int from = Math.max(current, 0);
        final int start = repeated && current >= 0
                ? from + 1
                : from;
        for (int i = 0; i < count; i++) {
            final int row = (start + i) % count;
            final String label = labelOf.apply(row);
            if (label != null && label.trim().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return row;
            }
        }
        return -1;
    }

    private static boolean isOneCharacterRepeated(final String text) {
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) != text.charAt(0)) {
                return false;
            }
        }
        return true;
    }
}
