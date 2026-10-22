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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestMergedPipeline {

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private DocRef template;

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        template = new DocRef(PipelineDoc.TYPE, "t", "Event Data (Text)");
        final PipelineDataBuilder templateData = new PipelineDataBuilder()
                .addElement(new PipelineElement("dsParser", "DSParser"))
                .addElement(new PipelineElement("decorationFilter", "XSLTFilter"))
                .addElement(new PipelineElement("translationFilter", "XSLTFilter"))
                .addProperty(code("decorationFilter", "xslt", new DocRef(XsltDoc.TYPE, "d", "Decoration")));
        fixture.add(template, PipelineDoc.builder().uuid("t").name(template.getName())
                .pipelineData(templateData.build()).build(), true);
        final PipelineDataBuilder ownData = new PipelineDataBuilder()
                .addProperty(code("dsParser", "textConverter", new DocRef(TextConverterDoc.TYPE, "tc", "Acme")))
                .addProperty(code("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "x", "Acme")));
        fixture.add(new DocRef(PipelineDoc.TYPE, "p", "Acme"), PipelineDoc.builder().uuid("p").name("Acme")
                .parentPipeline(template).pipelineData(ownData.build()).build(), true);
    }

    @Test
    void translationDocsMarkCodeInheritedFromTheTemplate() {
        final MergedPipeline layers = MergedPipeline.load(fixture.documentAccess(), "p").orElseThrow();

        final Map<Object, Map<String, Object>> docs = layers.translationDocMaps().stream()
                .collect(Collectors.toMap(doc -> doc.get("element"), doc -> doc));

        assertThat(docs.get("decorationFilter").get("inherited_from_template")).isEqualTo(true);
        assertThat(docs.get("decorationFilter").get("set_by")).isEqualTo("Event Data (Text)");
        assertThat(docs.get("translationFilter").get("inherited_from_template")).isEqualTo(false);
        assertThat(((Map<?, ?>) docs.get("dsParser").get("doc")).get("type")).isEqualTo(TextConverterDoc.TYPE);
    }

    @Test
    void thePipelinesOwnElementsAreThoseItConfigures() {
        final MergedPipeline layers = MergedPipeline.load(fixture.documentAccess(), "p").orElseThrow();

        assertThat(layers.ownElements()).containsExactlyInAnyOrder("dsParser", "translationFilter");
        // Its own XSLT is where its translation happens.
        assertThat(layers.defaultOutputs()).containsExactly("translationFilter");
        assertThat(layers.parser()).isEqualTo("dsParser");
        // The template adds both its XSLTs itself.
        assertThat(MergedPipeline.load(fixture.documentAccess(), "t").orElseThrow().defaultOutputs())
                .isEqualTo(List.of("decorationFilter", "translationFilter"));
    }

    @Test
    void aPipelineWhoseTemplateCantBeReadIsNotLoaded() {
        fixture.visible.remove("t");

        // Failing closed: what it inherits can't be known.
        assertThat(MergedPipeline.load(fixture.documentAccess(), "p")).isEmpty();
        assertThat(MergedPipeline.load(fixture.documentAccess(), "no-such-pipeline")).isEmpty();
    }

    private static PipelineProperty code(final String element, final String name, final DocRef doc) {
        return PipelineProperty.builder().element(element).name(name).value(new PipelinePropertyValue(doc)).build();
    }
}
