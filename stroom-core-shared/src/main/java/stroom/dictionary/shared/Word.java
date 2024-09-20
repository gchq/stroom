/*
 * Copyright 2024 Crown Copyright
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

package stroom.dictionary.shared;

import stroom.docref.DocRef;
import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Comparator;
import java.util.Objects;

/**
 * Represents a word/line from a {@link DictionaryDoc} with the provenance of
 * where the word came from.
 * Note: in some cases a Word (aka a line) may be delimited, e.g. comma delimited to represent
 * a list of items.
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class Word {

    public static final Comparator<Word> CASE_INSENSE_WORD_COMPARATOR = Comparator.nullsFirst(
            Comparator.comparing(Word::getWord, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Word::getSourceUuid));

    @JsonProperty
    private final String word;
    @JsonProperty
    private final String sourceUuid;

    @JsonCreator
    public Word(@JsonProperty("word") final String word,
                @JsonProperty("sourceUuid") final String sourceUuid) {
        // Trim all words
        this.word = Objects.requireNonNull(word).trim();
        if (GwtNullSafe.isBlankString(word)) {
            throw new IllegalArgumentException("Blank words not allowed");
        }
        this.sourceUuid = Objects.requireNonNull(sourceUuid);
    }

    public Word(final String word,
                final DocRef source) {
        // Trim all words
        this.word = Objects.requireNonNull(word).trim();
        this.sourceUuid = Objects.requireNonNull(source).getUuid();
    }

    public String getWord() {
        return word;
    }

    public String getSourceUuid() {
        return sourceUuid;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final Word word1 = (Word) object;
        return Objects.equals(word, word1.word) && Objects.equals(sourceUuid, word1.sourceUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(word, sourceUuid);
    }

    @Override
    public String toString() {
        return "Word{" +
                "word='" + word + '\'' +
                ", sourceUuid='" + sourceUuid + '\'' +
                '}';
    }
}
