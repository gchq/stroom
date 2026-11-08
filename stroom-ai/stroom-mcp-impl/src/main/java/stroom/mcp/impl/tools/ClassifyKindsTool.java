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
import stroom.ai.impl.tools.IpAddresses;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// `classify_kinds` - the agent classifies every kind of event in the sample the user's documentation doesn't
/// cover, and the user confirms the classifications, before the translation is drafted (work package E8, every
/// decision the user's).
///
/// Called without proposals, it lists those kinds (found as the draft finds them, inner messages unpacked), each
/// with its count and up to three of its records, and keeps them in the build's source notes as still to classify.
/// Called with proposals (a catalogue entry a kind: its action element, Action, outcome, which field is the actor
/// and which acted on, and how sure the agent is), it keeps them and gives back a table for the chat: a set at a
/// time, high confidence first (one table), then medium, then low (in detail), each set numbered from 1. Called with
/// confirm, a short form asks the user to accept the set shown or amend it, one or the other. What the user accepts
/// is recorded in the source notes as `user_confirmed`, which the draft follows (a rule a kind) and the build checks
/// against; amendments come back for the agent, which sends the amended entries as the user's (`from_user`),
/// recorded without another form. Without forms, the agent puts the table to the user in the chat.
///
/// The tool holds no knowledge of any source: the meanings are the agent's and the user's.
class ClassifyKindsTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClassifyKindsTool.class);

    static final String NAME = "classify_kinds";
    private static final String BUILD_ARG = "build";
    private static final String SOURCE_ARG = "source";
    private static final String STREAM_IDS_ARG = "stream_ids";
    private static final String SPLITTER_ARG = "splitter";
    private static final String PROPOSALS_ARG = "proposals";
    private static final String CONFIRM_ARG = "confirm";
    private static final String ONE_KIND_ARG = "one_kind";
    // The answer a form suggests for records that are one kind of event.
    private static final String ONE_KIND = "One kind";

    // A proposal's keys, as given and as kept in the notes (a catalogue event's, as record_source_notes takes them).
    private static final String FIELD = "field";
    private static final String VALUE = "value";
    private static final String EVENT = "event";
    private static final String TITLE = "title";
    private static final String PRODUCT = "product";
    private static final String DESCRIPTION = "description";
    private static final String EVENT_DETAIL = "event_detail";
    private static final String ACTION = "action";
    private static final String SUCCESS = "success";
    private static final String TYPE_ID = "type_id";
    private static final String ACTOR = "actor";
    private static final String AFFECTED = "affected";
    private static final String AFFECTED_AS = "affected_as";
    private static final String CONFIDENCE = "confidence";
    private static final String BASIS = "basis";
    private static final String FROM_USER = "from_user";
    // A kind the user said to leave untranslated, sent as theirs.
    private static final String DROP = "drop";
    // A kept proposal's kind, as field=value.
    private static final String KEY = "kind";
    // A proposal's number in the set shown, from 1, as the chat shows it.
    private static final String NUMBER = "number";
    // A kept proposal's place among those of its confidence, from 1, and how many there are: the heading's range.
    private static final String POSITION = "position";
    private static final String OF = "of";
    // A kind's sample record, shown with its proposal.
    private static final String SAMPLE = "sample";
    /// The notes block's key for each kind's sample record (from the listing), shown with its proposal.
    static final String SAMPLES = "samples";
    // A kind told apart by its format alone.
    private static final String CATCH_ALL = "catch_all";
    // A kind's record count, kept with its sample.
    private static final String RECORDS = "records";
    // The action element of an event not classified.
    private static final String UNKNOWN = "Unknown";

    // How sure the agent is of a proposal, in the order the proposals are put to the user.
    private static final String HIGH = "high";
    private static final String MEDIUM = "medium";
    private static final String LOW = "low";
    private static final List<String> CONFIDENCES = List.of(HIGH, MEDIUM, LOW);

    // A result's keys.
    private static final String STATUS = "status";
    // A result's status when its table is for the user, as it is.
    private static final String NEEDS_SHOWING = "needs_showing";
    private static final String HINT = "hint";
    private static final String TABLE = "table";
    private static final String RECORDED = "recorded";
    private static final String SOURCE_NOTES = "source_notes";
    private static final String TO_CLASSIFY = "to_classify";
    private static final String STILL_TO_CLASSIFY = "still_to_classify";
    private static final String AMENDMENTS = "amendments";

    // The draft's mapping and its rules' keys.
    private static final String MAPPING = "mapping";
    private static final String EVENTS = "events";
    private static final String EQUALS = "equals";

    // The kinds a round takes (asked for by the user: a source of a hundred kinds in rounds a person can read).
    static final int BATCH = 18;
    // The proposals put to the user at once, of one confidence (asked for by the user).
    static final int SET_SIZE = 10;
    private static final int MAX_SAMPLE_STREAMS = 5;
    private static final int EXAMPLES = 3;
    private static final int EXAMPLE_FIELDS = 20;
    private static final int EXAMPLE_VALUE = 100;
    // The shortest value looked for in the sample's lines, to find a record's own line.
    private static final int MIN_MATCHED = 3;
    // A field named for a time or date, whose values (Nov  6 09:02:00) are several words but not free text.
    private static final Pattern TIME_LIKE = Pattern.compile("(?i)(time|date)");
    // The fewest words a value of free text has.
    private static final int FREE_TEXT_WORDS = 4;
    // A field holding a record's time, left out of a record shown by its fields.
    private static final Pattern TIME_FIELD = Pattern.compile("(?i)(time|timestamp|ts|date|datetime|@timestamp)");
    // A field's name that says it names a user or account (generic words, no product's).
    private static final Pattern USER_NAME = Pattern.compile("(?i)(user|account|login|principal|subject|actor|"
                                                             + "owner|member|identity|uid)");

    private final RecordSourceNotesTool notes;
    private final SampleStreamTexts sampleStreamTexts;
    private final Consent consent;
    private final BuildPlan plan;
    private final TranslationBuilds builds;

    @Inject
    ClassifyKindsTool(final RecordSourceNotesTool notes,
                      final SampleStreamTexts sampleStreamTexts,
                      final Consent consent,
                      final BuildPlan plan,
                      final TranslationBuilds builds) {
        this.notes = notes;
        this.sampleStreamTexts = sampleStreamTexts;
        this.consent = consent;
        this.plan = plan;
        this.builds = builds;
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
                             + "Classify each kind of event in the sample that the user's documentation doesn't "
                             + "cover, and have the user confirm the classifications in one form, before drafting "
                             + "the translation. Call first without proposals: it lists those kinds, each with its "
                             + "count and records. Then call with a proposal for every kind listed, from the records "
                             + "and what you know of the source: it gives back a table to show the user in your "
                             + "reply. Then call with confirm=true: a short form asks them to accept them all or "
                             + "amend them, a set at a time (high confidence, then medium, then low), each set's "
                             + "table shown first. What is confirmed goes "
                             + "into the build's source notes, which draft_translation_mapping follows (a rule a "
                             + "kind). Send corrections the user gave (in the form or the chat) as proposals with "
                             + "from_user: true; leave out a kind the user rejected and ask them what it is. "
                             + "from_user is only for what the user said: never for your own proposals, which "
                             + "the user confirms in the form.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addStringProperty(SOURCE_ARG, "The source's name, as record_source_notes takes it; the "
                                                       + "build's source notes' when it has them, else the build's "
                                                       + "name.")
                        .addProperty(STREAM_IDS_ARG, BuildTranslationXsltTool.streamIdsSchema(
                                "The sample streams; the build's newest when left out."))
                        .addProperty(SPLITTER_ARG, TranslationArgs.object("The Data Splitter spec the text is read "
                                                                          + "with (build_data_splitter's spec), as "
                                                                          + "for draft_translation_mapping; the "
                                                                          + "build's saved Data Splitter when left "
                                                                          + "out."))
                        .addProperty(PROPOSALS_ARG, ArraySchema.builder()
                                .description("One per kind listed: its field and value (as listed), description, "
                                             + "event_detail (the action element, e.g. Authenticate, Update, "
                                             + "Network/Permit), action and success where it has them, type_id, "
                                             + "actor and affected where the records name them (the fields "
                                             + "naming the user who acted, and who or what it was done to; left "
                                             + "out otherwise), affected_as (what that is: User, File, "
                                             + "Document, Group...), and confidence (high, medium or low); or "
                                             + "from_user: true only for what the user said.")
                                .items(proposal())
                                .build())
                        .addBooleanProperty(CONFIRM_ARG, "True once the proposals' table is shown in the chat: the "
                                                         + "user confirms or corrects them in a short form.")
                        .addBooleanProperty(ONE_KIND_ARG, "True when the records, not told apart into kinds, are one "
                                                          + "kind of event: the user confirms it in a short form "
                                                          + "(without forms, ask them in the chat first).")
                        .required(BUILD_ARG)
                        .build())
                .build();
    }

    private static ObjectSchema proposal() {
        return ObjectSchema.builder()
                .addStringProperty(FIELD, "The field the kind is told by, as listed.")
                .addStringProperty(VALUE, "Its value for this kind, as listed.")
                .addStringProperty(EVENT, "The kind's name or id (the value when left out).")
                .addStringProperty(TITLE, "A short name for the kind, the heading the user reads, e.g. 'SQL "
                                            + "injection signature'.")
                .addStringProperty(PRODUCT, "The product or part of it the kind comes from, in the heading's "
                                              + "brackets, e.g. 'NetScaler AppFirewall'.")
                .addStringProperty(DESCRIPTION, "What the event is.")
                .addStringProperty(EVENT_DETAIL, "The EventDetail action element.")
                .addStringProperty(ACTION, "The action element's Action, when it has one.")
                .addBooleanProperty(SUCCESS, "The outcome, when the kind says it.")
                .addStringProperty(TYPE_ID, "The event's TypeId.")
                .addStringProperty(ACTOR, "The field naming the user or account who acted (one of user_fields); "
                                            + "left out when the records name none, as for network traffic.")
                .addStringProperty(AFFECTED, "The field naming the user or object acted on; left out when there "
                                               + "is none, as for an alert or a connection.")
                .addStringProperty(AFFECTED_AS, "What the affected field names, as the action element's object: "
                                                  + "User, File, Document, Group... (or its leaf, User/Name).")
                .addStringProperty(CONFIDENCE, "high, medium or low: how sure you are.")
                .addBooleanProperty(FROM_USER, "True for what the user said (recorded without asking again).")
                .addBooleanProperty(DROP, "With from_user, true for a kind the user said to leave untranslated: "
                                          + "kept as settled (not classified) and left out of later rounds; give "
                                          + "it a drop rule in the mapping (drop_when).")
                .required(FIELD, VALUE, DESCRIPTION)
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(classify(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> classify(final ToolCall call, final ToolCallContext context) {
        final String build = stringArg(call, BUILD_ARG);
        if (build == null) {
            throw new ToolRefusal("Give build");
        }
        final String givenSource = stringArg(call, SOURCE_ARG);
        final Map<String, Object> notesNow = notes.notesOf(context, build, givenSource);
        // The source notes' name, else the build's (the plan's call gives none).
        final String source = NullSafe.nonBlankStringElse(givenSource,
                NullSafe.nonBlankStringElse(ToolText.str(notesNow.getOrDefault("source", "")), build));
        final List<Map<?, ?>> proposals = RecordSourceNotesTool.objects(call, PROPOSALS_ARG);
        final Map<String, Object> result = !proposals.isEmpty()
                ? proposed(context, build, source, notesNow, proposals)
                : confirming(call)
                        ? confirmed(call, context, build, source, notesNow)
                        : listed(call, context, build, source, notesNow);
        if (!proposals.isEmpty() && confirming(call) && result.containsKey(TABLE)) {
            // Seen in testing: confirm=true sent with the proposals, the form expected; the user hasn't seen the
            // table yet, so the form waits for the next call.
            result.put(HINT, "No form yet: confirm=true is not taken with proposals. " + result.get(HINT));
        }
        return plan.withNext(context, build, result);
    }

    // The kinds to classify: those the draft finds that the documentation (or the user) hasn't settled.
    private Map<String, Object> listed(final ToolCall call,
                                       final ToolCallContext context,
                                       final String build,
                                       final String source,
                                       final Map<String, Object> notesNow) {
        List<Long> streams = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        if (streams.isEmpty()) {
            streams = plan.rawSampleStreams(build, MAX_SAMPLE_STREAMS);
        }
        if (streams.isEmpty()) {
            throw new ToolRefusal("No sample streams: upload the samples first, or give stream_ids");
        }
        final Map<String, String> samples = sampleStreamTexts.read(streams).texts();
        SplitterSpec splitter = BuildTranslationXsltTool.Request.splitter(rawArg(call, SPLITTER_ARG));
        String readWith = null;
        if (splitter == null) {
            // The build's saved Data Splitter, where it has one (seen in chat 163: called without splitter six times,
            // the records read with a spec inferred afresh, two giant catch-alls each time, then called again with
            // the spec).
            final Optional<Map.Entry<String, String>> saved = builds.dataSplitter(build, null);
            if (saved.isPresent()) {
                splitter = SplitterSpec.ofConverter(saved.get().getValue());
                readWith = saved.get().getKey();
            }
        }
        final Map<String, Object> draft = DraftMapping.draft(List.copyOf(samples.values()), source, null, null, null,
                null, splitter);
        @SuppressWarnings("unchecked") final Map<String, Object> mapping = (Map<String, Object>) draft.get(MAPPING);
        final List<Map<String, Object>> records = unpacked(DraftMapping.records(samples, splitter).records(),
                mapping);
        final List<Kind> kinds = kinds(draft, records);
        final String variantField = variantField(draft, records);
        final List<String> recordFields = recordFields(records);
        final Set<String> settled = settled(notesNow);
        // Not those the user rejected: the agent asks what they are, and they stay out of later rounds.
        final List<String> rejected = JsonValues.strings(notesNow.get(RecordSourceNotesTool.REJECTED));
        // Nor those the user said to leave untranslated (seen in chat 163: a kind the user said to drop put to them
        // four times).
        final List<String> dropped = JsonValues.strings(notesNow.get(RecordSourceNotesTool.DROPPED));
        final List<Kind> todo = kinds.stream()
                .filter(kind -> !settled.contains(kind.key()) && !rejected.contains(kind.key())
                                && !dropped.contains(kind.key()))
                .toList();
        final List<Kind> batch = todo.subList(0, Math.min(BATCH, todo.size()));
        final Map<String, Object> result = new LinkedHashMap<>();
        if (readWith != null) {
            result.put("read_with", "the build's Data Splitter '" + readWith + "'");
        }
        result.put("kinds_found", kinds.size());
        result.put("already_settled", kinds.stream().map(Kind::key).filter(settled::contains).toList());
        final Optional<String> freeText = kinds.size() <= 1
                ? freeTextField(records, variantField)
                : Optional.empty();
        if (freeText.isPresent()) {
            // None told apart, yet the records say what happened in free text: likely several kinds, which the user
            // confirms once a field names them (seen in chat 162: a splitter reading timestamp, host and message, so
            // "nothing to classify", the step done, and some 25 rules written by hand, none put to the user).
            final Optional<Map<String, Object>> notTold = oneKind(call, context, result, freeText.get());
            if (notTold.isPresent()) {
                return notTold.get();
            }
        }
        if (kinds.size() <= 1 || todo.isEmpty()) {
            // Nothing for the user to decide: one kind (the draft's own rule, confirmed by the user when its records
            // are free text), or every kind settled already.
            result.put(SOURCE_NOTES, notes.recordClassified(context, build, source, List.of(), List.of(),
                    Map.of(RecordSourceNotesTool.RECORD_FIELDS, recordFields, RecordSourceNotesTool.PROPOSED,
                            List.of())));
            result.put(TO_CLASSIFY, List.of());
            result.put(HINT, kinds.size() <= 1
                    ? "One kind of event (or none told apart): nothing to classify. Go on to the draft."
                    : "Every kind is settled by the documentation or the user: go on to the draft.");
            return result;
        }
        // Each kind's first record, shown with its proposal.
        final List<Map<String, Object>> kindSamples = batch.stream()
                .filter(kind -> kind.first() != null)
                .map(kind -> Map.<String, Object>of(KEY, kind.key(), SAMPLE, recordText(kind, samples, variantField),
                        CATCH_ALL, kind.catchAll(), RECORDS, kind.records()))
                .toList();
        result.put(SOURCE_NOTES, notes.recordClassified(context, build, source, List.of(),
                todo.stream().map(Kind::key).toList(), Map.of(RecordSourceNotesTool.RECORD_FIELDS, recordFields,
                        RecordSourceNotesTool.PROPOSED, List.of(), SAMPLES, kindSamples)));
        result.put(TO_CLASSIFY, batch.stream().map(Kind::shown).toList());
        if (todo.size() > batch.size()) {
            result.put("round", batch.size() + " of the " + todo.size() + " kinds still to classify; the next round "
                                + "lists the others");
        }
        result.put("record_fields", recordFields);
        final List<String> users = userFields(records, recordFields);
        result.put("user_fields", users.isEmpty()
                ? "None: the records name no user or account, so leave actor out."
                : users);
        result.put(HINT, "Propose a classification for each of these " + batch.size() + " kinds (proposals=, one "
                           + "each, with its field and value as listed, a short title and the product it comes "
                           + "from), from its records and what you know of the source, saying how sure you are; the "
                           + "user confirms them a set at a time: high confidence, then medium, then low. A kind "
                           + "marked catch_all is a whole format's records, not one event: at most medium."
                           + (batch.stream().anyMatch(Kind::catchAll)
                ? " A catch_all kind may hold several kinds of event (seen: a syslog format's records, the "
                  + "product's event names left inside the message): if its records name their event, refine the "
                  + "splitter (build_data_splitter) so a field holds the name, save it, and list again, so the "
                  + "user confirms each kind; else propose it as one."
                : ""));
        return result;
    }

    // The proposals: the user's own recorded as they said them; the agent's kept for the chat (the table, which a
    // form can't show: the chat renders Markdown, a form's fields plain text) and the short form that follows.
    private Map<String, Object> proposed(final ToolCallContext context,
                                         final String build,
                                         final String source,
                                         final Map<String, Object> notesNow,
                                         final List<Map<?, ?>> given) {
        final List<String> pending = pending(notesNow);
        final List<String> recordFields = JsonValues.strings(notesNow.get(RecordSourceNotesTool.RECORD_FIELDS));
        final List<Proposal> proposals = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        final List<String> rated = new ArrayList<>();
        final List<String> unknownFields = new ArrayList<>();
        final List<String> drops = new ArrayList<>();
        for (int i = 0; i < given.size(); i++) {
            final Map<?, ?> item = given.get(i);
            final String key = ToolText.str(item.get(FIELD)) + "=" + ToolText.str(item.get(VALUE));
            final boolean fromUser = isTrue(item.get(FROM_USER));
            if (isTrue(item.get(DROP))) {
                if (fromUser) {
                    drops.add(key);
                } else {
                    problems.add("proposals[" + i + "]: drop is only for a kind the user said to leave untranslated "
                                 + "(with from_user: true)");
                }
                continue;
            }
            final String stated = valueText(item.get(CONFIDENCE));
            // A catch-all kind (a format's records, the events in it not told apart) is shown to the user on its
            // own: one classification for many events is a guess, however sure the agent is (asked for by the
            // user, after the agent rated catch-alls high in testing).
            final boolean lowered = !fromUser && HIGH.equals(stated) && catchAll(notesNow, key);
            final String confidence = lowered
                    ? MEDIUM
                    : stated;
            if (lowered) {
                rated.add(key);
            }
            if (!fromUser && !pending.contains(key)) {
                problems.add("proposals[" + i + "]: " + key + " isn't a kind still to classify (" + String.join(
                        ", ", pending) + ")");
            } else if (!fromUser && !CONFIDENCES.contains(confidence)) {
                problems.add("proposals[" + i + "]: confidence is high, medium or low");
            } else if (!fromUser && ToolText.isBlank(valueText(item.get(EVENT_DETAIL)))) {
                problems.add("proposals[" + i + "]: give event_detail, the action element");
            } else if (!fromUser && catchAll(notesNow, key) && UNKNOWN.equalsIgnoreCase(valueText(item.get(
                    EVENT_DETAIL)))) {
                // Seen in chat 160: the agent proposed a catch-all as Unknown, leaving the events in it (a
                // gateway's logons, a host's sudo commands) for the mapping, never put to the user.
                problems.add("proposals[" + i + "]: " + key + " is a catch_all kind (a format's records, the "
                             + "events in it not told apart), so not Unknown: if its records name their event, "
                             + "refine the splitter (build_data_splitter) so a field holds the name, save it, and "
                             + "list again; else classify it as the one kind of event it is");
            } else if (unknownField(item, ACTOR, recordFields) || unknownField(item, AFFECTED, recordFields)) {
                // Seen in the agent eval: a label from the text ('By User') given for a field, the event's user
                // then drafted empty.
                unknownFields.add("proposals[" + i + "]");
            } else {
                proposals.add(new Proposal(key, item, confidence, fromUser));
            }
        }
        if (!unknownFields.isEmpty()) {
            // The fields listed once, not for each proposal (seen in chat 161: some 80 fields, three times over).
            problems.add(String.join(", ", unknownFields) + ": actor and affected name fields of the records, as "
                         + "listed (" + String.join(", ", recordFields) + "), or are left out");
        }
        if (!problems.isEmpty()) {
            throw new ToolRefusal("Nothing recorded: " + String.join("; ", problems));
        }
        // Each checked as a catalogue entry before anything is kept or shown.
        final List<Map<String, Object>> checked = notes.checkedEvents(context, build,
                proposals.stream().map(Proposal::entry).toList(), PROPOSALS_ARG);
        final List<Map<String, Object>> fromUser = new ArrayList<>();
        final List<Map<String, Object>> toConfirm = new ArrayList<>();
        for (int i = 0; i < proposals.size(); i++) {
            final Map<String, Object> entry = new LinkedHashMap<>(checked.get(i));
            if (proposals.get(i).fromUser()) {
                fromUser.add(confirmedEntry(entry));
            } else {
                entry.put(KEY, proposals.get(i).key());
                entry.put(CONFIDENCE, proposals.get(i).confidence());
                for (final String shown : List.of(TITLE, PRODUCT)) {
                    final Object value = proposals.get(i).given().get(shown);
                    if (value != null && !ToolText.isBlank(ToolText.str(value))) {
                        entry.put(shown, ToolText.str(value));
                    }
                }
                final String key = proposals.get(i).key();
                JsonValues.maps(notesNow.get(SAMPLES)).stream()
                        .filter(sample -> key.equals(sample.get(KEY)))
                        .findFirst()
                        .ifPresent(sample -> {
                            entry.put(SAMPLE, sample.get(SAMPLE));
                            if (Boolean.TRUE.equals(sample.get(CATCH_ALL))) {
                                entry.put(CATCH_ALL, true);
                                entry.put(RECORDS, sample.get(RECORDS));
                            }
                        });
                toConfirm.add(entry);
            }
        }
        // High confidence first, then medium, then low: the order the sets are put to the user; each with its place
        // among those of its confidence, for the heading's range (asked for by the user: "1-10 of 21").
        toConfirm.sort(Comparator.comparingInt(entry -> CONFIDENCES
                .indexOf(String.valueOf(entry.get(CONFIDENCE)))));
        for (final String confidence : CONFIDENCES) {
            final List<Map<String, Object>> ofConfidence = toConfirm.stream()
                    .filter(entry -> confidence.equals(entry.get(CONFIDENCE)))
                    .toList();
            for (int i = 0; i < ofConfidence.size(); i++) {
                ofConfidence.get(i).put(POSITION, i + 1);
                ofConfidence.get(i).put(OF, ofConfidence.size());
            }
        }
        final Set<String> recorded = new LinkedHashSet<>();
        fromUser.forEach(entry -> recorded.add(entry.get(FIELD) + "=" + entry.get(VALUE)));
        final List<String> left = pending.stream()
                .filter(key -> !recorded.contains(key) && !drops.contains(key))
                .toList();
        // Only the user's (a correction between the rounds, say): the proposals still to confirm stay.
        final List<Map<String, Object>> kept = toConfirm.isEmpty()
                ? JsonValues.maps(notesNow.get(RecordSourceNotesTool.PROPOSED)).stream()
                        .filter(entry -> !recorded.contains(String.valueOf(entry.get(KEY)))
                                         && !drops.contains(String.valueOf(entry.get(KEY))))
                        .toList()
                : toConfirm;
        final Set<String> allDropped = new LinkedHashSet<>(JsonValues.strings(notesNow.get(
                RecordSourceNotesTool.DROPPED)));
        allDropped.addAll(drops);
        final Map<String, Object> doc = notes.recordClassified(context, build, source, fromUser, left,
                Map.of(RecordSourceNotesTool.PROPOSED, kept, RecordSourceNotesTool.DROPPED, List.copyOf(allDropped)));
        final Map<String, Object> result = new LinkedHashMap<>();
        if (!recorded.isEmpty()) {
            result.put(RECORDED, List.copyOf(recorded));
        }
        if (!drops.isEmpty()) {
            result.put("dropped", drops);
            result.put("dropped_note", "Left untranslated, as the user said: settled, so not listed again. Give "
                                       + "each a drop rule in the mapping (drop_when, with the user's reason).");
        }
        result.put(SOURCE_NOTES, doc);
        if (!rated.isEmpty()) {
            result.put("rated_medium", rated);
            result.put("rated_medium_why", "Catch-all kinds (a format's records, its events not told apart): "
                                           + "shown in detail, with the medium-confidence ones.");
        }
        if (toConfirm.isEmpty()) {
            if (!kept.isEmpty()) {
                result.put("still_to_confirm", kept.stream().map(entry -> entry.get(KEY)).toList());
                presented(result, build, firstSet(kept), context.session().isChat());
                return result;
            }
            if (!left.isEmpty()) {
                result.put(STILL_TO_CLASSIFY, left);
            }
            result.put(HINT, hint(left));
            return result;
        }
        presented(result, build, firstSet(toConfirm), context.session().isChat());
        return result;
    }

    // The proposals kept, put to the user a set at a time, high confidence first, then medium, then low (asked for
    // by the user): the set's table shown in the chat, numbered from 1, then a short form of plain text with one
    // question, accept them all (a button) or amend them (in the user's own words), one or the other.
    private Map<String, Object> confirmed(final ToolCall call,
                                          final ToolCallContext context,
                                          final String build,
                                          final String source,
                                          final Map<String, Object> notesNow) {
        final List<Map<String, Object>> proposed = JsonValues.maps(notesNow.get(RecordSourceNotesTool.PROPOSED));
        if (proposed.isEmpty()) {
            throw new ToolRefusal("Nothing to confirm: send the proposals first (proposals=), and show what comes "
                                  + "back in the chat");
        }
        final List<Map<String, Object>> set = firstSet(proposed);
        final String confidence = confidenceOf(set);
        final String accept = set.size() == 1
                ? "Accept it"
                : "Accept all " + set.size();
        final Optional<String> answer = consent.answer(call, context, NAME,
                "Review the " + confidence + "-confidence classifications shown in the chat.",
                accept + ", or type your amendments (e.g. 2 is Access, not Alert)", accept);
        if (answer.isEmpty()) {
            return noForms();
        }
        if (accepting(answer.get(), accept)) {
            return recorded(context, build, source, notesNow, proposed, set);
        }
        // Amended: nothing recorded yet; the agent sends them again as the user said, and the next set follows.
        // The set leaves the proposals now, so one the user rejected (left out by the agent, which asks what it is)
        // isn't put to them again, and stays still to classify.
        final Set<String> amended = new LinkedHashSet<>();
        set.forEach(entry -> amended.add(String.valueOf(entry.get(KEY))));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(SOURCE_NOTES, notes.recordClassified(context, build, source, List.of(), pending(notesNow),
                Map.of(RecordSourceNotesTool.PROPOSED, proposed.stream()
                        .filter(entry -> !amended.contains(String.valueOf(entry.get(KEY))))
                        .toList())));
        result.put(RECORDED, List.of());
        result.put(AMENDMENTS, answer.get());
        result.put(HINT, "The user amended the " + confidence + "-confidence classifications: send "
                         + (set.size() == 1
                ? "it"
                : "all " + set.size() + " of them") + " again with the amendments applied, as proposals with "
                         + "from_user: true (ask the user first if an amendment isn't clear; leave out any they "
                         + "reject and ask what it is; one they say to drop, send with drop: true); the next set "
                         + "then follows.");
        return result;
    }

    // Whether the answer accepts: the button's, or the user's own words saying the same.
    private static boolean accepting(final String answer, final String accept) {
        return answer.equalsIgnoreCase(accept)
               || answer.matches("(?i)(accept( all| them| it)?|yes|ok|okay|agreed?|confirm(ed)?)[.!]?");
    }

    private static Map<String, Object> noForms() {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("asked", false);
        result.put(HINT, "This client shows no forms: ask the user in the chat whether what you showed is "
                         + "right, then send what they confirmed or corrected as proposals with from_user: "
                         + "true; leave out any they reject and ask what it is.");
        return result;
    }

    // The set the user accepted recorded, and the next set presented.
    private Map<String, Object> recorded(final ToolCallContext context,
                                         final String build,
                                         final String source,
                                         final Map<String, Object> notesNow,
                                         final List<Map<String, Object>> proposed,
                                         final List<Map<String, Object>> set) {
        final Set<String> accepted = new LinkedHashSet<>();
        set.forEach(entry -> accepted.add(String.valueOf(entry.get(KEY))));
        final List<Map<String, Object>> confirmedEntries = set.stream()
                .map(entry -> confirmedEntry(new LinkedHashMap<>(entry)))
                .toList();
        final List<Map<String, Object>> remaining = proposed.stream()
                .filter(entry -> !accepted.contains(String.valueOf(entry.get(KEY))))
                .toList();
        final List<String> left = pending(notesNow).stream()
                .filter(key -> !accepted.contains(key))
                .toList();
        final Map<String, Object> doc = notes.recordClassified(context, build, source, confirmedEntries, left,
                Map.of(RecordSourceNotesTool.PROPOSED, remaining));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(RECORDED, List.copyOf(accepted));
        result.put(SOURCE_NOTES, doc);
        if (!remaining.isEmpty()) {
            presented(result, build, firstSet(remaining), context.session().isChat());
            return result;
        }
        if (!left.isEmpty()) {
            result.put(STILL_TO_CLASSIFY, left.size());
        }
        result.put(HINT, hint(left));
        return result;
    }

    // What the user is shown before the form: a set of proposals of one confidence, the high-confidence ones as one
    // table, the others in detail, with what to call next. Stroom AI's chat shows the table itself; another client's
    // agent shows it.
    private static void presented(final Map<String, Object> result,
                                  final String build,
                                  final List<Map<String, Object>> set,
                                  final boolean shownByChat) {
        final String confidence = confidenceOf(set);
        // Shown to the user as it is (seen in testing: a set's table saved to the workspace, then retyped by the
        // agent, shortened and restyled, or never shown at all): in Stroom AI's chat, by the chat itself; elsewhere,
        // the result in full however long, for the agent to show.
        result.put(STATUS, NEEDS_SHOWING);
        result.put(TABLE, HIGH.equals(confidence)
                ? summary(set)
                : details(set));
        final String form = "call classify_kinds build=" + build + " confirm=true: a short form asks them to accept "
                            + "all the " + confidence + "-confidence classifications or amend them.";
        result.put(HINT, shownByChat
                ? "The chat shows the user this table itself, as it is: don't repeat, restyle or describe it, nor "
                  + "any others to come. Now " + form
                : "Show the table to the user in your reply, as it is, whole (Markdown the chat renders: don't "
                  + "shorten, restyle or summarise it), then " + form + " Show only this table: any others are "
                  + "shown after the form, so don't describe them now.");
    }

    // The set put to the user next (asked for by the user): up to ten proposals of the first confidence they have
    // (high, then medium, then low), numbered from 1, as the table shows them and the user's amendments name them.
    private static List<Map<String, Object>> firstSet(final List<Map<String, Object>> entries) {
        for (final String confidence : CONFIDENCES) {
            final List<Map<String, Object>> ofConfidence = entries.stream()
                    .filter(entry -> confidence.equals(entry.get(CONFIDENCE)))
                    .limit(SET_SIZE)
                    .toList();
            if (!ofConfidence.isEmpty()) {
                final List<Map<String, Object>> set = new ArrayList<>();
                for (int i = 0; i < ofConfidence.size(); i++) {
                    final Map<String, Object> numbered = new LinkedHashMap<>(ofConfidence.get(i));
                    numbered.put(NUMBER, i + 1);
                    set.add(numbered);
                }
                return set;
            }
        }
        return entries;
    }

    private static String confidenceOf(final List<Map<String, Object>> set) {
        return String.valueOf(set.getFirst().get(CONFIDENCE));
    }

    // Whether the kind was listed as a catch-all: a format's records, the events in it not told apart.
    private static boolean catchAll(final Map<String, Object> notesNow, final String key) {
        return JsonValues.maps(notesNow.get(SAMPLES)).stream()
                .anyMatch(sample -> key.equals(sample.get(KEY)) && Boolean.TRUE.equals(sample.get(CATCH_ALL)));
    }

    // A boolean argument given as true or "true".
    private static boolean isTrue(final Object given) {
        return Boolean.TRUE.equals(given) || "true".equals(String.valueOf(given));
    }

    private boolean confirming(final ToolCall call) {
        final Object given = argsMap(call).get(CONFIRM_ARG);
        return isTrue(given);
    }

    private static List<String> pending(final Map<String, Object> notesNow) {
        if (!notesNow.containsKey(RecordSourceNotesTool.PENDING)) {
            throw new ToolRefusal("Call classify_kinds without proposals first: it lists the kinds to classify");
        }
        return JsonValues.strings(notesNow.get(RecordSourceNotesTool.PENDING));
    }

    // An entry as recorded once the user confirmed it.
    private static Map<String, Object> confirmedEntry(final Map<String, Object> entry) {
        entry.remove(KEY);
        entry.remove(CONFIDENCE);
        entry.remove(TITLE);
        entry.remove(PRODUCT);
        entry.remove(SAMPLE);
        entry.remove(NUMBER);
        entry.remove(POSITION);
        entry.remove(OF);
        entry.remove(CATCH_ALL);
        entry.remove(RECORDS);
        entry.put(BASIS, RecordSourceNotesTool.USER_CONFIRMED);
        return entry;
    }

    // Whether a proposal names, for the key, a field the records don't have (when their fields are known).
    private static boolean unknownField(final Map<?, ?> item, final String key, final List<String> recordFields) {
        final Object named = item.get(key);
        return named != null && !ToolText.isBlank(ToolText.str(named)) && !recordFields.isEmpty()
               && !recordFields.contains(ToolText.str(named));
    }

    // The fields the records hold, in the order first seen.
    private static List<String> recordFields(final List<Map<String, Object>> records) {
        final Set<String> seen = new LinkedHashSet<>();
        records.forEach(record -> record.forEach((field, value) -> {
            if (named(field) && value != null && !ToolText.isBlank(ToolText.str(value))) {
                seen.add(field);
            }
        }));
        return List.copyOf(seen);
    }

    // Not an inner XML element's 'anywhere below' alias (message//User beside message/Event/User): the draft
    // matches with it, but the same value is shown and named by its path.
    private static boolean named(final String field) {
        return !field.contains("//");
    }

    // The fields whose names say user or account and whose values aren't addresses: who may have acted.
    private static List<String> userFields(final List<Map<String, Object>> records, final List<String> fields) {
        return fields.stream()
                .filter(field -> USER_NAME.matcher(field).find())
                .filter(field -> records.stream()
                        .map(record -> record.get(field))
                        .filter(value -> value != null && !ToolText.isBlank(ToolText.str(value)))
                        .anyMatch(value -> !IpAddresses.isAddress(ToolText.str(value))))
                .toList();
    }

    private static String hint(final List<String> left) {
        return left.isEmpty()
                ? "Every kind is classified: draft_translation_mapping build=... follows them, a rule a kind."
                : "Call classify_kinds without proposals for the next round of the " + left.size() + " kinds still "
                  + "to classify.";
    }

    // The kinds the draft makes rules for, each with its records.
    private static List<Kind> kinds(final Map<String, Object> draft, final List<Map<String, Object>> records) {
        final List<Kind> kinds = new ArrayList<>();
        @SuppressWarnings("unchecked") final Map<String, Object> mapping = (Map<String, Object>) draft.get(MAPPING);
        final String variantField = variantField(draft, records);
        final List<List<Map<String, Object>>> earlierRules = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get(EVENTS))) {
            final List<Map<String, Object>> when = JsonValues.maps(rule.get("when"));
            final List<Map<String, Object>> equals = when.stream()
                    .filter(c -> c.get(FIELD) != null && c.get(EQUALS) != null)
                    .toList();
            if (equals.isEmpty()) {
                continue;
            }
            // The kind is told by its last condition (a variant's own condition comes first).
            final Map<String, Object> told = equals.getLast();
            // Told by its format alone. Its format's 'other' (after the format's own kinds) has the records they
            // leave; none left, it is the rule for events the sample doesn't have, as a single format's is, and not
            // listed.
            final boolean byFormat = equals.size() == 1 && told.get(FIELD).equals(variantField);
            final boolean leftovers = byFormat && earlierRules.stream()
                    .anyMatch(earlierRule -> earlierRule.size() > 1 && sameCondition(earlierRule.getFirst(), told));
            final List<Map<String, Object>> ofKind = records.stream()
                    .filter(record -> matches(record, equals))
                    .filter(record -> !byFormat || earlierRules.stream().noneMatch(rule1 -> matches(record, rule1)))
                    .toList();
            earlierRules.add(equals);
            // A catch-all, its events not told apart: a format's leftovers, or a whole format whose records say what
            // happened in free text. Not a format the splitter reads into fields of one event (seen in chat 161:
            // the agent's sshd_failed_password and sshd_accepted variants, each one kind, rated medium as
            // catch-alls).
            final boolean catchAll = byFormat && (leftovers || freeText(ofKind, variantField));
            if (catchAll && ofKind.isEmpty()) {
                continue;
            }
            final Kind kind = new Kind(String.valueOf(told.get(FIELD)), String.valueOf(told.get(EQUALS)),
                    String.valueOf(rule.get("name")), ofKind.size(), examples(ofKind), catchAll, ofKind.isEmpty()
                    ? null
                    : ofKind.getFirst());
            // One kind in several rules (a rule a variant): listed once, its records counted together.
            final Kind earlier = kinds.stream().filter(k -> k.key().equals(kind.key())).findFirst().orElse(null);
            if (earlier == null) {
                kinds.add(kind);
            } else {
                final List<Map<String, Object>> both = new ArrayList<>(earlier.examples());
                kind.examples().stream().filter(e -> !both.contains(e) && both.size() < EXAMPLES).forEach(both::add);
                kinds.set(kinds.indexOf(earlier), new Kind(earlier.field(), earlier.value(), earlier.rule(),
                        earlier.records() + kind.records(), both, earlier.catchAll() && kind.catchAll(),
                        earlier.first()));
            }
        }
        return kinds;
    }

    // The records with the fields the draft reads inside an inner message: the keys of JSON text (json_fields),
    // the elements of XML text (xml_fields) and the values its extractions take from text (key/value pairs, say),
    // as the translation reads them. Seen in testing: kinds told apart inside a message field listed with no
    // records or sample, and no user field to name as the actor.
    private static List<Map<String, Object>> unpacked(final List<Map<String, Object>> records,
                                                      final Map<String, Object> mapping) {
        final List<Object> held = new ArrayList<>(records);
        LocalCheck.withJsonFields(held, JsonValues.strings(mapping.get("json_fields")));
        LocalCheck.withXmlFields(held, JsonValues.strings(mapping.get("xml_fields")));
        if (JsonValues.maps(mapping.get("extract")).isEmpty()) {
            return records;
        }
        final TranslationMapping read;
        try {
            read = TranslationMapping.fromJson(mapping);
        } catch (final RuntimeException e) {
            LOGGER.debug("The draft's extractions can't be read: {}", e.getMessage());
            return records;
        }
        for (final Map<String, Object> record : records) {
            final Map<String, String> extracted = LocalCheck.extractedValues(read, record);
            if (extracted != null) {
                extracted.forEach((name, value) -> {
                    if (!value.isEmpty()) {
                        record.putIfAbsent(name, value);
                    }
                });
            }
        }
        return records;
    }

    private static boolean sameCondition(final Map<String, Object> one, final Map<String, Object> other) {
        return String.valueOf(one.get(FIELD)).equals(String.valueOf(other.get(FIELD)))
               && String.valueOf(one.get(EQUALS)).equals(String.valueOf(other.get(EQUALS)));
    }

    // Whether the records say what happened in free text.
    private static boolean freeText(final List<Map<String, Object>> records, final String variantField) {
        return freeTextField(records, variantField).isPresent();
    }

    // The field in which the records say what happened in free text, if any: a field (not their format or time)
    // whose values are mostly several words, and not all the same.
    private static Optional<String> freeTextField(final List<Map<String, Object>> records, final String variantField) {
        final Set<String> fields = new LinkedHashSet<>();
        records.forEach(record -> fields.addAll(record.keySet()));
        return fields.stream()
                .filter(field -> named(field) && !field.equals(variantField) && !TIME_LIKE.matcher(field).find())
                .filter(field -> {
                    final List<String> values = records.stream()
                            .map(record -> valueText(record.get(field)).strip())
                            .filter(value -> !value.isEmpty())
                            .toList();
                    final long worded = values.stream()
                            .filter(value -> value.split("\\s+").length >= FREE_TEXT_WORDS)
                            .count();
                    return !values.isEmpty() && worded * 2 >= values.size()
                           && values.stream().distinct().count() > 1;
                })
                .findFirst();
    }

    // Records not told apart into kinds whose text says what happened: put back to the agent, to refine the splitter
    // so a field names each kind; or, with one_kind, put to the user, who confirms they are one kind of event (the
    // listing then goes on, nothing to classify) or says what kinds they hold.
    private Optional<Map<String, Object>> oneKind(final ToolCall call,
                                                  final ToolCallContext context,
                                                  final Map<String, Object> result,
                                                  final String field) {
        result.put(TO_CLASSIFY, List.of());
        if (!isTrue(argsMap(call).get(ONE_KIND_ARG))) {
            result.put(HINT, "No kinds told apart, yet the records say what happened in free text ('" + field + "'), "
                             + "so they likely hold several kinds of event: refine the splitter (build_data_splitter) "
                             + "so a field names each record's event (the survey's shapes show them), save it, and "
                             + "call classify_kinds again with it. If they truly are one kind of event, call it with "
                             + "one_kind=true: the user confirms it.");
            return Optional.of(result);
        }
        final Optional<String> answer = consent.answer(call, context, NAME,
                "The records aren't told apart into kinds of event; their text ('" + field + "') says what happened.",
                "Treat them all as one kind of event, or type the kinds they hold", ONE_KIND);
        if (answer.isEmpty() || accepting(answer.get(), ONE_KIND)) {
            // One kind, as the user confirmed (or, without forms, as they said in the chat when asked).
            return Optional.empty();
        }
        result.put("user_says", answer.get());
        result.put(HINT, "The user says the records hold these kinds: refine the splitter (build_data_splitter) so a "
                         + "field names each, save it, and call classify_kinds again with it.");
        return Optional.of(result);
    }

    // Whether the record meets each of the conditions.
    private static boolean matches(final Map<String, Object> record, final List<Map<String, Object>> conditions) {
        return conditions.stream().allMatch(c -> String.valueOf(c.get(EQUALS))
                .equals(String.valueOf(record.get(String.valueOf(c.get(FIELD))))));
    }

    // The field a record's format (its variant) is written in, when the records are in several; else none. A spec
    // the draft can't read back (a saved Data Splitter's, read by Stroom's own parser; seen in the e2e suite, the
    // call failed on it): the field a converter build_data_splitter wrote names the variant in, where the records
    // have it.
    @SuppressWarnings("unchecked")
    private static String variantField(final Map<String, Object> draft, final List<Map<String, Object>> records) {
        if (draft.get("splitter") instanceof final Map<?, ?> spec) {
            try {
                final SplitterSpec read = SplitterSpec.fromMap((Map<String, ?>) spec);
                return NullSafe.isEmptyCollection(read.parts())
                        ? null
                        : read.variantField();
            } catch (final IllegalArgumentException e) {
                return records.stream().anyMatch(record -> record.containsKey(SplitterSpec.VARIANT))
                        ? SplitterSpec.VARIANT
                        : null;
            }
        }
        return null;
    }

    // Up to three of the kind's records whose values differ, each cut short.
    private static List<Map<String, Object>> examples(final List<Map<String, Object>> records) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map<String, Object> record : records) {
            final Map<String, Object> shown = new LinkedHashMap<>();
            record.forEach((field, value) -> {
                final String text = valueText(value);
                if (named(field) && !text.isBlank() && shown.size() < EXAMPLE_FIELDS) {
                    shown.put(field, text.length() <= EXAMPLE_VALUE
                            ? text
                            : text.substring(0, EXAMPLE_VALUE) + "...");
                }
            });
            if (!out.contains(shown)) {
                out.add(shown);
            }
            if (out.size() == EXAMPLES) {
                break;
            }
        }
        return out;
    }

    // The kinds the notes settle: catalogue events the documentation gave, or the user confirmed, by field=value.
    private static Set<String> settled(final Map<String, Object> notesNow) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Map<String, Object> event : JsonValues.maps(notesNow.get(EVENTS))) {
            if (event.get(FIELD) != null && event.get(VALUE) != null
                && !RecordSourceNotesTool.AGENT_KNOWLEDGE.equals(event.get(BASIS))) {
                out.add(event.get(FIELD) + "=" + event.get(VALUE));
            }
        }
        return out;
    }

    // The proposals for the agent to show in the chat, one after another (asked for by the user: one wide table
    // of long descriptions was hard to read): a numbered heading for each, '[product] title', then a table of two
    // columns, the row's name and its value, with a row only where the proposal has one.
    static String details(final List<Map<String, Object>> entries) {
        final List<String> lines = new ArrayList<>();
        String group = null;
        for (final Map<String, Object> e : entries) {
            // Grouped by confidence, under a heading as the high-confidence ones are (asked for by the user).
            final String confidence = text(e.get(CONFIDENCE));
            if (!confidence.equals(group)) {
                group = confidence;
                final String shown = confidence;
                lines.add(heading(confidence, entries.stream()
                        .filter(entry -> shown.equals(text(entry.get(CONFIDENCE))))
                        .toList()));
                lines.add("");
            }
            lines.add("### " + e.get(NUMBER) + ". " + (ToolText.isBlank(text(e.get(PRODUCT)))
                    ? ""
                    : "[" + text(e.get(PRODUCT)) + "] ") + title(e));
            lines.add("");
            if (e.get(SAMPLE) != null) {
                // Preformatted (asked for by the user).
                lines.add("```");
                lines.add(ToolText.str(e.get(SAMPLE)).replace("```", "'''"));
                lines.add("```");
                lines.add("");
            }
            // What it says, a row each in a table of Element and Value (asked for by the user; the headings name the
            // kind and its confidence): the action element always, the others where the proposal has them.
            lines.add("| Element | Value |");
            lines.add("| --- | --- |");
            if (Boolean.TRUE.equals(e.get(CATCH_ALL))) {
                // What a catch-all covers, so one example's meaning isn't confirmed for a whole format (seen in chat
                // 163: a format's 200 records confirmed from one line, as one kind).
                item(lines, "Covers", "Every '" + text(e.get(VALUE)) + "' record no other kind takes"
                                      + (e.get(RECORDS) instanceof final Number count
                        ? " (" + StreamReader.thousands(count.longValue()) + " records)"
                        : ""));
            }
            item(lines, "Schema action", e.get(EVENT_DETAIL));
            item(lines, "Action", e.get(ACTION));
            item(lines, "Outcome", outcome(e));
            item(lines, "Type id", e.get(TYPE_ID));
            item(lines, "Who acted", e.get(ACTOR));
            item(lines, "Acted on", e.get(AFFECTED) == null
                    ? null
                    : text(e.get(AFFECTED)) + (e.get(AFFECTED_AS) == null
                            ? ""
                            : " (" + text(e.get(AFFECTED_AS)) + ")"));
            lines.add("");
        }
        return String.join("\n", lines).strip();
    }

    // The high-confidence proposals as one table: their number, the product (Rule), the kind's title and its
    // mapping (asked for by the user).
    static String summary(final List<Map<String, Object>> entries) {
        final List<String> lines = new ArrayList<>(List.of(heading(HIGH, entries), "",
                "| # | Rule | Kind | Mapping |", "| --- | --- | --- | --- |"));
        for (final Map<String, Object> e : entries) {
            lines.add("| " + e.get(NUMBER) + " | " + cell(text(e.get(PRODUCT))) + " | " + cell(title(e)) + " | "
                      + cell(mapping(e)) + " |");
        }
        return String.join("\n", lines);
    }

    // A set's heading: its confidence, and which of those of its confidence it shows (asked for by the user), e.g.
    // "## Medium-confidence classifications (1-10 of 21)".
    private static String heading(final String confidence, final List<Map<String, Object>> set) {
        final String title = "## " + Character.toUpperCase(confidence.charAt(0)) + confidence.substring(1)
                             + "-confidence classifications";
        // Numbers as kept in the notes (read back from JSON) or as just numbered.
        final List<Integer> positions = set.stream()
                .map(entry -> entry.get(POSITION))
                .filter(Number.class::isInstance)
                .map(position -> ((Number) position).intValue())
                .toList();
        if (positions.isEmpty() || !(set.getFirst().get(OF) instanceof final Number of)) {
            return title;
        }
        final int first = positions.stream().mapToInt(Integer::intValue).min().orElse(1);
        final int last = positions.stream().mapToInt(Integer::intValue).max().orElse(first);
        return title + " (" + (first == last
                ? String.valueOf(first)
                : first + "-" + last) + " of " + of.intValue() + ")";
    }

    // A proposal's mapping in short: its action element, Action and outcome, e.g. "Authenticate: Logon (Failure)".
    // Seen in testing: failed and successful logons both shown as "Authenticate: Logon" in the table accepted
    // together.
    private static String mapping(final Map<String, Object> e) {
        final String outcome = outcome(e);
        return text(e.get(EVENT_DETAIL)) + (e.get(ACTION) == null
                ? ""
                : ": " + text(e.get(ACTION))) + (outcome == null
                ? ""
                : " (" + outcome + ")");
    }

    // A proposal's outcome, where it says one.
    private static String outcome(final Map<String, Object> e) {
        return e.get(SUCCESS) == null
                ? null
                : JsonValues.truthy(e.get(SUCCESS))
                        ? "Success"
                        : "Failure";
    }

    // A proposal's short name: its title, else the start of its description.
    private static String title(final Map<String, Object> e) {
        return ToolText.isBlank(text(e.get(TITLE)))
                ? cut(text(e.get(DESCRIPTION)), 70)
                : text(e.get(TITLE));
    }

    // One of what a proposal says, a row of its table, only where it has a value.
    private static void item(final List<String> lines, final String name, final Object value) {
        if (value != null && !ToolText.isBlank(ToolText.str(value))) {
            lines.add("| " + name + " | " + cell(value) + " |");
        }
    }

    // A table cell's text: its pipes escaped, on one line.
    private static String cell(final Object value) {
        return ToolText.str(value).replace("|", "\\|").replace("\n", " ");
    }

    private static String cut(final String text, final int most) {
        return text.length() <= most
                ? text
                : text.substring(0, most - 3).strip() + "...";
    }

    // A kind's record as the sample has it, whole (asked for by the user: the record itself, not its fields cut
    // short): the sample's line holding most of the record's values (among those holding its kind's value, but for a
    // catch-all); else its fields, whole, but for its format and time.
    private static String recordText(final Kind kind,
                                     final Map<String, String> samples,
                                     final String variantField) {
        final List<String> values = kind.first().values().stream()
                .map(ClassifyKindsTool::valueText)
                .filter(value -> value.length() >= MIN_MATCHED && !value.contains("\"") && !value.contains("\\")
                                 && !value.contains("\n"))
                .distinct()
                .toList();
        String best = null;
        long bestCount = 0;
        for (final String text : samples.values()) {
            for (final String line : text.split("\n")) {
                // A catch-all's value is its format's name, not in its records' text.
                if (!kind.catchAll() && !line.contains(kind.value())) {
                    continue;
                }
                final long count = values.stream().filter(line::contains).count();
                if (count > bestCount) {
                    best = line;
                    bestCount = count;
                }
            }
        }
        if (best != null && bestCount * 2 >= values.size()) {
            return best.strip();
        }
        final List<String> lines = new ArrayList<>();
        kind.first().forEach((field, value) -> {
            if (named(field) && !field.equals(variantField) && !TIME_FIELD.matcher(field).matches()) {
                lines.add(field + " = " + valueText(value));
            }
        });
        return String.join("\n", lines);
    }

    // A value as text, "" for none.
    private static String valueText(final Object value) {
        return ToolText.str(Objects.requireNonNullElse(value, ""));
    }

    // A value as text for a table cell or form title: "" for none, with no '|' to break a table.
    private static String text(final Object value) {
        return value == null
                ? ""
                : ToolText.str(value).replace("|", "/");
    }


    // --------------------------------------------------------------------------------


    // A kind of event the draft tells apart: its field and value, its rule, how many records, and some of them.
    private record Kind(String field,
                        String value,
                        String rule,
                        int records,
                        List<Map<String, Object>> examples,
                        boolean catchAll,
                        Map<String, Object> first) {

        String key() {
            return field + "=" + value;
        }

        Map<String, Object> shown() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put(FIELD, field);
            out.put(VALUE, value);
            out.put("rule", rule);
            out.put("records", records);
            out.put("examples", examples);
            if (catchAll) {
                out.put(CATCH_ALL, true);
            }
            return out;
        }
    }

    // A proposal as given: its kind, the entry, the agent's confidence, and whether the user said it.
    private record Proposal(String key, Map<?, ?> given, String confidence, boolean fromUser) {

        Map<?, ?> entry() {
            final Map<String, Object> out = new LinkedHashMap<>();
            given.forEach((k, v) -> out.put(String.valueOf(k), v));
            out.putIfAbsent(EVENT, given.get(VALUE));
            out.remove(FROM_USER);
            out.remove(CONFIDENCE);
            return out;
        }
    }
}
