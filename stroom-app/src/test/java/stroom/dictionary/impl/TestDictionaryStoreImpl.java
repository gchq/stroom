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

package stroom.dictionary.impl;


import stroom.dictionary.api.DictionaryStore;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDictionaryStoreImpl extends AbstractCoreIntegrationTest {

    @Inject
    private DictionaryStore dictionaryStore;
    @Inject
    private WordListProvider wordListProvider;

    @Test
    void test() {
        // Create a dictionary and save it.
        final DocRef docRef = dictionaryStore.createDocument("TEST");
        final DictionaryDoc dictionary = dictionaryStore.readDocument(docRef);
        dictionary.setData("This\nis\na\nlist\nof\nwords");
        dictionaryStore.writeDocument(dictionary);

        // Make sure we can get it back.
        final DictionaryDoc loaded = dictionaryStore.readDocument(docRef);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getData()).isEqualTo(dictionary.getData());
        assertThat(wordListProvider.getCombinedData(docRef)).isEqualTo(dictionary.getData());
    }

    @Test
    void testImport() {
        // Create a dictionary and save it.
        final DocRef docRef1 = dictionaryStore.createDocument("TEST");
        final DictionaryDoc dictionary1 = dictionaryStore.readDocument(docRef1);
        dictionary1.setData("dic1");
        dictionaryStore.writeDocument(dictionary1);

        // Create a dictionary and save it.
        final DocRef docRef2 = dictionaryStore.createDocument("TEST");
        final DictionaryDoc dictionary2 = dictionaryStore.readDocument(docRef2);
        dictionary2.setData("dic2");
        dictionary2.setImports(Collections.singletonList(docRef1));
        dictionaryStore.writeDocument(dictionary2);

        // Create a dictionary and save it.
        final DocRef docRef3 = dictionaryStore.createDocument("TEST");
        final DictionaryDoc dictionary3 = dictionaryStore.readDocument(docRef3);
        dictionary3.setData("dic3");
        dictionary3.setImports(Collections.singletonList(docRef2));
        dictionaryStore.writeDocument(dictionary3);

        // Make sure we can get it back.
        assertThat(wordListProvider.getCombinedData(docRef1)).isEqualTo("dic1");
        assertThat(wordListProvider.getCombinedData(docRef2)).isEqualTo("dic1\ndic2");
        assertThat(wordListProvider.getCombinedData(docRef3)).isEqualTo("dic1\ndic2\ndic3");
    }

    @Test
    void testFindByName() {
        // Create a dictionary and save it.
        final DocRef docRef1 = dictionaryStore.createDocument("dic1_name");
        final DictionaryDoc dictionary1 = dictionaryStore.readDocument(docRef1);
        dictionary1.setData("dic1");
        dictionaryStore.writeDocument(dictionary1);

        // Create a dictionary and save it.
        final DocRef docRef2 = dictionaryStore.createDocument("dic2_name");
        final DictionaryDoc dictionary2 = dictionaryStore.readDocument(docRef2);
        dictionary2.setData("dic2");
        dictionary2.setImports(Collections.singletonList(docRef1));
        dictionaryStore.writeDocument(dictionary2);

        // Make sure we can get it back.
        assertThat(wordListProvider.getCombinedData(docRef1)).isEqualTo("dic1");
        assertThat(wordListProvider.getCombinedData(docRef2)).isEqualTo("dic1\ndic2");

        final List<DocRef> dictionary1Results = wordListProvider.findByName("dic1_name");
        assertThat(dictionary1Results.size()).isOne();

        final List<DocRef> badResults = wordListProvider.findByName("BAD NAME");
        assertThat(badResults.size()).isZero();
    }
}
