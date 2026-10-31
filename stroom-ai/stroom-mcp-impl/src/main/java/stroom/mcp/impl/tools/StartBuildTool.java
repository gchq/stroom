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
import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `start_build` - creates (or finds) a build's workspace folder, where every write tool creates documents, and
/// returns the standing instructions (AGENTS docs) that apply (the Stroom MCP server's `tools/builds.py`
/// `start_build`). The existing feeds it is given that are outside the build are recorded as what it works from.
class StartBuildTool extends AbstractBuiltInTool {

    static final String NAME = "start_build";
    static final String BUILD_ARG = "build";
    static final String FEEDS_ARG = "feeds";
    static final String FOLDERS_ARG = "folders";
    static final String KIND_ARG = "kind";

    private final WriteGuard guard;
    private final BuildRecords buildRecords;
    private final FeedNames feedNames;
    private final AgentInstructions agentInstructions;
    private final BuildPlan plan;

    @Inject
    StartBuildTool(final WriteGuard guard,
                   final BuildRecords buildRecords,
                   final FeedNames feedNames,
                   final AgentInstructions agentInstructions,
                   final BuildPlan plan) {
        this.guard = guard;
        this.buildRecords = buildRecords;
        this.feedNames = feedNames;
        this.agentInstructions = agentInstructions;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It makes the build's folder, and records the feeds it works from.
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
                .checksDocuments(DocumentPermission.VIEW)
                .note("The build's folder is made in the workspace folder (stroom.ai.builds.workspaceFolder) as the "
                      + "user, who needs permission to create folders there; feeds are found among those the user "
                      + "can view.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Create (or find) the build's workspace folder. Every write tool creates documents "
                             + "there. Returns the standing instructions (AGENTS docs) that apply, which the work "
                             + "must follow.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, "Build name, e.g. 'acme-door-v1.3'.")
                        .addProperty(FEEDS_ARG, stringArraySchema("Feeds the build is for, if known, so the standing "
                                                                  + "instructions for their folders are included."))
                        .addProperty(FOLDERS_ARG, stringArraySchema("Folders the work will be promoted to, if "
                                                                    + "known."))
                        .addEnumProperty(KIND_ARG, BuildRecords.BUILD_KINDS, "The kind of work, when it is "
                                                                             + "known: discovery (raw data indexed as "
                                                                             + "it is, for exploration) or reference "
                                                                             + "(a table to look values up in); the "
                                                                             + "plan's steps follow it. Given again, "
                                                                             + "it changes the build's.")
                        .required(BUILD_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String build = stringArg(call, BUILD_ARG);
        if (build == null) {
            return JsonResults.refused("Give build: the build's name, e.g. 'acme-door-v1.3'.");
        }
        try {
            final String kind = stringArg(call, KIND_ARG);
            if (kind != null && !BuildRecords.BUILD_KINDS.contains(kind)) {
                return JsonResults.refused("kind is one of " + PyText.list(BuildRecords.BUILD_KINDS) + ", not '"
                                           + kind + "'");
            }
            return JsonResults.of(start(context, build, stringListArg(call, FEEDS_ARG),
                    stringListArg(call, FOLDERS_ARG), kind));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> start(final ToolCallContext context,
                                      final String build,
                                      final List<String> feeds,
                                      final List<String> folders,
                                      final String kind) {
        final WriteGuard.Folder folder = guard.buildFolder(build);
        // The build later calls naming none work on, as start_onboarding's.
        plan.rememberBuild(context, build);
        if (kind != null) {
            buildRecords.rememberKind(build, kind);
        }
        // Feeds that exist outside the build are what it works from (an existing feed's data, a production
        // pipeline's feed): recorded, so the plan doesn't send it to create a feed and upload samples (seen in VS
        // Code: a test feed made and a workspace file uploaded though the user chose production streams), and
        // promotion's filters name them.
        final List<String> existing = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (final String name : feeds) {
            final List<DocRef> named = feedNames.feedsNamed(name);
            if (named.isEmpty()) {
                missing.add(name);
            }
            for (final DocRef found : named) {
                if (!guard.buildOf(found).equals(Optional.of(build))) {
                    existing.add(found.getName());
                    break;
                }
            }
        }
        if (!existing.isEmpty()) {
            buildRecords.addSourceFeeds(build, existing);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("build", build);
        result.put("folder", folder.path());
        result.put("uuid", folder.uuid());
        if (!existing.isEmpty()) {
            result.put("works_from", existing);
        }
        if (!missing.isEmpty()) {
            result.put("not_found", FeedNames.notFound("No feed named " + String.join(", ", missing.stream()
                    .map(PyText::repr)
                    .toList())) + " (A feed this build is to create is fine: it doesn't exist yet.)");
        }
        result.put("standing_instructions", agentInstructions.applicableInstructions(context.session(),
                "Standing instructions read by the " + NAME + " tool",
                agentInstructions.targets(folders, List.of(), feeds, List.of())));
        if (kind != null) {
            result.put("kind", kind);
        }
        // The build's own steps once its kind is known; a new source's onboarding until then.
        result.put("plan", kind == null
                ? BuildPlan.onboardingChecklist()
                : plan.plannedSteps(build));
        result.put("task_list", BuildPlan.TASK_LIST);
        plan.nextStep(build, null).ifPresent(next -> result.put("next", next));
        result.put("done", false);
        return result;
    }
}
