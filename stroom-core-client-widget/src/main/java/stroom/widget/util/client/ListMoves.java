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

import java.util.List;
import java.util.function.Predicate;

/// Moves an item of a list one place among the items that count, e.g. a table's column among the
/// columns shown, skipping the hidden ones: the keyboard's way to do what dragging a column's header
/// one place does.
public final class ListMoves {

    private ListMoves() {
        // Static utility
    }

    /// @param items        The items, in order.
    /// @param item         The item to move.
    /// @param towardsStart Whether it would move towards the start (left), else the end (right).
    /// @param counts       Whether an item counts, e.g. is shown.
    /// @return Whether it can move: an item that counts is beyond it in that direction.
    public static <T> boolean canMoveOnePlace(final List<T> items,
                                              final T item,
                                              final boolean towardsStart,
                                              final Predicate<T> counts) {
        return target(items, item, towardsStart, counts) >= 0;
    }

    /// Moves an item past the next item that counts in a direction, so it takes that item's place
    /// among the items that count. Items that don't count between them stay where they are relative
    /// to the others.
    ///
    /// @param items        The items, in order, changed in place.
    /// @param item         The item to move.
    /// @param towardsStart Whether to move it towards the start (left), else the end (right).
    /// @param counts       Whether an item counts, e.g. is shown.
    /// @return Whether it moved: false if it isn't in the list or nothing that counts is beyond it.
    public static <T> boolean moveOnePlace(final List<T> items,
                                           final T item,
                                           final boolean towardsStart,
                                           final Predicate<T> counts) {
        final int target = target(items, item, towardsStart, counts);
        if (target < 0) {
            return false;
        }
        // Towards the end, the target shifts down one as the item is taken out, so the item goes in
        // after it; towards the start, before it
        items.remove(item);
        items.add(target, item);
        return true;
    }

    // The index of the next item that counts beyond the item, or -1 if none does
    private static <T> int target(final List<T> items,
                                  final T item,
                                  final boolean towardsStart,
                                  final Predicate<T> counts) {
        final int index = items.indexOf(item);
        if (index < 0) {
            return -1;
        }
        final int step = towardsStart
                ? -1
                : 1;
        for (int i = index + step; i >= 0 && i < items.size(); i += step) {
            if (counts.test(items.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
