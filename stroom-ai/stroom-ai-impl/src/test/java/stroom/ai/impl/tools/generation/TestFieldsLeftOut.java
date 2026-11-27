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

package stroom.ai.impl.tools.generation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The sample's fields a hand-written translation never reads, left out of the mapping read back from it
/// (`ignored_fields`), so the XSLT regenerated from that mapping logs what the original did (decided by the user,
/// 2026-11-25; seen in eval case 105: the regenerated XSLT logged "No rule reads field src_ip", which the original
/// never did, and the agent mapped src_ip unasked).
class TestFieldsLeftOut {

    // A hand-written JSON translation: it reads action, ts and user, never src_ip or host.
    private static final String HAND_WRITTEN = """
            <xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="http://www.w3.org/2013/XSL/json" version="3.0">
              <xsl:template match="map[string[@key='action'] = 'login']">
                <Event><EventTime><TimeCreated><xsl:value-of select="string[@key=&quot;ts&quot;]"/></TimeCreated>
                </EventTime><User><Id><xsl:value-of select="string[@key='user']"/></Id></User></Event>
              </xsl:template>
            </xsl:stylesheet>""";
    private static final List<String> SAMPLE_FIELDS = List.of("ts", "action", "user", "src_ip", "host");

    @Test
    void theSampleFieldsTheOriginalNeverNamesAreLeftOutInTheSampleOrder() {
        assertThat(FieldsLeftOut.of(HAND_WRITTEN, mapping("json", List.of()), SAMPLE_FIELDS))
                .containsExactly("src_ip", "host");
    }

    @Test
    void aFieldTheMappingReadsOrAlreadyIgnoresIsNotListedAgain() {
        final TranslationMapping reading = TranslationMapping.parse(Map.of("input", "json", "ignored_fields",
                List.of("host"), "events", List.of(Map.of("name", "logon", "fields", List.of(
                        Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                        Map.of("path", "EventSource/Client/IPAddress", "field", "src_ip"))))));

        assertThat(FieldsLeftOut.of(HAND_WRITTEN, reading, SAMPLE_FIELDS)).isEmpty();
    }

    @Test
    void anXsltThatLogsItsOwnUnreadFieldsHasNoneLeftOut() {
        // Generated (its own "No rule reads field" logging): what it logs is already what the regenerated one logs.
        final String logging = HAND_WRITTEN.replace("</xsl:stylesheet>", "<xsl:template name=\"unread\">"
                + "<xsl:sequence select=\"stroom:log('WARN', concat('" + XsltGenerator.UNREAD_FIELD
                + "', @key))\"/></xsl:template></xsl:stylesheet>");

        assertThat(FieldsLeftOut.of(logging, mapping("json", List.of()), SAMPLE_FIELDS)).isEmpty();
    }

    @Test
    void inputWhoseUnreadFieldsAreNeverLoggedHasNoneLeftOut() {
        assertThat(FieldsLeftOut.of(HAND_WRITTEN, mapping("xml", List.of()), SAMPLE_FIELDS)).isEmpty();
    }

    @Test
    void noCodeOrNoSampleFieldsLeavesNothingOut() {
        assertThat(FieldsLeftOut.of(null, mapping("json", List.of()), SAMPLE_FIELDS)).isEmpty();
        assertThat(FieldsLeftOut.of(HAND_WRITTEN, mapping("json", List.of()), List.of())).isEmpty();
        assertThat(FieldsLeftOut.of(HAND_WRITTEN, null, SAMPLE_FIELDS)).isEmpty();
        assertThat(FieldsLeftOut.of(HAND_WRITTEN, mapping("json", List.of()), null)).isEmpty();
    }

    @Test
    void aBlankOrDuplicatedSampleFieldIsListedOnceAndBlankNever() {
        assertThat(FieldsLeftOut.of(HAND_WRITTEN, mapping("json", List.of()), List.of("src_ip", "", " ",
                "src_ip"))).containsExactly("src_ip");
    }

    @Test
    void aNameThatIsOnlyPartOfAQuotedLiteralIsNotRead() {
        // 'user' is read; 'use' is not, though its letters are in the code.
        assertThat(FieldsLeftOut.of(HAND_WRITTEN, mapping("json", List.of()), List.of("use", "user")))
                .containsExactly("use");
    }

    @Test
    void theMappingIgnoresTheFieldsLeftOutBesideItsOwn() {
        final TranslationMapping ignoring = FieldsLeftOut.ignoring(mapping("json", List.of("host")),
                List.of("src_ip", "host"));

        assertThat(ignoring.ignoredFields()).containsExactly("host", "src_ip");
        final TranslationMapping same = mapping("json", List.of("host"));
        assertThat(FieldsLeftOut.ignoring(same, List.of())).isSameAs(same);
    }

    @Test
    void whatWasLeftOutIsSaidPlainly() {
        assertThat(FieldsLeftOut.said(List.of("src_ip", "host"))).isEqualTo("left out, as the original: src_ip, "
                                                                             + "host");
    }

    private static TranslationMapping mapping(final String input, final List<String> ignored) {
        return TranslationMapping.parse(Map.of("input", input, "ignored_fields", ignored, "events", List.of(
                Map.of("name", "logon", "fields", List.of(Map.of("path", "EventDetail/TypeId", "value",
                        "Logon"))))));
    }
}
