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
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.DocFinder;
import stroom.explorer.shared.DocContentMatch;
import stroom.mcp.impl.tools.PipelineIndex.Entry;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestPipelineIndex {

    private static final DocRef A = new DocRef(PipelineDoc.TYPE, "a", "A");
    private static final DocRef B = new DocRef(PipelineDoc.TYPE, "b", "B");
    private static final DocRef GONE = new DocRef(PipelineDoc.TYPE, "gone", "Gone");
    private static final DocRef HIDDEN = new DocRef(PipelineDoc.TYPE, "hidden", "Hidden");

    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private PipelineIndex pipelineIndex;

    @BeforeEach
    void setUp() {
        Mockito.when(documentAccess.canView(any())).thenReturn(true);
        Mockito.when(documentAccess.canView(HIDDEN)).thenReturn(false);
        Mockito.when(documentAccess.path(any())).thenReturn("/Feeds/Acme");
        Mockito.when(documentAccess.read(any(), eq(PipelineDoc.class))).thenReturn(Optional.empty());
        given(A, null);
        given(B, A);
        pipelineIndex = new PipelineIndex(() -> docFinder, documentAccess);
    }

    @Test
    void testAPipelineCreatedAfterTheIndexWasReadIsInTheNextOne() {
        // Seen in e2e: find_pipeline_templates read the index, a sibling pipeline was created, and
        // describe_template then missed it among the template's children for a few minutes. The index is read
        // afresh on every call.
        Mockito.when(docFinder.findByName(PipelineDoc.TYPE, "*", true)).thenReturn(List.of(A));
        assertThat(pipelineIndex.read()).containsOnlyKeys("a");
        Mockito.when(docFinder.findByName(PipelineDoc.TYPE, "*", true)).thenReturn(List.of(A, B));
        final Map<String, Entry> index = pipelineIndex.read();
        assertThat(index).containsOnlyKeys("a", "b");
        assertThat(index.get("b").parentUuid()).isEqualTo("a");
        assertThat(index.get("b").path()).isEqualTo("System/Feeds/Acme");
    }

    @Test
    void testDocumentsContainingTextAreFoundWithTheContentIndex() {
        final DocRef refXslt = new DocRef(XsltDoc.TYPE, "ref", "Ref");
        final DocRef hiddenXslt = new DocRef(XsltDoc.TYPE, "hiddenXslt", "Hidden XSLT");
        Mockito.when(documentAccess.canView(hiddenXslt)).thenReturn(false);
        final ContentIndex contentIndex = Mockito.mock(ContentIndex.class);
        Mockito.when(contentIndex.isReady()).thenReturn(true);
        Mockito.when(contentIndex.findInContent(any())).thenReturn(ResultPage.createUnboundedList(List.of(
                DocContentMatch.builder().docRef(refXslt).build(),
                DocContentMatch.builder().docRef(refXslt).build(),
                DocContentMatch.builder().docRef(hiddenXslt).build(),
                DocContentMatch.builder().docRef(A).build())));
        final PipelineIndex indexed = new PipelineIndex(() -> docFinder, documentAccess, () -> contentIndex);

        // Each XSLT once, only what the user can view, and only of the type asked for.
        assertThat(indexed.containing(XsltDoc.TYPE, "reference-data:2")).containsExactly(refXslt);
        Mockito.verifyNoInteractions(docFinder);
    }

    @Test
    void testWhileTheContentIndexIsNotReadyEveryDocumentOfTheTypeIsGiven() {
        final ContentIndex contentIndex = Mockito.mock(ContentIndex.class);
        Mockito.when(contentIndex.isReady()).thenReturn(false);
        Mockito.when(docFinder.findByName(PipelineDoc.TYPE, "*", true)).thenReturn(List.of(B, A));

        assertThat(new PipelineIndex(() -> docFinder, documentAccess, () -> contentIndex)
                .containing(PipelineDoc.TYPE, "anything")).containsExactly(A, B);
    }

    @Test
    void testContentIsSearchedWhileTheIndexHasNotBeenBuiltSoTheSearchStartsIt() {
        // As the Stroom MCP server's explorer content search: Stroom's findInContent starts the index's first build
        // (and waits a moment for it). Seen in e2e_cef: asked only once the index was ready, draft_cef_mapping never
        // found an XSLT writing CEF on a node where nothing else had started the index.
        final DocRef cefXslt = new DocRef(XsltDoc.TYPE, "cef", "Vault CEF");
        final ContentIndex contentIndex = Mockito.mock(ContentIndex.class);
        Mockito.when(contentIndex.isReady()).thenReturn(false);
        Mockito.when(contentIndex.findInContent(any())).thenReturn(ResultPage.createUnboundedList(List.of(
                DocContentMatch.builder().docRef(cefXslt).build())));

        assertThat(new PipelineIndex(() -> docFinder, documentAccess, () -> contentIndex)
                .withContent("CEF:0", 200)).containsExactly(cefXslt);
    }

    @Test
    void testContentStillBeingIndexedFindsNothing() {
        // Stroom answers "The content is currently being indexed (17% complete)" until the build is done: the
        // Stroom MCP server's draft went on without the XSLTs it would list.
        final ContentIndex contentIndex = Mockito.mock(ContentIndex.class);
        Mockito.when(contentIndex.findInContent(any())).thenThrow(new RuntimeException(
                "The content is currently being indexed (17% complete)."));

        assertThat(new PipelineIndex(() -> docFinder, documentAccess, () -> contentIndex)
                .withContent("CEF:0", 200)).isEmpty();
    }

    @Test
    void testAPipelineGoneSinceItWasListedOrNotVisibleIsLeftOut() {
        Mockito.when(docFinder.findByName(PipelineDoc.TYPE, "*", true)).thenReturn(List.of(GONE, HIDDEN, A));
        assertThat(pipelineIndex.read()).containsOnlyKeys("a");
    }

    @Test
    void testAPathAtTheTopOfTheTreeIsSystem() {
        Mockito.when(documentAccess.path(A)).thenReturn("/");
        assertThat(PipelineIndex.path(documentAccess, A)).isEqualTo("System");
    }

    private void given(final DocRef docRef, final DocRef parent) {
        final PipelineDoc doc = PipelineDoc.builder().uuid(docRef.getUuid()).name(docRef.getName())
                .parentPipeline(parent).build();
        Mockito.when(documentAccess.read(docRef, PipelineDoc.class)).thenReturn(Optional.of(doc));
    }
}
