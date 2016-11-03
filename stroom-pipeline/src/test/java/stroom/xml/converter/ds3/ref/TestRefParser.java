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

package stroom.xml.converter.ds3.ref;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import stroom.util.test.StroomUnitTest;

public class TestRefParser extends StroomUnitTest {
    @Test
    public void testSingle() {
        final RefParser refParser = new RefParser();

        List<RefFactory> sections = null;
        StoreRefFactory refDesc = null;
        MatchIndex matchIndex = null;

        // Try null.
        sections = refParser.parse(null);
        Assert.assertEquals(0, sections.size());

        // Try empty string.
        sections = refParser.parse("");
        Assert.assertEquals(0, sections.size());

        // Try remote ref.
        sections = refParser.parse("$heading$");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertEquals("heading", refDesc.getRefId());
        Assert.assertEquals(0, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertNull(matchIndex);

        // Try group.
        sections = refParser.parse("$");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertNull(refDesc.getRefId());
        Assert.assertEquals(0, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertNull(matchIndex);

        // Try numbered group.
        sections = refParser.parse("$6");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertNull(refDesc.getRefId());
        Assert.assertEquals(6, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertNull(matchIndex);

        // Try numbered group with array.
        sections = refParser.parse("$6[2]");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertNull(refDesc.getRefId());
        Assert.assertEquals(6, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertEquals(false, matchIndex.isOffset());
        Assert.assertEquals(2, matchIndex.getIndex());

        // Try numbered group with array with positive offset.
        sections = refParser.parse("$6[+2]");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertNull(refDesc.getRefId());
        Assert.assertEquals(6, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertEquals(true, matchIndex.isOffset());
        Assert.assertEquals(2, matchIndex.getIndex());

        // Try numbered group with array with negative offset.
        sections = refParser.parse("$6[-2]");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertNull(refDesc.getRefId());
        Assert.assertEquals(6, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertEquals(true, matchIndex.isOffset());
        Assert.assertEquals(-2, matchIndex.getIndex());

        // Try remote ref and numbered group.
        sections = refParser.parse("$heading$6");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertEquals("heading", refDesc.getRefId());
        Assert.assertEquals(6, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertNull(matchIndex);

        // Try remote ref and numbered group and array.
        sections = refParser.parse("$heading$6[2]");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertEquals("heading", refDesc.getRefId());
        Assert.assertEquals(6, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertEquals(false, matchIndex.isOffset());
        Assert.assertEquals(2, matchIndex.getIndex());

        // Try remote ref and numbered group and array with positive offset.
        sections = refParser.parse("$heading$6[+2]");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertEquals("heading", refDesc.getRefId());
        Assert.assertEquals(6, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertEquals(true, matchIndex.isOffset());
        Assert.assertEquals(2, matchIndex.getIndex());

        // Try remote ref and numbered group and array with negative offset.
        sections = refParser.parse("$heading$6[-2]");
        Assert.assertEquals(1, sections.size());
        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertEquals("heading", refDesc.getRefId());
        Assert.assertEquals(6, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertEquals(true, matchIndex.isOffset());
        Assert.assertEquals(-2, matchIndex.getIndex());
    }

    @Test
    public void testComposite() {
        StoreRefFactory refDesc = null;
        MatchIndex matchIndex = null;
        TextRefFactory textDesc = null;

        final RefParser refParser = new RefParser();
        final List<RefFactory> sections = refParser.parse("$heading$1[+3]+'test'+'2'+$heading2$+$3+$heading3$[5]");

        Assert.assertEquals(6, sections.size());

        refDesc = (StoreRefFactory) sections.get(0);
        Assert.assertEquals("heading", refDesc.getRefId());
        Assert.assertEquals(1, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertEquals(true, matchIndex.isOffset());
        Assert.assertEquals(3, matchIndex.getIndex());

        textDesc = (TextRefFactory) sections.get(1);
        Assert.assertEquals("test", textDesc.getText());

        textDesc = (TextRefFactory) sections.get(2);
        Assert.assertEquals("2", textDesc.getText());

        refDesc = (StoreRefFactory) sections.get(3);
        Assert.assertEquals("heading2", refDesc.getRefId());
        Assert.assertEquals(0, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertNull(matchIndex);

        refDesc = (StoreRefFactory) sections.get(4);
        Assert.assertNull(refDesc.getRefId());
        Assert.assertEquals(3, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertNull(matchIndex);

        refDesc = (StoreRefFactory) sections.get(5);
        Assert.assertEquals("heading3", refDesc.getRefId());
        Assert.assertEquals(0, refDesc.getRefGroup());
        matchIndex = refDesc.getMatchIndex();
        Assert.assertEquals(false, matchIndex.isOffset());
        Assert.assertEquals(5, matchIndex.getIndex());
    }

    @Test
    public void testFixed() {
        TextRefFactory textDesc = null;

        final RefParser refParser = new RefParser();
        final List<RefFactory> sections = refParser.parse("some heading");

        Assert.assertEquals(1, sections.size());

        textDesc = (TextRefFactory) sections.get(0);
        Assert.assertEquals("some heading", textDesc.getText());
    }

    @Test
    public void testFixedWithQuotes() {
        TextRefFactory textDesc = null;

        final RefParser refParser = new RefParser();
        final List<RefFactory> sections = refParser.parse("'some ''other'' heading'");

        Assert.assertEquals(1, sections.size());

        textDesc = (TextRefFactory) sections.get(0);
        Assert.assertEquals("some 'other' heading", textDesc.getText());
    }
}
