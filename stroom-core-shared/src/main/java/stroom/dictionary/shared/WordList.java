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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A list of {@link Word} objects
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class WordList {

    /**
     * Immutable empty wordlist
     */
    public static final WordList EMPTY = new WordList(Collections.emptyList(), Collections.emptyMap());

    @JsonProperty
    private final List<Word> wordList;
    @JsonProperty
    private final Map<String, DocRef> sourceUuidToDocRefMap;

    @JsonCreator
    WordList(@JsonProperty("wordList") final List<Word> wordList,
             @JsonProperty("sourceUuidToDocRefMap") final Map<String, DocRef> sourceUuidToDocRefMap) {
        this.wordList = wordList;
        this.sourceUuidToDocRefMap = sourceUuidToDocRefMap;
    }

    public List<Word> getWordList() {
        return wordList;
    }

    Map<String, DocRef> getSourceUuidToDocRefMap() {
        return sourceUuidToDocRefMap;
    }

    @JsonIgnore
    public Set<DocRef> getSources() {
        return Collections.unmodifiableSet(new HashSet<>(sourceUuidToDocRefMap.values()));
    }

    public Optional<DocRef> getSource(final Word word) {
        return Optional.ofNullable(sourceUuidToDocRefMap.get(Objects.requireNonNull(word).getSourceUuid()));
    }

    public Optional<DocRef> getSource(final String sourceUuid) {
        return Optional.ofNullable(sourceUuidToDocRefMap.get(Objects.requireNonNull(sourceUuid)));
    }

    /**
     * @return The word list sorted by word (case-insensitive), then by source UUID (if duplicates are allowed).
     */
    @JsonIgnore
    public List<Word> getSortedList() {
        return wordList.stream()
                .sorted(Word.CASE_INSENSE_WORD_COMPARATOR)
                .collect(Collectors.toList());
    }

    public Optional<Word> getWord(final String word) {
        if (NullSafe.isNonBlankString(word)) {
            return wordList.stream()
                    .filter(wordObj -> Objects.equals(wordObj.getWord(), word))
                    .findFirst();
        } else {
            return Optional.empty();
        }
    }

    @JsonIgnore
    public boolean isEmpty() {
        return wordList.isEmpty();
    }

    public int size() {
        return wordList.size();
    }

    public int sourceCount() {
        return sourceUuidToDocRefMap.size();
    }

    /**
     * @return The complete wordList as a single string with words delimited by {@code \n}.
     * The last item may not have a trailing {@code \n}.
     */
    public String asString() {
        if (wordList.isEmpty()) {
            return "";
        } else {
            return wordList.stream()
                    .map(Word::getWord)
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * @return The complete word list as a simple array or words.
     */
    public String[] asWordArray() {
        if (wordList.isEmpty()) {
            return new String[0];
        } else {
            return wordList.stream()
                    .map(Word::getWord)
                    .toArray(String[]::new);
        }
    }

    @Override
    public String toString() {
        return "WordList{" +
                "wordList=" + wordList +
                ", sourceUuidToDocRefMap=" + sourceUuidToDocRefMap +
                '}';
    }

    public static Builder builder(final boolean deDup) {
        return new Builder(deDup);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private final List<SourcedWord> wordList = new ArrayList<>();
        private final Set<String> wordSet = new HashSet<>();
        private final Map<String, DocRef> sourceUuidToDocRefMap = new HashMap<>();
        private final Map<String, List<String>> wordToSourceUuidsMap = new HashMap<>();
        private final boolean deDup;

        private Builder(final boolean deDup) {
            this.deDup = deDup;
        }

        public Builder addWord(final String word, final DocRef source) {
            // Silently ignore blank/null words
            if (NullSafe.isNonBlankString(word)) {
                final String sourceUuid = Objects.requireNonNull(
                        source,
                        "Source DocRef required for word").getUuid();
                boolean doAdd = true;
                // Assumes that words are added in precedence order, i.e. most important source first
                if (deDup) {
                    if (wordSet.contains(word)) {
                        doAdd = false;
                    }
                }
                if (doAdd) {
                    wordList.add(new SourcedWord(word, sourceUuid));
//                    sourceList.add(sourceUuid);
                    wordSet.add(word);
                }

                // If not de-duping, then each word only has one source so we don't need this list
                final List<String> sourceUuids = wordToSourceUuidsMap.computeIfAbsent(
                        word, k -> new ArrayList<>());
                if (!sourceUuids.contains(sourceUuid)) {
                    sourceUuids.add(sourceUuid);
                }

                // Our reference lookup of uuid -> DocRef
                sourceUuidToDocRefMap.putIfAbsent(sourceUuid, source);
            }
            return this;
        }

        public WordList build() {
            if (wordList.isEmpty()) {
                return EMPTY;
            } else {
                final List<Word> wordObjList = new ArrayList<>(wordList.size());
                for (final SourcedWord sourcedWord : wordList) {
                    final String word = sourcedWord.word;
                    final String sourceUuid = sourcedWord.sourceUuid;

                    List<String> additionalSources = null;
                    final List<String> allSources = NullSafe.list(wordToSourceUuidsMap.get(word));
                    if (deDup) {
                        // First one is the primary source, so ignore it
                        if (allSources.size() > 1) {
                            additionalSources = allSources.subList(1, allSources.size());
                        }
                    } else {
                        // Not de-duping so get all sources except ours
                        additionalSources = allSources.stream()
                                .filter(uuid -> !Objects.equals(sourceUuid, uuid))
                                .collect(Collectors.toList());
                    }
                    final Word wordObj = new Word(word, sourceUuid, additionalSources);
                    wordObjList.add(wordObj);
                }
                return new WordList(wordObjList, sourceUuidToDocRefMap);
            }
        }
    }


    // --------------------------------------------------------------------------------


    @SuppressWarnings("ClassCanBeRecord") // Not in GWT
    private static final class SourcedWord {

        private final String word;
        private final String sourceUuid;

        private SourcedWord(final String word, final String sourceUuid) {
            Objects.requireNonNull(word);
            Objects.requireNonNull(sourceUuid);
            this.word = word;
            this.sourceUuid = sourceUuid;
        }

        public String word() {
            return word;
        }

        public String sourceUuid() {
            return sourceUuid;
        }

        @Override
        public String toString() {
            return "SourcedWord[" +
                    "word=" + word + ", " +
                    "sourceUuid=" + sourceUuid + ']';
        }
    }
}
