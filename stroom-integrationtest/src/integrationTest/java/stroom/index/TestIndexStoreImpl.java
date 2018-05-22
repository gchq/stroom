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

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.util.BaseEntityDeProxyProcessor;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFields;
import stroom.docref.DocRef;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import java.util.List;

public class TestIndexStoreImpl extends AbstractCoreIntegrationTest {
    @Inject
    private IndexStore indexStore;

    private DocRef testIndex;
    private DocRef refIndex;

    @Override
    protected void onBefore() {
        refIndex = indexStore.createDocument("Ref index");
        testIndex = indexStore.createDocument("Test index");

        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createDateField("TimeCreated"));
        indexFields.add(IndexField.createField("User"));

        final IndexDoc index = indexStore.readDocument(testIndex);
        index.setIndexFields(indexFields);
        indexStore.writeDocument(index);
    }

    @Test
    public void testIndexRetrieval() {
        Assert.assertEquals(2, indexStore.list().size());

        final IndexDoc index = indexStore.readDocument(indexStore.list().get(1));

        Assert.assertNotNull(index);
        Assert.assertEquals("Test index", index.getName());

        final IndexSerialiser indexSerialiser = new IndexSerialiser();
        final String xml = "" +
                "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                "<fields>\n" +
                "   <field>\n" +
                "      <analyzerType>KEYWORD</analyzerType>\n" +
                "      <caseSensitive>false</caseSensitive>\n" +
                "      <fieldName>StreamId</fieldName>\n" +
                "      <fieldType>ID</fieldType>\n" +
                "      <indexed>true</indexed>\n" +
                "      <stored>true</stored>\n" +
                "      <termPositions>false</termPositions>\n" +
                "   </field>\n" +
                "   <field>\n" +
                "      <analyzerType>KEYWORD</analyzerType>\n" +
                "      <caseSensitive>false</caseSensitive>\n" +
                "      <fieldName>EventId</fieldName>\n" +
                "      <fieldType>ID</fieldType>\n" +
                "      <indexed>true</indexed>\n" +
                "      <stored>true</stored>\n" +
                "      <termPositions>false</termPositions>\n" +
                "   </field>\n" +
                "   <field>\n" +
                "      <analyzerType>ALPHA_NUMERIC</analyzerType>\n" +
                "      <caseSensitive>false</caseSensitive>\n" +
                "      <fieldName>TimeCreated</fieldName>\n" +
                "      <fieldType>DATE_FIELD</fieldType>\n" +
                "      <indexed>true</indexed>\n" +
                "      <stored>false</stored>\n" +
                "      <termPositions>false</termPositions>\n" +
                "   </field>\n" +
                "   <field>\n" +
                "      <analyzerType>ALPHA_NUMERIC</analyzerType>\n" +
                "      <caseSensitive>false</caseSensitive>\n" +
                "      <fieldName>User</fieldName>\n" +
                "      <fieldType>FIELD</fieldType>\n" +
                "      <indexed>true</indexed>\n" +
                "      <stored>false</stored>\n" +
                "      <termPositions>false</termPositions>\n" +
                "   </field>\n" +
                "</fields>\n";
        final IndexFields indexFields = indexSerialiser.getIndexFieldsFromLegacyXML(xml);
        Assert.assertEquals(indexFields.getIndexFields(), index.getIndexFields());
    }

    @Test
    public void testLoad() {
        IndexDoc index = indexStore.readDocument(testIndex);
        Assert.assertNotNull(index);
        Assert.assertEquals("Test index", index.getName());
    }

    @Test
    public void testClientSideStuff1() {
        IndexDoc index = indexStore.readDocument(refIndex);
        index = ((IndexDoc) new BaseEntityDeProxyProcessor(true).process(index));
        indexStore.writeDocument(index);

    }

    @Test
    public void testClientSideStuff2() {
        IndexDoc index = indexStore.readDocument(testIndex);
        index = ((IndexDoc) new BaseEntityDeProxyProcessor(true).process(index));
        indexStore.writeDocument(index);
    }
}
