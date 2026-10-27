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

package stroom.ai.impl.extraction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestExtractionChange {

    // An extraction XSLT written by hand, as the content pack's are.
    private static final String HAND_WRITTEN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet xmlns="records:2" xmlns:stroom="stroom" xmlns:evt="event-logging:3"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
              <!-- Each event's <record> -->
              <xsl:template match="evt:Events">
                <records version="2.0">
                  <xsl:apply-templates/>
                </records>
              </xsl:template>
              <xsl:template match="evt:Event">
                <record>
                  <data name="StreamId" value="{@StreamId}"/>
                  <data name="EventId" value="{@EventId}"/>
                  <data name="UserId" value="{evt:EventSource/evt:User/evt:Id}"/>
                </record>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void oneStroomWroteIsWrittenAgainFromEveryFieldItIsToGive() {
        final String before = ExtractionXslt.xslt("event-logging:3", List.of(
                new ExtractionField("UserId", "EventSource/User/Id", null),
                new ExtractionField("Type", "EventDetail/TypeId", null)));

        // UserId kept, Type removed, Organisation added.
        final ExtractionChange.Changed changed = ExtractionChange.change(before, "", List.of(
                new ExtractionField("UserId", "EventSource/User/Id", null),
                new ExtractionField("Organisation", "EventSource/User/Organisation", null)));

        assertThat(changed.generated()).isTrue();
        assertThat(changed.alsoGiven()).isEmpty();
        assertThat(changed.added()).extracting(ExtractionField::name).containsExactly("Organisation");
        // What anything else using it loses.
        assertThat(changed.removed()).containsExactly("Type");
        // The namespace it was written with is kept.
        assertThat(ExtractionXslt.read(changed.code())).hasValueSatisfying(generated -> {
            assertThat(generated.namespace()).isEqualTo("event-logging:3");
            assertThat(generated.fields()).extracting(ExtractionField::name)
                    .containsExactly("UserId", "Organisation");
        });
        assertThat(ExtractionPreview.values(TestExtractionXslt.transform(changed.code(), TestExtractionXslt.EVENT)))
                .containsExactly(Map.entry("StreamId", "7"), Map.entry("EventId", "2"), Map.entry("UserId", "jsmith"),
                        Map.entry("Organisation", "Finance & Ops"));
    }

    @Test
    void oneWrittenByHandKeepsWhatItGivesWithTheFieldsAdded() {
        final ExtractionChange.Changed changed = ExtractionChange.change(HAND_WRITTEN, "event-logging:3", List.of(
                new ExtractionField("Organisation", "EventSource/User/Organisation", null),
                new ExtractionField("Bytes", "EventDetail/Data[@Name = \"Bytes\"]/@Value", "number")));

        assertThat(changed.generated()).isFalse();
        assertThat(changed.alsoGiven()).containsExactly("StreamId", "EventId", "UserId");
        assertThat(changed.added()).extracting(ExtractionField::name).containsExactly("Organisation", "Bytes");
        // Only added to: everything it had is as it was.
        assertThat(changed.code()).startsWith(HAND_WRITTEN.substring(0, HAND_WRITTEN.indexOf("</record>")).strip());
        assertThat(changed.code()).endsWith(HAND_WRITTEN.substring(HAND_WRITTEN.indexOf("</record>")));
        assertThat(ExtractionPreview.values(TestExtractionXslt.transform(changed.code(), TestExtractionXslt.EVENT)))
                .containsExactly(Map.entry("StreamId", "7"), Map.entry("EventId", "2"), Map.entry("UserId", "jsmith"),
                        Map.entry("Organisation", "Finance & Ops"), Map.entry("Bytes", "512"));
    }

    @Test
    void fieldsAreAddedToOneWrittenByHandForEventsWithNoNamespace() {
        final String code = """
                <x:stylesheet version="2.0" xmlns:x="http://www.w3.org/1999/XSL/Transform">
                  <x:template match="/*"><records><x:apply-templates select="*"/></records></x:template>
                  <x:template match="/*/*"><record><data name="Host" value="{Host}"/></record></x:template>
                </x:stylesheet>""";

        final ExtractionChange.Changed changed = ExtractionChange.change(code, "", List.of(
                new ExtractionField("Port", "Port", "number")));

        assertThat(ExtractionPreview.values(TestExtractionXslt.transform(changed.code(),
                "<Records><Record><Host>web1</Host><Port>443</Port></Record></Records>")))
                .containsExactly(Map.entry("Host", "web1"), Map.entry("Port", "443"));
    }

    @Test
    void aFieldOneWrittenByHandGivesAlreadyIsntAddedAgain() {
        assertThatThrownBy(() -> ExtractionChange.change(HAND_WRITTEN, "event-logging:3", List.of(
                new ExtractionField("userid", "EventSource/User/Id", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gives userid already");
        assertThatThrownBy(() -> ExtractionChange.change(HAND_WRITTEN, "event-logging:3", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Give the fields to add");
    }

    @Test
    void oneWrittenByHandWithoutOneRecordIsntGuessedAt() {
        final String twoRecords = HAND_WRITTEN.replace("</xsl:template>\n</xsl:stylesheet>",
                "</xsl:template>\n  <xsl:template match=\"x\"><record/></xsl:template>\n</xsl:stylesheet>")
                .replace("<record/>", "<record></record>");
        assertThatThrownBy(() -> ExtractionChange.change(twoRecords, "", List.of(
                new ExtractionField("Org", "Org", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 record elements");

        // A record's end in a comment doesn't count.
        final String commented = HAND_WRITTEN.replace("<!-- Each event's <record> -->",
                "<!-- Each event's </record> -->");
        assertThat(ExtractionChange.change(commented, "", List.of(new ExtractionField("Org", "Org", null))).code())
                .contains("<!-- Each event's </record> -->");

        assertThatThrownBy(() -> ExtractionChange.change(HAND_WRITTEN.replace("</record>", "</rec>"), "",
                List.of(new ExtractionField("Org", "Org", null))))
                .hasMessageContaining("no record element");
        assertThatThrownBy(() -> ExtractionChange.change(" ", "", List.of(new ExtractionField("Org", "Org", null))))
                .hasMessageContaining("empty");
    }

    @Test
    void anXsltNamingFieldsAsItRunsIsSeen() {
        assertThat(ExtractionChange.namesAsItRuns(HAND_WRITTEN)).isFalse();
        assertThat(ExtractionChange.namesAsItRuns("<xsl:for-each select=\"Data\"><data name=\"{@Name}\" "
                                                  + "value=\"{@Value}\"/></xsl:for-each>")).isTrue();
        assertThat(ExtractionChange.namesAsItRuns("<xsl:element name=\"data\"><xsl:attribute name=\"name\">"))
                .isTrue();
        assertThat(ExtractionChange.namesAsItRuns("<data>\n  <xsl:attribute name=\"name\" select=\"@Name\"/>"))
                .isTrue();
        // Not in a comment.
        assertThat(ExtractionChange.namesAsItRuns("<!-- <data name=\"{@Name}\"/> -->")).isFalse();
        final ExtractionChange.Changed changed = ExtractionChange.change(HAND_WRITTEN.replace(
                "<data name=\"UserId\" value=\"{evt:EventSource/evt:User/evt:Id}\"/>",
                "<xsl:for-each select=\"evt:Data\"><data name=\"{@Name}\" value=\"{@Value}\"/></xsl:for-each>"),
                "", List.of(new ExtractionField("Org", "Org", null)));
        assertThat(changed.namesAsItRuns()).isTrue();
        assertThat(changed.alsoGiven()).containsExactly("StreamId", "EventId");
    }

    @Test
    void theNamesAnXsltGivesAreReadFromItsDataElements() {
        assertThat(ExtractionChange.dataNames(HAND_WRITTEN)).containsExactly("StreamId", "EventId", "UserId");
        assertThat(ExtractionChange.dataNames("<!-- <data name=\"Old\"/> --><r:data name='New' value='x'/>"))
                .containsExactly("New");
    }
}
