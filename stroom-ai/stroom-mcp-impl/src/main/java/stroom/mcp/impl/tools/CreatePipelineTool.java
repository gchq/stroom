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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.mcp.impl.tools.PipelineWrites.Filled;
import stroom.mcp.impl.tools.PipelineWrites.Layout;
import stroom.mcp.impl.tools.PipelineWrites.ReferenceRequest;
import stroom.mcp.impl.tools.PipelineWrites.Swap;
import stroom.mcp.impl.tools.PipelineWrites.Typed;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// `create_pipeline` - creates a pipeline in the build as a child of a template, setting only what the child
/// supplies (the Stroom MCP server's `tools/pipeline_writes.py` `create_pipeline`, with `_standalone_kafka` for a
/// pipeline of its own sending Events to Kafka where no template exists). The user confirms the template and name
/// first.
class CreatePipelineTool extends AbstractBuiltInTool {

    static final String NAME = "create_pipeline";
    static final String NAME_ARG = "name";
    static final String TEMPLATE_UUID_ARG = "template_uuid";
    static final String SET_PROPERTIES_ARG = "set_properties";
    static final String BUILD_ARG = "build";
    static final String TEMPLATE_ARG = "template";
    static final String DESCRIPTION_ARG = "description";
    static final String REPLACE_PARSER_ARG = "replace_parser";
    static final String REFERENCES_ARG = "references";
    static final String REUSE_EXISTING_DOCS_ARG = "reuse_existing_docs";
    static final String ACCEPT_PARSER_MISMATCH_ARG = "accept_parser_mismatch";
    static final String CONFIRMATION_ID_ARG = "confirmation_id";
    static final String STANDALONE_ARG = "standalone";
    /// The chain of a pipeline of its own sending Events to Kafka: element ids and types, in order.
    static final List<Map.Entry<String, String>> KAFKA_CHAIN = List.of(Map.entry("Source", "Source"),
            Map.entry("xmlParser", "XMLParser"), Map.entry("splitFilter", "SplitFilter"),
            Map.entry("xsltFilter", "XSLTFilter"), Map.entry("schemaFilter", "SchemaFilter"),
            Map.entry("standardKafkaProducer", "StandardKafkaProducer"));

    private final PipelineWrites writes;
    private final PipelineDocs pipelineDocs;
    private final PipelineShapes pipelineShapes;
    private final WriteGuard guard;
    private final Consent consent;
    private final BuildPlan plan;

    @Inject
    CreatePipelineTool(final PipelineWrites writes,
                       final PipelineDocs pipelineDocs,
                       final PipelineShapes pipelineShapes,
                       final WriteGuard guard,
                       final Consent consent,
                       final BuildPlan plan) {
        this.writes = writes;
        this.pipelineDocs = pipelineDocs;
        this.pipelineShapes = pipelineShapes;
        this.guard = guard;
        this.consent = consent;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It creates a pipeline.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms the template and name (or corrects the name) in a form before it is made.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The pipeline is created in the build folder as the user, who needs Create there, Edit on "
                      + "the pipeline, and View (or Use) on the template and the documents it names.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Create a new pipeline as a child of a template, setting only what the child "
                             + "supplies. It keeps the template's structure and defaults (including any optional "
                             + "steps such as an empty decoration XSLT), unless replace_parser swaps the parser. "
                             + "References attach reference data for stroom:lookup(). It refuses an XSLT or text "
                             + "converter from outside the build (a new source gets its own, from "
                             + "build_translation_xslt and build_data_splitter) and a template whose parser cannot "
                             + "read the build's sample streams. The user confirms the template and name first.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(NAME_ARG, "Pipeline name following the environment's convention.")
                        .addStringProperty(TEMPLATE_UUID_ARG, "Parent template, from find_pipeline_templates (its "
                                                              + "UUID; `template` takes a UUID or a name too).")
                        .addProperty(SET_PROPERTIES_ARG, propertiesSchema("What the child supplies "
                                                                          + "(translationFilter.xslt, "
                                                                          + "dsParser.textConverter), as objects: "
                                                                          + PropertyValue.EXAMPLE + ". May be left "
                                                                          + "for update_pipeline once the XSLT and "
                                                                          + "converter are saved."))
                        .addStringProperty(BUILD_ARG, "The build this pipeline belongs to; defaults to the build "
                                                      + "this session is working on (start_onboarding / "
                                                      + "start_build).")
                        .addStringProperty(TEMPLATE_ARG, "The template's UUID or exact name, instead of "
                                                         + "template_uuid.")
                        .addStringProperty(DESCRIPTION_ARG, "What the pipeline does, kept on the pipeline doc.")
                        .addStringProperty(REPLACE_PARSER_ARG, "Parser element type to use instead of the "
                                                               + "template's, e.g. 'XMLFragmentParser' for XML "
                                                               + "fragments (several root elements) when no "
                                                               + "template has one. It takes the template's "
                                                               + "parser's place and links, with the id of its "
                                                               + "type (xmlFragmentParser), which set_properties "
                                                               + "may address (xmlFragmentParser.textConverter).")
                        .addProperty(REFERENCES_ARG, referencesSchema("Reference data the translation looks up "
                                                                      + "(the mapping's lookup entries): the feed "
                                                                      + "and its loader pipeline, from "
                                                                      + "find_reference_data."))
                        .addBooleanProperty(REUSE_EXISTING_DOCS_ARG, "Only when the user says an XSLT or text "
                                                                     + "converter that already exists outside "
                                                                     + "this build is the right one for this "
                                                                     + "pipeline. Otherwise the child's documents "
                                                                     + "must be ones this build made.")
                        .addBooleanProperty(ACCEPT_PARSER_MISMATCH_ARG, "Only when the user says to use this "
                                                                        + "template although the build's sample "
                                                                        + "is not in a format its parser reads.")
                        .addStringProperty(CONFIRMATION_ID_ARG, "From an earlier needs_confirmation reply.")
                        .addEnumProperty(STANDALONE_ARG, List.of("kafka"), "Only when no forwarding template "
                                                                          + "exists (find_pipeline_templates "
                                                                          + "stage=forwarding, and the standing "
                                                                          + "instructions name none): a pipeline "
                                                                          + "of its own sending Events to Kafka, "
                                                                          + "Source -> xmlParser -> splitFilter "
                                                                          + "(one Event a record) -> xsltFilter "
                                                                          + "-> schemaFilter (kafka-records:1) -> "
                                                                          + "standardKafkaProducer; "
                                                                          + "set_properties gives xsltFilter.xslt "
                                                                          + "and standardKafkaProducer.kafkaConfig."
                                                                          + " The user confirms the chain.")
                        .required(NAME_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(create(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PipelineUnreadableException e) {
            return JsonResults.refused("Stroom can't load the pipeline: " + e.getMessage());
        }
    }

    private Map<String, Object> create(final ToolCall call, final ToolCallContext context) {
        final String proposed = stringArg(call, NAME_ARG);
        if (proposed == null) {
            throw new ToolRefusal("Give name");
        }
        final String build = plan.resolveBuild(context, stringArg(call, BUILD_ARG), NAME);
        final List<PropertyValue> given = PropertyValue.listOf(argsMap(call).get(SET_PROPERTIES_ARG),
                SET_PROPERTIES_ARG);
        final String description = NullSafe.string(rawArg(call, DESCRIPTION_ARG));
        final boolean reuse = PipelineArguments.flag(argsMap(call).get(REUSE_EXISTING_DOCS_ARG));
        final String templateGiven = stringArg(call, TEMPLATE_UUID_ARG) != null
                ? stringArg(call, TEMPLATE_UUID_ARG)
                : stringArg(call, TEMPLATE_ARG);
        if (stringArg(call, STANDALONE_ARG) != null) {
            if (templateGiven != null) {
                throw new ToolRefusal("standalone is for when there is no template: give it, or a template, not "
                                      + "both");
            }
            return standaloneKafka(call, context, build, proposed, description, given, reuse);
        }
        return fromTemplate(call, context, new FromTemplate(build, proposed, templateGiven, given, description,
                stringArg(call, REPLACE_PARSER_ARG),
                ReferenceRequest.listOf(argsMap(call).get(REFERENCES_ARG), REFERENCES_ARG), reuse,
                PipelineArguments.flag(argsMap(call).get(ACCEPT_PARSER_MISMATCH_ARG)),
                stringArg(call, CONFIRMATION_ID_ARG)));
    }

    /// Creates a pipeline as a child of a template, setting only what the child supplies: create_pipeline's path,
    /// for a tool that gives its arguments itself (create_indexing_pipeline). The user confirms the template and
    /// name (or corrects the name) first.
    ///
    /// @param call    The call asking, whose id tells its forms apart in a chat.
    /// @param context The call's context.
    /// @param request What the pipeline is made from.
    /// @return create_pipeline's result: the pipeline made, with `next`; or the confirmation round to return.
    /// @throws ToolRefusal if the template, its properties or the user's answer don't allow it.
    /// @throws PipelineUnreadableException if Stroom can't load the template.
    Map<String, Object> fromTemplate(final ToolCall call,
                                     final ToolCallContext context,
                                     final FromTemplate request) {
        final String build = request.build();
        final String proposed = request.name();
        final List<PropertyValue> given = request.properties();
        final String description = request.description();
        final String replaceParser = request.replaceParser();
        final String templateUuid = writes.templateUuid(request.template());
        final PipelineDoc template = pipelineDocs.read(templateUuid);
        final Layout merged = writes.layout(templateUuid);
        final Set<String> elements = new LinkedHashSet<>(merged.elements().keySet());
        final PipelineDataBuilder data = new PipelineDataBuilder();
        final Map<String, String> elementTypes = new LinkedHashMap<>(merged.elements());
        Swap swap = null;
        if (replaceParser != null) {
            swap = PipelineWrites.swapParser(merged, replaceParser);
            swap.applyTo(data);
            elements.remove(swap.removed().getId());
            elements.add(swap.newId());
            elementTypes.put(swap.newId(), replaceParser);
        }
        final Set<String> unknown = new HashSet<>();
        given.forEach(property -> unknown.add(property.element()));
        unknown.removeAll(elements);
        if (!unknown.isEmpty()) {
            throw new ToolRefusal("The pipeline has no element(s) " + SampleArguments.pythonList(
                    PipelineWrites.sorted(unknown)) + "; its elements are " + SampleArguments.pythonList(
                    PipelineWrites.sorted(elements)));
        }
        PipelineWrites.keepValidation(merged.elements(), given);
        final Typed typed = writes.typedProperties(elementTypes, given);
        // The parser first: a template whose parser can't read the sample is the wrong one, whatever else it lacks
        // (seen: an XML source refused the Data Splitter template for want of a text converter, not for its
        // parser).
        writes.parserReadsSample(build, merged, replaceParser, request.acceptParserMismatch());
        final Filled slots = writes.fillOpenSlots(build, merged, replaceParser, typed.properties());
        final List<PropertyValue> properties = new ArrayList<>(slots.properties());
        final List<String> filled = new ArrayList<>(slots.filled());
        final Optional<PropertyValue> array = writes.jsonArrayParser(merged.elements(), properties,
                writes.sampleIsArray(build));
        array.ifPresent(property -> {
            properties.add(property);
            filled.add(property.element() + ".addRootObject = false (the sample is a JSON array: each item is a "
                       + "record)");
        });
        writes.ownDocuments(build, properties, request.reuseExistingDocs());
        final List<PipelineReference> references = writes.referenceEntries(merged, request.references());
        final List<String> sets = properties.stream().map(PropertyValue::dotted).toList();
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("build", build);
        details.put("pipeline name", proposed);
        details.put("template", template.getName());
        details.put("sets", sets);
        if (!filled.isEmpty()) {
            details.put("filled from the build", filled);
        }
        if (!slots.stillOpen().isEmpty()) {
            details.put("still to set", slots.stillOpen());
        }
        if (swap != null) {
            details.put("parser", replaceParser + " in place of the template's " + swap.oldType());
        }
        if (!references.isEmpty()) {
            details.put("reference data", references.stream().map(PipelineWrites::describe).toList());
        }
        final Optional<Map<String, Object>> gate = consent.require(call, context, Consent.Request.confirmation(NAME,
                        "Create pipeline '" + proposed + "' from template '" + template.getName() + "'", details,
                        request.confirmationId())
                .withEditable(NAME_ARG, "Pipeline name", proposed));
        if (gate.isPresent()) {
            return gate.get();
        }
        // The user may have corrected it in the form.
        final String name = consent.edited(context, NAME_ARG, proposed);
        final String role = pipelineShapes.role(templateUuid);
        final Map<String, String> keys = new LinkedHashMap<>(typed.keys());
        final List<PropertyValue> added = properties.subList(typed.properties().size(), properties.size());
        final Typed addedTyped = writes.typedProperties(elementTypes, added);
        keys.putAll(addedTyped.keys());
        final List<PropertyValue> toSet = new ArrayList<>(typed.properties());
        toSet.addAll(addedTyped.properties());
        references.forEach(data::addPipelineReference);
        final PipelineDoc doc = guard.createFilled(PipelineDoc.TYPE, name, build, ref -> {
            if (WriteGuard.SOURCE_ROLES.contains(role)) {
                // The build's text converter and XSLT it runs, made before it, go where it went: Events or Records.
                for (final PropertyValue property : toSet) {
                    if ((TextConverterDoc.TYPE.equals(property.docType()) || XsltDoc.TYPE.equals(property.docType()))
                        && property.docUuid() != null) {
                        guard.moveToRole(build, new DocRef(property.docType(), property.docUuid()), role);
                    }
                }
            }
            PipelineDoc child = described(pipelineDocs.read(ref.getUuid()), description).copy()
                    .parentPipeline(new DocRef(PipelineDoc.TYPE, templateUuid, template.getName()))
                    .pipelineData(data.build())
                    .build();
            for (final PropertyValue property : toSet) {
                child = PipelineDocs.withProperty(child, property.element(), property.name(),
                        writes.value(property, keys.get(property.dotted())));
            }
            return pipelineDocs.write(child);
        }, false, role);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", PipelineDoc.TYPE);
        result.put("uuid", doc.getUuid());
        result.put("name", doc.getName());
        result.put("template", template.getName());
        result.put("sets", sets);
        if (!filled.isEmpty()) {
            result.put("filled_from_build", filled);
        }
        if (!slots.stillOpen().isEmpty()) {
            result.put("still_to_set", slots.stillOpen());
            result.put("hint", "The pipeline cannot run until " + SampleArguments.pythonList(slots.stillOpen())
                               + " is set: save the translation XSLT (build_translation_xslt build=, name=) and "
                               + "update_pipeline with set_properties=[{element, name: 'xslt', doc_uuid, "
                               + "doc_type: 'XSLT'}]");
        }
        if (!references.isEmpty()) {
            result.put("reference_data", references.stream().map(MergedPipeline::describe).toList());
        }
        return plan.withNext(context, build, result);
    }

    /// A pipeline of its own sending Events to Kafka, where the environment has no template for one.
    ///
    /// Built as the environment's own Kafka pipeline is (live: XMLParser, SplitFilter, XSLTFilter, SchemaFilter on
    /// kafka-records:1, StandardKafkaProducer), but one Event a record: kafka-records v1.1 takes a single
    /// kafkaRecord a document, so a split of 100 (that pipeline's) fails the schema filter whenever a chunk holds
    /// more than one.
    private Map<String, Object> standaloneKafka(final ToolCall call,
                                                final ToolCallContext context,
                                                final String build,
                                                final String proposed,
                                                final String description,
                                                final List<PropertyValue> given,
                                                final boolean reuse) {
        final Map<String, String> types = new LinkedHashMap<>();
        KAFKA_CHAIN.forEach(entry -> types.put(entry.getKey(), entry.getValue()));
        final Set<String> givenKeys = new HashSet<>();
        given.forEach(property -> givenKeys.add(property.dotted()));
        final Set<String> unknown = new HashSet<>();
        given.forEach(property -> unknown.add(property.element()));
        unknown.removeAll(types.keySet());
        if (!unknown.isEmpty()) {
            throw new ToolRefusal("The Kafka pipeline has no element(s) " + SampleArguments.pythonList(
                    PipelineWrites.sorted(unknown)) + "; its elements are " + SampleArguments.pythonList(
                    List.copyOf(types.keySet())));
        }
        final List<String> missing = List.of("xsltFilter.xslt", "standardKafkaProducer.kafkaConfig").stream()
                .filter(key -> !givenKeys.contains(key))
                .toList();
        if (!missing.isEmpty()) {
            throw new ToolRefusal("set_properties needs " + SampleArguments.pythonList(missing) + ": the CEF XSLT "
                                  + "draft_cef_mapping saved, and the KafkaConfig the user picks "
                                  + "(draft_cef_mapping lists them as kafka_configs)");
        }
        final Set<String> fixed = Set.of("splitFilter.splitCount", "schemaFilter.namespaceURI");
        final List<PropertyValue> properties = new ArrayList<>();
        properties.add(PropertyValue.plain("splitFilter", "splitCount", 1L));
        properties.add(PropertyValue.plain("schemaFilter", "namespaceURI", "kafka-records:1"));
        given.stream()
                .filter(property -> !fixed.contains(property.dotted()))
                .forEach(properties::add);
        writes.ownDocuments(build, properties, reuse);
        final Typed typed = writes.typedProperties(types, properties);
        final String chain = String.join(" -> ", types.keySet());
        final List<String> sets = typed.properties().stream().map(PropertyValue::dotted).toList();
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("build", build);
        details.put("pipeline name", proposed);
        details.put("template", "none: a pipeline of its own");
        details.put("chain", chain);
        details.put("sets", sets);
        details.put("sends", "each Event, once processed, as a Kafka record through the KafkaConfig set here "
                             + "(stepping sends nothing)");
        final Optional<Map<String, Object>> gate = consent.require(call, context, Consent.Request.confirmation(NAME,
                        "Create pipeline '" + proposed + "' with no template: " + chain, details,
                        stringArg(call, CONFIRMATION_ID_ARG))
                .withEditable(NAME_ARG, "Pipeline name", proposed));
        if (gate.isPresent()) {
            return gate.get();
        }
        final String name = consent.edited(context, NAME_ARG, proposed);
        final PipelineDataBuilder data = new PipelineDataBuilder();
        String previous = null;
        for (final Map.Entry<String, String> element : KAFKA_CHAIN) {
            data.addElement(new PipelineElement(element.getKey(), element.getValue()));
            if (previous != null) {
                data.addLink(previous, element.getKey());
            }
            previous = element.getKey();
        }
        final PipelineDoc doc = guard.createFilled(PipelineDoc.TYPE, name, build, ref -> {
            PipelineDoc made = described(pipelineDocs.read(ref.getUuid()), description).copy()
                    .pipelineData(data.build())
                    .build();
            for (final PropertyValue property : typed.properties()) {
                made = PipelineDocs.withProperty(made, property.element(), property.name(),
                        writes.value(property, typed.keys().get(property.dotted())));
            }
            return pipelineDocs.write(made);
        }, false, "CEF");
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", PipelineDoc.TYPE);
        result.put("uuid", doc.getUuid());
        result.put("name", doc.getName());
        result.put("template", null);
        result.put("chain", chain);
        result.put("sets", sets);
        return plan.withNext(context, build, result);
    }

    // The new pipeline with the description given, if one was.
    private static PipelineDoc described(final PipelineDoc doc, final String description) {
        return NullSafe.isNonBlankString(description)
                ? doc.copy().description(description).build()
                : doc;
    }

    /// @param description What the properties are.
    /// @return The schema of a list of properties ([PropertyValue]).
    static ArraySchema propertiesSchema(final String description) {
        return ArraySchema.builder()
                .description(description)
                .items(ObjectSchema.builder()
                        .addStringProperty("element", "Element id, e.g. 'translationFilter'.")
                        .addStringProperty("name", "Property name, e.g. 'xslt', 'textConverter', 'index', "
                                                   + "'indexName'.")
                        .addStringProperty("doc_uuid", "For document properties: the document's UUID.")
                        .addStringProperty("doc_type", "For document properties: e.g. 'XSLT', 'TextConverter', "
                                                       + "'Index'.")
                        .addStringProperty("value", "For plain properties: the value, written as the type Stroom "
                                                    + "declares for it (true/false, a number, text).")
                        .required("element", "name")
                        .build())
                .build();
    }

    /// @param description What the references are.
    /// @return The schema of a list of references ([ReferenceRequest]).
    static ToolSchema referencesSchema(final String description) {
        return ArraySchema.builder()
                .description(description)
                .items(ObjectSchema.builder()
                        .description("Reference data an XSLT step reads with stroom:lookup(): a feed of Reference "
                                     + "streams, loaded by a loader pipeline (find_reference_data lists the "
                                     + "environment's loaders).")
                        .addStringProperty("feed", ReferenceRequest.FEED)
                        .addStringProperty("loader_pipeline", ReferenceRequest.LOADER_PIPELINE)
                        .addStringProperty("element", ReferenceRequest.ELEMENT)
                        .required("feed")
                        .build())
                .build();
    }

    /// What a pipeline is made from a template with: create_pipeline's arguments, read.
    ///
    /// @param build                The build it belongs to.
    /// @param name                 The name proposed for it.
    /// @param template             The template's UUID or exact name.
    /// @param properties           What the child supplies.
    /// @param description          What it does, or empty.
    /// @param replaceParser        The parser type to use instead of the template's, or null.
    /// @param references           The reference data its translation looks up.
    /// @param reuseExistingDocs    Whether documents from outside the build may be used.
    /// @param acceptParserMismatch Whether a template whose parser can't read the build's sample may be used.
    /// @param confirmationId       From an earlier needs_confirmation reply, or null.
    record FromTemplate(String build,
                        String name,
                        String template,
                        List<PropertyValue> properties,
                        String description,
                        String replaceParser,
                        List<ReferenceRequest> references,
                        boolean reuseExistingDocs,
                        boolean acceptParserMismatch,
                        String confirmationId) {

    }
}
