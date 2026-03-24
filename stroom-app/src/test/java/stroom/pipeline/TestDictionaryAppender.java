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

package stroom.pipeline;

import stroom.dictionary.api.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.test.common.StroomPipelineTestFileUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestDictionaryAppender extends AbstractAppenderTest {

    @Inject
    private DictionaryStore dictionaryStore;

    @Test
    void testXML() {
        final DocRef dictionary = createDictionary();
        test("TestDictionaryAppender", "XML", dictionary);
        final DictionaryDoc dictionaryDoc = dictionaryStore.readDocument(dictionary);
        final String out = StroomPipelineTestFileUtil
                .getString("TestDictionaryAppender/TestDictionaryAppender_XML.out");
        assertThat(dictionaryDoc.getData()).isEqualTo(out);
    }

    @Test
    void testText() {
        final DocRef dictionary = createDictionary();
        test("TestDictionaryAppender", "Text", dictionary);
        final DictionaryDoc dictionaryDoc = dictionaryStore.readDocument(dictionary);
        final String out = StroomPipelineTestFileUtil
                .getString("TestDictionaryAppender/TestDictionaryAppender_Text.out");
        assertThat(dictionaryDoc.getData()).isEqualTo(out);
    }

    public DocRef createDictionary() {
        return dictionaryStore.createDocument("test");
    }
}
