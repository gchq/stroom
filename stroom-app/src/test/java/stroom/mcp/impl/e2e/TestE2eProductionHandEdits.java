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

import stroom.docref.DocRef;
import stroom.mcp.impl.McpTestClient;
import stroom.mcp.impl.tools.WriteGuard;
import stroom.pipeline.shared.XsltDoc;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/// A generated translation promoted to production, then edited by hand there; later the user asks the agent to
/// change the pipeline (here, to write the source's IP address too, as a fix for its Error streams would). The
/// change goes into a new version of the pipeline made in a new build (copy_pipeline; a promoted pipeline and the
/// code it runs are never changed in place, decided by the user 2026-11-17), carrying the hand edit over, and is
/// promoted beside production, which is left as it was. Driving the REAL tools on a real Stroom, for each kind of
/// hand edit (asked for by the user):
///
/// 1. XSL the generator doesn't write (an xsl:iterate): the agent's generated change is refused with the user's two
///    choices, the mapping can't be rebuilt from the XSLT, and only code written to keep the edit is saved;
/// 2. a field mapped differently: the generated change is refused until the edit is carried into the mapping
///    (rebuild_mapping, proven on the sample), then goes through with the edit kept;
/// 3. a field the mapping hasn't got: likewise.
///
/// Each time the new version ends with the edit and the change, and production with the edit alone. A production
/// edit made after the new version was copied stays in production, nothing asked; a working copy of production's
/// code left in a build opened before new versions became the only way is refused at promotion.
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
    // The new version's names, as copy_pipeline proposes them for names with no version token of their own (taken
    // as V1.0, as promotion files them).
    private static final String VERSION_PIPELINE = FEED + "-Events-V1.1";
    private static final String VERSION_XSLT = FEED + "-Events-V1.1";

    @Inject
    private WriteGuard guard;

    @Test
    void anEditNoMappingCanHoldIsKeptAndOnlyCodeKeepingItIsSaved() {
        final Production production = promoted();
        // Users written once each, by hand, with an xsl:iterate.
        final String iterate = "<xsl:iterate select=\"data[@name='user']\"><xsl:param name=\"seen\" select=\"()\"/>"
                               + "<xsl:if test=\"not(@value = $seen)\"><Data Name=\"seen\" Value=\"{@value}\"/>"
                               + "</xsl:if><xsl:next-iteration><xsl:with-param name=\"seen\" "
                               + "select=\"($seen, @value)\"/></xsl:next-iteration></xsl:iterate>";
        editInProduction(production, code -> code.replace("</Authenticate>", iterate + "</Authenticate>"));
        final Version version = newVersion(production, "prodedit-fix1");
        final String copy = version.xslt();

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
        assertThat(stepSample(version.pipeline(), List.of(production.raw())).get("verdict")).isEqualTo("clean");

        promote("prodedit-fix1");
        assertThat(readXslt(copy).getData()).as("the new version: the edit and the change")
                .contains("xsl:iterate", "data[@name='ip']");
        assertThat(readXslt(production.xslt()).getData()).as("production: the edit alone, left as it was")
                .contains("xsl:iterate")
                .doesNotContain("data[@name='ip']");
    }

    @Test
    void aFieldMappedDifferentlyIsCarriedIntoTheMappingThenTheChangeKeepsIt() {
        final Production production = promoted();
        // The user's id from the account, not the user name: the event's user only.
        editInProduction(production, code -> code.replaceFirst("data\\[@name='user'\\]", "data[@name='account']"));
        assertThat(readXslt(production.xslt()).getData()).contains("data[@name='account']");
        final Version version = carriedThenChanged(production, "prodedit-fix2");

        assertThat(readXslt(version.xslt()).getData()).as("the new version: the edit and the change")
                .contains("data[@name='account']", "data[@name='ip']");
        assertThat(readXslt(production.xslt()).getData()).as("production: the edit alone, left as it was")
                .contains("data[@name='account']")
                .doesNotContain("data[@name='ip']");
    }

    @Test
    void aFieldTheMappingHasNotGotIsCarriedIntoItThenTheChangeKeepsIt() {
        final Production production = promoted();
        // The account written as well, as a Data entry of the Authenticate.
        editInProduction(production, code -> code.replace("</Authenticate>",
                "<Data Name=\"account\" Value=\"{data[@name='account']/@value}\"/></Authenticate>"));
        final Version version = carriedThenChanged(production, "prodedit-fix3");

        assertThat(readXslt(version.xslt()).getData()).as("the new version: the edit and the change")
                .contains("account", "data[@name='ip']");
        assertThat(readXslt(production.xslt()).getData()).as("production: the edit alone, left as it was")
                .contains("account")
                .doesNotContain("data[@name='ip']");
    }

    @Test
    void aProductionEditMadeAfterTheNewVersionWasCopiedStaysInProductionNothingAsked() {
        // Found reviewing the tools: production edited by hand after its working copy was taken lost the edit when
        // the copy was promoted (but for the backup). A new version is promoted beside production, never over it,
        // so a later production edit stays there and nothing is put to the user.
        final Production production = promoted();
        final Version version = newVersion(production, "prodedit-fix4");
        final Map<String, Object> changed = agreed("build_translation_xslt", args("changes", CHANGE, "uuid",
                version.xslt(), "stream_ids", List.of(production.raw()), "change", "IP address written"));
        assertThat(changed.get("ok")).as("the change made: %s", changed.get("problems")).isEqualTo(true);
        assertThat(stepSample(version.pipeline(), List.of(production.raw())).get("verdict")).isEqualTo("clean");
        // Production is edited by hand after the new version was copied.
        editInProduction(production, code -> code.replace("<TypeId>Logon</TypeId>",
                "<TypeId>Logon (edited in production)</TypeId>"));

        final int formsBefore = client.forms().size();
        promote("prodedit-fix4");
        assertThat(formsSince(formsBefore)).noneSatisfy(form -> assertThat(form.message())
                .contains("was changed in production since"));
        assertThat(readXslt(production.xslt()).getData()).as("production as the user left it")
                .contains("Logon (edited in production)")
                .doesNotContain("data[@name='ip']");
        assertThat(readXslt(version.xslt()).getData()).as("the new version, promoted beside it")
                .contains("data[@name='ip']")
                .doesNotContain("Logon (edited in production)");
        assertThat(folderOf(version.xslt())).startsWith("System/Feeds/ProdEdit");
        assertThat(findFolder("System/MCP Workspace/prodedit-fix4")).as("promoted").isEmpty();
    }

    @Test
    void aWorkingCopyOfProductionCodeFromAnOlderBuildIsRefusedAtPromotion() {
        // A build opened before new versions became the only way (decided by the user, 2026-11-17) may hold a
        // working copy of a production XSLT, changed: promotion refuses it rather than writing it back over
        // production, and changes nothing.
        final Production production = promoted();
        final String build = "prodedit-old";
        final String original = readXslt(production.xslt()).getData();
        final DocRef copy = asUser(admin, () -> guard.create(XsltDoc.TYPE, FEED + "-Events", build,
                production.xslt(), false, "Events"));
        editXslt(copy.getUuid(), doc -> doc.copy().data(original.replace("<TypeId>Logon</TypeId>",
                "<TypeId>Logon (working copy)</TypeId>")).build());

        final String refused = refusal("promote_build", args("build", build, "destinations", DESTINATIONS));
        dump("promote_build working copy", refused);
        assertThat(refused).contains("XSLT '" + FEED + "-Events' is a working copy of a promoted pipeline's code",
                "make a new version", "copy_pipeline", "Nothing was changed");
        assertThat(readXslt(production.xslt()).getData()).as("production untouched").isEqualTo(original);
        assertThat(readXslt(copy.getUuid()).getData()).as("the copy, still in the build")
                .contains("Logon (working copy)");
    }

    // A change refused until the hand edit is carried into the mapping (rebuild_mapping, proven on the sample and
    // confirmed by the user), then made with the edit kept, in a new version promoted beside production.
    private Version carriedThenChanged(final Production production, final String build) {
        final Version version = newVersion(production, build);
        final String copy = version.xslt();
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
        assertThat(stepSample(version.pipeline(), List.of(production.raw())).get("verdict")).isEqualTo("clean");
        promote(build);
        return version;
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

    // A new version of the production pipeline in a new build, under the names proposed for it, which the user
    // agrees to: its pipeline and XSLT, the XSLT carrying production's hand edit over.
    private Version newVersion(final Production production, final String build) {
        final Map<String, Object> copy = agreed("copy_pipeline", args("build", build, "source_uuid",
                production.pipeline(), "new_name", VERSION_PIPELINE));
        dump("copy_pipeline " + build, copy);
        assertThat(copy).containsEntry("name", VERSION_PIPELINE)
                .containsEntry("copy_of", FEED + "-Events")
                .doesNotContainKey("working_copy");
        final Map<String, Object> xslt = ((List<Map<String, Object>>) copy.get("copied_documents")).stream()
                .filter(doc -> "XSLT".equals(doc.get("type")))
                .findFirst()
                .orElseThrow();
        assertThat(xslt).as("the XSLT under the new version's name").containsEntry("name", VERSION_XSLT);
        final String xsltUuid = (String) xslt.get("uuid");
        assertThat(xsltUuid).isNotEqualTo(production.xslt());
        assertThat(readXslt(xsltUuid).getData()).as("the new version holds the production edit")
                .isEqualTo(readXslt(production.xslt()).getData());
        client.answeringWith(McpTestClient::agree);
        return new Version(uuidOf(copy), xsltUuid);
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

    /// A new version of the production pipeline, in a build.
    ///
    /// @param pipeline The new version's pipeline.
    /// @param xslt     Its XSLT, a copy of production's.
    private record Version(String pipeline, String xslt) {

    }
}
