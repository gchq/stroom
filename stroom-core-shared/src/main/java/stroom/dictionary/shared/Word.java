/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Comparator;
import java.util.List;
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
            Comparator.comparing(Word::getWord, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(Word::getSourceUuid));

    @JsonProperty
    private final String word;
    @JsonProperty
    private final String sourceUuid;
    @JsonProperty
    private final List<String> additionalSourceUuids;

    @JsonCreator
    public Word(@JsonProperty("word") final String word,
                @JsonProperty("sourceUuid") final String sourceUuid,
                @JsonProperty("additionalSourceUuids") final List<String> additionalSourceUuids) {
        // Trim all words
        this.word = Objects.requireNonNull(word).trim();
        this.additionalSourceUuids = NullSafe.hasItems(additionalSourceUuids)
                ? additionalSourceUuids
                : null;
        if (NullSafe.isBlankString(word)) {
            throw new IllegalArgumentException("Blank words not allowed");
        }
        this.sourceUuid = Objects.requireNonNull(sourceUuid);
    }

    public Word(final String word,
                final String sourceUuid) {
        this(word, sourceUuid, null);
    }

    public Word(final String word,
                final DocRef source) {
        this(word, Objects.requireNonNull(source).getUuid(), null);
    }

    public Word(final String word,
                final DocRef source,
                final List<String> additionalSourceUuids) {
        // Trim all words
        this.word = Objects.requireNonNull(word).trim();
        this.sourceUuid = Objects.requireNonNull(source).getUuid();
        this.additionalSourceUuids = additionalSourceUuids;
    }

    public String getWord() {
        return word;
    }

    /**
     * @return The primary source document for this word
     */
    public String getSourceUuid() {
        return sourceUuid;
    }

    /**
     * @return The list of other source documents that contain this word. This will only
     * contain items if the word list has been de-duplicated.
     */
    public List<String> getAdditionalSourceUuids() {
        return NullSafe.list(additionalSourceUuids);
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
        return Objects.equals(word, word1.word) && Objects.equals(sourceUuid,
                word1.sourceUuid) && Objects.equals(additionalSourceUuids, word1.additionalSourceUuids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(word, sourceUuid, additionalSourceUuids);
    }

    @Override
    public String toString() {
        return "Word{" +
                "word='" + word + '\'' +
                ", sourceUuid='" + sourceUuid + '\'' +
                ", additionalSourceUuids=" + additionalSourceUuids +
                '}';
    }
}
