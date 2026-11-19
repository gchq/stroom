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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class TestListMoves {

    private static final Predicate<String> ALL = item -> true;
    // Items in lower case are hidden
    private static final Predicate<String> SHOWN = item -> Character.isUpperCase(item.charAt(0));

    @Test
    void movesRight() {
        final List<String> items = list("A", "B", "C");
        assertThat(ListMoves.moveOnePlace(items, "A", false, ALL)).isTrue();
        assertThat(items).containsExactly("B", "A", "C");
    }

    @Test
    void movesLeft() {
        final List<String> items = list("A", "B", "C");
        assertThat(ListMoves.moveOnePlace(items, "C", true, ALL)).isTrue();
        assertThat(items).containsExactly("A", "C", "B");
    }

    @Test
    void movesPastHiddenItems() {
        final List<String> items = list("A", "x", "y", "B", "C");
        assertThat(ListMoves.moveOnePlace(items, "A", false, SHOWN)).isTrue();
        // Past B, the next shown; the hidden ones stay before B
        assertThat(items).containsExactly("x", "y", "B", "A", "C");

        final List<String> back = list("A", "x", "y", "B", "C");
        assertThat(ListMoves.moveOnePlace(back, "B", true, SHOWN)).isTrue();
        assertThat(back).containsExactly("B", "A", "x", "y", "C");
    }

    @Test
    void stopsAtTheEnds() {
        final List<String> items = list("A", "B", "x");
        assertThat(ListMoves.canMoveOnePlace(items, "A", true, SHOWN)).isFalse();
        assertThat(ListMoves.moveOnePlace(items, "A", true, SHOWN)).isFalse();
        // Only a hidden item is beyond B: it is the last shown
        assertThat(ListMoves.canMoveOnePlace(items, "B", false, SHOWN)).isFalse();
        assertThat(ListMoves.moveOnePlace(items, "B", false, SHOWN)).isFalse();
        assertThat(items).containsExactly("A", "B", "x");
        assertThat(ListMoves.canMoveOnePlace(items, "B", true, SHOWN)).isTrue();
    }

    @Test
    void anItemNotInTheListDoesNotMove() {
        final List<String> items = list("A", "B");
        assertThat(ListMoves.canMoveOnePlace(items, "Z", false, ALL)).isFalse();
        assertThat(ListMoves.moveOnePlace(items, "Z", false, ALL)).isFalse();
        assertThat(items).containsExactly("A", "B");
    }

    @Test
    void aSingleItemCannotMove() {
        final List<String> items = list("A");
        assertThat(ListMoves.moveOnePlace(items, "A", false, ALL)).isFalse();
        assertThat(ListMoves.moveOnePlace(items, "A", true, ALL)).isFalse();
    }

    private static List<String> list(final String... items) {
        return new ArrayList<>(List.of(items));
    }
}
