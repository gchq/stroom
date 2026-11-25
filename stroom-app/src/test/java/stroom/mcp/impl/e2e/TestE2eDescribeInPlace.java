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

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A hand-written translation described in place (decided with the user 2026-11-25): its translation XSLT read into
/// a mapping and a catalogue of event types without a new version, written without asking where the user may (only
/// the tools' hidden blocks change: the XSLT's code and the user's own description text are kept), the catalogue in
/// a source notes doc beside the pipeline, checked against every feed of every pipeline using the XSLT (the count
/// and an estimate stated first), each event type with its timeline role. Driving the real tools on a real Stroom,
/// as the agent would.
@SuppressWarnings("unchecked")
class TestE2eDescribeInPlace extends AbstractE2eTest {

    private static final String IN_PLACE = "Describe it in place (no new version)";
    // The hand-written translation: a template per event, matched on the record's action.
    private static final String XSLT = textResource("hand_written_vpn.xsl");
    // The user's own words about the translation, in its description.
    private static final String USERS_TEXT = "Owned by the network team: ask in #vpn before changing it.";
    private static final String SAMPLE = "["
            + "{\"ts\": \"2026-11-03T08:00:01.000Z\", \"action\": \"login\", \"user\": \"jbloggs\", "
            + "\"host\": \"vpn-gw-1\", \"result\": \"success\"}, "
            + "{\"ts\": \"2026-11-03T08:02:45.000Z\", \"action\": \"login\", \"user\": \"asmith\", "
            + "\"host\": \"vpn-gw-1\", \"result\": \"failure\"}, "
            + "{\"ts\": \"2026-11-03T12:10:00.000Z\", \"action\": \"logout\", \"user\": \"jbloggs\", "
            + "\"host\": \"vpn-gw-1\"}, "
            + "{\"ts\": \"2026-11-03T09:30:00.000Z\", \"action\": \"config_change\", \"user\": \"admin\", "
            + "\"host\": \"vpn-gw-1\", \"setting\": \"idle_timeout\", \"new_value\": \"900\"}]";
    // The same events as the device writes them before a pre-processor: operation codes, no action.
    private static final String CODED_SAMPLE = SAMPLE.replace("\"action\": \"login\"", "\"op\": \"LGN\"")
            .replace("\"action\": \"logout\"", "\"op\": \"LGT\"")
            .replace("\"action\": \"config_change\"", "\"op\": \"CFG\"");
    // A pre-processor before the translation: each operation code written as the action the translation reads.
    private static final String PRE_PROCESS = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xmlns="http://www.w3.org/2013/XSL/json" xpath-default-namespace="http://www.w3.org/2013/XSL/json" \
            version="3.0">
              <xsl:template match="@*|node()"><xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>\
            </xsl:template>
              <xsl:template match="map[string[@key='op']]">
                <map>
                  <xsl:apply-templates select="@*|node()"/>
                  <string key="action"><xsl:value-of select="if (string[@key='op'] = 'LGN') then 'login' \
            else if (string[@key='op'] = 'LGT') then 'logout' else if (string[@key='op'] = 'CFG') \
            then 'config_change' else 'other'"/></string>
                </map>
              </xsl:template>
            </xsl:stylesheet>""";

    @Inject
    private DocumentAccess documentAccess;
    @Inject
    private PipelineStore pipelineStore;

    @Test
    void aHandWrittenTranslationIsDescribedInPlaceItsTextKeptAndItsCatalogueBesideIt() {
        final HandWritten vpn = vpn("System/Feeds/PlaceVpn", "PLACEVPN", SAMPLE);
        editXslt(vpn.xslt(), doc -> doc.copy().description(USERS_TEXT).build());
        final String build = "placevpn-described";
        json("start_build", args("build", build, "feeds", List.of("PLACEVPN")));
        json("survey_feed", args("feed", "PLACEVPN", "build", build));

        // The user describes it in place: the choice says what that covers.
        client.answeringWith(choosing(IN_PLACE));
        final int formsBefore = client.forms().size();
        final Map<String, Object> described = json(CATALOGUE, args("build", build));
        client.answeringWith(McpTestClient::agree);
        dump("described in place", described);
        assertThat(described).as("described: %s", described).containsEntry("status", "described_in_place")
                .doesNotContainKey("not_written");
        assertThat(formsSince(formsBefore)).anySatisfy(form -> assertThat(form.message())
                .contains(IN_PLACE, "no new version", "It runs in 1 pipeline ('PLACEVPN-Events')",
                        "PLACEVPN (Raw Events): 1 stream"));

        // Its XSLT: the code as it was, the user's text first, the hidden mapping and link after it.
        final XsltDoc xslt = readXslt(vpn.xslt());
        assertThat(xslt.getData()).isEqualTo(XSLT);
        assertThat(xslt.getDescription()).startsWith(USERS_TEXT);
        assertThat(KeptMapping.readMapping(xslt.getDescription())).isPresent();
        final DocRef notes = KeptMapping.readSourceNotes(xslt.getDescription()).orElseThrow();
        assertThat(folderOf(notes.getUuid())).isEqualTo("System/Feeds/PlaceVpn");

        // The catalogue read back, checked against the feed's records, confirmed, and written back beside it.
        final Map<String, Object> listed = json(CATALOGUE, args("build", build, "stream_ids", List.of(vpn.raw())));
        dump("catalogue listed", listed);
        assertThat(countsOf(listed)).containsEntry("vpn-login", 1L).containsEntry("vpn-logout", 1L);
        final Map<String, Object> confirmed = confirmedCatalogue(build);
        assertThat(map(confirmed, "in_place")).containsKey("notes");
        final Map<String, Object> beside = catalogueNear("System/Feeds/PlaceVpn");
        assertThat(list(beside, "event_types")).extracting(type -> type.get("type_id"))
                .contains("vpn-login", "vpn-logout", "vpn-config-change");
        assertThat(list(beside, "event_types")).allSatisfy(type -> assertThat(type).containsEntry("status",
                "confirmed"));
        assertThat((List<String>) beside.get("checked_feeds")).containsExactly("PLACEVPN");
        // No new version: no pipeline or XSLT of the build's own, nothing to promote.
        final Map<String, Object> status = json("build_status", args("build", build));
        assertThat(status).containsKey("in_place").doesNotContainKey("next");
        assertThat(readXslt(vpn.xslt()).getData()).isEqualTo(XSLT);
    }

    @Test
    void anIndexBuiltOnAPipelineDescribedInPlaceIsPromotedAloneThePipelineLeftWhereItIs() {
        // Decided by the user (2026-11-25): an in-place build may build an index on the described pipeline;
        // promote_build moves only that, the pipeline and its XSLT "described in place, not promoted".
        final HandWritten vpn = vpn("System/Feeds/IdxVpn", "IDXVPN", SAMPLE);
        final String build = "idxvpn-described";
        json("start_build", args("build", build, "feeds", List.of("IDXVPN")));
        json("survey_feed", args("feed", "IDXVPN", "build", build));
        client.answeringWith(choosing(IN_PLACE));
        assertThat(json(CATALOGUE, args("build", build))).containsEntry("status", "described_in_place");
        client.answeringWith(McpTestClient::agree);
        json(CATALOGUE, args("build", build, "stream_ids", List.of(vpn.raw())));
        confirmedCatalogue(build);
        // Nothing new yet: nothing to promote.
        assertThat(refusal("promote_build", args("build", build))).contains("Nothing to promote",
                "has made nothing new");

        // The hand-written pipeline's own Events, indexed by the build into a Lucene index of its own.
        processAll();
        final List<Long> events = streamIdsOf("IDXVPN").stream()
                .filter(id -> "Events".equals(meta(id).getTypeName()))
                .toList();
        assertThat(events).as("the hand-written pipeline's Events").isNotEmpty();
        json("get_field_conventions", args("name", "stroom-flat"));
        final String indexName = "E2E-IDXVPN-" + Long.toHexString(System.nanoTime()).toUpperCase(Locale.ROOT);
        final Map<String, Object> plan = map(json("draft_index_mapping", args("backend", "lucene", "index_name",
                indexName, "convention", "stroom-flat", "events_stream_ids", events)), "plan");
        final Map<String, Object> index = agreed("create_index_doc", args("build", build, "backend", "lucene",
                "name", indexName, "time_field", plan.get("time_field")));
        final String indexXslt = uuidOf(json("save_xslt", args("build", build, "name", indexName + "-XSLT",
                "index_plan", plan)));
        final Map<String, Object> template = templates("indexing").stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend")))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> indexing = agreed("create_indexing_pipeline", args("build", build, "name",
                indexName + " - Indexing", "template_uuid", template.get("uuid"), "xslt_uuid", indexXslt,
                "index_uuid", uuidOf(index), "events_stream_ids", events));
        assertThat(json("step_sample", args("pipeline_uuid", uuidOf(indexing), "stream_ids", events)))
                .containsEntry("verdict", "clean");
        // Its sample indexed from the described pipeline's Events.
        agreed("create_processor_filter", args("pipeline_uuid", uuidOf(indexing), "stream_ids", events,
                "source_pipeline_uuid", vpn.pipeline()));
        processAll();
        flushIndexShards();
        // The plan goes on to the index's steps and promotion, not "nothing to promote".
        final Map<String, Object> status = json("build_status", args("build", build));
        assertThat(status).doesNotContainKey("in_place").containsKey("next");

        // The review: the index and its pipeline laid out, the described pipeline and XSLT said not promoted.
        final ExplorerNode promotedFolder = folder("System/E2E Described Index " + Long.toHexString(
                System.nanoTime()));
        final Map<String, Object> destinations = args("Feed", "System/Feeds/IdxVpn", "Index",
                "System/" + promotedFolder.getName(), "indexing", "System/" + promotedFolder.getName());
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        dump("in-place review", review);
        assertThat(review).containsEntry("status", "needs_review");
        assertThat(String.valueOf(review.get("layout"))).contains(indexName + " - Indexing  (Pipeline)")
                .doesNotContain("IDXVPN-Events");
        // Once promoted, it goes on indexing the described pipeline's Events.
        assertThat(String.valueOf(review.get("processing_after_promotion"))).contains(indexName + " - Indexing",
                "IDXVPN-Events");
        assertThat((List<String>) review.get("described_in_place")).containsExactly(
                "Pipeline 'IDXVPN-Events': described in place, not promoted",
                "XSLT 'IDXVPN-Events': described in place, not promoted");

        final Map<String, Object> promoted = agreed("promote_build", args("build", build, "destinations",
                destinations));
        dump("in-place promoted", promoted);
        assertThat(String.valueOf(promoted.get("promoted"))).contains(indexName + " - Indexing")
                .doesNotContain("IDXVPN-Events");
        // The described pipeline and its XSLT where they were, the XSLT's code as it was.
        assertThat(folderOf(vpn.pipeline())).isEqualTo("System/Feeds/IdxVpn");
        assertThat(folderOf(vpn.xslt())).isEqualTo("System/Feeds/IdxVpn");
        assertThat(readXslt(vpn.xslt()).getData()).isEqualTo(XSLT);
        assertThat(folderOf(uuidOf(indexing))).startsWith("System/" + promotedFolder.getName());
    }

    @Test
    void aTranslationAfterAPreProcessorIsReadFromTheTranslationAndItsRecordsAtItsInput() {
        // The raw records lack the actions the translation reads: clean only once the pre-processor is in.
        final HandWritten vpn = handWritten("System/Feeds/PreDescribed", "PREDESC", XSLT, "PREDESC-Events",
                "Event Data (JSON)", List.of(addRootObject()), "Raw Events", CODED_SAMPLE, false);
        final String pre = create(XsltDoc.TYPE, "PREDESC-Codes", "System/Feeds/PreDescribed").getUuid();
        editXslt(pre, doc -> doc.copy().data(PRE_PROCESS).build());
        withPreProcessor(vpn.pipeline(), pre, vpn.xslt(), List.of(addRootObject()));
        assertThat(stepSample(vpn.pipeline(), List.of(vpn.raw())).get("verdict")).isEqualTo("clean");
        final String build = "predesc-described";
        json("start_build", args("build", build, "feeds", List.of("PREDESC")));
        json("survey_feed", args("feed", "PREDESC", "build", build));

        client.answeringWith(choosing(IN_PLACE));
        final Map<String, Object> described = json(CATALOGUE, args("build", build));
        client.answeringWith(McpTestClient::agree);
        dump("described in place", described);
        assertThat(described).as("described: %s", described).containsEntry("status", "described_in_place");
        // The translation's XSLT keeps the mapping; the pre-processor before it is never taken for it.
        assertThat(KeptMapping.readMapping(readXslt(vpn.xslt()).getDescription())).isPresent();
        assertThat(KeptMapping.readMapping(readXslt(pre).getDescription())).isEmpty();
        assertThat(String.valueOf(described.get("event_types"))).contains("vpn-login", "vpn-logout");

        // The survey and the catalogue read the records where the translation reads them: the actions the
        // pre-processor writes, which the raw records lack.
        // The raw survey made before the choice no longer counts: the plan's next surveys again, no streams named.
        final Map<String, Object> status = json("build_status", args("build", build));
        assertThat(nextTool(status)).isEqualTo("survey_feed");
        final Map<String, Object> surveyed = json("survey_feed", args("feed", "PREDESC", "build", build));
        assertThat(surveyed).containsEntry("read_at", "translation_input");
        // Surveyed at the translation's input: the plan moves on to the catalogue (seen in eval case 106 on Haiku:
        // the build's combined survey left out where it read, and the plan asked for the survey again).
        assertThat(nextTool(json("build_status", args("build", build)))).isEqualTo(CATALOGUE);
        dump("survey at the translation's input", surveyed);
        assertThat(String.valueOf(surveyed.get("shapes"))).contains("action");
        final Map<String, Object> listed = json(CATALOGUE, args("build", build, "stream_ids", List.of(vpn.raw())));
        dump("catalogue at the translation's input", listed);
        assertThat(String.valueOf(listed.get("read_with"))).contains("the translation's input");
        assertThat(countsOf(listed)).containsEntry("vpn-login", 1L).containsEntry("vpn-logout", 1L)
                .containsEntry("vpn-config-change", 1L);
    }

    @Test
    void aSharedXsltServingTwoFeedsIsSurveyedOverBothTheCountStatedFirst() {
        final HandWritten first = vpn("System/Feeds/SharedA", "SHVPN-A", SAMPLE);
        // A second pipeline, in another folder, naming the same XSLT for another feed.
        final ExplorerNode folder = folder("System/Feeds/SharedB");
        create(FeedDoc.TYPE, "SHVPN-B", folder);
        final long rawB = storeParts("SHVPN-B", List.of(Map.entry(Map.of("Feed", "SHVPN-B"), SAMPLE)));
        final String second = childPipeline(folder, "SHVPN-B-Events", templateUuid("translation",
                "Event Data (JSON)"), List.of(property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE,
                first.xslt())), PipelineProperty.builder().element("jsonParser").name("addRootObject")
                .value(new PipelinePropertyValue(false)).build()));
        feedFilter("SHVPN-B", "Raw Events", second);
        final String build = "shvpn-described";
        json("start_build", args("build", build, "feeds", List.of("SHVPN-A")));
        json("survey_feed", args("feed", "SHVPN-A", "build", build));

        // The choice states both pipelines and both feeds first; the user keeps both in the survey.
        client.answeringWith(choosing(IN_PLACE));
        final int formsBefore = client.forms().size();
        final Map<String, Object> described = json(CATALOGUE, args("build", build));
        client.answeringWith(McpTestClient::agree);
        dump("described in place", described);
        assertThat(formsSince(formsBefore)).anySatisfy(form -> assertThat(form.message())
                .contains("It runs in 2 pipelines", "'SHVPN-A-Events'", "'SHVPN-B-Events'",
                        "whose filters read 2 feeds", "SHVPN-A (Raw Events): 1 stream",
                        "SHVPN-B (Raw Events): 1 stream", "survey call"));
        assertThat(formsSince(formsBefore)).anySatisfy(form -> assertThat(String.valueOf(form.requestedSchema()))
                .contains("Which feeds should the survey cover?", "All 2 feeds"));
        assertThat(described).containsEntry("status", "described_in_place")
                .containsEntry("feeds", List.of("SHVPN-A", "SHVPN-B"));
        // Both are the build's to survey; each is surveyed.
        assertThat(String.valueOf(json("build_status", args("build", build)).get("works_from")))
                .contains("SHVPN-A", "SHVPN-B");
        json("survey_feed", args("feed", "SHVPN-B", "build", build));

        final Map<String, Object> listed = json(CATALOGUE, args("build", build, "stream_ids", List.of(first.raw(),
                rawB)));
        assertThat(countsOf(listed)).containsEntry("vpn-login", 2L);
        confirmedCatalogue(build);
        assertThat((List<String>) catalogueNear("System/Feeds/SharedA").get("checked_feeds"))
                .containsExactly("SHVPN-A", "SHVPN-B");
    }

    @Test
    void eachEventTypesTimelineRoleIsFilledFromTheSchemaAndTheRestAsked() {
        // A session switch the VPN writes as an Authenticate Action the schema's table doesn't name
        // (hand_written_vpn_switch.xsl): its role is the user's to say (here, a focus).
        final String switching = textResource("hand_written_vpn_switch.xsl");
        final String sample = SAMPLE.replace("]", ", {\"ts\": \"2026-11-03T12:00:00.000Z\", \"action\": "
                                                  + "\"switch\", \"user\": \"jbloggs\", \"host\": \"vpn-gw-1\"}]");
        final HandWritten vpn = handWritten("System/Feeds/RoleVpn", "ROLEVPN", switching, "ROLEVPN-Events",
                "Event Data (JSON)", List.of(addRootObject()), "Raw Events", sample);
        final String build = "rolevpn-described";
        json("start_build", args("build", build, "feeds", List.of("ROLEVPN")));
        json("survey_feed", args("feed", "ROLEVPN", "build", build));
        client.answeringWith(choosing(IN_PLACE));
        json(CATALOGUE, args("build", build));
        client.answeringWith(McpTestClient::agree);

        final Map<String, Object> listed = json(CATALOGUE, args("build", build, "stream_ids", List.of(vpn.raw())));
        dump("catalogue with roles", listed);
        final Map<String, Object> compact = map(listed, "catalogue");
        assertThat(list(compact, "event_types")).filteredOn(type -> "vpn-login".equals(type.get("type_id")))
                .singleElement().satisfies(type -> assertThat(type).containsEntry("timeline_role", "active"));
        assertThat(list(compact, "event_types")).filteredOn(type -> "vpn-logout".equals(type.get("type_id")))
                .singleElement().satisfies(type -> assertThat(type).containsEntry("timeline_role", "idle"));
        assertThat((List<String>) compact.get("timeline_roles_to_ask")).containsExactly("vpn-session-switch");
        final Map<String, Object> shown = json(CATALOGUE, args("build", build, "confirm", true));
        assertThat(String.valueOf(shown.get("table"))).contains("Not settled by the schema: vpn-session-switch");

        // The user says it is a focus: proposed, then confirmed; written back beside the pipeline with the rest.
        json(CATALOGUE, args("build", build, "proposals", List.of(Map.of("op", "change", "type_id",
                "vpn-session-switch", "timeline_role", "focus"))));
        confirmedCatalogue(build);
        final List<Map<String, Object>> beside = list(catalogueNear("System/Feeds/RoleVpn"), "event_types");
        assertThat(beside).filteredOn(type -> "vpn-session-switch".equals(type.get("type_id"))).singleElement()
                .satisfies(type -> assertThat(type).containsEntry("timeline_role", "focus")
                        .containsEntry("timeline_role_from", "given"));
        assertThat(beside).filteredOn(type -> "vpn-config-change".equals(type.get("type_id"))).singleElement()
                .satisfies(type -> assertThat(type).containsEntry("timeline_role", "action")
                        .containsEntry("timeline_role_from", "schema"));
    }

    // The hand-written VPN feed and pipeline, as an environment made them.
    private HandWritten vpn(final String folder, final String feed, final String sample) {
        return handWritten(folder, feed, XSLT, feed + "-Events", "Event Data (JSON)", List.of(addRootObject()),
                "Raw Events", sample);
    }

    private static PipelineProperty addRootObject() {
        return PipelineProperty.builder()
                .element("jsonParser")
                .name("addRootObject")
                .value(new PipelinePropertyValue(false))
                .build();
    }
}
