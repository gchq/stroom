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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// create_pipeline (the Stroom MCP server's `tools/pipeline_writes.py` `create_pipeline` and `_standalone_kafka`).
class TestCreatePipelineTool {

    private static final String BUILD = PipelineWorld.BUILD;
    private static final String TEMPLATE = PipelineWorld.TEMPLATE_UUID;

    private final PipelineWorld pipelines = new PipelineWorld();
    private final CreatePipelineTool tool = pipelines.createTool();

    @Test
    void theUserConfirmsTheTemplateAndNameAndTheChildTakesTheBuildsDocuments() {
        textTemplate();
        pipelines.textConverter("tc", "Acme-Splitter");
        pipelines.xslt("x1", "Acme-Events", Map.of());
        final ToolCallContext context = PortedToolCalls.context();
        final String arguments = "{\"build\": \"acme-v1\", \"name\": \"Acme-V1-Events\", \"template\": "
                                 + "\"Event Data (Text)\", \"description\": \"Acme's events\"";

        final Map<String, Object> asked = PipelineWorld.json(call(arguments + "}", context));

        assertThat(asked.get("status")).isEqualTo("needs_confirmation");
        assertThat(asked.get("summary")).isEqualTo("Create pipeline 'Acme-V1-Events' from template 'Event Data "
                                                   + "(Text)'");
        assertThat(asked.get("details")).isEqualTo(Map.of("build", BUILD, "pipeline name", "Acme-V1-Events",
                "template", "Event Data (Text)", "sets", List.of("dsParser.textConverter", "translationFilter.xslt"),
                "filled from the build", List.of("dsParser.textConverter = Acme-Splitter (the build's only "
                                                 + "TextConverter)",
                        "translationFilter.xslt = Acme-Events (the build's only XSLT)")));
        // Nothing made until the user agrees.
        assertThat(pipelines.pipelineNamed("Acme-V1-Events")).isNull();

        final Map<String, Object> made = PipelineWorld.json(call(arguments + ", \"confirmation_id\": \""
                                                                 + asked.get("confirmation_id") + "\"}", context));

        final PipelineDoc doc = pipelines.pipelineNamed("Acme-V1-Events");
        assertThat(doc.getParentPipeline()).isEqualTo(new DocRef(PipelineDoc.TYPE, TEMPLATE, "Event Data (Text)"));
        assertThat(doc.getDescription()).isEqualTo("Acme's events");
        assertThat(PipelineDocs.addedProperties(doc)).containsExactly(
                new PipelineProperty("dsParser", "textConverter", new PipelinePropertyValue(new DocRef(
                        "TextConverter", "tc", "Acme-Splitter"))),
                new PipelineProperty("translationFilter", "xslt", new PipelinePropertyValue(new DocRef("XSLT", "x1",
                        "Acme-Events"))));
        // Inherited, not copied: the child adds no elements of its own.
        assertThat(doc.getPipelineData().getAddedElements()).isEmpty();
        // In the build's Events folder, with the converter and XSLT it runs.
        final DocRef ref = doc.asDocRef();
        assertThat(pipelines.world.guard().buildOf(ref)).contains(BUILD);
        assertThat(pipelines.world.tags(doc.getUuid())).contains(WriteGuard.MANAGED, WriteGuard.GENERATED);
        assertThat(pipelines.world.documentAccess.path(ref)).endsWith("/" + BUILD + "/Events");
        assertThat(pipelines.world.documentAccess.path(new DocRef("XSLT", "x1"))).endsWith("/Events");
        assertThat(made).containsEntry("type", "Pipeline").containsEntry("uuid", doc.getUuid())
                .containsEntry("template", "Event Data (Text)")
                .containsEntry("sets", List.of("dsParser.textConverter", "translationFilter.xslt"))
                .containsKey("filled_from_build").containsKey("next").doesNotContainKey("still_to_set");
    }

    @Test
    void theNameTheUserCorrectsInTheFormIsTheOneMade() {
        textTemplate();
        pipelines.textConverter("tc", "Acme-Splitter");

        final Map<String, Object> made = PipelineWorld.json(call("{\"build\": \"acme-v1\", \"name\": "
                                                                 + "\"Acme-Events\", \"template_uuid\": \""
                                                                 + TEMPLATE + "\"}",
                PortedToolCalls.answering(Map.of("name", "ACME-V1-Events"))));

        assertThat(made).containsEntry("name", "ACME-V1-Events")
                .containsEntry("changed_by_user", Map.of("name", "ACME-V1-Events"));
        assertThat(pipelines.pipelineNamed("ACME-V1-Events")).isNotNull();
        // No XSLT yet: made, with what it still needs said.
        assertThat(made).containsEntry("still_to_set", List.of("translationFilter.xslt"));
        assertThat(String.valueOf(made.get("hint"))).startsWith("The pipeline cannot run until "
                                                                + "['translationFilter.xslt'] is set");
    }

    @Test
    void anElementTheTemplateLacksOrItsValidationIsRefusedBeforeTheUserIsAsked() {
        textTemplate();

        final ToolResult unknown = call("{\"build\": \"acme-v1\", \"name\": \"P\", \"template_uuid\": \"" + TEMPLATE
                                        + "\", \"set_properties\": \"xsltFilter.xslt=x\"}", PortedToolCalls.context());
        final ToolResult validation = call("{\"build\": \"acme-v1\", \"name\": \"P\", \"template_uuid\": \""
                                           + TEMPLATE + "\", \"set_properties\": [{\"element\": \"schemaFilter\", "
                                           + "\"name\": \"schemaGroup\", \"value\": \"OTHER\"}]}",
                PortedToolCalls.context());

        assertThat(unknown.isError()).isTrue();
        assertThat(unknown.text()).isEqualTo("The pipeline has no element(s) ['xsltFilter']; its elements are "
                                             + "['dsParser', 'schemaFilter', 'streamAppender', "
                                             + "'translationFilter']");
        assertThat(validation.text()).contains("Fix the output instead");
        assertThat(pipelines.world.created).isEmpty();
    }

    @Test
    void anotherSourcesXsltIsRefused() {
        textTemplate();
        pipelines.textConverter("tc", "Acme-Splitter");
        pipelines.world.add("XSLT", "Other-Events", pipelines.world.folder("System/XSLT").uuid, "other");

        final ToolResult refused = call("{\"build\": \"acme-v1\", \"name\": \"P\", \"template_uuid\": \"" + TEMPLATE
                                        + "\", \"set_properties\": [{\"element\": \"translationFilter\", \"name\": "
                                        + "\"xslt\", \"doc_uuid\": \"other\", \"doc_type\": \"XSLT\"}]}",
                PortedToolCalls.context());

        assertThat(refused.text()).startsWith("translationFilter.xslt: XSLT other is not a document of build "
                                              + "'acme-v1'.")
                .endsWith("call again with reuse_existing_docs=true.");
    }

    @Test
    void theParserIsSwappedForXmlFragmentsAndFedFromSource() {
        pipelines.template(TEMPLATE, "Event Data (XML)", PipelineLayers.layer(TEMPLATE).chain("Source", "Source",
                        "xmlParser", "XMLParser", "translationFilter", "XSLTFilter", "streamAppender", "StreamAppender")
                .property("streamAppender", "streamType", "Events"));
        pipelines.textConverter("tc", "Acme-Wrapper");
        pipelines.xslt("x1", "Acme-Events", Map.of());

        final Map<String, Object> made = PipelineWorld.json(call("{\"build\": \"acme-v1\", \"name\": \"P\", "
                                                                 + "\"template_uuid\": \"" + TEMPLATE + "\", "
                                                                 + "\"replace_parser\": \"XMLFragmentParser\"}",
                PortedToolCalls.answering(Map.of())));

        final PipelineDoc doc = pipelines.pipelineNamed("P");
        assertThat(doc.getPipelineData().getAddedElements()).containsExactly(
                new PipelineElement("xmlFragmentParser", "XMLFragmentParser"));
        assertThat(doc.getPipelineData().getRemovedElements()).containsExactly(
                new PipelineElement("xmlParser", "XMLParser"));
        assertThat(doc.getPipelineData().getAddedLinks()).containsExactly(
                new PipelineLink("Source", "xmlFragmentParser"),
                new PipelineLink("xmlFragmentParser", "translationFilter"));
        assertThat(made.get("sets")).isEqualTo(List.of("translationFilter.xslt",
                "xmlFragmentParser.textConverter"));
    }

    @Test
    void aJsonArraySampleIsReadItemByItem() {
        pipelines.template(TEMPLATE, "Event Data (JSON)", PipelineLayers.layer(TEMPLATE).chain("jsonParser",
                        "JSONParser", "translationFilter", "XSLTFilter", "streamAppender", "StreamAppender")
                .property("streamAppender", "streamType", "Events"));
        pipelines.xslt("x1", "Acme-Events", null);
        pipelines.feeds.facts.ownRaw = List.of(7L);
        pipelines.feeds.facts.profiler = text -> FakeBuildFacts.profileOf("json array");

        final Map<String, Object> made = PipelineWorld.json(call("{\"build\": \"acme-v1\", \"name\": \"P\", "
                                                                 + "\"template_uuid\": \"" + TEMPLATE + "\"}",
                PortedToolCalls.answering(Map.of())));

        assertThat(made.get("filled_from_build")).isEqualTo(List.of(
                "translationFilter.xslt = Acme-Events (the build's only XSLT)",
                "jsonParser.addRootObject = false (the sample is a JSON array: each item is a record)"));
        assertThat(PipelineDocs.addedProperties(pipelines.pipelineNamed("P"))).contains(
                new PipelineProperty("jsonParser", "addRootObject", new PipelinePropertyValue(false)));
    }

    @Test
    void referenceDataIsAttachedForTheTranslationsLookups() {
        textTemplate();
        pipelines.textConverter("tc", "Acme-Splitter");
        pipelines.xslt("x1", "Acme-Events", Map.of());
        pipelines.productionPipeline(PipelineWorld.LOADER, "Reference Loader", null,
                PipelineLayers.layer(PipelineWorld.LOADER));
        pipelines.feeds.feedAt("System/Feeds", "ACME-USERS", "f-users");
        Mockito.when(pipelines.referenceData.resolveLoader("ACME-USERS")).thenReturn(PipelineWorld.LOADER);

        final Map<String, Object> made = PipelineWorld.json(call("{\"build\": \"acme-v1\", \"name\": \"P\", "
                                                                 + "\"template_uuid\": \"" + TEMPLATE + "\", "
                                                                 + "\"references\": [{\"feed\": \"ACME-USERS\"}]}",
                PortedToolCalls.answering(Map.of())));

        assertThat(made.get("reference_data")).isEqualTo(List.of("ACME-USERS via Reference Loader"));
        assertThat(pipelines.pipelineNamed("P").getPipelineData().getAddedPipelineReferences()).singleElement()
                .satisfies(reference -> assertThat(reference.getElement()).isEqualTo("translationFilter"));
    }

    @Test
    void aStandaloneKafkaPipelineIsAChainOfItsOwnWithOneEventARecord() {
        final DocRef kafka = pipelines.world.add("KafkaConfig", "Kafka-Dev",
                pipelines.world.folder("System/Kafka").uuid, "k1").docRef();
        pipelines.xslt("cef", "Acme-CEF", null);

        final ToolResult missing = call("{\"build\": \"acme-v1\", \"name\": \"Acme-CEF\", \"standalone\": "
                                        + "\"kafka\", \"set_properties\": \"xsltFilter.xslt=cef\"}",
                PortedToolCalls.context());
        final ToolResult both = call("{\"build\": \"acme-v1\", \"name\": \"Acme-CEF\", \"standalone\": \"kafka\", "
                                     + "\"template\": \"T\"}", PortedToolCalls.context());
        final Map<String, Object> made = PipelineWorld.json(call("{\"build\": \"acme-v1\", \"name\": \"Acme-CEF\", "
                                                                 + "\"standalone\": \"kafka\", \"set_properties\": "
                                                                 + "[{\"element\": \"xsltFilter\", \"name\": \"xslt\", "
                                                                 + "\"doc_uuid\": \"cef\", \"doc_type\": \"XSLT\"}, "
                                                                 + "{\"element\": \"standardKafkaProducer\", "
                                                                 + "\"name\": \"kafkaConfig\", \"doc_uuid\": \"k1\", "
                                                                 + "\"doc_type\": \"KafkaConfig\"}]}",
                PortedToolCalls.answering(Map.of())));

        assertThat(missing.text()).isEqualTo("set_properties needs ['standardKafkaProducer.kafkaConfig']: the CEF "
                                             + "XSLT draft_cef_mapping saved, and the KafkaConfig the user picks "
                                             + "(draft_cef_mapping lists them as kafka_configs)");
        assertThat(both.text()).isEqualTo("standalone is for when there is no template: give it, or a template, "
                                          + "not both");
        assertThat(made).containsEntry("chain", "Source -> xmlParser -> splitFilter -> xsltFilter -> schemaFilter "
                                                + "-> standardKafkaProducer")
                .containsEntry("sets", List.of("splitFilter.splitCount", "schemaFilter.namespaceURI",
                        "xsltFilter.xslt", "standardKafkaProducer.kafkaConfig"));
        final PipelineDoc doc = pipelines.pipelineNamed("Acme-CEF");
        assertThat(doc.getParentPipeline()).isNull();
        assertThat(doc.getPipelineData().getAddedElements()).hasSize(6);
        assertThat(doc.getPipelineData().getAddedLinks()).hasSize(5);
        assertThat(PipelineDocs.addedProperties(doc)).containsExactly(
                new PipelineProperty("splitFilter", "splitCount", new PipelinePropertyValue(1)),
                new PipelineProperty("schemaFilter", "namespaceURI", new PipelinePropertyValue("kafka-records:1")),
                new PipelineProperty("xsltFilter", "xslt", new PipelinePropertyValue(new DocRef("XSLT", "cef",
                        "Acme-CEF"))),
                new PipelineProperty("standardKafkaProducer", "kafkaConfig", new PipelinePropertyValue(kafka)));
        assertThat(pipelines.world.documentAccess.path(doc.asDocRef())).endsWith("/" + BUILD + "/CEF");
    }

    @Test
    void theBuildIsTheSessionsWhenTheCallNamesNone() {
        final ToolResult refused = call("{\"name\": \"P\", \"template_uuid\": \"" + TEMPLATE + "\"}",
                PortedToolCalls.context());

        assertThat(refused.text()).startsWith("create_pipeline needs build: the build this work belongs to");
    }

    @Test
    void itConfirmsItselfAndNoParameterIsNamedAfterASchemaKeyword() {
        // VS Code read a create_pipeline parameter called `properties` as the JSON Schema keyword and refused every
        // call with "must have required property 'properties'", although the schema required only `name`.
        final Set<String> keywords = Set.of("properties", "patternProperties", "additionalProperties", "required",
                "items", "anyOf", "oneOf", "allOf", "not", "$ref", "$defs", "definitions");
        for (final ToolSchema.ObjectSchema parameters : List.of(tool.definition().parameters(),
                pipelines.copyTool().definition().parameters(), pipelines.updateTool().definition().parameters())) {
            assertThat(parameters.properties().keySet()).doesNotContainAnyElementsOf(keywords);
        }
        assertThat(tool.confirmsItself()).isTrue();
        assertThat(pipelines.copyTool().confirmsItself()).isTrue();
        assertThat(pipelines.updateTool().confirmsItself()).isFalse();
        assertThat(tool.definition().description()).startsWith("Onboarding step 5 of 15 (pipeline): ");
    }

    private void textTemplate() {
        pipelines.template(TEMPLATE, "Event Data (Text)", PipelineLayers.layer(TEMPLATE).chain("dsParser",
                        "DSParser", "translationFilter", "XSLTFilter", "schemaFilter", "SchemaFilter", "streamAppender",
                        "StreamAppender")
                .property("schemaFilter", "schemaGroup", "EVENTS")
                .property("streamAppender", "streamType", "Events"));
    }

    private ToolResult call(final String arguments, final ToolCallContext context) {
        return tool.execute(PortedToolCalls.call(CreatePipelineTool.NAME, arguments), context);
    }
}
