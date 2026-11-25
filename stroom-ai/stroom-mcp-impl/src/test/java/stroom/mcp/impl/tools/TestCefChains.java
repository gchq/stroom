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

import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/// A CEF forwarder's shape told from its elements and effective properties: two stages (an open or own XSLT, then a
/// shared XSLT writing the records a producer, writer or appender takes) or one; never an Events template's
/// decoration.
class TestCefChains {

    private static final DocRef TEMPLATE = new DocRef(PipelineDoc.TYPE, "t", "Kafka Forwarding");
    private static final DocRef CHILD = new DocRef(PipelineDoc.TYPE, "c", "Vault CEF");
    private static final String KAFKA_XSLT = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                             + "version=\"2.0\" xmlns=\"kafka-records:1\"><xsl:template match=\"/\">"
                                             + "<kafkaRecords/></xsl:template></xsl:stylesheet>";
    private static final String DECORATION = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                             + "version=\"2.0\"><xsl:template match=\"@*|node()\"><xsl:copy>"
                                             + "<xsl:apply-templates select=\"@*|node()\"/></xsl:copy></xsl:template>"
                                             + "</xsl:stylesheet>";

    private final Map<String, String> code = new HashMap<>(Map.of("shared", KAFKA_XSLT, "dec", DECORATION,
            "own", KAFKA_XSLT));
    private final Function<DocRef, Optional<String>> reader = ref -> Optional.ofNullable(code.get(ref.getUuid()));

    @Test
    void aTemplateWithAnOpenSlotThenASharedXsltBeforeTheProducerHasTwoStages() {
        final MergedPipeline template = MergedPipeline.of(List.of(layer(TEMPLATE, chain("kafkaProducer",
                "kafkaOutputFilter", "shared"))));

        final CefChains.Chain chain = CefChains.ofTemplate(template, reader).orElseThrow();

        assertThat(chain.flattener()).isEqualTo("flattenFilter");
        assertThat(chain.stage()).isEqualTo("kafkaOutputFilter");
        assertThat(chain.sharedXslt().getUuid()).isEqualTo("shared");
        assertThat(chain.toMap()).containsEntry("flattener", "flattenFilter");
        // As the forwarder's template: its own flattener, the shared XSLT inherited.
        final MergedPipeline child = MergedPipeline.of(List.of(layer(TEMPLATE, chain("kafkaProducer",
                "kafkaOutputFilter", "shared")), layer(CHILD, new PipelineDataBuilder().addProperty(xslt(
                "flattenFilter", "own")))));
        assertThat(CefChains.ofForwarder(child, reader)).map(CefChains.Chain::stage).contains("kafkaOutputFilter");
        assertThat(CefChains.linesElement(child, "flattenFilter")).isEqualTo("kafkaOutputFilter");
        assertThat(CefChains.ofTemplate(child, reader)).as("no open slot left").isEmpty();
    }

    @Test
    void anEventsTemplatesDecorationBeforeItsWriterIsNoSharedStage() {
        final MergedPipeline template = MergedPipeline.of(List.of(layer(TEMPLATE, chain("streamAppender",
                "decorationFilter", "dec"))));

        assertThat(CefChains.ofTemplate(template, reader)).isEmpty();
    }

    @Test
    void oneStageOrASharedXsltTheUserCantReadIsNotTwoStages() {
        // One open slot straight to the producer.
        final PipelineDataBuilder one = new PipelineDataBuilder()
                .addElement(new PipelineElement("flattenFilter", "XSLTFilter"))
                .addElement(new PipelineElement("kafkaProducer", "StandardKafkaProducer"))
                .addLink("flattenFilter", "kafkaProducer");
        final MergedPipeline single = MergedPipeline.of(List.of(layer(TEMPLATE, one)));
        assertThat(CefChains.ofTemplate(single, reader)).isEmpty();
        assertThat(CefChains.linesElement(single, "flattenFilter")).isEqualTo("flattenFilter");

        code.remove("shared");
        assertThat(CefChains.ofTemplate(MergedPipeline.of(List.of(layer(TEMPLATE, chain("kafkaProducer",
                "kafkaOutputFilter", "shared")))), reader)).isEmpty();
    }

    @Test
    void aSecondXsltOfTheForwardersOwnIsNoInheritedStage() {
        // Both XSLTs its own: the review still reads its lines at the second, but it doesn't inherit one.
        final MergedPipeline own = MergedPipeline.of(List.of(layer(TEMPLATE, chain("kafkaProducer",
                "kafkaOutputFilter", null)), layer(CHILD, new PipelineDataBuilder()
                .addProperty(xslt("flattenFilter", "own"))
                .addProperty(xslt("kafkaOutputFilter", "shared")))));

        assertThat(CefChains.ofForwarder(own, reader)).isEmpty();
        assertThat(CefChains.linesElement(own, "flattenFilter")).isEqualTo("kafkaOutputFilter");
    }

    // A parser, an open flattener slot, then the stage given (its XSLT set if given), a schema filter and the last
    // element: a producer, a writer or an appender.
    private static PipelineDataBuilder chain(final String last, final String stage, final String stageXslt) {
        final List<String> ids = new ArrayList<>(List.of("xmlParser", "flattenFilter", stage, "schemaFilter", last));
        final Map<String, String> types = Map.of("xmlParser", "XMLParser", "flattenFilter", "XSLTFilter", stage,
                "XSLTFilter", "schemaFilter", "SchemaFilter", "kafkaProducer", "StandardKafkaProducer",
                "streamAppender", "StreamAppender");
        final PipelineDataBuilder data = new PipelineDataBuilder();
        for (int i = 0; i < ids.size(); i++) {
            data.addElement(new PipelineElement(ids.get(i), types.get(ids.get(i))));
            if (i > 0) {
                data.addLink(ids.get(i - 1), ids.get(i));
            }
        }
        if (stageXslt != null) {
            data.addProperty(xslt(stage, stageXslt));
        }
        return data;
    }

    private static PipelineProperty xslt(final String element, final String uuid) {
        return new PipelineProperty(element, "xslt", new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, uuid,
                uuid)));
    }

    private static PipelineLayer layer(final DocRef ref, final PipelineDataBuilder data) {
        return new PipelineLayer(ref, data.build());
    }
}
