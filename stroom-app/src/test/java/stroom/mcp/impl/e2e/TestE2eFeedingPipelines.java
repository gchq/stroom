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

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.instructions.InstructionChecks.Finding;
import stroom.ai.impl.tools.instructions.InstructionFacts;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.processing.XsltStages.How;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.ai.impl.tools.processing.XsltStages.StagedXslt;
import stroom.ai.impl.tools.streams.StreamProvenance;
import stroom.ai.impl.tools.streams.StreamProvenance.PipelineFacts;
import stroom.ai.impl.tools.streams.StreamProvenance.Provenance;
import stroom.ai.impl.tools.timeline.TimelineFields;
import stroom.ai.impl.tools.timeline.TimelineFields.Found;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.index.impl.IndexFieldService;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.LuceneIndexDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.shared.Meta;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// The shared foundations of many feeds (decided with the user, 2026-11-25), against a real Stroom: each XSLT of a
/// pipeline classified by its stage (a pre-processor before the translation is never taken for it, by content or by
/// stepping), provenance by stream (two feeds, each translated by its own version of a pipeline), and an index fed by
/// two indexing pipelines whose plans map the user to different fields (check_instructions' facts and the timeline's
/// field lookup give both, and the index's documentation says its From column varies by pipeline).
///
/// The database is cleared after each test.
@SuppressWarnings("unchecked")
class TestE2eFeedingPipelines extends AbstractE2eTest {

    // The hand-written translation of a VPN's JSON audit, and its records.
    private static final String VPN_XSLT = textResource("hand_written_vpn.xsl");
    private static final String VPN_SAMPLE = "["
            + "{\"ts\": \"2026-11-03T08:00:01.000Z\", \"action\": \"login\", \"user\": \"jbloggs\", "
            + "\"src_ip\": \"198.51.100.7\", \"host\": \"vpn-gw-1\", \"result\": \"success\"}, "
            + "{\"ts\": \"2026-11-03T12:10:00.000Z\", \"action\": \"logout\", \"user\": \"jbloggs\", "
            + "\"src_ip\": \"198.51.100.7\", \"host\": \"vpn-gw-1\"}]";
    // A pre-processor before the translation: JSON in, the same JSON out.
    private static final String PRE_PROCESS = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                              + "xpath-default-namespace=\"http://www.w3.org/2013/XSL/json\" "
                                              + "version=\"3.0\"><xsl:template match=\"@*|node()\"><xsl:copy>"
                                              + "<xsl:apply-templates select=\"@*|node()\"/></xsl:copy>"
                                              + "</xsl:template></xsl:stylesheet>";
    // An indexing XSLT reading Events into Lucene documents, the user written to the field given and the host from
    // the path given.
    private static final String INDEXING = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                           + "xpath-default-namespace=\"event-logging:3\" xmlns=\"records:2\" "
                                           + "xmlns:stroom=\"stroom\" version=\"3.0\"><xsl:template match=\"/Events\">"
                                           + "<records><xsl:apply-templates/></records></xsl:template>"
                                           + "<xsl:template match=\"Event\"><record><data name=\"StreamId\" "
                                           + "value=\"{@StreamId}\"/><data name=\"EventId\" value=\"{@EventId}\"/>"
                                           + "<data name=\"EventTime\" value=\"{EventTime/TimeCreated}\"/>"
                                           + "<data name=\"%s\" value=\"{EventSource/User/Id}\"/>"
                                           + "<data name=\"Host\" value=\"{%s}\"/></record>"
                                           + "</xsl:template></xsl:stylesheet>";
    private static final ToolSession SESSION = ToolSession.chat(1);

    @Inject
    private XsltStages xsltStages;
    @Inject
    private DocumentAccess documentAccess;
    @Inject
    private StreamProvenance streamProvenance;
    @Inject
    private TimelineFields timelineFields;
    @Inject
    private InstructionFacts instructionFacts;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private IndexFieldService indexFieldService;

    @Test
    void aPreProcessorBeforeTheTranslationIsNeverOfferedAsTheTranslation() {
        final HandWritten vpn = vpn("System/Feeds/PreVpn", "PREVPN", "PREVPN-Events");
        final String pre = create(XsltDoc.TYPE, "PREVPN-Unpack", "System/Feeds/PreVpn").getUuid();
        editXslt(pre, doc -> doc.copy().data(PRE_PROCESS).build());
        withPreProcessor(vpn.pipeline(), pre, vpn.xslt());

        // By content: the pre-processor first, the translation after it.
        final MergedPipeline merged = asUser(admin, () -> MergedPipeline.load(documentAccess, vpn.pipeline())
                .orElseThrow());
        final List<StagedXslt> byContent = asUser(admin, () -> xsltStages.classify(merged));
        assertThat(byContent).extracting(StagedXslt::element, StagedXslt::stage)
                .containsExactly(tuple("preFilter", Stage.PRE_PROCESS), tuple("translationFilter", Stage.TRANSLATION));
        // Exactly, by stepping a record of the feed: where the Events first appear.
        final List<StagedXslt> stepped = asUser(admin, () -> xsltStages.classify(merged, merged.doc(), vpn.raw()));
        assertThat(stepped).extracting(StagedXslt::stage, StagedXslt::how)
                .containsExactly(tuple(Stage.PRE_PROCESS, How.STEPPED), tuple(Stage.TRANSLATION, How.STEPPED));
        assertThat(stepSample(vpn.pipeline(), List.of(vpn.raw())).get("verdict")).as("the pipeline still works")
                .isEqualTo("clean");

        // The existing translation the build is offered is the translation, its event types read from it.
        final String build = "prevpn-v1-1";
        json("start_build", args("build", build, "feeds", List.of("PREVPN")));
        json("survey_feed", args("feed", "PREVPN", "build", build));
        client.answeringWith(choosing("Start from the existing pipeline (recommended)"));
        final int formsBefore = client.forms().size();
        final Map<String, Object> chosen = json(CATALOGUE, args("build", build));
        client.answeringWith(McpTestClient::agree);
        dump("catalogue_event_types start", chosen);
        assertThat(formsSince(formsBefore)).anySatisfy(form -> assertThat(form.message())
                .contains("An existing pipeline already translates this feed: 'PREVPN-Events'", "4 event types"));
    }

    @Test
    void eachStreamsFeedProducingPipelineAndItsTranslationAreReadByStream() {
        final HandWritten first = vpn("System/Feeds/ProvA", "PROVA", "PROV-V1.0-EVENTS");
        final HandWritten second = vpn("System/Feeds/ProvB", "PROVB", "PROV-V1.1-EVENTS");
        // The newer version keeps its mapping and the link to its source notes, as the tools write them.
        final String notes = documentationDoc("System/Feeds/ProvB", "PROV source notes", "# PROV source notes\n");
        editXslt(second.xslt(), doc -> doc.copy().description(KeptMapping.withSourceNotes(KeptMapping.withMapping(
                "Hand-written, then read into a mapping.", KeptMapping.TRANSLATION, Map.of("schema_version",
                        VERSION, "mapping", Map.of("input", "json"))), new DocRef(DocumentationDoc.TYPE, notes,
                "PROV source notes"))).build());
        processAll();
        final List<Long> events = new ArrayList<>();
        for (final String feed : List.of("PROVA", "PROVB")) {
            streamsOf(feed).stream()
                    .filter(meta -> "Events".equals(meta.getTypeName()))
                    .map(Meta::getId)
                    .forEach(events::add);
        }
        assertThat(events).as("each feed's raw stream translated").hasSize(2);

        final Provenance found = asUser(admin, () -> streamProvenance.of(events));

        assertThat(found.notSeen()).isZero();
        assertThat(found.byFeed()).containsOnlyKeys("PROVA", "PROVB");
        assertThat(found.streams().values()).extracting(source -> source.pipeline().getUuid(),
                        source -> source.parentId())
                .containsExactlyInAnyOrder(tuple(first.pipeline(), first.raw()), tuple(second.pipeline(),
                        second.raw()));
        final PipelineFacts older = found.pipelines().get(first.pipeline());
        assertThat(older.pipeline().getName()).isEqualTo("PROV-V1.0-EVENTS");
        assertThat(older.of(Stage.TRANSLATION)).extracting(xslt -> xslt.xslt().getUuid()).containsExactly(
                first.xslt());
        assertThat(older.mapping()).as("written by hand: no mapping kept").isNull();
        final PipelineFacts newer = found.pipelines().get(second.pipeline());
        assertThat(newer.mapping()).containsEntry("schema_version", VERSION);
        assertThat(newer.sourceNotes().getUuid()).isEqualTo(notes);
        // A stream no one has is counted, not named.
        assertThat(asUser(admin, () -> streamProvenance.of(List.of(events.getFirst(), 999_999L))).notSeen())
                .isEqualTo(1);
    }

    @Test
    void anIndexFedByTwoPipelinesWhosePlansDifferGivesEachPipelinesField() {
        final String folder = "System/Feeds/TwoPlans";
        final ExplorerNode node = folder(folder);
        final ExplorerNode index = create(LuceneIndexDoc.TYPE, "TWOPLANS-INDEX", node);
        asUser(admin, () -> indexFieldService.addFields(index.getDocRef(), List.of(
                field("StreamId", FieldType.ID), field("EventId", FieldType.ID), field("EventTime", FieldType.DATE),
                field("UserId", FieldType.TEXT), field("UserName", FieldType.TEXT), field("Host", FieldType.TEXT))));
        // Each writes the user to a field of its own, and the host from a path of its own.
        final String a = indexing(node, "TWOPLANS-A - Indexing", "UserId", "EventSource/Device/HostName",
                index.getDocRef());
        final String b = indexing(node, "TWOPLANS-B - Indexing", "UserName", "EventSource/Client/HostName",
                index.getDocRef());

        // The timeline's field lookup: both pipelines' plans read, each pipeline's field given.
        final Set<String> fields = Set.of("StreamId", "EventId", "EventTime", "UserId", "UserName", "Host");
        final Found user = asUser(admin, () -> timelineFields.find(SESSION, index.getDocRef(),
                InstructionRules.USER_FIELD, List.of(), fields::contains)).orElseThrow();
        dump("user field", user);
        assertThat(user.origin()).isEqualTo(TimelineFields.Origin.PLAN);
        assertThat(user.fields()).containsExactlyInAnyOrder("UserId", "UserName");
        assertThat(user.from()).contains("differ by pipeline", "'TWOPLANS-A - Indexing'", "'TWOPLANS-B - Indexing'");

        // check_instructions' facts: a ## Fields line naming one is a note naming both, not a fix to one.
        final List<Finding> findings = asUser(admin, () -> instructionFacts.checkDraft(SESSION,
                "Checked by the e2e suite", "/Feeds/TwoPlans", "## Fields\n- user: UserId\n"));
        dump("findings", findings);
        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.rule()).isEqualTo("plans_differ");
            assertThat(finding.message()).contains("UserId ('TWOPLANS-A - Indexing')",
                    "UserName ('TWOPLANS-B - Indexing')");
        });
        assertThat(findings).noneMatch(finding -> "plan_disagrees".equals(finding.rule()));

        // The index's documentation: its From column says the user's source per pipeline where they differ.
        final String build = "twoplans";
        createFeed(build, "TWOPLANS");
        final Map<String, Object> documented = agreed("write_documentation", args("build", build, "index_uuid",
                index.getUuid(), "markdown", "## Purpose and data\n\nTwo pipelines' events.\n", "change",
                "Documented by the e2e suite", "document_missing_fields", true));
        dump("write_documentation", documented);
        final String section = String.valueOf(documented.get("field_mapping"));
        assertThat(section).as("the host's source differs by pipeline: %s", section)
                .contains("varies by pipeline: `EventSource/Device/HostName` (TWOPLANS-A - Indexing); "
                          + "`EventSource/Client/HostName` (TWOPLANS-B - Indexing)");
        assertThat(a).isNotEqualTo(b);
    }

    // ------------------------------------------------------------------------------------------------

    // A VPN feed translated by a hand-written pipeline of its own, in a folder of its own.
    private HandWritten vpn(final String folder, final String feed, final String name) {
        return handWritten(folder, feed, VPN_XSLT, name, "Event Data (JSON)", List.of(PipelineProperty.builder()
                .element("jsonParser")
                .name("addRootObject")
                .value(new PipelinePropertyValue(false))
                .build()), "Raw Events", VPN_SAMPLE);
    }

    // The pipeline with a pre-processor between its parser and its translation, set before the translation's XSLT
    // (the first of its own XSLTs, as a hand-written pipeline may list them).
    private void withPreProcessor(final String pipeline, final String pre, final String translation) {
        final MergedPipeline template = asUser(admin, () -> MergedPipeline.load(documentAccess, pipeline)
                .orElseThrow());
        final List<String> chain = template.chain();
        final String before = chain.get(chain.indexOf("translationFilter") - 1);
        asUser(admin, () -> {
            final PipelineDoc doc = pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, pipeline));
            final PipelineDataBuilder data = new PipelineDataBuilder()
                    .addElement(new PipelineElement("preFilter", "XSLTFilter"))
                    .removeLink(new PipelineLink(before, "translationFilter"))
                    .addLink(new PipelineLink(before, "preFilter"))
                    .addLink(new PipelineLink("preFilter", "translationFilter"))
                    .addProperty(property("preFilter", "xslt", new DocRef(XsltDoc.TYPE, pre)))
                    .addProperty(property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, translation)))
                    .addProperty(PipelineProperty.builder().element("jsonParser").name("addRootObject")
                            .value(new PipelinePropertyValue(false)).build());
            pipelineStore.writeDocument(doc.copy().pipelineData(data.build()).build());
        });
    }

    // An indexing pipeline of the content pack's Indexing template writing to the Lucene index, its own XSLT
    // keeping a plan that maps the user to the field given.
    private String indexing(final ExplorerNode folder,
                            final String name,
                            final String userField,
                            final String hostSource,
                            final DocRef index) {
        final String xslt = create(XsltDoc.TYPE, name, folder).getUuid();
        final Map<String, Object> plan = Map.of("backend", "lucene", "index_name", index.getName(),
                "time_field", "EventTime", "fields", List.of(
                        Map.of("name", "StreamId", "type", "id", "source", "@StreamId"),
                        Map.of("name", "EventId", "type", "id", "source", "@EventId"),
                        Map.of("name", "EventTime", "type", "date", "source", "EventTime/TimeCreated"),
                        Map.of("name", userField, "type", "keyword", "source", "EventSource/User/Id"),
                        Map.of("name", "Host", "type", "keyword", "source", hostSource)));
        editXslt(xslt, doc -> doc.copy().data(INDEXING.formatted(userField, hostSource))
                .description(KeptMapping.withMapping("", "index", plan)).build());
        return childPipeline(folder, name, templateUuid("indexing", "Indexing"), List.of(
                property("xsltFilter", "xslt", new DocRef(XsltDoc.TYPE, xslt)),
                property("indexingFilter", "index", index)));
    }

    private static IndexField field(final String name, final FieldType type) {
        return IndexFieldImpl.builder()
                .fldName(name)
                .fldType(type)
                .analyzerType(AnalyzerType.KEYWORD)
                .indexed(true)
                .stored(true)
                .build();
    }
}
