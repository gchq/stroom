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

import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.mcp.impl.tools.PipelineWrites.Filled;
import stroom.mcp.impl.tools.PipelineWrites.Layout;
import stroom.mcp.impl.tools.PipelineWrites.Slot;
import stroom.mcp.impl.tools.PipelineWrites.Swap;
import stroom.mcp.impl.tools.PipelineWrites.Typed;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The pipeline write tools' helpers (the Stroom MCP server's `tools/pipeline_writes.py`, and its tests in
/// `tests/test_slots.py`, `tests/test_property_types.py`, `tests/test_xml_fragments.py`, `tests/test_stepping.py`,
/// `tests/test_plan.py`, `tests/test_tolerance.py`, `tests/test_pipelines.py`, `tests/test_off_path.py`,
/// `tests/test_surface.py` and `tests/test_index_rename.py`).
class TestPipelineWrites {

    private static final String BUILD = PipelineWorld.BUILD;
    private static final Map<String, String> TYPES = Map.of("jsonParser", "JSONParser", "splitFilter",
            "SplitFilter", "streamAppender", "StreamAppender", "translationFilter", "XSLTFilter");
    private static final Map<String, Object> JSON_ARRAY = Map.of("input", "json", "json_layout", "array");

    private final PipelineWorld pipelines = new PipelineWorld();
    private final PipelineWrites writes = pipelines.writes;

    @Test
    void anElementsIdIsItsTypeWithALeadingAcronymLoweredWhole() {
        assertThat(PipelineWrites.elementId("XMLFragmentParser")).isEqualTo("xmlFragmentParser");
        assertThat(PipelineWrites.elementId("DSParser")).isEqualTo("dsParser");
        assertThat(PipelineWrites.elementId("CombinedParser")).isEqualTo("combinedParser");
        assertThat(PipelineWrites.elementId("XSLTFilter")).isEqualTo("xsltFilter");
    }

    @Test
    void aChildsRemovalDropsTheElementAndItsDanglingLink() {
        // Shaped like the live Keycloak-V1.2-Events pipeline: the child removes the template's empty decoration
        // step and re-links translation straight to schema validation.
        final PipelineLayer template = layer("t-1", PipelineLayers.layer("t-1").chain("jsonParser", "JSONParser",
                "translationFilter", "XSLTFilter", "decorationFilter", "XSLTFilter", "schemaFilter", "SchemaFilter",
                "streamAppender", "StreamAppender").data());
        final PipelineDataBuilder child = new PipelineDataBuilder()
                .removeElement(new PipelineElement("decorationFilter", "XSLTFilter"))
                .addLink("translationFilter", "schemaFilter")
                .removeLink("translationFilter", "decorationFilter");

        final Layout merged = Layout.of(List.of(template, layer("c-1", child.build())));

        assertThat(merged.elements()).doesNotContainKey("decorationFilter");
        assertThat(merged.links()).doesNotContain(new PipelineLink("decorationFilter", "schemaFilter"))
                .contains(new PipelineLink("translationFilter", "schemaFilter"));
        assertThat(merged.chain()).containsExactly("jsonParser", "translationFilter", "schemaFilter",
                "streamAppender");
    }

    @Test
    void openSlotsAreTheParsersConverterAndTheFirstXslt() {
        final Layout text = Layout.of(List.of(textTemplate()));
        assertThat(slots(text, null)).containsExactly("dsParser.textConverter", "translationFilter.xslt");

        final Layout xml = Layout.of(List.of(layer("x", PipelineLayers.layer("x").chain("xmlParser", "XMLParser",
                "translationFilter", "XSLTFilter").data())));
        assertThat(slots(xml, "XMLFragmentParser")).containsExactly("translationFilter.xslt",
                "xmlFragmentParser.textConverter");
    }

    @Test
    void jsonAndXmlParsersNeedNoConverter() {
        for (final String parser : List.of("JSONParser", "XMLParser")) {
            final Layout merged = Layout.of(List.of(layer("t", PipelineLayers.layer("t").chain("parser", parser,
                    "translationFilter", "XSLTFilter").data())));
            assertThat(slots(merged, null)).containsExactly("translationFilter.xslt");

            // A build with no converter at all.
            final Filled filled = writes.fillOpenSlots(BUILD, merged, null, List.of());

            assertThat(filled.properties()).isEmpty();
            assertThat(filled.filled()).isEmpty();
            assertThat(filled.stillOpen()).containsExactly("translationFilter.xslt");
        }
    }

    @Test
    void slotsAreFilledFromTheBuildsOnlyCandidates() {
        pipelines.textConverter("tc", "FW");
        pipelines.xslt("x1", "FW-Events", Map.of());
        pipelines.xslt("x2", "FW-INDEX-XSLT", null);
        final Layout merged = Layout.of(List.of(textTemplate()));

        final Filled filled = writes.fillOpenSlots(BUILD, merged, null, List.of());

        assertThat(filled.properties()).containsExactly(
                PropertyValue.document("dsParser", "textConverter", "tc", "TextConverter"),
                PropertyValue.document("translationFilter", "xslt", "x1", "XSLT"));
        assertThat(filled.filled()).containsExactly("dsParser.textConverter = FW (the build's only TextConverter)",
                "translationFilter.xslt = FW-Events (the build's only XSLT)");
        assertThat(filled.stillOpen()).isEmpty();

        // Given properties are kept; the XSLT with the mapping wins over the indexing one.
        final Filled given = writes.fillOpenSlots(BUILD, merged, null, List.of(
                PropertyValue.document("dsParser", "textConverter", "other", "TextConverter")));

        assertThat(given.properties()).extracting(PropertyValue::docUuid).containsExactly("other", "x1");
        assertThat(given.filled()).hasSize(1);
    }

    @Test
    void aParserWithNoConverterInTheBuildIsRefused() {
        pipelines.xslt("x1", "FW-Events", Map.of());

        assertThatThrownBy(() -> writes.fillOpenSlots(BUILD, Layout.of(List.of(textTemplate())), null, List.of()))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("needs a text converter and build 'acme-v1' has none");
    }

    @Test
    void withNoXsltYetThePipelineMayBeCreatedWithTheSlotStillOpen() {
        pipelines.textConverter("tc", "FW");

        final Filled filled = writes.fillOpenSlots(BUILD, Layout.of(List.of(textTemplate())), null, List.of());

        assertThat(filled.stillOpen()).containsExactly("translationFilter.xslt");
        assertThat(filled.properties()).extracting(PropertyValue::name).containsExactly("textConverter");
    }

    @Test
    void withTwoConvertersTheModelMustChoose() {
        pipelines.textConverter("tc", "FW");
        pipelines.textConverter("tc2", "FW2");
        pipelines.xslt("x1", "FW-Events", Map.of());

        assertThatThrownBy(() -> writes.fillOpenSlots(BUILD, Layout.of(List.of(textTemplate())), null, List.of()))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("has 2 TextConverter documents (FW (tc), FW2 (tc2)): pass the right one in "
                                      + "set_properties.");
    }

    @Test
    void aStringForABooleanIsWrittenAsABoolean() {
        // Seen: addRootObject given as "false" and written {'string': 'false'}; every step of the pipeline then
        // outlasted Stroom's wait, on any stream.
        final Typed typed = writes.typedProperties(TYPES, List.of(
                PropertyValue.plain("jsonParser", "addRootObject", "false"),
                PropertyValue.plain("splitFilter", "splitCount", "100"),
                PropertyValue.plain("streamAppender", "streamType", "Records")));

        assertThat(typed.properties()).extracting(PropertyValue::value).containsExactly(false, 100L, "Records");
        final List<PipelinePropertyValue> values = new ArrayList<>();
        typed.properties().forEach(property -> values.add(writes.value(property,
                typed.keys().get(property.dotted()))));
        assertThat(values).containsExactly(new PipelinePropertyValue(false), new PipelinePropertyValue(100),
                new PipelinePropertyValue("Records"));
    }

    @Test
    void aValueNotOfItsTypeOrAPropertyTheElementLacksIsRefused() {
        assertThatThrownBy(() -> writes.typedProperties(TYPES, List.of(
                PropertyValue.plain("jsonParser", "addRootObject", "no"))))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("jsonParser.addRootObject is true or false, not 'no'");
        assertThatThrownBy(() -> writes.typedProperties(TYPES, List.of(
                PropertyValue.plain("splitFilter", "splitCount", "many"))))
                .hasMessage("splitFilter.splitCount is a whole number, not 'many'");
        assertThatThrownBy(() -> writes.typedProperties(TYPES, List.of(
                PropertyValue.plain("jsonParser", "addRootObjects", false))))
                .hasMessage("jsonParser (JSONParser) has no property 'addRootObjects'; its properties are "
                            + "['addRootObject']");
    }

    @Test
    void withoutDeclaredTypesValuesAreWrittenAsGiven() {
        // A Stroom that gives no property types.
        Mockito.when(pipelines.propertyTypes.declared()).thenReturn(Map.of());

        final Typed typed = writes.typedProperties(TYPES, List.of(
                PropertyValue.plain("jsonParser", "addRootObject", "x")));

        assertThat(typed.keys()).isEmpty();
        assertThat(typed.properties()).extracting(PropertyValue::value).containsExactly("x");
    }

    @Test
    void theWrapperParserCanBeSwappedInWhereTheTemplatesWas() {
        final PipelineLayer template = layer("t", PipelineLayers.layer("t").chain("Source", "Source",
                "xmlParser", "XMLParser", "splitFilter", "SplitFilter", "translationFilter", "XSLTFilter").data());

        final Swap swap = PipelineWrites.swapParser(Layout.of(List.of(template)), "XMLFragmentParser");

        assertThat(swap.newId()).isEqualTo("xmlFragmentParser");
        assertThat(swap.oldType()).isEqualTo("XMLParser");
        assertThat(swap.addedElements()).containsExactly(new PipelineElement("xmlFragmentParser",
                "XMLFragmentParser"));
        assertThat(swap.removed()).isEqualTo(new PipelineElement("xmlParser", "XMLParser"));
        assertThat(swap.addedLinks()).containsExactly(new PipelineLink("Source", "xmlFragmentParser"),
                new PipelineLink("xmlFragmentParser", "splitFilter"));
        assertThat(swap.removedLinks()).containsExactly(new PipelineLink("Source", "xmlParser"),
                new PipelineLink("xmlParser", "splitFilter"));
        // The child's layer applied over the template gives the swapped chain, fed from Source (seen: the new
        // parser was linked onwards only, so nothing fed it, the UI didn't show it and stepping failed).
        final Layout child = Layout.of(List.of(template, layer("c", swap.applyTo(new PipelineDataBuilder())
                .build())));
        assertThat(child.elements().keySet()).containsExactlyInAnyOrder("Source", "xmlFragmentParser",
                "splitFilter", "translationFilter");
        assertThat(child.chain().subList(0, 2)).containsExactly("Source", "xmlFragmentParser");
        assertThatThrownBy(() -> PipelineWrites.swapParser(Layout.of(List.of(template)), "XSLTFilter"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("replace_parser must be one of ['CombinedParser', 'DSParser', 'JSONParser', "
                            + "'XMLFragmentParser', 'XMLParser']");
    }

    @Test
    void aTemplateWithNoSourceElementHasTheNewParserFedFromAddedSource() {
        // A template with no Source element (Event Data (XML), locally and live): the new parser is fed from a
        // Source added as Stroom's own children write it (seen: the UI showed Source linked to nothing).
        final PipelineLayer legacy = layer("t", PipelineLayers.layer("t").chain("xmlParser", "XMLParser",
                "splitFilter", "SplitFilter").data());

        final Swap swap = PipelineWrites.swapParser(Layout.of(List.of(legacy)), "XMLFragmentParser");

        assertThat(swap.addedElements()).containsExactly(new PipelineElement("Source", "Source"),
                new PipelineElement("xmlFragmentParser", "XMLFragmentParser"));
        assertThat(swap.addedLinks()).containsExactly(new PipelineLink("Source", "xmlFragmentParser"),
                new PipelineLink("xmlFragmentParser", "splitFilter"));
        final Layout child = Layout.of(List.of(legacy, layer("c", swap.applyTo(new PipelineDataBuilder())
                .build())));
        assertThat(child.chain().subList(0, 3)).containsExactly("Source", "xmlFragmentParser", "splitFilter");
    }

    @Test
    void aJsonArrayMappingSetsTheParserToReadEachItemAsARecord() {
        // Seen: four 755 KB arrays each stepped as one record of 985 events, every step outlasting Stroom's wait.
        final Map<String, String> elements = new LinkedHashMap<>();
        elements.put("jsonParser", "JSONParser");
        elements.put("translationFilter", "XSLTFilter");
        pipelines.xslt("x1", "Acme-Events", JSON_ARRAY);
        pipelines.xslt("x2", "Acme-Lines", Map.of("input", "json", "json_layout", "lines"));
        pipelines.xslt("x3", "Acme-Hand-Written", null);
        final List<PropertyValue> xslt = List.of(PropertyValue.document("translationFilter", "xslt", "x1", "XSLT"));

        assertThat(writes.jsonArrayParser(elements, xslt, false))
                .contains(PropertyValue.plain("jsonParser", "addRootObject", false));
        // JSON lines need the root map.
        assertThat(writes.jsonArrayParser(elements, List.of(PropertyValue.document("translationFilter", "xslt",
                "x2", "XSLT")), false)).isEmpty();
        // With no mapping (an XSLT written by hand, or none yet), the sample decides: seen, never set otherwise.
        final List<PropertyValue> unmapped = List.of(PropertyValue.document("translationFilter", "xslt", "x3",
                "XSLT"));
        assertThat(writes.jsonArrayParser(elements, unmapped, false)).isEmpty();
        assertThat(writes.jsonArrayParser(elements, List.of(), true))
                .contains(PropertyValue.plain("jsonParser", "addRootObject", false));
        // The agent's own choice stands.
        final List<PropertyValue> chosen = new ArrayList<>(xslt);
        chosen.add(PropertyValue.plain("jsonParser", "addRootObject", true));
        assertThat(writes.jsonArrayParser(elements, chosen, false)).isEmpty();
    }

    @Test
    void theBuildsSampleIsAJsonArrayOnlyWhenItsProfileSaysSo() {
        assertThat(writes.sampleIsArray(BUILD)).isFalse();
        pipelines.feeds.facts.ownRaw = List.of(7L);
        pipelines.feeds.facts.profiler = text -> FakeBuildFacts.profileOf("json array");

        assertThat(writes.sampleIsArray(BUILD)).isTrue();
        assertThat(writes.sampleIsArray(null)).isFalse();
    }

    @Test
    void createPipelineRefusesDocumentsFromOutsideTheBuild() {
        pipelines.xslt("x", "FortiOS-Events", null);
        final List<PropertyValue> properties = List.of(PropertyValue.document("translationFilter", "xslt", "x",
                "XSLT"));

        assertThatThrownBy(() -> writes.ownDocuments("fortios", properties, false))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("is not a document of build 'fortios'");
        // Its own build's XSLT.
        writes.ownDocuments(BUILD, properties, false);
        // The user said so.
        writes.ownDocuments("fortios", properties, true);

        // A production XSLT.
        pipelines.world.add(XsltDoc.TYPE, "Shared-Events", pipelines.world.folder("System/XSLT").uuid, "prod");
        assertThatThrownBy(() -> writes.ownDocuments(BUILD, List.of(PropertyValue.document("translationFilter",
                "xslt", "prod", "XSLT")), false))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("reuse_existing_docs=true");
    }

    @Test
    void createPipelineRefusesAParserThatCannotReadTheSample() {
        final Layout merged = Layout.of(List.of(layer("t", PipelineLayers.layer("t").chain("jsonParser",
                "JSONParser", "translationFilter", "XSLTFilter").data())));
        // No sample uploaded yet: nothing to check.
        writes.parserReadsSample(BUILD, merged, null, false);
        pipelines.feeds.facts.ownRaw = List.of(7L);
        pipelines.feeds.facts.profiler = text -> {
            final Map<String, Object> profile = new LinkedHashMap<>(FakeBuildFacts.profileOf("key=value"));
            profile.put("suggested_parser", "Data Splitter splitting on spaces then =");
            return profile;
        };

        assertThatThrownBy(() -> writes.parserReadsSample(BUILD, merged, null, false))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("is key=value, which this template's JSONParser cannot read; it needs "
                                      + "DSParser (Data Splitter splitting on spaces then =)");
        // Reads anything.
        writes.parserReadsSample(BUILD, merged, "CombinedParser", false);
        writes.parserReadsSample(BUILD, merged, null, true);
    }

    @Test
    void theTemplateMayBeAName() {
        pipelines.template(PipelineWorld.TEMPLATE_UUID, "Event Data (Text)", PipelineLayers.layer("t"));

        assertThat(writes.templateUuid(PipelineWorld.TEMPLATE_UUID)).isEqualTo(PipelineWorld.TEMPLATE_UUID);
        assertThat(writes.templateUuid("Event Data (Text)")).isEqualTo(PipelineWorld.TEMPLATE_UUID);
        assertThatThrownBy(() -> writes.templateUuid(null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("Give template_uuid");
        assertThatThrownBy(() -> writes.templateUuid("Event Data (JSON)"))
                .hasMessage("No Pipeline document(s) named 'Event Data (JSON)' (find_reference_data lists "
                            + "loaders)");
    }

    @Test
    void weakeningValidationToGetPastAnErrorIsRefused() {
        assertThatThrownBy(() -> PipelineWrites.keepValidation(Map.of("schemaFilter", "SchemaFilter"), List.of(
                PropertyValue.plain("schemaFilter", "schemaValidation", "false"))))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("['schemaFilter'] validate the pipeline's output against its schema")
                .hasMessageContaining("Fix the output instead");
    }

    @Test
    void schemaValidationIsNeverChangedOnlyTheOutput() {
        final PipelineDoc before = pipelines.buildPipeline("p", "ACME - Indexing", null,
                PipelineLayers.layer("p").chain("xsltFilter", "XSLTFilter", "schemaFilter", "SchemaFilter"));

        assertThatThrownBy(() -> writes.setPipelineProperty(PortedToolCalls.context(), "p",
                PropertyValue.plain("schemaFilter", "schemaValidation", "false")))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("Fix the output instead");
        assertThat(pipelines.pipelineDoc("p")).isSameAs(before);
    }

    @Test
    void theIndexNameIsSetOnAPipelineOnlyAsItsDocNamesIt() {
        // Seen in testing: the doc named as the user said, the pipeline the plan's name, and the index template
        // made for the pipeline's, so for an index Stroom doesn't search.
        pipelines.world.inBuild(BUILD, ElasticIndexDoc.TYPE, "ACME-ES", "i");
        pipelines.world.docs.put("i", ElasticIndexDoc.builder().uuid("i").name("ACME-ES").indexName("acme-v1")
                .build());
        final PipelineDoc before = pipelines.buildPipeline("ix", "ACME-Indexing", null, PipelineLayers.layer("ix")
                .chain("elasticIndexingFilter", "ElasticIndexingFilter")
                .property("elasticIndexingFilter", "indexName", "acme-v1"));

        assertThatThrownBy(() -> writes.setPipelineProperty(PortedToolCalls.context(), "ix",
                PropertyValue.plain("elasticIndexingFilter", "indexName", "acme-other")))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("rename_index changes the index name in both");
        assertThat(pipelines.pipelineDoc("ix")).isSameAs(before);

        final Map<String, Object> set = writes.setPipelineProperty(PortedToolCalls.context(), "ix",
                PropertyValue.plain("elasticIndexingFilter", "indexName", "acme-v1"));

        assertThat(set).containsEntry("set", "elasticIndexingFilter.indexName").containsEntry("name",
                "ACME-Indexing");
        assertThat(pipelines.pipelineDoc("ix")).isNotSameAs(before);
    }

    @Test
    void aPropertyIsSetOnlyOnAnElementThePipelineHasAndOnlyInABuild() {
        pipelines.buildPipeline("p", "Acme-Events", null, PipelineLayers.layer("p").chain("jsonParser",
                "JSONParser", "translationFilter", "XSLTFilter"));
        pipelines.productionPipeline("prod", "Acme-Prod", null, PipelineLayers.layer("prod").chain("jsonParser",
                "JSONParser"));

        assertThatThrownBy(() -> writes.setPipelineProperty(PortedToolCalls.context(), "p",
                PropertyValue.plain("xsltFilter", "xslt", "x")))
                .hasMessage("No element 'xsltFilter' in this pipeline; elements are ['jsonParser', "
                            + "'translationFilter']");
        assertThatThrownBy(() -> writes.setPipelineProperty(PortedToolCalls.context(), "prod",
                PropertyValue.plain("jsonParser", "addRootObject", false)))
                .isInstanceOf(ToolRefusal.class);

        writes.setPipelineProperty(PortedToolCalls.context(), "p",
                PropertyValue.plain("jsonParser", "addRootObject", "false"));

        assertThat(PipelineDocs.addedProperties(pipelines.pipelineDoc("p")))
                .anySatisfy(property -> {
                    assertThat(property.getElement()).isEqualTo("jsonParser");
                    assertThat(property.getValue()).isEqualTo(new PipelinePropertyValue(false));
                });
    }

    @Test
    void referenceDataIsAttachedToTheTranslationStepOnceEach() {
        final DocRef loader = pipelines.productionPipeline(PipelineWorld.LOADER, "Reference Loader", null,
                PipelineLayers.layer(PipelineWorld.LOADER)).asDocRef();
        final DocRef feed = pipelines.feeds.feedAt("System/Feeds", "ACME-USERS", "f-users");
        Mockito.when(pipelines.referenceData.resolveLoader("ACME-USERS")).thenReturn(PipelineWorld.LOADER);
        pipelines.buildPipeline("p", "Acme-Events", null, PipelineLayers.layer("p").chain("jsonParser",
                "JSONParser", "translationFilter", "XSLTFilter", "decorationFilter", "XSLTFilter"));
        final List<PipelineWrites.ReferenceRequest> references = List.of(
                new PipelineWrites.ReferenceRequest("ACME-USERS", null, null));

        final Map<String, Object> first = writes.setPipelineReferences(PortedToolCalls.context(), "p", references);
        final Map<String, Object> again = writes.setPipelineReferences(PortedToolCalls.context(), "p", references);

        assertThat(first).containsEntry("added", 1).containsEntry("reference_data",
                List.of("ACME-USERS via Reference Loader on translationFilter"));
        assertThat(again).containsEntry("added", 0);
        assertThat(pipelines.pipelineDoc("p").getPipelineData().getAddedPipelineReferences())
                .singleElement()
                .satisfies(reference -> {
                    assertThat(reference.getFeed().getUuid()).isEqualTo(feed.getUuid());
                    assertThat(reference.getPipeline().getUuid()).isEqualTo(loader.getUuid());
                    assertThat(reference.getStreamType()).isEqualTo("Reference");
                });
    }

    @Test
    void aPipelineWithNoXsltStepHasNowhereForReferenceData() {
        assertThatThrownBy(() -> writes.referenceEntries(Layout.of(List.of(layer("t", PipelineLayers.layer("t")
                        .chain("jsonParser", "JSONParser").data()))),
                List.of(new PipelineWrites.ReferenceRequest("ACME-USERS", PipelineWorld.LOADER, null))))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("The pipeline has no XSLT step to attach reference data to");
    }

    @Test
    void theOpenSlotsTheBuildPlanReadsAreTheSameSlots() {
        final Map<ElementProperty, Object> values = Map.of(new ElementProperty("dsParser", "textConverter"),
                Map.of("type", "TextConverter", "uuid", "tc"));
        final List<Slot> slots = PipelineWrites.openSlots(Map.of("dsParser", "DSParser", "translationFilter",
                "XSLTFilter"), List.of("dsParser", "translationFilter"), values, null);

        assertThat(slots).containsExactly(new Slot("translationFilter", "XSLTFilter", "xslt"));
    }

    private static List<String> slots(final Layout merged, final String replaceParser) {
        return PipelineWrites.openSlots(merged.elements(), merged.chain(), merged.properties(), replaceParser)
                .stream()
                .map(Slot::dotted)
                .toList();
    }

    private static PipelineLayer textTemplate() {
        return layer("text", PipelineLayers.layer("text").chain("dsParser", "DSParser", "translationFilter",
                "XSLTFilter", "decorationFilter", "XSLTFilter", "schemaFilter", "SchemaFilter").data());
    }

    private static PipelineLayer layer(final String uuid, final PipelineData data) {
        return new PipelineLayer(new DocRef(PipelineDoc.TYPE, uuid, uuid), data);
    }
}
