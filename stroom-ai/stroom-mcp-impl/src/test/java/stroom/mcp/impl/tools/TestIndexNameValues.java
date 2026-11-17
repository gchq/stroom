/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl.tools;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// An index name's variables are filled in only as Stroom's ElasticIndexingFilter would fill them: from the
/// document's root field of that name, so from what the indexing XSLT writes there, when that is known before it
/// runs (a fixed text, or the feed's name).
class TestIndexNameValues {

    private static final String LOWER_FEED = xslt("<string key=\"feed\"><xsl:value-of "
                                                  + "select=\"lower-case(stroom:feed-name())\"/></string>");

    @Test
    void aFeedVariableWrittenAsTheFeedsNameIsOneNameForEachFeed() {
        assertThat(IndexNameValues.filled("{feed}-events", List.of(LOWER_FEED), Set.of("ACME-B", "ACME-A")))
                .containsExactly("acme-a-events", "acme-b-events");
        // As written (no lower-case): Stroom uses the value as it is.
        assertThat(IndexNameValues.filled("{feed}-events", List.of(xslt("<string key=\"feed\"><xsl:value-of "
                                                                       + "select=\"stroom:feed-name()\"/></string>")),
                Set.of("ACME-A"))).containsExactly("ACME-A-events");
    }

    @Test
    void aFixedValueIsFilledInAndAnyOtherStaysAVariable() {
        final String code = xslt("<string key=\"_suffix\">-v2</string>"
                                 + "<string key=\"_year\"><xsl:value-of select=\"substring(@Time, 1, 4)\"/>"
                                 + "</string><string key=\"_kind\"><xsl:text>auth</xsl:text></string>");
        assertThat(IndexNameValues.filled("acme{_suffix}-{_kind}-{_year}", List.of(code), Set.of("ACME")))
                .containsExactly("acme-v2-auth-{_year}");
        // Written nowhere: Stroom fails the document, nothing known.
        assertThat(IndexNameValues.filled("acme-{other}", List.of(code), Set.of("ACME")))
                .containsExactly("acme-{other}");
    }

    @Test
    void aFieldWrittenInTwoWaysStaysAVariable() {
        final String code = xslt("<string key=\"feed\">acme</string><string key=\"feed\">other</string>");
        assertThat(IndexNameValues.fill(List.of(code), "feed")).isEmpty();
        assertThat(IndexNameValues.filled("{feed}-events", List.of(code), Set.of("ACME"))).containsExactly(
                "{feed}-events");
    }

    @Test
    void aFeedVariableWithNoFeedKnownStaysAVariable() {
        assertThat(IndexNameValues.filled("{feed}-events", List.of(LOWER_FEED), Set.of())).containsExactly(
                "{feed}-events");
        assertThat(IndexNameValues.filled("acme-events-v1", List.of(), Set.of("ACME"))).containsExactly(
                "acme-events-v1");
    }

    @Test
    void filledNamesTellFeedsApartAndAnUnknownVariableIsAnyValue() {
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "a", "Acme-A-Indexing");
        final ProductionIndexes.Claim forA = new ProductionIndexes.Claim(pipeline, "{feed}-events",
                List.of("acme-a-events"));
        assertThat(ProductionIndexes.matches(forA, List.of("acme-a-events"))).isTrue();
        assertThat(ProductionIndexes.matches(forA, List.of("acme-b-events"))).isFalse();
        // A multi-feed pipeline claims each feed's.
        final ProductionIndexes.Claim forAb = new ProductionIndexes.Claim(pipeline, "{feed}-events",
                List.of("acme-a-events", "acme-b-events"));
        assertThat(ProductionIndexes.matches(forAb, List.of("acme-b-events"))).isTrue();
        // Unfilled, as before: any value.
        assertThat(ProductionIndexes.matches(new ProductionIndexes.Claim(pipeline, "{feed}-events"),
                List.of("acme-b-events"))).isTrue();
    }

    private static String xslt(final String body) {
        return "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:stroom=\"stroom\" "
               + "xmlns=\"http://www.w3.org/2013/XSL/json\" version=\"3.0\"><xsl:template match=\"/\"><array><map>"
               + body + "</map></array></xsl:template></xsl:stylesheet>";
    }
}
