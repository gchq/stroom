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

package stroom.dashboard.client.component;

import stroom.dictionary.shared.Word;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.NullSafe;

import java.util.Objects;
import java.util.Optional;

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
}
