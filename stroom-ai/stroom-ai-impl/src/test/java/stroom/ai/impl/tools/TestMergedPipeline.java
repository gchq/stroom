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
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestMergedPipeline {

    private static final DocRef TEMPLATE = new DocRef(PipelineDoc.TYPE, "t-1", "Event Data (JSON)");
    private static final DocRef CHILD = new DocRef(PipelineDoc.TYPE, "c-1", "Keycloak-V1.2-Events");
    private static final DocRef MAXMIND = new DocRef(PipelineDoc.TYPE, "r-1", "Reference Loader");
    private static final DocRef MAXMIND_FEED = new DocRef("Feed", "f-1", "Maxmind-GeoLite2-City-IPv4-CSV");
    private static final DocRef KEYCLOAK_XSLT = new DocRef(XsltDoc.TYPE, "x-1", "Keycloak-V1.2-Events");

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

    @Test
    void aChildsRemovalDropsTheElementAndItsDanglingLink() {
        final Map<String, Object> described = MergedPipeline.of(keycloakLayers()).description();

        assertThat(ids(described.get("elements"))).doesNotContain("decorationFilter");
        assertThat(described.get("links")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .doesNotContain(link("decorationFilter", "schemaFilter"))
                .contains(link("translationFilter", "schemaFilter"));
        assertThat(described.get("chain"))
                .isEqualTo(List.of("jsonParser", "translationFilter", "schemaFilter", "streamAppender"));
    }

    @Test
    void propertiesRecordTheirValueAndTheLayerThatSetThem() {
        final Map<String, Map<?, ?>> properties = properties(MergedPipeline.of(keycloakLayers()).description());

        assertThat(properties.get("schemaFilter.schemaGroup")).isEqualTo(Map.of("element", "schemaFilter",
                "name", "schemaGroup", "value", "EVENTS", "from", MergedPipeline.docRef(TEMPLATE)));
        assertThat(properties.get("translationFilter.xslt").get("value"))
                .isEqualTo(MergedPipeline.docRef(KEYCLOAK_XSLT));
        assertThat(properties.get("translationFilter.xslt").get("from")).isEqualTo(MergedPipeline.docRef(CHILD));
        assertThat(properties.get("jsonParser.addRootObject").get("value")).isEqualTo(false);
    }

    @Test
    void aChildsPropertyOverridesItsTemplatesValue() {
        final List<PipelineLayer> layers = new ArrayList<>(keycloakLayers());
        layers.add(new PipelineLayer(CHILD, new PipelineDataBuilder()
                .addProperty(PipelineProperty.builder().element("schemaFilter").name("schemaGroup")
                        .value(new PipelinePropertyValue("CUSTOM")).build())
                .build()));

        assertThat(properties(MergedPipeline.of(layers).description()).get("schemaFilter.schemaGroup").get("value"))
                .isEqualTo("CUSTOM");
    }

    @Test
    void itsReferencesAndWhatItRemovesFromItsTemplateAreReported() {
        final Map<String, Object> described = MergedPipeline.of(keycloakLayers()).description();

        final Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("element", "translationFilter");
        reference.put("name", "pipelineReference");
        reference.put("pipeline", MergedPipeline.docRef(MAXMIND));
        reference.put("feed", MergedPipeline.docRef(MAXMIND_FEED));
        reference.put("stream_type", "Reference");
        assertThat(described.get("references")).isEqualTo(List.of(reference));
        assertThat(described.get("removed_by_this_pipeline")).isEqualTo(Map.of(
                "elements", List.of(Map.of("id", "decorationFilter", "type", "XSLTFilter")),
                "links", List.of(link("translationFilter", "decorationFilter"))));
        assertThat(described.get("template_chain"))
                .isEqualTo(List.of(MergedPipeline.docRef(TEMPLATE), MergedPipeline.docRef(CHILD)));
        assertThat(described.get("own_elements")).isEqualTo(List.of("jsonParser", "translationFilter"));
    }

    @Test
    void aLoadedPipelineIsDescribedWithItsNameAndCode() {
        final Map<String, Object> described = MergedPipeline.load(fixture.documentAccess(), "p").orElseThrow()
                .description();

        assertThat(described.get("pipeline")).asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("uuid", "p")
                .containsEntry("name", "Acme")
                .containsEntry("description", null);
        assertThat(described.get("removed_by_this_pipeline")).isEqualTo(Map.of());
        assertThat(described.get("translation_docs")).asInstanceOf(InstanceOfAssertFactories.LIST).hasSize(3);
    }

    // Shaped like the live Keycloak-V1.2-Events pipeline: the child removes the template's empty decoration step,
    // re-links translation straight to schema validation and adds a GeoIP lookup.
    private static List<PipelineLayer> keycloakLayers() {
        final PipelineDataBuilder template = new PipelineDataBuilder()
                .addElement(new PipelineElement("jsonParser", "JSONParser"))
                .addElement(new PipelineElement("translationFilter", "XSLTFilter"))
                .addElement(new PipelineElement("decorationFilter", "XSLTFilter"))
                .addElement(new PipelineElement("schemaFilter", "SchemaFilter"))
                .addElement(new PipelineElement("streamAppender", "StreamAppender"))
                .addLink(new PipelineLink("jsonParser", "translationFilter"))
                .addLink(new PipelineLink("translationFilter", "decorationFilter"))
                .addLink(new PipelineLink("decorationFilter", "schemaFilter"))
                .addLink(new PipelineLink("schemaFilter", "streamAppender"))
                .addProperty(PipelineProperty.builder().element("schemaFilter").name("schemaGroup")
                        .value(new PipelinePropertyValue("EVENTS")).build())
                .addProperty(PipelineProperty.builder().element("streamAppender").name("streamType")
                        .value(new PipelinePropertyValue("Events")).build());
        final PipelineDataBuilder child = new PipelineDataBuilder()
                .removeElement(new PipelineElement("decorationFilter", "XSLTFilter"))
                .addLink(new PipelineLink("translationFilter", "schemaFilter"))
                .removeLink(new PipelineLink("translationFilter", "decorationFilter"))
                .addProperty(code("translationFilter", "xslt", KEYCLOAK_XSLT))
                .addProperty(PipelineProperty.builder().element("jsonParser").name("addRootObject")
                        .value(new PipelinePropertyValue(false)).build())
                .addPipelineReference(new PipelineReference("translationFilter", "pipelineReference", MAXMIND,
                        MAXMIND_FEED, "Reference"));
        return List.of(new PipelineLayer(TEMPLATE, template.build()), new PipelineLayer(CHILD, child.build()));
    }

    private static Map<String, Object> link(final String from, final String to) {
        return Map.of("from", from, "to", to);
    }

    private static List<Object> ids(final Object elements) {
        return ((List<?>) elements).stream().<Object>map(element -> ((Map<?, ?>) element).get("id")).toList();
    }

    // The described properties by element.name.
    private static Map<String, Map<?, ?>> properties(final Map<String, Object> described) {
        return ((List<?>) described.get("properties")).stream()
                .map(property -> (Map<?, ?>) property)
                .collect(Collectors.toMap(property -> property.get("element") + "." + property.get("name"),
                        property -> property));
    }

    private static PipelineProperty code(final String element, final String name, final DocRef doc) {
        return PipelineProperty.builder().element(element).name(name).value(new PipelinePropertyValue(doc)).build();
    }
}
