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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// A feed's registry beside it, changed through a working copy: the version it was copied at recorded, so promotion
/// asks before writing over a registry changed since; refused while another build is open on the feed; and a build
/// holding only what its promotion kept in the workspace is not open on the feed.
class TestSourceTypeRegistries {

    private static final String BUILD = "pega-v2";
    private static final String FEED = "PEGA";
    private static final String REGISTRY = "PEGA source types";

    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final DocumentWrites documentWrites = Mockito.mock(DocumentWrites.class);
    private final FeedNames feedNames = Mockito.mock(FeedNames.class);
    private final DocumentPlaces places = Mockito.mock(DocumentPlaces.class);
    private final BuildRecords records = Mockito.mock(BuildRecords.class);
    private final SourceTypeRegistries registries = new SourceTypeRegistries(guard, documentAccess, documentWrites,
            feedNames, places, () -> records);
    private final ToolSession session = Mockito.mock(ToolSession.class);

    @BeforeEach
    void setUp() {
        Mockito.when(guard.builds()).thenReturn(List.of(BUILD));
        Mockito.when(records.load(anyString())).thenReturn(BuildRecords.empty());
        Mockito.when(guard.create(eq(DocumentationDoc.TYPE), eq(REGISTRY), eq(BUILD), eq("orig"), anyBoolean(),
                any())).thenReturn(new DocRef(DocumentationDoc.TYPE, "copy", REGISTRY));
        Mockito.when(documentAccess.read(eq(new DocRef(DocumentationDoc.TYPE, "orig", REGISTRY)),
                        eq(DocumentationDoc.class)))
                .thenReturn(Optional.of(DocumentationDoc.builder().uuid("orig").name(REGISTRY).version("v7")
                        .build()));
        Mockito.when(documentWrites.writeText(any(), anyString())).thenAnswer(inv -> DocumentationDoc.builder()
                .uuid(((DocRef) inv.getArgument(0)).getUuid()).name(REGISTRY).build());
    }

    @Test
    void aWorkingCopyOfTheRegistryBesideTheFeedRecordsTheVersionItWasCopiedAt() {
        registries.writeRegistry(session, BUILD, FEED, promoted(), "text");

        @SuppressWarnings("unchecked") final ArgumentCaptor<Consumer<Map<String, Object>>> change =
                ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(records).update(eq(BUILD), eq(new DocRef(DocumentationDoc.TYPE, "copy", REGISTRY)),
                change.capture());
        final Map<String, Object> entry = new LinkedHashMap<>();
        change.getValue().accept(entry);
        assertThat(entry).containsEntry(BuildRecords.COPY_OF_VERSION, "v7");
    }

    @Test
    void noWorkingCopyOfTheRegistryIsMadeWhileAnotherBuildIsOpenOnTheFeed() {
        Mockito.when(guard.builds()).thenReturn(List.of(BUILD, "other"));
        Mockito.when(guard.folderContents("other")).thenReturn(List.of(doc(DocumentationDoc.TYPE, "r2", REGISTRY)));

        assertThatThrownBy(() -> registries.writeRegistry(session, BUILD, FEED, promoted(), "text"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("build 'other' is open on it");
        Mockito.verify(guard, Mockito.never()).create(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void aBuildHoldingOnlyWhatItsPromotionKeptIsNotOpen() {
        Mockito.when(guard.builds()).thenReturn(List.of(BUILD, "done"));
        Mockito.when(guard.folderContents("done")).thenReturn(List.of(doc(DocumentationDoc.TYPE, "s1",
                "PEGA - Survey"), doc(XsltDoc.TYPE, "x1", "PEGA scratch")));
        Mockito.when(places.documentationBeside(any(), eq(REGISTRY))).thenReturn(Optional.empty());
        final Map<String, Object> record = BuildRecords.empty();
        record.put(BuildRecords.SOURCE_FEEDS, List.of(FEED));
        final Map<String, Object> kept = new LinkedHashMap<>();
        kept.put("s1", new LinkedHashMap<>(Map.of(BuildRecords.KEPT_AT_PROMOTION, true)));
        kept.put("x1", new LinkedHashMap<>(Map.of(BuildRecords.KEPT_AT_PROMOTION, true)));
        record.put(BuildRecords.DOCS, kept);
        Mockito.when(records.load("done")).thenReturn(record);

        // Kept by its promotion, the XSLT is not work in progress: the feed is free for another build.
        registries.refuseOpenBuild(FEED, BUILD, true);

        // Work done in the build since is.
        kept.put("x1", new LinkedHashMap<>());
        assertThatThrownBy(() -> registries.refuseOpenBuild(FEED, BUILD, true))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("build 'done' is open on it");
    }

    private static SourceTypeRegistries.Found promoted() {
        return new SourceTypeRegistries.Found(new SurveyRecord("orig", REGISTRY, ""), true, Map.of("feed", FEED));
    }

    private static WriteGuard.BuildDoc doc(final String type, final String uuid, final String name) {
        return new WriteGuard.BuildDoc(type, uuid, name, List.of(), "", null);
    }
}
