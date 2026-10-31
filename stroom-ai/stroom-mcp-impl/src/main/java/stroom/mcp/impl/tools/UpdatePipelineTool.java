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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.mcp.impl.tools.PipelineWrites.Layout;
import stroom.mcp.impl.tools.PipelineWrites.ReferenceRequest;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `update_pipeline` - changes a pipeline the tools created, in its build: sets element properties and attaches
/// reference data. It asks nothing itself.
class UpdatePipelineTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UpdatePipelineTool.class);

    static final String NAME = "update_pipeline";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String SET_PROPERTIES_ARG = "set_properties";
    static final String REFERENCES_ARG = "references";

    private final PipelineWrites writes;
    private final BuildPlan plan;

    @Inject
    UpdatePipelineTool(final PipelineWrites writes, final BuildPlan plan) {
        this.writes = writes;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It changes a pipeline of the build.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("Only a pipeline the tools created, in its build folder, is changed, as the user, who needs "
                      + "Edit on it.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Change a pipeline the agent created: set element properties, and attach "
                             + "reference data (feed and loader pipeline, from find_reference_data) so its XSLT's "
                             + "lookups find the maps.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, "A pipeline the agent created.")
                        .addProperty(SET_PROPERTIES_ARG, CreatePipelineTool.propertiesSchema(
                                "Element properties to set, as objects: " + PropertyValue.EXAMPLE + "."))
                        .addProperty(REFERENCES_ARG, CreatePipelineTool.referencesSchema(
                                "Reference data to attach (added to any the pipeline already has), for "
                                + "stroom:lookup()."))
                        .required(PIPELINE_UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(update(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PipelineUnreadableException e) {
            return JsonResults.refused("Stroom can't load the pipeline: " + e.getMessage());
        }
    }

    private Map<String, Object> update(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            throw new ToolRefusal("Give " + PIPELINE_UUID_ARG);
        }
        final List<PropertyValue> properties = PropertyValue.listOf(argsMap(call).get(SET_PROPERTIES_ARG),
                SET_PROPERTIES_ARG);
        final List<ReferenceRequest> references = ReferenceRequest.listOf(argsMap(call).get(REFERENCES_ARG),
                REFERENCES_ARG);
        if (properties.isEmpty() && references.isEmpty()) {
            throw new ToolRefusal("Give set_properties, references, or both");
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", pipelineUuid);
        final List<String> set = new ArrayList<>();
        result.put("set", set);
        result.put("reference_data", null);
        for (final PropertyValue property : properties) {
            final Map<String, Object> outcome = writes.setPipelineProperty(context, pipelineUuid, property);
            result.put("name", outcome.get("name"));
            result.put("type", PipelineDoc.TYPE);
            set.add(String.valueOf(outcome.get("set")));
        }
        if (!references.isEmpty()) {
            final Map<String, Object> outcome = writes.setPipelineReferences(context, pipelineUuid, references);
            result.put("name", outcome.get("name"));
            result.put("type", PipelineDoc.TYPE);
            result.put("reference_data", outcome.get("reference_data"));
        }
        // A pipeline made before its XSLT (or with one written by hand) never had the JSON parser set for an array.
        final Optional<PropertyValue> array = arrayParser(pipelineUuid, result, properties);
        if (array.isPresent()) {
            final Map<String, Object> outcome = writes.setPipelineProperty(context, pipelineUuid, array.get());
            set.add(String.valueOf(outcome.get("set")));
            result.put("note", "Also set " + array.get().element() + ".addRootObject false: the build's sample is "
                               + "a JSON array, so each item is a record (with the parser's root map, the whole "
                               + "array was one).");
        }
        return result;
    }

    // The JSON parser's addRootObject, false, when the build's sample is a JSON array or the XSLT's mapping reads
    // one; a convenience on top of what was asked: never fail the update over it.
    private Optional<PropertyValue> arrayParser(final String pipelineUuid,
                                                final Map<String, Object> result,
                                                final List<PropertyValue> properties) {
        try {
            final Layout merged = writes.layout(pipelineUuid);
            final List<PropertyValue> have = new ArrayList<>();
            merged.properties().forEach((key, value) -> {
                if ("addRootObject".equals(key.name())) {
                    have.add(PropertyValue.plain(key.element(), key.name(), value));
                }
            });
            have.addAll(properties);
            final String build = plan.buildOf(new DocRef(PipelineDoc.TYPE, pipelineUuid,
                    (String) result.get("name"))).orElse(null);
            return writes.jsonArrayParser(merged.elements(), have, writes.sampleIsArray(build));
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> LogUtil.message(
                    "Couldn't check pipeline {}'s JSON parser: {}",
                    pipelineUuid,
                    e.getMessage()));
            return Optional.empty();
        }
    }
}
