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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// `record_source_types` - the plan's source types step for an aggregated feed: each source type the build's survey
/// counted is profiled (by the registry's rows, so a pattern's source type is one), the profiles are shown to the
/// user as a table, the user chooses in a form which source types the build covers (its scope), and the feed's
/// source type registry (`<feed> source types`) is written in the build, through [WriteGuard]. An assessment writes
/// the registry without a scope. Rows are a value each, or patterns where the values are many structured ones,
/// confirmed by the user; a registry beside the feed is carried on from (a working copy), its rows and statuses kept
/// and values it lacks added as `new`.
///
/// The scope is recorded in the registry and in the survey state, with the rows it was chosen by
/// ([SourceTypeScope]), and the build's tools read the feed through it (the survey, samples, classification, the
/// draft, the sample checks and review_coverage). The source types in it stay `parked` until a source type group
/// translates them. A scope that adds source types the survey's streams weren't read for starts its coverage again.
class RecordSourceTypesTool extends AbstractBuiltInTool {

    static final String NAME = "record_source_types";
    static final String BUILD_ARG = "build";
    static final String FEED_ARG = "feed";
    static final String REVIEWED_ARG = "reviewed";
    static final String ROWS_ARG = "rows";
    static final String PATTERNS_ARG = "patterns";
    static final String SCOPE_ARG = "scope";
    static final String REASON_ARG = "reason";
    static final String DROPPED_ARG = "dropped";
    static final String DROP_REASON_ARG = "drop_reason";
    static final String SYSTEM_NAME_ARG = "system_name";
    static final String SYSTEM_NAMES_ARG = "system_names";
    static final String ENVIRONMENT_ARG = "environment";
    // The answers to the proposed patterns.
    static final String USE_PATTERNS = "Use these patterns";
    static final String ONE_PER_VALUE = "One source type per value";
    // Who decided a status that no one chose: an assessment registers every source type as parked.
    private static final String ASSESSED = "assessment";
    private static final String NEEDS_REVIEW = "needs_review";
    private static final String NEEDS_USER = "needs_user";

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
                             + "again with reviewed=<its review_id>, the user chooses in a form which source types "
                             + "to build now; the others are parked with their reason. Then it writes the feed's "
                             + "registry ('<feed> source types') in the build. An assessment build writes the "
                             + "registry without a scope. Where the field's values are many paths or names, it "
                             + "proposes anchored patterns, confirmed by the user.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, "The build.")
                        .addStringProperty(FEED_ARG, "The surveyed feed.")
                        .addStringProperty(REVIEWED_ARG, "The review_id of the needs_review reply, once its table "
                                                         + "has been shown to the user.")
                        .addProperty(ROWS_ARG, stringArraySchema("Only as the user says: the source types in the "
                                                                 + "order tried, each 'name = value' (exactly) or "
                                                                 + "'name ~ ^pattern' (an anchored regular "
                                                                 + "expression on the field's value), e.g. 'mail "
                                                                 + "audit ~ ^/var/log/mail/audit/'. Narrower before "
                                                                 + "wider. Without it, rows are proposed from the "
                                                                 + "values the survey saw."))
                        .addStringProperty(PATTERNS_ARG, "Only when the client shows no forms: the user's answer "
                                                         + "to the proposed patterns ('use' them, or 'exact': a "
                                                         + "source type per value).")
                        .addProperty(SCOPE_ARG, stringArraySchema("Only when the client shows no forms: the "
                                                                  + "source types the user chose to build now, by "
                                                                  + "name."))
                        .addStringProperty(REASON_ARG, "Why the other source types are left for later, in the "
                                                       + "user's words (no forms).")
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
        final Rows rows = rows(call, context, feed, field, values, before);
        if (rows.early() != null) {
            return rows.early();
        }
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
            final SurveyRecord written = registries.writeRegistry(context.session(), build, feed, existing,
                    SourceTypeRegistry.render(registry));
            final Map<String, Object> result = written(feed, field, registry, written, table);
            result.put("hint", "The registry is written: show the user the table (as it is), and offer to onboard "
                               + "the source types they pick, in a build of its own (start_onboarding on the feed: "
                               + "it starts at the scope choice). Then promote_build, the survey and registry going "
                               + "beside the feed.");
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
            result.put(ROWS_ARG, rows.rows().stream().map(row -> row.name() + (SourceTypeRows.PATTERN.equals(
                    row.match())
                    ? " ~ "
                    : " = ") + row.value()).toList());
            result.put("hint", (context.session().isChat()
                    ? "The chat shows the user this table itself, as it is: don't repeat or restyle it. "
                    : "Show the user this table in your reply, as it is, sorted by suggested category then size, "
                      + "with what each suggestion rests on. ")
                               + "Then call " + NAME + " again with the same arguments, rows=<these rows> and "
                               + "reviewed=<review_id>: "
                               + "the user chooses in a form which source types to build now. Suggest; don't "
                               + "choose for them.");
            return result;
        }
        final Optional<Scope> scope = scope(call, context, feed, registry, profiles);
        if (scope.isEmpty()) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", NEEDS_USER);
            result.put("feed", feed);
            result.put("offered", offered(registry).stream().map(row -> JsonValues.text(row.get("name"))).toList());
            result.put("hint", "Ask the user in the chat which of the offered source types to build now (the table "
                               + "shown), and why the others are left for later; then call " + NAME + " again with "
                               + "reviewed=<review_id>, " + SCOPE_ARG + "=[their names] and " + REASON_ARG + ".");
            return result;
        }
        final String now = SurveyDoc.at(Instant.now());
        applyScope(registry, build, scope.get(), now);
        applyDropped(call, registry, now);
        registry.put("scope", Entries.of("build", build, "source_types", scope.get().chosen(), "by", "user",
                "at", now));
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
            restarted = restartsCoverage(survey, new SourceTypeScope(build, field, rows.rows(), scope.get().chosen()));
            surveyDocuments.write(context.session(), surveyDoc.get(), SurveyDoc.render(survey));
        }
        final Map<String, Object> result = written(feed, field, registry, written, table);
        result.put("scope", scope.get().chosen());
        if (restarted) {
            result.put("coverage_restarted", "The scope adds source types some streams the survey read weren't read "
                                             + "for (a narrower scope read them): its coverage starts again, and "
                                             + "survey_feed build='" + build + "' feed='" + feed + "' reads them "
                                             + "again for those source types.");
        }
        result.put("hint", "The registry is written and the build's scope recorded: " + scope.get().chosen()
                           + ". Carry on with the plan (next).");
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
            return new Rows(given.stream().map(RecordSourceTypesTool::parseRow).toList(), null);
        }
        if (!before.isEmpty()) {
            final List<SourceTypeRows.Row> out = new ArrayList<>(SourceTypeRegistry.rows(before));
            values.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .filter(entry -> SourceTypeRows.first(out, entry.getKey()).isEmpty())
                    .forEach(entry -> out.add(new SourceTypeRows.Row(entry.getKey(), SourceTypeRows.VALUE,
                            entry.getKey())));
            return new Rows(out, null);
        }
        if (values.isEmpty()) {
            throw new ToolRefusal("The survey of feed '" + feed + "' has counted no records by '" + field + "' yet: "
                                  + "survey_feed with the build first.");
        }
        final SourceTypeRows.Proposal proposal = SourceTypeRows.propose(values);
        final List<SourceTypeRows.Row> proposed = proposal.rows().stream().map(SourceTypeRows.Proposed::row)
                .toList();
        if (!proposal.patterns()) {
            return new Rows(proposed, null);
        }
        final String given2 = stringArg(call, PATTERNS_ARG);
        final Optional<String> answer = given2 != null
                ? Optional.of(given2)
                : consent.answer(call, context, NAME, patternsIntro(feed, field, proposal),
                        "Use these patterns, or one source type per value?", List.of(USE_PATTERNS, ONE_PER_VALUE));
        if (answer.isEmpty()) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", NEEDS_USER);
            result.put("feed", feed);
            result.put("proposed_rows", proposal.rows().stream()
                    .map(row -> Entries.of("name", row.row().name(), "match", row.row().said(), "values",
                            row.values(), "records", row.records()))
                    .toList());
            result.put("hint", "The values of '" + field + "' are many paths or names: these patterns are proposed, "
                               + "each covering the values shown, tried in order (narrower first). Ask the user in "
                               + "the chat whether to use them, or have a source type per value, then call " + NAME
                               + " again with " + PATTERNS_ARG + "='use' or 'exact' (or " + ROWS_ARG
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
        if (said.startsWith("use") || said.startsWith("yes") || said.contains("pattern")) {
            return new Rows(proposed, null);
        }
        throw new ToolRefusal("The user's answer ('" + answer.get().strip() + "') is neither: ask them in the chat "
                              + "how to match the source types, and call " + NAME + " again with " + ROWS_ARG
                              + " as they say ('name = value' or 'name ~ ^pattern', in order).");
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

    // A row as the user gave it: `name = value` or `name ~ ^pattern`.
    private static SourceTypeRows.Row parseRow(final String given) {
        final int tilde = given.indexOf(" ~ ");
        final int equals = given.indexOf(" = ");
        if (tilde > 0 && (equals < 0 || tilde < equals)) {
            return new SourceTypeRows.Row(given.substring(0, tilde).strip(), SourceTypeRows.PATTERN,
                    given.substring(tilde + 3).strip());
        }
        if (equals > 0) {
            return new SourceTypeRows.Row(given.substring(0, equals).strip(), SourceTypeRows.VALUE,
                    given.substring(equals + 3).strip());
        }
        throw new ToolRefusal("Row '" + given + "' is neither 'name = value' nor 'name ~ ^pattern'.");
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
        final List<Map<String, Object>> out = new ArrayList<>();
        final boolean first = before.isEmpty();
        final String now = SurveyDoc.at(Instant.now());
        for (final SourceTypeRows.Row row : rows) {
            final Map<String, Object> was = previous.getOrDefault(row.name(), Map.of());
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", row.name());
            entry.put("match", row.match());
            entry.put("value", row.value());
            entry.put("system_name", firstSet(systemNames.get(row.name()), was.get("system_name"), null));
            entry.put("status", was.get("status"));
            entry.put("decided", was.get("decided"));
            if (was.isEmpty() && !first) {
                // Seen since the registry was made: for the user to place.
                decide(entry, SourceTypeRegistry.NEW, "survey", now, "seen after the registry was made");
            }
            entry.put("group", was.get("group"));
            // Where its group's XSLT is, the pipeline that runs it, and what it looks up: the feed's dispatchers are
            // generated from them.
            if (was.get("group_xslt") != null) {
                entry.put("group_xslt", was.get("group_xslt"));
            }
            entry.put("pipeline", was.get("pipeline"));
            entry.put("output_feed", was.get("output_feed"));
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

    // The scope the user chooses in a form (a yes for each source type offered: parked, new, or every one of a
    // first registry), with why the others wait; or as they said in the chat (scope=, reason=).
    private Optional<Scope> scope(final ToolCall call,
                                  final ToolCallContext context,
                                  final String feed,
                                  final Map<String, Object> registry,
                                  final Map<String, Map<String, Object>> profiles) {
        final List<Map<String, Object>> offered = offered(registry);
        final List<String> names = offered.stream().map(row -> JsonValues.text(row.get("name"))).toList();
        final List<String> given = stringListArg(call, SCOPE_ARG);
        if (!given.isEmpty()) {
            final List<String> chosen = new ArrayList<>();
            for (final String name : given) {
                chosen.add(names.stream().filter(offer -> offer.equalsIgnoreCase(name.strip())).findFirst()
                        .orElseThrow(() -> new ToolRefusal("'" + name + "' is none of the source types offered: "
                                                           + names)));
            }
            return Optional.of(new Scope(chosen, NullSafe.string(stringArg(call, REASON_ARG)).strip()));
        }
        final Map<String, Consent.Editable> items = new LinkedHashMap<>();
        for (final String name : names) {
            final Map<String, Object> profile = profiles.getOrDefault(name, Map.of());
            items.put(name, new Consent.Editable("Build " + name + " now", "about "
                                                                          + JsonValues.text(profile.getOrDefault(
                    "share_percent", 0)) + "% of the records read; " + String.join(", ",
                    JsonValues.mapOrEmpty(profile.get("formats")).keySet())));
        }
        final Optional<Map<String, Object>> answers = consent.review(call, context, NAME, "Which of feed '" + feed
                                                                                          + "''s source types does "
                                                                                          + "this build cover? Each "
                                                                                          + "is profiled in the "
                                                                                          + "table shown in the "
                                                                                          + "chat. Those you leave "
                                                                                          + "out are parked for a "
                                                                                          + "later build.", items,
                "Why are the others left for later?");
        return answers.map(found -> new Scope(names.stream().filter(name -> Boolean.TRUE.equals(found.get(name)))
                .toList(), NullSafe.string(NullSafe.get(found.get(Consent.REVIEW_TEXT), String::valueOf)).strip()));
    }

    // The rows offered for the scope: those not yet translated or dropped.
    private static List<Map<String, Object>> offered(final Map<String, Object> registry) {
        return JsonValues.maps(registry.get("rows")).stream()
                .filter(row -> !SourceTypeRegistry.TRANSLATED.equals(row.get("status"))
                               && !SourceTypeRegistry.DROPPED.equals(row.get("status")))
                .toList();
    }

    // The scope's statuses: every source type offered is parked (none is translated until a source type group
    // handles it); those in the scope say so, the rest carry the user's reason.
    private static void applyScope(final Map<String, Object> registry,
                                   final String build,
                                   final Scope scope,
                                   final String now) {
        final Set<String> chosen = new LinkedHashSet<>(scope.chosen());
        for (final Map<String, Object> row : offered(registry)) {
            final String name = JsonValues.text(row.get("name"));
            decide(row, SourceTypeRegistry.PARKED, "user", now, chosen.contains(name)
                    ? "in the scope of build '" + build + "': translated once its source type group is built"
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

    /// The scope the user chose.
    ///
    /// @param chosen The source types to build now.
    /// @param reason Why the others wait, in their words.
    private record Scope(List<String> chosen, String reason) {

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
