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

package stroom.pipeline.xml.converter.ds3.ref;


import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestRefParser extends StroomUnitTest {

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    void testSingle() {
        final RefParser refParser = new RefParser();

        List<RefFactory> sections = null;
        StoreRefFactory refDesc = null;
        MatchIndex matchIndex = null;

        // Try null.
        sections = refParser.parse(null);
        assertThat(sections.size()).isEqualTo(0);

        // Try empty string.
        sections = refParser.parse("");
        assertThat(sections.size()).isEqualTo(0);

        // Try remote ref.
        sections = refParser.parse("$heading$");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isEqualTo("heading");
        assertThat(refDesc.getRefGroup()).isEqualTo(0);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex).isNull();

        // Try group.
        sections = refParser.parse("$");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isNull();
        assertThat(refDesc.getRefGroup()).isEqualTo(0);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex).isNull();

        // Try numbered group.
        sections = refParser.parse("$6");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isNull();
        assertThat(refDesc.getRefGroup()).isEqualTo(6);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex).isNull();

        // Try numbered group with array.
        sections = refParser.parse("$6[2]");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isNull();
        assertThat(refDesc.getRefGroup()).isEqualTo(6);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex.isOffset()).isEqualTo(false);
        assertThat(matchIndex.getIndex()).isEqualTo(2);

        // Try numbered group with array with positive offset.
        sections = refParser.parse("$6[+2]");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isNull();
        assertThat(refDesc.getRefGroup()).isEqualTo(6);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex.isOffset()).isEqualTo(true);
        assertThat(matchIndex.getIndex()).isEqualTo(2);

        // Try numbered group with array with negative offset.
        sections = refParser.parse("$6[-2]");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isNull();
        assertThat(refDesc.getRefGroup()).isEqualTo(6);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex.isOffset()).isEqualTo(true);
        assertThat(matchIndex.getIndex()).isEqualTo(-2);

        // Try remote ref and numbered group.
        sections = refParser.parse("$heading$6");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isEqualTo("heading");
        assertThat(refDesc.getRefGroup()).isEqualTo(6);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex).isNull();

        // Try remote ref and numbered group and array.
        sections = refParser.parse("$heading$6[2]");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isEqualTo("heading");
        assertThat(refDesc.getRefGroup()).isEqualTo(6);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex.isOffset()).isEqualTo(false);
        assertThat(matchIndex.getIndex()).isEqualTo(2);

        // Try remote ref and numbered group and array with positive offset.
        sections = refParser.parse("$heading$6[+2]");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isEqualTo("heading");
        assertThat(refDesc.getRefGroup()).isEqualTo(6);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex.isOffset()).isEqualTo(true);
        assertThat(matchIndex.getIndex()).isEqualTo(2);

        // Try remote ref and numbered group and array with negative offset.
        sections = refParser.parse("$heading$6[-2]");
        assertThat(sections.size()).isEqualTo(1);
        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isEqualTo("heading");
        assertThat(refDesc.getRefGroup()).isEqualTo(6);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex.isOffset()).isEqualTo(true);
        assertThat(matchIndex.getIndex()).isEqualTo(-2);
    }

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    void testComposite() {
        StoreRefFactory refDesc = null;
        MatchIndex matchIndex = null;
        TextRefFactory textDesc = null;

        final RefParser refParser = new RefParser();
        final List<RefFactory> sections = refParser.parse("$heading$1[+3]+'test'+'2'+$heading2$+$3+$heading3$[5]");

        assertThat(sections.size()).isEqualTo(6);

        refDesc = (StoreRefFactory) sections.get(0);
        assertThat(refDesc.getRefId()).isEqualTo("heading");
        assertThat(refDesc.getRefGroup()).isEqualTo(1);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex.isOffset()).isEqualTo(true);
        assertThat(matchIndex.getIndex()).isEqualTo(3);

        textDesc = (TextRefFactory) sections.get(1);
        assertThat(textDesc.getText()).isEqualTo("test");

        textDesc = (TextRefFactory) sections.get(2);
        assertThat(textDesc.getText()).isEqualTo("2");

        refDesc = (StoreRefFactory) sections.get(3);
        assertThat(refDesc.getRefId()).isEqualTo("heading2");
        assertThat(refDesc.getRefGroup()).isEqualTo(0);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex).isNull();

        refDesc = (StoreRefFactory) sections.get(4);
        assertThat(refDesc.getRefId()).isNull();
        assertThat(refDesc.getRefGroup()).isEqualTo(3);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex).isNull();

        refDesc = (StoreRefFactory) sections.get(5);
        assertThat(refDesc.getRefId()).isEqualTo("heading3");
        assertThat(refDesc.getRefGroup()).isEqualTo(0);
        matchIndex = refDesc.getMatchIndex();
        assertThat(matchIndex.isOffset()).isEqualTo(false);
        assertThat(matchIndex.getIndex()).isEqualTo(5);
    }

    @Test
    void testFixed() {
        TextRefFactory textDesc = null;

        final RefParser refParser = new RefParser();
        final List<RefFactory> sections = refParser.parse("some heading");

        assertThat(sections.size()).isEqualTo(1);

        textDesc = (TextRefFactory) sections.get(0);
        assertThat(textDesc.getText()).isEqualTo("some heading");
    }

    @Test
    void testFixedWithQuotes() {
        TextRefFactory textDesc = null;

        final RefParser refParser = new RefParser();
        final List<RefFactory> sections = refParser.parse("'some ''other'' heading'");

        assertThat(sections.size()).isEqualTo(1);

        textDesc = (TextRefFactory) sections.get(0);
        assertThat(textDesc.getText()).isEqualTo("some 'other' heading");
    }
}
