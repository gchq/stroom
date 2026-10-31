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
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// `build_status` - where an onboarding stands: every step of the plan as done or to do, read from what the build
/// holds, with the sample's format and what is next.
class BuildStatusTool extends AbstractBuiltInTool {

    static final String NAME = "build_status";
    static final String BUILD_ARG = "build";
    static final String FULL_ARG = "full";
    // First, so a result too large to show in full still shows them.
    private static final List<String> FIRST = List.of("next", "before_promotion", "steps");

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
                .unlockedBy(AppPermission.MANAGE_PROCESSORS_PERMISSION, AppPermission.VIEW_DATA_PERMISSION)
                .note("The build's documents are those the user can view; whether its processor filters are made "
                      + "and running needs the Manage Processors permission. The sample's format (from its text) "
                      + "needs the View Data permission: without it, the plan is made as before a sample is "
                      + "uploaded.")
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
                        .addBooleanProperty(FULL_ARG, "True for each step's description and tools, and each "
                                                      + "document's path; by default each step is given only by "
                                                      + "its state, and each document on one line.")
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
            final Map<String, Object> status = plan.status(build, null);
            return JsonResults.of(Boolean.parseBoolean(stringArg(call, FULL_ARG))
                    ? status
                    : compact(status));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    /// The status, put briefly (seen in testing: the full 10 KB result saved to the workspace and called for again and
    /// again, as the agent couldn't keep it in view): `next`, `before_promotion` and the steps first, each step by its
    /// state, each document on one line, the rest as it is.
    ///
    /// @param status The build's status, as [BuildPlan#status] gives it.
    /// @return It briefly.
    @SuppressWarnings("unchecked")
    static Map<String, Object> compact(final Map<String, Object> status) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final String key : FIRST) {
            if (status.containsKey(key)) {
                out.put(key, status.get(key));
            }
        }
        if (status.get("steps") instanceof final List<?> steps) {
            final Map<String, Object> states = new LinkedHashMap<>();
            steps.forEach(step -> {
                final Map<String, Object> item = (Map<String, Object>) step;
                states.put(String.valueOf(item.get("step")), item.get("state"));
            });
            out.put("steps", states);
        }
        status.forEach((key, value) -> {
            if (!out.containsKey(key)) {
                out.put(key, "documents".equals(key) && value instanceof final List<?> docs
                        ? docs.stream().map(doc -> line((Map<String, Object>) doc)).toList()
                        : value);
            }
        });
        out.put("hint", "Each step by its state, each document on one line: call again with full: true for each "
                        + "step's description and tools, and each document's path.");
        return out;
    }

    // A document on one line: its type, name, role, uuid, and what it is a working copy of.
    private static String line(final Map<String, Object> doc) {
        final Object role = doc.get("role");
        final Object copyOf = doc.get("working_copy_of");
        return doc.get("type") + " '" + doc.get("name") + "'"
               + (role == null
                ? ""
                : " (" + role + ")")
               + " " + doc.get("uuid")
               + (copyOf == null
                ? ""
                : ", a working copy of " + copyOf);
    }
}
