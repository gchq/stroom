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
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.ZoneSource;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// `compare_feeds` - for a build of two or more feeds of one source, how their surveys differ (decided by the user,
/// 2026-11-25): the union of their shapes, each feed's differences from the others (fields or kinds of event in
/// some only, formats, time zones) for the user to confirm, each feed's volume and dates (a dormant feed included
/// like the others), and the rules the AGENTS docs of their folders give differently for the documents they share,
/// each asked. The answers are kept in the build's record: each feed's zone (the translation reads each feed's times
/// in its own), and the rule chosen for each conflict (the tools apply it). Nothing merges silently.
class CompareFeedsTool extends AbstractBuiltInTool {

    static final String NAME = "compare_feeds";
    static final String BUILD_ARG = "build";
    static final String CONFIRM_ARG = "confirm";
    static final String TIME_ZONES_ARG = "time_zones";
    static final String AGENTS_ARG = "agents";
    // The union question's answers.
    static final String UNION = "Yes: one translation for every feed's records (the union)";
    static final String NOT_UNION = "No: I'll say what to do";
    // The zone question's last option.
    private static final String OTHER_ZONE = "Another zone (type it)";

    private final BuildPlan plan;
    private final TranslationBuilds builds;
    private final BuildRecords records;
    private final FeedOverviews overviews;
    private final AgentsConflicts agents;
    private final Consent consent;

    @Inject
    CompareFeedsTool(final BuildPlan plan,
                     final TranslationBuilds builds,
                     final BuildRecords records,
                     final FeedOverviews overviews,
                     final AgentsConflicts agents,
                     final Consent consent) {
        this.plan = plan;
        this.builds = builds;
        this.records = records;
        this.overviews = overviews;
        this.agents = agents;
        this.consent = consent;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It keeps the user's answers in the build's record.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        return true;
    }

    @Override
    public boolean confirmsItself() {
        // The user answers in its forms before anything is kept.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("Reads the build's survey docs and the AGENTS docs the user can view, and counts each feed's "
                      + "streams as the user; the answers are kept in the build's record, which needs Edit on it.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "For a build of two or more feeds, once each is surveyed: how they differ (fields or "
                             + "kinds of event in some only, formats, time zones), each feed's streams and dates (a "
                             + "dormant feed is included like the others), and the AGENTS rules their folders give "
                             + "differently. Show the user its table in the chat; they confirm the union and answer "
                             + "each time zone and AGENTS conflict in short forms, kept in the build (the "
                             + "translation reads each feed's times in its own zone). Without forms, pass their "
                             + "answers: confirm=true, time_zones, agents.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addBooleanProperty(CONFIRM_ARG, "True only on the user's word that one translation serves "
                                                         + "every feed's records (the union shown).")
                        .addProperty(TIME_ZONES_ARG, ObjectSchema.builder()
                                .description("Each feed's zone for times with none, as the user said: {feed: IANA "
                                             + "name or offset}.")
                                .build())
                        .addProperty(AGENTS_ARG, ObjectSchema.builder()
                                .description("Each AGENTS conflict's key (from conflicts) and the value the user "
                                             + "chose: {key: value}.")
                                .build())
                        .required(BUILD_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(compare(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> compare(final ToolCall call, final ToolCallContext context) {
        final String build = stringArg(call, BUILD_ARG);
        if (build == null) {
            throw new ToolRefusal("Give build: the build's name, as start_build named it.");
        }
        final List<String> feeds = plan.buildFeeds(build);
        if (feeds.size() < 2) {
            throw new ToolRefusal("Build '" + build + "' is for " + (feeds.isEmpty()
                    ? "no existing feed"
                    : "one feed (" + feeds.getFirst() + ")") + ": there is nothing to compare. Go on with the "
                                  + "plan (build_status).");
        }
        final Map<String, Map<String, Object>> states = statesByFeed(build, feeds);
        final List<String> unsurveyed = feeds.stream()
                .filter(feed -> !states.containsKey(feed))
                .toList();
        if (!unsurveyed.isEmpty()) {
            throw new ToolRefusal("Not every feed of build '" + build + "' is surveyed yet: " + String.join(", ",
                    unsurveyed) + ". survey_feed build=" + build + " feed=" + unsurveyed.getFirst() + " first, "
                                  + "then each other, then call " + NAME + " again.");
        }
        final FeedShapeComparison.Comparison comparison = FeedShapeComparison.compare(states);
        final List<AgentsConflicts.Conflict> conflicts = AgentsConflicts.conflicts(
                agents.applyingByFeed(context.session(), feeds));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("build", build);
        result.put("feeds", overviews.overviews(feeds));
        result.put("differences", comparison.json());
        result.put("differences_said", comparison.said().isEmpty()
                ? List.of("The feeds' records look alike: the same format, fields, kinds of event and time zones.")
                : comparison.said());
        if (!conflicts.isEmpty()) {
            result.put("conflicts", conflicts.stream().map(conflict -> conflictJson(conflict, feeds)).toList());
        }
        final Map<String, Object> kept = records.feedComparison(build);
        if (comparison.digest().equals(kept.get("digest")) && answered(kept, comparison, conflicts)) {
            result.put("status_said", "Confirmed by the user on " + kept.get("confirmed") + ": the build "
                                      + "translates every feed's records as one union.");
            result.put("kept", kept);
            return plan.withNext(context, build, result);
        }
        final Answers answers = answers(call, context, build, feeds, comparison, conflicts);
        if (answers == null) {
            result.put("status", "needs_user");
            result.put("hint", "Show the user the feeds table and differences_said in the chat (a table: feed, "
                               + "streams, first and last stream, dormant), then ask: whether one translation "
                               + "serves every feed's records (the union)" + (comparison.zonesToAsk().isEmpty()
                    ? ""
                    : "; each feed's time zone for times with none (" + String.join(", ", comparison.zonesToAsk())
                      + ")") + (conflicts.isEmpty()
                    ? ""
                    : "; which rule applies for each conflict") + ". Then call " + NAME + " build=" + build
                               + " confirm=true" + (comparison.zonesToAsk().isEmpty()
                    ? ""
                    : " time_zones={feed: zone}") + (conflicts.isEmpty()
                    ? ""
                    : " agents={key: value}") + ", only as they said.");
            return result;
        }
        final Map<String, Object> keep = new LinkedHashMap<>();
        keep.put("digest", comparison.digest());
        keep.put("feeds", feeds);
        keep.put("confirmed", LocalDate.now(ZoneOffset.UTC).toString());
        keep.put("time_zones", answers.zones());
        keep.put("agents", answers.agents());
        records.rememberFeedComparison(build, keep);
        result.put("kept", keep);
        result.put("status_said", "Kept in the build: one translation for every feed's records" + (answers.zones()
                .isEmpty()
                ? ""
                : "; each feed's times read in its own zone (the draft writes them in)") + (answers.agents()
                .isEmpty()
                ? ""
                : "; the AGENTS rules chosen are the ones the tools apply to the shared documents") + ".");
        return plan.withNext(context, build, result);
    }

    // Each feed's survey state, by the feed's name as the build names it.
    private Map<String, Map<String, Object>> statesByFeed(final String build, final List<String> feeds) {
        final Map<String, Map<String, Object>> byLower = new LinkedHashMap<>();
        for (final Map<String, Object> state : builds.surveyStatesInBuild(build)) {
            byLower.put(JsonValues.text(state.get("feed")).toLowerCase(Locale.ROOT), state);
        }
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (final String feed : feeds) {
            final Map<String, Object> state = byLower.get(feed.toLowerCase(Locale.ROOT));
            if (state != null) {
                out.put(feed, state);
            }
        }
        return out;
    }

    // Whether what is kept answers everything asked now: each zone asked, each conflict.
    private static boolean answered(final Map<String, Object> kept,
                                    final FeedShapeComparison.Comparison comparison,
                                    final List<AgentsConflicts.Conflict> conflicts) {
        final Map<String, Object> zones = JsonValues.mapOrEmpty(kept.get("time_zones"));
        final Map<String, Object> chosen = JsonValues.mapOrEmpty(kept.get("agents"));
        return comparison.zonesToAsk().stream().allMatch(zones::containsKey)
               && conflicts.stream().allMatch(conflict -> chosen.containsKey(conflict.key()));
    }

    // The user's answers: given as arguments (on their word), else asked in forms, a set at a time (the union and
    // the conflicts, then the zones); null when no form can be shown and none were given.
    private Answers answers(final ToolCall call,
                            final ToolCallContext context,
                            final String build,
                            final List<String> feeds,
                            final FeedShapeComparison.Comparison comparison,
                            final List<AgentsConflicts.Conflict> conflicts) {
        final Object confirm = ToolArgs.raw(call, CONFIRM_ARG);
        if (confirm != null) {
            if (!JsonValues.truthy(confirm)) {
                throw new ToolRefusal(notUnion(build));
            }
            return new Answers(givenZones(call, comparison), givenAgents(call, conflicts, feeds));
        }
        final Map<String, Consent.Question> questions = new LinkedHashMap<>();
        questions.put("union", new Consent.Question(comparison.differs()
                ? "These feeds differ (see the chat). One translation for them all, every feed's fields and kinds "
                  + "of event handled?"
                : "These feeds' records look alike. One translation for them all?", List.of(UNION, NOT_UNION)));
        final Map<String, Map<String, AgentsConflicts.Ruled>> options = new LinkedHashMap<>();
        for (final AgentsConflicts.Conflict conflict : conflicts) {
            final Map<String, AgentsConflicts.Ruled> each = conflict.options(feeds);
            options.put(conflict.key(), each);
            questions.put(conflict.key(), new Consent.Question(conflict.question(), List.copyOf(each.keySet())));
        }
        final Optional<Map<String, String>> chosen = consent.chooseEach(call, context, NAME, "Build '" + build
                                                                                              + "': "
                                                                                              + String.join(" ",
                comparison.said().isEmpty()
                        ? List.of("its feeds' records look alike.")
                        : comparison.said()), questions);
        if (chosen.isEmpty()) {
            return null;
        }
        if (!UNION.equals(chosen.get().get("union"))) {
            throw new ToolRefusal(notUnion(build));
        }
        final Map<String, Object> agentsChosen = new LinkedHashMap<>();
        options.forEach((key, each) -> {
            final AgentsConflicts.Ruled ruled = each.get(chosen.get().get(key));
            if (ruled != null) {
                agentsChosen.put(key, choice(ruled.value(), ruled.folder()));
            }
        });
        final Map<String, Object> zones = new LinkedHashMap<>();
        if (!comparison.zonesToAsk().isEmpty()) {
            final Map<String, Consent.OpenQuestion> asked = new LinkedHashMap<>();
            for (final String feed : comparison.zonesToAsk()) {
                final List<String> offered = new ArrayList<>();
                comparison.zones().getOrDefault(feed, Set.of()).stream()
                        .filter(zone -> !FeedShapeComparison.NO_ZONE.equals(zone))
                        .forEach(offered::add);
                if (!offered.contains("UTC")) {
                    offered.add("UTC");
                }
                asked.put(feed, new Consent.OpenQuestion("Feed " + feed + ": which zone does it write times with "
                                                         + "no zone in?", offered, OTHER_ZONE, "The zone (an IANA "
                                                                                               + "name or offset)"));
            }
            final Optional<Map<String, String>> given = consent.chooseOrTypeEach(call, context, NAME,
                    "Build '" + build + "': its feeds' times are written in different zones. Each feed's zone is "
                    + "kept for its translation.", asked);
            if (given.isEmpty()) {
                return null;
            }
            given.get().forEach((feed, zone) -> zones.put(feed, checkedZone(feed, zone)));
        }
        return new Answers(zones, agentsChosen);
    }

    private static String notUnion(final String build) {
        return "The user doesn't want one translation for every feed of build '" + build + "' as it stands: nothing "
               + "was kept. Ask them what to do (a feed left out of the build, or built on its own), and do that.";
    }

    // The zones given as time_zones=, each checked; every feed asked must have one.
    private static Map<String, Object> givenZones(final ToolCall call,
                                                  final FeedShapeComparison.Comparison comparison) {
        final Map<String, Object> given = JsonValues.mapOrEmpty(ToolArgs.raw(call, TIME_ZONES_ARG));
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final String feed : comparison.zonesToAsk()) {
            final Object zone = given.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(feed))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (zone == null || ToolText.isBlank(String.valueOf(zone))) {
                throw new ToolRefusal("The feeds' times are written in different zones: give time_zones with each "
                                      + "of " + String.join(", ", comparison.zonesToAsk()) + " and its zone, as "
                                      + "the user said (" + feed + " has none). Nothing was kept.");
            }
            out.put(feed, checkedZone(feed, String.valueOf(zone)));
        }
        return out;
    }

    // The conflicts' answers given as agents=, each one of the values the feeds' docs give.
    private static Map<String, Object> givenAgents(final ToolCall call,
                                                   final List<AgentsConflicts.Conflict> conflicts,
                                                   final List<String> feeds) {
        final Map<String, Object> given = JsonValues.mapOrEmpty(ToolArgs.raw(call, AGENTS_ARG));
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final AgentsConflicts.Conflict conflict : conflicts) {
            final String value = JsonValues.text(given.get(conflict.key())).strip();
            final Optional<AgentsConflicts.Ruled> ruled = conflict.byFeed().values().stream()
                    .filter(each -> each.value().equalsIgnoreCase(value))
                    .findFirst();
            if (ruled.isEmpty()) {
                final Set<String> values = new LinkedHashSet<>();
                conflict.options(feeds).values().forEach(each -> values.add("'" + each.value() + "'"));
                throw new ToolRefusal("The AGENTS docs of the feeds' folders differ on " + conflict.key() + ": give "
                                      + "agents={'" + conflict.key() + "': <one of " + String.join(", ", values)
                                      + ">}, as the user chose. Nothing was kept.");
            }
            out.put(conflict.key(), choice(ruled.get().value(), ruled.get().folder()));
        }
        return out;
    }

    private static String checkedZone(final String feed, final String zone) {
        final String stripped = zone.strip();
        if (ZoneSource.unreadable(stripped)) {
            throw new ToolRefusal("'" + stripped + "' (for feed " + feed + ") is not a zone Stroom reads: give an "
                                  + "IANA name (e.g. Australia/Sydney, UTC) or an offset (+10:00). Nothing was kept.");
        }
        return stripped;
    }

    private static Map<String, Object> choice(final String value, final String folder) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("value", value);
        out.put("folder", NullSafe.nonBlankStringElse(folder, "/"));
        return out;
    }

    private static Map<String, Object> conflictJson(final AgentsConflicts.Conflict conflict,
                                                    final List<String> feeds) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", conflict.key());
        final Map<String, Object> byFeed = new LinkedHashMap<>();
        for (final String feed : feeds) {
            final AgentsConflicts.Ruled ruled = conflict.byFeed().get(feed);
            if (ruled != null) {
                byFeed.put(feed, ruled.value() + " (the AGENTS doc in " + ruled.folder() + ")");
            }
        }
        out.put("by_feed", byFeed);
        return out;
    }


    // --------------------------------------------------------------------------------


    // The user's answers: each feed's zone (where asked) and each conflict's choice.
    private record Answers(Map<String, Object> zones, Map<String, Object> agents) {

    }
}
