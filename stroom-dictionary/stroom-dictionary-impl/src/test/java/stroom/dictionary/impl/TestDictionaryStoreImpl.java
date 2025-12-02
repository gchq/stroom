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

package stroom.dictionary.impl;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestDictionaryStoreImpl {

    @Mock
    private DictionarySerialiser mockDictionarySerialiser;
    @Mock
    private Store<DictionaryDoc> mockStore;
    @Mock
    private StoreFactory mockStoreFactory;

    @Test
    void getWords_unix() {

        // Unix line ends
        final DocRef docRef = createDoc("""

                one
                  two


                   three   \s
                four

                   """, "foo");

        final String[] words = getDictionaryStore().getWords(docRef);
        assertThat(words)
                .containsExactly(
                        "one",
                        "two",
                        "three",
                        "four");
    }

    @Test
    void getWords_windows() {

        // Use windows/dos line ends
        final DocRef docRef = createDoc("""
                \r
                one\r
                  two\r
                \r
                \r
                   three    \r
                four\r
                \r
                """, "foo");

        final String[] words = getDictionaryStore().getWords(docRef);
        assertThat(words)
                .containsExactly(
                        "one",
                        "two",
                        "three",
                        "four");
    }

    @Test
    void getCombinedData_noImports() {
        final String data = """
                one
                two
                three""";
        final DocRef docRef = createDoc(data, "foo");

        final String combinedData = getDictionaryStore().getCombinedData(docRef);

        assertThat(combinedData)
                .isEqualTo(data);
    }

    @Test
    void getCombinedData_oneImport() {
        final String data1 = """
                one
                two
                three""";

        final String data2 = """
                four
                five
                six""";

        final DocRef docRef1 = createDoc(data1, "doc1");
        final DocRef docRef2 = createDoc(data2, "doc2", docRef1);

        final String combinedData = getDictionaryStore().getCombinedData(docRef2);

        final String expected = String.join("\n", data1, data2).trim();
        assertThat(combinedData)
                .isEqualTo(expected);
    }

    @Test
    void getCombinedData_twoImports() {
        final String data1 = """
                one
                two
                three""";

        final String data2 = """
                four
                five
                six""";

        final String data3 = """
                seven
                eight
                nine""";

        final DocRef docRef1 = createDoc(data1, "doc1");
        final DocRef docRef2 = createDoc(data2, "doc2");
        final DocRef docRef3 = createDoc(data3, "doc3", docRef1, docRef2);

        final String combinedData = getDictionaryStore().getCombinedData(docRef3);

        final String expected = String.join("\n", data1, data2, data3);
        assertThat(combinedData)
                .isEqualTo(expected);
    }

    @Test
    void getCombinedData_nestedImports() {
        final String data1 = """
                one
                two
                three""";

        final String data2 = """
                four
                five
                six""";

        final String data3 = """
                seven
                eight
                nine""";

        final DocRef docRef1 = createDoc(data1, "doc1");
        final DocRef docRef2 = createDoc(data2, "doc2", docRef1);
        final DocRef docRef3 = createDoc(data3, "doc3", docRef2);

        final String combinedData = getDictionaryStore().getCombinedData(docRef3);

        final String expected = String.join("\n", data1, data2, data3);
        assertThat(combinedData)
                .isEqualTo(expected);
    }

    @Test
    void emptyDict() {
        final DocRef docRef1 = createDoc("""
                """, "doc1");

        final String[] words = getDictionaryStore().getWords(docRef1);

        Assertions.assertThat(words)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void blankDict() {
        final DocRef docRef1 = createDoc("""
                     \t

                \t
                """, "doc1");

        final String[] words = getDictionaryStore().getWords(docRef1);

        Assertions.assertThat(words)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void nullWords() {
        final String[] words = getDictionaryStore().getWords(DictionaryDoc.buildDocRef().randomUuid().build());
        Assertions.assertThat(words.length).isZero();
    }

    private DocRef createDoc(final String data, final String name, final DocRef... imports) {
        final DocRef docRef = DictionaryDoc.buildDocRef()
                .randomUuid()
                .name(name)
                .build();
        final DictionaryDoc dictionaryDoc = DictionaryDoc.builder()
                .uuid(docRef.getUuid())
                .name(docRef.getName())
                .data(data)
                .build();
        if (imports != null && imports.length > 0) {
            dictionaryDoc.setImports(Arrays.asList(imports));
        }

        Mockito.when(mockStore.readDocument(Mockito.eq(docRef)))
                .thenReturn(dictionaryDoc);

        return docRef;
    }

    private DictionaryStoreImpl getDictionaryStore() {
        final Store<DictionaryDoc> store = mockStoreFactory.createStore(
                Mockito.any(),
                Mockito.eq(DictionaryDoc.TYPE),
                Mockito.any());
        Mockito.when(store).thenReturn(mockStore);

        return new DictionaryStoreImpl(
                mockStoreFactory,
                mockDictionarySerialiser);
    }
}
