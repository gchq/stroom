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
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueCheck;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.CatalogueDiff;
import stroom.ai.impl.tools.catalogue.CatalogueSample;
import stroom.ai.impl.tools.catalogue.CatalogueSeed;
import stroom.ai.impl.tools.catalogue.CatalogueValidation;
import stroom.ai.impl.tools.catalogue.EventMeaning;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.KindFields;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/// `catalogue_event_types` (replacing classify_kinds; stroom-ai/DESIGN.md, Event type catalogue): the source's
/// catalogue of event types, in its build's source notes. The agent proposes the event types; the tool gives it the
/// evidence, checks every proposal mechanically against every sampled record of the build's scope, and puts them to
/// the user to confirm.
///
/// * **No proposals**: the evidence ([CatalogueEvidence]: the fields that tell records apart, the records' shapes,
///   examples, the draft's seed) and the catalogue so far, with its check.
/// * **proposals**: `add`, `change` or `remove` event types by TypeId ([CatalogueProposals]), then the whole
///   catalogue checked ([CatalogueCheck]) against the scope's records as processing reads them: compact (counts and
///   problems), the full listing saved to the workspace. Problems the proposals bring are refused (nothing
///   recorded); overlaps and event types that may be several are recorded, and refuse confirmation until fixed.
/// * **confirm**: the next set put to the user, its table shown in the chat first (`needs_showing`), then a short
///   form ("Accept all N event types, or type your amendments"): the high-confidence event types in one form, the
///   medium and the low in sets of up to ten, then the catch-all for uncatalogued records. A confirmation, given only
///   in its form (decided 2026-11-18): amendments come back for the agent to propose (`op: change`), and the set is
///   put to the user again. A client that can't show forms confirms through `needs_review` and its `review_id`.
///
/// The survey comes first (it alone finds an aggregated feed's source type field), as for classify_kinds.
class CatalogueEventTypesTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CatalogueEventTypesTool.class);

    /// The tool's name.
    static final String NAME = "catalogue_event_types";
    /// A result's status: the evidence listed.
    static final String LISTED = "listed";
    /// A result's status: proposals checked.
    static final String CHECKED = "checked";
    /// A result's status: a set waits for the user (amended, or its table to show first).
    static final String NEEDS_CONFIRMATION = "needs_confirmation";
    /// A result's status: a set's table for the chat to show as it is, before its form.
    static final String NEEDS_SHOWING = "needs_showing";
    /// A result's status: a set for a client without forms to show, then confirm with its review id.
    static final String NEEDS_REVIEW = "needs_review";
    /// A result's status: every event type and the catch-all confirmed.
    static final String CONFIRMED = "confirmed";
    /// The most medium- or low-confidence event types put to the user at once.
    static final int SET_SIZE = 10;

    private static final String BUILD_ARG = "build";
    private static final String SOURCE_ARG = "source";
    private static final String STREAM_IDS_ARG = "stream_ids";
    private static final String SPLITTER_ARG = "splitter";
    private static final String PROPOSALS_ARG = "proposals";
    private static final String CONFIRM_ARG = "confirm";
    private static final String CATCH_ALL_ARG = "catch_all";
    private static final String REVIEWED_ARG = "reviewed";
    private static final String AMENDMENTS = "amendments";
    private static final String STATUS = "status";
    private static final String HINT = "hint";
    private static final String TABLE = "table";
    private static final String HIGH = "high";
    private static final List<String> CONFIDENCES = List.of(HIGH, "medium", "low");
    // The confirmation in progress, in the notes block: the set last shown (its digest, when, and the call that
    // asked its form), a set the user amended (its digest and their words), and the streams the catalogue is of.
    private static final String SHOWN = "shown";
    private static final String AT = "at";
    private static final String ASKED_BY = "asked_by";
    private static final String DIGEST = "digest";
    private static final String AMENDED = "amended";
    private static final String STREAMS = "streams";
    // How long a shown set stays in view for its form: past it, the set is shown again before the form.
    private static final long SHOWN_FRESH_MS = 90_000;
    private static final int MAX_SAMPLE_STREAMS = 5;
    // The most of the build's own sample streams named in the refusal to catalogue before a survey.
    private static final int MOST_SURVEY_STREAMS = 20;
    private static final String LISTING_FOLDER = "/outputs/catalogue/";
    private static final Pattern PLAIN = Pattern.compile("[^A-Za-z0-9._-]");

    private final RecordSourceNotesTool notes;
    private final SampleStreamTexts sampleStreamTexts;
    private final Consent consent;
    private final BuildPlan plan;
    private final TranslationBuilds builds;
    private final LongSupplier clock;
    private Provider<CatalogueNotes> catalogueNotesProvider;

    @Inject
    CatalogueEventTypesTool(final RecordSourceNotesTool notes,
                            final SampleStreamTexts sampleStreamTexts,
                            final Consent consent,
                            final BuildPlan plan,
                            final TranslationBuilds builds) {
        this(notes, sampleStreamTexts, consent, plan, builds, System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    ///
    /// @param notes             The build's source notes.
    /// @param sampleStreamTexts Reads the sample streams.
    /// @param consent           Asks the user.
    /// @param plan              The build's plan.
    /// @param builds            The builds.
    /// @param clock             The time now, in milliseconds.
    CatalogueEventTypesTool(final RecordSourceNotesTool notes,
                            final SampleStreamTexts sampleStreamTexts,
                            final Consent consent,
                            final BuildPlan plan,
                            final TranslationBuilds builds,
                            final LongSupplier clock) {
        this.notes = notes;
        this.sampleStreamTexts = sampleStreamTexts;
        this.consent = consent;
        this.plan = plan;
        this.builds = builds;
        this.clock = clock;
    }

    /// Where the build's catalogue is kept across versions (set by injection; none in tests that make the tool
    /// themselves): a next version's listing and confirmation say what changed since the promoted catalogue.
    ///
    /// @param catalogueNotes The build's catalogue.
    @Inject
    void setCatalogueNotes(final Provider<CatalogueNotes> catalogueNotes) {
        this.catalogueNotesProvider = catalogueNotes;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It writes the build's source notes.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("Reads the sample streams (View Data) and writes the build's source notes doc as the user.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Catalogue the event types the source writes, with the user, before the translation "
                             + "is drafted (stroom://guide/mapping). Without proposals: the evidence (fields that "
                             + "tell records apart, record shapes, examples, the draft's seed) and the catalogue so "
                             + "far. With proposals: event types added, changed or removed by TypeId, each checked "
                             + "against every sampled record (counts, overlaps, uncatalogued records). Then "
                             + "confirm=true, again after each answer: the user confirms the event types in short "
                             + "forms, a set at a time, then the catch-all for uncatalogued records.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addStringProperty(SOURCE_ARG, "The source's name; the build's source notes' when it has "
                                                       + "them, else the build's name.")
                        .addProperty(STREAM_IDS_ARG, BuildTranslationXsltTool.streamIdsSchema(
                                "The sample streams; the build's when left out."))
                        .addProperty(SPLITTER_ARG, TranslationArgs.object("The Data Splitter spec the text is read "
                                                                          + "with; the build's saved one when left "
                                                                          + "out."))
                        .addProperty(PROPOSALS_ARG, ArraySchema.builder()
                                .description("Changes to the catalogue: {op: add|change|remove, type_id, ...the "
                                             + "event type's keys: title, description, event_detail, action, "
                                             + "success, actor, affected, affected_as, recognised_by [{when: "
                                             + "[conditions], names}], basis, confidence, one_type_because}. "
                                             + "change gives only the keys that change.")
                                .items(ObjectSchema.builder()
                                        .addStringProperty(CatalogueProposals.OP, "add, change or remove.")
                                        .addStringProperty(CatalogueProposals.TYPE_ID, "Verb-Object, e.g. "
                                                                                       + "Update-Password.")
                                        .required(CatalogueProposals.OP)
                                        .build())
                                .build())
                        .addBooleanProperty(CONFIRM_ARG, "True to put the next set to the user (its table shown "
                                                         + "in the chat first); again after each answer.")
                        .addProperty(CATCH_ALL_ARG, ObjectSchema.builder()
                                .description("With confirm: the catch-all's agreement, {allow_unknown: why the "
                                             + "uncatalogued records are kept as Unknown}, used at its turn.")
                                .addStringProperty("allow_unknown", "Why.")
                                .build())
                        .addStringProperty(REVIEWED_ARG, "With confirm, for a client without forms: the review_id "
                                                         + "of the needs_review reply, once the user has seen it "
                                                         + "and agreed.")
                        .required(BUILD_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(catalogue(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> catalogue(final ToolCall call, final ToolCallContext context) {
        final String build = stringArg(call, BUILD_ARG);
        if (build == null) {
            throw new ToolRefusal("Give build");
        }
        final Optional<String> unsurveyed = unsurveyed(build);
        final String givenSource = stringArg(call, SOURCE_ARG);
        final Map<String, Object> notesNow = notes.notesOf(context, build, givenSource);
        final String source = NullSafe.nonBlankStringElse(givenSource,
                NullSafe.nonBlankStringElse(ToolText.str(notesNow.getOrDefault("source", "")), build));
        final Calling calling = new Calling(call, context, build, source, notesNow, current(build, notesNow));
        final List<Map<?, ?>> proposals = RecordSourceNotesTool.objects(call, PROPOSALS_ARG);
        final Map<String, Object> result = !proposals.isEmpty()
                ? proposed(calling, proposals)
                : isTrue(argsMap(call).get(CONFIRM_ARG))
                        ? confirmed(calling)
                        : listed(calling);
        unsurveyed.ifPresent(why -> result.put("survey", why));
        sincePromoted(build).ifPresent(said -> result.put("since_promoted", said));
        return result;
    }

    // What changed in the build's catalogue since the promoted one it started from (a feed's next version), in
    // words: the event types added, changed and removed; empty for a first version, or when nothing changed.
    private Optional<List<String>> sincePromoted(final String build) {
        if (catalogueNotesProvider == null) {
            return Optional.empty();
        }
        try {
            return catalogueNotesProvider.get().changedSincePromoted(build)
                    .filter(diff -> !diff.none())
                    .map(CatalogueDiff.Diff::said);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("What changed since the promoted catalogue of {} wasn't read: {}",
                    build, e.getMessage()), e);
            return Optional.empty();
        }
    }

    // The catalogue so far, its scope the build's now; an empty one before the source is catalogued.
    private Catalogue current(final String build, final Map<String, Object> notesNow) {
        final Catalogue kept = SourceNotes.catalogue(notesNow);
        final Catalogue.Scope scope = builds.scope(build)
                .map(CatalogueEventTypesTool::scopeOf)
                .orElse(Catalogue.Scope.NONE);
        return kept == null
                ? new Catalogue(List.of(), Catalogue.CatchAll.PROPOSED, scope)
                : new Catalogue(kept.eventTypes(), kept.catchAll(), scope);
    }

    private static Catalogue.Scope scopeOf(final SourceTypeScope scope) {
        return new Catalogue.Scope(scope.field(), scope.names() == null
                ? List.of()
                : scope.names().stream().sorted(ToolText::compare).toList());
    }

    // A build catalogues only once survey_feed has run for it, whether it works from existing feeds or from samples
    // of its own: the survey finds an aggregated feed's source type field, which nothing else looks for (seen in chat
    // 176: the survey failed, and the feed's source types were never found; seen in evals 58 and 59: the agent went
    // straight on with its own uploads, and an aggregated feed was never recognised). Refused until then; once a
    // survey for the build was refused for nothing readable, it goes on, and says so (returned). Empty for a build
    // with a survey, or with no streams to survey.
    private Optional<String> unsurveyed(final String build) {
        if (!builds.surveyStatesInBuild(build).isEmpty()) {
            return Optional.empty();
        }
        final List<String> sources = plan.worksFrom(build);
        final List<Long> samples = sources.isEmpty()
                ? plan.rawSampleStreams(build, MOST_SURVEY_STREAMS)
                : List.of();
        if (sources.isEmpty() && samples.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, String> unreadable = plan.unreadableSurveys(build);
        if (unreadable.isEmpty()) {
            final List<String> feeds = sources.isEmpty()
                    ? plan.inputFeeds(build)
                    : sources;
            final String feed = feeds.isEmpty()
                    ? "<the feed>"
                    : feeds.getFirst();
            throw new ToolRefusal("Survey first: survey_feed feed=" + feed + " build=" + build + (samples.isEmpty()
                    ? ""
                    : " stream_ids=" + samples) + ". The survey reads the records' formats and any source type "
                                  + "field (an aggregated feed, whose source types are built a group at a time), "
                                  + "quickly on a small sample; " + NAME + " catalogues the event types of the "
                                  + "build's records.");
        }
        final List<String> said = new ArrayList<>();
        unreadable.forEach((feed, why) -> said.add("'" + feed + "': " + why));
        return Optional.of("survey_feed couldn't read the feed (" + String.join("; ", said) + "), so this "
                           + "catalogue comes from the sample streams alone, and source types (an aggregated feed's "
                           + "source type field) were not looked for. Tell the user, and survey again once the cause "
                           + "is fixed.");
    }


    // --------------------------------------------------------------------------------
    // The evidence


    private Map<String, Object> listed(final Calling calling) {
        final Records read = read(calling);
        final Catalogue none = new Catalogue(List.of(), Catalogue.CatchAll.PROPOSED, calling.catalogue().scope());
        final CatalogueCheckResult asIs = CatalogueCheck.check(none, read.sample(), null);
        final List<EventType> seed = seed(read, calling.catalogue().scope().sourceTypeField());
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, LISTED);
        read.said(result);
        result.put("evidence", CatalogueEvidence.of(read.evidenceRecords(), asIs, seed,
                calling.catalogue().scope().sourceTypeField()));
        if (!calling.catalogue().eventTypes().isEmpty()) {
            final CatalogueCheckResult check = check(calling, calling.catalogue(), read);
            result.put("check", check.toJson());
            saved(calling, calling.catalogue(), check).ifPresent(path -> result.put("listing", path));
        }
        result.put("catalogue", compact(calling.catalogue()));
        result.put(HINT, "Propose the source's event types (proposals=[{op: add, type_id, title, description, "
                         + "event_detail, action, actor, affected, affected_as, recognised_by, basis, "
                         + "confidence}]) from this evidence and what you know of the product: a type is what "
                         + "happened, recognised by conditions on the records' fields (stroom://guide/mapping). The "
                         + "seed is the draft's own rules, for you to correct: add only what you keep. "
                         + "A Data Splitter part is a line format, not an event type.");
        result.put("next", next(calling.build(), Map.of(PROPOSALS_ARG, "<the event types>"),
                "checks them against every record; then confirm=true puts them to the user"));
        return result;
    }


    // The seed: the draft's own rules as event types. Within a source type group the draft may tell its records
    // apart by the build's source type field, which no event type is recognised by (the group's module passes other
    // source types over already): then the seed is derived as the draft derives its rules, by the field naming what
    // happened, chosen among the records' other fields ([KindFields]), an event type a value.
    private static List<EventType> seed(final Records read, final String sourceTypeField) {
        final List<EventType> drafted = CatalogueSeed.fromDraft(read.draft());
        final boolean bySourceType = sourceTypeField != null && drafted.stream()
                .flatMap(type -> type.recognisedBy().stream())
                .flatMap(way -> way.when().stream())
                .anyMatch(c -> CatalogueValidation.onSourceTypeField(sourceTypeField, c.field()));
        if (!bySourceType) {
            return drafted;
        }
        final List<Map<String, Object>> records = new ArrayList<>();
        for (final Map<String, Object> record : read.evidenceRecords()) {
            final Map<String, Object> copy = new LinkedHashMap<>(record);
            copy.keySet().removeIf(field -> CatalogueValidation.onSourceTypeField(sourceTypeField, field));
            records.add(copy);
        }
        final List<String> fields = KindFields.choose(records).fields();
        if (fields.isEmpty()) {
            return List.of();
        }
        final String field = fields.getFirst();
        final Set<String> values = new LinkedHashSet<>();
        records.forEach(record -> {
            if (record.get(field) != null && !String.valueOf(record.get(field)).isBlank()) {
                values.add(String.valueOf(record.get(field)));
            }
        });
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final String value : values) {
            final Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("name", value);
            rule.put("when", List.of(Map.of("field", field, "equals", value)));
            rule.put("fields", List.of());
            rules.add(rule);
        }
        return CatalogueSeed.fromDraft(Map.of("mapping", Map.of("events", rules)));
    }

    // --------------------------------------------------------------------------------
    // The check


    private Map<String, Object> proposed(final Calling calling, final List<Map<?, ?>> proposals) {
        final Records read = read(calling);
        final CatalogueProposals.Applied proposed = CatalogueProposals.apply(calling.catalogue(), proposals);
        final CatalogueProposals.Applied applied = new CatalogueProposals.Applied(withLeaves(proposed.catalogue(),
                notes.schemaOrNull(calling.context(), calling.build())), proposed.refused());
        final CatalogueCheckResult check = check(calling, applied.catalogue(), read);
        // Problems the proposals bring are refused, not those the catalogue had already.
        final Set<String> before = new LinkedHashSet<>();
        if (!calling.catalogue().eventTypes().isEmpty()) {
            check(calling, calling.catalogue(), read).problems().forEach(problem -> before.add(said(problem)));
        }
        final List<String> brought = check.problems().stream()
                .map(CatalogueEventTypesTool::said)
                .filter(problem -> !before.contains(problem))
                .toList();
        final boolean changed = !applied.catalogue().equals(calling.catalogue());
        final boolean recorded = changed && brought.isEmpty();
        final Catalogue kept = recorded
                ? applied.catalogue()
                : calling.catalogue();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, CHECKED);
        result.put("recorded", recorded);
        if (recorded) {
            result.put("source_notes", notes.recordCatalogue(calling.context(), calling.build(), calling.source(),
                    kept, withStreams(confirming(calling.notesNow()), read.streams())));
        }
        if (!applied.refused().isEmpty()) {
            result.put("refused", applied.refused().stream().map(CatalogueProposals.Refused::toJson).toList());
        }
        result.put("check", check.toJson());
        final List<String> notInSample = CatalogueTables.notInSample(check);
        if (!notInSample.isEmpty()) {
            result.put("not_in_sample", notInSample.stream()
                    .map(typeId -> typeId + ": not in the sample (kept, and said so in the review)")
                    .toList());
        }
        final List<EventType> next = nextSet(kept);
        if (recorded && !check.refused() && !next.isEmpty()) {
            // The set the user confirms next, as the chat will show it.
            result.put(TABLE, CatalogueTables.setTable(next, check));
        }
        result.put("catalogue", compact(kept));
        saved(calling, applied.catalogue(), check).ifPresent(path -> result.put("listing", path));
        result.put(HINT, proposedHint(recorded, brought, applied, check));
        result.put("next", recorded && !check.refused()
                ? next(calling.build(), Map.of(CONFIRM_ARG, true), "puts the next set of event types to the user: "
                                                                   + "its table shown in the chat, then a short form")
                : next(calling.build(), Map.of(PROPOSALS_ARG, "<corrections>"), "checks the catalogue again"));
        return result;
    }

    private static String proposedHint(final boolean recorded,
                                       final List<String> brought,
                                       final CatalogueProposals.Applied applied,
                                       final CatalogueCheckResult check) {
        final List<String> said = new ArrayList<>();
        if (!brought.isEmpty()) {
            said.add("Nothing recorded: the proposals bring problems (check.problems): " + String.join("; ",
                    brought.subList(0, Math.min(5, brought.size()))) + ". Correct them and send the proposals "
                     + "again (a regex short and anchored; over long values, starts_with or contains).");
        } else if (!applied.refused().isEmpty()) {
            said.add(applied.refused().size() + " proposal(s) refused (refused says why)" + (recorded
                    ? "; the rest recorded"
                    : "") + ".");
        }
        if (!check.overlaps().isEmpty()) {
            said.add(check.overlaps().size() + " overlap(s): a record two event types recognise is refused. "
                     + "check.overlaps names the record, both TypeIds and the fields whose values tell their other "
                     + "records apart: tighten one with a condition on them (op: change).");
        }
        if (check.several().stream().anyMatch(CatalogueCheckResult.Several::blocking)) {
            said.add("An event type of the draft's seed may be several (check.several): split it into event types "
                     + "by the field named, or say why it is one (op: change, one_type_because).");
        }
        if (!check.notChecked().isEmpty()) {
            said.add("Some conditions can't be checked against the records here (check.not_checked): their records "
                     + "aren't counted; tell them by a field's value where you can.");
        }
        if (check.uncatalogued().records() > 0) {
            said.add(check.uncatalogued().records() + " record(s) no event type recognises (check.uncatalogued, by "
                     + "shape): the catch-all translates them as Unknown; propose event types for those that are "
                     + "events of their own.");
        }
        if (said.isEmpty() || recorded && !check.refused()) {
            said.add("When the catalogue is right, " + NAME + " confirm=true puts it to the user a set at a time "
                     + "(the table above is the first set).");
        }
        return String.join(" ", said);
    }

    // The catalogue with each event type's affected_as written out to the leaf the affected field's value goes in
    // (User becomes User/Id), as the translation writes it; as given where the schema doesn't say (or is unreadable).
    private static Catalogue withLeaves(final Catalogue catalogue, final EventSchema schema) {
        if (schema == null) {
            return catalogue;
        }
        final List<EventType> types = new ArrayList<>();
        for (final EventType type : catalogue.eventTypes()) {
            if (type.affectedAs() == null || type.affectedAs().isBlank() || type.eventDetail() == null) {
                types.add(type);
                continue;
            }
            final String leaf = EventMeaning.affected(schema, "event type " + type.typeId(), type.eventDetail(),
                    type.affectedAs()).path();
            types.add(leaf == null || leaf.equals(type.affectedAs())
                    ? type
                    : new EventType(type.typeId(), type.title(), type.description(), type.eventDetail(),
                            type.action(), type.success(), type.actor(), type.affected(), leaf,
                            type.recognisedBy(), type.drop(), type.basis(), type.confidence(),
                            type.oneTypeBecause(), type.status()));
        }
        return catalogue.withEventTypes(types);
    }

    // The catalogue's check over the scope's records.
    private CatalogueCheckResult check(final Calling calling, final Catalogue catalogue, final Records read) {
        final EventSchema schema = notes.schemaOrNull(calling.context(), calling.build());
        return CatalogueCheck.check(catalogue, read.sample(), schema);
    }

    // The full listing (the catalogue and its check, in full) saved to the session's workspace: its path, or empty
    // without a workspace.
    private Optional<String> saved(final Calling calling, final Catalogue catalogue, final CatalogueCheckResult check) {
        final Optional<SessionWorkspace> workspace = calling.context().workspace();
        if (workspace.isEmpty()) {
            return Optional.empty();
        }
        final String path = LISTING_FOLDER + PLAIN.matcher(calling.build()).replaceAll("_") + ".json";
        final Map<String, Object> listing = new LinkedHashMap<>();
        listing.put("build", calling.build());
        listing.put("catalogue", catalogue.toJson());
        listing.put("check", check.toFullJson());
        try {
            workspace.get().write(WorkspacePath.parse(path), JsonUtil.writeValueAsString(listing, true), null);
            return Optional.of(path);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The catalogue's listing wasn't saved for {}: {}", calling.build(),
                    e.getMessage()), e);
            return Optional.empty();
        }
    }


    // --------------------------------------------------------------------------------
    // Confirmation


    private Map<String, Object> confirmed(final Calling calling) {
        final Catalogue catalogue = calling.catalogue();
        final Map<String, Object> confirming = confirming(calling.notesNow());
        final Records read = read(calling);
        final CatalogueCheckResult check = check(calling, catalogue, read);
        if (check.refused()) {
            throw new ToolRefusal(notConfirmable(check));
        }
        final List<EventType> set = nextSet(catalogue);
        if (set.isEmpty() && catalogue.catchAll().confirmed()) {
            return done(calling, catalogue, check, List.of());
        }
        final boolean catchAll = set.isEmpty();
        final String shown = catchAll
                ? catchAllShown(check)
                : CatalogueTables.setTable(set, check);
        final String digest = Consent.reviewId(shown);
        if (JsonValues.map(confirming.get(AMENDED)) != null
            && digest.equals(JsonValues.map(confirming.get(AMENDED)).get(DIGEST))) {
            throw new ToolRefusal("The user amended this set in its form: \""
                                  + JsonValues.map(confirming.get(AMENDED)).get(AMENDMENTS) + "\". Send their "
                                  + "amendments first, as proposals (op: change, or add and remove), in one call; "
                                  + "the set is then put to them again. Ask them in the chat if an amendment isn't "
                                  + "clear.");
        }
        if (!calling.context().userQuestions().canAsk()) {
            return withoutForms(calling, catalogue, check, set, shown, confirming);
        }
        if (!freshlyShown(confirming, digest, calling.call().id())) {
            // Shown long ago, or another set: shown again, right before its form (seen in chat 169: a set shown, the
            // agent drafting for some forty calls, then the form asking about a table far up the chat).
            return presented(calling, catalogue, check, set, shown, confirming);
        }
        asked(calling, catalogue, confirming, digest);
        final Catalogue now;
        if (catchAll) {
            consent.require(calling.call(), calling.context(), catchAllRequest(check, catchAllReason(calling))
                    .boundTo(shown));
            now = catalogue.withCatchAllConfirmedByUser(catchAllReason(calling));
        } else {
            final String accept = "Accept all " + set.size() + " event type" + (set.size() == 1
                    ? ""
                    : "s");
            final Map<String, Object> details = new LinkedHashMap<>();
            details.put("event types", "the " + set.size() + " " + confidenceOf(set) + "-confidence ones in the "
                                       + "table shown in the chat, as they are");
            consent.require(calling.call(), calling.context(), Consent.Request.confirmation(NAME, accept
                            + ", or type your amendments", details)
                    .withEditable(AMENDMENTS, accept + ", or type your amendments", accept)
                    .boundTo(shown));
            final Consent.Change amended = consent.changes(calling.context()).get(AMENDMENTS);
            if (amended != null && NullSafe.isNonBlankString(amended.theirs())
                && !amended.theirs().strip().equalsIgnoreCase(accept)) {
                return amendedSet(calling, catalogue, set, digest, confirming, amended.theirs().strip());
            }
            now = catalogue.confirmedByUser(typeIds(set));
        }
        return afterConfirming(calling, now, check, catchAll
                ? List.of()
                : typeIds(set).stream().toList(), withoutMarks(confirming));
    }

    // The set confirmed recorded, and the next one presented; or, every one confirmed, the catalogue done.
    private Map<String, Object> afterConfirming(final Calling calling,
                                                final Catalogue now,
                                                final CatalogueCheckResult check,
                                                final List<String> confirmedNow,
                                                final Map<String, Object> confirming) {
        final List<EventType> next = nextSet(now);
        if (next.isEmpty() && now.catchAll().confirmed()) {
            notes.recordCatalogue(calling.context(), calling.build(), calling.source(), now, confirming);
            return done(calling, now, check, confirmedNow);
        }
        final String shown = next.isEmpty()
                ? catchAllShown(check)
                : CatalogueTables.setTable(next, check);
        final Map<String, Object> result;
        if (calling.context().userQuestions().canAsk()) {
            result = presented(calling, now, check, next, shown, confirming);
        } else {
            notes.recordCatalogue(calling.context(), calling.build(), calling.source(), now, confirming);
            result = review(calling, now, next, shown);
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(STATUS, result.remove(STATUS));
        if (!confirmedNow.isEmpty()) {
            out.put("confirmed_now", confirmedNow);
        }
        out.putAll(result);
        return out;
    }

    // Every event type and the catch-all confirmed: the catalogue done, the plan's next step.
    private Map<String, Object> done(final Calling calling,
                                     final Catalogue catalogue,
                                     final CatalogueCheckResult check,
                                     final List<String> confirmedNow) {
        final Map<String, Object> result = new LinkedHashMap<>();
        if (!confirmedNow.isEmpty()) {
            result.put("confirmed_now", confirmedNow);
        }
        result.put("catalogue", compact(catalogue));
        result.put("check", check.toJson());
        result.put(HINT, "Every event type and the catch-all are confirmed: the catalogue is in the build's source "
                         + "notes, which draft_translation_mapping (build=) follows, a rule a way of recognising "
                         + "each event type.");
        final Map<String, Object> withNext = plan.withNext(calling.context(), calling.build(), result);
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(STATUS, CONFIRMED);
        out.putAll(withNext);
        return out;
    }

    // The set's table for the chat to show, as it is, marked as shown now: the form follows on the next call.
    private Map<String, Object> presented(final Calling calling,
                                          final Catalogue catalogue,
                                          final CatalogueCheckResult check,
                                          final List<EventType> set,
                                          final String shown,
                                          final Map<String, Object> confirming) {
        final Map<String, Object> marks = new LinkedHashMap<>(confirming);
        final Map<String, Object> mark = new LinkedHashMap<>();
        mark.put(DIGEST, Consent.reviewId(shown));
        mark.put(AT, clock.getAsLong());
        marks.put(SHOWN, mark);
        notes.recordCatalogue(calling.context(), calling.build(), calling.source(), catalogue, marks);
        final Map<String, Object> result = new LinkedHashMap<>();
        // Shown to the user as it is (seen in testing: a set's table saved to the workspace, then retyped by the
        // agent, shortened and restyled): in Stroom AI's chat, by the chat itself; elsewhere, by the agent.
        result.put(STATUS, NEEDS_SHOWING);
        result.put(TABLE, shown);
        result.put("catalogue", compact(catalogue));
        final String what = set.isEmpty()
                ? "the catch-all for the uncatalogued records (the " + check.uncatalogued().records() + " records "
                  + "no event type recognises, kept as Unknown)"
                : "the " + set.size() + " " + confidenceOf(set) + "-confidence event type(s)";
        final String form = NAME + " build=" + calling.build() + " confirm=true: a short form asks them to accept "
                            + what + " or type their amendments.";
        result.put(HINT, calling.context().session() != null && calling.context().session().isChat()
                ? "The chat shows the user this table itself, as it is: don't repeat, restyle or describe it. Now "
                  + "call " + form
                : "Show the table to the user in your reply, as it is, whole (Markdown: don't shorten, restyle or "
                  + "summarise it), then call " + form);
        result.put("next", next(calling.build(), Map.of(CONFIRM_ARG, true), "asks the user in a short form"));
        return result;
    }

    // A client that can't show forms: the set shown in the chat by the agent, and confirmed by the call that passes
    // back its review id once the user has agreed.
    private Map<String, Object> withoutForms(final Calling calling,
                                             final Catalogue catalogue,
                                             final CatalogueCheckResult check,
                                             final List<EventType> set,
                                             final String shown,
                                             final Map<String, Object> confirming) {
        if (calling.context().session() != null && calling.context().session().isChat()) {
            // The user is away from the chat: never confirmed for them.
            throw new ToolRefusal(FormsRequired.cannotAsk(calling.context().session(), NAME, "the event types put "
                                                                                             + "to the user"));
        }
        final Object reviewed = argsMap(calling.call()).get(REVIEWED_ARG);
        if (!Consent.wasReviewed(reviewed, shown)) {
            final Map<String, Object> result = review(calling, catalogue, set, shown);
            result.put(HINT, result.get(HINT) + Consent.reviewNote(reviewed));
            return result;
        }
        final Catalogue now = set.isEmpty()
                ? catalogue.withCatchAllConfirmedByUser(catchAllReason(calling))
                : catalogue.confirmedByUser(typeIds(set));
        return afterConfirming(calling, now, check, set.isEmpty()
                ? List.of()
                : typeIds(set).stream().toList(), withoutMarks(confirming));
    }

    private Map<String, Object> review(final Calling calling,
                                       final Catalogue catalogue,
                                       final List<EventType> set,
                                       final String shown) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, NEEDS_REVIEW);
        result.put(TABLE, shown);
        result.put("review_id", Consent.reviewId(shown));
        result.put("catalogue", compact(catalogue));
        result.put(HINT, "Show the user this table in your reply, as it is, whole, and ask them to accept "
                         + (set.isEmpty()
                ? "the catch-all (the uncatalogued records kept as Unknown)"
                : "all " + set.size() + " event type(s)") + " or say what to change. Once they accept, call "
                         + NAME + " build=" + calling.build() + " confirm=true reviewed='" + Consent.reviewId(shown)
                         + "' (this table's review_id). Amendments they give go back as proposals (op: change), and "
                         + "the set is shown again.");
        result.put("next", next(calling.build(), Map.of(CONFIRM_ARG, true, REVIEWED_ARG, "<the review_id, once "
                                                                                          + "the user agrees>"),
                "confirms the set the user agreed to"));
        return result;
    }

    // The user's amendments, for the agent to propose: nothing confirmed of the set, which is put to them again once
    // the amendments are proposed.
    private Map<String, Object> amendedSet(final Calling calling,
                                           final Catalogue catalogue,
                                           final List<EventType> set,
                                           final String digest,
                                           final Map<String, Object> confirming,
                                           final String amendments) {
        final Map<String, Object> marks = withoutMarks(confirming);
        final Map<String, Object> amended = new LinkedHashMap<>();
        amended.put(DIGEST, digest);
        amended.put(AMENDMENTS, amendments);
        marks.put(AMENDED, amended);
        notes.recordCatalogue(calling.context(), calling.build(), calling.source(), catalogue, marks);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, NEEDS_CONFIRMATION);
        result.put(AMENDMENTS, amendments);
        result.put("amended", typeIds(set).stream().toList());
        result.put("catalogue", compact(catalogue));
        result.put(HINT, "The user amended the " + confidenceOf(set) + "-confidence event types in the form: \""
                         + amendments + "\". Nothing of the set is confirmed. Send their amendments as proposals "
                         + "(op: change; add or remove where they split or reject one; drop: true only where they "
                         + "said to drop one), in one call, then " + NAME + " confirm=true: the set is put to them "
                         + "again. Ask them in the chat if an amendment isn't clear.");
        result.put("next", next(calling.build(), Map.of(PROPOSALS_ARG, "<the user's amendments>"),
                "checks them; then confirm=true puts the set to the user again"));
        return result;
    }

    // The form follows a table shown lately (within SHOWN_FRESH_MS), or one this call asked already (the chat making
    // the call again with the answer, however long the user took).
    private boolean freshlyShown(final Map<String, Object> confirming, final String digest, final String callId) {
        final Map<String, Object> mark = JsonValues.map(confirming.get(SHOWN));
        if (mark == null || !digest.equals(mark.get(DIGEST))) {
            return false;
        }
        if (callId != null && callId.equals(mark.get(ASKED_BY))) {
            return true;
        }
        final Object at = mark.get(AT);
        return at instanceof final Number when && clock.getAsLong() - when.longValue() <= SHOWN_FRESH_MS;
    }

    // The shown set's mark with the call asking its form.
    private void asked(final Calling calling,
                       final Catalogue catalogue,
                       final Map<String, Object> confirming,
                       final String digest) {
        final Map<String, Object> mark = new LinkedHashMap<>(NullSafe.map(JsonValues.map(confirming.get(SHOWN))));
        if (calling.call().id() == null || calling.call().id().equals(mark.get(ASKED_BY))) {
            return;
        }
        mark.put(DIGEST, digest);
        mark.put(ASKED_BY, calling.call().id());
        final Map<String, Object> marks = new LinkedHashMap<>(confirming);
        marks.put(SHOWN, mark);
        notes.recordCatalogue(calling.context(), calling.build(), calling.source(), catalogue, marks);
    }

    // The catch-all's confirmation: the uncatalogued records translated as Unknown, their fields kept as Data.
    private static Consent.Request catchAllRequest(final CatalogueCheckResult check, final String reason) {
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("uncatalogued records", check.uncatalogued().records() + " in the sample, of "
                                            + check.uncatalogued().shapes().size() + " shape(s), in the chat");
        if (reason != null) {
            details.put("why", reason);
        }
        return Consent.Request.confirmation(NAME, "Keep the uncatalogued records (those no event type recognises) "
                                                  + "as Unknown events, their fields kept as Data", details);
    }

    // What the user is shown of the catch-all: the uncatalogued records' count and shapes.
    private static String catchAllShown(final CatalogueCheckResult check) {
        final List<String> lines = new ArrayList<>(List.of("**Uncatalogued records**: " + check.uncatalogued().records()
                                                            + " of the sample's records no event type recognises, "
                                                            + "translated as Unknown, their fields kept as Data."));
        final List<String> shapes = CatalogueTables.uncataloguedShapes(check);
        if (!shapes.isEmpty()) {
            lines.add("");
            shapes.forEach(shape -> lines.add("- " + shape));
        }
        return String.join("\n", lines);
    }

    private String catchAllReason(final Calling calling) {
        final Map<String, Object> given = JsonValues.map(argsMap(calling.call()).get(CATCH_ALL_ARG));
        final String reason = given == null
                ? null
                : NullSafe.trim(ToolText.str(given.get("allow_unknown")));
        return NullSafe.isBlankString(reason)
                ? calling.catalogue().catchAll().allowUnknown()
                : reason;
    }

    // Why the catalogue can't be put to the user as it is: overlaps, event types that may be several (the seed's),
    // problems.
    private static String notConfirmable(final CatalogueCheckResult check) {
        final List<String> said = new ArrayList<>();
        check.overlaps().stream().limit(5).forEach(overlap -> said.add("record " + overlap.record() + " is "
                                                                       + "recognised by " + String.join(" and ",
                overlap.typeIds()) + " (an overlap)"));
        check.several().stream()
                .filter(CatalogueCheckResult.Several::blocking)
                .forEach(several -> said.add(several.typeId() + " may be several event types" + (several.byField()
                                                                                                  == null
                        ? " (its records come in different shapes)"
                        : " (told apart by " + several.byField().field() + ")") + ": split it, or say why it is "
                                             + "one (op: change, one_type_because)"));
        check.problems().stream().limit(5).forEach(problem -> said.add(said(problem)));
        return "Not put to the user: the catalogue can't be confirmed as it is: " + String.join("; ", said) + ". "
               + "Correct it with proposals (op: change, add or remove), then confirm again.";
    }

    // The set put to the user next: the high-confidence event types not yet confirmed, all at once (the build is one
    // source type group); else up to ten of the medium, then of the low, in the catalogue's order.
    static List<EventType> nextSet(final Catalogue catalogue) {
        for (final String confidence : CONFIDENCES) {
            final List<EventType> waiting = catalogue.eventTypes().stream()
                    .filter(type -> !type.confirmed() && confidence.equals(type.confidence()))
                    .toList();
            if (!waiting.isEmpty()) {
                return HIGH.equals(confidence)
                        ? waiting
                        : waiting.subList(0, Math.min(SET_SIZE, waiting.size()));
            }
        }
        return catalogue.eventTypes().stream().filter(type -> !type.confirmed()).limit(SET_SIZE).toList();
    }


    // --------------------------------------------------------------------------------
    // Reading the records


    // The scope's records, read as processing will: the build's sample streams (or those given, or those the
    // catalogue was last checked against), only the scope's source types, with the build's saved Data Splitter
    // unless one is given.
    private Records read(final Calling calling) {
        List<Long> streams = StartOnboardingTool.streamIds(calling.call(), STREAM_IDS_ARG);
        if (streams.isEmpty()) {
            final List<Object> kept = JsonValues.listOrEmpty(confirming(calling.notesNow()).get(STREAMS));
            streams = kept.stream().map(id -> (long) Double.parseDouble(String.valueOf(id))).toList();
        }
        if (streams.isEmpty()) {
            streams = plan.rawSampleStreams(calling.build(), MAX_SAMPLE_STREAMS);
        }
        if (streams.isEmpty()) {
            // A build on an existing feed, with no samples of its own: the streams its survey read.
            final Set<Long> surveyed = new LinkedHashSet<>();
            builds.surveyStatesInBuild(calling.build()).forEach(state -> surveyed.addAll(SurveyDoc.streams(state)));
            streams = surveyed.stream().limit(MAX_SAMPLE_STREAMS).toList();
        }
        if (streams.isEmpty()) {
            throw new ToolRefusal("No sample streams: upload the samples first, or give stream_ids");
        }
        final SampleStreamTexts.Texts texts = sampleStreamTexts.read(streams);
        final Optional<SourceTypeScope> scope = builds.scope(calling.build());
        final SampleStreamTexts.Scoped scoped = scope.map(found -> sampleStreamTexts.scoped(texts, found))
                .orElse(null);
        final Map<String, String> samples = scoped == null
                ? texts.texts()
                : scoped.texts().texts();
        SplitterSpec splitter = BuildTranslationXsltTool.Request.splitter(rawArg(calling.call(), SPLITTER_ARG));
        String readWith = null;
        if (splitter == null) {
            // The build's saved Data Splitter, where it has one (seen in chat 163: called without splitter, the
            // records read with a spec inferred afresh each time).
            final Optional<Map.Entry<String, String>> saved = builds.dataSplitter(calling.build(), null);
            if (saved.isPresent()) {
                splitter = SplitterSpec.ofConverter(saved.get().getValue());
                readWith = saved.get().getKey();
            }
        }
        final List<Object> sampleTexts = List.copyOf(samples.values());
        final Map<String, Object> draft = DraftMapping.draft(sampleTexts, calling.source(), null, null, null, null,
                splitter);
        final Map<String, Object> mapping = NullSafe.map(JsonValues.map(draft.get("mapping")));
        final List<Map<String, Object>> raw = DraftMapping.records(samples, splitter).records();
        final String variantField = variantField(draft, raw);
        final CatalogueSample sample = CatalogueSample.read(reading(mapping), sampleTexts, splitter == null
                ? inferred(draft)
                : splitter, LocalCheck.JSON_PARSER_CUT, variantField);
        return new Records(streams, readWith, draft, sample, scoped == null
                ? null
                : scoped.said(), HeldKindRecords.unpacked(raw, mapping));
    }

    // The Data Splitter the draft inferred for the records (a data_splitter input given none), or null.
    @SuppressWarnings("unchecked")
    private static SplitterSpec inferred(final Map<String, Object> draft) {
        if (draft.get("splitter") instanceof final Map<?, ?> spec) {
            try {
                return SplitterSpec.fromMap((Map<String, ?>) spec);
            } catch (final IllegalArgumentException e) {
                LOGGER.debug(() -> LogUtil.message("The draft's Data Splitter can't be read back: {}",
                        e.getMessage()));
            }
        }
        return null;
    }

    // A mapping the conditions are evaluated by: how the draft reads a record (its input, held fields, nil values
    // and extractions), its rules aside.
    private static TranslationMapping reading(final Map<String, Object> mapping) {
        final List<Extraction> extractions = new ArrayList<>();
        for (final Map<String, Object> entry : JsonValues.maps(mapping.get("extract"))) {
            try {
                extractions.add(Extraction.fromJson(entry));
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("An extraction of the draft can't be read: {}", e.getMessage()));
            }
        }
        final String input = NullSafe.nonBlankStringElse(ToolText.str(mapping.getOrDefault("input", "")),
                "data_splitter");
        return CatalogueSample.reading(input, text(mapping.get("record")), text(mapping.get("xml_namespace")),
                JsonValues.strings(mapping.get("json_fields")), JsonValues.strings(mapping.get("xml_fields")),
                JsonValues.strings(mapping.get("nil_values")), extractions);
    }

    // The field a record's line format (its variant) is written in, when the records are in several; else none.
    @SuppressWarnings("unchecked")
    private static String variantField(final Map<String, Object> draft, final List<Map<String, Object>> records) {
        if (draft.get("splitter") instanceof final Map<?, ?> spec) {
            try {
                final SplitterSpec read = SplitterSpec.fromMap((Map<String, ?>) spec);
                return NullSafe.isEmptyCollection(read.parts())
                        ? null
                        : read.variantField();
            } catch (final IllegalArgumentException e) {
                // The build's saved converter, whose spec the draft can't read back: its variant field where the
                // records have it.
                return records.stream().anyMatch(record -> record.containsKey(SplitterSpec.VARIANT))
                        ? SplitterSpec.VARIANT
                        : null;
            }
        }
        return null;
    }


    // --------------------------------------------------------------------------------
    // Helpers


    // The catalogue as every result carries it: compact, each event type's TypeId, title and status, and the
    // catch-all's status.
    private static Map<String, Object> compact(final Catalogue catalogue) {
        final List<Map<String, Object>> types = new ArrayList<>();
        for (final EventType type : catalogue.eventTypes()) {
            final Map<String, Object> one = new LinkedHashMap<>();
            one.put("type_id", type.typeId());
            one.put("title", type.title());
            one.put(STATUS, type.status());
            types.add(one);
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("event_types", types);
        out.put(SourceNotes.CATCH_ALL, Map.of(STATUS, catalogue.catchAll().status()));
        return out;
    }

    // A next call of this tool.
    private static Map<String, Object> next(final String build, final Map<String, Object> more, final String then) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(BUILD_ARG, build);
        arguments.putAll(more);
        final Map<String, Object> call = new LinkedHashMap<>();
        call.put("tool", NAME);
        call.put("arguments", arguments);
        final Map<String, Object> next = new LinkedHashMap<>();
        next.put("step", BuildPlan.CATALOGUE);
        next.put("call", call);
        next.put("then", then);
        return next;
    }

    private static Map<String, Object> confirming(final Map<String, Object> notesNow) {
        return new LinkedHashMap<>(NullSafe.map(JsonValues.map(notesNow.get(RecordSourceNotesTool.CONFIRMING))));
    }

    // The confirmation's marks cleared (no set shown or amended), the streams kept.
    private static Map<String, Object> withoutMarks(final Map<String, Object> confirming) {
        final Map<String, Object> out = new LinkedHashMap<>(confirming);
        out.remove(SHOWN);
        out.remove(AMENDED);
        return out;
    }

    private static Map<String, Object> withStreams(final Map<String, Object> confirming, final List<Long> streams) {
        final Map<String, Object> out = new LinkedHashMap<>(confirming);
        out.put(STREAMS, streams);
        return out;
    }

    private static Set<String> typeIds(final List<EventType> set) {
        final Set<String> out = new LinkedHashSet<>();
        set.forEach(type -> out.add(type.typeId()));
        return out;
    }

    private static String confidenceOf(final List<EventType> set) {
        return set.isEmpty()
                ? ""
                : set.getFirst().confidence();
    }

    private static String said(final CatalogueValidation.Problem problem) {
        return problem.typeId() + (problem.way() >= 0
                ? " (way " + (problem.way() + 1) + ")"
                : "") + ": " + problem.problem();
    }

    private static boolean isTrue(final Object given) {
        return Boolean.TRUE.equals(given) || "true".equalsIgnoreCase(String.valueOf(given));
    }

    private static String text(final Object value) {
        return value == null || ToolText.str(value).isBlank()
                ? null
                : ToolText.str(value);
    }


    // --------------------------------------------------------------------------------


    /// One call, and what it works on.
    ///
    /// @param call      The call.
    /// @param context   Its context.
    /// @param build     The build.
    /// @param source    The source's name.
    /// @param notesNow  The build's source notes as they are.
    /// @param catalogue The catalogue so far, its scope the build's.
    private record Calling(ToolCall call,
                           ToolCallContext context,
                           String build,
                           String source,
                           Map<String, Object> notesNow,
                           Catalogue catalogue) {

    }


    // --------------------------------------------------------------------------------


    /// The scope's records, as read for a call.
    ///
    /// @param streams         The sample streams read.
    /// @param readWith        The name of the build's saved Data Splitter they were read with, or null.
    /// @param draft           The draft made from them (its reading and its rules, the seed's).
    /// @param sample          The records as processing reads them, for the check.
    /// @param scopeSaid       What the scope left out, or null without one.
    /// @param evidenceRecords The records as the draft reads them, the fields inside held values among them.
    private record Records(List<Long> streams,
                           String readWith,
                           Map<String, Object> draft,
                           CatalogueSample sample,
                           Map<String, Object> scopeSaid,
                           List<Map<String, Object>> evidenceRecords) {

        // What was read, said in a result.
        private void said(final Map<String, Object> result) {
            if (readWith != null) {
                result.put("read_with", "the build's Data Splitter '" + readWith + "'");
            }
            if (scopeSaid != null) {
                result.put("scope", scopeSaid);
            }
        }
    }
}
