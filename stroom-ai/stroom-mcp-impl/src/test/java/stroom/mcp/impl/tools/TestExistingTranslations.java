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

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.streams.SteppingWalks;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// The pipeline already translating what a build is to ([ExistingTranslations]): a source type group's, as the
/// registry's rows of the build's scope name it (by UUID, else by name), else the feed's, as its processor filters
/// read it; never one of the build's own.
class TestExistingTranslations {

    private static final String BUILD = "acme";
    private static final String XSL = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\"";
    private static final String PRE_PROCESS = "<xsl:stylesheet " + XSL + " xpath-default-namespace=\"http://www.w3"
                                              + ".org/2013/XSL/json\" xmlns=\"http://www.w3.org/2013/XSL/json\">"
                                              + "<xsl:template match=\"/\"><map/></xsl:template></xsl:stylesheet>";
    private static final String TRANSLATION = "<xsl:stylesheet " + XSL + " xpath-default-namespace=\"http://www.w3"
                                              + ".org/2013/XSL/json\" xmlns=\"event-logging:3\"><xsl:template "
                                              + "match=\"/map\"><Events><Event><EventDetail><TypeId>1001</TypeId>"
                                              + "</EventDetail></Event></Events></xsl:template></xsl:stylesheet>";

    private final SourceTypeRegistries registries = Mockito.mock(SourceTypeRegistries.class);
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final PipelineShapes pipelineShapes = Mockito.mock(PipelineShapes.class);
    private final ExistingTranslations existing = new ExistingTranslations(() -> registries, () -> docFinder,
            documentAccess, guard, pipelineShapes, new XsltStages(documentAccess, Mockito.mock(HeldSchemas.class),
            Mockito.mock(SteppingWalks.class)));

    @Test
    void aGroupsPipelineIsTheOneTheRegistryNamesForTheBuildsSourceTypes() {
        registry(List.of(row("Mail", "grp-1", "ACME Mail V1.0"), row("Chat", "grp-1", "ACME Mail V1.0"),
                row("Files", "grp-2", "ACME Files V1.0")), List.of("Mail", "Chat"));
        readable("grp-1", "ACME Mail V1.0");
        readable("grp-2", "ACME Files V1.0");

        assertThat(existing.find(BUILD, List.of("ACME"), feeds -> List.of()))
                .contains(Map.of("name", "ACME Mail V1.0", "uuid", "grp-1", "source_types", List.of("Mail",
                        "Chat")));
    }

    @Test
    void aRowNamingItsPipelineOnlyByNameIsFoundByThatName() {
        registry(List.of(row("Mail", "", "ACME Mail V1.0")), List.of("Mail"));
        final DocRef named = new DocRef(PipelineDoc.TYPE, "grp-9", "ACME Mail V1.0");
        Mockito.when(docFinder.findByName(PipelineDoc.TYPE, "ACME Mail V1.0", false)).thenReturn(List.of(named));
        Mockito.when(documentAccess.canView(named)).thenReturn(true);

        assertThat(existing.find(BUILD, List.of("ACME"), feeds -> List.of()).orElseThrow())
                .containsEntry("uuid", "grp-9").containsEntry("source_types", List.of("Mail"));
    }

    @Test
    void anAggregatedFeedsNewGroupIsOfferedNoOtherGroupsPipeline() {
        // The feed's first group's pipeline reads every stream of the feed: it is not the new group's.
        registry(List.of(row("Mail", "grp-1", "ACME Mail V1.0"), Map.of("name", "Files", "group", "files")),
                List.of("Files"));
        readable("grp-1", "ACME Mail V1.0");

        assertThat(existing.find(BUILD, List.of("ACME"), feeds -> List.of(Map.of("name", "ACME Mail V1.0",
                "uuid", "grp-1")))).isEmpty();
    }

    @Test
    void withoutAScopeTheFeedsTranslationIsTheOneItsFiltersRead() {
        Mockito.when(registries.registry(BUILD, "ACME")).thenReturn(Optional.empty());
        Mockito.when(guard.buildOf(Mockito.any())).thenReturn(Optional.empty());

        assertThat(existing.find(BUILD, List.of("ACME"), feeds -> List.of(Map.of("name", "ACME-V1.0-Events",
                "uuid", "prod-ev")))).contains(Map.of("name", "ACME-V1.0-Events", "uuid", "prod-ev"));
        // One of the build's own is never offered.
        Mockito.when(guard.buildOf(new DocRef(PipelineDoc.TYPE, "own"))).thenReturn(Optional.of(BUILD));
        assertThat(existing.find(BUILD, List.of("ACME"), feeds -> List.of(Map.of("name", "Own", "uuid", "own"))))
                .isEmpty();
        assertThat(existing.find(BUILD, List.of(), feeds -> List.of(Map.of("name", "X", "uuid", "x")))).isEmpty();
    }

    @Test
    void theXsltOfferedIsTheTranslationNotAPreProcessorBeforeIt() {
        // The pipeline's own XSLTs: a JSON unpacker first, then the translation.
        xslt("pre", "Unpack nested JSON", PRE_PROCESS);
        xslt("tr", "ACME-V1.0-EVENTS", TRANSLATION);
        Mockito.when(pipelineShapes.merged("prod-ev")).thenReturn(MergedPipeline.of(List.of(
                new PipelineLayer(new DocRef(PipelineDoc.TYPE, "prod-ev", "ACME-V1.0-Events"),
                        chain(List.of("preFilter", "translationFilter"))
                                .addProperty(xsltOn("preFilter", "pre"))
                                .addProperty(xsltOn("translationFilter", "tr"))
                                .build()))));

        final Map<String, Object> described = existing.describe(Map.of("name", "ACME-V1.0-Events", "uuid",
                "prod-ev"));

        assertThat(described.get("xslt")).isEqualTo(Map.of("name", "ACME-V1.0-EVENTS", "uuid", "tr"));
        assertThat(described).containsEntry("built_by_tools", false);
    }

    @Test
    void aTranslationInheritedFromAParentPipelineIsOfferedSayingSo() {
        xslt("tr", "ACME-V1.0-EVENTS", TRANSLATION);
        final DocRef parent = new DocRef(PipelineDoc.TYPE, "parent", "ACME translation");
        Mockito.when(pipelineShapes.merged("child")).thenReturn(MergedPipeline.of(List.of(
                new PipelineLayer(parent, chain(List.of("translationFilter"))
                        .addProperty(xsltOn("translationFilter", "tr")).build()),
                new PipelineLayer(new DocRef(PipelineDoc.TYPE, "child", "ACME Site B"),
                        new PipelineDataBuilder().build()))));

        assertThat(existing.describe(Map.of("name", "ACME Site B", "uuid", "child")).get("xslt"))
                .isEqualTo(Map.of("name", "ACME-V1.0-EVENTS", "uuid", "tr", "inherited_from", "ACME translation"));
    }

    @Test
    void thePipelineIsSaidWithWhoWroteItAndItsEventTypes() {
        final Map<String, Object> byHand = new LinkedHashMap<>(Map.of("name", "ACME-V1.0-Events"));
        byHand.put("built_by_tools", false);
        byHand.put("changed", "2026-03-02");
        byHand.put("changed_by", "jbloggs");
        byHand.put("event_types", 2);
        byHand.put("examples", List.of("1001", "1002"));
        assertThat(ExistingTranslations.said(byHand)).isEqualTo("'ACME-V1.0-Events' (not built by Stroom AI: its "
                                                                + "XSLT was written by hand, last changed "
                                                                + "2026-03-02 by jbloggs; 2 event types, e.g. "
                                                                + "1001, 1002)");
        final Map<String, Object> built = new LinkedHashMap<>(Map.of("name", "ACME Mail V1.0", "source_types",
                List.of("Mail", "Chat")));
        built.put("built_by_tools", true);
        built.put("event_types", 1);
        assertThat(ExistingTranslations.said(built)).isEqualTo("'ACME Mail V1.0' translates Mail and Chat (built by "
                                                               + "Stroom AI; 1 event type)");
    }

    private void registry(final List<Map<String, Object>> rows, final List<String> scoped) {
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("scope", Map.of("build", BUILD, "source_types", scoped));
        state.put("rows", rows);
        Mockito.when(registries.registry(BUILD, "ACME")).thenReturn(Optional.of(new SourceTypeRegistries.Found(
                null, false, state)));
        Mockito.when(guard.buildOf(Mockito.any())).thenReturn(Optional.empty());
    }

    private void xslt(final String uuid, final String name, final String code) {
        Mockito.when(documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class))
                .thenReturn(Optional.of(XsltDoc.builder().uuid(uuid).name(name).data(code).build()));
    }

    private static PipelineDataBuilder chain(final List<String> xsltFilters) {
        final PipelineDataBuilder data = new PipelineDataBuilder();
        String previous = null;
        for (final String element : xsltFilters) {
            data.addElement(new PipelineElement(element, "XSLTFilter"));
            if (previous != null) {
                data.addLink(new PipelineLink(previous, element));
            }
            previous = element;
        }
        return data;
    }

    private static PipelineProperty xsltOn(final String element, final String uuid) {
        return new PipelineProperty(element, "xslt", new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, uuid)));
    }

    private void readable(final String uuid, final String name) {
        Mockito.when(documentAccess.read(new DocRef(PipelineDoc.TYPE, uuid), PipelineDoc.class))
                .thenReturn(Optional.of(PipelineDoc.builder().uuid(uuid).name(name).build()));
    }

    private static Map<String, Object> row(final String name, final String uuid, final String pipeline) {
        return Map.of("name", name, "group", "g", SourceTypeGroups.PIPELINE, Map.of("uuid", uuid, "name",
                pipeline));
    }
}
