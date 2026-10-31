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
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// `set_processor_filter_enabled` - disables a processor filter on a pipeline in a build. Enabling one is the
/// user's, in Stroom: this refuses it (asked for by the user: the tools enable no filter, bar processing in a build
/// that the user approves). Ported from the Stroom MCP server's `tools/processing_writes.py`.
///
/// The filter is changed through Stroom's processor filter service as the user (Manage Processors, as the
/// Processors tab needs).
class SetProcessorFilterEnabledTool extends AbstractBuiltInTool {

    static final String NAME = "set_processor_filter_enabled";
    static final String FILTER_ID_ARG = "filter_id";
    static final String ENABLED_ARG = "enabled";

    private final ProcessorFilterAccess processorFilterAccess;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final ProcessorFilterWrites filterWrites;

    @Inject
    SetProcessorFilterEnabledTool(final ProcessorFilterAccess processorFilterAccess,
                                  final Provider<ProcessorFilterService> processorFilterServiceProvider,
                                  final ProcessorFilterWrites filterWrites) {
        this.processorFilterAccess = processorFilterAccess;
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.filterWrites = filterWrites;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Disable a processor filter on a pipeline the agent created. Enabling one is the "
                             + "user's, in Stroom: this refuses it.")
                .parameters(ObjectSchema.builder()
                        .addIntegerProperty(FILTER_ID_ARG, "Processor filter id, e.g. from processing_status.")
                        .addBooleanProperty(ENABLED_ARG, "false: disable it. true is refused: enabling is the "
                                                         + "user's.")
                        .required(FILTER_ID_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean isReadOnly() {
        // It disables a processor filter.
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
                .requires(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Only for a filter on a pipeline in a build.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(disable(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> disable(final ToolCall call, final ToolCallContext context) {
        final Integer filterId = intArg(call, FILTER_ID_ARG);
        if (filterId == null) {
            throw new ToolRefusal("Give filter_id: the processor filter's id, e.g. from processing_status.");
        }
        final boolean enabled = ToolArgs.flag(call, ENABLED_ARG);
        final ProcessorFilter current = fetch(filterId)
                .orElseThrow(() -> new ToolRefusal("No processor filter " + filterId + " was found, or you don't "
                                                   + "have permission to see it (it needs the Manage Processors "
                                                   + "permission, as the Processors tab does)"));
        final PipelineDoc pipeline = filterWrites.managedPipeline(context.session(), current.getPipelineUuid());
        if (enabled) {
            // Asked for by the user: the server enables no filter, bar reprocessing in a build that the user
            // approves.
            final DocRef ref = ProcessorFilterWrites.ref(pipeline);
            throw new ToolRefusal("Enabling a processor filter is the user's: ask them to enable filter " + filterId
                                  + " on the Processors tab of pipeline '" + pipeline.getName() + "' ("
                                  + context.links().documentTarget(ref) + "), then wait_for_processing.");
        }
        try {
            processorFilterServiceProvider.get().setEnabled(filterId, false);
        } catch (final RuntimeException e) {
            throw new ToolRefusal("Stroom refused to disable processor filter " + filterId + ": "
                                  + PipelineShapes.reason(e));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("filter_id", filterId);
        result.put("enabled", false);
        return result;
    }

    // The filter, if the user may manage processors and it exists.
    private Optional<ProcessorFilter> fetch(final int filterId) {
        if (!processorFilterAccess.canManageProcessors()) {
            return Optional.empty();
        }
        try {
            return processorFilterServiceProvider.get().fetch(filterId)
                    .filter(filter -> !filter.isDeleted() && filter.getPipelineUuid() != null);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }
}
