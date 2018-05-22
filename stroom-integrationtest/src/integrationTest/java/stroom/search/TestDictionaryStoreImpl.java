/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.search;

import org.junit.Assert;
import org.junit.Test;
import stroom.dictionary.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import java.util.Collections;

public class TestDictionaryStoreImpl extends AbstractCoreIntegrationTest {
    @Inject
    private DictionaryStore dictionaryStore;

    @Test
    public void test() {
        // Create a dictionary and save it.
        final DocRef docRef = dictionaryStore.createDocument("TEST");
        final DictionaryDoc dictionary = dictionaryStore.readDocument(docRef);
        dictionary.setData("This\nis\na\nlist\nof\nwords");
        dictionaryStore.writeDocument(dictionary);

        // Make sure we can get it back.
        final DictionaryDoc loaded = dictionaryStore.readDocument(docRef);
        Assert.assertNotNull(loaded);
        Assert.assertEquals(dictionary.getData(), loaded.getData());
        Assert.assertEquals(dictionary.getData(), dictionaryStore.getCombinedData(docRef));
    }

    @Test
    public void testImport() {
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
        Assert.assertEquals("dic1", dictionaryStore.getCombinedData(docRef1));
        Assert.assertEquals("dic1\ndic2", dictionaryStore.getCombinedData(docRef2));
        Assert.assertEquals("dic1\ndic2\ndic3", dictionaryStore.getCombinedData(docRef3));
    }
}
