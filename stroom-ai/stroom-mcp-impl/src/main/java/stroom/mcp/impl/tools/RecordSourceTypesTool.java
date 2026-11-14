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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.indexing.FeedIndexes;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeProfiles;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// `record_source_types` - the plan's source types step for an aggregated feed: each source type the build's survey
/// counted is profiled (by the registry's rows, so a pattern's source type is one), the profiles are shown to the
/// user as a table, the user chooses in a form which source types go in the build's one source type group and what it
/// is called (its scope), and the feed's source type registry (`<feed> source types`) is written in the build,
/// through [WriteGuard]. An assessment writes
/// the registry without a scope. Rows are a value each, or patterns where the values are many structured ones,
/// confirmed by the user; a registry beside the feed is carried on from (a working copy), its rows and statuses kept
/// and values it lacks added as `new`.
///
/// A build makes at most one source type group, so one pipeline: the scope is that group, any number of source types
/// under one name (the agent suggests it, the user keeps or changes it), and the source types left out are parked for
/// a later build. Naming a group the registry has already adds the chosen source types to it (they join its pipeline
/// and output feed when it is built). The scope and its group are recorded in the registry and in the survey state,
/// with the rows it was chosen by ([SourceTypeScope]), and the build's tools read the feed through it (the survey,
/// samples, classification, the draft, the sample checks and review_coverage). The source types in it stay `parked`
/// until a source type group translates them. A scope that adds source types the survey's streams weren't read for
/// starts its coverage again.
class RecordSourceTypesTool extends AbstractBuiltInTool {

    static final String NAME = "record_source_types";
    static final String BUILD_ARG = "build";
    static final String FEED_ARG = "feed";
    static final String REVIEWED_ARG = "reviewed";
    static final String ROWS_ARG = "rows";
    static final String REMOVE_ARG = "remove";
    static final String PATTERNS_ARG = "patterns";
    static final String SCOPE_ARG = "scope";
    static final String GROUP_ARG = "group";
    /// The scope's key for the name of the build's source type group.
    static final String SCOPE_GROUP = "group";
    static final String REASON_ARG = "reason";
    static final String DROPPED_ARG = "dropped";
    static final String DROP_REASON_ARG = "drop_reason";
    static final String SYSTEM_NAME_ARG = "system_name";
    static final String SYSTEM_NAMES_ARG = "system_names";
    static final String ENVIRONMENT_ARG = "environment";
    // The answers to the proposed patterns.
    static final String USE_PATTERNS = "Use these patterns";
    // The answer using the proposed list items.
    static final String USE_ITEMS = "Use these list items";
    static final String ONE_PER_VALUE = "One source type per value";
    // Who decided a status that no one chose: an assessment registers every source type as parked.
    private static final String ASSESSED = "assessment";
    private static final String NEEDS_REVIEW = "needs_review";
    private static final String NEEDS_USER = "needs_user";
    // The scope form's free-text field: the group's name.
    private static final String GROUP_QUESTION = "What is the group called?";

    private final SurveyDocuments surveyDocuments;
    private final SourceTypeRegistries registries;
    private final BuildRecords buildRecords;
    private final Consent consent;
    private final BuildPlan plan;

    @Inject
    RecordSourceTypesTool(final SurveyDocuments surveyDocuments,
                          final SourceTypeRegistries registries,
                          final BuildRecords buildRecords,
                          final Consent consent,
                          final BuildPlan plan) {
        this.surveyDocuments = surveyDocuments;
        this.registries = registries;
        this.buildRecords = buildRecords;
        this.consent = consent;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean isReadOnly() {
        // It writes the build's registry and survey docs.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder.
        return true;
    }

    @Override
    public boolean confirmsItself() {
        // The user reviews the profiles in the chat, then chooses the scope in a form, before anything is written.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The registry is made (or changed) in the build's folder as the user, and the build's survey "
                      + "doc is changed as the user; a registry beside the feed is read as the user.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("The source types step of an aggregated feed (one whose records come from several "
                             + "sources, told apart by one field the survey confirmed with the user). The first "
                             + "call profiles each source type from the build's survey (size, message formats, "
                             + "fields, kinds, levels, signals of an audit trail or operational traffic, a "
                             + "suggested category) and returns a table to show the user (needs_review). Called "
                             + "again with reviewed=<its review_id> and group=<a name you suggest>, the user chooses "
                             + "in a form which source types go in this build's source type group (a build makes "
                             + "one group, one pipeline; any number of source types) and keeps or changes its "
                             + "name; the others are parked for later builds. Naming an existing group adds the "
                             + "chosen source types to it, or, none chosen, changes it alone. Then it writes the "
                             + "feed's registry ('<feed> source types') in the build. An assessment build writes the "
                             + "registry without a scope. Where the field's values are many paths or names, it "
                             + "proposes anchored patterns, and where they are lists (`DNS, File Server`), a row "
                             + "an item; either confirmed by the user. Rows given again keep the "
                             + "registry's translated and placed rows they leave out; remove= drops a row, and "
                             + "removing a translated or placed row, or changing a translated row's value or "
                             + "pattern, is confirmed by the user in a form.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, "The build.")
                        .addStringProperty(FEED_ARG, "The surveyed feed.")
                        .addStringProperty(REVIEWED_ARG, "The review_id of the needs_review reply, once its table "
                                                         + "has been shown to the user.")
                        .addProperty(ROWS_ARG, stringArraySchema("The rows a needs_review reply gave back, or "
                                                                 + "only as the user says: the source types in the "
                                                                 + "order tried, each 'name = value' (exactly) or "
                                                                 + "'name ~ ^pattern' (an anchored regular "
                                                                 + "expression on the field's value), e.g. 'mail "
                                                                 + "audit ~ ^/var/log/mail/audit/', or 'name ∈ "
                                                                 + "item' (ASCII: 'name in item'): a value that is "
                                                                 + "a list (its separator read from the values seen) "
                                                                 + "holding the item exactly, e.g. 'Mail ∈ Mail "
                                                                 + "Server'. Narrower before wider; the first row "
                                                                 + "matching takes a record, so of a list holding "
                                                                 + "several rows' items, the earlier row's. Without "
                                                                 + "it, rows are proposed from the "
                                                                 + "values the survey saw. A registry's translated "
                                                                 + "or placed rows left out are kept (remove= "
                                                                 + "drops one); a translated row's changed value or "
                                                                 + "pattern is confirmed by the user in a form; a "
                                                                 + "translated row can't be renamed (add the new "
                                                                 + "row and remove the old one)."))
                        .addProperty(REMOVE_ARG, stringArraySchema("Only on the user's word: rows of the registry "
                                                                   + "to remove, by name. A translated or placed "
                                                                   + "row's removal is confirmed by the user in a "
                                                                   + "form (refused for a translated row where no "
                                                                   + "form can be shown)."))
                        .addStringProperty(PATTERNS_ARG, "Only when the client shows no forms: the user's answer "
                                                         + "to the proposed patterns or list items ('use' them, or "
                                                         + "'exact': a source type per value).")
                        .addProperty(SCOPE_ARG, stringArraySchema("Only when the client shows no forms: the "
                                                                  + "source types the user chose for this build's "
                                                                  + "group, by name (with group). [] with an "
                                                                  + "existing group's name changes that group "
                                                                  + "alone (refused for a new group)."))
                        .addStringProperty(GROUP_ARG, "The name of this build's source type group, for its source "
                                                      + "types (e.g. 'Messaging'): with forms, your suggestion, "
                                                      + "which the user keeps or changes in the form; without, "
                                                      + "the name the user gave. An existing group's name adds the "
                                                      + "source types to that group.")
                        .addStringProperty(REASON_ARG, "Why the other source types are left for later, in the "
                                                       + "user's words (no forms; default 'left for a later "
                                                       + "build').")
                        .addProperty(DROPPED_ARG, stringArraySchema("Only on the user's word: source types never "
                                                                    + "to be translated, by name."))
                        .addStringProperty(DROP_REASON_ARG, "Why those are never translated, in the user's words.")
                        .addStringProperty(SYSTEM_NAME_ARG, "The feed's EventSource/System/Name, the default for "
                                                            + "every source type, as the user gave it.")
                        .addProperty(SYSTEM_NAMES_ARG, TranslationArgs.object("A source type's own System Name "
                                                                              + "where it differs from the feed's, "
                                                                              + "by source type name, as the user "
                                                                              + "gave them."))
                        .addStringProperty(ENVIRONMENT_ARG, "The feed's Environment, as the user gave it.")
                        .required(FEED_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final String build = plan.resolveBuild(context, stringArg(call, BUILD_ARG), NAME);
            return JsonResults.of(record(call, context, build));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> record(final ToolCall call, final ToolCallContext context, final String build) {
        final String feed = stringArg(call, FEED_ARG);
        if (feed == null) {
            throw new ToolRefusal("Give feed: the surveyed feed.");
        }
        // One open build per aggregated feed: a registry is made or changed by one build at a time.
        registries.refuseOpenBuild(feed, build, true);
        final Optional<SurveyRecord> surveyDoc = surveyDocuments.load(build, feed);
        final Map<String, Object> survey = surveyDoc.map(doc -> SurveyDoc.readState(doc.text())).orElse(null);
        final Map<String, Object> sources = survey == null
                ? Map.of()
                : JsonValues.mapOrEmpty(survey.get(SourceTypeProfiles.STATE_KEY));
        final SourceTypeRegistries.Found existing = registries.registry(build, feed).orElse(null);
        final Map<String, Object> before = existing == null
                ? Map.of()
                : existing.state();
        final String field = NullSafe.nonBlankStringElse(JsonValues.text(sources.get(SourceTypeProfiles.FIELD)),
                JsonValues.text(before.get("field")));
        if (field.isBlank()) {
            throw new ToolRefusal("No source type field is known for feed '" + feed + "' in build '" + build
                                  + "': survey_feed build='" + build + "' feed='" + feed + "' finds it and asks the "
                                  + "user (or source_type_field= as they said), then call " + NAME + " again.");
        }
        final Map<String, Long> values = SourceTypeProfiles.valueCounts(sources);
        final Rows proposed = rows(call, context, feed, field, values, before);
        if (proposed.early() != null) {
            return proposed.early();
        }
        // The registry's translated and placed rows are kept unless removed; changing or removing one is the user's.
        final Edits edits = edits(call, context, proposed.rows(), before);
        final Rows rows = new Rows(edits.rows(), null);
        final List<String> problems = SourceTypeRows.problems(rows.rows(), values.keySet());
        if (!problems.isEmpty()) {
            throw new ToolRefusal("These rows can't be used: " + String.join("; ", problems) + ". Nothing was "
                                  + "written.");
        }
        final Map<String, Map<String, Object>> profiles = profiles(sources, before, rows.rows());
        final String table = table(feed, field, rows.rows(), profiles, statuses(before));
        final boolean assessment = buildRecords.kind(build).filter(StartOnboardingTool.ASSESSMENT::equals)
                .isPresent();
        final Map<String, Object> registry = registry(call, build, feed, field, sources, before, rows.rows(),
                profiles);
        if (assessment) {
            final String now = SurveyDoc.at(Instant.now());
            JsonValues.maps(registry.get("rows")).stream()
                    .filter(row -> row.get("status") == null)
                    .forEach(row -> decide(row, SourceTypeRegistry.PARKED, ASSESSED, now, "assessed: no build has "
                                                                                          + "taken it on yet"));
            confirm(call, context, feed, edits);
            final SurveyRecord written = registries.writeRegistry(context.session(), build, feed, existing,
                    SourceTypeRegistry.render(registry));
            final Map<String, Object> result = written(feed, field, registry, written, table);
            result.put("hint", "The registry is written: show the user the table (as it is). Then promote_build, the "
                               + "registry going beside the feed (the survey doc stays in this build): until then "
                               + "this build is open on the feed, and no other build can start on it. Once "
                               + "promoted, offer to onboard the source types they pick, one source type group "
                               + "(any number of source types, one pipeline) a build (start_build feeds=['" + feed
                               + "']: it surveys the feed afresh, and " + NAME + " offers the registry's parked "
                               + "source types for its group).");
            offerToFind(result, build, feed, unseen(registry, before, List.of()));
            return plan.withNext(context, build, result);
        }
        final Object reviewed = argsMap(call).get(REVIEWED_ARG);
        if (!Consent.wasReviewed(reviewed, table)) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", NEEDS_REVIEW);
            result.put("feed", feed);
            result.put("field", field);
            result.put("table", table);
            result.put("review_id", Consent.reviewId(table));
            // The rows as confirmed, for the next call to give back, so the user isn't asked about them again.
            result.put(ROWS_ARG, rows.rows().stream().map(row -> row.name() + " " + row.said()).toList());
            if (!edits.confirmations().isEmpty()) {
                result.put("to_confirm", edits.confirmations());
            }
            result.put("hint", (context.session().isChat()
                    ? "The chat shows the user this table itself, as it is: don't repeat or restyle it. "
                    : "Show the user this table in your reply, as it is, sorted by suggested category then size, "
                      + "with what each suggestion rests on. ")
                               + "Then call " + NAME + " again with the same arguments, rows=<these rows>, "
                               + "reviewed=<review_id> and " + GROUP_ARG + "=<a name you suggest for the group>: "
                               + "the user chooses in a form which source types go in this build's one source type "
                               + "group (one pipeline; any number of them, e.g. those of one kind of system) and "
                               + "keeps or changes its name; the rest are parked for later builds. Suggest; don't "
                               + "choose for them." + groupsSaid(registryGroups(before)));
            return result;
        }
        final Optional<Scope> scope = scope(call, context, feed, registry, profiles);
        if (scope.isEmpty()) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", NEEDS_USER);
            result.put("feed", feed);
            result.put("offered", offered(registry).stream().map(row -> JsonValues.text(row.get("name"))).toList());
            result.put("groups", registryGroups(registry));
            result.put("hint", "Ask the user in the chat which of the offered source types go in this build's source "
                               + "type group (a build makes one group, one pipeline; the table shown) and what the "
                               + "group is called (suggest a name; an existing group's name adds them to it); the "
                               + "rest are parked for later builds. Then call " + NAME + " again with "
                               + "reviewed=<review_id>, " + SCOPE_ARG + "=[their names], " + GROUP_ARG
                               + "=<the name> and, if they gave one, " + REASON_ARG + " (why the rest wait); "
                               + SCOPE_ARG + "=[] with an existing group's name changes that group alone.");
            return result;
        }
        // The group's source types: those it translates already (an existing group named), and those chosen now.
        final List<String> groupTypes = new ArrayList<>(groupRows(registry, scope.get().group()));
        scope.get().chosen().stream().filter(name -> !groupTypes.contains(name)).forEach(groupTypes::add);
        if (groupTypes.isEmpty()) {
            throw new ToolRefusal("No source type was chosen for group '" + scope.get().group() + "': a build makes "
                                  + "one source type group, of one or more source types. Ask the user which go in it, "
                                  + "and call " + NAME + " again. Nothing was written.");
        }
        confirm(call, context, feed, edits);
        final String now = SurveyDoc.at(Instant.now());
        applyScope(registry, build, scope.get(), now);
        applyDropped(call, registry, now);
        registry.put("scope", Entries.of("build", build, SCOPE_GROUP, scope.get().group(), "source_types",
                groupTypes, "by", "user", "at", now));
        final SurveyRecord written = registries.writeRegistry(context.session(), build, feed, existing,
                SourceTypeRegistry.render(registry));
        boolean restarted = false;
        if (surveyDoc.isPresent() && survey != null) {
            // The scope is the survey's too: kept in its state, as the kind fields are, with the rows it was chosen
            // by, so the tools reading through it match records as the registry does.
            final Map<String, Object> kept = new LinkedHashMap<>(sources);
            final Map<String, Object> surveyScope = new LinkedHashMap<>(JsonValues.mapOrEmpty(registry.get("scope")));
            surveyScope.put(SourceTypeScope.ROWS, SourceTypeScope.kept(rows.rows()));
            kept.put(SourceTypeProfiles.SCOPE, surveyScope);
            survey.put(SourceTypeProfiles.STATE_KEY, kept);
            restarted = restartsCoverage(survey, new SourceTypeScope(build, field, rows.rows(), groupTypes));
            surveyDocuments.write(context.session(), surveyDoc.get(), SurveyDoc.render(survey));
        }
        final Map<String, Object> result = written(feed, field, registry, written, table);
        result.put("scope", groupTypes);
        result.put(GROUP_ARG, scope.get().group());
        final Optional<Map<String, Object>> joined = placedRow(registry, scope.get().group());
        if (joined.isPresent()) {
            result.put("joins", "Group '" + scope.get().group() + "' exists: the source types join it, its pipeline"
                                + " and where its Events go, when build_translation_xslt builds it (through a "
                                + "working copy of its XSLT where it is promoted).");
        }
        if (restarted) {
            result.put("coverage_restarted", "The scope adds source types some streams the survey read weren't read "
                                             + "for (a narrower scope read them): its coverage starts again, and "
                                             + "survey_feed build='" + build + "' feed='" + feed + "' reads them "
                                             + "again for those source types.");
        }
        result.put("hint", "The registry is written and the build's scope recorded: source type group '"
                           + scope.get().group() + "', " + groupTypes + ". build_translation_xslt builds "
                           + "it (group and source_types default to these); a build makes one group, so another "
                           + "is a later build's, after this one is promoted. Carry on with the plan (next).");
        offerToFind(result, build, feed, unseen(registry, before, groupTypes));
        return plan.withNext(context, build, result);
    }

    /// Whether a scope adds source types some stream the survey read wasn't read for: if so the survey is no longer
    /// covered (changed in place: not saturated, nor accepted as it is), and survey_feed reads those streams again
    /// for them.
    ///
    /// @param survey The build's survey state, changed in place.
    /// @param scope  The scope chosen.
    /// @return True if the survey's coverage starts again.
    static boolean restartsCoverage(final Map<String, Object> survey, final SourceTypeScope scope) {
        final boolean uncovered = JsonValues.listOrEmpty(survey.get("streams_read")).stream()
                .filter(Number.class::isInstance)
                .map(id -> ((Number) id).longValue())
                .anyMatch(stream -> !scope.covers(SurveyDoc.readFor(survey, stream)));
        if (uncovered) {
            survey.put("saturated", false);
            survey.remove(SurveyDoc.ACCEPTED);
        }
        return uncovered;
    }

    /// The rows of a registry written that the survey has read no record of, and that the user would want found: those
    /// new to the registry (nominated by the user), and those in the build's group; never a dropped one.
    ///
    /// @param registry The registry written.
    /// @param before   The registry before (empty when there was none).
    /// @param group    The source types of the build's group, or none.
    /// @return Their names, in the registry's order.
    static List<String> unseen(final Map<String, Object> registry,
                               final Map<String, Object> before,
                               final Collection<String> group) {
        final Set<String> known = new LinkedHashSet<>();
        JsonValues.maps(before.get("rows")).forEach(row -> known.add(JsonValues.text(row.get("name"))));
        return JsonValues.maps(registry.get("rows")).stream()
                .filter(row -> !SourceTypeRegistry.DROPPED.equals(row.get("status")))
                .filter(row -> JsonValues.mapOrEmpty(row.get("profile")).get("records_read")
                                       instanceof final Number read && read.longValue() == 0)
                .map(row -> JsonValues.text(row.get("name")))
                .filter(name -> !known.contains(name) || group.contains(name))
                .toList();
    }

    // Rows the survey hasn't met: the agent offers the user two ways to find their records, in plain words.
    private static void offerToFind(final Map<String, Object> result,
                                    final String build,
                                    final String feed,
                                    final List<String> unseen) {
        if (unseen.isEmpty()) {
            return;
        }
        result.put("unseen_source_types", unseen);
        result.put("hint", result.get("hint") + " The survey has read no record of " + unseen + " yet. Offer the "
                           + "user two ways to find one: they give the ID of a stream that holds it (survey_feed "
                           + "feed='" + feed + "' build='" + build + "' stream_ids=[<id>] find='<name>'), or a "
                           + "search of the feed's streams, newest first (survey_feed feed='" + feed + "' build='"
                           + build + "' find='<name>'). One source type a call.");
    }

    // The registry's rows: as the user gave them, those of the registry beside the feed (values it lacks added),
    // or proposed from the values the survey saw (patterns confirmed by the user).
    private Rows rows(final ToolCall call,
                      final ToolCallContext context,
                      final String feed,
                      final String field,
                      final Map<String, Long> values,
                      final Map<String, Object> before) {
        final List<String> given = stringListArg(call, ROWS_ARG);
        if (!given.isEmpty()) {
            // A list's item is matched on the separator the values seen are lists by.
            return new Rows(SourceTypeRows.withSeparator(given.stream().map(RecordSourceTypesTool::parseRow)
                    .toList(), SourceTypeRows.separator(values.keySet())), null);
        }
        if (!before.isEmpty()) {
            return new Rows(withValuesSeen(SourceTypeRegistry.rows(before), values), null);
        }
        if (values.isEmpty()) {
            throw new ToolRefusal("The survey of feed '" + feed + "' has counted no records by '" + field + "' yet: "
                                  + "survey_feed with the build first.");
        }
        final SourceTypeRows.Proposal proposal = SourceTypeRows.propose(values);
        final List<SourceTypeRows.Row> proposed = proposal.rows().stream().map(SourceTypeRows.Proposed::row)
                .toList();
        if (!proposal.patterns() && !proposal.lists()) {
            return new Rows(proposed, null);
        }
        final String given2 = stringArg(call, PATTERNS_ARG);
        final Optional<String> answer = given2 != null
                ? Optional.of(given2)
                : proposal.lists()
                        ? consent.answer(call, context, NAME, itemsIntro(feed, field, proposal),
                                "Use these list items, or one source type per value?", List.of(USE_ITEMS,
                                        ONE_PER_VALUE))
                        : consent.answer(call, context, NAME, patternsIntro(feed, field, proposal),
                                "Use these patterns, or one source type per value?", List.of(USE_PATTERNS,
                                        ONE_PER_VALUE));
        if (answer.isEmpty()) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", NEEDS_USER);
            result.put("feed", feed);
            result.put("proposed_rows", proposal.rows().stream()
                    .map(row -> Entries.of("name", row.row().name(), "match", row.row().said(), "values",
                            row.values(), "records", row.records()))
                    .toList());
            result.put("hint", (proposal.lists()
                    ? "The values of '" + field + "' are lists: a source type an item is proposed, rarest first "
                      + "(a record whose list holds several is the first row's), each covering the values shown. "
                    : "The values of '" + field + "' are many paths or names: these patterns are proposed, "
                      + "each covering the values shown, tried in order (narrower first). ")
                               + "Ask the user in the chat whether to use them, or have a source type per value, then "
                               + "call " + NAME + " again with " + PATTERNS_ARG + "='use' or 'exact' (or " + ROWS_ARG
                               + " as they say).");
            return new Rows(List.of(), result);
        }
        final String said = answer.get().strip().toLowerCase(Locale.ROOT);
        if (said.startsWith("one") || said.contains("exact") || said.contains("per value")) {
            final List<SourceTypeRows.Row> exact = new ArrayList<>();
            values.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> exact.add(new SourceTypeRows.Row(entry.getKey(), SourceTypeRows.VALUE,
                            entry.getKey())));
            return new Rows(exact, null);
        }
        if (said.startsWith("use") || said.startsWith("yes") || said.contains("pattern") || said.contains("item")) {
            return new Rows(proposed, null);
        }
        throw new ToolRefusal("The user's answer ('" + answer.get().strip() + "') is neither: ask them in the chat "
                              + "how to match the source types, and call " + NAME + " again with " + ROWS_ARG
                              + " as they say ('name = value', 'name ~ ^pattern' or 'name ∈ item', in order).");
    }

    // The rows as they will be: those given (or proposed), with the registry's translated and placed rows they leave
    // out kept in their place, and the rows remove= names taken out. A translated row renamed is refused; removing a
    // translated or placed row, or changing a translated row's value or pattern, is for the user to confirm (refused
    // for a translated row where no form can be shown).
    private Edits edits(final ToolCall call,
                        final ToolCallContext context,
                        final List<SourceTypeRows.Row> given,
                        final Map<String, Object> before) {
        final List<String> remove = stringListArg(call, REMOVE_ARG).stream().map(String::strip).toList();
        final List<Map<String, Object>> previous = JsonValues.maps(before.get("rows"));
        final Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        previous.forEach(row -> byName.put(JsonValues.text(row.get("name")), row));
        final Set<String> givenNames = new LinkedHashSet<>();
        given.forEach(row -> givenNames.add(row.name()));
        for (final String name : remove) {
            if (!byName.containsKey(name)) {
                throw new ToolRefusal("remove names '" + name + "', which is no row of the registry: it has "
                                      + ToolText.list(List.copyOf(byName.keySet())) + ". Nothing was written.");
            }
            if (givenNames.contains(name) && !stringListArg(call, ROWS_ARG).isEmpty()) {
                throw new ToolRefusal("'" + name + "' is both in rows and in remove: give it in one. Nothing was "
                                      + "written.");
            }
        }
        // A translated row renamed: its records would leave its group unsaid.
        for (final SourceTypeRows.Row row : given) {
            if (byName.containsKey(row.name())) {
                continue;
            }
            previous.stream()
                    .filter(was -> translated(was) && !givenNames.contains(JsonValues.text(was.get("name")))
                                   && !remove.contains(JsonValues.text(was.get("name")))
                                   && row.match().equals(JsonValues.text(was.get("match")))
                                   && row.value().equals(JsonValues.text(was.get("value"))))
                    .findFirst()
                    .ifPresent(was -> {
                        throw new ToolRefusal("Row '" + row.name() + "' would rename translated row '"
                                              + was.get("name") + "' (group '" + was.get("group") + "'): a translated "
                                              + "row isn't renamed. Add the new row and remove the old one (remove=['"
                                              + was.get("name") + "']), as the user confirms. Nothing was written.");
                    });
        }
        final List<String> confirmations = new ArrayList<>();
        final List<String> needForms = new ArrayList<>();
        for (final SourceTypeRows.Row row : given) {
            final Map<String, Object> was = byName.get(row.name());
            if (was != null && translated(was) && (!row.match().equals(JsonValues.text(was.get("match")))
                                                   || !row.value().equals(JsonValues.text(was.get("value"))))) {
                confirmations.add("Change translated row '" + row.name() + "' (group '" + was.get("group") + "') from "
                                  + said(was) + " to " + row.said());
                needForms.add(row.name());
            }
        }
        for (final String name : remove) {
            final Map<String, Object> was = byName.get(name);
            if (translated(was) || placed(was)) {
                confirmations.add("Remove row '" + name + "'" + (translated(was)
                        ? " (translated by group '" + was.get("group") + "': its records are passed over once its "
                          + "group is built again)"
                        : " (placed in a pipeline)"));
                if (translated(was)) {
                    needForms.add(name);
                }
            }
        }
        if (!needForms.isEmpty() && !context.userQuestions().canAsk()) {
            throw new ToolRefusal("Changing or removing translated row(s) " + ToolText.list(needForms) + " is for "
                                  + "the user to confirm in a form, and this client can't show one. Leave them as "
                                  + "they are. Nothing was written.");
        }
        // Kept in their place: each after the row it followed, else first.
        final List<SourceTypeRows.Row> out = new ArrayList<>(given.stream()
                .filter(row -> !remove.contains(row.name()))
                .toList());
        String after = null;
        for (final Map<String, Object> was : previous) {
            final String name = JsonValues.text(was.get("name"));
            final boolean present = out.stream().anyMatch(row -> row.name().equals(name));
            if (!present && !remove.contains(name) && (translated(was) || placed(was))) {
                final String follows = after;
                final int at = follows == null
                        ? 0
                        : out.stream().map(SourceTypeRows.Row::name).toList().indexOf(follows) + 1;
                out.add(at, SourceTypeRows.Row.of(was));
            }
            if (out.stream().anyMatch(row -> row.name().equals(name))) {
                after = name;
            }
        }
        return new Edits(out, confirmations);
    }

    // The user confirms the changes to translated or placed rows in a form (plain text), before anything is written.
    // Where no form can be shown, only a placed row not translated is removed (on the user's word, as remove= is
    // given): a translated row's change was refused before.
    private void confirm(final ToolCall call, final ToolCallContext context, final String feed, final Edits edits) {
        if (edits.confirmations().isEmpty() || !context.userQuestions().canAsk()) {
            return;
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("feed", feed);
        details.put("changes", edits.confirmations());
        consent.require(call, context, Consent.Request.confirmation(NAME, "Change the source type registry of '"
                                                                          + feed + "'", details));
    }

    private static boolean translated(final Map<String, Object> row) {
        return row != null && SourceTypeRegistry.TRANSLATED.equals(row.get("status"));
    }

    private static boolean placed(final Map<String, Object> row) {
        return row != null && (row.get("group") != null || row.get("pipeline") != null);
    }

    // A registry row's match, as the user gives one: `= value`, `~ pattern` or `∈ item`.
    private static String said(final Map<String, Object> row) {
        return SourceTypeRows.Row.of(row).said();
    }

    /// The registry's rows with a row added for each value the survey saw that none matches, commonest first: a value
    /// a row, or, where the registry's rows are a list's items, a row for the value's rarest item (over the values
    /// seen) that no row has, so the value is matched and no row added is one no value reaches.
    ///
    /// @param rows   The registry's rows, in order.
    /// @param values The records the survey saw with each value.
    /// @return The rows, those added after the registry's.
    static List<SourceTypeRows.Row> withValuesSeen(final List<SourceTypeRows.Row> rows,
                                                   final Map<String, Long> values) {
        final List<SourceTypeRows.Row> out = new ArrayList<>(rows);
        final Optional<SourceTypeRows.Row> listed = rows.stream()
                .filter(row -> SourceTypeRows.MEMBER.equals(row.match()))
                .findFirst();
        final Map<String, Long> itemRecords = new LinkedHashMap<>();
        listed.ifPresent(row -> values.forEach((value, records) -> SourceTypeRows.items(value,
                row.separatorOrDefault()).forEach(item -> itemRecords.merge(item, records, Long::sum))));
        values.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> {
                    if (SourceTypeRows.first(out, entry.getKey()).isPresent()) {
                        return;
                    }
                    final Optional<String> item = listed.flatMap(row -> SourceTypeRows.items(entry.getKey(),
                                    row.separatorOrDefault()).stream()
                            .filter(candidate -> out.stream().noneMatch(had -> had.name().equals(candidate)))
                            .min(Comparator.comparing((String candidate) -> itemRecords.getOrDefault(candidate, 0L))
                                    .thenComparing(Comparator.naturalOrder())));
                    out.add(item.isPresent()
                            ? new SourceTypeRows.Row(item.get(), SourceTypeRows.MEMBER, item.get(),
                            listed.get().separatorOrDefault())
                            : new SourceTypeRows.Row(entry.getKey(), SourceTypeRows.VALUE, entry.getKey()));
                });
        return out;
    }

    // The form's message: each list item proposed with the values and records it covers, in plain text.
    private static String itemsIntro(final String feed,
                                     final String field,
                                     final SourceTypeRows.Proposal proposal) {
        final String separator = proposal.rows().isEmpty()
                ? SourceTypeRows.DEFAULT_SEPARATOR
                : proposal.rows().getFirst().row().separatorOrDefault();
        final List<String> lines = new ArrayList<>();
        int n = 1;
        for (final SourceTypeRows.Proposed row : proposal.rows()) {
            lines.add(n++ + ". " + row.row().name() + " " + row.row().said() + ": " + row.records() + " records ("
                      + row.values().size() + " value(s))");
        }
        return "The values of '" + field + "' in feed '" + feed + "' are lists (split on '" + separator + "'): a "
               + "source type is proposed for each item, tried in this order, rarest first; a record whose list "
               + "holds several items is of the first row's. A value none matches later is a new source type.\n"
               + String.join("\n", lines);
    }

    // The form's message: each pattern with the values and records it covers, in plain text.
    private static String patternsIntro(final String feed,
                                        final String field,
                                        final SourceTypeRows.Proposal proposal) {
        final List<String> lines = new ArrayList<>();
        int n = 1;
        for (final SourceTypeRows.Proposed row : proposal.rows()) {
            lines.add(n++ + ". " + row.row().name() + " " + row.row().said() + ": " + row.records() + " records ("
                      + String.join(", ", row.values().subList(0, Math.min(4, row.values().size())))
                      + (row.values().size() > 4
                    ? ", +" + (row.values().size() - 4) + " more"
                    : "") + ")");
        }
        return "The values of '" + field + "' in feed '" + feed + "' are paths or names: these source types are "
               + "proposed, tried in this order, the first match winning (a pattern matches values starting so). A "
               + "value none matches later is a new source type.\n" + String.join("\n", lines);
    }

    // A row as the user gave it: `name = value`, `name ~ ^pattern`, or `name ∈ item` (`name in item`).
    private static SourceTypeRows.Row parseRow(final String given) {
        return SourceTypeRows.parse(given).orElseThrow(() -> new ToolRefusal("Row '" + given + "' is none of "
                                                                             + "'name = value', 'name ~ ^pattern' "
                                                                             + "and 'name ∈ item' ('name in "
                                                                             + "item')."));
    }

    // Each row's profile: from the survey's counts, else the registry beside the feed's own (carried over).
    private static Map<String, Map<String, Object>> profiles(final Map<String, Object> sources,
                                                             final Map<String, Object> before,
                                                             final List<SourceTypeRows.Row> rows) {
        if (JsonValues.truthy(sources.get(SourceTypeProfiles.VALUES))) {
            return SourceTypeProfiles.summarise(sources, rows);
        }
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> kept = new LinkedHashMap<>();
        JsonValues.maps(before.get("rows")).forEach(row -> kept.put(JsonValues.text(row.get("name")),
                JsonValues.mapOrEmpty(row.get("profile"))));
        rows.forEach(row -> out.put(row.name(), kept.getOrDefault(row.name(), Entries.of("records_read", 0,
                "rests_on", "not seen: no survey records read"))));
        return out;
    }

    // The registry's machine block, rows and statuses carried over from the one before (statuses of new rows are
    // decided below).
    private Map<String, Object> registry(final ToolCall call,
                                         final String build,
                                         final String feed,
                                         final String field,
                                         final Map<String, Object> sources,
                                         final Map<String, Object> before,
                                         final List<SourceTypeRows.Row> rows,
                                         final Map<String, Map<String, Object>> profiles) {
        final Map<String, Map<String, Object>> previous = new LinkedHashMap<>();
        JsonValues.maps(before.get("rows")).forEach(row -> previous.put(JsonValues.text(row.get("name")), row));
        final Map<String, Object> systemNames = JsonValues.mapOrEmpty(argsMap(call).get(SYSTEM_NAMES_ARG));
        final Map<String, Object> system = buildRecords.system(build).orElse(Map.of());
        final Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("feed", feed);
        registry.put("field", field);
        registry.put("field_by", NullSafe.nonBlankStringElse(JsonValues.text(sources.get(SourceTypeProfiles.BY)),
                JsonValues.text(before.getOrDefault("field_by", "user"))));
        registry.put("environment", firstSet(stringArg(call, ENVIRONMENT_ARG), before.get("environment"),
                system.get("environment")));
        registry.put("system_name", firstSet(stringArg(call, SYSTEM_NAME_ARG), before.get("system_name"),
                system.get("system_name") instanceof final String single && !single.startsWith("{")
                        ? single
                        : null));
        registry.put("updated", SurveyDoc.at(Instant.now()));
        if (before.get("modules") != null) {
            // The feed's helpers and common module, as its source type groups import them.
            registry.put("modules", before.get("modules"));
        }
        // The feed's shared indexing decisions and its output feeds' indexes, made by earlier builds.
        for (final String key : List.of(FeedIndexes.INDEXING, FeedIndexes.INDEXES)) {
            if (before.get(key) != null) {
                registry.put(key, before.get(key));
            }
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        final boolean first = before.isEmpty();
        final String now = SurveyDoc.at(Instant.now());
        for (final SourceTypeRows.Row row : rows) {
            final Map<String, Object> was = previous.getOrDefault(row.name(), Map.of());
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", row.name());
            entry.put("match", row.match());
            entry.put("value", row.value());
            if (SourceTypeRows.MEMBER.equals(row.match())) {
                entry.put("separator", row.separatorOrDefault());
            }
            entry.put("system_name", firstSet(systemNames.get(row.name()), was.get("system_name"), null));
            entry.put("status", was.get("status"));
            entry.put("decided", was.get("decided"));
            if (was.isEmpty() && !first) {
                // Seen since the registry was made: for the user to place.
                decide(entry, SourceTypeRegistry.NEW, "survey", now, "seen after the registry was made");
            }
            entry.put("group", was.get("group"));
            // Where its group's XSLT is, the group's pipeline, where its Events go and what it looks up.
            for (final String key : List.of("group_xslt", "pipeline", "output_feed")) {
                if (was.get(key) != null) {
                    entry.put(key, was.get(key));
                }
            }
            if (was.get("pipeline") instanceof final Map<?, ?> pipeline) {
                // Only what a group's pipeline is: its name, its uuid and whether it is a new version (an earlier
                // routing between pipelines, before each group had a pipeline of its own, is not kept).
                final Map<String, Object> kept = new LinkedHashMap<>(JsonValues.mapOrEmpty(pipeline));
                kept.keySet().retainAll(List.of("name", "uuid", "new_version"));
                entry.put("pipeline", kept);
            }
            if (was.get("references") != null) {
                entry.put("references", was.get("references"));
            }
            entry.put("profile", profiles.getOrDefault(row.name(), Map.of()));
            out.add(entry);
        }
        registry.put("rows", out);
        if (before.get("scope") != null) {
            registry.put("scope", before.get("scope"));
        }
        return registry;
    }

    // The scope the user chooses in a form: a yes for each source type offered (parked, new, or every one of a first
    // registry; none ticked to start) to put in the build's one source type group, and the group's name (the agent's
    // suggestion, kept or changed); the others are parked for a later build. Or as they said in the chat (scope=,
    // group=, reason=). An existing group's name, in any case, is that group's.
    private Optional<Scope> scope(final ToolCall call,
                                  final ToolCallContext context,
                                  final String feed,
                                  final Map<String, Object> registry,
                                  final Map<String, Map<String, Object>> profiles) {
        final List<Map<String, Object>> offered = offered(registry);
        final List<String> names = offered.stream().map(row -> JsonValues.text(row.get("name"))).toList();
        final List<String> given = stringListArg(call, SCOPE_ARG);
        final String suggested = NullSafe.string(stringArg(call, GROUP_ARG)).strip();
        final List<String> groups = registryGroups(registry);
        if (given.isEmpty() && emptyScopeGiven(call) && !context.userQuestions().canAsk()) {
            // Without forms, scope=[] with an existing group's name is the user's "change that group alone", as no
            // source type ticked in the form is.
            if (suggested.isEmpty()) {
                throw new ToolRefusal("Give " + GROUP_ARG + ": an empty " + SCOPE_ARG + " changes an existing source "
                                      + "type group alone, and names it" + groupsSaid(groups)
                                      + ". Nothing was written.");
            }
            final String group = groupNamed(groups, suggested);
            if (!groups.contains(group)) {
                throw new ToolRefusal("An empty " + SCOPE_ARG + " changes an existing source type group alone, and '"
                                      + group + "' is a new one: a new group needs one or more source types. Ask "
                                      + "the user which go in it" + groupsSaid(groups) + ". Nothing was written.");
            }
            return Optional.of(new Scope(List.of(), NullSafe.string(stringArg(call, REASON_ARG)).strip(), group));
        }
        if (!given.isEmpty()) {
            if (suggested.isEmpty()) {
                throw new ToolRefusal("Give " + GROUP_ARG + ": the name the user gave this build's source type group "
                                      + "(a build makes one group, its source types the scope's)"
                                      + groupsSaid(groups) + ". Nothing was written.");
            }
            final List<String> chosen = new ArrayList<>();
            for (final String name : given) {
                chosen.add(names.stream().filter(offer -> offer.equalsIgnoreCase(name.strip())).findFirst()
                        .orElseThrow(() -> new ToolRefusal("'" + name + "' is none of the source types offered: "
                                                           + names)));
            }
            return Optional.of(new Scope(List.copyOf(new LinkedHashSet<>(chosen)), NullSafe.string(stringArg(call,
                    REASON_ARG)).strip(), groupNamed(groups, suggested)));
        }
        if (context.userQuestions().canAsk() && suggested.isEmpty() && !names.isEmpty()) {
            throw new ToolRefusal("Give " + GROUP_ARG + ": a name you suggest for this build's source type group "
                                  + "(e.g. for the kind of system its source types are), which the user keeps or "
                                  + "changes in the form" + groupsSaid(groups) + ". Nothing was asked.");
        }
        final Map<String, Consent.Editable> items = new LinkedHashMap<>();
        for (final String name : names) {
            final Map<String, Object> profile = profiles.getOrDefault(name, Map.of());
            items.put(name, new Consent.Editable("Put " + name + " in the group", "about "
                                                                                + JsonValues.text(profile.getOrDefault(
                    "share_percent", 0)) + "% of the records read; " + String.join(", ",
                    JsonValues.mapOrEmpty(profile.get("formats")).keySet())));
        }
        final Optional<Map<String, Object>> answers = consent.review(call, context, NAME, scopeIntro(feed, groups),
                items, GROUP_QUESTION, suggested, false);
        if (answers.isEmpty()) {
            return Optional.empty();
        }
        final List<String> chosen = names.stream().filter(name -> Boolean.TRUE.equals(answers.get().get(name)))
                .toList();
        final String named = NullSafe.nonBlankStringElse(NullSafe.get(answers.get().get(Consent.REVIEW_TEXT),
                String::valueOf), suggested).strip();
        return Optional.of(new Scope(chosen, "", groupNamed(groups, named)));
    }

    // True when the call gives scope as an empty list: an answer (no source type added), not a scope left out.
    private static boolean emptyScopeGiven(final ToolCall call) {
        return argsMap(call).get(SCOPE_ARG) instanceof final List<?> list && list.isEmpty();
    }

    // The scope form's message, in plain text.
    private static String scopeIntro(final String feed, final List<String> groups) {
        return "Which of feed '" + feed + "''s source types go in this build's source type group? A build makes one "
               + "group, translated by one pipeline. Each source type is profiled in the table shown in the chat. "
               + "Those you leave out are parked for a later build." + (groups.isEmpty()
                ? ""
                : " Naming an existing group (" + String.join(", ", groups) + ") adds them to it, or, none "
                  + "ticked, changes it alone.");
    }

    // The registry's source type groups, by name, in the order its rows name them.
    private static List<String> registryGroups(final Map<String, Object> registry) {
        return List.copyOf(JsonValues.maps(registry.get("rows")).stream()
                .map(row -> JsonValues.text(row.get("group")))
                .filter(group -> !group.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    // The registry's groups, said for a refusal or a hint: empty when it has none.
    private static String groupsSaid(final List<String> groups) {
        return groups.isEmpty()
                ? ""
                : "; the feed's groups are " + ToolText.list(groups) + ", and naming one adds the source types to it";
    }

    // The group's name: an existing group's own where it names one (any case), else as given.
    private static String groupNamed(final List<String> groups, final String name) {
        if (NullSafe.isBlankString(name)) {
            throw new ToolRefusal("The source type group has no name: ask the user what to call it, and call " + NAME
                                  + " again with " + GROUP_ARG + ". Nothing was written.");
        }
        return groups.stream().filter(group -> group.equalsIgnoreCase(name.strip())).findFirst()
                .orElse(name.strip());
    }

    // The registry's source types a group translates already, in order: none for a new group.
    private static List<String> groupRows(final Map<String, Object> registry, final String group) {
        return JsonValues.maps(registry.get("rows")).stream()
                .filter(row -> group.equals(JsonValues.text(row.get("group"))) && translated(row))
                .map(row -> JsonValues.text(row.get("name")))
                .toList();
    }

    // A row of the group placed in a pipeline: the group exists, and the source types added to it join it there.
    private static Optional<Map<String, Object>> placedRow(final Map<String, Object> registry, final String group) {
        return JsonValues.maps(registry.get("rows")).stream()
                .filter(row -> group.equals(JsonValues.text(row.get("group"))) && placed(row))
                .findFirst();
    }

    // The rows offered for the scope: those not yet translated or dropped.
    private static List<Map<String, Object>> offered(final Map<String, Object> registry) {
        return JsonValues.maps(registry.get("rows")).stream()
                .filter(row -> !SourceTypeRegistry.TRANSLATED.equals(row.get("status"))
                               && !SourceTypeRegistry.DROPPED.equals(row.get("status")))
                .toList();
    }

    // The scope's statuses: every source type offered is parked (none is translated until its source type group
    // handles it); those in the scope say so, the rest carry the user's reason.
    private static void applyScope(final Map<String, Object> registry,
                                   final String build,
                                   final Scope scope,
                                   final String now) {
        final Set<String> chosen = new LinkedHashSet<>(scope.chosen());
        for (final Map<String, Object> row : offered(registry)) {
            final String name = JsonValues.text(row.get("name"));
            decide(row, SourceTypeRegistry.PARKED, "user", now, chosen.contains(name)
                    ? "in the scope of build '" + build + "': translated once its source type group '"
                      + scope.group() + "' is built"
                    : scope.reason().isBlank()
                            ? "left for a later build"
                            : scope.reason());
        }
    }

    // Source types never to be translated, on the user's word only, with their reason.
    private void applyDropped(final ToolCall call, final Map<String, Object> registry, final String now) {
        final List<String> dropped = stringListArg(call, DROPPED_ARG);
        if (dropped.isEmpty()) {
            return;
        }
        final String reason = NullSafe.string(stringArg(call, DROP_REASON_ARG)).strip();
        if (reason.isEmpty()) {
            throw new ToolRefusal("Give " + DROP_REASON_ARG + ": why, in the user's words, " + dropped + " are never "
                                  + "to be translated. Nothing was written.");
        }
        final List<Map<String, Object>> rows = JsonValues.maps(registry.get("rows"));
        for (final String name : dropped) {
            final Map<String, Object> row = rows.stream()
                    .filter(found -> JsonValues.text(found.get("name")).equalsIgnoreCase(name.strip()))
                    .findFirst()
                    .orElseThrow(() -> new ToolRefusal("'" + name + "' is no source type of the registry. Nothing "
                                                       + "was written."));
            decide(row, SourceTypeRegistry.DROPPED, "user", now, reason);
        }
    }

    private static void decide(final Map<String, Object> row,
                               final String status,
                               final String by,
                               final String at,
                               final String reason) {
        row.put("status", status);
        row.put("decided", Entries.of("by", by, "at", at, "reason", reason));
    }

    private static Map<String, String> statuses(final Map<String, Object> before) {
        final Map<String, String> out = new LinkedHashMap<>();
        JsonValues.maps(before.get("rows")).forEach(row -> out.put(JsonValues.text(row.get("name")),
                JsonValues.text(row.get("status"))));
        return out;
    }

    // The profiles as a table for the chat: a row a source type, sorted by suggested category then size.
    private static String table(final String feed,
                                final String field,
                                final List<SourceTypeRows.Row> rows,
                                final Map<String, Map<String, Object>> profiles,
                                final Map<String, String> statuses) {
        final List<SourceTypeRows.Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing((SourceTypeRows.Row row) -> JsonValues.text(profiles.getOrDefault(row.name(),
                        Map.of()).getOrDefault("suggested_category", "~")))
                .thenComparing(row -> -number(profiles.getOrDefault(row.name(), Map.of()).get("records_read"))));
        final List<String> lines = new ArrayList<>(List.of("## Source types of '" + feed + "' (by " + field + ")", "",
                "| Source type | Match | Records | Formats | Top kinds | Signals | Suggested | Status |",
                "| --- | --- | --- | --- | --- | --- | --- | --- |"));
        for (final SourceTypeRows.Row row : sorted) {
            final Map<String, Object> profile = profiles.getOrDefault(row.name(), Map.of());
            final long read = number(profile.get("records_read"));
            final List<String> kinds = JsonValues.maps(profile.get("top_kinds")).stream()
                    .limit(3)
                    .map(kind -> JsonValues.text(kind.get("kind")) + " " + JsonValues.text(kind.get("records")))
                    .toList();
            final List<String> signals = JsonValues.maps(profile.get("signals")).stream()
                    .map(signal -> JsonValues.text(signal.get("signal")) + " " + JsonValues.text(signal.get(
                            "share_percent")) + "%")
                    .toList();
            lines.add("| " + cell(row.name()) + " | " + cell(row.said()) + " | " + cell(read == 0
                    ? JsonValues.text(profile.getOrDefault("rests_on", "not seen"))
                    : "about " + JsonValues.text(profile.get("share_percent")) + "% (" + read + " read"
                      + (read < SourceTypeProfiles.FEW_RECORDS
                    ? "; from only " + read + " records"
                    : "") + ")") + " | " + cell(String.join(", ", JsonValues.mapOrEmpty(profile.get("formats"))
                    .keySet())) + " | " + cell(String.join(", ", kinds)) + " | " + cell(String.join(", ", signals))
                      + " | " + cell(profile.get("suggested_category") == null
                    ? "-"
                    : profile.get("suggested_category") + " (" + profile.get("confidence") + ")") + " | "
                      + cell(statuses.getOrDefault(row.name(), "-")) + " |");
        }
        return String.join("\n", lines);
    }

    private static Map<String, Object> written(final String feed,
                                               final String field,
                                               final Map<String, Object> registry,
                                               final SurveyRecord written,
                                               final String table) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", feed);
        result.put("field", field);
        result.put("registry", Entries.of("type", "Documentation", "uuid", written.uuid(), "name",
                written.name()));
        result.put("rows", JsonValues.maps(registry.get("rows")).stream()
                .map(row -> Entries.of("name", row.get("name"), "match", ("pattern".equals(row.get("match"))
                        ? "~ "
                        : "= ") + row.get("value"), "status", row.get("status"), "reason",
                        JsonValues.mapOrEmpty(row.get("decided")).get("reason"), "records_read",
                        JsonValues.mapOrEmpty(row.get("profile")).get("records_read"), "share_percent",
                        JsonValues.mapOrEmpty(row.get("profile")).get("share_percent"), "suggested_category",
                        JsonValues.mapOrEmpty(row.get("profile")).get("suggested_category"), "confidence",
                        JsonValues.mapOrEmpty(row.get("profile")).get("confidence")))
                .toList());
        result.put("table", table);
        return result;
    }

    private static String cell(final Object value) {
        return JsonValues.text(value).replace("|", "\\|").replace("\n", " ");
    }

    private static Object firstSet(final Object... values) {
        for (final Object value : values) {
            if (value != null && !JsonValues.text(value).isBlank()) {
                return JsonValues.text(value);
            }
        }
        return null;
    }

    private static long number(final Object value) {
        return value instanceof final Number n
                ? n.longValue()
                : 0L;
    }


    // --------------------------------------------------------------------------------


    /// The rows, or a result to return at once (the user is to be asked in the chat).
    ///
    /// @param rows  The rows, in order.
    /// @param early The result, or null.
    private record Rows(List<SourceTypeRows.Row> rows, Map<String, Object> early) {

    }

    /// The rows as they will be, and what of the registry's translated or placed rows the user is to confirm.
    ///
    /// @param rows          The rows, in order.
    /// @param confirmations Each change or removal to confirm, said for the user.
    private record Edits(List<SourceTypeRows.Row> rows, List<String> confirmations) {

    }

    /// The scope the user chose.
    ///
    /// @param chosen The source types of the build's source type group.
    /// @param reason Why the others wait, in their words.
    /// @param group  The group's name: an existing group's adds the source types to it.
    private record Scope(List<String> chosen, String reason, String group) {

    }

    // Maps in the order given, nulls kept (Map.of refuses them).
    private static final class Entries {

        private Entries() {
        }

        private static Map<String, Object> of(final Object... keysAndValues) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < keysAndValues.length; i += 2) {
                map.put((String) keysAndValues[i], keysAndValues[i + 1]);
            }
            return map;
        }
    }
}
