/*
 * Copyright 2016 Crown Copyright
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

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.entity.server.util.BaseEntityDeProxyProcessor;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.FolderService;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFields;

import javax.annotation.Resource;

public class TestIndexServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private IndexService indexService;
    @Resource
    private FolderService folderService;

    private Index testIndex;
    private Index refIndex;

    @Override
    protected void onBefore() {
        final DocRef testFolder = DocRefUtil.create(folderService.create(null, "Test Group"));
        refIndex = indexService.create(testFolder, "Ref index");
        testIndex = indexService.create(testFolder, "Test index");

        final IndexFields indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createDateField("TimeCreated"));
        indexFields.add(IndexField.createField("User"));
        testIndex.setIndexFieldsObject(indexFields);
        testIndex = indexService.save(testIndex);
    }

    @Test
    public void testIndexRetrieval() {
        final FindIndexCriteria criteria = new FindIndexCriteria();
        final BaseResultList<Index> list = indexService.find(criteria);

        Assert.assertEquals(2, list.size());

        final Index index = list.get(1);

        Assert.assertNotNull(index);
        Assert.assertEquals("Test index", index.getName());

        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        sb.append("<fields>\n");
        sb.append("   <field>\n");
        sb.append("      <analyzerType>KEYWORD</analyzerType>\n");
        sb.append("      <caseSensitive>false</caseSensitive>\n");
        sb.append("      <fieldName>StreamId</fieldName>\n");
        sb.append("      <fieldType>ID</fieldType>\n");
        sb.append("      <indexed>true</indexed>\n");
        sb.append("      <stored>true</stored>\n");
        sb.append("      <termPositions>false</termPositions>\n");
        sb.append("   </field>\n");
        sb.append("   <field>\n");
        sb.append("      <analyzerType>KEYWORD</analyzerType>\n");
        sb.append("      <caseSensitive>false</caseSensitive>\n");
        sb.append("      <fieldName>EventId</fieldName>\n");
        sb.append("      <fieldType>ID</fieldType>\n");
        sb.append("      <indexed>true</indexed>\n");
        sb.append("      <stored>true</stored>\n");
        sb.append("      <termPositions>false</termPositions>\n");
        sb.append("   </field>\n");
        sb.append("   <field>\n");
        sb.append("      <analyzerType>ALPHA_NUMERIC</analyzerType>\n");
        sb.append("      <caseSensitive>false</caseSensitive>\n");
        sb.append("      <fieldName>TimeCreated</fieldName>\n");
        sb.append("      <fieldType>DATE_FIELD</fieldType>\n");
        sb.append("      <indexed>true</indexed>\n");
        sb.append("      <stored>false</stored>\n");
        sb.append("      <termPositions>false</termPositions>\n");
        sb.append("   </field>\n");
        sb.append("   <field>\n");
        sb.append("      <analyzerType>ALPHA_NUMERIC</analyzerType>\n");
        sb.append("      <caseSensitive>false</caseSensitive>\n");
        sb.append("      <fieldName>User</fieldName>\n");
        sb.append("      <fieldType>FIELD</fieldType>\n");
        sb.append("      <indexed>true</indexed>\n");
        sb.append("      <stored>false</stored>\n");
        sb.append("      <termPositions>false</termPositions>\n");
        sb.append("   </field>\n");
        sb.append("</fields>\n");
        Assert.assertEquals(sb.toString(), index.getIndexFields());
    }

    @Test
    public void testLoad() {
        Index index = new Index();
        index.setId(testIndex.getId());
        index = indexService.load(index);

        Assert.assertNotNull(index);
        Assert.assertEquals("Test index", index.getName());
    }

    @Test
    public void testLoadById() {
        final Index index = indexService.loadById(testIndex.getId());
        Assert.assertNotNull(index);
        Assert.assertEquals("Test index", index.getName());
    }

    @Test
    public void testClientSideStuff1() {
        Index index = indexService.loadById(refIndex.getId());
        index = ((Index) new BaseEntityDeProxyProcessor(true).process(index));
        indexService.save(index);

    }

    @Test
    public void testClientSideStuff2() {
        Index index = indexService.loadById(testIndex.getId());
        index = ((Index) new BaseEntityDeProxyProcessor(true).process(index));
        indexService.save(index);
    }
}
