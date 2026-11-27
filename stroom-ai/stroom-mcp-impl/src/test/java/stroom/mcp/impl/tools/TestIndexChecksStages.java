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
import stroom.ai.impl.tools.query.IndexSearches;
import stroom.ai.impl.tools.streams.SteppingWalks;
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// verify_index traces a hit at the indexing pipeline's indexing XSLT, told by its stage ([XsltStages]): never an
/// Events decorator before it, though the pipeline lists the decorator first.
class TestIndexChecksStages {

    private static final String XSL = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\"";
    // Events to Events: a decorator adding a user's id.
    private static final String DECORATOR = "<xsl:stylesheet " + XSL + " xpath-default-namespace=\"event-logging:3\" "
                                            + "xmlns=\"event-logging:3\"><xsl:template match=\"/Events\"><Events/>"
                                            + "</xsl:template></xsl:stylesheet>";
    // Events to an index's records.
    private static final String INDEXING = "<xsl:stylesheet " + XSL + " xpath-default-namespace=\"event-logging:3\" "
                                           + "xmlns=\"records:2\"><xsl:template match=\"/Events\"><records/>"
                                           + "</xsl:template></xsl:stylesheet>";

    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final IndexChecks checks = new IndexChecks(Mockito.mock(IndexSearches.class),
            Mockito.mock(PipelineStepping.class), Mockito.mock(StreamReader.class),
            Mockito.mock(IndexingMemory.class), Mockito.mock(IndexTemplateAgreements.class));

    @Test
    void aHitIsTracedAtTheIndexingXsltNotTheDecoratorListedBeforeIt() {
        final SteppedPipeline pipeline = pipeline();

        // Without the stages: the first own XSLT, as before.
        assertThat(checks.indexingElement(pipeline, "p")).isEqualTo("decorationFilter");

        final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
        Mockito.when(heldSchemas.forks()).thenReturn(List.of());
        checks.setXsltStages(new XsltStages(documentAccess, heldSchemas, Mockito.mock(SteppingWalks.class)));
        assertThat(checks.indexingElement(pipeline, "p")).isEqualTo("xsltFilter");
        assertThat(IndexChecks.ownXsltElement(pipeline, "p")).isEqualTo("decorationFilter");
    }

    @Test
    void aPipelineWithNoXsltOfItsOwnHasNoElement() {
        final MergedPipeline merged = Mockito.mock(MergedPipeline.class);
        Mockito.when(merged.translationDocs("p")).thenReturn(List.of());
        final SteppedPipeline pipeline = Mockito.mock(SteppedPipeline.class);
        Mockito.when(pipeline.merged()).thenReturn(merged);

        assertThat(checks.indexingElement(pipeline, "p")).isNull();
    }

    private SteppedPipeline pipeline() {
        final DocRef decorator = new DocRef(XsltDoc.TYPE, "dec", "ACME-Decorate");
        final DocRef indexing = new DocRef(XsltDoc.TYPE, "idx", "ACME-Index");
        Mockito.when(documentAccess.read(decorator, XsltDoc.class)).thenReturn(Optional.of(XsltDoc.builder()
                .uuid("dec").data(DECORATOR).build()));
        Mockito.when(documentAccess.read(indexing, XsltDoc.class)).thenReturn(Optional.of(XsltDoc.builder()
                .uuid("idx").data(INDEXING).build()));
        final MergedPipeline merged = Mockito.mock(MergedPipeline.class);
        Mockito.when(merged.translationDocs(Mockito.any())).thenReturn(List.of(
                new MergedPipeline.TranslationDoc("decorationFilter", "XSLTFilter", decorator, "p", false),
                new MergedPipeline.TranslationDoc("xsltFilter", "XSLTFilter", indexing, "p", false)));
        Mockito.when(merged.chain()).thenReturn(List.of("source", "decorationFilter", "xsltFilter",
                "indexingFilter"));
        Mockito.when(merged.elements()).thenReturn(Map.of("source", "Source", "decorationFilter", "XSLTFilter",
                "xsltFilter", "XSLTFilter", "indexingFilter", "IndexingFilter"));
        Mockito.when(merged.pipeline()).thenReturn(new DocRef(PipelineDoc.TYPE, "p"));
        final SteppedPipeline pipeline = Mockito.mock(SteppedPipeline.class);
        Mockito.when(pipeline.merged()).thenReturn(merged);
        return pipeline;
    }
}
