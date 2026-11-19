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

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

class TestTypeAhead {

    // A menu's items, with a separator (null, which can't be gone to)
    private static final List<String> ITEMS = Arrays.asList(
            "Open", "Save", "Save As…", null, "Export", "Edit", "export all", "Close");
    private static final IntFunction<String> LABELS = ITEMS::get;

    // Far enough apart that each character starts what is typed again
    private static final double APART = TypeAhead.PAUSE_MILLIS + 1;

    @Test
    void goesToTheNextItemStartingWithTheCharacter() {
        final TypeAhead typeAhead = new TypeAhead();

        assertThat(typeAhead.next("e", 0, 0, ITEMS.size(), LABELS)).isEqualTo(4);
    }

    @Test
    void ignoresCase() {
        final TypeAhead typeAhead = new TypeAhead();

        assertThat(typeAhead.next("S", 0, 0, ITEMS.size(), LABELS)).isEqualTo(1);
        assertThat(typeAhead.next("X", APART, 0, ITEMS.size(), LABELS)).isEqualTo(-1);
    }

    @Test
    void theSameCharacterAgainGoesOnToTheNextAndWrapsRound() {
        final TypeAhead typeAhead = new TypeAhead();

        // Pressed apart, from the item each one went to
        assertThat(typeAhead.next("e", 0, 0, ITEMS.size(), LABELS)).isEqualTo(4);
        assertThat(typeAhead.next("e", APART, 4, ITEMS.size(), LABELS)).isEqualTo(5);
        assertThat(typeAhead.next("e", 2 * APART, 5, ITEMS.size(), LABELS)).isEqualTo(6);
        assertThat(typeAhead.next("e", 3 * APART, 6, ITEMS.size(), LABELS)).isEqualTo(4);
    }

    @Test
    void theSameCharacterTypedQuicklyAlsoGoesOn() {
        final TypeAhead typeAhead = new TypeAhead();

        assertThat(typeAhead.next("e", 0, 0, ITEMS.size(), LABELS)).isEqualTo(4);
        // "ee" matches no item, so it is taken as "e" again
        assertThat(typeAhead.next("e", 100, 4, ITEMS.size(), LABELS)).isEqualTo(5);
    }

    @Test
    void charactersTypedTogetherMatchMoreOfTheLabel() {
        final TypeAhead typeAhead = new TypeAhead();

        assertThat(typeAhead.next("e", 0, 0, ITEMS.size(), LABELS)).isEqualTo(4);
        // "ed" keeps to the item with the focus while it still matches, else goes on
        assertThat(typeAhead.next("d", 100, 4, ITEMS.size(), LABELS)).isEqualTo(5);
    }

    @Test
    void keepsTheItemWithTheFocusWhileItStillMatches() {
        final TypeAhead typeAhead = new TypeAhead();

        assertThat(typeAhead.next("s", 0, 0, ITEMS.size(), LABELS)).isEqualTo(1);
        assertThat(typeAhead.next("a", 100, 1, ITEMS.size(), LABELS)).isEqualTo(1);
        assertThat(typeAhead.next("v", 200, 1, ITEMS.size(), LABELS)).isEqualTo(1);
        assertThat(typeAhead.next("e", 300, 1, ITEMS.size(), LABELS)).isEqualTo(1);
        assertThat(typeAhead.next(" ", 400, 1, ITEMS.size(), LABELS)).isEqualTo(2);
    }

    @Test
    void aPauseStartsAgain() {
        final TypeAhead typeAhead = new TypeAhead();

        assertThat(typeAhead.next("s", 0, 0, ITEMS.size(), LABELS)).isEqualTo(1);
        // After a pause "c" is a new start, not "sc"
        assertThat(typeAhead.next("c", APART, 1, ITEMS.size(), LABELS)).isEqualTo(7);
    }

    @Test
    void withNoFocusStartsAtTheFirstItem() {
        final TypeAhead typeAhead = new TypeAhead();

        assertThat(typeAhead.next("o", 0, -1, ITEMS.size(), LABELS)).isEqualTo(0);
    }

    @Test
    void skipsItemsThatCantBeGoneTo() {
        final TypeAhead typeAhead = new TypeAhead();
        final List<String> labels = Arrays.asList(null, "Alpha", null);

        assertThat(typeAhead.next("a", 0, -1, labels.size(), labels::get)).isEqualTo(1);
    }

    @Test
    void noMatchOrNoItemsLeavesTheFocus() {
        final TypeAhead typeAhead = new TypeAhead();

        assertThat(typeAhead.next("z", 0, 2, ITEMS.size(), LABELS)).isEqualTo(-1);
        assertThat(typeAhead.next("a", APART, -1, 0, LABELS)).isEqualTo(-1);
    }

    @Test
    void ignoresSpaceBeforeTheLabel() {
        final TypeAhead typeAhead = new TypeAhead();
        final List<String> labels = List.of(" One", "  Two");

        assertThat(typeAhead.next("t", 0, 0, labels.size(), labels::get)).isEqualTo(1);
    }
}
