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

package stroom.index;


import stroom.docref.DocRef;
import stroom.index.impl.IndexFields;
import stroom.index.impl.IndexStore;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.legacy.impex_6_1.LegacyXmlSerialiser;
import stroom.legacy.impex_6_1.MappingUtil;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexStoreImpl extends AbstractCoreIntegrationTest {

    @Inject
    private IndexStore indexStore;

    private DocRef testIndex;
    private DocRef refIndex;

    @BeforeEach
    void setup() {
        LuceneIndexDoc refIndex = indexStore.createDocument();
        refIndex.setName("Ref index");
        this.refIndex = indexStore.writeDocument(refIndex).asDocRef();

        LuceneIndexDoc index = indexStore.createDocument();
        index.setName("Test index");


        final List<LuceneIndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(LuceneIndexField.createDateField("TimeCreated"));
        indexFields.add(LuceneIndexField.createField("User"));

        index.setFields(indexFields);
        index = indexStore.writeDocument(index);
        testIndex = index.asDocRef();
    }

    @Test
    void testIndexRetrieval() {
        List<DocRef> list = indexStore.list();
        assertThat(list.size()).isEqualTo(2);

        assertThat(list.stream()
                .filter(docRef ->
                        docRef.getName().equals("Test index"))
                .count())
                .isEqualTo(1);
        assertThat((int) list.stream()
                .filter(docRef ->
                        docRef.getName().equals("Ref index"))
                .count())
                .isEqualTo(1);

        final LuceneIndexDoc index = indexStore.readDocument(list.stream()
                .filter(docRef ->
                        docRef.getName().equals("Test index"))
                .findFirst()
                .orElseThrow());

        assertThat(index).isNotNull();
        assertThat(index.getName()).isEqualTo("Test index");

        final String xml =
                """
                        <?xml version="1.1" encoding="UTF-8"?>
                        <fields>
                           <field>
                              <analyzerType>KEYWORD</analyzerType>
                              <caseSensitive>false</caseSensitive>
                              <fieldName>StreamId</fieldName>
                              <fieldType>ID</fieldType>
                              <indexed>true</indexed>
                              <stored>true</stored>
                              <termPositions>false</termPositions>
                           </field>
                           <field>
                              <analyzerType>KEYWORD</analyzerType>
                              <caseSensitive>false</caseSensitive>
                              <fieldName>EventId</fieldName>
                              <fieldType>ID</fieldType>
                              <indexed>true</indexed>
                              <stored>true</stored>
                              <termPositions>false</termPositions>
                           </field>
                           <field>
                              <analyzerType>ALPHA_NUMERIC</analyzerType>
                              <caseSensitive>false</caseSensitive>
                              <fieldName>TimeCreated</fieldName>
                              <fieldType>DATE_FIELD</fieldType>
                              <indexed>true</indexed>
                              <stored>false</stored>
                              <termPositions>false</termPositions>
                           </field>
                           <field>
                              <analyzerType>ALPHA_NUMERIC</analyzerType>
                              <caseSensitive>false</caseSensitive>
                              <fieldName>User</fieldName>
                              <fieldType>FIELD</fieldType>
                              <indexed>true</indexed>
                              <stored>false</stored>
                              <termPositions>false</termPositions>
                           </field>
                        </fields>
                        """;
        final List<LuceneIndexField> indexFields = MappingUtil.map(
                LegacyXmlSerialiser.getIndexFieldsFromLegacyXml(xml));
        assertThat(index.getFields()).isEqualTo(indexFields);
    }

    @Test
    void testLoad() {
        LuceneIndexDoc index = indexStore.readDocument(testIndex);
        assertThat(index).isNotNull();
        assertThat(index.getName()).isEqualTo("Test index");
    }

    @Test
    void testClientSideStuff1() {
        LuceneIndexDoc index = indexStore.readDocument(refIndex);
        indexStore.writeDocument(index);

    }

    @Test
    void testClientSideStuff2() {
        LuceneIndexDoc index = indexStore.readDocument(testIndex);
        indexStore.writeDocument(index);
    }
}
