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
import stroom.ai.impl.tools.LargeResults;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSchema.StringSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueCheck;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.CatalogueDiff;
import stroom.ai.impl.tools.catalogue.CatalogueSeed;
import stroom.ai.impl.tools.catalogue.CatalogueValidation;
import stroom.ai.impl.tools.catalogue.EventMeaning;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.catalogue.TimelineRoles;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.sample.KindFields;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
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
import java.util.Locale;
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
/// * **confirm**: the next set put to the user, shown in the chat first (`needs_showing`), then a short form
///   ("Accept all N event types, or type your amendments, or ask about any of them"): at most three sets, then the
///   catch-all for uncatalogued records (decided by the user 2026-11-20: never many forms). The high-confidence, the
///   medium, then the low event types, however many, each one set shown as a summary by what happened with those
///   worth a look row by row ([CatalogueSummary]: every medium and low one is, as far as the rows listed go, and
///   every one amended since the set was shown); every row in the source notes' "Event types" table, which the
///   summary and the form refer to. A confirmation, given only in its form (decided 2026-11-18): amendments come
///   back for the agent to propose (`op: change`), and the set is put to the user again. A client that can't show
///   forms confirms through `needs_review` and its `review_id`.
/// * **explain**: what the user asked about while confirming (decided by the user 2026-11-20: they may ask for
///   examples or clarification on any item presented): an event type by TypeId, or a group of the summary by its
///   action element, each its meaning, how it is recognised, how it is translated, its records and a few of them as
///   read. It changes nothing: the agent answers in the chat and puts the set to the user again. A message the user
///   types instead of answering the form is the agent's to read: a question is answered so, a change proposed, and
///   acceptance is only ever the form's.
///
/// The survey comes first: it alone finds an aggregated feed's source type field.
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
    /// A result's status: the next set leaves an element the schema requires empty, for the agent to place first.
    static final String NEEDS_PLACEMENTS = "needs_placements";
    /// A result's status: every event type and the catch-all confirmed.
    static final String CONFIRMED = "confirmed";
    /// A result's status: event types or groups explained, for the agent to answer the user's question with.
    static final String EXPLAINED = "explained";
    /// The most event types explained in full in one call; a group's members are listed, not explained.
    static final int MOST_EXPLAINED = 20;
    /// The most records given as examples of an event type explained.
    static final int EXAMPLES_GIVEN = 3;
    /// The confirmation's key for the reading a call gave (a source with no mapping yet), kept for the calls after
    /// it.
    static final String READING = "reading";

    private static final String BUILD_ARG = "build";
    private static final String SOURCE_ARG = "source";
    static final String STREAM_IDS_ARG = "stream_ids";
    private static final String SPLITTER_ARG = "splitter";
    static final String READING_ARG = "reading";
    private static final String PROPOSALS_ARG = "proposals";
    private static final String CONFIRM_ARG = "confirm";
    static final String CATCH_ALL_ARG = "catch_all";
    static final String TRANSLATION = "translation";
    private static final String REVIEWED_ARG = "reviewed";
    static final String EXPLAIN_ARG = "explain";
    private static final String ANSWERED_ARG = "answered";
    private static final String START_FROM_ARG = "start_from";
    /// The question put to the user when a pipeline already translates what the build is to.
    static final String START_QUESTION = "How should this build start?";
    /// The choice of starting from the existing pipeline.
    static final String START_EXISTING = "Start from the existing pipeline (recommended)";
    /// The choice of describing the existing pipeline in place (decided with the user 2026-11-25): its XSLT read
    /// into a mapping and catalogue kept with it, no new version.
    static final String START_IN_PLACE = "Describe it in place (no new version)";
    /// The choice of drafting a new translation.
    static final String START_DRAFT = "Draft a new translation";
    /// The question put to the user when a translation described in place serves several feeds.
    static final String FEEDS_QUESTION = "Which feeds should the survey cover?";
    static final String SURVEY_FEEDS_ARG = "survey_feeds";
    // The catch-all, as explain= names it.
    private static final List<String> CATCH_ALL_NAMES = List.of("unknown", "uncatalogued records",
            "uncatalogued");
    private static final String AMENDMENTS = "amendments";
    static final String STATUS = "status";
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
    // What the agent is told of fields kept as Data whose meaning an element carries (asked for by the user
    // 2026-11-22: worded more strongly, with an example of the call; 2026-11-23, chat 187: one test, the element that
    // means it, as a setting's value was put in a Description the schema "had a home for").
    private static final String SHOULD_BE_TYPED = "These fields should be typed: each one whose meaning an element "
                                                  + "carries belongs in a placement at the element that means it, "
                                                  + "from what you know of the source; the rest stay as Data. Never "
                                                  + "put a value in an element that means something else (e.g. a "
                                                  + "setting's value in a Description). ";
    static final String STREAMS = "streams";
    // What the proposals changed since the set was shown (the user's amendments, from the form or the chat), kept
    // until the set is confirmed: the amended event types are worth a look when it is shown again.
    private static final String CHANGES = "changes";
    // How long a shown set stays in view for its form: past it, the set is shown again before the form.
    private static final long SHOWN_FRESH_MS = 90_000;
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
    private final Provider<BuildRecords> buildRecords;
    private final CatalogueReading recordReading;
    private final CatalogueCatchAll catchAllForms;
    private Provider<CatalogueNotes> catalogueNotesProvider;
    private Provider<ExistingTranslations> existingProvider;
    private Provider<InPlaceInduction> inPlaceProvider;

    @Inject
    CatalogueEventTypesTool(final RecordSourceNotesTool notes,
                            final SampleStreamTexts sampleStreamTexts,
                            final Consent consent,
                            final BuildPlan plan,
                            final TranslationBuilds builds,
                            final Provider<BuildRecords> buildRecords) {
        this(notes, sampleStreamTexts, consent, plan, builds, buildRecords, System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    ///
    /// @param notes             The build's source notes.
    /// @param sampleStreamTexts Reads the sample streams.
    /// @param consent           Asks the user.
    /// @param plan              The build's plan.
    /// @param builds            The builds.
    /// @param buildRecords      The build records: the user's answers of which key of each old/new list holds the
    ///                          value before the change, which the check follows as the draft does.
    /// @param clock             The time now, in milliseconds.
    CatalogueEventTypesTool(final RecordSourceNotesTool notes,
                            final SampleStreamTexts sampleStreamTexts,
                            final Consent consent,
                            final BuildPlan plan,
                            final TranslationBuilds builds,
                            final Provider<BuildRecords> buildRecords,
                            final LongSupplier clock) {
        this.buildRecords = buildRecords;
        this.notes = notes;
        this.sampleStreamTexts = sampleStreamTexts;
        this.consent = consent;
        this.plan = plan;
        this.builds = builds;
        this.clock = clock;
        this.recordReading = new CatalogueReading(sampleStreamTexts, plan, builds);
        this.catchAllForms = new CatalogueCatchAll(notes);
    }

    /// Where the build's catalogue is kept across versions (set by injection; none in tests that make the tool
    /// themselves): a next version's listing and confirmation say what changed since the promoted catalogue.
    ///
    /// @param catalogueNotes The build's catalogue.
    @Inject
    void setCatalogueNotes(final Provider<CatalogueNotes> catalogueNotes) {
        this.catalogueNotesProvider = catalogueNotes;
    }

    /// Where the pipeline already translating what a build is to is described (set by injection; none in tests that
    /// make the tool themselves): the user is asked whether the build starts from it.
    ///
    /// @param existing The existing translations.
    @Inject
    void setExistingTranslations(final Provider<ExistingTranslations> existing) {
        this.existingProvider = existing;
    }

    /// Where an existing pipeline is described in place (set by injection; none in tests that make the tool
    /// themselves, which offer only the copy and the draft).
    ///
    /// @param inPlace The in-place description.
    @Inject
    void setInPlaceInduction(final Provider<InPlaceInduction> inPlace) {
        this.inPlaceProvider = inPlace;
    }

    /// Where a build's records are read at its translation's input (set by injection; none in tests that make the
    /// tool themselves): a pipeline described in place whose translation a pre-processor feeds.
    ///
    /// @param inputs The translation's inputs.
    @Inject
    void setTranslationInputs(final Provider<TranslationInputs> inputs) {
        recordReading.setTranslationInputs(inputs);
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
                        .addProperty(READING_ARG, TranslationArgs.object("For a source with no mapping yet: how "
                                                                         + "its records are read, as a mapping's "
                                                                         + "reading keys (" + String.join(", ",
                                CatalogueReading.READING_KEYS) + ")."))
                        .addProperty(PROPOSALS_ARG, ArraySchema.builder()
                                .description("Changes to the catalogue: {op: add|change|remove, type_id, ...the "
                                             + "event type's keys: title, description, event_detail, action, "
                                             + "success, actor, recognised_by [{when: [conditions], names}], "
                                             + "placements [{path (below event_detail, or from "
                                             + "EventSource/), field or value, "
                                             + "transform, time_format, map, for_each...}: the mapping's fields "
                                             + "entries], basis, confidence, one_type_because}. change gives only "
                                             + "the keys that change.")
                                .items(ObjectSchema.builder()
                                        .addStringProperty(CatalogueProposals.OP, "add, change or remove.")
                                        .addStringProperty(CatalogueProposals.TYPE_ID, "Verb-Object, e.g. "
                                                                                       + "Update-Password.")
                                        .addStringProperty("description", "Written into every event of the "
                                                                          + "type (EventDetail/Description): what "
                                                                          + "happened, in a sentence. Never counts "
                                                                          + "or notes about the build (those go in "
                                                                          + "basis).")
                                        .required(CatalogueProposals.OP)
                                        .build())
                                .build())
                        .addBooleanProperty(CONFIRM_ARG, "True to put the next set to the user (its table shown "
                                                         + "in the chat first); again after each answer.")
                        .addProperty(CATCH_ALL_ARG, ObjectSchema.builder()
                                .description("With confirm: the catch-all's agreement, {allow_unknown: why the "
                                             + "uncatalogued records are kept as Unknown}, used at its turn; or, "
                                             + "where the user said in the chat they should be another event, "
                                             + "translation.")
                                .addStringProperty("allow_unknown", "Why.")
                                .addProperty(TRANSLATION, ObjectSchema.builder()
                                        .description("The event the uncatalogued records are written as instead "
                                                     + "of Unknown, as the user said: {type_id (Verb-Object), "
                                                     + "event_detail (its action element), action, description}; "
                                                     + "their fields kept as Data. Put to the user in the "
                                                     + "catch-all's form.")
                                        .addStringProperty("type_id", "Verb-Object.")
                                        .addStringProperty("event_detail", "Its action element.")
                                        .addStringProperty("action", "Its Action, or left out.")
                                        .addStringProperty("description", "Its fixed Description.")
                                        .build())
                                .build())
                        .addStringProperty(REVIEWED_ARG, "With confirm, for a client without forms: the review_id "
                                                         + "of the needs_review reply, once the user has seen it "
                                                         + "and agreed.")
                        .addProperty(EXPLAIN_ARG, ArraySchema.builder()
                                .description("Alone: what the user asked about while confirming: TypeIds, or "
                                             + "action elements (a group of the summary). Each one's meaning, how "
                                             + "it is recognised, its records and a few examples, to answer them "
                                             + "in the chat. Changes nothing.")
                                .items(StringSchema.builder().build())
                                .build())
                        .addStringProperty(START_FROM_ARG, "Only where no form could ask it: the user's answer "
                                                           + "in the chat to how the build starts, existing, "
                                                           + "in_place or draft.")
                        .addProperty(SURVEY_FEEDS_ARG, ArraySchema.builder()
                                .description("Only where no form could ask it: the feeds the user chose in the chat "
                                             + "for an in-place description's survey.")
                                .items(StringSchema.builder().build())
                                .build())
                        .addBooleanProperty(ANSWERED_ARG, "With confirm, after the user wrote a question in the "
                                                          + "form instead of amendments: true once you've "
                                                          + "answered it; the set is put to them again as it is.")
                        .required(BUILD_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Map<String, Object> result = catalogue(call, context);
            // A client outside Stroom takes only so much of a result; Stroom AI's chat keeps a large result itself,
            // and gives its model the outline in the result's stub.
            final String build = stringArg(call, BUILD_ARG);
            final Map<String, Object> chatOutline = LargeResults.forCaller(context.caller(), result,
                    (caller, said, limit) -> CatalogueOutline.outlineIfLarge(caller, context.workspace(), build, said,
                            limit));
            return JsonResults.of(result).withChatOutline(chatOutline);
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
        final Optional<String> carried = carriedFromPromoted(build);
        final String givenSource = stringArg(call, SOURCE_ARG);
        final Map<String, Object> notesNow = notes.notesOf(context, build, givenSource);
        final String source = NullSafe.nonBlankStringElse(givenSource,
                NullSafe.nonBlankStringElse(ToolText.str(notesNow.getOrDefault("source", "")), build));
        final Calling calling = new Calling(call, context, build, source, notesNow, current(build, notesNow));
        final List<Map<?, ?>> proposals = RecordSourceNotesTool.objects(call, PROPOSALS_ARG);
        final List<String> explain = CatalogueExplanations.explainArg(call);
        if (!explain.isEmpty() && (!proposals.isEmpty() || isTrue(argsMap(call).get(CONFIRM_ARG)))) {
            throw new ToolRefusal(EXPLAIN_ARG + "= changes nothing, so it is given alone, without proposals or "
                                  + "confirm: explain first, answer the user in the chat, then call again.");
        }
        if (explain.isEmpty() && proposals.isEmpty() && !isTrue(argsMap(call).get(CONFIRM_ARG))) {
            final Optional<Map<String, Object>> asked = startChoice(calling);
            if (asked.isPresent()) {
                return asked.get();
            }
        }
        // Started from an existing pipeline: its catalogue is read back with the copy's XSLT, so nothing is listed,
        // proposed or confirmed before that (seen in eval case 104 on Haiku: listed before the copy, its origin "0
        // event types", the proposals refused as not Verb-Object and the catalogue never made).
        final Optional<BuildPlan.ReadBack> waiting = plan.awaitingReadBack(build);
        if (waiting.isPresent()) {
            throw new ToolRefusal("Not yet: " + waiting.get().next() + ". Then " + NAME + " build=" + build
                                  + " lists the catalogue read back, to check and put to the user. Nothing was "
                                  + "recorded.");
        }
        final Map<String, Object> result = !explain.isEmpty()
                ? explained(calling, explain)
                : !proposals.isEmpty()
                        ? proposed(calling, proposals)
                        : isTrue(argsMap(call).get(CONFIRM_ARG))
                                ? confirmed(calling)
                                : listed(calling);
        unsurveyed.ifPresent(why -> result.put("survey", why));
        carried.ifPresent(said -> result.put("carried", said));
        sincePromoted(build).ifPresent(said -> result.put("since_promoted", said));
        return result;
    }

    // A pipeline already translates what the build is to, and the user hasn't chosen how the build starts: asked in a
    // form (decided by the user, 2026-11-24; seen in chat 192: the plan routed to drafting from scratch, and the
    // agent spent some ten calls copying the old catalogue by hand). Empty where nothing is asked, or the user chose
    // to draft (the listing goes on).
    private Optional<Map<String, Object>> startChoice(final Calling calling) {
        if (existingProvider == null) {
            return Optional.empty();
        }
        // Chosen to describe it in place, the feeds not yet chosen: asked again.
        final Optional<Map<String, Object>> pending = inPlacePending(calling);
        if (pending.isPresent()) {
            return pending;
        }
        final Optional<Map<String, Object>> found = plan.existingToOffer(calling.build());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, Object> described = existingProvider.get().describe(found.get());
        final String said = ExistingTranslations.said(described);
        // Its translation described in place (decided with the user 2026-11-25), where one can be told in it: what
        // that covers stated first, so the user can narrow the survey.
        final Optional<InPlaceInduction.Scope> scope = inPlaceProvider == null
                ? Optional.empty()
                : inPlaceProvider.get().scope(ToolText.str(described.get("uuid")));
        final String message = "An existing pipeline already translates " + (JsonValues.strings(
                described.get("source_types")).isEmpty()
                ? "this feed"
                : "these source types") + ": " + said + ". " + START_QUESTION + " " + START_EXISTING + ": it is "
                               + "copied as a new version and its XSLT read back into a mapping and a catalogue of "
                               + "event types; what the tools can't read stays as XSLT; the original isn't "
                               + "touched. " + scope.map(inPlace -> START_IN_PLACE + ": its XSLT read into a mapping "
                                                                     + "and a catalogue kept with it, in hidden "
                                                                     + "blocks only (its code and your text "
                                                                     + "untouched), no new version. "
                                                                     + inPlace.said() + " ")
                                       .orElse("")
                               + "Or " + START_DRAFT.toLowerCase(Locale.ROOT) + " from the sample.";
        final List<String> options = scope.isPresent()
                ? List.of(START_EXISTING, START_IN_PLACE, START_DRAFT)
                : List.of(START_EXISTING, START_DRAFT);
        final ToolCall call = calling.call();
        final ToolCallContext context = calling.context();
        final String given = stringArg(call, START_FROM_ARG);
        final Optional<String> chosen;
        if (given != null && consent.takesChatAnswer(context, NAME, START_QUESTION)) {
            chosen = Optional.of(chatChoice(given, scope.isPresent()));
        } else {
            chosen = consent.choose(call, context, NAME, message, options, START_QUESTION);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        if (chosen.isEmpty()) {
            consent.askInChat(context, NAME, START_QUESTION);
            result.put(STATUS, "needs_choice");
            result.put("existing", described);
            final Map<String, Object> routes = new LinkedHashMap<>();
            routes.put(ExistingTranslations.EXISTING, START_EXISTING + ": copied as a new version (copy_pipeline), "
                                                      + "its XSLT read back into a mapping and catalogue "
                                                      + "(rebuild_mapping); the original isn't touched");
            scope.ifPresent(inPlace -> routes.put(InPlaceInduction.IN_PLACE, START_IN_PLACE + ": its XSLT read into "
                                                                            + "a mapping and catalogue kept with it "
                                                                            + "(hidden blocks only), no new version. "
                                                                            + inPlace.said()));
            routes.put(ExistingTranslations.DRAFT, START_DRAFT + " from the sample, the event types catalogued "
                                                   + "here");
            result.put("routes", routes);
            result.put(HINT, consent.whyInChat(context, NAME, START_QUESTION) + ": ask the user in the chat. "
                             + message + " Then call again with " + START_FROM_ARG + "=existing"
                             + (scope.isPresent()
                    ? ", " + START_FROM_ARG + "=in_place"
                    : "") + " or " + START_FROM_ARG + "=draft, as they answer.");
            return Optional.of(result);
        }
        final String choice = START_EXISTING.equals(chosen.get())
                ? ExistingTranslations.EXISTING
                : START_IN_PLACE.equals(chosen.get())
                        ? InPlaceInduction.IN_PLACE
                        : ExistingTranslations.DRAFT;
        final Map<String, Object> startFrom = new LinkedHashMap<>();
        startFrom.put("choice", choice);
        final Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("name", described.get("name"));
        pipeline.put("uuid", described.get("uuid"));
        startFrom.put("pipeline", pipeline);
        startFrom.put(AT, clock.getAsLong());
        if (ExistingTranslations.EXISTING.equals(choice) && scope.isPresent()) {
            // Copied: where its translation reads its records, which a pre-processor may feed.
            startFrom.put(InPlaceInduction.ELEMENT, scope.get().translation().element());
            startFrom.put(InPlaceInduction.PRE_PROCESSED, scope.get().preProcessed());
        }
        buildRecords.get().rememberStartFrom(calling.build(), startFrom);
        if (InPlaceInduction.IN_PLACE.equals(choice)) {
            return Optional.of(describeInPlace(calling, scope.orElseThrow()));
        }
        if (ExistingTranslations.DRAFT.equals(choice)) {
            return Optional.empty();
        }
        result.put(STATUS, "start_from_existing");
        result.put("existing", described);
        result.put(HINT, "The user chose to start from " + said + ". Next: copy it as a new version (the user "
                         + "confirms its names), then rebuild_mapping reads the copy's XSLT back into a mapping and "
                         + "this catalogue; then " + NAME + " build=" + calling.build() + " checks the catalogue "
                         + "against the sample and puts it to the user.");
        plan.nextStep(calling.build(), null).ifPresent(next -> result.put("next", next));
        return Optional.of(result);
    }

    // The user's answer in the chat to how the build starts, as the form's choice.
    private static String chatChoice(final String given, final boolean inPlace) {
        final String answer = given.strip().toLowerCase(Locale.ROOT);
        if (ExistingTranslations.EXISTING.equals(answer)) {
            return START_EXISTING;
        }
        if (inPlace && InPlaceInduction.IN_PLACE.equals(answer)) {
            return START_IN_PLACE;
        }
        if (ExistingTranslations.DRAFT.equals(answer)) {
            return START_DRAFT;
        }
        throw new ToolRefusal(START_FROM_ARG + " is the user's answer: existing" + (inPlace
                ? ", in_place"
                : "") + " or draft, not '" + given + "'.");
    }

    // A build whose user chose to describe the pipeline in place, not yet described (its feeds to choose): the
    // choice of feeds asked again; empty otherwise.
    private Optional<Map<String, Object>> inPlacePending(final Calling calling) {
        if (inPlaceProvider == null) {
            return Optional.empty();
        }
        final Map<String, Object> startFrom = plan.startFrom(calling.build()).orElse(null);
        if (!InPlaceInduction.inPlace(startFrom) || startFrom.containsKey(InPlaceInduction.IN_PLACE)) {
            return Optional.empty();
        }
        final String uuid = ToolText.str(JsonValues.mapOrEmpty(startFrom.get("pipeline")).get("uuid"));
        final InPlaceInduction.Scope scope = inPlaceProvider.get().scope(uuid)
                .orElseThrow(() -> new ToolRefusal("The pipeline chosen to describe in place (" + uuid + ") has no "
                                                   + "translation the tools can tell: copy it, or draft a new one"));
        return Optional.of(describeInPlace(calling, scope));
    }

    // The pipeline described in place: the feeds to survey chosen first where its XSLT serves several (the count and
    // estimate stated), then its mapping and catalogue read back and written without asking, where the user may.
    private Map<String, Object> describeInPlace(final Calling calling, final InPlaceInduction.Scope scope) {
        final List<String> all = scope.users().feeds().isEmpty()
                ? plan.worksFrom(calling.build())
                : scope.users().feeds();
        final Optional<List<String>> feeds = all.size() > 1
                ? feedsChosen(calling, scope, all)
                : Optional.of(all);
        final Map<String, Object> result = new LinkedHashMap<>();
        if (feeds.isEmpty()) {
            consent.askInChat(calling.context(), NAME, FEEDS_QUESTION);
            result.put(STATUS, "needs_feeds");
            result.put("feeds", all);
            result.put(HINT, consent.whyInChat(calling.context(), NAME, FEEDS_QUESTION) + ": ask the user in the "
                             + "chat. " + scope.said() + " " + FEEDS_QUESTION + " Then call again with "
                             + SURVEY_FEEDS_ARG + "=[the feeds they chose] (every one, unless they narrow it).");
            return result;
        }
        final Map<String, Object> described = inPlaceProvider.get().describe(calling.context(), calling.build(),
                calling.source(), scope, feeds.get());
        result.put(STATUS, described.remove(STATUS));
        result.putAll(described);
        if ("described_in_place".equals(result.get(STATUS))) {
            // Every feed chosen is the build's to survey: the plan works through them.
            buildRecords.get().addSourceFeeds(calling.build(), feeds.get());
            result.put(HINT, "Described in place, without asking (hidden blocks only; nothing else changed). Next: "
                             + "survey each feed (" + String.join(", ", feeds.get()) + "), then " + NAME + " build="
                             + calling.build() + " checks the catalogue read back against their records and puts it "
                             + "to the user to confirm; once confirmed it is written back beside the pipeline. No "
                             + "new version, no promotion.");
            plan.nextStep(calling.build(), null).ifPresent(next -> result.put("next", next));
        }
        return result;
    }

    // The feeds the user chose for the survey: all, or those their answer names; empty where no form can ask (the
    // agent asks in the chat).
    private Optional<List<String>> feedsChosen(final Calling calling,
                                               final InPlaceInduction.Scope scope,
                                               final List<String> all) {
        final Object given = argsMap(calling.call()).get(SURVEY_FEEDS_ARG);
        if (given instanceof final List<?> names && consent.takesChatAnswer(calling.context(), NAME,
                FEEDS_QUESTION)) {
            final List<String> chosen = scope.users().narrowedTo(names.stream().map(String::valueOf).toList());
            if (chosen.isEmpty()) {
                throw new ToolRefusal(SURVEY_FEEDS_ARG + " names none of the feeds the XSLT serves: "
                                      + String.join(", ", all));
            }
            return Optional.of(chosen);
        }
        final String everyOne = "All " + all.size() + " feeds";
        final Optional<String> answer = consent.answer(calling.call(), calling.context(), NAME, scope.said(),
                FEEDS_QUESTION, everyOne);
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        if (answer.get().equalsIgnoreCase(everyOne)) {
            return Optional.of(all);
        }
        final List<String> named = scope.users().namedIn(answer.get());
        if (named.isEmpty()) {
            throw new ToolRefusal("The user's answer names none of the feeds the XSLT serves (" + String.join(", ",
                    all) + "): \"" + answer.get() + "\". Ask them which, then call again with " + SURVEY_FEEDS_ARG
                                  + "=[those feeds].");
        }
        return Optional.of(named);
    }

    // The words a set's summary starts with when the build started from an existing pipeline: where its event types
    // came from (decided by the user, 2026-11-24); empty otherwise.
    private Optional<String> origin(final Calling calling) {
        final Map<String, Object> startFrom = plan.startFrom(calling.build()).orElse(Map.of());
        final boolean inPlace = InPlaceInduction.inPlace(startFrom);
        if (!ExistingTranslations.EXISTING.equals(startFrom.get("choice")) && !inPlace) {
            return Optional.empty();
        }
        final int count = calling.catalogue().eventTypes().size();
        // Described in place too (seen in eval case 108 on Haiku: with no origin, the agent took the listing's seed
        // for the catalogue, removed the types read back and re-added them, its TypeIds then refused as not
        // Verb-Object): the event types are the pipeline's, kept as it writes them.
        return Optional.of(count + " event type" + (count == 1
                ? ""
                : "s") + " from '" + ToolText.str(JsonValues.mapOrEmpty(startFrom.get("pipeline")).get("name"))
                           + "', the existing pipeline this build " + (inPlace
                ? "describes in place"
                : "started from") + " (read back from its XSLT, its TypeIds kept as it writes them; the "
                           + "evidence's seed is the draft's, not these).");
    }

    // A next version's build starts from the promoted catalogue beside the feed it works from, whether or not
    // copy_pipeline has run yet (seen in eval case 70: catalogued before the copy, the confirmed event types were
    // lost): what was carried, said; empty when nothing was.
    private Optional<String> carriedFromPromoted(final String build) {
        if (catalogueNotesProvider == null) {
            return Optional.empty();
        }
        try {
            return catalogueNotesProvider.get().carryBesideFeeds(build, plan.worksFrom(build));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No promoted catalogue carried into {}: {}", build, e.getMessage()),
                    e);
            return Optional.empty();
        }
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
                : new Catalogue(kept.eventTypes(), kept.catchAll(), scope, kept.checkedFeeds());
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
        final List<Map<String, Object>> states = builds.surveyStatesInBuild(build);
        if (!states.isEmpty()) {
            // A build of several feeds catalogues once each is surveyed (decided by the user, 2026-11-25): the
            // evidence holds every feed's records (seen in eval case 103: one surveyed, the other never read).
            final List<String> toSurvey = plan.feedsToSurvey(build);
            if (!toSurvey.isEmpty()) {
                throw new ToolRefusal("Survey every feed first: survey_feed feed=" + toSurvey.getFirst() + " build="
                                      + build + (toSurvey.size() > 1
                        ? ", then each of " + String.join(", ", toSurvey.subList(1, toSurvey.size()))
                        : "") + ", then compare_feeds build=" + build + ". The catalogue's evidence is every "
                                      + "feed's records.");
            }
            // A pre-processed translation described in place (or copied): a survey of the raw records, made before
            // the start was chosen, isn't what the translation reads (seen in eval case 106 on Haiku: the catalogue
            // checked and confirmed against it, the plan's next passed over).
            final Map<String, Object> startFrom = plan.startFrom(build).orElse(null);
            final List<String> stale = states.stream()
                    .filter(state -> TranslationInputs.staleSurvey(startFrom, state))
                    .map(state -> JsonValues.text(state.get("feed")))
                    .toList();
            if (!stale.isEmpty()) {
                throw new ToolRefusal("Survey again first: survey_feed feed=" + NullSafe.nonBlankStringElse(
                        stale.getFirst(), "<the feed>") + " build=" + build + ". Its translation reads what a "
                                      + "pre-processor writes, so its records are surveyed at the translation's "
                                      + "input; the survey made before the start was chosen read them as the parser "
                                      + "does, and no longer counts.");
            }
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
        final CatalogueReading.Records read = read(calling);
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
        origin(calling).ifPresent(said -> result.put("origin", said));
        result.put(HINT, "Propose the source's event types (proposals=[{op: add, type_id, title, description, "
                         + "event_detail, action, actor, recognised_by, placements, basis, confidence}]) from this "
                         + "evidence and what you know of the product: a type is what happened, recognised by "
                         + "conditions on the records' fields; its placements type its fields below the action "
                         + "element (or in EventSource/), whatever is left being Data (stroom://guide/mapping). "
                         + "The seed is the draft's own rules, for you to correct: add only what you keep. A Data "
                         + "Splitter part is a line format, not an event type.");
        result.put("next", next(calling.build(), Map.of(PROPOSALS_ARG, "<the event types>"),
                "checks them against every record; then confirm=true puts them to the user"));
        return result;
    }


    // The seed: the draft's own rules as event types. Within a source type group the draft may tell its records
    // apart by the build's source type field, which no event type is recognised by (the group's module passes other
    // source types over already): then the seed is derived as the draft derives its rules, by the field naming what
    // happened, chosen among the records' other fields ([KindFields]), an event type a value.
    private static List<EventType> seed(final CatalogueReading.Records read, final String sourceTypeField) {
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
        final CatalogueReading.Records read = read(calling);
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
        // What changed since the set was shown (the user's amendments, from its form or the chat): listed as worth
        // a look when it is shown again.
        final Map<String, Object> confirmingNow = recorded
                ? withChanges(confirming(calling.notesNow()), calling.catalogue(), kept)
                : confirming(calling.notesNow());
        if (recorded) {
            result.put("source_notes", notes.recordCatalogue(calling.context(), calling.build(), calling.source(),
                    kept, withRecords(withStreams(confirmingNow, read), check)));
        }
        final boolean amendmentSent = !changed && brought.isEmpty() && applied.refused().isEmpty()
                                      && amendedDigest(confirming(calling.notesNow())) != null;
        if (amendmentSent) {
            // The user's amendments sent, leaving the set as it was (one the check refuses, the user then choosing
            // the rule as it was): the set is put to them again. Seen in eval case 61: kept as amended, every
            // confirm=true replayed the amendment, and the build stopped.
            final Map<String, Object> marks = withStreams(confirming(calling.notesNow()), read);
            marks.remove(AMENDED);
            marks.put(RecordSourceNotesTool.RECORDS, CatalogueTables.recordsOf(check));
            notes.recordCatalogue(calling.context(), calling.build(), calling.source(), kept, marks);
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
        // No table of the set the user confirms next: only confirm=true's needs_showing result carries it (decided
        // by the user 2026-11-23: seen in chats 187 to 189, qwen showed and restyled the check's table before
        // confirming, and the chat then showed the confirmation's too). The counts, problems and typing are the
        // agent's to judge the catalogue by.
        final List<EventType> next = nextSet(kept);
        result.put("catalogue", compact(kept));
        saved(calling, applied.catalogue(), check).ifPresent(path -> result.put("listing", path));
        result.put(HINT, amendmentSent
                ? "These proposals leave the catalogue as it was. The user's amendments are taken as sent: "
                  + NAME + " confirm=true puts the set to the user again."
                : proposedHint(recorded, brought, applied, check));
        result.put("next", (recorded || amendmentSent) && !check.refused()
                           && check.unplacedOf(typeIds(next)).isEmpty()
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
        if (!check.unplaced().isEmpty()) {
            said.add(check.unplaced().size() + " element(s) the schema requires that nothing is placed at "
                     + "(check.unplaced), e.g. " + said(check.unplaced().getFirst()) + ": give those event types "
                     + "placements (op: change, placements) before their set is put to the user.");
        }
        final List<String> couldType = check.typing().entrySet().stream()
                .filter(entry -> entry.getValue().couldType() != null)
                .map(Map.Entry::getKey)
                .toList();
        if (!couldType.isEmpty()) {
            said.add("Most fields of " + String.join(", ", couldType.subList(0, Math.min(5, couldType.size())))
                     + (couldType.size() > 5
                    ? " (and " + (couldType.size() - 5) + " more)"
                    : "") + " are kept as Data (check.typing.<TypeId>.could_type: the fields, the schema's "
                     + "elements below the action element and in EventSource, the arrays of objects a for_each "
                     + "group could type, the name/value lists with their pairs' names, and the old/new lists with "
                     + "their properties' names; the schema's elements "
                     + "every one of them has open said once, in "
                     + "check." + CatalogueCheckResult.COULD_TYPE_ALL + "). " + SHOULD_BE_TYPED
                     + typingExample(couldType.getFirst()));
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
                    : type.withAffectedAs(leaf));
        }
        return catalogue.withEventTypes(types);
    }

    // The scope's records, read as processing will ([CatalogueReading#read]).
    private CatalogueReading.Records read(final Calling calling) {
        return recordReading.read(calling, rawArg(calling.call(), SPLITTER_ARG));
    }

    // The catalogue's check over the scope's records.
    private CatalogueCheckResult check(final Calling calling,
                                       final Catalogue catalogue,
                                       final CatalogueReading.Records read) {
        final EventSchema schema = notes.schemaOrNull(calling.context(), calling.build());
        // What the draft writes: for every event (not an event type's Data, its user Authenticate's), and by its own
        // rules, which the translation's rules start from.
        // Old/new lists by the user's answers for the build, as the draft takes them (2026-11-23).
        return CatalogueCheck.check(catalogue, read.sample(), schema, CatalogueCheck.Drafted.of(
                JsonValues.mapOrEmpty(read.draft().get("mapping"))).withChangeSides(ChangeSides.kept(
                buildRecords.get(), calling.build())));
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
        final Catalogue catalogue = catchAllForms.withCatchAllTranslation(calling);
        final Map<String, Object> confirming = confirming(calling.notesNow());
        final CatalogueReading.Records read = read(calling);
        final CatalogueCheckResult check = check(calling, catalogue, read);
        if (check.refused()) {
            throw new ToolRefusal(notConfirmable(check));
        }
        if (catchAllSettles(catalogue, check)) {
            final Catalogue settled = catalogue.withCatchAllConfirmedByUser();
            notes.recordCatalogue(calling.context(), calling.build(), calling.source(), settled,
                    withoutMarks(confirming));
            return done(calling, settled, check, List.of(), true);
        }
        final List<EventType> set = nextSet(catalogue);
        if (set.isEmpty() && catalogue.catchAll().confirmed()) {
            return done(calling, catalogue, check, List.of(), false);
        }
        final List<CatalogueValidation.Problem> unplaced = check.unplacedOf(typeIds(set));
        if (!unplaced.isEmpty()) {
            throw new ToolRefusal(notPlaced(unplaced));
        }
        final boolean catchAll = set.isEmpty();
        final String shown = catchAll
                ? CatalogueCatchAll.catchAllShown(check, catalogue)
                : shownSet(calling, set, check, confirming);
        final String digest = Consent.reviewId(binding(shown, set, check));
        if (digest.equals(amendedDigest(confirming))) {
            if (!isTrue(argsMap(calling.call()).get(ANSWERED_ARG))) {
                throw new ToolRefusal("The user wrote in this set's form: \""
                                      + JsonValues.map(confirming.get(AMENDED)).get(AMENDMENTS) + "\". If it asks "
                                      + "for changes, send them first, as proposals (op: change, or add and remove), "
                                      + "in one call; the set is then put to them again. If it is a question, answer "
                                      + "it in the chat (" + NAME + " build=" + calling.build() + " " + EXPLAIN_ARG
                                      + "=<TypeIds or action elements>), then call confirm=true " + ANSWERED_ARG
                                      + "=true: the set is put to them again as it is. Ask them in the chat if you "
                                      + "can't tell which.");
            }
            // Their question answered in the chat: the set put to them again as it is, never accepted for them.
            confirming.remove(AMENDED);
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
            final Consent.Request request = CatalogueCatchAll.catchAllRequest(check, catalogue,
                    catchAllForms.catchAllReason(calling));
            try {
                consent.require(calling.call(), calling.context(), request.boundTo(shown));
            } catch (final Consent.SentInstead e) {
                throw new ToolRefusal(sentInstead(calling.build(), request.summary(), e.sent()));
            } catch (final ToolRefusal e) {
                if (Consent.notAgreed(request.summary(), NAME, false).equals(e.getMessage())) {
                    throw new ToolRefusal(CatalogueCatchAll.catchAllDeclined(calling.build(), catalogue, check));
                }
                throw e;
            }
            now = catalogue.withCatchAllConfirmedByUser(catchAllForms.catchAllReason(calling));
        } else {
            final String accept = "Accept all " + set.size() + " event type" + (set.size() == 1
                    ? ""
                    : "s");
            final Map<String, Object> details = new LinkedHashMap<>();
            details.put("event types", "the " + set.size() + " " + confidenceOf(set) + "-confidence ones "
                                       + "summarised in the chat, as they are");
            // The roles the schema leaves, asked in the set's own form, and those given (decided with the user
            // 2026-11-25; seen in eval case 109 on Sonnet: asked only in the table, which the agent didn't show).
            final List<String> roles = TimelineRoles.askedInForm(set);
            if (!roles.isEmpty()) {
                details.put("timeline roles", new Consent.Listed(roles));
            }
            final DocRef notesDoc = notes.notesDoc(calling.build(), calling.source());
            details.put("every row", notesDoc.getName());
            final Consent.Request request = Consent.Request.confirmation(NAME, formText(accept), details)
                    // The acceptance a button, the user's amendments or question typed under it (asked for by the
                    // user 2026-11-21: a prefilled box, as chat 182 showed, isn't a way to accept). The field's title
                    // short, as the form's message, shown above it, asks the question.
                    .withSuggested(AMENDMENTS, "Your answer", accept)
                    .boundTo(binding(shown, set, check));
            // The form links the source notes, where every row is, once the doc is there.
            try {
                consent.require(calling.call(), calling.context(), notesDoc.getUuid() == null
                        ? request
                        : request.withDocuments(notesDoc));
            } catch (final Consent.SentInstead e) {
                throw new ToolRefusal(sentInstead(calling.build(), formText(accept), e.sent()));
            }
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
        if (catchAllSettles(now, check)) {
            final Catalogue settled = now.withCatchAllConfirmedByUser();
            notes.recordCatalogue(calling.context(), calling.build(), calling.source(), settled, confirming);
            return done(calling, settled, check, confirmedNow, true);
        }
        final List<EventType> next = nextSet(now);
        if (next.isEmpty() && now.catchAll().confirmed()) {
            notes.recordCatalogue(calling.context(), calling.build(), calling.source(), now, confirming);
            return done(calling, now, check, confirmedNow, false);
        }
        final List<CatalogueValidation.Problem> unplaced = check.unplacedOf(typeIds(next));
        if (!unplaced.isEmpty()) {
            // The next set leaves an element the schema requires empty: not shown until it is placed.
            notes.recordCatalogue(calling.context(), calling.build(), calling.source(), now, confirming);
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put(STATUS, NEEDS_PLACEMENTS);
            if (!confirmedNow.isEmpty()) {
                out.put("confirmed_now", confirmedNow);
            }
            out.put("unplaced", unplaced.stream().map(CatalogueValidation.Problem::toJson).toList());
            out.put("catalogue", compact(now));
            out.put(HINT, notPlaced(unplaced));
            out.put("next", next(calling.build(), Map.of(PROPOSALS_ARG, "<placements>"), "places them; then "
                                                                                          + "confirm=true"));
            return out;
        }
        final String shown = next.isEmpty()
                ? CatalogueCatchAll.catchAllShown(check, now)
                : shownSet(calling, next, check, confirming);
        final Map<String, Object> result;
        if (calling.context().userQuestions().canAsk()) {
            result = presented(calling, now, check, next, shown, confirming);
        } else {
            notes.recordCatalogue(calling.context(), calling.build(), calling.source(), now, confirming);
            result = review(calling, now, check, next, shown);
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(STATUS, result.remove(STATUS));
        if (!confirmedNow.isEmpty()) {
            out.put("confirmed_now", confirmedNow);
        }
        out.putAll(result);
        return out;
    }

    // Every event type confirmed, and the catch-all left to put to the user, with no sampled record uncatalogued:
    // there is nothing to show them, so it stands as proposed with no form (asked for by the user 2026-11-21, chat
    // 182: a form asking them to keep 0 records as Unknown).
    private static boolean catchAllSettles(final Catalogue catalogue, final CatalogueCheckResult check) {
        return nextSet(catalogue).isEmpty()
               && !catalogue.catchAll().confirmed()
               && check.uncatalogued().records() == 0;
    }

    // Every event type and the catch-all confirmed: the catalogue done, the plan's next step.
    private Map<String, Object> done(final Calling calling,
                                     final Catalogue catalogue,
                                     final CatalogueCheckResult check,
                                     final List<String> confirmedNow,
                                     final boolean catchAllSettled) {
        final Map<String, Object> result = new LinkedHashMap<>();
        if (!confirmedNow.isEmpty()) {
            result.put("confirmed_now", confirmedNow);
        }
        result.put("catalogue", compact(catalogue));
        result.put("check", check.toJson());
        result.put(HINT, (catchAllSettled
                ? "No sampled record is uncatalogued, so the catch-all stands as proposed (records no event type "
                  + "recognises, should any come, kept as "
                  + CatalogueCatchAll.translatedAs(catalogue.catchAll().translation())
                  + "), with no form: don't ask the user about it. "
                : "")
                         + "Every event type and the catch-all are confirmed: the catalogue is in the build's source "
                         + "notes, which draft_translation_mapping (build=) follows, a rule a way of recognising "
                         + "each event type.");
        // A pipeline described in place: the confirmed catalogue written back beside it (decided 2026-11-25).
        final Optional<Map<String, Object>> inPlace = inPlaceProvider == null
                ? Optional.empty()
                : inPlaceProvider.get().confirmed(calling.context(), calling.build());
        inPlace.ifPresent(written -> {
            result.put(InPlaceInduction.IN_PLACE, written);
            result.put(HINT, "Every event type and the catch-all are confirmed. The pipeline was described in place: "
                             + "the catalogue is written back to the source notes beside it, where its translation "
                             + "XSLT links to it (in_place says what was written, and who can write what wasn't). No "
                             + "new version: the build is done.");
        });
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
        mark.put(DIGEST, Consent.reviewId(binding(shown, set, check)));
        mark.put(AT, clock.getAsLong());
        marks.put(SHOWN, mark);
        marks.put(RecordSourceNotesTool.RECORDS, CatalogueTables.recordsOf(check));
        notes.recordCatalogue(calling.context(), calling.build(), calling.source(), catalogue, marks);
        final Map<String, Object> result = new LinkedHashMap<>();
        // Shown to the user as it is (seen in testing: a set's table saved to the workspace, then retyped by the
        // agent, shortened and restyled): in Stroom AI's chat, by the chat itself; elsewhere, by the agent.
        result.put(STATUS, NEEDS_SHOWING);
        result.put(TABLE, shown);
        result.put("catalogue", compact(catalogue));
        final String what = set.isEmpty()
                ? "the catch-all for the uncatalogued records (the " + check.uncatalogued().records() + " records "
                  + "no event type recognises, kept as "
                  + CatalogueCatchAll.translatedAs(catalogue.catchAll().translation()) + ")"
                : "the " + set.size() + " " + confidenceOf(set) + "-confidence event type(s)" + (HIGH.equals(
                        confidenceOf(set))
                        ? " (this summary; every row in the source notes)"
                        : "");
        final String form = NAME + " build=" + calling.build() + " confirm=true: a short form asks them to accept "
                            + what + " or type their amendments. They may ask about any of them instead (in the "
                            + "form or the chat): answer a question in the chat (" + EXPLAIN_ARG + "=<TypeIds or "
                            + "action elements> gives each one's meaning, recognition and example records), then "
                            + "confirm=true puts the set to them again; propose only a change they ask for; only "
                            + "the form accepts.";
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
        if (!Consent.wasReviewed(reviewed, binding(shown, set, check))) {
            final Map<String, Object> result = review(calling, catalogue, check, set, shown);
            result.put(HINT, result.get(HINT) + Consent.reviewNote(reviewed));
            return result;
        }
        final Catalogue now = set.isEmpty()
                ? catalogue.withCatchAllConfirmedByUser(catchAllForms.catchAllReason(calling))
                : catalogue.confirmedByUser(typeIds(set));
        return afterConfirming(calling, now, check, set.isEmpty()
                ? List.of()
                : typeIds(set).stream().toList(), withoutMarks(confirming));
    }

    private Map<String, Object> review(final Calling calling,
                                       final Catalogue catalogue,
                                       final CatalogueCheckResult check,
                                       final List<EventType> set,
                                       final String shown) {
        final String reviewId = Consent.reviewId(binding(shown, set, check));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, NEEDS_REVIEW);
        result.put(TABLE, shown);
        result.put("review_id", reviewId);
        result.put("catalogue", compact(catalogue));
        result.put(HINT, "Show the user this table in your reply, as it is, whole, and ask them to accept "
                         + (set.isEmpty()
                ? "the catch-all (the uncatalogued records kept as Unknown)"
                : "all " + set.size() + " event type(s)") + " or say what to change; they may ask about any "
                         + "of them (answer from " + EXPLAIN_ARG + "=<TypeIds or action elements>, then show the "
                         + "table again). Once they accept, call "
                         + NAME + " build=" + calling.build() + " confirm=true reviewed='" + reviewId
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
        // The set put to them again once the amendments are proposed (shown again then), what changed so far kept.
        final Map<String, Object> marks = new LinkedHashMap<>(confirming);
        marks.remove(SHOWN);
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
        result.put(HINT, "The user wrote in the form of the " + confidenceOf(set) + "-confidence event types: \""
                         + amendments + "\". Nothing of the set is confirmed. Read it: changes they ask for you "
                         + "send as proposals (op: change; add or remove where they split or reject one; drop: true "
                         + "only where they said to drop one), in one call, then " + NAME + " confirm=true: the set "
                         + "is put to them again. A question about any of them (examples, what one means, how it is "
                         + "recognised, why they are grouped so) you answer in the chat (" + EXPLAIN_ARG
                         + "=<TypeIds or action elements>), then confirm=true " + ANSWERED_ARG + "=true puts the "
                         + "set to them again as it is. Ask them in the chat if you can't tell which.");
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

    // Why the catalogue can't be put to the user as it is: overlaps, event types that may be several (the seed's),
    // problems.
    // The set's event types leaving an element the schema requires empty: why the set isn't put to the user.
    private static String notPlaced(final List<CatalogueValidation.Problem> unplaced) {
        // Each said with its event type already.
        final List<String> said = unplaced.stream().limit(5).map(CatalogueValidation.Problem::problem).toList();
        return "Not put to the user: " + unplaced.size() + " element(s) the schema requires are left empty: "
               + String.join("; ", said) + (unplaced.size() > said.size()
                ? "; and " + (unplaced.size() - said.size()) + " more (check.unplaced)"
                : "") + ". Give those event types placements (op: change, placements: [{path below event_detail, "
               + "field or value}], from what you know of the source; stroom://guide/mapping), then confirm again.";
    }

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

    /// The set put to the user next (decided by the user 2026-11-20: at most three sets, never many forms): the
    /// high-confidence event types not yet confirmed, all at once (the build is one source type group); else all
    /// the medium; else all the low; in the catalogue's order. A confidence with none waiting is passed over.
    ///
    /// @param catalogue The catalogue.
    /// @return The next set; empty when every event type is confirmed (the catch-all's turn).
    static List<EventType> nextSet(final Catalogue catalogue) {
        for (final String confidence : CONFIDENCES) {
            final List<EventType> waiting = catalogue.eventTypes().stream()
                    .filter(type -> !type.confirmed() && confidence.equals(type.confidence()))
                    .toList();
            if (!waiting.isEmpty()) {
                return waiting;
            }
        }
        // A confidence the catalogue doesn't know (it reads none but these): the rest together.
        return catalogue.eventTypes().stream().filter(type -> !type.confirmed()).toList();
    }

    // What a set's confirmation is bound to: what the chat showed, and every row of the set (the summary names only
    // some TypeIds, so a change to any event type of the set, its title say, puts the set to the user again).
    private static String binding(final String shown, final List<EventType> set, final CatalogueCheckResult check) {
        return set.isEmpty()
                ? shown
                : shown + "\n\n" + CatalogueTables.setTable(set, check);
    }

    // A set as the chat shows it: a summary, whatever its number and confidence, the event types amended since it
    // was shown among those worth a look; every row in the source notes, which it names.
    private String shownSet(final Calling calling,
                            final List<EventType> set,
                            final CatalogueCheckResult check,
                            final Map<String, Object> confirming) {
        final String notesDoc = notesLink(calling);
        final String summary = CatalogueSummary.summary(set, check, notesDoc, CatalogueSummary.Amendments.fromJson(
                JsonValues.map(confirming.get(CHANGES))))
                               // Each event type's timeline role: the schema's filled in, the rest asked for here, in
                               // the set's own form (decided 2026-11-25).
                               + "\n\n" + TimelineRoles.said(set);
        return origin(calling)
                .map(said -> said + "\n\n" + summary)
                .orElse(summary);
    }

    // The source notes doc, where every row is: linked as the caller opens documents (Stroom AI's chat, or Stroom's
    // URL for a client outside it), at its top; its name quoted before it exists.
    private String notesLink(final Calling calling) {
        final DocRef notesDoc = notes.notesDoc(calling.build(), calling.source());
        return notesDoc.getUuid() == null
                ? "'" + notesDoc.getName() + "'"
                : calling.context().links().document(notesDoc);
    }

    // The confirmation's marks with what the proposals changed, while a set is shown or amended (the user's
    // amendments, from its form or the chat); as they are otherwise.
    private static Map<String, Object> withChanges(final Map<String, Object> confirming,
                                                   final Catalogue before,
                                                   final Catalogue after) {
        if (!confirming.containsKey(SHOWN) && !confirming.containsKey(AMENDED)) {
            return confirming;
        }
        final CatalogueSummary.Amendments changes = CatalogueSummary.Amendments.fromJson(
                JsonValues.map(confirming.get(CHANGES))).with(CatalogueDiff.between(before, after));
        final Map<String, Object> out = new LinkedHashMap<>(confirming);
        if (changes.none()) {
            out.remove(CHANGES);
        } else {
            out.put(CHANGES, changes.toJson());
        }
        return out;
    }

    // The digest of the set the user wrote in the form of, until its amendments are sent; else null.
    private static String amendedDigest(final Map<String, Object> confirming) {
        final Map<String, Object> amended = JsonValues.map(confirming.get(AMENDED));
        return amended == null
                ? null
                : text(amended.get(DIGEST));
    }

    // The catalogue form's words: short and plain (the set is summarised in the chat).
    private static String formText(final String accept) {
        return accept + ", or type your amendments, or ask about any of them";
    }

    // The user sent a message in the chat instead of answering the catalogue's form: the agent's to read (decided by
    // the user 2026-11-20). A question about the event types is answered in the chat, the set then put to them again;
    // a change is proposed; acceptance is only ever the form's.
    private static String sentInstead(final String build, final String asked, final String sent) {
        final String quoted = NullSafe.isBlankString(sent)
                ? " (it is in the chat, just before this)"
                : ": \"" + sent.strip() + "\"";
        return "The user sent a message instead of answering '" + asked + "'" + quoted + ". Nothing was confirmed. "
               + "Read it first. A question about what was put to them (examples, what an event type means, how it "
               + "is recognised, why they are grouped so, the uncatalogued records) you answer in the chat: " + NAME
               + " build=" + build + " " + EXPLAIN_ARG + "=<TypeIds or action elements> gives what you need; then "
               + NAME + " build=" + build + " confirm=true puts the set to them again. A change they ask for you "
               + "propose (proposals: op change, or add and remove), then confirm=true. If you can't tell which, ask "
               + "them. Only the form accepts the event types: never take their message as acceptance.";
    }


    // --------------------------------------------------------------------------------
    // Explaining


    // What the user asked about while confirming, for the agent to answer them in the chat: each event type named
    // (TypeId), each group (an action element of the summary) with its members, or the uncatalogued records. Nothing
    // is changed: no catalogue, no confirmation mark, no listing written.
    private Map<String, Object> explained(final Calling calling, final List<String> wanted) {
        final Catalogue catalogue = calling.catalogue();
        final List<EventType> pending = nextSet(catalogue);
        final CatalogueReading.Records read = read(calling);
        final CatalogueCheckResult check = check(calling, catalogue, read);
        final List<EventType> types = new ArrayList<>();
        final List<CatalogueSummary.Group> groups = new ArrayList<>();
        boolean uncatalogued = false;
        final List<String> unknown = new ArrayList<>();
        for (final String name : wanted) {
            final EventType type = CatalogueExplanations.eventType(catalogue, name);
            final Optional<CatalogueSummary.Group> group = type == null
                    ? CatalogueExplanations.group(pending, catalogue, check, name)
                    : Optional.empty();
            if (type != null) {
                if (!types.contains(type)) {
                    types.add(type);
                }
            } else if (group.isPresent()) {
                groups.add(group.get());
            } else if (CATCH_ALL_NAMES.contains(name.strip().toLowerCase(Locale.ROOT))) {
                uncatalogued = true;
            } else {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ToolRefusal(CatalogueExplanations.unknownNames(catalogue, check, unknown));
        }
        final List<EventType> explainedTypes = types.subList(0, Math.min(MOST_EXPLAINED, types.size()));
        final Map<String, List<CatalogueCheck.Example>> examples = CatalogueCheck.examples(read.sample(),
                explainedTypes, EXAMPLES_GIVEN);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, EXPLAINED);
        read.said(result);
        if (!explainedTypes.isEmpty()) {
            result.put("event_types", explainedTypes.stream()
                    .map(type -> CatalogueExplanations.explainedType(type, check,
                            examples.getOrDefault(type.typeId(), List.of()), pending.contains(type)))
                    .toList());
        }
        if (types.size() > explainedTypes.size()) {
            result.put("not_explained", "The other " + (types.size() - explainedTypes.size()) + " event types "
                                        + "asked for, explained " + MOST_EXPLAINED + " at a time: "
                                        + String.join(", ", types.subList(MOST_EXPLAINED, types.size()).stream()
                    .map(EventType::typeId)
                    .toList()));
        }
        if (!groups.isEmpty()) {
            result.put("groups", groups.stream()
                    .map(group -> CatalogueExplanations.explainedGroup(group, check))
                    .toList());
        }
        if (uncatalogued) {
            final Map<String, Object> rest = new LinkedHashMap<>();
            rest.put("translated_as", CatalogueCatchAll.translatedAs(catalogue.catchAll().translation())
                                      + ", their fields kept as Data");
            rest.putAll(JsonValues.mapOrEmpty(check.toJson().get("uncatalogued")));
            result.put("uncatalogued_records", rest);
        }
        result.put("every_row", "the source notes " + notesLink(calling) + ", a section per action element");
        result.put("catalogue", compact(catalogue));
        final boolean wroteInForm = amendedDigest(confirming(calling.notesNow())) != null;
        final Map<String, Object> then = new LinkedHashMap<>();
        then.put(CONFIRM_ARG, true);
        if (wroteInForm) {
            then.put(ANSWERED_ARG, true);
        }
        result.put(HINT, "Nothing was changed. Answer the user's question in the chat from this, in their words "
                         + "(event type, record, field, recognised by), quoting a record or two as they are where "
                         + "they asked for examples. Then " + NAME + " build=" + calling.build() + " confirm=true"
                         + (wroteInForm
                ? " " + ANSWERED_ARG + "=true"
                : "") + " puts the set to them again: only its form accepts. If they asked for a change, propose "
                         + "it first (proposals).");
        result.put("next", next(calling.build(), then, "puts the set to the user again, for their answer in its "
                                                       + "form"));
        return result;
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
            // Its timeline role, filled in from the schema or given (decided 2026-11-25): listed.
            final EventType settled = TimelineRoles.settled(type);
            if (settled.timelineRole() != null) {
                one.put(EventType.TIMELINE_ROLE, settled.timelineRole());
            }
            types.add(one);
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("event_types", types);
        out.put(SourceNotes.CATCH_ALL, Map.of(STATUS, catalogue.catchAll().status()));
        final List<String> asked = TimelineRoles.unsettled(catalogue.eventTypes()).stream()
                .map(EventType::typeId)
                .toList();
        if (!asked.isEmpty()) {
            // The user is asked for these in their set's form (the schema doesn't settle them).
            out.put("timeline_roles_to_ask", asked);
        }
        if (!catalogue.checkedFeeds().isEmpty()) {
            out.put(Catalogue.CHECKED_FEEDS, catalogue.checkedFeeds());
        }
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

    /// The confirmation in progress, as the build's source notes hold it: a copy, empty when there is none.
    ///
    /// @param notesNow The build's source notes as they are.
    /// @return The confirmation in progress.
    static Map<String, Object> confirming(final Map<String, Object> notesNow) {
        return new LinkedHashMap<>(NullSafe.map(JsonValues.map(notesNow.get(RecordSourceNotesTool.CONFIRMING))));
    }

    // An example of the call placing fields, for the event type named (the element and field an illustration only:
    // which field goes where is the agent's to say).
    private static String typingExample(final String typeId) {
        return "E.g. " + NAME + " proposals=[{\"op\": \"change\", \"type_id\": \"" + typeId + "\", "
               + "\"placements\": [{\"path\": \"Folder/Path\", \"field\": \"<the field holding the folder's "
               + "path>\"}]}] (the path below the action element, or from EventSource/ as EventSource/Client/Name, one "
               + "placement per field, at the element that means it; a name/value list's pair by its name, "
               + "{\"path\": \"Configuration/Name\", \"pair\": {\"list\": \"<the list>\", \"name\": \"<the "
               + "pair's name>\"}}; an old/new list's property by its name for an Update's Before and After, "
               + "{\"path\": \"User/Name\", \"change\": {\"list\": \"<the list>\", \"name\": \"<the "
               + "property's name>\"}}).";
    }

    // The confirmation's marks cleared (no set shown or amended), the streams kept.
    private static Map<String, Object> withoutMarks(final Map<String, Object> confirming) {
        final Map<String, Object> out = new LinkedHashMap<>(confirming);
        out.remove(SHOWN);
        out.remove(AMENDED);
        out.remove(CHANGES);
        return out;
    }

    // The confirmation's marks with the records of the sample each event type recognises, for the notes' table.
    private static Map<String, Object> withRecords(final Map<String, Object> confirming,
                                                   final CatalogueCheckResult check) {
        final Map<String, Object> out = new LinkedHashMap<>(confirming);
        out.put(RecordSourceNotesTool.RECORDS, CatalogueTables.recordsOf(check));
        return out;
    }

    // The confirmation's marks with the streams read, and the reading given (kept for the calls after it).
    private static Map<String, Object> withStreams(final Map<String, Object> confirming,
                                                   final CatalogueReading.Records read) {
        final Map<String, Object> out = new LinkedHashMap<>(confirming);
        out.put(STREAMS, read.streams());
        if (read.given() != null) {
            out.put(READING, read.given());
        }
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

    /// A value as text.
    ///
    /// @param value The value, or null.
    /// @return It as text; null when it is null or blank.
    static String text(final Object value) {
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
    record Calling(ToolCall call,
                           ToolCallContext context,
                           String build,
                           String source,
                           Map<String, Object> notesNow,
                           Catalogue catalogue) {

    }
}
