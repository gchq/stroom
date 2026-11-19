/*
 * Copyright 2022 Crown Copyright
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

import stroom.dictionary.shared.Word;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/// A value a List Input offers: a word, and the dictionary it came from, if any.
public class WordItem implements HasDisplayValue {

    /// No value.
    public static final WordItem EMPTY = new WordItem("", null);

    private final String word;
    private final DocRef sourceDocRef;

    /// @param word         The word.
    /// @param sourceDocRef The dictionary it came from, or `null` if it was typed in the settings.
    public WordItem(final String word, final DocRef sourceDocRef) {
        this.word = word;
        this.sourceDocRef = sourceDocRef;
    }

    /// @param word     A word of a dictionary's word list.
    /// @param wordList The word list, which says which dictionary the word came from.
    public WordItem(final Word word, final WordList wordList) {
        this.word = Objects.requireNonNull(word).getWord();
        this.sourceDocRef = wordList.getSource(word).orElseThrow();
    }

    /// A value typed in the settings, not from a dictionary.
    ///
    /// @param word The word.
    /// @return The value, or [#EMPTY] for a blank word.
    public static WordItem simpleWord(final String word) {
        if (NullSafe.isNonBlankString(word)) {
            return new WordItem(word, null);
        } else {
            return EMPTY;
        }
    }

    /// A value from a dictionary's word list.
    ///
    /// @param word     The word.
    /// @param wordList The word list.
    /// @return The value, or [#EMPTY] for a blank word or one not in the list.
    public static WordItem sourcedWord(final String word, final WordList wordList) {
        if (NullSafe.isNonBlankString(word)) {
            return wordList.getWord(word)
                    .map(wordObj -> {
                        final DocRef sourceDocRef = wordList.getSource(wordObj).orElse(null);
                        return new WordItem(word, sourceDocRef);
                    })
                    .orElse(EMPTY);
        } else {
            return EMPTY;
        }
    }

    /// The values whose words contain a filter's text, ignoring case, e.g. for a chooser's quick
    /// filter.
    ///
    /// @param values The values, in the list's order.
    /// @param filter The filter's text, or null or blank for all.
    /// @return The values that match, in the same order.
    public static List<WordItem> matching(final List<WordItem> values, final String filter) {
        if (NullSafe.isBlankString(filter)) {
            return values;
        }
        final String lowerFilter = filter.trim().toLowerCase(Locale.ROOT);
        return NullSafe.stream(values)
                .filter(value -> NullSafe.string(value.getWord()).toLowerCase(Locale.ROOT).contains(lowerFilter))
                .collect(Collectors.toList());
    }

    /// @return The word.
    public String getWord() {
        return word;
    }

    /// @return The dictionary the word came from, if any.
    public Optional<DocRef> getSourceDocRef() {
        return Optional.ofNullable(sourceDocRef);
    }

    @Override
    public String getDisplayValue() {
        return word;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WordItem that = (WordItem) o;
        return Objects.equals(word, that.word) &&
               Objects.equals(sourceDocRef, that.sourceDocRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(word, sourceDocRef);
    }
}
