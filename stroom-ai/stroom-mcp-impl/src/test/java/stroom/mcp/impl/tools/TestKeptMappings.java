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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.KeptMapping.Kept;
import stroom.docref.DocRef;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestKeptMappings {

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
}
