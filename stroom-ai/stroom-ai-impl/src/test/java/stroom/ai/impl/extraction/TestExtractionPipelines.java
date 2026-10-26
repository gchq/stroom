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

package stroom.ai.impl.extraction;

import stroom.ai.impl.tools.MergedPipeline;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineLayer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestExtractionPipelines {

    private static final DocRef TEMPLATE = new DocRef(PipelineDoc.TYPE, "template", "Search Extraction");
    private static final DocRef CHILD = new DocRef(PipelineDoc.TYPE, "child", "Logon extraction");
    private static final DocRef XSLT = new DocRef("XSLT", "xslt-1", "Logon extraction");

    @Test
    void theXsltWritingTheRecordsIsTheLastBeforeTheSearchOutput() {
        // As the Search Extraction template has it: another XSLT after the output, for detections.
        final PipelineData template = chain(new PipelineDataBuilder(),
                "Source:Source", "xmlParser:XMLParser", "splitFilter:SplitFilter",
                "idEnrichmentFilter:IdEnrichmentFilter", "prepare:XSLTFilter", "xsltFilter:XSLTFilter",
                "searchResultOutputFilter:SearchResultOutputFilter", "toDetections:XSLTFilter")
                .build();
        final PipelineData child = new PipelineDataBuilder()
                .addProperty(PipelineDataUtil.createProperty("xsltFilter", "xslt", XSLT))
                .build();
        final MergedPipeline merged = MergedPipeline.of(List.of(new PipelineLayer(TEMPLATE, template),
                new PipelineLayer(CHILD, child)));

        assertThat(ExtractionPipelines.outputXslt(merged)).contains("xsltFilter");
        assertThat(ExtractionPipelines.xsltOf(merged, "xsltFilter")).contains(XSLT);
        assertThat(ExtractionPipelines.xsltOf(merged, "prepare")).isEmpty();
    }

    @Test
    void aPipelineWithNoSearchOutputHasNoSuchXslt() {
        final PipelineData translation = chain(new PipelineDataBuilder(),
                "Source:Source", "xmlParser:XMLParser", "translationFilter:XSLTFilter", "writer:XMLWriter")
                .build();

        assertThat(ExtractionPipelines.outputXslt(MergedPipeline.of(List.of(new PipelineLayer(CHILD,
                translation))))).isEmpty();
    }

    @Test
    void aNewExtractionIsTriedInAPipelineAsTheTemplateMakes() {
        final PipelineDoc trial = ExtractionPipelines.trial(XSLT);
        final MergedPipeline merged = MergedPipeline.of(List.of(new PipelineLayer(trial.asDocRef(),
                trial.getPipelineData())));

        assertThat(merged.chain()).containsExactly("xmlParser", "splitFilter", "idEnrichmentFilter",
                ExtractionPipelines.XSLT_ELEMENT);
        // Stepping only takes code for an XSLT element whose XSLT is set.
        assertThat(ExtractionPipelines.xsltOf(merged, ExtractionPipelines.XSLT_ELEMENT)).contains(XSLT);
        assertThat(merged.properties().get(new MergedPipeline.ElementProperty("splitFilter", "splitDepth")))
                .isEqualTo(1);
    }

    // Elements given as "id:type", each linked to the next.
    private static PipelineDataBuilder chain(final PipelineDataBuilder builder, final String... elements) {
        String previous = null;
        for (final String element : elements) {
            final String[] parts = element.split(":");
            builder.addElement(PipelineDataUtil.createElement(parts[0], parts[1], null, null));
            if (previous != null) {
                builder.addLink(previous, parts[0]);
            }
            previous = parts[0];
        }
        return builder;
    }
}
