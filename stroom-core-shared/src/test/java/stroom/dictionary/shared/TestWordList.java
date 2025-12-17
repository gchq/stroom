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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestWordList {

    @Test
    void testAdd_deDup() {

        final DocRef petsRef = DictionaryDoc.buildDocRef()
                .uuid("200")
                .name("Pets")
                .build();
        final DocRef farmAnimalsRef = DictionaryDoc.buildDocRef()
                .uuid("100")
                .name("Farm Animals")
                .build();
        final DocRef wildAnimalsRef = DictionaryDoc.buildDocRef()
                .uuid("300")
                .name("Wild Animals")
                .build();

        final WordList wordList = WordList.builder(true)
                .addWord("cat", petsRef)
                .addWord("dog", petsRef)
                .addWord("hamster", petsRef)
                .addWord("pig", petsRef)
                .addWord("cow", farmAnimalsRef)
                .addWord("goat", farmAnimalsRef)
                .addWord("pig", farmAnimalsRef)
                .addWord("pig", wildAnimalsRef)
                .build();

        assertThat(wordList.isEmpty())
                .isFalse();
        assertThat(wordList.size())
                .isEqualTo(6);
        assertThat(wordList.sourceCount())
                .isEqualTo(3);
        assertThat(wordList.getWordList())
                .containsExactlyElementsOf(List.of(
                        new Word("cat", petsRef.getUuid(), null),
                        new Word("dog", petsRef.getUuid(), null),
                        new Word("hamster", petsRef.getUuid(), null),
                        new Word("pig", petsRef.getUuid(), sources(farmAnimalsRef, wildAnimalsRef)),
                        new Word("cow", farmAnimalsRef.getUuid(), null),
                        new Word("goat", farmAnimalsRef.getUuid(), null)));
        assertThat(wordList.getSortedList())
                .containsExactlyElementsOf(List.of(
                        new Word("cat", petsRef.getUuid(), null),
                        new Word("cow", farmAnimalsRef.getUuid(), null),
                        new Word("dog", petsRef.getUuid(), null),
                        new Word("goat", farmAnimalsRef.getUuid(), null),
                        new Word("hamster", petsRef.getUuid(), null),
                        new Word("pig", petsRef.getUuid(), sources(farmAnimalsRef, wildAnimalsRef))));

        assertThat(wordList.getSource(new Word("cat", petsRef.getUuid(), null)))
                .hasValue(petsRef);
        assertThat(wordList.getSource(new Word("cow", farmAnimalsRef.getUuid(), null)))
                .hasValue(farmAnimalsRef);

        assertThat(wordList.asString())
                .isEqualTo("""
                        cat
                        dog
                        hamster
                        pig
                        cow
                        goat""");
        assertThat(wordList.asWordArray())
                .containsExactly(
                        "cat",
                        "dog",
                        "hamster",
                        "pig",
                        "cow",
                        "goat");
        assertThat(wordList.getSources())
                .containsExactlyInAnyOrder(petsRef, farmAnimalsRef, wildAnimalsRef);

        final Word pig = wordList.getWord("pig").orElseThrow();
        assertThat(pig.getSourceUuid())
                .isEqualTo(petsRef.getUuid());
        assertThat(pig.getAdditionalSourceUuids())
                .containsExactly(farmAnimalsRef.getUuid(), wildAnimalsRef.getUuid());
    }

    @Test
    void testAdd_noDeDup() {

        final DocRef petsRef = DictionaryDoc.buildDocRef()
                .uuid("pets-uuid")
                .name("Pets")
                .build();
        final DocRef farmAnimalsRef = DictionaryDoc.buildDocRef()
                .uuid("farm-uuid")
                .name("Farm Animals")
                .build();
        final DocRef wildAnimalsRef = DictionaryDoc.buildDocRef()
                .uuid("wild-uuid")
                .name("Wild Animals")
                .build();

        final WordList wordList = WordList.builder(false)
                .addWord("cat", petsRef)
                .addWord("dog", petsRef)
                .addWord("hamster", petsRef)
                .addWord("pig", petsRef)
                .addWord("cow", farmAnimalsRef)
                .addWord("goat", farmAnimalsRef)
                .addWord("pig", farmAnimalsRef)
                .addWord("pig", wildAnimalsRef)
                .build();

        assertThat(wordList.isEmpty())
                .isFalse();
        assertThat(wordList.size())
                .isEqualTo(8);
        assertThat(wordList.sourceCount())
                .isEqualTo(3);
        assertThat(wordList.getWordList())
                .containsExactlyElementsOf(List.of(
                        new Word("cat", petsRef.getUuid()),
                        new Word("dog", petsRef.getUuid()),
                        new Word("hamster", petsRef.getUuid()),
                        new Word("pig", petsRef.getUuid(), sources(farmAnimalsRef, wildAnimalsRef)),
                        new Word("cow", farmAnimalsRef.getUuid()),
                        new Word("goat", farmAnimalsRef.getUuid()),
                        new Word("pig", farmAnimalsRef.getUuid(), sources(petsRef, wildAnimalsRef)),
                        new Word("pig", wildAnimalsRef.getUuid(), sources(petsRef, farmAnimalsRef))));
        assertThat(wordList.getSortedList())
                .containsExactlyElementsOf(List.of(
                        new Word("cat", petsRef.getUuid()),
                        new Word("cow", farmAnimalsRef.getUuid()),
                        new Word("dog", petsRef.getUuid()),
                        new Word("goat", farmAnimalsRef.getUuid()),
                        new Word("hamster", petsRef.getUuid()),
                        new Word("pig", farmAnimalsRef.getUuid(), sources(petsRef, wildAnimalsRef)),
                        new Word("pig", petsRef.getUuid(), sources(farmAnimalsRef, wildAnimalsRef)),
                        new Word("pig", wildAnimalsRef.getUuid(), sources(petsRef, farmAnimalsRef))));

        assertThat(wordList.getSource(new Word("cat", petsRef.getUuid())))
                .hasValue(petsRef);
        assertThat(wordList.getSource(new Word("cow", farmAnimalsRef.getUuid())))
                .hasValue(farmAnimalsRef);

        assertThat(wordList.asString())
                .isEqualTo("""
                        cat
                        dog
                        hamster
                        pig
                        cow
                        goat
                        pig
                        pig""");
        assertThat(wordList.asWordArray())
                .containsExactly(
                        "cat",
                        "dog",
                        "hamster",
                        "pig",
                        "cow",
                        "goat",
                        "pig",
                        "pig");
        assertThat(wordList.getSources())
                .containsExactlyInAnyOrder(petsRef, farmAnimalsRef, wildAnimalsRef);
    }

    @Test
    void testGetSource() {

        final DocRef petsRef = DictionaryDoc.buildDocRef()
                .uuid("200")
                .name("Pets")
                .build();
        final DocRef farmAnimalsRef = DictionaryDoc.buildDocRef()
                .uuid("100")
                .name("Farm Animals")
                .build();

        final WordList wordList = WordList.builder(false)
                .addWord("cat", petsRef)
                .addWord("dog", petsRef)
                .addWord("pig", petsRef)
                .build();

        assertThat(wordList.getSource(new Word("pig", petsRef.getUuid(), null)))
                .hasValue(petsRef);
        assertThat(wordList.getSource(new Word("pig", farmAnimalsRef.getUuid(), null)))
                .isEmpty();
    }

    @Test
    void testNullsAndBlanks() {
        final DocRef petsRef = DictionaryDoc.buildDocRef()
                .uuid("200")
                .name("Pets")
                .build();
        final WordList wordList = WordList.builder(false)
                .addWord("cat", petsRef)
                .addWord(null, petsRef)
                .addWord("", petsRef)
                .addWord(" ", petsRef)
                .addWord("\t ", petsRef)
                .build();

        assertThat(wordList.getWordList())
                .extracting(Word::getWord)
                .containsExactly(
                        "cat");
        assertThat(wordList.size())
                .isEqualTo(1);
    }

    private List<String> sources(final DocRef... docRefs) {
        return NullSafe.stream(docRefs)
                .map(DocRef::getUuid)
                .toList();
    }
}
