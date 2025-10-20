/*
 * Copyright 2024 Crown Copyright
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

package stroom.contentindex;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.DocContentHighlights;
import stroom.explorer.shared.DocContentMatch;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.StringMatch;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xslt.XsltStore;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class TestLuceneContentIndex extends AbstractCoreIntegrationTest {

    @SuppressWarnings("checkstyle:linelength")
    private static final String TEXT = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <xsl:stylesheet xpath-default-namespace="records:2" xmlns="reference-data:2" xmlns:evt="event-logging:3"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            version="2.0">
                <xsl:template match="records">
                    <referenceData xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd event-logging:3 file://event-logging-v3.0.0.xsd" version="2.0.1">
                        <xsl:apply-templates/>
                    </referenceData>
                </xsl:template>

                <xsl:template match="record">
                    <reference>
                        <map>FILENO_TO_LOCATION_MAP</map>
                        <key><xsl:value-of select="data[@name='FileNo']/@value"/></key>
                        <value>
                            <evt:Location>
                                <evt:Country><xsl:value-of select="data[@name='Country']/@value"/></evt:Country>
                                <evt:Site><xsl:value-of select="data[@name='Site']/@value"/></evt:Site>
                                <evt:Building><xsl:value-of select="data[@name='Building']/@value"/></evt:Building>
                                <evt:Floor><xsl:value-of select="data[@name='Floor']/@value"/></evt:Floor>
                                <evt:Room><xsl:value-of select="data[@name='Room']/@value"/></evt:Room>
                                <evt:Desk><xsl:value-of select="data[@name='Desk']/@value"/></evt:Desk>
                            </evt:Location>
                        </value>
                    </reference>
                </xsl:template>
            </xsl:stylesheet>
            """;

    @Inject
    private XsltStore xsltStore;

    private XsltDoc xsltDoc;
    private DocRef docRef;

    @Mock
    ExplorerNodeService explorerNodeService;

    @BeforeEach
    void setup() {
        docRef = xsltStore.createDocument("Test");
        xsltDoc = xsltStore.readDocument(docRef);
        xsltDoc.setData(TEXT);
        xsltStore.writeDocument(xsltDoc);
    }

    @Test
    void testBasic() {
        test(StringMatch.contains("LOCATION"), 4, "Location");
    }

    @Test
    void testCaseSensitive() {
        test(StringMatch.contains("LOCATION", true), 1, "LOCATION");
    }

    @Test
    void testPartial() {
        test(StringMatch.contains("TION"), 4, "tion");
    }

    @Test
    void testPartialCaseSensitive() {
        test(StringMatch.contains("TION", true), 1, "TION");
    }

    @Test
    void testRegex() {
        test(StringMatch.regex("XML\\w*"), 6, "xml");
    }

    @Test
    void testRegexCaseSensitive() {
        test(StringMatch.regex("XML\\w*", true), 1, "XMLSchema");
        test(StringMatch.regex("xml\\w*", true), 5, "xml");
    }

    void test(final StringMatch stringMatch,
              final int expectedHighlightCount,
              final String expectedFirstHightlight) {
        final DocContentHighlights highlights = test(stringMatch);
        assertThat(highlights.getText()).isEqualTo(TEXT);
        assertThat(highlights.getHighlights().size()).isEqualTo(expectedHighlightCount);
        assertThat(TEXT.substring(highlights.getHighlights().getFirst().getOffset(),
                highlights.getHighlights().getFirst().getOffset() +
                highlights.getHighlights().getFirst().getLength())).isEqualTo(expectedFirstHightlight);
    }

    private DocContentHighlights test(final StringMatch stringMatch) {
        final LuceneContentIndex contentIndex = new LuceneContentIndex(
                this::getCurrentTestDir,
                Set.of(xsltStore),
                new MockSecurityContext(),
                new SimpleTaskContextFactory(),
                explorerNodeService);
        contentIndex.reindex();
        contentIndex.flush();

        final FindInContentRequest findInContentRequest =
                new FindInContentRequest(
                        new PageRequest(0, 100),
                        null, stringMatch);
        final ResultPage<DocContentMatch> matchResultPage = contentIndex.findInContent(findInContentRequest);
        assertThat(matchResultPage.size()).isEqualTo(1);

        final DocContentMatch docContentMatch = matchResultPage.getFirst();
        final FetchHighlightsRequest fetchHighlightsRequest =
                new FetchHighlightsRequest(docContentMatch.getDocRef(), docContentMatch.getExtension(), stringMatch);
        return contentIndex.fetchHighlights(fetchHighlightsRequest);
    }
}
