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

package stroom.index;


import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldService;
import stroom.index.impl.IndexFields;
import stroom.index.impl.IndexStore;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexStoreImpl extends AbstractCoreIntegrationTest {

    @Inject
    private IndexStore indexStore;
    @Inject
    private IndexFieldService indexFieldService;

    private DocRef testIndex;
    private DocRef refIndex;

    @BeforeEach
    void setup() {
        refIndex = indexStore.createDocument("Ref index");
        testIndex = indexStore.createDocument("Test index");

        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(LuceneIndexField.createDateField("TimeCreated"));
        indexFields.add(LuceneIndexField.createField("User"));

        final LuceneIndexDoc index = indexStore.readDocument(testIndex);
        index.setFields(indexFields);
        indexStore.writeDocument(index);
    }

//    @Test
//    void testIndexRetrieval() {
//        List<DocRef> list = indexStore.list();
//        assertThat(list.size()).isEqualTo(2);
//
//        assertThat(list.stream()
//                .filter(docRef -> docRef.getName().equals("Test index"))
//                .count())
//                .isEqualTo(1);
//        assertThat((int) list.stream()
//                .filter(docRef -> docRef.getName().equals("Ref index"))
//                .count())
//                .isEqualTo(1);
//
//        final LuceneIndexDoc index = indexStore.readDocument(list.stream()
//                .filter(docRef -> docRef.getName().equals("Test index"))
//                .findFirst()
//                .orElseThrow());
//
//        assertThat(index).isNotNull();
//        assertThat(index.getName()).isEqualTo("Test index");
//
//        final String xml =
//                """
//                        <?xml version="1.1" encoding="UTF-8"?>
//                        <fields>
//                           <field>
//                              <analyzerType>KEYWORD</analyzerType>
//                              <caseSensitive>false</caseSensitive>
//                              <fieldName>EventId</fieldName>
//                              <fieldType>ID</fieldType>
//                              <indexed>true</indexed>
//                              <stored>true</stored>
//                              <termPositions>false</termPositions>
//                           </field>
//                           <field>
//                              <analyzerType>KEYWORD</analyzerType>
//                              <caseSensitive>false</caseSensitive>
//                              <fieldName>StreamId</fieldName>
//                              <fieldType>ID</fieldType>
//                              <indexed>true</indexed>
//                              <stored>true</stored>
//                              <termPositions>false</termPositions>
//                           </field>
//                           <field>
//                              <analyzerType>ALPHA_NUMERIC</analyzerType>
//                              <caseSensitive>false</caseSensitive>
//                              <fieldName>TimeCreated</fieldName>
//                              <fieldType>DATE_FIELD</fieldType>
//                              <indexed>true</indexed>
//                              <stored>false</stored>
//                              <termPositions>false</termPositions>
//                           </field>
//                           <field>
//                              <analyzerType>ALPHA_NUMERIC</analyzerType>
//                              <caseSensitive>false</caseSensitive>
//                              <fieldName>User</fieldName>
//                              <fieldType>FIELD</fieldType>
//                              <indexed>true</indexed>
//                              <stored>false</stored>
//                              <termPositions>false</termPositions>
//                           </field>
//                        </fields>
//                        """;
//        final List<IndexField> indexFields = MappingUtil
//                .map(LegacyXmlSerialiser.getIndexFieldsFromLegacyXml(xml))
//                .stream()
//                .map(indexField -> (IndexField) new IndexFieldImpl.Builder(indexField).build())
//                .sorted()
//                .toList();
//        final FindFieldCriteria findFieldCriteria =
//                new FindFieldCriteria(PageRequest.unlimited(), FindFieldCriteria.DEFAULT_SORT_LIST, index.asDocRef());
//        final List<IndexField> stored = indexFieldService.findFields(findFieldCriteria).getValues();
//        assertThat(stored).isEqualTo(indexFields);
//    }

    @Test
    void testLoad() {
        final LuceneIndexDoc index = indexStore.readDocument(testIndex);
        assertThat(index).isNotNull();
        assertThat(index.getName()).isEqualTo("Test index");
    }

    @Test
    void testClientSideStuff1() {
        final LuceneIndexDoc index = indexStore.readDocument(refIndex);
        indexStore.writeDocument(index);
    }

    @Test
    void testClientSideStuff2() {
        final LuceneIndexDoc index = indexStore.readDocument(testIndex);
        indexStore.writeDocument(index);
    }
}
