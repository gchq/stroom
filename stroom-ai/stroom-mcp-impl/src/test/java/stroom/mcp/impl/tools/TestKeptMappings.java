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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.documents.KeptMapping.Kept;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.streams.SteppingWalks;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestKeptMappings {

    // A pre-processor: JSON in, the same JSON out.
    private static final String PRE_PROCESS = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                              + "xpath-default-namespace=\"http://www.w3.org/2013/XSL/json\" "
                                              + "version=\"3.0\"><xsl:template match=\"@*|node()\"><xsl:copy>"
                                              + "<xsl:apply-templates select=\"@*|node()\"/></xsl:copy>"
                                              + "</xsl:template></xsl:stylesheet>";
    // The translation: JSON in, Events out.
    private static final String TRANSLATION = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                              + "xpath-default-namespace=\"http://www.w3.org/2013/XSL/json\" "
                                              + "xmlns=\"event-logging:3\" version=\"3.0\"><xsl:template "
                                              + "match=\"/\"><Events/></xsl:template></xsl:stylesheet>";
    private static final String HIDDEN = "Acme events\n\n<!-- stroom-mcp translation mapping: kept by the server, "
                                         + "do not edit or delete\n{\"mapping\":{\"input\":\"json\"},"
                                         + "\"schema_version\":\"4.1.0\"}\n-->";

    @Test
    void testAHiddenBlockIsRead() {
        final Optional<Kept> kept = KeptMapping.readMapping(HIDDEN);
        assertThat(kept).isPresent();
        assertThat(kept.get().kind()).isEqualTo("translation");
        assertThat(kept.get().payload().get("mapping")).isEqualTo(Map.of("input", "json"));
    }

    @Test
    void testABlockWrittenBeforeIsStillRead() {
        final String shown = "--- stroom-mcp translation mapping (generated) ---\n{\"mapping\": {}}\n"
                             + "--- end of stroom-mcp mapping ---";
        assertThat(KeptMapping.readMapping(shown)).map(Kept::kind).contains("translation");
    }

    @Test
    void testABlockIsOnlyEverAnObject() {
        assertThat(KeptMapping.readBlock("<!-- stroom-mcp agreed index template: kept by the server, do not edit "
                                         + "or delete\n[1, 2]\n-->", "agreed index template")).isEmpty();
        assertThat(KeptMapping.readMapping("no block here")).isEmpty();
        assertThat(KeptMapping.readMapping(null)).isEmpty();
    }

    @Test
    void testTheMappingIsKeptWithThePipelinesOwnXsltNotItsTemplates() {
        final PipelineService service = PipelineLayers.service();
        PipelineLayers.given(service, "p", PipelineLayers.of(
                PipelineLayers.layer("t")
                        .chain("p", "JSONParser", "translation", "XSLTFilter", "decoration", "XSLTFilter")
                        .property("decoration", "xslt", new DocRef(XsltDoc.TYPE, "dec")),
                PipelineLayers.layer("p").property("translation", "xslt", new DocRef(XsltDoc.TYPE, "own"))));
        final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
        Mockito.when(documentAccess.read(any(), eq(XsltDoc.class))).thenReturn(Optional.of(XsltDoc.builder()
                .uuid("dec").description(HIDDEN).build()));
        final KeptMappings keptMappings = new KeptMappings(PipelineLayers.shapes(service, new AiConfig()),
                documentAccess);
        assertThat(keptMappings.keptMapping("p")).isPresent();
        Mockito.verify(documentAccess).read(new DocRef(XsltDoc.TYPE, "own"), XsltDoc.class);
        Mockito.verify(documentAccess, Mockito.never()).read(new DocRef(XsltDoc.TYPE, "dec"), XsltDoc.class);
    }

    @Test
    void theTranslationsMappingIsReadBeforeAPreProcessorsThoughTheirOrderIsTheOther() {
        // Both of the pipeline's own XSLTs keep a block (the pre-processor a stale copy of one): the translation's is
        // the pipeline's mapping, the pre-processor before it never taken for it.
        final PipelineService service = PipelineLayers.service();
        PipelineLayers.given(service, "p", PipelineLayers.of(
                PipelineLayers.layer("t").chain("p", "JSONParser", "pre", "XSLTFilter", "translation", "XSLTFilter"),
                PipelineLayers.layer("p")
                        .property("pre", "xslt", new DocRef(XsltDoc.TYPE, "pre"))
                        .property("translation", "xslt", new DocRef(XsltDoc.TYPE, "tr"))));
        final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
        Mockito.when(documentAccess.read(new DocRef(XsltDoc.TYPE, "pre"), XsltDoc.class)).thenReturn(Optional.of(
                XsltDoc.builder().uuid("pre").data(PRE_PROCESS).description(KeptMapping.withMapping("",
                        KeptMapping.TRANSLATION, Map.of("mapping", Map.of("input", "xml")))).build()));
        Mockito.when(documentAccess.read(new DocRef(XsltDoc.TYPE, "tr"), XsltDoc.class)).thenReturn(Optional.of(
                XsltDoc.builder().uuid("tr").data(TRANSLATION).description(HIDDEN).build()));
        final KeptMappings keptMappings = new KeptMappings(PipelineLayers.shapes(service, new AiConfig()),
                documentAccess);
        final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
        Mockito.when(heldSchemas.forks()).thenReturn(List.of());

        // Without the stages, the pipeline's order: the pre-processor's.
        assertThat(keptMappings.keptXslt("p").orElseThrow().xslt().getUuid()).isEqualTo("pre");

        keptMappings.setXsltStages(new XsltStages(documentAccess, heldSchemas, Mockito.mock(SteppingWalks.class)));
        final KeptMappings.KeptXslt kept = keptMappings.keptXslt("p").orElseThrow();
        assertThat(kept.xslt().getUuid()).isEqualTo("tr");
        assertThat(kept.element()).isEqualTo("translation");
        assertThat(kept.kept().payload().get("mapping")).isEqualTo(Map.of("input", "json"));
    }
}
