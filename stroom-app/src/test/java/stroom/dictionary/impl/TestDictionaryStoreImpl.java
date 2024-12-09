/*
 * Copyright 2017-2024 Crown Copyright
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
        DictionaryDoc dictionary = dictionaryStore.createDocument();
        dictionary.setName("TEST");
        dictionary.setData("This\nis\na\nlist\nof\nwords");
        dictionary = dictionaryStore.writeDocument(dictionary);

        // Make sure we can get it back.
        final DictionaryDoc loaded = dictionaryStore.readDocument(dictionary.asDocRef());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getData()).isEqualTo(dictionary.getData());
        assertThat(wordListProvider.getCombinedData(dictionary.asDocRef())).isEqualTo(dictionary.getData());
    }

    @Test
    void testImport() {
        // Create a dictionary and save it.
        DictionaryDoc dictionary1 = dictionaryStore.createDocument();
        dictionary1.setName("TEST1");
        dictionary1.setData("dic1");
        dictionary1 = dictionaryStore.writeDocument(dictionary1);

        // Create a dictionary and save it.
        DictionaryDoc dictionary2 = dictionaryStore.createDocument();
        dictionary2.setName("TEST2");
        dictionary2.setData("dic2");
        dictionary2.setImports(Collections.singletonList(dictionary1.asDocRef()));
        dictionary2 = dictionaryStore.writeDocument(dictionary2);

        // Create a dictionary and save it.
        DictionaryDoc dictionary3 = dictionaryStore.createDocument();
        dictionary3.setName("TEST3");
        dictionary3.setData("dic3");
        dictionary3.setImports(Collections.singletonList(dictionary2.asDocRef()));
        dictionaryStore.writeDocument(dictionary3);

        // Make sure we can get it back.
        assertThat(wordListProvider.getCombinedData(dictionary1.asDocRef())).isEqualTo("dic1");
        assertThat(wordListProvider.getCombinedData(dictionary2.asDocRef())).isEqualTo("dic1\ndic2");
        assertThat(wordListProvider.getCombinedData(dictionary3.asDocRef())).isEqualTo("dic1\ndic2\ndic3");
    }

    @Test
    void testFindByName() {
        // Create a dictionary and save it.
        DictionaryDoc dictionary1 = dictionaryStore.createDocument();
        dictionary1.setName("dic1_name");
        dictionary1.setData("dic1");
        dictionary1 = dictionaryStore.writeDocument(dictionary1);

        // Create a dictionary and save it.
        DictionaryDoc dictionary2 = dictionaryStore.createDocument();
        dictionary2.setName("dic2_name");
        dictionary2.setData("dic2");
        dictionary2.setImports(Collections.singletonList(dictionary1.asDocRef()));
        dictionary2 = dictionaryStore.writeDocument(dictionary2);

        // Make sure we can get it back.
        assertThat(wordListProvider.getCombinedData(dictionary1.asDocRef())).isEqualTo("dic1");
        assertThat(wordListProvider.getCombinedData(dictionary2.asDocRef())).isEqualTo("dic1\ndic2");

        List<DocRef> dictionary1Results = wordListProvider.findByName("dic1_name");
        assertThat(dictionary1Results.size()).isOne();

        List<DocRef> badResults = wordListProvider.findByName("BAD NAME");
        assertThat(badResults.size()).isZero();
    }
}
