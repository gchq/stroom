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
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.ExampleTemplates;
import stroom.ai.impl.tools.indexing.IndexTemplates.NamedTemplate;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.TemplateCheck;
import stroom.ai.impl.tools.indexing.TemplateMappings;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/// `check_index_template` - checks a user's Elasticsearch index template (with the component templates it is
/// composed of) against the documents the candidate indexing pipeline writes, and has the user agree a compatible
/// one, kept with the pipeline (the Stroom MCP server's `tools/indexing.py` `check_index_template`). Nothing is
/// written to Elasticsearch.
class CheckIndexTemplateTool extends AbstractBuiltInTool {

    static final String NAME = "check_index_template";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String TEMPLATE_ARG = "template";
    static final String EVENTS_STREAM_IDS_ARG = "events_stream_ids";
    static final String COMPONENT_TEMPLATES_ARG = "component_templates";
    static final String WITHOUT_COMPONENTS_ARG = "without_components";
    static final String MAX_RECORDS_ARG = "max_records";
    static final String REVIEWED_ARG = "reviewed";

    // The records stepped by default, and at most.
    private static final int RECORDS = 50;
    private static final int MAX_RECORDS = 500;
    private static final int DOUBLED_SHOWN = 30;

    private final IndexPlans plans;
    private final IndexExamples examples;
    private final IndexingInputs inputs;
    private final BuildIndexes buildIndexes;
    private final TemplatePipelines pipelines;
    private final TemplateReviews reviews;
    private final IndexingMemory memory;

    @Inject
    CheckIndexTemplateTool(final IndexPlans plans,
                           final IndexExamples examples,
                           final IndexingInputs inputs,
                           final BuildIndexes buildIndexes,
                           final TemplatePipelines pipelines,
                           final TemplateReviews reviews,
                           final IndexingMemory memory) {
        this.plans = plans;
        this.examples = examples;
        this.inputs = inputs;
        this.buildIndexes = buildIndexes;
        this.pipelines = pipelines;
        this.reviews = reviews;
        this.memory = memory;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.INDEXING;
    }

    @Override
    public boolean isReadOnly() {
        // A compatible template the user agrees is kept in the pipeline's description.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user reviews the template in the chat and confirms it in a form before it is kept.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It steps up to 500 records of the pipeline.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ProposeIndexTemplateTool.permissionsOf();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Check a user's Elasticsearch index template (not a Stroom pipeline template), with the "
                             + "component templates it is composed of, against the candidate indexing pipeline: does "
                             + "it apply to the pipeline's index, and can it take every field the pipeline writes "
                             + "(types, date formats, dynamic setting, object clashes, renamed or dropped fields)? "
                             + "Components are merged as Elasticsearch composes them. An existing index's mapping "
                             + "may be given instead. Returns whether it is compatible and each change needed, mostly "
                             + "to the indexing XSLT, to flag to the user before anything is changed. A compatible "
                             + "index template (with index_patterns) is shown to the user to confirm, and kept with "
                             + "the pipeline as the agreed one.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, ProposeIndexTemplateTool.PIPELINE_UUID_DESCRIPTION)
                        .addStringProperty(TEMPLATE_ARG, "The Elasticsearch index template as the user gave it: a "
                                                         + "Dev Tools request (PUT _index_template/name {...}), the "
                                                         + "JSON body, or GET _index_template output; or an existing "
                                                         + "index's mapping (GET <index>/_mapping) as the example. "
                                                         + "Left out with reviewed=: the template that review showed, "
                                                         + "kept for the session (never retype it).")
                        .addProperty(EVENTS_STREAM_IDS_ARG, stringArraySchema("Events streams to step the pipeline "
                                                                              + "over."))
                        .addProperty(COMPONENT_TEMPLATES_ARG, stringArraySchema("Only if the index template lists "
                                                                                + "any in composed_of: those "
                                                                                + "component templates, as the user "
                                                                                + "gave them: each a Dev Tools "
                                                                                + "request (PUT "
                                                                                + "_component_template/name {...}) "
                                                                                + "or GET _component_template "
                                                                                + "output. Kept for the session once "
                                                                                + "given: leave out those given "
                                                                                + "before, never send them again."))
                        .addProperty(WITHOUT_COMPONENTS_ARG, stringArraySchema(
                                ProposeIndexTemplateTool.WITHOUT_COMPONENTS_DESCRIPTION))
                        .addIntegerProperty(MAX_RECORDS_ARG, "The most records stepped: 1 to " + MAX_RECORDS
                                                             + " (default " + RECORDS + ").")
                        .addStringProperty(REVIEWED_ARG, ProposeIndexTemplateTool.REVIEWED_DESCRIPTION)
                        .required(PIPELINE_UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Map<String, Object> result = check(call, context);
            return UserActionEntries.withActions(JsonResults.of(result), result);
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(PipelineStepping.CANCELLED);
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    private Map<String, Object> check(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            throw new ToolRefusal("Give pipeline_uuid");
        }
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, EVENTS_STREAM_IDS_ARG);
        if (streamIds.isEmpty()) {
            throw new ToolRefusal("Give events_stream_ids: the Events streams to step the pipeline over");
        }
        final int maxRecords = maxRecords(call);
        final Object reviewed = argsMap(call).get(REVIEWED_ARG);
        final List<String> without = ToolArgs.strings(call, WITHOUT_COMPONENTS_ARG);
        final String given = rawArg(call, TEMPLATE_ARG);
        final String template = NullSafe.isBlankString(given)
                ? memory.shownTemplate(reviewed).orElse(null)
                : given;
        if (template == null) {
            throw new ToolRefusal("Give template: the index template as the user gave it (or reviewed= the review_id "
                                  + "of one shown in this session)");
        }
        final NamedTemplate parsed = ProposeIndexTemplateTool.parse(template);
        final String name = parsed.name();
        final List<String> composedOf = JsonValues.strings(parsed.body().get("composed_of"));
        final Map<String, Map<String, Object>> components;
        try {
            components = examples.givenComponents(ToolArgs.raw(call, COMPONENT_TEMPLATES_ARG), composedOf,
                    template);
        } catch (final IllegalArgumentException e) {
            throw new ToolRefusal(e.getMessage());
        }
        final Map<String, Object> wanted = IndexingHelpers.componentsWanted(parsed.body(), components, without);
        if (wanted != null) {
            return wanted;
        }
        final TemplatePipelines.ComponentChoices choices = pipelines.componentChoices(context, pipelineUuid,
                List.of());
        final List<String> ruled = new ArrayList<>();
        choices.include().stream()
                .filter(component -> !composedOf.contains(component))
                .forEach(component -> ruled.add("composed_of lacks '" + component + "', which standing instructions "
                                                + "include"));
        choices.exclude().stream()
                .filter(composedOf::contains)
                .forEach(component -> ruled.add("composed_of has '" + component + "', which standing instructions "
                                                + "exclude"));
        final EcsSchema ecs = plans.ecs();
        final List<String> doubled = ecs == null
                ? List.of()
                : ExampleTemplates.ecsDuplicates(parsed.body(), ecs);
        if (!doubled.isEmpty()) {
            // Seen in testing: asked to use ecs@mappings, the agent composed it and still mapped every ECS field
            // itself.
            ruled.add("mapped explicitly just as ecs@mappings (which this template composes) maps them, so mapped "
                      + "twice: " + PyText.list(doubled.subList(0, Math.min(DOUBLED_SHOWN, doubled.size())))
                      + (doubled.size() > DOUBLED_SHOWN
                    ? " (+" + (doubled.size() - DOUBLED_SHOWN) + ")"
                    : "")
                      + ". Leave them out of properties, with dynamic mapping on: the component maps them as "
                      + "documents bring them (propose_index_template builds it that way)");
        }
        final TemplateMappings.Composed composed = TemplateMappings.compose(parsed.body(), components);
        final Map<String, Object> destination = pipelines.destination(pipelineUuid);
        final String index = String.valueOf(destination.get("index name"));
        buildIndexes.requireSameIndex(pipelineUuid, index);
        final String defaultName = IndexingHelpers.templateTarget(index).name();
        final Optional<IndexExamples.Kept> kept = examples.keptExample(pipelineUuid, List.of(index));
        String exampleName = null;
        if (kept.isPresent()) {
            try {
                exampleName = ProposeIndexTemplateTool.parse(kept.get().example()).name();
            } catch (final ToolRefusal e) {
                // An example kept as fields only (an index read through Stroom): no template name.
                exampleName = null;
            }
        }
        final String templateName = NullSafe.nonBlankStringElse(name, defaultName);
        TemplateReviews.refuseTheirName(templateName, name, exampleName, List.of());
        inputs.requireInput(pipelineUuid, streamIds, null);
        CallProgress.of(context).say("Stepping up to " + maxRecords + " records of the indexing pipeline for the "
                                     + "documents it writes");
        final TemplatePipelines.Documents documents = pipelines.documents(context, pipelineUuid, streamIds,
                maxRecords, NAME);
        final Map<String, Object> result = TemplateCheck.compare(composed.body(), documents.docs(), index, ecs);
        final List<String> notes = new ArrayList<>(ruled);
        notes.addAll(IndexingHelpers.componentNotes(composed.missing()));
        notes.addAll(JsonValues.strings(result.get("notes")));
        result.put("notes", notes);
        if (!components.isEmpty()) {
            result.put("component_templates", new ArrayList<>(new TreeSet<>(components.keySet())));
        }
        result.put("template_name", name);
        result.put("index", index);
        result.put("cluster", destination.get("cluster"));
        result.put("pipeline_link", context.links().documentTarget(new DocRef(PipelineDoc.TYPE, pipelineUuid)));
        ProposeIndexTemplateTool.stoppedEarly(context, documents, result);
        if (!Boolean.TRUE.equals(result.get("compatible"))) {
            result.put("hint", "Show the user pipeline_changes and ask whether to make them (update the indexing "
                               + "XSLT, step again) or to change the template instead. Nothing has been changed.");
            return result;
        }
        final Map<String, Object> own = parsed.body();
        if (!own.containsKey("index_patterns")) {
            result.put("hint", "Compatible, but this is a mapping, not an index template: propose_index_template with "
                               + "it as example_template builds the index template to agree.");
            return result;
        }
        final Optional<Map<String, Object>> gate = reviews.agree(call, context, NAME, pipelineUuid, destination,
                templateName, own, components, reviewed);
        if (gate.isPresent()) {
            return TemplateReviews.asking(gate.get(), result);
        }
        result.put("agreed", true);
        TemplateReviews.withCommitAction(result, templateName, destination);
        result.put("hint", "The user agreed this index template; it is kept with the pipeline. Once they say the "
                           + "cluster admin has committed it to the cluster, create_processor_filter: its approval "
                           + "asks them to confirm that, and starts indexing.");
        return result;
    }

    private int maxRecords(final ToolCall call) {
        final String given = stringArg(call, MAX_RECORDS_ARG);
        if (given == null) {
            return RECORDS;
        }
        final Integer value = intArg(call, MAX_RECORDS_ARG);
        if (value == null || value < 1 || value > MAX_RECORDS) {
            throw new ToolRefusal("max_records must be a whole number from 1 to " + MAX_RECORDS + ", not '" + given
                                  + "'");
        }
        return value;
    }
}
