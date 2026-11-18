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
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `start_build` - creates (or finds) a build's workspace folder, where every write tool creates documents, and returns
/// the standing instructions (AGENTS docs) that apply. The existing feeds it is given that are outside the build are
/// recorded as what it works from.
class StartBuildTool extends AbstractBuiltInTool {

    static final String NAME = "start_build";
    static final String BUILD_ARG = "build";
    static final String FEEDS_ARG = "feeds";
    static final String FOLDERS_ARG = "folders";
    static final String KIND_ARG = "kind";
    static final String CARRY_ON_ARG = "carry_on";
    // The answers suggested when the build already has work done.
    static final String CARRY_ON = "Carry on from it";
    static final String START_AFRESH = "Start afresh";

    private final WriteGuard guard;
    private final BuildRecords buildRecords;
    private final FeedNames feedNames;
    private final AgentInstructions agentInstructions;
    private final BuildPlan plan;
    private final Consent consent;

    // The feeds' source type registries; none in tests that make the tool themselves.
    private Provider<SourceTypeRegistries> registriesProvider;

    @Inject
    StartBuildTool(final WriteGuard guard,
                   final BuildRecords buildRecords,
                   final FeedNames feedNames,
                   final AgentInstructions agentInstructions,
                   final BuildPlan plan,
                   final Consent consent) {
        this.guard = guard;
        this.buildRecords = buildRecords;
        this.feedNames = feedNames;
        this.agentInstructions = agentInstructions;
        this.plan = plan;
        this.consent = consent;
    }

    /// Where the feeds' registries and open builds are found (set by injection; none in tests that make the tool
    /// themselves).
    ///
    /// @param registries The feeds' registries.
    @Inject
    void setRegistries(final Provider<SourceTypeRegistries> registries) {
        this.registriesProvider = registries;
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
                             + "must follow. A feed that aggregates source types (a source type registry) has one "
                             + "open build at a time: a build on one with another open is refused, naming it.")
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
                        .addBooleanProperty(CARRY_ON_ARG, "Only when the user has said, in the chat, to carry on "
                                                          + "from a build that already has work done (a client "
                                                          + "without forms is told to ask them).")
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
                return JsonResults.refused("kind is one of " + ToolText.list(BuildRecords.BUILD_KINDS) + ", not '"
                                           + kind + "'");
            }
            final Optional<Map<String, Object>> afresh = carryOn(call, context, build);
            if (afresh.isPresent()) {
                return JsonResults.of(afresh.get());
            }
            return JsonResults.of(start(context, build, stringListArg(call, FEEDS_ARG),
                    stringListArg(call, FOLDERS_ARG), kind));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    // A build that already has work done, started by name in a session not yet working on it, is put to the user:
    // carry on from it, or start afresh (seen in chat 164: the user meant to onboard the feed anew, an earlier chat's
    // build was left in place, and the agent went straight on to indexing from its steps). Nothing is deleted:
    // starting afresh is a new build name, or the user deleting the folder. Without forms, the agent is told to ask.
    private Optional<Map<String, Object>> carryOn(final ToolCall call,
                                                  final ToolCallContext context,
                                                  final String build) {
        if (plan.currentBuild(context).filter(build::equals).isPresent()
            || ToolArgs.flag(call, CARRY_ON_ARG)
            || guard.buildFolder(build, false).isEmpty()) {
            return Optional.empty();
        }
        final List<String> done = plan.doneSteps(build);
        if (done.isEmpty()) {
            return Optional.empty();
        }
        final String said = "Build '" + build + "' already exists, with these steps done: " + String.join(", ", done)
                            + ".";
        final Optional<String> answer = consent.answer(call, context, NAME, said,
                "Carry on from it, or start afresh", List.of(CARRY_ON, START_AFRESH));
        if (answer.isEmpty()) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "needs_user");
            result.put("build", build);
            result.put("done_steps", done);
            result.put("hint", said + " Ask the user whether to carry on from it or start afresh; nothing was "
                               + "changed. Carrying on: call start_build again with carry_on=true. Starting afresh: "
                               + "a new build name the user agrees to, or they delete the build's folder in Stroom "
                               + "first.");
            return Optional.of(result);
        }
        if (answer.get().equalsIgnoreCase(CARRY_ON)) {
            return Optional.empty();
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "start_afresh");
        result.put("build", build);
        result.put("done_steps", done);
        if (!answer.get().equalsIgnoreCase(START_AFRESH)) {
            result.put("user_says", answer.get());
        }
        result.put("hint", "The user doesn't want to carry on from build '" + build + "'; nothing was changed. "
                           + (result.containsKey("user_says")
                ? "Do what they say (user_says). "
                : "")
                           + "Start afresh under a new build name the user agrees to, or ask them to delete the "
                           + "build's folder in Stroom and call start_build again.");
        return Optional.of(result);
    }

    private Map<String, Object> start(final ToolCallContext context,
                                      final String build,
                                      final List<String> feeds,
                                      final List<String> folders,
                                      final String kind) {
        if (registriesProvider != null) {
            // One open build per aggregated feed (decided 2026-11-12).
            feeds.forEach(feed -> registriesProvider.get().refuseOpenBuild(feed, build, false));
        }
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
                    .map(ToolText::repr)
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
