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

package stroom.dictionary.client.component;

import stroom.dictionary.shared.Word;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestEffectiveWordsList {

    // Each word's source is named after its uuid; "local" is the dictionary itself
    private static final Map<String, String> SOURCES = Map.of("a", "Alpha", "b", "Beta", "local", "Mine");
    private static final Function<Word, String> SOURCE_NAME = word -> SOURCES.get(word.getSourceUuid());
    private static final Function<Word, String> TYPE = word -> "local".equals(word.getSourceUuid())
            ? "Local"
            : "Imported";

    @BeforeEach
    void setUp() {
        // The list is a component, made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void wordsOfOneTypeAreOrderedBySourceThenWord() {
        // Before, a tie on the column sorted on was broken by that column again, so these stayed in
        // the order they came in
        final List<Word> words = List.of(
                new Word("zebra", "b"),
                new Word("apple", "b"),
                new Word("mango", "a"),
                new Word("kiwi", "local"));

        assertThat(sorted(words, "Types", false)).containsExactly(
                "mango Alpha", "apple Beta", "zebra Beta", "kiwi Mine");
    }

    @Test
    void wordsOfOneSourceAreOrderedByWord() {
        final List<Word> words = List.of(new Word("zebra", "a"), new Word("apple", "a"), new Word("kiwi", "b"));

        assertThat(sorted(words, "SourceName", false)).containsExactly("apple Alpha", "zebra Alpha", "kiwi Beta");
    }

    @Test
    void theSameWordIsOrderedBySourceAndDescendingReversesTheLot() {
        final List<Word> words = List.of(new Word("apple", "b"), new Word("apple", "a"), new Word("kiwi", "a"));

        assertThat(sorted(words, "Word", false)).containsExactly("apple Alpha", "apple Beta", "kiwi Alpha");
        assertThat(sorted(words, "Word", true)).containsExactly("kiwi Alpha", "apple Beta", "apple Alpha");
    }

    @Test
    void anUnsortedColumnHasNoOrder() {
        assertThat(EffectiveWordsList.comparator("Other", false, SOURCE_NAME, TYPE)).isNull();
    }

    private static List<String> sorted(final List<Word> words, final String sortId, final boolean desc) {
        final Comparator<Word> comparator = EffectiveWordsList.comparator(sortId, desc, SOURCE_NAME, TYPE);
        return words.stream()
                .sorted(comparator)
                .map(word -> word.getWord() + " " + SOURCE_NAME.apply(word))
                .toList();
    }
}
