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
import stroom.ai.impl.AiPipelineStageConfig;
import stroom.ai.impl.AiPipelineStagesConfig;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.mcp.impl.tools.PipelineShapes.Classification;
import stroom.mcp.impl.tools.PipelineShapes.Shape;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestPipelineShapes {

    private static final Map<String, AiPipelineStageConfig> DEFAULT_MARKERS = new AiPipelineStagesConfig().byName();

    @Test
    void testStageAndBackendFromElements() {
        assertThat(classify(Map.of("jsonParser", "JSONParser", "schemaFilter", "SchemaFilter"),
                Map.of(new ElementProperty("schemaFilter", "schemaGroup"), "EVENTS")))
                .isEqualTo(new Classification("translation", null));
        assertThat(classify(Map.of("xmlParser", "XMLParser", "indexingFilter", "IndexingFilter"), Map.of()))
                .isEqualTo(new Classification("indexing", "lucene"));
        assertThat(classify(Map.of("xmlParser", "XMLParser", "elasticIndexingFilter", "ElasticIndexingFilter"),
                Map.of()))
                .isEqualTo(new Classification("indexing", "elasticsearch"));
        assertThat(classify(Map.of("jsonParser", "JSONParser", "elasticIndexingFilter", "ElasticIndexingFilter"),
                Map.of()))
                .isEqualTo(new Classification("discovery", "elasticsearch"));
        assertThat(classify(Map.of("p", "CombinedParser", "r", "ReferenceDataFilter"), Map.of()))
                .isEqualTo(new Classification("loader", null));
    }

    @Test
    void testForwardingPipelinesAreFoundByTheirKafkaProducer() {
        assertThat(classify(Map.of("p", "XMLParser", "x", "XSLTFilter", "k", "StandardKafkaProducer"), Map.of()))
                .isEqualTo(new Classification("forwarding", "kafka"));
        // Batch Search writes text from XML too: a text writer alone says nothing.
        assertThat(classify(Map.of("p", "XMLParser", "x", "XSLTFilter", "t", "TextWriter"), Map.of()).stage())
                .isNotEqualTo("forwarding");
    }

    @Test
    void testACefPipelineWritingTextIsToldByItsXslt() {
        final Map<String, String> text = Map.of("p", "XMLParser", "x", "XSLTFilter", "t", "TextWriter",
                "f", "FileAppender");
        assertThat(PipelineShapes.classify(new LinkedHashMap<>(text), Map.of(), DEFAULT_MARKERS, () -> true))
                .isEqualTo(new Classification("forwarding", "file"));
        assertThat(PipelineShapes.classify(new LinkedHashMap<>(text), Map.of(), DEFAULT_MARKERS, () -> false))
                .isEqualTo(new Classification("other", null));
        // A raw parser reads a source's own CEF: not CEF output.
        assertThat(PipelineShapes.classify(new LinkedHashMap<>(Map.of("p", "DSParser", "x", "XSLTFilter",
                "t", "TextWriter")), Map.of(), DEFAULT_MARKERS, () -> true))
                .isEqualTo(new Classification("other", null));
        // Its XSLTs are read only for a pipeline that would otherwise be 'other'.
        final AtomicBoolean asked = new AtomicBoolean();
        assertThat(PipelineShapes.classify(new LinkedHashMap<>(Map.of("p", "XMLParser", "x", "XSLTFilter",
                "k", "StandardKafkaProducer")), Map.of(), DEFAULT_MARKERS, () -> asked.getAndSet(true)))
                .isEqualTo(new Classification("forwarding", "kafka"));
        assertThat(PipelineShapes.classify(new LinkedHashMap<>(Map.of("p", "XMLParser", "s", "SchemaFilter")),
                Map.of(new ElementProperty("s", "schemaGroup"), "EVENTS"), DEFAULT_MARKERS,
                () -> asked.getAndSet(true)))
                .isEqualTo(new Classification("translation", null));
        assertThat(asked).isFalse();
    }

    @Test
    void testAnXsltWritesCefByItsLiteralOrItsKeptPlan() {
        final Map<String, XsltDoc> docs = new LinkedHashMap<>();
        docs.put("lit", xslt("lit", "<xsl:value-of select=\"concat('CEF:0|Acme|', .)\"/>", null));
        docs.put("v1", xslt("v1", "<xsl:text>CEF:1|Acme|</xsl:text>", null));
        docs.put("kept", xslt("kept", "<xsl:value-of select=\"$header\"/>", KeptMapping.withMapping(null, "cef",
                Map.of("output", "text"))));
        docs.put("index", xslt("index", "<record/>", KeptMapping.withMapping(null, "index", Map.of())));
        docs.put("batch", xslt("batch", "<xsl:value-of select=\"concat(a, ',', b)\"/>", null));
        assertThat(writesCef(docs, Map.of(), "lit")).isTrue();
        assertThat(writesCef(docs, Map.of(), "v1")).isTrue();
        assertThat(writesCef(docs, Map.of(), "kept")).isTrue();
        assertThat(writesCef(docs, Map.of(), "index")).isFalse();
        assertThat(writesCef(docs, Map.of(), "batch")).isFalse();
        // One that can't be read says nothing.
        assertThat(writesCef(docs, Map.of(), "gone")).isFalse();
    }

    @Test
    void testTheCefLiteralIsFoundInTheXsltsItImports() {
        final Map<String, XsltDoc> docs = new LinkedHashMap<>();
        docs.put("thin", xslt("thin", stylesheet("<xsl:import href=\"CEF Common\"/>"), null));
        docs.put("deep", xslt("deep", stylesheet("<xsl:include href=\"CEF Wrapper\"/>"), null));
        docs.put("broken", xslt("broken", stylesheet("<xsl:import href=\"Nowhere\"/>"), null));
        docs.put("loop", xslt("loop", stylesheet("<xsl:import href=\"Loop B\"/>"), null));
        final Map<String, XsltDoc> shared = new LinkedHashMap<>();
        shared.put("CEF Common", xslt("common", stylesheet("<xsl:template name=\"cef\">CEF:0|Acme|"
                                                           + "</xsl:template>"), null));
        shared.put("CEF Wrapper", xslt("wrapper", stylesheet("<xsl:import href=\"CEF Common\"/>"), null));
        shared.put("Loop B", xslt("loopB", stylesheet("<xsl:import href=\"Loop A\"/>"), null));
        shared.put("Loop A", xslt("loop", stylesheet("<xsl:import href=\"Loop B\"/>"), null));
        assertThat(writesCef(docs, shared, "thin")).as("one import deep").isTrue();
        assertThat(writesCef(docs, shared, "deep")).as("two imports deep").isTrue();
        assertThat(writesCef(docs, shared, "broken")).as("an import that can't be found is skipped").isFalse();
        assertThat(writesCef(docs, shared, "loop")).as("imports that loop end").isFalse();
    }

    @Test
    void testAPipelineWhoseInheritedXsltImportsCefIsForwardingToAFile() {
        final PipelineService service = PipelineLayers.service();
        final DocRef thin = new DocRef(XsltDoc.TYPE, "thin", "Thin");
        PipelineLayers.given(service, "c", PipelineLayers.of(
                PipelineLayers.layer("template")
                        .chain("xmlParser", "XMLParser", "xsltFilter", "XSLTFilter", "textWriter", "TextWriter",
                                "fileAppender", "FileAppender")
                        .property("xsltFilter", "xslt", thin),
                PipelineLayers.layer("c").property("fileAppender", "outputPaths", "/tmp/cef.log")));
        final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
        final DocFinder docFinder = Mockito.mock(DocFinder.class);
        final DocRef common = new DocRef(XsltDoc.TYPE, "common", "CEF Common");
        Mockito.when(documentAccess.read(thin, XsltDoc.class)).thenReturn(Optional.of(xslt("thin",
                stylesheet("<xsl:import href=\"CEF Common\"/>"), null)));
        Mockito.when(docFinder.findByName(XsltDoc.TYPE, "CEF Common", false)).thenReturn(List.of(common));
        Mockito.when(documentAccess.read(common, XsltDoc.class)).thenReturn(Optional.of(xslt("common",
                stylesheet("<xsl:text>CEF:0|Acme|</xsl:text>"), null)));

        final Shape shape = PipelineLayers.shapes(service, new AiConfig(), documentAccess, docFinder).shape("c");
        assertThat(shape.stage()).isEqualTo("forwarding");
        assertThat(shape.backend()).isEqualTo("file");
        assertThat(PipelineLayers.shapes(service, new AiConfig(), documentAccess, docFinder).role("c"))
                .isEqualTo("CEF");

        // Read as the user: an XSLT they may not read leaves it 'other'.
        Mockito.when(documentAccess.read(common, XsltDoc.class)).thenReturn(Optional.empty());
        assertThat(PipelineLayers.shapes(service, new AiConfig(), documentAccess, docFinder).shape("c").stage())
                .isEqualTo("other");
    }

    @Test
    void testEveryPipelinesStageIsReadByTheConfiguredMarkers() {
        // Seen: a translation pipeline the template search found by the policy's markers, classed 'other' when
        // processed, so its sample's filter was made disabled for the user to enable.
        final Map<String, String> elements = Map.of("p", "JSONParser", "x", "XSLTFilter", "s", "SchemaFilter");
        final Map<ElementProperty, Object> properties = Map.of(new ElementProperty("s", "schemaGroup"),
                "ACME-EVENTS");
        assertThat(classify(elements, properties)).isEqualTo(new Classification("other", null));

        final AiPipelineStagesConfig acme = new AiPipelineStagesConfig(
                new AiPipelineStageConfig(null, null, List.of("ACME-EVENTS"), List.of("Events")),
                null, null, null, null, null);
        assertThat(PipelineShapes.classify(elements, properties, acme.byName()))
                .isEqualTo(new Classification("translation", null));

        // And wherever a pipeline's stage is read: its shape.
        final PipelineService service = PipelineLayers.service();
        PipelineLayers.given(service, "p", PipelineLayers.of(PipelineLayers.layer("p")
                .chain("jsonParser", "JSONParser", "xsltFilter", "XSLTFilter", "schemaFilter", "SchemaFilter")
                .property("schemaFilter", "schemaGroup", "ACME-EVENTS")));
        assertThat(PipelineLayers.shapes(service, config(acme)).shape("p").stage()).isEqualTo("translation");
        assertThat(PipelineLayers.shapes(service, new AiConfig()).shape("p").stage()).isEqualTo("other");
    }

    @Test
    void testOnlyTheFirstUnsetXsltIsRequired() {
        final List<Map<String, Object>> openSlots = new ArrayList<>();
        final List<Map<String, Object>> shared = new ArrayList<>();
        PipelineShapes.slot("translationFilter", "XSLTFilter", "xslt", null, openSlots, shared);
        PipelineShapes.slot("decorationFilter", "XSLTFilter", "xslt", null, openSlots, shared);
        PipelineShapes.slot("elasticIndexingFilter", "ElasticIndexingFilter", "cluster", Map.of("name", "ES_PROD"),
                openSlots, shared);
        assertThat(openSlots.get(0)).doesNotContainKey("optional");
        assertThat(openSlots.get(1)).containsKey("optional");
        final Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("element", "elasticIndexingFilter");
        expected.put("type", "ElasticIndexingFilter");
        expected.put("property", "cluster");
        expected.put("value", Map.of("name", "ES_PROD"));
        assertThat(shared).containsExactly(expected);
    }

    @Test
    void testAShapeIsReadFromTheLayersAsStroomMergesThem() {
        final PipelineService service = PipelineLayers.service();
        final DocRef decoration = new DocRef("XSLT", "dec-uuid", "Decorate");
        PipelineLayers.given(service, "child", PipelineLayers.of(
                PipelineLayers.layer("template")
                        .element("Source", "Source")
                        .chain("dsParser", "DSParser", "translationFilter", "XSLTFilter",
                                "decorationFilter", "XSLTFilter", "schemaFilter", "SchemaFilter",
                                "streamAppender", "StreamAppender")
                        .link("Source", "dsParser")
                        .property("decorationFilter", "xslt", decoration)
                        .property("schemaFilter", "schemaGroup", "EVENTS")
                        .property("streamAppender", "streamType", "Events"),
                PipelineLayers.layer("child")
                        .property("dsParser", "textConverter", new DocRef("TextConverter", "tc", "Acme"))));
        final Shape shape = PipelineLayers.shapes(service, new AiConfig()).shape("child");

        // Stroom's Source element is left out: the chain starts with the parser.
        assertThat(shape.chain()).containsExactly("dsParser", "translationFilter", "decorationFilter",
                "schemaFilter", "streamAppender");
        assertThat(shape.parser()).isEqualTo("DSParser");
        assertThat(shape.stage()).isEqualTo("translation");
        assertThat(shape.childMustSupply()).extracting(slot -> slot.get("element"))
                .containsExactly("translationFilter");
        assertThat(shape.shared()).extracting(slot -> slot.get("element"))
                .containsExactly("dsParser", "decorationFilter", "schemaFilter");
        assertThat(shape.properties()).containsEntry("schemaFilter.schemaGroup", "EVENTS")
                .containsEntry("streamAppender.streamType", "Events");
    }

    @Test
    void testASchemaFilterIsSetByItsNamespaceToo() {
        final PipelineService service = PipelineLayers.service();
        PipelineLayers.given(service, "k", PipelineLayers.of(PipelineLayers.layer("k")
                .chain("xmlParser", "XMLParser", "schemaFilter", "SchemaFilter")
                .property("schemaFilter", "namespaceURI", "kafka-records:1")));
        final Shape shape = PipelineLayers.shapes(service, new AiConfig()).shape("k");
        assertThat(shape.childMustSupply()).isEmpty();
        assertThat(shape.shared()).extracting(slot -> slot.get("value")).containsExactly("kafka-records:1");
    }

    @Test
    void testStroomsReasonIsKeptForAPipelineItCantLoad() {
        final PipelineService service = PipelineLayers.service();
        Mockito.when(service.fetchPipelineLayers(new DocRef(PipelineDoc.TYPE, "bad")))
                .thenThrow(new PipelineModelException("Attempt to set property \"compressionLevel\" on element "
                                                      + "\"rollingFileAppender\" but property is unknown."));
        final PipelineShapes shapes = PipelineLayers.shapes(service, new AiConfig());
        assertThatThrownBy(() -> shapes.shape("bad"))
                .isInstanceOf(PipelineUnreadableException.class)
                .hasMessage("Attempt to set property \"compressionLevel\" on element \"rollingFileAppender\" but "
                            + "property is unknown.");
        // A pipeline Stroom can't load has no stage for a choice.
        assertThat(shapes.role("bad")).isNull();
    }

    @Test
    void testARoleIsTheStagesBuildFolder() {
        final PipelineService service = PipelineLayers.service();
        PipelineLayers.given(service, "i", PipelineLayers.of(PipelineLayers.layer("i")
                .chain("xmlParser", "XMLParser", "indexingFilter", "IndexingFilter")));
        assertThat(PipelineLayers.shapes(service, new AiConfig()).role("i")).isEqualTo("Indexing");
    }

    private static Classification classify(final Map<String, String> elements,
                                           final Map<ElementProperty, Object> properties) {
        return PipelineShapes.classify(new LinkedHashMap<>(elements), properties, DEFAULT_MARKERS);
    }

    private static boolean writesCef(final Map<String, XsltDoc> docs,
                                     final Map<String, XsltDoc> shared,
                                     final String uuid) {
        return PipelineShapes.writesCef(List.of(new DocRef(XsltDoc.TYPE, uuid)),
                ref -> Optional.ofNullable(docs.get(ref.getUuid())),
                href -> Optional.ofNullable(shared.get(href)));
    }

    private static XsltDoc xslt(final String uuid, final String data, final String description) {
        return XsltDoc.builder().uuid(uuid).name(uuid).data(data).description(description).build();
    }

    private static String stylesheet(final String body) {
        return "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">" + body
               + "</xsl:stylesheet>";
    }

    private static AiConfig config(final AiPipelineStagesConfig stages) {
        final AiConfig config = Mockito.mock(AiConfig.class);
        Mockito.when(config.getPipelineStages()).thenReturn(stages);
        return config;
    }
}
