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

import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.docref.DocRef;
import stroom.mcp.impl.McpTestClient;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A feed whose translation was written by hand in Stroom, never by the tools, and a build to change it (decided by
/// the user, 2026-11-24): after the survey, the build is offered to start from the existing pipeline; the user takes
/// it, so the pipeline is copied as a new version, its XSLT read back into a mapping (proven against the original on
/// the feed's records) and the catalogue read back from it confirmed, the agent adds the field the user asked for,
/// and the new version is promoted as V1.1 beside the original, which is left as it was. Driving the REAL tools on a
/// real Stroom, as the agent would by following `next`.
@SuppressWarnings("unchecked")
class TestE2eStartFromExistingPipeline extends AbstractE2eTest {

    private static final String FEED = "HANDVPN";
    private static final String FOLDER = "System/Feeds/HandVpn";
    private static final String ORIGINAL = FEED + "-Events";
    private static final String VERSION = FEED + "-Events-V1.1";
    private static final String BUILD = "handvpn-v1-1";
    // The hand-written translation: a template per event, matched by patterns on the record; the source IP isn't
    // translated.
    private static final String XSLT = textResource("hand_written_vpn.xsl");
    // A VPN's audit as a JSON array: logins (one failed), a logout, a setting changed, and a heartbeat the
    // translation leaves untranslated.
    private static final String SAMPLE = "["
            + "{\"ts\": \"2026-11-03T08:00:01.000Z\", \"action\": \"login\", \"user\": \"jbloggs\", "
            + "\"src_ip\": \"198.51.100.7\", \"host\": \"vpn-gw-1\", \"result\": \"success\"}, "
            + "{\"ts\": \"2026-11-03T08:02:45.000Z\", \"action\": \"login\", \"user\": \"asmith\", "
            + "\"src_ip\": \"198.51.100.9\", \"host\": \"vpn-gw-1\", \"result\": \"failure\"}, "
            + "{\"ts\": \"2026-11-03T12:10:00.000Z\", \"action\": \"logout\", \"user\": \"jbloggs\", "
            + "\"src_ip\": \"198.51.100.7\", \"host\": \"vpn-gw-1\"}, "
            + "{\"ts\": \"2026-11-03T09:30:00.000Z\", \"action\": \"config_change\", \"user\": \"admin\", "
            + "\"host\": \"vpn-gw-1\", \"setting\": \"idle_timeout\", \"new_value\": \"900\"}, "
            + "{\"ts\": \"2026-11-03T09:31:00.000Z\", \"action\": \"heartbeat\", \"host\": \"vpn-gw-1\"}]";
    // The change the user asks for: the source IP written too.
    private static final Map<String, Object> CHANGE = Map.of("common", List.of(Map.of("path",
            "EventSource/Client/IPAddress", "field", "src_ip")));
    private static final Map<String, Object> DESTINATIONS = Map.of("XSLT", "Feeds/HandVpn", "Pipeline",
            "Feeds/HandVpn", "Documentation", "Feeds/HandVpn");

    @Inject
    private PipelineStore pipelineStore;

    @Test
    void aHandWrittenPipelineIsInductedChangedAndPromotedAsItsNextVersion() {
        final HandWritten original = handWritten();

        // 1. The build works from the feed; the survey comes first, then the plan says the catalogue's first call
        // asks how the build starts.
        // start_build's advice is the survey, never a copy (eval case 97, 2026-11-25: both models copied the
        // hand-written pipeline straight from it, skipping the survey and the catalogue's offer).
        final Map<String, Object> started = json("start_build", args("build", BUILD, "feeds", List.of(FEED)));
        dump("start_build", started.get("next"));
        assertThat(String.valueOf(map(started, "next").get("then")))
                .contains("is translated now by '" + ORIGINAL + "'", "survey_feed build=" + BUILD + " feed=" + FEED
                                                                     + " first", "catalogue_event_types")
                .doesNotContain("copy_pipeline source_uuid", "rebuild_mapping");
        final Map<String, Object> survey = json("survey_feed", args("feed", FEED, "build", BUILD));
        dump("survey_feed", survey);
        final Map<String, Object> status = json("build_status", args("build", BUILD));
        dump("build_status after the survey", status.get("next"));
        assertThat(nextTool(status)).isEqualTo(CATALOGUE);
        assertThat(String.valueOf(map(status, "next").get("then"))).contains("how the build starts",
                "'" + ORIGINAL + "'");

        // 2. The user starts from the existing pipeline: the form says who wrote it and what it translates.
        client.answeringWith(choosing("Start from the existing pipeline (recommended)"));
        final int formsBefore = client.forms().size();
        final Map<String, Object> chosen = json(CATALOGUE, args("build", BUILD));
        client.answeringWith(McpTestClient::agree);
        dump("catalogue_event_types start", chosen);
        assertThat(chosen).containsEntry("status", "start_from_existing");
        assertThat(formsSince(formsBefore)).anySatisfy(form -> assertThat(form.message())
                .contains("An existing pipeline already translates this feed: '" + ORIGINAL + "'",
                        "not built by Stroom AI: its XSLT was written by hand", "4 event types",
                        "the original isn't touched"));
        assertThat(nextTool(chosen)).isEqualTo("copy_pipeline");

        // 3. Copied as a new version, under the names the user confirms.
        final Map<String, Object> copy = agreed("copy_pipeline", args("build", BUILD, "source_uuid",
                original.pipeline(), "new_name", VERSION));
        dump("copy_pipeline", copy);
        assertThat(copy).containsEntry("name", VERSION);
        final String copyXslt = ((List<Map<String, Object>>) copy.get("copied_documents")).stream()
                .filter(doc -> "XSLT".equals(doc.get("type")))
                .map(doc -> (String) doc.get("uuid"))
                .findFirst()
                .orElseThrow();
        final String copyPipeline = uuidOf(copy);
        assertThat(nextTool(copy)).isEqualTo("rebuild_mapping");

        // 4. Its XSLT read back, proven against the original on the feed's records, the catalogue read back with it.
        final Map<String, Object> rebuilt = agreed("rebuild_mapping", args("uuid", copyXslt, "pipeline_uuid",
                copyPipeline, "stream_ids", List.of(original.raw()), "change",
                "Read back from the hand-written translation"));
        dump("rebuild_mapping", rebuilt);
        assertThat(rebuilt.get("saved")).as("rebuilt and proven: %s", rebuilt).isNotNull();
        assertThat(rebuilt).doesNotContainKeys("differences", "unread");
        assertThat(String.valueOf(map(rebuilt, "rebuilt").get("written_by_hand")))
                .contains("a template per event (match patterns)");
        // The original's run over the sample: the heartbeat writes no Event.
        assertThat(map(rebuilt, "existing_run")).containsEntry("records", 5).containsEntry("events", 4)
                .containsEntry("no_event", 1);
        assertThat((List<String>) map(rebuilt, "catalogue").get("event_types"))
                .containsExactlyInAnyOrder("vpn-login", "vpn-login-failed", "vpn-logout", "vpn-config-change");
        // The sample's field the original never reads is left out on purpose, as the original leaves it (decided by
        // the user, 2026-11-25; seen in eval case 105): the regenerated XSLT doesn't log it as a field no rule reads.
        assertThat(String.valueOf(rebuilt.get("left_out"))).startsWith("left out, as the original: src_ip ");
        final Map<String, Object> keptMapping = (Map<String, Object>) KeptMapping.readMapping(
                readXslt(copyXslt).getDescription()).orElseThrow().payload().get("mapping");
        assertThat((List<String>) keptMapping.get("ignored_fields")).containsExactly("src_ip");

        // 5. The catalogue read back, checked against the sample and confirmed, its summary naming its origin.
        final Map<String, Object> afterRebuild = json("build_status", args("build", BUILD));
        assertThat(nextTool(afterRebuild)).isEqualTo(CATALOGUE);
        final Map<String, Object> listed = json(CATALOGUE, args("build", BUILD, "stream_ids",
                List.of(original.raw())));
        dump("catalogue listed", listed);
        assertThat(String.valueOf(listed.get("origin"))).startsWith("4 event types from '" + ORIGINAL + "'");
        final Map<String, Object> shown = json(CATALOGUE, args("build", BUILD, "confirm", true));
        dump("catalogue shown", shown);
        assertThat(String.valueOf(shown.get("table"))).startsWith("4 event types from '" + ORIGINAL + "'");
        confirmedCatalogue(BUILD);
        assertThat(catalogueOf(BUILD).toString()).contains("vpn-login", "vpn-config-change");

        // 6. The change the user asked for: the source IP, written as the mapping says.
        final Map<String, Object> changed = agreed("build_translation_xslt", args("uuid", copyXslt, "changes",
                CHANGE, "stream_ids", List.of(original.raw()), "change", "Source IP written"));
        dump("build_translation_xslt", changed);
        assertThat(changed.get("ok")).as("the change made: %s", changed.get("problems")).isEqualTo(true);
        assertThat(stepSample(copyPipeline, List.of(original.raw())).get("verdict")).isEqualTo("clean");
        // V1.1 writes what V1.0 writes, the source IP aside.
        final List<Integer> records = List.of(0, 1, 2, 3, 4);
        assertThat(eventsWithoutClient(copyPipeline, original.raw(), records))
                .isEqualTo(eventsOf(original.pipeline(), original.raw(), records));
        assertThat(String.join("\n", eventsOf(copyPipeline, original.raw(), List.of(0))))
                .contains("198.51.100.7");

        // 7. Documented and promoted as V1.1 beside the original, which is left as it was.
        final Map<String, Object> documentation = new LinkedHashMap<>(args("build", BUILD, "pipeline_uuid",
                copyPipeline, "markdown", "## Purpose and data\n\nVPN sign-ins and settings.\n", "change",
                "Source IP written", "stream_ids", List.of(original.raw())));
        Map<String, Object> written = agreed("write_documentation", documentation);
        if ("needs_descriptions".equals(written.get("status"))) {
            documentation.put("field_descriptions", described(list(written, "fields")));
            written = agreed("write_documentation", documentation);
        }
        assertThat(written.get("uuid")).as("documented: %s", written).isNotNull();
        final Map<String, Object> review = json("promote_build", args("build", BUILD, "destinations",
                DESTINATIONS));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", BUILD, "destinations",
                DESTINATIONS, "reviewed", review.get("review_id")));
        dump("promote_build", promoted);
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
        assertThat(readXslt(original.xslt()).getData()).as("the original, untouched").isEqualTo(XSLT);
        assertThat(readXslt(copyXslt).getName()).isEqualTo(VERSION);
        assertThat(readXslt(copyXslt).getData()).contains("src_ip");
        assertThat(folderOf(copyXslt)).startsWith(FOLDER);
        // The build's survey doc is kept in the workspace at promotion (its feed's lasting survey); the pipeline
        // moved out.
        assertThat(folderOf(copyPipeline)).startsWith(FOLDER);
    }

    @Test
    void aCopysXsltThatCantBeReadBackIsDraftedAnewIntoTheCopy() {
        // Decided by the user (2026-11-25): rebuild_mapping refusing (the XSLT makes its events in a function, no
        // template per record) lifts the read-back gate; the plan says why and offers drafting the translation anew,
        // draft_translation_mapping allowed for the build, saved into the copy (a child of the same template).
        final String feed = "UNREADVPN";
        final String build = "unreadvpn-v1-1";
        final HandWritten original = handWritten("System/Feeds/UnreadVpn", feed,
                textResource("hand_written_vpn_unreadable.xsl"), feed + "-Events", "Event Data (JSON)",
                List.of(PipelineProperty.builder()
                        .element("jsonParser")
                        .name("addRootObject")
                        .value(new PipelinePropertyValue(false))
                        .build()), "Raw Events", SAMPLE);
        json("start_build", args("build", build, "feeds", List.of(feed)));
        json("survey_feed", args("feed", feed, "build", build));
        client.answeringWith(choosing("Start from the existing pipeline (recommended)"));
        assertThat(json(CATALOGUE, args("build", build))).containsEntry("status", "start_from_existing");
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> copy = agreed("copy_pipeline", args("build", build, "source_uuid",
                original.pipeline(), "new_name", feed + "-Events-V1.1"));
        final String copyXslt = ((List<Map<String, Object>>) copy.get("copied_documents")).stream()
                .filter(doc -> "XSLT".equals(doc.get("type")))
                .map(doc -> (String) doc.get("uuid"))
                .findFirst()
                .orElseThrow();
        final String copyPipeline = uuidOf(copy);
        // Before the read-back, nothing is drafted.
        assertThat(refusal("draft_translation_mapping", args("build", build, "stream_ids",
                List.of(original.raw())))).contains("rebuild_mapping", "Nothing drafted");

        // The read-back fails: recorded, the gate lifted, and why said.
        final Map<String, Object> rebuilt = json("rebuild_mapping", args("uuid", copyXslt, "pipeline_uuid",
                copyPipeline, "stream_ids", List.of(original.raw())));
        dump("rebuild_mapping, unreadable", rebuilt);
        assertThat(String.valueOf(rebuilt.get("read_back"))).startsWith("failed: ");
        assertThat(String.valueOf(rebuilt.get("hint"))).contains("drafts its translation anew");
        final Map<String, Object> status = json("build_status", args("build", build));
        dump("build_status after the failed read-back", status);
        assertThat(String.valueOf(status.get("read_back"))).contains("'" + feed + "-Events-V1.1'",
                "drafted anew into the copy, a child of the same template");
        assertThat(nextTool(status)).isEqualTo(CATALOGUE);
        // The catalogue, gated on the read-back before, is made as for any new translation.
        proposed(build, List.of(original.raw()), List.of(
                eventType("Logon-VPN", "Authenticate", "high", when("action", "equals", "login")),
                eventType("Logoff-VPN", "Authenticate", "high", when("action", "equals", "logout")),
                eventType("Change-Setting", "Update", "high", when("action", "equals", "config_change"))));
        confirmedCatalogue(build);
        assertThat(nextTool(json("build_status", args("build", build)))).isEqualTo("draft_translation_mapping");

        // Drafted anew and saved into the copy's XSLT, the copy still the original template's child.
        final Map<String, Object> draft = json("draft_translation_mapping", args("build", build, "stream_ids",
                List.of(original.raw()), "system_name", "Example VPN", "environment", "Production"));
        assertThat(draft).as("drafted: %s", draft).doesNotContainKey("error");
        final Map<String, Object> saved = agreed("build_translation_xslt", args("uuid", copyXslt, "build", build,
                "mapping", timeChosen(draftedMapping(draft)), "schema_version", draft.get("schema_version"),
                "stream_ids", List.of(original.raw()), "change", "Drafted anew: the copy couldn't be read back"));
        dump("build_translation_xslt into the copy", saved);
        assertThat(saved.get("ok")).as("saved: %s", saved.get("problems")).isEqualTo(true);
        assertThat(KeptMapping.readMapping(readXslt(copyXslt).getDescription())).isPresent();
        assertThat(readXslt(original.xslt()).getData()).as("the original, untouched")
                .isEqualTo(textResource("hand_written_vpn_unreadable.xsl"));
        assertThat(parentOf(copyPipeline)).isEqualTo(parentOf(original.pipeline()));
        final Map<String, Object> after = json("build_status", args("build", build));
        assertThat(after).doesNotContainKey("read_back");
    }

    // The template a pipeline inherits, by UUID.
    private String parentOf(final String pipeline) {
        return asUser(admin, () -> pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, pipeline))
                .getParentPipeline().getUuid());
    }

    // The feed and its translation as an environment made them by hand, never through the tools.
    private HandWritten handWritten() {
        return handWritten(FOLDER, FEED, XSLT, ORIGINAL, "Event Data (JSON)", List.of(PipelineProperty.builder()
                .element("jsonParser")
                .name("addRootObject")
                .value(new PipelinePropertyValue(false))
                .build()), "Raw Events", SAMPLE);
    }

    // Each record's Events from a pipeline, canonical, with the source IP (EventSource/Client) taken out.
    private List<String> eventsWithoutClient(final String pipeline, final long stream, final List<Integer> records) {
        final List<String> out = new ArrayList<>();
        for (final int record : records) {
            final Element root = parse(translationOutput(pipeline, stream, record)).getDocumentElement();
            for (final Element event : children(root, "Event")) {
                for (final Element source : children(event, "EventSource")) {
                    for (final Element client : children(source, "Client")) {
                        source.removeChild(client);
                    }
                }
                out.add(canonical(event));
            }
        }
        return out;
    }
}
