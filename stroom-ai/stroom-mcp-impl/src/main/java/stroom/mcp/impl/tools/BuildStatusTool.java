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
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// `build_status` - where an onboarding stands: every step of the plan as done or to do, read from what the build
/// holds, with the sample's format and what is next.
class BuildStatusTool extends AbstractBuiltInTool {

    static final String NAME = "build_status";
    static final String BUILD_ARG = "build";
    static final String FULL_ARG = "full";
    /// The argument naming a stage the user said the build is not to do.
    static final String SKIP_STAGE_ARG = "skip_stage";
    /// The key of the line saying an aggregated feed's groups import different versions of its shared modules.
    static final String MODULE_VERSIONS = "module_versions";
    /// The key of each feed's streams and dates, for a build of several feeds.
    static final String SOURCE_FEEDS = "source_feeds";
    /// The key of the line pointing a new events version's follow-on pipelines at review_coverage.
    static final String FOLLOW_ON_REVIEW = "follow_on_review";
    // First, so a result too large to show in full still shows them.
    private static final List<String> FIRST = List.of("next", "before_promotion", "steps");
    // What the report says when each promoted feed is selected once.
    private static final String EACH_ONCE = "each feed the promotion gave a new filter is selected by one pipeline's "
                                            + "enabled filter";

    private final BuildPlan plan;
    private final PendingDocumentation pendingDocs;
    // An aggregated feed's source type groups; none in tests that make the tool themselves.
    private Provider<SourceTypeGroups> groupsProvider;
    // The processor filters naming a pipeline; none in tests that make the tool themselves.
    private Provider<ProcessorFilters> filtersProvider;
    // How a promoted build's feeds are selected; none in tests that make the tool themselves.
    private Provider<PromotionSwitchOver> switchOverProvider;
    // Each feed of a build of several: its streams and dates; none in tests that make the tool themselves.
    private Provider<FeedOverviews> overviewsProvider;

    @Inject
    BuildStatusTool(final BuildPlan plan, final PendingDocumentation pendingDocs) {
        this.plan = plan;
        this.pendingDocs = pendingDocs;
    }

    /// Where an aggregated feed's source type groups are found (set by injection; none in tests that make the tool
    /// themselves).
    ///
    /// @param groups The feed's source type groups.
    @Inject
    void setGroups(final Provider<SourceTypeGroups> groups) {
        this.groupsProvider = groups;
    }

    /// Where the pipelines reading a pipeline's Events are found (set by injection; none in tests that make the tool
    /// themselves).
    ///
    /// @param filters The processor filters.
    @Inject
    void setProcessorFilters(final Provider<ProcessorFilters> filters) {
        this.filtersProvider = filters;
    }

    /// How the feeds a promotion in this session gave new filters are selected now (set by injection; none in tests
    /// that make the tool themselves).
    ///
    /// @param switchOver Reads which filters select a feed.
    @Inject
    void setSwitchOver(final Provider<PromotionSwitchOver> switchOver) {
        this.switchOverProvider = switchOver;
    }

    /// How each feed of a build of several is described: its streams, size and dates, and whether it is dormant
    /// (set by injection; none in tests that make the tool themselves).
    ///
    /// @param overviews Describes each feed.
    @Inject
    void setOverviews(final Provider<FeedOverviews> overviews) {
        this.overviewsProvider = overviews;
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
                        .addStringProperty(SKIP_STAGE_ARG, "A stage the user said this build is not to do now "
                                                           + "(indexing or cef_output, e.g. after 'no indexing at "
                                                           + "this stage'): its steps are skipped and next goes on "
                                                           + "past them. Only as the user said.")
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
            final String skip = stringArg(call, SKIP_STAGE_ARG);
            if (skip != null) {
                plan.skipStage(build, skip);
            }
            final List<PromotedFeeds.Promoted> promoted = switchOverProvider == null
                    ? List.of()
                    : PromotedFeeds.recalled(context, build);
            final List<String> selection = promoted.isEmpty()
                    ? List.of()
                    : PromotedFeeds.report(promoted, switchOverProvider.get());
            final Map<String, Object> status = new LinkedHashMap<>(plan.status(build, null));
            final boolean empty = status.get("documents") instanceof final List<?> held && held.isEmpty();
            if (promoted.isEmpty() && empty) {
                // A name the workspace has no build of reads as a new build's empty plan (seen in chat 195).
                plan.refuseUnlessABuild(build);
            }
            if (!promoted.isEmpty() && empty) {
                // Promoted in this session, nothing left in it: what matters now is how its feeds are selected.
                return JsonResults.of(promotedStatus(build, selection));
            }
            if (!promoted.isEmpty()) {
                status.put(PromotedFeeds.FEED_SELECTION, selection.isEmpty()
                        ? List.of(EACH_ONCE)
                        : selection);
            }
            if (overviewsProvider != null) {
                // A build of several feeds: each feed's streams and dates, a dormant one included like the others
                // (decided by the user, 2026-11-25).
                final List<String> feeds = plan.buildFeeds(build);
                if (feeds.size() > 1) {
                    status.put(SOURCE_FEEDS, overviewsProvider.get().overviews(feeds));
                }
            }
            final List<String> skipped = plan.skippedByUser(build);
            if (!skipped.isEmpty()) {
                status.put("skipped_by_user", skipped);
            }
            // A doc write_documentation asked descriptions for and didn't write: not done, whatever else says so.
            pendingDocs.warning(context.session()).ifPresent(warning -> status.put("documentation_pending",
                    warning));
            if (groupsProvider != null) {
                // Informational: a promoted module changes only by a new version, so a feed's groups may import
                // different ones.
                final List<String> versions = groupsProvider.get().mixedModuleVersions(build);
                if (!versions.isEmpty()) {
                    status.put(MODULE_VERSIONS, String.join(" ", versions));
                }
            }
            if (filtersProvider != null && status.get("documents") instanceof final List<?> docs) {
                final List<String> followOns = followOnReview(docs, filtersProvider.get());
                if (!followOns.isEmpty()) {
                    status.put(FOLLOW_ON_REVIEW, String.join(" ", followOns));
                }
            }
            return JsonResults.of(Boolean.parseBoolean(stringArg(call, FULL_ARG))
                    ? status
                    : compact(status));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    /// A build promoted in this session with nothing left in it: how the feeds its promotion gave new filters are
    /// selected now, report only (decided by the user, 2026-11-25).
    ///
    /// @param build     The build.
    /// @param selection What [PromotedFeeds#report] says.
    /// @return The status.
    static Map<String, Object> promotedStatus(final String build, final List<String> selection) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("build", build);
        out.put("status", "promoted");
        out.put(PromotedFeeds.FEED_SELECTION, selection.isEmpty()
                ? List.of(EACH_ONCE)
                : selection);
        out.put("next", selection.isEmpty()
                ? "Nothing to do for its feeds' selection: each is processed by one pipeline's enabled filter."
                : "Tell the user the feed_selection as it is: a feed selected twice is processed twice once both "
                  + "filters are enabled, so the switch-over (the promotion's next steps) comes first; a feed "
                  + "selected by no enabled filter isn't processed until one is enabled. Report only: the tools "
                  + "change no filter or dictionary.");
        return out;
    }

    /// For each new version of an events (or records) pipeline in the build whose predecessor's Events promoted
    /// pipelines read (a CEF flattener, an indexing pipeline: their filters name it), the review to do before
    /// promotion: the new version can't process production streams, so review_coverage steps it
    /// (changed_pipeline_uuid) and checks them against its Events. Promotion gives each a filter reading the new
    /// version's Events (decided by the user, 2026-11-17).
    ///
    /// @param docs    The build's documents, as its status lists them.
    /// @param filters The processor filters.
    /// @return What to say, one line a new version; none when no pipeline reads a predecessor's Events.
    static List<String> followOnReview(final List<?> docs, final ProcessorFilters filters) {
        final Set<String> inBuild = new LinkedHashSet<>();
        docs.forEach(doc -> inBuild.add(String.valueOf(((Map<?, ?>) doc).get("uuid"))));
        final List<String> out = new ArrayList<>();
        for (final Object item : docs) {
            final Map<?, ?> doc = (Map<?, ?>) item;
            final Object before = doc.get("version_of");
            if (before == null || !PipelineDoc.TYPE.equals(doc.get("type"))
                || !WriteGuard.SOURCE_ROLES.contains(String.valueOf(doc.get("role")))) {
                continue;
            }
            final List<String> reading = filters.followOnPipelines(String.valueOf(before), Set.of()).stream()
                    .filter(entry -> !inBuild.contains(String.valueOf(entry.get("uuid"))))
                    .map(entry -> "'" + entry.get("name") + "'")
                    .toList();
            if (!reading.isEmpty()) {
                out.add("Promoted pipelines read the Events of the version '" + doc.get("name") + "' replaces: "
                        + String.join(", ", reading) + ". Before promoting, review_coverage pipeline_uuid=" + before
                        + " changed_pipeline_uuid=" + doc.get("uuid") + " checks them against the new version's "
                        + "Events. Promotion gives each a new filter, disabled, reading the new version's Events; "
                        + "their own filters are left for the admin.");
            }
        }
        return out;
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
