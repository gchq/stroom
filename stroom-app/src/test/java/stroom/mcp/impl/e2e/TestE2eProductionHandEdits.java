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
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/// A generated translation promoted to production, then edited by hand there; later the user asks the agent to
/// change the pipeline (here, to write the source's IP address too, as a fix for its Error streams would). The
/// change goes through a working copy in a new build, written back over production by promoting it. Driving the
/// REAL tools on a real Stroom, for each kind of hand edit (asked for by the user):
///
/// 1. XSL the generator doesn't write (an xsl:iterate): the agent's generated change is refused with the user's two
///    choices, the mapping can't be rebuilt from the XSLT, and only code written to keep the edit is saved;
/// 2. a field mapped differently: the generated change is refused until the edit is carried into the mapping
///    (rebuild_mapping, proven on the sample), then goes through with the edit kept;
/// 3. a field the mapping hasn't got: likewise.
///
/// Each time production ends with the edit and the change.
@SuppressWarnings("unchecked")
class TestE2eProductionHandEdits extends AbstractE2eTest {

    private static final String BUILD = "prodedit";
    private static final String FEED = "PRODEDIT";
    private static final String SAMPLE = """
            time,user,account,host,ip,action
            2026-10-01T10:00:00,alice,a.smith,ws01,10.0.0.1,login
            2026-10-01T10:05:00,bob,b.jones,ws02,10.0.0.2,login
            """;
    private static final Map<String, Object> MAPPING = Map.of(
            "input", "data_splitter",
            "unmatched", "warn",
            "common", List.of(
                    Map.of("path", "EventTime/TimeCreated", "field", "time", "time_format", "yyyy-MM-dd'T'HH:mm:ss"),
                    Map.of("path", "EventSource/System/Name", "value", "ProdEdit"),
                    Map.of("path", "EventSource/System/Environment", "value", "Test"),
                    Map.of("path", "EventSource/Generator", "value", "e2e"),
                    Map.of("path", "EventSource/Device/HostName", "field", "host"),
                    Map.of("path", "EventSource/User/Id", "field", "user")),
            "events", List.of(Map.of("name", "login", "when", List.of(Map.of("field", "action", "equals", "login")),
                    "fields", List.of(
                            Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                            Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                            Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user")))));
    // The change the user asks for later: the source's IP address written too.
    private static final Map<String, Object> CHANGE = Map.of("common", List.of(Map.of("path",
            "EventSource/Device/IPAddress", "field", "ip")));
    private static final Map<String, Object> DESTINATIONS = Map.of("Feed", "Feeds/ProdEdit", "TextConverter",
            "Feeds/ProdEdit", "XSLT", "Feeds/ProdEdit", "Pipeline", "Feeds/ProdEdit", "Documentation",
            "Feeds/ProdEdit");

    @Test
    void anEditNoMappingCanHoldIsKeptAndOnlyCodeKeepingItIsSaved() {
        final Production production = promoted();
        // Users written once each, by hand, with an xsl:iterate.
        final String iterate = "<xsl:iterate select=\"data[@name='user']\"><xsl:param name=\"seen\" select=\"()\"/>"
                               + "<xsl:if test=\"not(@value = $seen)\"><Data Name=\"seen\" Value=\"{@value}\"/>"
                               + "</xsl:if><xsl:next-iteration><xsl:with-param name=\"seen\" "
                               + "select=\"($seen, @value)\"/></xsl:next-iteration></xsl:iterate>";
        editInProduction(production, code -> code.replace("</Authenticate>", iterate + "</Authenticate>"));
        final String copy = workingCopy(production, "prodedit-fix1");

        // The generated change: the user decides field by field and keeps their edit, so it is refused, with
        // their two choices.
        client.answeringWith(choosing("Decide field by field", "Keep my hand edit"));
        final String refused = refusal("build_translation_xslt", args("changes", CHANGE, "uuid", copy,
                "stream_ids", List.of(production.raw()), "change", "IP address written"));
        client.answeringWith(McpTestClient::agree);
        assertThat(refused).contains("edited by hand", "uses what no mapping can hold (", "iterate not read",
                        "keep the XSLT as it is (and make no generated change to it), or to drop the edit",
                        "discard_hand_edit=true")
                .doesNotContain("rebuild_mapping uuid=");
        // The mapping can't be rebuilt from it: what it couldn't read is named.
        final Map<String, Object> rebuilt = client.json("rebuild_mapping", args("uuid", copy, "stream_ids",
                List.of(production.raw())));
        assertThat(rebuilt.get("saved")).isNull();
        assertThat((List<String>) rebuilt.get("unread")).anySatisfy(note -> assertThat(note)
                .contains("iterate not read"));
        // The user keeps their edit: the agent writes the change into the code itself, keeping it.
        final String kept = readXslt(copy).getData().replace("</HostName>",
                "</HostName><IPAddress><xsl:value-of select=\"data[@name='ip']/@value\"/></IPAddress>");
        json("save_xslt", args("uuid", copy, "code", kept));
        assertThat(stepSample(production.pipeline(), List.of(production.raw())).get("verdict")).isEqualTo("clean");

        promote("prodedit-fix1");
        assertThat(readXslt(production.xslt()).getData()).as("production: the edit and the change")
                .contains("xsl:iterate", "data[@name='ip']");
    }

    @Test
    void aFieldMappedDifferentlyIsCarriedIntoTheMappingThenTheChangeKeepsIt() {
        final Production production = promoted();
        // The user's id from the account, not the user name: the event's user only.
        editInProduction(production, code -> code.replaceFirst("data\\[@name='user'\\]", "data[@name='account']"));
        assertThat(readXslt(production.xslt()).getData()).contains("data[@name='account']");
        carriedThenChanged(production, "prodedit-fix2");

        assertThat(readXslt(production.xslt()).getData()).as("production: the edit and the change")
                .contains("data[@name='account']", "data[@name='ip']");
    }

    @Test
    void aFieldTheMappingHasNotGotIsCarriedIntoItThenTheChangeKeepsIt() {
        final Production production = promoted();
        // The account written as well, as a Data entry of the Authenticate.
        editInProduction(production, code -> code.replace("</Authenticate>",
                "<Data Name=\"account\" Value=\"{data[@name='account']/@value}\"/></Authenticate>"));
        carriedThenChanged(production, "prodedit-fix3");

        assertThat(readXslt(production.xslt()).getData()).as("production: the edit and the change")
                .contains("account", "data[@name='ip']");
    }

    // A change refused until the hand edit is carried into the mapping (rebuild_mapping, proven on the sample and
    // confirmed by the user), then made with the edit kept; promoted back over production.
    private void carriedThenChanged(final Production production, final String build) {
        final String copy = workingCopy(production, build);
        final String refused = refusal("build_translation_xslt", args("changes", CHANGE, "uuid", copy,
                "stream_ids", List.of(production.raw()), "change", "IP address written"));
        assertThat(refused).contains("edited by hand", "carry it into the mapping", "rebuild_mapping uuid='" + copy)
                .doesNotContain("no mapping can hold");

        final Map<String, Object> rebuilt = agreed("rebuild_mapping", args("uuid", copy, "stream_ids",
                List.of(production.raw()), "change", "Hand edit in production carried into the mapping"));
        assertThat(rebuilt.get("saved")).as("rebuilt, proven on the sample: %s", rebuilt).isNotNull();
        assertThat(rebuilt).doesNotContainKey("unread");

        final Map<String, Object> changed = agreed("build_translation_xslt", args("changes", CHANGE, "uuid", copy,
                "stream_ids", List.of(production.raw()), "change", "IP address written"));
        assertThat(changed.get("ok")).as("the change made: %s", changed.get("problems")).isEqualTo(true);
        assertThat(stepSample(production.pipeline(), List.of(production.raw())).get("verdict")).isEqualTo("clean");
        promote(build);
    }

    // A translation built from the mapping, stepped clean, documented and promoted to production.
    private Production promoted() {
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
        agreed("write_documentation", args("build", BUILD, "pipeline_uuid", pipeline, "markdown",
                "## Purpose and data\n\nLogons.\n", "change", "Created", "stream_ids", List.of(raw)));
        promote(BUILD);
        assertThat(findFolder("System/MCP Workspace/" + BUILD)).as("promoted").isEmpty();
        return new Production(raw, pipeline, xslt);
    }

    // The user edits the production XSLT by hand in Stroom; it still steps clean.
    private void editInProduction(final Production production, final UnaryOperator<String> edit) {
        final String before = readXslt(production.xslt()).getData();
        final String after = edit.apply(before);
        assertThat(after).as("the edit applies").isNotEqualTo(before);
        editXslt(production.xslt(), doc -> doc.copy().data(after).build());
        assertThat(stepSample(production.pipeline(), List.of(production.raw())).get("verdict"))
                .as("the edited XSLT steps clean").isEqualTo("clean");
    }

    // A working copy of the production pipeline in a new build: its XSLT's uuid.
    private String workingCopy(final Production production, final String build) {
        final Map<String, Object> copy = agreed("copy_pipeline", args("build", build, "source_uuid",
                production.pipeline(), "new_name", FEED + "-Events-WORKING", "working_copy", true));
        final String xslt = (String) ((List<Map<String, Object>>) copy.get("copied_documents")).stream()
                .filter(doc -> "XSLT".equals(doc.get("type")))
                .findFirst()
                .orElseThrow()
                .get("uuid");
        assertThat(readXslt(xslt).getData()).as("the copy holds the production edit")
                .isEqualTo(readXslt(production.xslt()).getData());
        client.answeringWith(McpTestClient::agree);
        return xslt;
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees.
    private void promote(final String build) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                DESTINATIONS));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                DESTINATIONS, "reviewed", review.get("review_id")));
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
    }

    /// The promoted translation.
    ///
    /// @param raw      The sample's raw stream.
    /// @param pipeline The production pipeline.
    /// @param xslt     The production XSLT.
    private record Production(long raw, String pipeline, String xslt) {

    }
}
