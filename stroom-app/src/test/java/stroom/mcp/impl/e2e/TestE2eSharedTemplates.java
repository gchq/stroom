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

package stroom.mcp.impl.e2e;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The environment's common elements written as its pipelines write them (asked for by the user: event pipelines
/// share standard imports that write, e.g., the event's device). Two event pipelines, children of the same template,
/// call a named template of a shared XSLT inside EventSource; a new build's drafted mapping calls it too, in place of
/// what the draft mapped there, and the XSLT generated from it steps clean. Driving the REAL tools on a real Stroom.
@SuppressWarnings("unchecked")
class TestE2eSharedTemplates extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    private static final String SHARED = "Shared-Device-" + STAMP;
    private static final String SAMPLE = """
            time,user,host,action
            2026-10-01T10:00:00,alice,ws01,login
            2026-10-01T10:05:00,bob,ws02,login
            """;
    // The shared XSLT: a named template writing the event's device from the record it is called on.
    private static final String SHARED_XSLT = """
            <xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xpath-default-namespace="records:2" version="3.0">
              <xsl:template name="sourceDevice">
                <Device>
                  <HostName><xsl:value-of select="data[@name='host']/@value"/></HostName>
                </Device>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void aNewBuildCallsTheSharedTemplateItsSiblingsCall() {
        final String siblings = "shared-sib-" + STAMP;
        final String template = templateUuid("translation", "Event Data (Text)");
        saveXslt(siblings, SHARED, SHARED_XSLT);
        final String splitter = saveTextConverter(siblings, "SIB-CSV-" + STAMP, CSV_SPLITTER);
        for (final String name : List.of("SIB-A-" + STAMP, "SIB-B-" + STAMP)) {
            final String xslt = saveXslt(siblings, name + "-XSLT", sibling(name));
            createPipeline(siblings, name + "-Events", template,
                    docProperty("dsParser", "textConverter", splitter, "TextConverter"),
                    docProperty("translationFilter", "xslt", xslt, "XSLT"));
        }

        final String build = "shared-new-" + STAMP;
        final String feed = "SHARED-NEW-" + STAMP;
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        final Map<String, Object> converter = map(agreed("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", feed + "-CSV", "build", build)), "saved");
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "build", build, "schema_version", VERSION));
        final Map<String, Object> mapping = (Map<String, Object>) draft.get("mapping");

        assertThat((List<Map<String, Object>>) mapping.get("shared")).as("the siblings' call, in the draft")
                .contains(Map.of("href", SHARED, "template", "sourceDevice", "at", "EventSource/Device"));
        assertThat(String.valueOf(draft.get("notes"))).contains("2 existing pipelines", "sourceDevice",
                "so this mapping calls it too (shared)");

        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", mapping, "stream_ids",
                List.of(raw), "build", build, "name", feed + "-Events", "schema_version", VERSION));
        assertThat(built.get("ok")).as("generated: %s", built.get("problems")).isEqualTo(true);
        final String xslt = (String) map(built, "saved").get("uuid");
        assertThat(readXslt(xslt).getData()).contains("<xsl:import href=\"" + SHARED + "\"",
                "<xsl:call-template name=\"sourceDevice\"");
        assertThat(String.valueOf(built.get("warnings"))).as("following its siblings, nothing to warn of")
                .doesNotContain("existing pipelines");
        final String pipeline = (String) createPipeline(build, feed + "-Events", template,
                docProperty("dsParser", "textConverter", (String) converter.get("uuid"), "TextConverter"),
                docProperty("translationFilter", "xslt", xslt, "XSLT")).get("uuid");
        assertThat(stepSample(pipeline, List.of(raw)).get("verdict")).isEqualTo("clean");
    }

    // A sibling's translation: it imports the shared XSLT and calls its template inside EventSource.
    private static String sibling(final String name) {
        return """
                <xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="records:2"
                    version="3.0">
                  <xsl:import href="%s"/>
                  <xsl:template match="records">
                    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
                      <xsl:apply-templates select="record"/>
                    </Events>
                  </xsl:template>
                  <xsl:template match="record">
                    <Event>
                      <EventTime><TimeCreated><xsl:value-of select="data[@name='time']/@value"/></TimeCreated>
                      </EventTime>
                      <EventSource>
                        <System><Name>%s</Name><Environment>Test</Environment></System>
                        <Generator>e2e</Generator>
                        <xsl:call-template name="sourceDevice"/>
                      </EventSource>
                      <EventDetail><TypeId>Logon</TypeId><Authenticate><Action>Logon</Action></Authenticate>
                      </EventDetail>
                    </Event>
                  </xsl:template>
                </xsl:stylesheet>
                """.formatted(SHARED, name);
    }
}
