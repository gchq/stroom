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

package stroom.ai.impl.tools.streams;

import stroom.ai.impl.tools.ToolTestFixture;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.catalogue.TimelineRole;
import stroom.ai.impl.tools.catalogue.TimelineRoles;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.ai.impl.tools.streams.StreamProvenance.PipelineFacts;
import stroom.ai.impl.tools.streams.StreamProvenance.Provenance;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Where streams came from: each stream's feed, producing pipeline and parent, looked up in batches as the user, and
/// each producing pipeline's XSLTs by stage, kept mapping, source notes link and index plan.
class TestStreamProvenance {

    private static final String TRANSLATION = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                              + "version=\"3.0\" xpath-default-namespace=\"records:2\" "
                                              + "xmlns=\"event-logging:3\"><xsl:template match=\"/records\">"
                                              + "<Events/></xsl:template></xsl:stylesheet>";
    private static final DocRef NOTES = new DocRef(DocumentationDoc.TYPE, "notes-1", "Acme source notes");

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final Map<Long, MetaRow> metas = new LinkedHashMap<>();
    private final List<Integer> batches = new ArrayList<>();
    private StreamProvenance provenance;

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
        Mockito.when(heldSchemas.forks()).thenReturn(List.of());
        provenance = new StreamProvenance(streamReader, fixture.documentAccess(), new XsltStages(
                fixture.documentAccess(), heldSchemas, Mockito.mock(SteppingWalks.class)));
        // The streams the user may see, found by their ids.
        Mockito.when(streamReader.find(Mockito.any(), Mockito.anyInt(), Mockito.anyBoolean())).thenAnswer(inv -> {
            final ExpressionOperator expression = inv.getArgument(0);
            final Set<Long> ids = expression.getChildren().stream()
                    .map(ExpressionTerm.class::cast)
                    .flatMap(term -> Arrays.stream(term.getValue().split(",")))
                    .map(String::strip)
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());
            batches.add(ids.size());
            return new ResultPage<>(ids.stream().map(metas::get).filter(Objects::nonNull).toList());
        });
        xslt("tr-1", "Acme-V1.0-EVENTS", NOTES);
        xslt("tr-2", "Acme-V1.1-EVENTS", null);
        pipeline("p-1", "Acme-V1.0-EVENTS", "tr-1");
        pipeline("p-2", "Acme-V1.1-EVENTS", "tr-2");
    }

    @Test
    void eachStreamsFeedPipelineAndParentAreGivenWithItsPipelinesFactsReadOnce() {
        stream(11, "ACME-SITE-A-EVENTS", "p-1", 1L);
        stream(12, "ACME-SITE-A-EVENTS", "p-1", 2L);
        stream(21, "ACME-SITE-B-EVENTS", "p-2", 3L);

        final Provenance found = provenance.of(List.of(21L, 11L, 12L, 11L));

        assertThat(found.streams().keySet()).containsExactly(21L, 11L, 12L);
        assertThat(found.streams().get(11L).feed()).isEqualTo("ACME-SITE-A-EVENTS");
        assertThat(found.streams().get(11L).parentId()).isEqualTo(1L);
        assertThat(found.streams().get(11L).type()).isEqualTo("Events");
        assertThat(found.notSeen()).isZero();
        assertThat(found.byFeed()).containsExactly(Map.entry("ACME-SITE-B-EVENTS", List.of(21L)),
                Map.entry("ACME-SITE-A-EVENTS", List.of(11L, 12L)));
        assertThat(found.byPipeline()).containsOnlyKeys("p-1", "p-2");
        assertThat(found.pipelines()).containsOnlyKeys("p-2", "p-1");
        final PipelineFacts first = found.pipelineOf(11L).orElseThrow();
        assertThat(first.readable()).isTrue();
        assertThat(first.pipeline().getName()).isEqualTo("Acme-V1.0-EVENTS");
        assertThat(first.of(Stage.TRANSLATION)).singleElement()
                .satisfies(xslt -> assertThat(xslt.xslt().getUuid()).isEqualTo("tr-1"));
        assertThat(first.mapping()).containsKey("mapping");
        assertThat(first.sourceNotes().getUuid()).isEqualTo("notes-1");
        assertThat(first.indexPlan()).isNull();
        // The newer version keeps no link to source notes.
        assertThat(found.pipelineOf(21L).orElseThrow().sourceNotes()).isNull();
    }

    @Test
    void streamsTheUserMayNotSeeAreCountedNotNamed() {
        stream(11, "ACME-SITE-A-EVENTS", "p-1", null);

        final Provenance found = provenance.of(Arrays.asList(11L, 99L, null, 98L));

        assertThat(found.streams().keySet()).containsExactly(11L);
        assertThat(found.notSeen()).isEqualTo(2);
        assertThat(found.pipelineOf(99L)).isEmpty();
        assertThat(provenance.of(List.of()).streams()).isEmpty();
    }

    @Test
    void aPipelineTheUserCantReadIsNamedByItsUuidOnly() {
        stream(11, "ACME-SITE-A-EVENTS", "p-1", null);
        fixture.visible.remove("p-1");

        final PipelineFacts facts = provenance.of(List.of(11L)).pipelineOf(11L).orElseThrow();

        assertThat(facts.readable()).isFalse();
        assertThat(facts.pipeline().getUuid()).isEqualTo("p-1");
        assertThat(facts.pipeline().getName()).isNull();
        assertThat(facts.xslts()).isEmpty();
        assertThat(facts.mapping()).isNull();
    }

    @Test
    void manyStreamsAreLookedUpInBatches() {
        final List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= StreamProvenance.BATCH + 10; id++) {
            stream(id, "ACME-SITE-A-EVENTS", "p-1", null);
            ids.add(id);
        }

        assertThat(provenance.of(ids).streams()).hasSize(StreamProvenance.BATCH + 10);
        assertThat(batches).containsExactly(StreamProvenance.BATCH, 10);
    }

    @Test
    void aStreamWithNoPipelineHasNoPipelinesFacts() {
        metas.put(5L, new MetaRow(Meta.builder().id(5L).feedName("ACME-SITE-A").typeName("Raw Events").build(),
                null, Map.of()));

        final Provenance found = provenance.of(List.of(5L));

        assertThat(found.streams().get(5L).pipeline()).isNull();
        assertThat(found.pipelineOf(5L)).isEmpty();
        assertThat(found.pipelines()).isEmpty();
    }

    @Test
    void aStreamsTimelineRolesAreReadFromTheCatalogueItsTranslationLinksTo() {
        stream(11, "ACME-SITE-A-EVENTS", "p-1", null);
        stream(21, "ACME-SITE-B-EVENTS", "p-2", null);
        final Catalogue catalogue = new Catalogue(List.of(
                type("Logon-User", "Authenticate", "Logon"),
                type("Focus-Window", "Unknown", null).withTimelineRole("focus", TimelineRoles.FROM_GIVEN),
                type("Odd-Thing", "Unknown", null)), null, null);
        final Map<String, Object> notes = new LinkedHashMap<>(catalogue.toJson());
        fixture.add(NOTES, DocumentationDoc.builder().uuid(NOTES.getUuid()).name(NOTES.getName())
                .data("# Acme source notes\n\n" + SourceNotes.block(notes)).build(), true);

        final Provenance found = provenance.of(List.of(11L, 21L));
        final Map<String, TimelineRoles.Role> roles = provenance.timelineRoles(found, 11L);

        // Given, else the schema's; one neither settles is left for the timeline to classify.
        assertThat(roles.keySet()).containsExactly("Logon-User", "Focus-Window");
        assertThat(roles.get("Logon-User").role()).isEqualTo(TimelineRole.ACTIVE);
        assertThat(roles.get("Focus-Window").role()).isEqualTo(TimelineRole.FOCUS);
        // The newer version links no notes; a stream not seen has no pipeline.
        assertThat(provenance.timelineRoles(found, 21L)).isEmpty();
        assertThat(provenance.timelineRoles(found, 99L)).isEmpty();
        assertThat(provenance.timelineRoles((PipelineFacts) null)).isEmpty();
    }

    @Test
    void notesTheUserCantReadGiveNoRoles() {
        stream(11, "ACME-SITE-A-EVENTS", "p-1", null);
        fixture.add(NOTES, DocumentationDoc.builder().uuid(NOTES.getUuid()).name(NOTES.getName()).data("x").build(),
                false);

        assertThat(provenance.timelineRoles(provenance.of(List.of(11L)), 11L)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------

    private void stream(final long id, final String feed, final String pipeline, final Long parent) {
        metas.put(id, new MetaRow(Meta.builder().id(id).feedName(feed).typeName("Events").pipelineUuid(pipeline)
                .processorUuid("proc-" + pipeline).parentDataId(parent).build(),
                new DocRef(PipelineDoc.TYPE, pipeline, "named in meta"), Map.of()));
    }

    private static EventType type(final String typeId, final String element, final String action) {
        return new EventType(typeId, typeId, null, element, action, null, null, null, null, List.of(), false,
                EventType.AGENT_KNOWLEDGE, "high", null, EventType.CONFIRMED);
    }

    private void xslt(final String uuid, final String name, final DocRef notes) {
        String description = KeptMapping.withMapping("Hand notes.", KeptMapping.TRANSLATION,
                Map.of("schema_version", "4.0.0", "mapping", Map.of("input", "csv")));
        description = KeptMapping.withSourceNotes(description, notes);
        fixture.add(new DocRef(XsltDoc.TYPE, uuid, name), XsltDoc.builder().uuid(uuid).name(name).data(TRANSLATION)
                .description(description).build(), true);
    }

    private void pipeline(final String uuid, final String name, final String xslt) {
        fixture.add(new DocRef(PipelineDoc.TYPE, uuid, name), PipelineDoc.builder().uuid(uuid).name(name)
                .pipelineData(new PipelineDataBuilder()
                        .addElement(new PipelineElement("dsParser", "DSParser"))
                        .addElement(new PipelineElement("translationFilter", "XSLTFilter"))
                        .addLink(new PipelineLink("dsParser", "translationFilter"))
                        .addProperty(new PipelineProperty("translationFilter", "xslt", new PipelinePropertyValue(
                                fixture.docRefs.get(xslt))))
                        .build())
                .build(), true);
    }
}
