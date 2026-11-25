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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.ai.impl.tools.streams.SteppingWalks;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/// A hand-written translation described in place: its translation XSLT (never a pre-processor before it) read back
/// into a mapping and a catalogue, the mapping and the link to the notes written into its description in hidden
/// blocks (its code and the user's own text kept), the catalogue in a source notes doc made beside the pipeline (the
/// build's notes a working copy of it), without asking; where the user may not, who can is said and it is kept in
/// the build only, written back once confirmed by someone who may.
class TestInPlaceInduction {

    private static final String BUILD = "vpn-described";
    private static final String FOLDER = "System/Feeds/Vpn";
    private static final String USERS_TEXT = "Our VPN translation, owned by the network team. Ask Jo before changing.";
    // A hand-written translation the tools never built: a template per event, by match patterns.
    private static final String XSLT = """
            <xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="http://www.w3.org/2013/XSL/json" version="3.0">
              <xsl:template match="/">
                <Events Version="4.1.0"><xsl:apply-templates select="array/map"/></Events>
              </xsl:template>
              <xsl:template match="map[string[@key='action'] = 'login']">
                <Event>
                  <EventTime><TimeCreated><xsl:value-of select="string[@key='ts']"/></TimeCreated></EventTime>
                  <EventSource>
                    <System><Name>Example VPN</Name><Environment>Production</Environment></System>
                    <Generator>vpnd</Generator>
                    <Device><HostName><xsl:value-of select="string[@key='host']"/></HostName></Device>
                    <User><Id><xsl:value-of select="string[@key='user']"/></Id></User>
                  </EventSource>
                  <EventDetail>
                    <TypeId>vpn-login</TypeId>
                    <Description>A user logged in to the VPN</Description>
                    <Authenticate><Action>Logon</Action><User><Id><xsl:value-of select="string[@key='user']"/>\
            </Id></User></Authenticate>
                  </EventDetail>
                </Event>
              </xsl:template>
              <xsl:template match="map[string[@key='action'] = 'logout']">
                <Event>
                  <EventTime><TimeCreated><xsl:value-of select="string[@key='ts']"/></TimeCreated></EventTime>
                  <EventSource>
                    <System><Name>Example VPN</Name><Environment>Production</Environment></System>
                    <Generator>vpnd</Generator>
                    <Device><HostName><xsl:value-of select="string[@key='host']"/></HostName></Device>
                    <User><Id><xsl:value-of select="string[@key='user']"/></Id></User>
                  </EventSource>
                  <EventDetail>
                    <TypeId>vpn-logout</TypeId>
                    <Description>A user logged out of the VPN</Description>
                    <Authenticate><Action>Logoff</Action><User><Id><xsl:value-of select="string[@key='user']"/>\
            </Id></User></Authenticate>
                  </EventDetail>
                </Event>
              </xsl:template>
              <xsl:template match="map"/>
            </xsl:stylesheet>""";
    // A pre-processor before it: JSON in, the same JSON out.
    private static final String PRE_PROCESS = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                              + "xpath-default-namespace=\"http://www.w3.org/2013/XSL/json\" "
                                              + "version=\"3.0\"><xsl:template match=\"@*|node()\"><xsl:copy>"
                                              + "<xsl:apply-templates select=\"@*|node()\"/></xsl:copy>"
                                              + "</xsl:template></xsl:stylesheet>";

    // A rule, which a mapping needs: its reading is what is read.
    private static final Map<String, Object> RULE = Map.of("name", "reading", "fields",
            List.of(Map.of("path", "EventDetail/TypeId", "value", "Reading")));

    private final BuildWorld world = new BuildWorld();
    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final XsltUsers xsltUsers = Mockito.mock(XsltUsers.class);
    private final CatalogueNotes catalogueNotes = new CatalogueNotes(world.guard(), world.documentAccess,
            new DocumentWrites(world.documentAccess), new FeedNames(() -> Mockito.mock(DocFinder.class),
            world.documentAccess));
    private final Consent consent = new Consent(() -> Mockito.mock(AiAuditLog.class));
    private InPlaceInduction induction;
    private DocRef xslt;
    private DocRef pipeline;

    @BeforeEach
    void setUp() {
        Mockito.when(heldSchemas.forks()).thenReturn(List.of());
        final DocFinder docFinder = Mockito.mock(DocFinder.class);
        induction = new InPlaceInduction(world.pipelineShapes, new XsltStages(world.documentAccess, heldSchemas,
                Mockito.mock(SteppingWalks.class)), world.documentAccess, new DocumentWrites(world.documentAccess),
                world.guard(), catalogueNotes, xsltUsers, new EventVersions(heldSchemas, consent, new BuildMemory(),
                world::guard, world::records, () -> Mockito.mock(EventsTargetInference.class)), () -> docFinder,
                world::records, () -> 1_800_000_000_000L);
        final String folder = world.folder(FOLDER).uuid;
        xslt = world.add(XsltDoc.TYPE, "VPN-Events", folder, "hand-xslt").docRef();
        world.docs.put("hand-xslt", XsltDoc.builder().uuid("hand-xslt").name("VPN-Events").version("v0")
                .data(XSLT).description(USERS_TEXT).build());
        pipeline = world.add(PipelineDoc.TYPE, "VPN-Events", folder, "hand-pipeline").docRef();
        PipelineLayers.given(world.pipelineService, "hand-pipeline", PipelineLayers.of(
                PipelineLayers.layer("t").chain("jsonParser", "JSONParser", "translationFilter", "XSLTFilter"),
                PipelineLayers.layer("hand-pipeline").property("translationFilter", "xslt", xslt)));
        Mockito.when(xsltUsers.of("hand-xslt")).thenReturn(users("VPN-A", "VPN-B"));
        world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD);
        world.records().rememberStartFrom(BUILD, Map.of("choice", InPlaceInduction.IN_PLACE, "pipeline",
                Map.of("name", "VPN-Events", "uuid", "hand-pipeline")));
        Mockito.when(world.documentAccess.canEdit(any())).thenReturn(true);
        Mockito.when(world.documentAccess.canCreate(any(), anyString())).thenReturn(true);
    }

    @Test
    void theTranslationIsDescribedInPlaceWithoutANewVersionTheUsersTextKept() {
        final InPlaceInduction.Scope scope = induction.scope("hand-pipeline").orElseThrow();
        assertThat(scope.translation().xslt().getUuid()).isEqualTo("hand-xslt");
        assertThat(scope.preProcessed()).isFalse();

        final Map<String, Object> described = induction.describe(BuildToolCalls.context(), BUILD, "VPN", scope,
                List.of("VPN-A", "VPN-B"));

        assertThat(described).containsEntry("status", "described_in_place").doesNotContainKey("not_written");
        assertThat(JsonValues.strings(described.get("event_types"))).containsExactlyInAnyOrder("vpn-login",
                "vpn-logout");
        // The XSLT: its code as it was; its description the user's text, then the hidden mapping and link.
        final XsltDoc after = (XsltDoc) world.docs.get("hand-xslt");
        assertThat(after.getData()).isEqualTo(XSLT);
        assertThat(after.getDescription()).startsWith(USERS_TEXT);
        final KeptMapping.Kept kept = KeptMapping.readMapping(after.getDescription()).orElseThrow();
        assertThat(kept.kind()).isEqualTo(KeptMapping.TRANSLATION);
        assertThat(JsonValues.mapOrEmpty(kept.payload().get(InPlaceInduction.INDUCTED))).containsEntry("build",
                BUILD).containsEntry(InPlaceInduction.XSLT, "hand-xslt");
        assertThat(TranslationMapping.parse(kept.payload().get("mapping")).input()).isEqualTo("json");
        // The source notes beside the pipeline, linked from the XSLT; the build's notes a working copy of them.
        final DocRef notes = KeptMapping.readSourceNotes(after.getDescription()).orElseThrow();
        assertThat(world.documentAccess.parent(notes).orElseThrow().getUuid()).isEqualTo(world.folder(FOLDER).uuid);
        assertThat(world.guard().buildOf(notes)).isEmpty();
        final DocRef copy = catalogueNotes.notesDocRef(BUILD).orElseThrow();
        assertThat(world.guard().copyOf(copy)).contains(notes.getUuid());
        // The catalogue read back, proposed for the user, with the feeds it is checked against.
        final Catalogue catalogue = catalogueNotes.catalogue(BUILD).orElseThrow();
        assertThat(catalogue.eventTypes()).extracting(EventType::typeId).containsExactlyInAnyOrder("vpn-login",
                "vpn-logout");
        assertThat(catalogue.eventTypes()).allSatisfy(type -> {
            assertThat(type.confirmed()).isFalse();
            assertThat(type.basis()).isEqualTo(EventType.EXISTING_PIPELINE);
        });
        assertThat(catalogue.checkedFeeds()).containsExactly("VPN-A", "VPN-B");
        assertThat(catalogue.eventType("vpn-login").timelineRole()).isEqualTo("active");
        // How the catalogue reads the records: as the translation does.
        final Map<String, Object> confirming = JsonValues.mapOrEmpty(SourceNotes.readNotes(world.body(
                copy.getUuid())).get(RecordSourceNotesTool.CONFIRMING));
        assertThat(JsonValues.mapOrEmpty(confirming.get(CatalogueEventTypesTool.READING))).containsEntry("input",
                "json");
        // The build record: what was described.
        final Map<String, Object> recorded = JsonValues.mapOrEmpty(world.records().startFrom(BUILD).orElseThrow()
                .get(InPlaceInduction.IN_PLACE));
        assertThat(recorded).containsEntry(InPlaceInduction.ELEMENT, "translationFilter")
                .containsEntry(InPlaceInduction.PRE_PROCESSED, false)
                .containsEntry("written", true)
                .doesNotContainKey("mapping");
        assertThat(JsonValues.strings(recorded.get(InPlaceInduction.FEEDS))).containsExactly("VPN-A", "VPN-B");
    }

    @Test
    void describedAgainOnlyTheHiddenBlocksChange() {
        final InPlaceInduction.Scope scope = induction.scope("hand-pipeline").orElseThrow();
        induction.describe(BuildToolCalls.context(), BUILD, "VPN", scope, List.of("VPN-A"));
        final String once = ((XsltDoc) world.docs.get("hand-xslt")).getDescription();

        induction.describe(BuildToolCalls.context(), BUILD, "VPN", scope, List.of("VPN-A"));

        final String twice = ((XsltDoc) world.docs.get("hand-xslt")).getDescription();
        assertThat(twice).startsWith(USERS_TEXT);
        assertThat(twice.split("stroom-mcp translation mapping", -1)).hasSize(2);
        assertThat(KeptMapping.readSourceNotes(twice)).isEqualTo(KeptMapping.readSourceNotes(once));
        // One notes doc beside the pipeline, not another.
        assertThat(world.documentAccess.children(world.folder(FOLDER).docRef()).stream()
                .filter(node -> DocumentationDoc.TYPE.equals(node.getType()))).hasSize(1);
    }

    @Test
    void withoutEditOrCreateItIsKeptInTheBuildOnlyAndWhoCanIsSaid() {
        Mockito.when(world.documentAccess.canEdit(any())).thenReturn(false);
        Mockito.when(world.documentAccess.canCreate(any(), anyString())).thenReturn(false);
        final InPlaceInduction.Scope scope = induction.scope("hand-pipeline").orElseThrow();

        final Map<String, Object> described = induction.describe(BuildToolCalls.context(), BUILD, "VPN", scope,
                List.of("VPN-A"));

        assertThat(described).containsEntry("status", "described_in_place");
        assertThat(String.valueOf(described.get("not_written"))).contains("Edit on XSLT", "Create (Documentation)",
                "owner", "kept in this build only");
        final XsltDoc after = (XsltDoc) world.docs.get("hand-xslt");
        assertThat(after.getDescription()).isEqualTo(USERS_TEXT);
        // The catalogue in the build's notes; the mapping in its record, for later.
        assertThat(catalogueNotes.catalogue(BUILD).orElseThrow().eventTypes()).hasSize(2);
        final Map<String, Object> recorded = JsonValues.mapOrEmpty(world.records().startFrom(BUILD).orElseThrow()
                .get(InPlaceInduction.IN_PLACE));
        assertThat(recorded).containsEntry("written", false).containsKey("mapping");

        // Confirmed later by someone who may edit the XSLT: its mapping written then.
        Mockito.when(world.documentAccess.canEdit(any())).thenReturn(true);
        final Map<String, Object> confirmed = induction.confirmed(BuildToolCalls.context(), BUILD).orElseThrow();
        assertThat(confirmed).containsKey("xslt");
        final XsltDoc now = (XsltDoc) world.docs.get("hand-xslt");
        assertThat(now.getDescription()).startsWith(USERS_TEXT);
        assertThat(KeptMapping.readMapping(now.getDescription())).isPresent();
        assertThat(JsonValues.mapOrEmpty(world.records().startFrom(BUILD).orElseThrow()
                .get(InPlaceInduction.IN_PLACE))).doesNotContainKey("mapping");
    }

    @Test
    void theConfirmedCatalogueIsWrittenBackBesideThePipeline() {
        final InPlaceInduction.Scope scope = induction.scope("hand-pipeline").orElseThrow();
        induction.describe(BuildToolCalls.context(), BUILD, "VPN", scope, List.of("VPN-A", "VPN-B"));
        final Catalogue confirmed = catalogueNotes.catalogue(BUILD).orElseThrow()
                .confirmedByUser(Set.of("vpn-login", "vpn-logout")).withCatchAllConfirmedByUser();
        catalogueNotes.write(null, BUILD, "VPN", confirmed);

        final Map<String, Object> said = induction.confirmed(BuildToolCalls.context(), BUILD).orElseThrow();

        assertThat(String.valueOf(said.get("notes"))).contains("written back");
        final DocRef notes = KeptMapping.readSourceNotes(((XsltDoc) world.docs.get("hand-xslt")).getDescription())
                .orElseThrow();
        final Catalogue beside = SourceNotes.catalogue(SourceNotes.readNotes(world.body(notes.getUuid())));
        assertThat(beside.eventTypes()).allSatisfy(type -> assertThat(type.confirmed()).isTrue());
        assertThat(beside.checkedFeeds()).containsExactly("VPN-A", "VPN-B");
        // A build that describes nothing in place writes nothing back.
        world.records().rememberStartFrom(BUILD, Map.of("choice", ExistingTranslations.DRAFT));
        assertThat(induction.confirmed(BuildToolCalls.context(), BUILD)).isEmpty();
    }

    @Test
    void aPreProcessorBeforeTheTranslationIsNeverDescribedAndItsRecordsAreReadAsTheTranslationReadsThem() {
        final String folder = world.folder(FOLDER).uuid;
        world.add(XsltDoc.TYPE, "VPN-Unpack", folder, "pre");
        world.docs.put("pre", XsltDoc.builder().uuid("pre").name("VPN-Unpack").version("v0").data(PRE_PROCESS)
                .build());
        PipelineLayers.given(world.pipelineService, "hand-pipeline", PipelineLayers.of(
                PipelineLayers.layer("t").chain("jsonParser", "JSONParser", "preFilter", "XSLTFilter",
                        "translationFilter", "XSLTFilter"),
                PipelineLayers.layer("hand-pipeline")
                        .property("preFilter", "xslt", new DocRef(XsltDoc.TYPE, "pre", "VPN-Unpack"))
                        .property("translationFilter", "xslt", xslt)));

        final InPlaceInduction.Scope scope = induction.scope("hand-pipeline").orElseThrow();

        assertThat(scope.translation().xslt().getUuid()).isEqualTo("hand-xslt");
        assertThat(scope.translation().stage()).isEqualTo(Stage.TRANSLATION);
        assertThat(scope.preProcessed()).isTrue();
        assertThat(scope.said()).contains("after a pre-processor");
        induction.describe(BuildToolCalls.context(), BUILD, "VPN", scope, List.of("VPN-A"));
        assertThat(((XsltDoc) world.docs.get("pre")).getDescription()).isNull();
        assertThat(TranslationInputs.at(world.records().startFrom(BUILD).orElseThrow())).contains(
                new TranslationInputs.At("hand-pipeline", "translationFilter"));
    }

    @Test
    void aTranslationNamedByUuidAloneInItsPipelineIsDescribedAndRemembered() {
        // Seen in the e2e suite: a pipeline naming its XSLT by UUID alone failed to be remembered (no name to say).
        PipelineLayers.given(world.pipelineService, "hand-pipeline", PipelineLayers.of(
                PipelineLayers.layer("t").chain("jsonParser", "JSONParser", "translationFilter", "XSLTFilter"),
                PipelineLayers.layer("hand-pipeline").property("translationFilter", "xslt",
                        new DocRef(XsltDoc.TYPE, "hand-xslt"))));
        final InPlaceInduction.Scope scope = induction.scope("hand-pipeline").orElseThrow();

        final Map<String, Object> described = induction.describe(BuildToolCalls.context(), BUILD, "VPN", scope,
                List.of("VPN-A"));

        assertThat(described).containsEntry("status", "described_in_place");
        assertThat(JsonValues.mapOrEmpty(described.get("xslt"))).containsEntry("name", "VPN-Events");
        assertThat(JsonValues.mapOrEmpty(world.records().startFrom(BUILD).orElseThrow()
                .get(InPlaceInduction.IN_PLACE))).containsKey("xslt");
    }

    @Test
    void aPipelineWithNoTranslationHasNothingToDescribe() {
        PipelineLayers.given(world.pipelineService, "hand-pipeline", PipelineLayers.of(
                PipelineLayers.layer("t").chain("jsonParser", "JSONParser", "preFilter", "XSLTFilter")));

        assertThat(induction.scope("hand-pipeline")).isEmpty();
        assertThat(induction.scope("no-such-pipeline")).isEmpty();
    }

    @Test
    void aPreProcessedReadingReadsRecordsAsJsonByTheirNames() {
        final TranslationMapping mapping = TranslationMapping.parse(Map.of("input", "data_splitter", "events",
                List.of(RULE)));

        assertThat(InPlaceInduction.reading(mapping, false)).containsEntry("input", "data_splitter");
        assertThat(InPlaceInduction.reading(mapping, true)).containsEntry("input", "json");
        assertThat(InPlaceInduction.reading(TranslationMapping.parse(Map.of("input", "xml", "record", "entry",
                "events", List.of(RULE))), true)).containsEntry("input", "xml").containsEntry("record", "entry");
        // Not described in place, or no pre-processor: read as the raw streams are.
        assertThat(TranslationInputs.at(Map.of("choice", ExistingTranslations.EXISTING))).isEmpty();
    }

    private static XsltUsers.Users users(final String... feeds) {
        final Map<String, FeedVolumes.Volume> volumes = new LinkedHashMap<>();
        for (final String feed : feeds) {
            volumes.put(feed, new FeedVolumes.Volume(feed, XsltUsers.RAW_EVENTS, 3, 3000L, false, null, null,
                    false));
        }
        return new XsltUsers.Users("hand-xslt", List.of(new XsltUsers.User(new DocRef(PipelineDoc.TYPE,
                "hand-pipeline", "VPN-Events"), true, List.of(feeds))), volumes, true);
    }
}
