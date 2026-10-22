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
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.IndexTemplates;
import stroom.ai.impl.tools.indexing.IndexTemplates.NamedTemplate;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.IndexingHelpers.TemplateTarget;
import stroom.ai.impl.tools.indexing.TemplateCheck;
import stroom.ai.impl.tools.indexing.TemplateMappings;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `propose_index_template` - builds the final Elasticsearch index template for an indexing pipeline's destination
/// index, from the user's example index template (pasted, or kept in the build when the plan was drafted) and its
/// component templates, checks it against the documents the pipeline writes, and has the user agree it (the Stroom
/// MCP server's `tools/indexing.py` `propose_index_template`). The agreed template is kept with the pipeline; the
/// user's cluster admin commits it: nothing is written to Elasticsearch.
class ProposeIndexTemplateTool extends AbstractBuiltInTool {

    static final String NAME = "propose_index_template";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String PLAN_ARG = "plan";
    static final String EVENTS_STREAM_IDS_ARG = "events_stream_ids";
    static final String EXAMPLE_TEMPLATE_ARG = "example_template";
    static final String COMPONENT_TEMPLATES_ARG = "component_templates";
    static final String WITHOUT_COMPONENTS_ARG = "without_components";
    static final String INCLUDE_COMPONENTS_ARG = "include_components";
    static final String TEMPLATE_NAME_ARG = "template_name";
    static final String PRIORITY_ARG = "priority";
    static final String WITHOUT_EXAMPLE_ARG = "without_example";
    static final String REVIEWED_ARG = "reviewed";
    static final String CONFIRMATION_ID_ARG = "confirmation_id";
    /// The pipeline's argument, as both template tools describe it.
    static final String PIPELINE_UUID_DESCRIPTION = "The candidate Elasticsearch indexing pipeline.";
    /// The reviewed argument, as both template tools describe it.
    static final String REVIEWED_DESCRIPTION = "The review_id of the needs_review reply, once the user has been shown "
                                               + "its template (dev_tools) in the chat: they then confirm it in a "
                                               + "form. Not true: a template that changes gets a new id, to show "
                                               + "again.";
    /// The without_components argument, as both template tools describe it.
    static final String WITHOUT_COMPONENTS_DESCRIPTION = "Only when the user can't give some of the example's "
                                                         + "component templates: their names. The template is then "
                                                         + "built without their fields, which it may repeat or "
                                                         + "contradict.";

    // The records stepped for the documents the template is checked against.
    private static final int RECORDS = 50;

    private final IndexPlans plans;
    private final IndexExamples examples;
    private final IndexingInputs inputs;
    private final BuildIndexes buildIndexes;
    private final TemplatePipelines pipelines;
    private final TemplateReviews reviews;

    @Inject
    ProposeIndexTemplateTool(final IndexPlans plans,
                             final IndexExamples examples,
                             final IndexingInputs inputs,
                             final BuildIndexes buildIndexes,
                             final TemplatePipelines pipelines,
                             final TemplateReviews reviews) {
        this.plans = plans;
        this.examples = examples;
        this.inputs = inputs;
        this.buildIndexes = buildIndexes;
        this.pipelines = pipelines;
        this.reviews = reviews;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.INDEXING;
    }

    @Override
    public boolean isReadOnly() {
        // The agreed template is kept in the pipeline's description.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user reviews the template in the chat and confirms it in a form before it is kept.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It steps up to 50 records of the pipeline.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return permissionsOf();
    }

    /// @return What both template tools need: Stepping, View on the pipeline (and what it uses), Edit to keep the
    /// agreed template with it.
    static ToolPermissions permissionsOf() {
        return ToolPermissions.builder()
                .requires(AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The pipeline is stepped as the user, and the agreed template is kept in its description "
                      + "(the build's own pipeline only). Nothing is written to Elasticsearch: the user's cluster "
                      + "admin commits the template.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Build the final Elasticsearch index template (not a Stroom pipeline template) for the "
                             + "indexing pipeline's destination index, for the cluster admin to apply: from the "
                             + "user's example index template or mapping and any component templates it is composed "
                             + "of, following their conventions: their settings and composed_of, the new index's own "
                             + "pattern, fields they map left as they map them, new fields mapped in their style for "
                             + "the type (keyword ignore_above, text sub-fields, date formats), and names unlike "
                             + "theirs reported to rename in the field plan. Without an example, nothing is built "
                             + "until the user says they have none (without_example), and then from the field plan "
                             + "alone. Checked against the documents the pipeline writes; when it fits, the user "
                             + "confirms it as shown (or corrects it: check_index_template), and the agreed template "
                             + "is kept with the pipeline. The user then has the cluster admin commit it, and "
                             + "create_processor_filter starts indexing once they confirm that.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, PIPELINE_UUID_DESCRIPTION)
                        .addProperty(PLAN_ARG, ObjectSchema.builder()
                                .description("The field plan from draft_index_mapping (backend elasticsearch). "
                                             + "Omitted: the one kept with the pipeline's indexing XSLT (what it "
                                             + "writes), else the one drafted in this session.")
                                .build())
                        .addProperty(EVENTS_STREAM_IDS_ARG, stringArraySchema("Events streams to check the template "
                                                                              + "against (required)."))
                        .addStringProperty(EXAMPLE_TEMPLATE_ARG, "The user's example: the Elasticsearch index "
                                                                 + "template a sibling source's index uses (GET "
                                                                 + "_index_template/<name>, or a Dev Tools request), "
                                                                 + "or an existing index's mapping (GET "
                                                                 + "<index>/_mapping). Ask the user for it: the final "
                                                                 + "template follows it.")
                        .addProperty(COMPONENT_TEMPLATES_ARG, stringArraySchema("Only if the example lists any in "
                                                                                + "composed_of: those component "
                                                                                + "templates, as the user gave them "
                                                                                + "(GET _component_template/<name>, "
                                                                                + "or PUT _component_template/<name> "
                                                                                + "{...}). Kept for the session once "
                                                                                + "given: leave out those given "
                                                                                + "before, never send them again."))
                        .addProperty(WITHOUT_COMPONENTS_ARG, stringArraySchema(WITHOUT_COMPONENTS_DESCRIPTION))
                        .addProperty(INCLUDE_COMPONENTS_ARG, stringArraySchema("Component templates the user wants "
                                                                               + "the new template to compose besides "
                                                                               + "the example's (or without one), by "
                                                                               + "name: referenced whatever they map, "
                                                                               + "their fields left to them. Give "
                                                                               + "their content in "
                                                                               + "component_templates too (but "
                                                                               + "ecs@mappings, known here)."))
                        .addStringProperty(TEMPLATE_NAME_ARG, "Template name; defaults to the index name.")
                        .addIntegerProperty(PRIORITY_ARG, "Only when the user asks for one. None by default: with an "
                                                          + "example, the example's; without one, none "
                                                          + "(Elasticsearch's 0).")
                        .addBooleanProperty(WITHOUT_EXAMPLE_ARG, "Only when the user has said they have no example: "
                                                                 + "the template is built from the field plan alone, "
                                                                 + "and they confirm it as shown.")
                        .addStringProperty(REVIEWED_ARG, REVIEWED_DESCRIPTION)
                        .addStringProperty(CONFIRMATION_ID_ARG, "From an earlier needs_confirmation reply.")
                        .required(PIPELINE_UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Map<String, Object> result = propose(call, context);
            return UserActionEntries.withActions(JsonResults.of(result), result);
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(PipelineStepping.CANCELLED);
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    private Map<String, Object> propose(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = required(call, PIPELINE_UUID_ARG);
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, EVENTS_STREAM_IDS_ARG);
        if (streamIds.isEmpty()) {
            throw new ToolRefusal("Give events_stream_ids: the Events streams to check the template against");
        }
        final Long priority = priority(call);
        final boolean withoutExample = ToolArgs.flag(call, WITHOUT_EXAMPLE_ARG);
        final String templateName = stringArg(call, TEMPLATE_NAME_ARG);
        final List<String> without = ToolArgs.strings(call, WITHOUT_COMPONENTS_ARG);
        final List<String> nominated = ToolArgs.strings(call, INCLUDE_COMPONENTS_ARG);
        final Object reviewed = argsMap(call).get(REVIEWED_ARG);
        final String confirmationId = stringArg(call, CONFIRMATION_ID_ARG);
        final CallProgress progress = CallProgress.of(context);

        FieldPlan plan = plans.given(IndexingArgs.plan(call, PLAN_ARG), List.of(), pipelineUuid);
        if (plan == null) {
            throw new ToolRefusal("Give plan= the field plan: the pipeline's indexing XSLT keeps none, and "
                                  + "draft_index_mapping drafted none (or several) in this session");
        }
        if (!FieldPlan.ELASTICSEARCH.equals(plan.backend())) {
            throw new ToolRefusal("Index templates are for Elasticsearch; Lucene fields are set with create_index_doc "
                                  + "(plan=...)");
        }
        plan = plans.withConvention(plan, pipelineUuid);
        final EcsSchema ecs = plans.ecs();
        final Map<String, Object> destination = pipelines.destination(pipelineUuid);
        final String index = String.valueOf(destination.get("index name"));
        buildIndexes.requireSameIndex(pipelineUuid, index);
        final TemplateTarget target = IndexingHelpers.templateTarget(index);
        final String name = NullSafe.nonBlankStringElse(templateName, target.name());
        final FieldPlan forIndex = plan.withIndexName(target.indexName());
        Map<String, Object> body = body(forIndex.elasticTemplate(name, priority, true, ecs));
        List<String> notes = new ArrayList<>();
        Map<String, Map<String, Object>> components = new LinkedHashMap<>();
        final TemplatePipelines.ComponentChoices choices = pipelines.componentChoices(context, pipelineUuid,
                nominated);
        final List<String> include = choices.include();
        final List<String> exclude = choices.exclude();

        final String given = rawArg(call, EXAMPLE_TEMPLATE_ARG);
        String example = NullSafe.isBlankString(given)
                ? null
                : given;
        Object componentTemplates = ToolArgs.raw(call, COMPONENT_TEMPLATES_ARG);
        String keptNote = null;
        if (!withoutExample && (example == null || !IndexExamples.isTemplate(example))) {
            final Optional<IndexExamples.Kept> kept = examples.keptExample(pipelineUuid,
                    List.of(index, NullSafe.string(plan.indexName())));
            if (kept.isPresent()) {
                keptNote = "the example index template " + kept.get().how() + ", kept in the build when the plan "
                           + "was drafted"
                           + (kept.get().how().endsWith(IndexExamples.FIELDS_ONLY)
                        ? ": its settings, component templates, keyword sub-fields and ignore_above aren't readable "
                          + "through Stroom, so Elasticsearch's defaults; the user may paste its index template for "
                          + "them"
                        : "")
                           + (example != null
                        ? "; what was given as example_template is not an index template"
                        : "");
                example = kept.get().example();
                componentTemplates = kept.get().components();
            }
        }
        if (example != null) {
            final NamedTemplate parsed = parse(example);
            final Map<String, Object> exampleBody = parsed.body();
            final List<String> composition = ExampleTemplates.composition(
                    JsonValues.strings(exampleBody.get("composed_of")), include, exclude);
            components = components(componentTemplates, composition, example);
            final Map<String, Object> wanted = IndexingHelpers.componentsWanted(Map.of("composed_of", composition),
                    components, without);
            if (wanted != null) {
                return wanted;
            }
            final List<String> named = new ArrayList<>(components.keySet());
            named.addAll(JsonValues.strings(exampleBody.get("composed_of")));
            TemplateReviews.refuseTheirName(name, templateName, parsed.name(), named);
            // Every planned field: the example says what it is composed of, and so which fields are left to that.
            final Map<String, Object> full = body(forIndex.elasticTemplate(name, priority, false, ecs));
            final ExampleTemplates.Built built = ExampleTemplates.fromExample(full, exampleBody, components,
                    plan.discovery() != null, include, exclude, IndexingHelpers.ecsBase(exampleBody), ecs);
            body = built.body();
            notes = new ArrayList<>(choices.notes());
            notes.addAll(built.notes());
            if (keptNote != null) {
                notes.addFirst(keptNote);
            }
        }
        final String link = context.links().documentTarget(new DocRef(PipelineDoc.TYPE, pipelineUuid));
        if (example == null && !withoutExample) {
            // No template to commit yet: one handed out now would be applied to the cluster without the user's
            // conventions, and without being agreed here, so indexing would still be refused.
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("agreed", false);
            result.put("needs", EXAMPLE_TEMPLATE_ARG);
            result.put("index", index);
            result.put("cluster", destination.get("cluster"));
            result.put("pipeline_link", link);
            result.put("hint", "No example was given, so no template is built: call again with example_template= the "
                               + "index template (or an index's mapping) a sibling "
                               + (plan.discovery() != null
                    ? "discovery index"
                    : "source's index") + " uses, exactly as the user pasted it, and its component templates. If it "
                               + "is not in this conversation, ask the user to paste it and end your turn. Only if "
                               + "they say they have none: without_example=true, and they confirm the template built "
                               + "from the plan.");
            return result;
        }
        List<String> planNotes = new ArrayList<>();
        if (example == null && (!include.isEmpty() || !exclude.isEmpty())) {
            components = components(componentTemplates, include, null);
            final Map<String, Object> wanted = IndexingHelpers.componentsWanted(Map.of("composed_of", include),
                    components, without);
            if (wanted != null) {
                return wanted;
            }
            final ExampleTemplates.Built built = ExampleTemplates.withComponents(body, components, include, exclude,
                    ecs);
            body = built.body();
            planNotes = new ArrayList<>(choices.notes());
            planNotes.addAll(built.notes());
        }
        inputs.requireInput(pipelineUuid, streamIds, plan);
        progress.say("Stepping up to " + RECORDS + " records of the indexing pipeline for the documents it writes");
        final TemplatePipelines.Documents documents = pipelines.documents(context, pipelineUuid, streamIds, RECORDS,
                NAME);
        String timeNote = null;
        if (plan.discovery() != null && example == null) {
            // A discovery index is a data stream when the source's time field gives every record its time.
            final ExampleTemplates.DataStream stream = ExampleTemplates.discoveryDataStream(body, documents.docs(),
                    plan.discovery().timestampField());
            body = stream.body();
            timeNote = stream.note();
        }
        final Map<String, Object> composed = TemplateMappings.compose(body, components).body();
        final Map<String, Object> check = TemplateCheck.compare(composed, documents.docs(), index, ecs);
        if (example == null) {
            notes = new ArrayList<>();
            notes.add("built from the field plan alone: the user has no example index template");
            if (timeNote != null) {
                notes.add(timeNote);
            }
            notes.addAll(planNotes);
        }
        final String devTools = IndexTemplates.devTools(name, body);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("template_name", name);
        result.put("index", index);
        result.put("cluster", destination.get("cluster"));
        result.put("template", body);
        result.put("dev_tools", devTools);
        result.put("self_check", check);
        result.put(example != null
                ? "from_example"
                : "notes", notes);
        if (timeNote != null) {
            result.put("data_stream", timeNote);
        }
        result.put("pipeline_link", link);
        stoppedEarly(context, documents, result);
        if (!Boolean.TRUE.equals(check.get("compatible"))) {
            result.put("hint", "It does not fit the documents the pipeline writes (self_check): show the user and ask "
                               + "whether to change the indexing XSLT or the template. Not yet shown for "
                               + "confirmation.");
            return result;
        }
        final Optional<Map<String, Object>> gate = reviews.agree(call, context, NAME, pipelineUuid, destination, name,
                body, components, confirmationId, reviewed);
        if (gate.isPresent()) {
            return TemplateReviews.asking(gate.get(), result);
        }
        result.put("agreed", true);
        TemplateReviews.withCommitAction(result, name, destination);
        result.put("hint", "The user agreed this index template; it is kept with the pipeline. Give them dev_tools for "
                           + "the cluster admin to commit to the cluster, and end your turn. Once they say it is "
                           + "committed, create_processor_filter: its approval asks them to confirm that, and starts "
                           + "indexing.");
        return result;
    }

    private Map<String, Map<String, Object>> components(final Object given,
                                                        final List<String> names,
                                                        final String example) {
        try {
            return examples.givenComponents(given, names, example);
        } catch (final IllegalArgumentException e) {
            throw new ToolRefusal(e.getMessage());
        }
    }

    /// Says when the session's time limit cut the stepping short (the Stroom MCP server's tool had no time limit).
    ///
    /// @param context   The call's context.
    /// @param documents The documents stepped.
    /// @param result    The tool's result.
    static void stoppedEarly(final ToolCallContext context,
                             final TemplatePipelines.Documents documents,
                             final Map<String, Object> result) {
        if (documents.stopped()) {
            result.put("stopped_early", true);
            result.put("stepped", PipelineStepping.outOfTime(context, "stepping after " + documents.records()
                                                                      + " records") + " The check is of the "
                                  + "documents those records gave only.");
        }
    }

    /// @param text An index template, as given.
    /// @return It, read.
    /// @throws ToolRefusal if it can't be read, saying why.
    static NamedTemplate parse(final String text) {
        try {
            return IndexTemplates.parseTemplate(text);
        } catch (final IllegalArgumentException e) {
            throw new ToolRefusal(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(final Map<String, Object> template) {
        return (Map<String, Object>) template.get("body");
    }

    private Long priority(final ToolCall call) {
        final String given = stringArg(call, PRIORITY_ARG);
        if (given == null) {
            return null;
        }
        final Integer priority = intArg(call, PRIORITY_ARG);
        if (priority == null || priority < 0) {
            throw new ToolRefusal("priority must be a whole number of at least 0, not '" + given + "'");
        }
        return priority.longValue();
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }
}
