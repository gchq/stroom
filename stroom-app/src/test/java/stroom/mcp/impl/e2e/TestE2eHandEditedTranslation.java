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

import stroom.mcp.impl.McpTestClient;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A translation XSLT saved from its mapping, then edited by hand in Stroom with what no mapping can hold (an
/// xsl:iterate, as users made distinct are written by hand): documented as it runs, by the mapping's rules; the
/// agent's next generated change refused with the user's two choices, not "carry it into the mapping"; and a rebuild
/// of the mapping refused, naming what it couldn't read. Driving the REAL tools on a real Stroom.
@SuppressWarnings("unchecked")
class TestE2eHandEditedTranslation extends AbstractE2eTest {

    private static final String BUILD = "handedit";
    private static final String FEED = "HANDEDIT";
    private static final String SAMPLE = """
            time,user,host,action
            2026-10-01T10:00:00,alice,ws01,login
            2026-10-01T10:05:00,bob,ws02,login
            """;
    private static final Map<String, Object> MAPPING = Map.of(
            "input", "data_splitter",
            "unmatched", "warn",
            "common", List.of(
                    Map.of("path", "EventTime/TimeCreated", "field", "time", "time_format", "yyyy-MM-dd'T'HH:mm:ss"),
                    Map.of("path", "EventSource/System/Name", "value", "HandEdit"),
                    Map.of("path", "EventSource/System/Environment", "value", "Test"),
                    Map.of("path", "EventSource/Generator", "value", "e2e"),
                    Map.of("path", "EventSource/Device/HostName", "field", "host"),
                    Map.of("path", "EventSource/User/Id", "field", "user")),
            "events", List.of(Map.of("name", "login", "when", List.of(Map.of("field", "action", "equals", "login")),
                    "fields", List.of(
                            Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                            Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                            Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user")))));
    // The hand edit: the TypeId changed, and each user written as a Data entry by an xsl:iterate.
    private static final String ITERATE = "<xsl:iterate select=\"data[@name='user']\">"
                                          + "<xsl:param name=\"seen\" select=\"()\"/>"
                                          + "<xsl:if test=\"not(@value = $seen)\"><Data Name=\"seen\" "
                                          + "Value=\"{@value}\"/></xsl:if>"
                                          + "<xsl:next-iteration><xsl:with-param name=\"seen\" "
                                          + "select=\"($seen, @value)\"/></xsl:next-iteration></xsl:iterate>";

    @Test
    void anEditNoMappingCanHoldIsDocumentedAsItRunsAndLeftForTheUserToKeepOrDrop() {
        createFeed(BUILD, FEED);
        final long raw = upload(FEED, SAMPLE);
        final Map<String, Object> converter = map(agreed("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", FEED + "-CSV", "build", BUILD)), "saved");
        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", MAPPING, "stream_ids",
                List.of(raw), "build", BUILD, "name", FEED + "-Events", "schema_version", VERSION));
        assertThat(built.get("ok")).as("XSLT saved: %s", built.get("problems")).isEqualTo(true);
        final String xslt = (String) map(built, "saved").get("uuid");
        final String pipeline = (String) createPipeline(BUILD, FEED + "-Events",
                templateUuid("translation", "Event Data (Text)"),
                docProperty("dsParser", "textConverter", (String) converter.get("uuid"), "TextConverter"),
                docProperty("translationFilter", "xslt", xslt, "XSLT")).get("uuid");
        assertThat(stepSample(pipeline, List.of(raw)).get("verdict")).isEqualTo("clean");

        // Edited by hand in Stroom.
        final String generated = readXslt(xslt).getData();
        assertThat(generated).contains("<TypeId>Logon</TypeId>", "</Authenticate>");
        editXslt(xslt, doc -> doc.copy().data(generated.replace("<TypeId>Logon</TypeId>",
                "<TypeId>Logon (edited)</TypeId>").replace("</Authenticate>", ITERATE + "</Authenticate>")).build());
        assertThat(stepSample(pipeline, List.of(raw)).get("verdict")).as("the edited XSLT steps clean")
                .isEqualTo("clean");

        // Documented as it runs: the edited TypeId and the Data the edit writes, each event by the mapping's rule.
        final Map<String, Object> documented = agreed("write_documentation", args("build", BUILD, "pipeline_uuid",
                pipeline, "markdown", "## Purpose and data\n\nLogons, edited by hand.\n", "change", "Created",
                "stream_ids", List.of(raw)));
        final String section = String.valueOf(documented.get("field_mapping"));
        assertThat(section).contains("Logon (edited)", "The values and counts above are of the XSLT as it runs")
                .doesNotContain("not written by a rule of this mapping");
        assertThat(section).as("the Data the edit writes, with its values").contains("seen").contains("alice");

        // The agent's next generated change: the user decides field by field and keeps their edit, so it is
        // refused, with the user's two choices (not carrying the edit into the mapping, which can't hold it).
        client.answeringWith(choosing("Decide field by field", "Keep my hand edit"));
        final String refused = refusal("build_translation_xslt", args("changes", Map.of("events", List.of(Map.of(
                        "name", "logout", "when", List.of(Map.of("field", "action", "equals", "logout")),
                        "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Logoff"),
                                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logoff"),
                                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user"))))),
                "uuid", xslt, "stream_ids", List.of(raw), "change", "Logouts"));
        assertThat(refused).contains("uses what no mapping can hold (", "iterate not read",
                        "keep the XSLT as it is (and make no generated change to it), or to drop the edit",
                        "discard_hand_edit=true")
                .doesNotContain("rebuild_mapping uuid=");

        client.answeringWith(McpTestClient::agree);
        // A rebuild of the mapping: refused, naming what it couldn't read; nothing stepped or saved.
        final Map<String, Object> rebuilt = client.json("rebuild_mapping", args("uuid", xslt, "stream_ids",
                List.of(raw)));
        assertThat(rebuilt.get("saved")).isNull();
        assertThat((List<String>) rebuilt.get("unread")).anySatisfy(note -> assertThat(note)
                .contains("iterate not read"));
        assertThat(readXslt(xslt).getData()).as("the hand edit kept").contains("Logon (edited)", "xsl:iterate");
    }
}
