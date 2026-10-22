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
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

/// `build_status` - where an onboarding stands: every step of the plan as done or to do, read from what the build
/// holds, with the sample's format and what is next (the Stroom MCP server's `tools/plan.py` `build_status`).
class BuildStatusTool extends AbstractBuiltInTool {

    static final String NAME = "build_status";
    static final String BUILD_ARG = "build";

    private final BuildPlan plan;

    @Inject
    BuildStatusTool(final BuildPlan plan) {
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .unlockedBy(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .note("The build's documents are those the user can view; whether its processor filters are made "
                      + "and running needs the Manage Processors permission.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Where an onboarding stands: every step of the plan (feed, sample streams, converter, "
                             + "translation with its mapping, pipeline, clean step, Events, validation, "
                             + "documentation, index, indexing pipeline, promotion) as done or to do, read from what "
                             + "the build holds, with the sample's format and what is next. Call it whenever unsure "
                             + "what remains; every write tool's result carries the same `next`.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, "Build name, e.g. 'acme-door-v1.0'.")
                        .required(BUILD_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String build = stringArg(call, BUILD_ARG);
        if (build == null) {
            return JsonResults.refused("Give build: the build's name, as start_onboarding or start_build named it "
                                       + "(e.g. 'onboard-acme-door').");
        }
        try {
            return JsonResults.of(plan.status(build, null));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }
}
