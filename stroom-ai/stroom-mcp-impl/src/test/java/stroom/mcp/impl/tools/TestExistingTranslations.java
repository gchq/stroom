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
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.PipelineDoc;

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

    private final SourceTypeRegistries registries = Mockito.mock(SourceTypeRegistries.class);
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final ExistingTranslations existing = new ExistingTranslations(() -> registries, () -> docFinder,
            documentAccess, guard, Mockito.mock(PipelineShapes.class));

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

    private void readable(final String uuid, final String name) {
        Mockito.when(documentAccess.read(new DocRef(PipelineDoc.TYPE, uuid), PipelineDoc.class))
                .thenReturn(Optional.of(PipelineDoc.builder().uuid(uuid).name(name).build()));
    }

    private static Map<String, Object> row(final String name, final String uuid, final String pipeline) {
        return Map.of("name", name, "group", "g", SourceTypeGroups.PIPELINE, Map.of("uuid", uuid, "name",
                pipeline));
    }
}
