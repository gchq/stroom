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
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// `classify_kinds` - the agent classifies every kind of event in the sample the user's documentation doesn't
/// cover, and the user confirms the classifications in one form, before the translation is drafted (work package
/// E8, every decision the user's).
///
/// Called without proposals, it lists those kinds (found as the draft finds them), each with its count and up to
/// three of its records, and keeps them in the build's source notes as still to classify. Called with proposals (a
/// catalogue entry a kind: its action element, Action, outcome, which field is the actor and which acted on, and how
/// sure the agent is), it puts them to the user: every proposal in a table, those of high and medium confidence
/// accepted together, each low one on its own, and a field for corrections. What the user accepts is recorded in
/// the source notes as `user_confirmed`, which the draft follows (a rule a kind) and the build checks against. A
/// proposal the user rejected is left out (the agent asks what it is); corrections come back for the agent, which
/// sends the corrected entries as the user's (`from_user`), recorded without another form. Without forms, the
/// table comes back for the agent to put to the user in the chat.
///
/// The tool holds no knowledge of any source: the meanings are the agent's and the user's.
class ClassifyKindsTool extends AbstractBuiltInTool {

    static final String NAME = "classify_kinds";
    static final String BUILD_ARG = "build";
    static final String SOURCE_ARG = "source";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String SPLITTER_ARG = "splitter";
    static final String PROPOSALS_ARG = "proposals";
    static final String CONFIRM_ARG = "confirm";
    /// The notes block's key for each kind's sample record (from the listing), shown with its proposal.
    static final String SAMPLES = "samples";

    // Low-confidence proposals each asked on their own in the form; past this many, with the others.
    private static final int MOST_SINGLY = 10;
    private static final int MAX_SAMPLE_STREAMS = 5;
    private static final int EXAMPLES = 3;
    private static final int EXAMPLE_FIELDS = 20;
    private static final int EXAMPLE_VALUE = 100;
    private static final String FROM_USER = "from_user";
    private static final String BLOCK = "block";
    // A kept proposal's kind, as field=value.
    private static final String KEY = "kind";
    private static final int SAMPLE_CHARS = 100;
    // A field's name that says it names a user or account (generic words, no product's).
    private static final Pattern USER_NAME = Pattern.compile("(?i)(user|account|login|principal|subject|actor|"
                                                             + "owner|member|identity|uid)");

    private final RecordSourceNotesTool notes;
    private final SampleStreamTexts sampleStreamTexts;
    private final Consent consent;
    private final BuildPlan plan;

    @Inject
    ClassifyKindsTool(final RecordSourceNotesTool notes,
                      final SampleStreamTexts sampleStreamTexts,
                      final Consent consent,
                      final BuildPlan plan) {
        this.notes = notes;
        this.sampleStreamTexts = sampleStreamTexts;
        this.consent = consent;
        this.plan = plan;
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
                             + "reply. Then call with confirm=true: a short form asks them to accept those you are "
                             + "sure of together and the others one by one, or correct them. What is confirmed goes "
                             + "into the build's source notes, which draft_translation_mapping follows (a rule a "
                             + "kind). Send corrections the user gave (in the form or the chat) as proposals with "
                             + "from_user: true; leave out a kind the user rejected and ask them what it is.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addStringProperty(SOURCE_ARG, "The source's name, as record_source_notes takes it; the "
                                                       + "build's source notes' when it has them, else the build's "
                                                       + "name.")
                        .addProperty(STREAM_IDS_ARG, BuildTranslationXsltTool.streamIdsSchema(
                                "The sample streams; the build's newest when left out."))
                        .addProperty(SPLITTER_ARG, TranslationArgs.object("The Data Splitter spec the text is read "
                                                                          + "with (build_data_splitter's spec), as "
                                                                          + "for draft_translation_mapping."))
                        .addProperty(PROPOSALS_ARG, ArraySchema.builder()
                                .description("One per kind listed: its field and value (as listed), description, "
                                             + "event_detail (the action element, e.g. Authenticate, Update, "
                                             + "Network/Permit), action and success where it has them, type_id, "
                                             + "actor and affected where the records name them (the fields "
                                             + "naming the user who acted, and who or what it was done to; left "
                                             + "out otherwise), affected_as (what that is: User, File, "
                                             + "Document, Group...), and confidence (high, medium or low); or "
                                             + "from_user: true for what the user said.")
                                .items(proposal())
                                .build())
                        .addBooleanProperty(CONFIRM_ARG, "True once the proposals' table is shown in the chat: the "
                                                         + "user confirms or corrects them in a short form.")
                        .required(BUILD_ARG)
                        .build())
                .build();
    }

    private static ObjectSchema proposal() {
        return ObjectSchema.builder()
                .addStringProperty("field", "The field the kind is told by, as listed.")
                .addStringProperty("value", "Its value for this kind, as listed.")
                .addStringProperty("event", "The kind's name or id (the value when left out).")
                .addStringProperty("title", "A short name for the kind, the heading the user reads, e.g. 'SQL "
                                            + "injection signature'.")
                .addStringProperty("product", "The product or part of it the kind comes from, in the heading's "
                                              + "brackets, e.g. 'NetScaler AppFirewall'.")
                .addStringProperty("description", "What the event is.")
                .addStringProperty("event_detail", "The EventDetail action element.")
                .addStringProperty("action", "The action element's Action, when it has one.")
                .addBooleanProperty("success", "The outcome, when the kind says it.")
                .addStringProperty("type_id", "The event's TypeId.")
                .addStringProperty("actor", "The field naming the user or account who acted (one of user_fields); "
                                            + "left out when the records name none, as for network traffic.")
                .addStringProperty("affected", "The field naming the user or object acted on; left out when there "
                                               + "is none, as for an alert or a connection.")
                .addStringProperty("affected_as", "What the affected field names, as the action element's object: "
                                                  + "User, File, Document, Group... (or its leaf, User/Name).")
                .addStringProperty("confidence", "high, medium or low: how sure you are.")
                .addBooleanProperty(FROM_USER, "True for what the user said (recorded without asking again).")
                .required("field", "value", "description")
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
        final Map<String, Object> notesNow = notes.notesOf(context, build);
        // The source notes' name, else the build's (the plan's call gives none).
        final String source = NullSafe.nonBlankStringElse(stringArg(call, SOURCE_ARG),
                NullSafe.nonBlankStringElse(ToolText.str(notesNow.getOrDefault("source", "")), build));
        final List<Map<?, ?>> proposals = RecordSourceNotesTool.objects(call, PROPOSALS_ARG);
        final Map<String, Object> result = !proposals.isEmpty()
                ? proposed(context, build, source, notesNow, proposals)
                : confirming(call)
                        ? confirmed(call, context, build, source, notesNow)
                        : listed(call, context, build, source, notesNow);
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
        final SplitterSpec splitter = BuildTranslationXsltTool.Request.splitter(rawArg(call, SPLITTER_ARG));
        final List<Map<String, Object>> records = DraftMapping.records(samples, splitter).records();
        final List<Kind> kinds = kinds(samples, splitter, source, records);
        final List<String> recordFields = recordFields(records);
        final Set<String> settled = settled(notesNow);
        final List<Kind> todo = kinds.stream().filter(kind -> !settled.contains(kind.key())).toList();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("kinds_found", kinds.size());
        result.put("already_settled", kinds.stream().map(Kind::key).filter(settled::contains).toList());
        if (kinds.size() <= 1 || todo.isEmpty()) {
            // Nothing for the user to decide: one kind (the draft's own rule), or every kind settled already.
            result.put("source_notes", notes.recordClassified(context, build, source, List.of(), List.of(),
                    Map.of(RecordSourceNotesTool.RECORD_FIELDS, recordFields, RecordSourceNotesTool.PROPOSED,
                            List.of())));
            result.put("to_classify", List.of());
            result.put("hint", kinds.size() <= 1
                    ? "One kind of event (or none told apart): nothing to classify. Go on to the draft."
                    : "Every kind is settled by the documentation or the user: go on to the draft.");
            return result;
        }
        // Each kind's first record, shown with its proposal.
        final List<Map<String, Object>> kindSamples = todo.stream()
                .filter(kind -> !kind.examples().isEmpty())
                .map(kind -> Map.<String, Object>of(KEY, kind.key(), "sample", sampleText(kind.examples().getFirst())))
                .toList();
        result.put("source_notes", notes.recordClassified(context, build, source, List.of(),
                todo.stream().map(Kind::key).toList(), Map.of(RecordSourceNotesTool.RECORD_FIELDS, recordFields,
                        RecordSourceNotesTool.PROPOSED, List.of(), SAMPLES, kindSamples)));
        result.put("to_classify", todo.stream().map(Kind::shown).toList());
        result.put("record_fields", recordFields);
        final List<String> users = userFields(records, recordFields);
        result.put("user_fields", users.isEmpty()
                ? "None: the records name no user or account, so leave actor out."
                : users);
        result.put("hint", "Propose a classification for each of these " + todo.size() + " kinds (proposals=, one "
                           + "each, with its field and value as listed), from its records and what you know of the "
                           + "source, saying how sure you are; the user confirms them in one form.");
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
        for (int i = 0; i < given.size(); i++) {
            final Map<?, ?> item = given.get(i);
            final String key = ToolText.str(item.get("field")) + "=" + ToolText.str(item.get("value"));
            final boolean fromUser = Boolean.TRUE.equals(item.get(FROM_USER))
                                     || "true".equals(String.valueOf(item.get(FROM_USER)));
            final String confidence = ToolText.str(Objects.requireNonNullElse(item.get("confidence"), ""));
            if (!fromUser && !pending.contains(key)) {
                problems.add("proposals[" + i + "]: " + key + " isn't a kind still to classify (" + String.join(
                        ", ", pending) + ")");
            } else if (!fromUser && !List.of("high", "medium", "low").contains(confidence)) {
                problems.add("proposals[" + i + "]: confidence is high, medium or low");
            } else if (!fromUser && ToolText.isBlank(ToolText.str(Objects.requireNonNullElse(item.get(
                    "event_detail"), "")))) {
                problems.add("proposals[" + i + "]: give event_detail, the action element");
            } else if (unknownField(item, "actor", recordFields) || unknownField(item, "affected", recordFields)) {
                // Seen in the agent eval: a label from the text ('By User') given for a field, the event's user
                // then drafted empty.
                problems.add("proposals[" + i + "]: actor and affected name fields of the records, as listed ("
                             + String.join(", ", recordFields) + "), or are left out");
            } else {
                proposals.add(new Proposal(key, item, confidence, fromUser));
            }
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
                entry.put("confidence", proposals.get(i).confidence());
                for (final String shown : List.of("title", "product")) {
                    final Object value = proposals.get(i).given().get(shown);
                    if (value != null && !ToolText.isBlank(ToolText.str(value))) {
                        entry.put(shown, ToolText.str(value));
                    }
                }
                final String key = proposals.get(i).key();
                JsonValues.maps(notesNow.get(SAMPLES)).stream()
                        .filter(sample -> key.equals(sample.get(KEY)))
                        .findFirst()
                        .ifPresent(sample -> entry.put("sample", sample.get("sample")));
                toConfirm.add(entry);
            }
        }
        final Set<String> recorded = new LinkedHashSet<>();
        fromUser.forEach(entry -> recorded.add(entry.get("field") + "=" + entry.get("value")));
        final List<String> left = pending.stream().filter(key -> !recorded.contains(key)).toList();
        final Map<String, Object> doc = notes.recordClassified(context, build, source, fromUser, left,
                Map.of(RecordSourceNotesTool.PROPOSED, toConfirm));
        final Map<String, Object> result = new LinkedHashMap<>();
        if (!recorded.isEmpty()) {
            result.put("recorded", List.copyOf(recorded));
        }
        result.put("source_notes", doc);
        if (toConfirm.isEmpty()) {
            if (!left.isEmpty()) {
                result.put("still_to_classify", left);
            }
            result.put("hint", hint(List.of(), "", left));
            return result;
        }
        result.put("table", table(toConfirm));
        result.put("hint", "Show the table to the user in your reply, as it is (a Markdown table the chat renders), "
                           + "then call classify_kinds build=" + build + " confirm=true: a short form asks them to "
                           + "accept the classifications or correct them.");
        return result;
    }

    // The proposals kept, put to the user in a short form of plain text (the table is in the chat), and what they
    // confirmed recorded.
    private Map<String, Object> confirmed(final ToolCall call,
                                          final ToolCallContext context,
                                          final String build,
                                          final String source,
                                          final Map<String, Object> notesNow) {
        final List<Map<String, Object>> proposed = JsonValues.maps(notesNow.get(RecordSourceNotesTool.PROPOSED));
        if (proposed.isEmpty()) {
            throw new ToolRefusal("Nothing to confirm: send the proposals first (proposals=), and show their table "
                                  + "in the chat");
        }
        final List<Map<String, Object>> singly = proposed.stream()
                .filter(entry -> "low".equals(entry.get("confidence")))
                .limit(MOST_SINGLY)
                .toList();
        final List<Map<String, Object>> together = proposed.stream().filter(entry -> !singly.contains(entry))
                .toList();
        final Map<String, Consent.Editable> items = new LinkedHashMap<>();
        if (!together.isEmpty()) {
            items.put(BLOCK, new Consent.Editable("Accept the " + together.size() + " classification"
                                                  + (together.size() == 1
                    ? ""
                    : "s") + " of " + (singly.isEmpty()
                    ? ""
                    : "high or medium ") + "confidence in the table above", null));
        }
        singly.forEach(entry -> items.put(String.valueOf(entry.get(KEY)), new Consent.Editable(line(entry,
                proposed.indexOf(entry) + 1), null)));
        final Optional<Map<String, Object>> answers = consent.review(call, context, NAME,
                "Confirm how the " + proposed.size() + " kinds of event shown in the chat are classified, before "
                + "the translation is drafted.", items, "Corrections, if any (e.g. 4662 is Access, not Alert)");
        final Map<String, Object> result = new LinkedHashMap<>();
        if (answers.isEmpty()) {
            result.put("asked", false);
            result.put("hint", "This client shows no forms: ask the user in the chat whether the table's "
                               + "classifications are right, then send what they confirmed or corrected as "
                               + "proposals with from_user: true; leave out any they reject and ask what it is.");
            return result;
        }
        final Set<String> accepted = new LinkedHashSet<>();
        final List<String> rejected = new ArrayList<>();
        final boolean block = Boolean.TRUE.equals(answers.get().get(BLOCK));
        together.forEach(entry -> (block
                ? accepted
                : rejected).add(String.valueOf(entry.get(KEY))));
        singly.forEach(entry -> {
            final String key = String.valueOf(entry.get(KEY));
            if (Boolean.TRUE.equals(answers.get().get(key))) {
                accepted.add(key);
            } else {
                rejected.add(key);
            }
        });
        final String corrections = ToolText.str(Objects.requireNonNullElse(answers.get().get(Consent.REVIEW_TEXT),
                ""));
        final List<Map<String, Object>> confirmedEntries = proposed.stream()
                .filter(entry -> accepted.contains(String.valueOf(entry.get(KEY))))
                .map(entry -> confirmedEntry(new LinkedHashMap<>(entry)))
                .toList();
        final List<String> left = pending(notesNow).stream()
                .filter(key -> !accepted.contains(key) && !rejected.contains(key))
                .toList();
        final Map<String, Object> doc = notes.recordClassified(context, build, source, confirmedEntries, left,
                Map.of(RecordSourceNotesTool.PROPOSED, List.of()));
        result.put("recorded", List.copyOf(accepted));
        result.put("source_notes", doc);
        if (!rejected.isEmpty()) {
            result.put("rejected", rejected);
        }
        if (!corrections.isBlank()) {
            result.put("corrections", corrections);
        }
        if (!left.isEmpty()) {
            result.put("still_to_classify", left);
        }
        result.put("hint", hint(rejected, corrections, left));
        return result;
    }

    private boolean confirming(final ToolCall call) {
        final Object given = argsMap(call).get(CONFIRM_ARG);
        return Boolean.TRUE.equals(given) || "true".equals(String.valueOf(given));
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
        entry.remove("confidence");
        entry.remove("title");
        entry.remove("product");
        entry.remove("sample");
        entry.put("basis", RecordSourceNotesTool.USER_CONFIRMED);
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
            if (value != null && !ToolText.isBlank(ToolText.str(value))) {
                seen.add(field);
            }
        }));
        return List.copyOf(seen);
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

    private static String hint(final List<String> rejected, final String corrections, final List<String> left) {
        final List<String> said = new ArrayList<>();
        if (!corrections.isBlank()) {
            said.add("Apply the user's corrections: call again with each corrected entry, from_user: true");
        }
        if (!rejected.isEmpty()) {
            said.add("Ask the user what the rejected kinds are (" + String.join(", ", rejected) + "); send their "
                     + "answers with from_user: true, or leave them for the draft to keep Unknown");
        }
        if (!left.isEmpty()) {
            said.add("Propose the kinds still to classify (" + String.join(", ", left) + ")");
        }
        return said.isEmpty()
                ? "Every kind is classified: draft_translation_mapping build=... follows them, a rule a kind."
                : String.join(". ", said) + ".";
    }

    // The kinds the draft makes rules for, each with its records.
    private static List<Kind> kinds(final Map<String, String> samples,
                                    final SplitterSpec splitter,
                                    final String source,
                                    final List<Map<String, Object>> records) {
        final Map<String, Object> draft = DraftMapping.draft(List.copyOf(samples.values()), source, null, null, null,
                null, splitter);
        final List<Kind> kinds = new ArrayList<>();
        @SuppressWarnings("unchecked") final Map<String, Object> mapping = (Map<String, Object>) draft.get("mapping");
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            final List<Map<String, Object>> when = JsonValues.maps(rule.get("when"));
            final List<Map<String, Object>> equals = when.stream()
                    .filter(c -> c.get("field") != null && c.get("equals") != null)
                    .toList();
            if (equals.isEmpty()) {
                continue;
            }
            // The kind is told by its last condition (a variant's own condition comes first).
            final Map<String, Object> told = equals.getLast();
            final List<Map<String, Object>> ofKind = records.stream()
                    .filter(record -> equals.stream().allMatch(c -> String.valueOf(c.get("equals"))
                            .equals(String.valueOf(record.get(String.valueOf(c.get("field")))))))
                    .toList();
            final Kind kind = new Kind(String.valueOf(told.get("field")), String.valueOf(told.get("equals")),
                    String.valueOf(rule.get("name")), ofKind.size(), examples(ofKind));
            // One kind in several rules (a rule a variant): listed once, its records counted together.
            final Kind earlier = kinds.stream().filter(k -> k.key().equals(kind.key())).findFirst().orElse(null);
            if (earlier == null) {
                kinds.add(kind);
            } else {
                final List<Map<String, Object>> both = new ArrayList<>(earlier.examples());
                kind.examples().stream().filter(e -> !both.contains(e) && both.size() < EXAMPLES).forEach(both::add);
                kinds.set(kinds.indexOf(earlier), new Kind(earlier.field(), earlier.value(), earlier.rule(),
                        earlier.records() + kind.records(), both));
            }
        }
        return kinds;
    }

    // Up to three of the kind's records whose values differ, each cut short.
    private static List<Map<String, Object>> examples(final List<Map<String, Object>> records) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map<String, Object> record : records) {
            final Map<String, Object> shown = new LinkedHashMap<>();
            record.forEach((field, value) -> {
                final String text = ToolText.str(Objects.requireNonNullElse(value, ""));
                if (!text.isBlank() && shown.size() < EXAMPLE_FIELDS) {
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
        for (final Map<String, Object> event : JsonValues.maps(notesNow.get("events"))) {
            if (event.get("field") != null && event.get("value") != null
                && !"agent_knowledge".equals(event.get("basis"))) {
                out.add(event.get("field") + "=" + event.get("value"));
            }
        }
        return out;
    }

    // The proposals for the agent to show in the chat, one after another (asked for by the user: one wide table
    // of long descriptions was hard to read): a numbered heading for each, '[product] title', then a table of two
    // columns, the row's name and its value, with a row only where the proposal has one.
    static String table(final List<Map<String, Object>> entries) {
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            final Map<String, Object> e = entries.get(i);
            lines.add("### " + (i + 1) + ". " + (ToolText.isBlank(text(e.get("product")))
                    ? ""
                    : "[" + text(e.get("product")) + "] ") + title(e));
            lines.add("");
            if (e.get("sample") != null) {
                // Preformatted (asked for by the user), so above the table: a table's cell can't hold it.
                lines.add("```");
                lines.add(ToolText.str(e.get("sample")).replace("```", "'''"));
                lines.add("```");
                lines.add("");
            }
            // The heading names the kind (asked for by the user: no Kind row); every proposal has a confidence, the
            // table's first row (a table needs one).
            lines.add("| Confidence | " + cell(e.get("confidence")) + " |");
            lines.add("| --- | --- |");
            row(lines, "Action element", e.get("event_detail"));
            row(lines, "Action", e.get("action"));
            row(lines, "Outcome", e.get("success") == null
                    ? null
                    : JsonValues.truthy(e.get("success"))
                            ? "Success"
                            : "Failure");
            row(lines, "Type id", e.get("type_id"));
            row(lines, "Who acted", e.get("actor"));
            row(lines, "Acted on", e.get("affected") == null
                    ? null
                    : text(e.get("affected")) + (e.get("affected_as") == null
                            ? ""
                            : " (" + text(e.get("affected_as")) + ")"));
            lines.add("");
        }
        return String.join("\n", lines).strip();
    }

    // A proposal's short name: its title, else the start of its description.
    private static String title(final Map<String, Object> e) {
        return ToolText.isBlank(text(e.get("title")))
                ? cut(text(e.get("description")), 70)
                : text(e.get("title"));
    }

    // A row of a proposal's table, only where it has a value.
    private static void row(final List<String> lines, final String name, final Object value) {
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

    // A record as its fields, one a line (field = value), each value cut short: the sample shown with a kind.
    private static String sampleText(final Map<String, Object> record) {
        final List<String> lines = new ArrayList<>();
        record.forEach((field, value) -> lines.add(field + " = " + cut(ToolText.str(value).replace("\n", " "),
                SAMPLE_CHARS)));
        return String.join("\n", lines);
    }

    // A low-confidence proposal as its form item, plain and short: its number and title as the chat shows them,
    // and its action element.
    private static String line(final Map<String, Object> e, final int number) {
        return number + ". " + title(e) + " (" + text(e.get("event_detail")) + (e.get("action") == null
                ? ""
                : ": " + text(e.get("action"))) + ")";
    }

    private static String text(final Object value) {
        return value == null
                ? ""
                : ToolText.str(value).replace("|", "/");
    }


    // --------------------------------------------------------------------------------


    // A kind of event the draft tells apart: its field and value, its rule, how many records, and some of them.
    private record Kind(String field, String value, String rule, int records, List<Map<String, Object>> examples) {

        String key() {
            return field + "=" + value;
        }

        Map<String, Object> shown() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("field", field);
            out.put("value", value);
            out.put("rule", rule);
            out.put("records", records);
            out.put("examples", examples);
            return out;
        }
    }

    // A proposal as given: its kind, the entry, the agent's confidence, and whether the user said it.
    private record Proposal(String key, Map<?, ?> given, String confidence, boolean fromUser) {

        Map<?, ?> entry() {
            final Map<String, Object> out = new LinkedHashMap<>();
            given.forEach((k, v) -> out.put(String.valueOf(k), v));
            out.putIfAbsent("event", given.get("value"));
            out.remove(FROM_USER);
            out.remove("confidence");
            return out;
        }
    }
}
