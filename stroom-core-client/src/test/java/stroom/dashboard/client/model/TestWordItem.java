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

package stroom.dashboard.client.model;

import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestWordItem {

    private static final DocRef COLOURS = new DocRef("Dictionary", "d1", "Colours");
    private static final List<WordItem> VALUES = List.of(
            WordItem.simpleWord("Red"),
            WordItem.simpleWord("green"),
            WordItem.simpleWord("Dark Red"));

    /// A chooser's ticked values are found by equality, so two items of the same word from the
    /// same dictionary are equal.
    @Test
    void testEquals_sameWordAndSource() {
        final WordItem a = new WordItem("red", COLOURS);
        final WordItem b = new WordItem("red", COLOURS);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void testEquals_differentSource() {
        assertThat(new WordItem("red", COLOURS)).isNotEqualTo(new WordItem("red", null));
        assertThat(new WordItem("red", COLOURS)).isNotEqualTo(new WordItem("blue", COLOURS));
    }

    /// No filter matches every value.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void testMatching_noFilter(final String filter) {
        assertThat(WordItem.matching(VALUES, filter)).containsExactlyElementsOf(VALUES);
    }

    /// A filter matches part of a word, ignoring case and the filter's surrounding spaces, keeping
    /// the list's order.
    @Test
    void testMatching_containsIgnoringCase() {
        assertThat(WordItem.matching(VALUES, " RED "))
                .extracting(WordItem::getWord)
                .containsExactly("Red", "Dark Red");
    }

    @Test
    void testMatching_noneMatch() {
        assertThat(WordItem.matching(VALUES, "purple")).isEmpty();
    }

    @Test
    void testMatching_noValues() {
        assertThat(WordItem.matching(Collections.emptyList(), "red")).isEmpty();
        assertThat(WordItem.matching(null, "red")).isEmpty();
    }
}
