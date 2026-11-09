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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.generation.XPathRegex;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/// `record_source_notes` - keeps the user's vendor or event reference documentation in Stroom, and the notes condensed
/// from it, as Documentation docs in the build: each document verbatim (`<source> reference - <title>`), and `<source>
/// source notes` with the field dictionary and event catalogue, carried as a block the tools read back ([SourceNotes]).
/// Called again for the same source, the notes doc is updated: what earlier calls recorded stays unless given again.
class RecordSourceNotesTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RecordSourceNotesTool.class);

    static final String NAME = "record_source_notes";
    static final String BUILD_ARG = "build";
    static final String SOURCE_ARG = "source";
    static final String SUMMARY_ARG = "summary";
    static final String FIELDS_ARG = "fields";
    static final String EVENTS_ARG = "events";
    static final String REFERENCES_ARG = "references";
    static final String DOCUMENTS_ARG = "documents";

    // A note's keys, in the order the notes keep them; the first required key names the note.
    private static final List<String> FIELD_KEYS = List.of("field", "meaning", "type", "example",
            "event_logging_path", "values", "basis", "confidence");
    private static final List<String> FIELD_REQUIRED = List.of("field", "meaning");
    /// A pattern event's listed kind (a catch-all, `variant=syslog`) whose records its pattern tells apart.
    static final String WITHIN = "within";
    private static final List<String> EVENT_KEYS = List.of("event", "description", "event_detail", "type_id", "field",
            "value", "action", "success", "actor", "affected", "affected_as", "basis", "confidence",
            SourceNotes.MATCHES, SourceNotes.NAMES, WITHIN);
    private static final List<String> EVENT_REQUIRED = List.of("event", "description");
    private static final Pattern GROUP_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_NAME = 200;
    // Where a note came from: the user's documentation (the default), or the agent's own knowledge of the source.
    private static final String DOCUMENTATION = "documentation";
    /// A note from the agent's own knowledge of the source, not the user's documentation.
    static final String AGENT_KNOWLEDGE = "agent_knowledge";
    /// A note the agent proposed and the user confirmed (classify_kinds).
    static final String USER_CONFIRMED = "user_confirmed";
    // The notes block's kinds still to classify (classify_kinds), as field=value.
    static final String PENDING = "pending";
    // The notes block's fields of the sample's records (classify_kinds), for checking the fields its proposals name.
    static final String RECORD_FIELDS = "record_fields";
    // The notes block's proposals classify_kinds keeps between showing them in the chat and asking the user.
    static final String PROPOSED = "proposed";
    // The block keys classify_kinds keeps, carried from one write of the notes to the next.
    // The notes block's kinds the user rejected in classify_kinds, left out of its later rounds.
    static final String REJECTED = "rejected";
    // The notes block's kinds the user said to leave untranslated in classify_kinds: settled, left out of its rounds.
    static final String DROPPED = "dropped";
    /// The set of proposals last shown the user (classify_kinds): when, then its kinds.
    static final String SHOWN = "shown";
    /// The kinds of a set the user amended or declined in classify_kinds, until the agent sends them again as the
    /// user's (from_user): not proposed again, nor listed, meanwhile.
    static final String AMENDED = "amended";
    private static final List<String> CLASSIFY_KEYS = List.of(RECORD_FIELDS, PROPOSED, ClassifyKindsTool.SAMPLES,
            REJECTED, DROPPED, SHOWN, AMENDED);
    private static final List<String> CONFIDENCES = List.of("high", "medium", "low");

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final DocumentWrites documentWrites;
    private final EventVersions eventVersions;
    private final BuildPlan plan;

    @Inject
    RecordSourceNotesTool(final WriteGuard guard,
                          final DocumentAccess documentAccess,
                          final DocumentWrites documentWrites,
                          final EventVersions eventVersions,
                          final BuildPlan plan) {
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.documentWrites = documentWrites;
        this.eventVersions = eventVersions;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It writes Documentation docs in the build.
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
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The Documentation docs are created in the build folder, and changed, as the user; a "
                      + "document given by uuid is read as the user, who needs View on it.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Keep the user's vendor or event reference documentation in Stroom, and the notes "
                             + "condensed from it, as Documentation docs in the build: each document verbatim "
                             + "('<source> reference - <title>'), and '<source> source notes' with the field "
                             + "dictionary and event catalogue. The notes are read back by the tools: "
                             + "draft_translation_mapping (build=) drafts from them (fields where the dictionary "
                             + "puts them, a rule per catalogued event that gives the field and value showing it), "
                             + "build_translation_xslt (build=) checks a mapping against the catalogue, and "
                             + "write_documentation lists the source fields with their meanings. Promotion puts them "
                             + "beside the feed.\n\n"
                             + "For documentation the user gave (pasted, attached, or already in Stroom), and for "
                             + "what you know of a source yourself (a vendor's published event ids and fields), each "
                             + "such note marked basis=agent_knowledge with a confidence. Never notes guessed from "
                             + "the sample's values: the draft would follow them over the sample "
                             + "(draft_translation_mapping reads those itself). The result's to_review lists your "
                             + "notes of low confidence: put those to the user first.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addStringProperty(SOURCE_ARG, "Source name, e.g. 'Acme door controller'.")
                        .addStringProperty(SUMMARY_ARG, "What the documentation says about the source, in a few "
                                                        + "lines.")
                        .addProperty(FIELDS_ARG, list("Field dictionary condensed from the documentation.",
                                fieldNote()))
                        .addProperty(EVENTS_ARG, list("Event catalogue condensed from the documentation.",
                                eventNote()))
                        .addProperty(REFERENCES_ARG, stringArraySchema("Titles or links of the documents used."))
                        .addProperty(DOCUMENTS_ARG, list("The user's documents themselves, kept verbatim in Stroom "
                                                         + "as Documentation docs '<source> reference - <title>', "
                                                         + "to read and search later (find_in_content pattern=an "
                                                         + "event id).", referenceDocument()))
                        .required(BUILD_ARG, SOURCE_ARG, SUMMARY_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(record(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> record(final ToolCall call, final ToolCallContext context) {
        final String build = required(call, BUILD_ARG);
        final String source = required(call, SOURCE_ARG);
        final String givenSummary = NullSafe.string(rawArg(call, SUMMARY_ARG));
        final List<Map<String, Object>> givenFields = notes(call, FIELDS_ARG, FIELD_KEYS, FIELD_REQUIRED);
        final List<Map<String, Object>> allEvents = notes(call, EVENTS_ARG, EVENT_KEYS, EVENT_REQUIRED);
        // An event whose action element the schema doesn't have is left out, to be sent again corrected; the rest is
        // recorded (seen: 10,500 characters of notes refused whole for three such events, then sent whole again).
        final Function<String, String> detailCheck = detailCheck(context, build);
        final List<Map<String, Object>> givenEvents = new ArrayList<>();
        final List<String> eventProblems = new ArrayList<>();
        final EventSchema affectedSchema = schemaOrNull(context, build);
        for (final Map<String, Object> event : allEvents) {
            final List<String> problems = new ArrayList<>(SourceNotes.catalogueProblems(List.of(event),
                    detailCheck));
            if (problems.isEmpty()) {
                affectedChecked(affectedSchema, event).ifPresent(problems::add);
            }
            if (problems.isEmpty()) {
                givenEvents.add(event);
            } else {
                eventProblems.addAll(problems);
            }
        }
        if (!givenEvents.isEmpty()) {
            // Two events a record can't tell apart are the catalogue's as a whole: nothing recorded (each event's
            // action element already checked).
            final List<String> problems = SourceNotes.catalogueProblems(givenEvents, null);
            if (!problems.isEmpty()) {
                throw new ToolRefusal("Nothing recorded; the event catalogue can't be drafted from as it is: "
                                      + String.join("; ", problems) + ". Correct the events and call again.");
            }
        }
        final List<Map<String, Object>> pathProblems = unknownPaths(context, build, givenFields);
        final List<Map<String, Object>> documents = documents(call);
        // Called again for the same source (a long manual sent a part at a time), the build's docs are updated, not
        // made twice: the build refuses a second doc of one name.
        final Map<String, WriteGuard.BuildDoc> contents = new LinkedHashMap<>();
        guard.folderContents(build).forEach(doc -> contents.putIfAbsent(doc.type() + "\n" + doc.name(), doc));
        final List<Map<String, Object>> keptDocs = keep(context.session(), contents, build, source, documents);

        final String notesName = notesName(contents, source);
        final Map<String, Object> earlier = earlierNotes(contents, notesName);
        final String summary = NullSafe.isBlankString(givenSummary)
                ? text(earlier.get("summary"))
                : givenSummary;
        // What earlier calls recorded stays, unless this one gives the same field or event again.
        final List<Map<String, Object>> fields = merged(earlier.get("fields"), givenFields, FIELD_KEYS,
                FIELD_REQUIRED);
        final List<Map<String, Object>> events = merged(earlier.get("events"), givenEvents, EVENT_KEYS,
                EVENT_REQUIRED);
        final Set<String> references = new LinkedHashSet<>(JsonValues.strings(earlier.get("references")));
        references.addAll(stringListArg(call, REFERENCES_ARG));
        final Set<String> keptNames = new LinkedHashSet<>(JsonValues.strings(earlier.get("documents")));
        keptDocs.forEach(doc -> keptNames.add(text(doc.get("name"))));

        final DocumentationDoc notesDoc = written(context.session(), contents, build, notesName,
                notesText(source, summary, fields, events, List.copyOf(references), List.copyOf(keptNames),
                        JsonValues.strings(earlier.get(PENDING)), earlier.containsKey(PENDING),
                        carried(earlier, Map.of())));
        final long usable = events.stream()
                .filter(event -> JsonValues.truthy(event.get("field"))
                                 && (JsonValues.truthy(event.get("value")) || SourceNotes.isPattern(event)))
                .count();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", DocumentationDoc.TYPE);
        result.put("uuid", notesDoc.getUuid());
        result.put("name", notesDoc.getName());
        result.put("fields", fields.size());
        result.put("events", events.size());
        result.put("documents_kept", keptDocs);
        final List<String> review = new ArrayList<>(toReview(fields, "field"));
        review.addAll(toReview(events, "event"));
        if (!review.isEmpty()) {
            // The agent's own notes it is least sure of: for the user to confirm or correct first.
            result.put("to_review", review);
        }
        if (!pathProblems.isEmpty()) {
            result.put("path_problems", pathProblems);
        }
        if (!eventProblems.isEmpty()) {
            result.put("event_problems", eventProblems);
        }
        result.put("hint", (eventProblems.isEmpty()
                ? ""
                : eventProblems.size() + " event(s) were left out (event_problems says why): call again with just "
                  + "those events, corrected (build, source and summary as before); the rest is kept. ")
                           + (pathProblems.isEmpty()
                ? ""
                : pathProblems.size() + " event_logging_path(s) aren't in the event-logging schema and were left "
                  + "out (path_problems says what is allowed where each stops): call again with just those fields "
                  + "and corrected paths; the rest is kept. ")
                           + "draft_translation_mapping with build= drafts from these notes" + (usable < events.size()
                ? "; events without a field and value (" + (events.size() - usable) + ") cannot become rules: give "
                  + "them where the documentation says how a record shows the event"
                : "") + ".");
        return plan.withNext(context, build, result);
    }

    // The user's documents: those already in Stroom named, the others kept verbatim in the build.
    private List<Map<String, Object>> keep(final ToolSession session,
                                           final Map<String, WriteGuard.BuildDoc> contents,
                                           final String build,
                                           final String source,
                                           final List<Map<String, Object>> documents) {
        final List<Map<String, Object>> keptDocs = new ArrayList<>();
        for (final Map<String, Object> document : documents) {
            if (NullSafe.isNonEmptyString(text(document.get("uuid")))) {
                // Already in Stroom (uploaded by the user): kept where it is, and named in the notes.
                keptDocs.add(alreadyInStroom(text(document.get("uuid"))));
            }
        }
        for (final Map<String, Object> document : documents) {
            if (NullSafe.isEmptyString(text(document.get("uuid")))) {
                final String title = text(document.get("title"));
                final String name = cut(source + SourceNotes.REFERENCE_INFIX + title);
                final String origin = text(document.get("source"));
                final String body = "# " + title + "\n\n" + (origin.isEmpty()
                        ? ""
                        : "Source: " + origin + "\n\n") + text(document.get("text")).strip() + "\n";
                final DocumentationDoc kept = written(session, contents, build, name, body);
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("uuid", kept.getUuid());
                entry.put("name", kept.getName());
                keptDocs.add(entry);
            }
        }
        return keptDocs;
    }

    // The given fields' paths the build's event-logging schema doesn't have, each left out of its note; none when
    // the schema is unreadable (the paths are then kept, as before).
    private List<Map<String, Object>> unknownPaths(final ToolCallContext context,
                                                   final String build,
                                                   final List<Map<String, Object>> fields) {
        final EventSchema schema;
        try {
            schema = eventVersions.eventSchema(context, build, eventVersions.chosenOrLatest(context, build));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No event-logging schema to check the paths against: {}",
                    e.getMessage()), e);
            return List.of();
        }
        return SourceNotes.withoutUnknownPaths(schema, fields);
    }

    // Why an event_detail can't be an action element, from the event-logging schema of the version chosen for the
    // build (else the newest held); null when the schema is unreadable (the events are still checked for twins).
    private Function<String, String> detailCheck(final ToolCallContext context, final String build) {
        try {
            final EventSchema schema = eventVersions.eventSchema(context, build,
                    eventVersions.chosenOrLatest(context, build));
            return detail -> SourceNotes.detailProblem(schema, detail);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "No event-logging schema to check the catalogue against: {}",
                    e.getMessage()),
                    e);
            return null;
        }
    }

    /// Records catalogue events classify_kinds settled (each basis user_confirmed) into the build's source notes, with
    /// the kinds still to classify; earlier notes stay, an event given again replaced.
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param source  The source's name (its notes doc is `<source> source notes`).
    /// @param events  The events, each checked by [#checkedEvents].
    /// @param pending The kinds still to classify, as field=value (none when every kind is settled).
    /// @param kept    Block keys classify_kinds keeps (record_fields, proposed), each replacing the earlier one; a
    ///                key given as an empty list removes it; the others are carried over.
    /// @return The notes doc's `uuid` and `name`.
    Map<String, Object> recordClassified(final ToolCallContext context,
                                         final String build,
                                         final String source,
                                         final List<Map<String, Object>> events,
                                         final List<String> pending,
                                         final Map<String, Object> kept) {
        final Map<String, WriteGuard.BuildDoc> contents = new LinkedHashMap<>();
        guard.folderContents(build).forEach(doc -> contents.putIfAbsent(doc.type() + "\n" + doc.name(), doc));
        final String notesName = notesName(contents, source);
        final Map<String, Object> earlier = earlierNotes(contents, notesName);
        final List<Map<String, Object>> fields = merged(earlier.get("fields"), List.of(), FIELD_KEYS,
                FIELD_REQUIRED);
        final List<Map<String, Object>> merged = merged(earlier.get("events"), events, EVENT_KEYS, EVENT_REQUIRED,
                RecordSourceNotesTool::kindOf);
        final String summary = NullSafe.nonBlankStringElse(text(earlier.get("summary")),
                "The kinds of event the agent classified and the user confirmed.");
        final DocumentationDoc doc = written(context.session(), contents, build, notesName, notesText(source,
                summary, fields, merged, JsonValues.strings(earlier.get("references")),
                JsonValues.strings(earlier.get("documents")), pending, true, carried(earlier, kept)));
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", doc.getUuid());
        out.put("name", doc.getName());
        return out;
    }

    // The classify_kinds keys of the earlier notes, with those given in their place; an empty list removes one.
    private static Map<String, Object> carried(final Map<String, Object> earlier, final Map<String, Object> given) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final String key : CLASSIFY_KEYS) {
            final Object value = given.containsKey(key)
                    ? given.get(key)
                    : earlier.get(key);
            if (value instanceof final List<?> list && !list.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }

    /// Catalogue events as recorded: normalised, basis checked, and each checked against the schema's action
    /// elements and the others (two a record can't tell apart).
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param given   The events.
    /// @param name    The argument they were given in, for the problems.
    /// @return The events.
    /// @throws ToolRefusal naming every problem.
    List<Map<String, Object>> checkedEvents(final ToolCallContext context,
                                            final String build,
                                            final List<Map<?, ?>> given,
                                            final String name) {
        final List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < given.size(); i++) {
            for (final String key : EVENT_REQUIRED) {
                if (given.get(i).get(key) == null) {
                    throw new ToolRefusal(name + "[" + i + "]." + key + " is required");
                }
            }
            final Map<String, Object> event = normalised(given.get(i), EVENT_KEYS, EVENT_REQUIRED);
            basisChecked(event, name + "[" + i + "]");
            events.add(event);
        }
        final List<String> problems = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            patternChecked(events.get(i), name + "[" + i + "]").ifPresent(problems::add);
        }
        if (!problems.isEmpty()) {
            throw new ToolRefusal("Nothing recorded: " + String.join("; ", problems) + ". Correct them and call "
                                  + "again.");
        }
        problems.addAll(SourceNotes.catalogueProblems(events, detailCheck(context, build)));
        if (problems.isEmpty()) {
            final EventSchema schema = schemaOrNull(context, build);
            events.forEach(event -> affectedChecked(schema, event).ifPresent(problems::add));
            events.forEach(event -> problems.addAll(partsChecked(schema, event)));
        }
        if (problems.isEmpty()) {
            problems.addAll(SourceNotes.catalogueProblems(events, null));
        }
        if (!problems.isEmpty()) {
            throw new ToolRefusal("Nothing recorded: " + String.join("; ", problems) + ". Correct them and call "
                                  + "again.");
        }
        return events;
    }

    // A pattern event checked: its field, its pattern instead of a value, the pattern one the generated XSLT runs, and
    // a name for each capture group; what is wrong, if anything.
    private static Optional<String> patternChecked(final Map<String, Object> event, final String where) {
        if (!SourceNotes.isPattern(event)) {
            return event.containsKey(SourceNotes.NAMES)
                    ? Optional.of(where + ": names are for a pattern's capture groups (with matches)")
                    : Optional.empty();
        }
        if (text(event.get("field")).isBlank()) {
            return Optional.of(where + ": a pattern is matched against a field: give field (e.g. message)");
        }
        if (!text(event.get("value")).isBlank()) {
            return Optional.of(where + ": give value or matches, not both");
        }
        final String regex = text(event.get(SourceNotes.MATCHES));
        final Optional<String> problem = XPathRegex.problem(regex);
        if (problem.isPresent()) {
            return Optional.of(where + ": matches " + regex + ": " + problem.get());
        }
        final List<String> names = JsonValues.strings(event.get(SourceNotes.NAMES));
        final int groups;
        try {
            groups = XPathRegex.groups(regex);
        } catch (final XPathRegex.XPathRegexException e) {
            return Optional.of(where + ": matches " + regex + " does not compile: " + e.getMessage());
        }
        if (groups != names.size()) {
            return Optional.of(where + ": matches has " + groups + " capture group(s) and names " + names.size()
                               + ": name each group (the field it reads, e.g. user), and make any other group "
                               + "(?:...)");
        }
        return names.stream()
                .filter(group -> !GROUP_NAME.matcher(group).matches())
                .findFirst()
                .map(group -> where + ": the name '" + group + "' is letters, digits and _");
    }

    /// @param context The call's context.
    /// @param build   The build.
    /// @param source  The source's name, if given: the notes doc named for it is read when the build has one.
    /// @return The build's source notes (the doc [#notesName] picks), or empty without any.
    Map<String, Object> notesOf(final ToolCallContext context, final String build, final String source) {
        final Map<String, WriteGuard.BuildDoc> contents = new LinkedHashMap<>();
        guard.folderContents(build).forEach(doc -> contents.putIfAbsent(doc.type() + "\n" + doc.name(), doc));
        return earlierNotes(contents, notesName(contents, NullSafe.string(source)));
    }

    /// The name of the build's source notes doc: the one named for the source; else the one the build has
    /// already (named for the build, say, by an earlier call with no source); else a new one's, for the source.
    /// A build keeps one notes doc. Seen in testing: classify_kinds wrote its proposals to a second doc, named for
    /// the source, and read the first, named for the build, so the confirmation found nothing to confirm.
    ///
    /// @param contents The build folder's docs, keyed by type and name.
    /// @param source   The source's name (blank when not given).
    /// @return The notes doc's name.
    static String notesName(final Map<String, WriteGuard.BuildDoc> contents, final String source) {
        final String named = source + SourceNotes.NOTES_SUFFIX;
        final List<String> existing = contents.values().stream()
                .filter(doc -> DocumentationDoc.TYPE.equals(doc.type())
                               && doc.name().endsWith(SourceNotes.NOTES_SUFFIX))
                .map(WriteGuard.BuildDoc::name)
                .toList();
        if (existing.contains(named)) {
            return named;
        }
        return existing.stream()
                .filter(name -> name.equalsIgnoreCase(named))
                .findFirst()
                .or(() -> existing.stream().findFirst())
                .orElse(named);
    }

    // An event's affected_as checked against its action element, and written out to the leaf the affected field's
    // value goes in (User becomes User/Id); why it can't be, if not. Left as given when the schema is unreadable.
    private static Optional<String> affectedChecked(final EventSchema schema,
                                                              final Map<String, Object> event) {
        if (schema == null || !JsonValues.truthy(event.get("affected_as"))) {
            return Optional.empty();
        }
        try {
            event.put("affected_as", SourceNotes.affectedPath(schema, text(event.getOrDefault("event_detail",
                    "Unknown")), text(event.get("affected_as"))));
            return Optional.empty();
        } catch (final IllegalArgumentException e) {
            return Optional.of("event " + text(event.get("event")) + ": " + e.getMessage());
        }
    }

    // An event's Action and outcome checked against its action element: the parts it has no element for (seen in chat
    // 163: Alert given an outcome and an Action, which the user confirmed, and the draft then failed on). None when
    // the schema is unreadable.
    private static List<String> partsChecked(final EventSchema schema, final Map<String, Object> event) {
        if (schema == null || !JsonValues.truthy(event.get("event_detail"))) {
            return List.of();
        }
        final String detail = "EventDetail/" + text(event.get("event_detail"));
        final List<String> problems = new ArrayList<>();
        if (event.get("success") != null && !schema.hasPath(detail + "/Outcome")) {
            problems.add("event " + text(event.get("event")) + ": " + detail + " has no Outcome, so leave success "
                         + "out");
        }
        if (JsonValues.truthy(event.get("action")) && !schema.hasPath(detail + "/Action")) {
            problems.add("event " + text(event.get("event")) + ": " + detail + " has no Action, so leave action "
                         + "out");
        }
        return problems;
    }

    // The event-logging schema of the build's version (else the newest held), or null when unreadable.
    private EventSchema schemaOrNull(final ToolCallContext context, final String build) {
        try {
            return eventVersions.eventSchema(context, build, eventVersions.chosenOrLatest(context, build));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No event-logging schema to check affected_as against: {}",
                    e.getMessage()), e);
            return null;
        }
    }

    private Map<String, Object> alreadyInStroom(final String uuid) {
        final DocumentationDoc existing = documentAccess.read(new DocRef(DocumentationDoc.TYPE, uuid),
                        DocumentationDoc.class)
                .orElseThrow(() -> new ToolRefusal(FeedNames.notFound("No Documentation doc '" + uuid + "'")));
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("uuid", uuid);
        entry.put("name", existing.getName());
        entry.put("already_in_stroom", true);
        return entry;
    }

    // The build's Documentation doc of the name written with the text: the one already there, else a new one.
    private DocumentationDoc written(final ToolSession session,
                                     final Map<String, WriteGuard.BuildDoc> contents,
                                     final String build,
                                     final String name,
                                     final String text) {
        final WriteGuard.BuildDoc there = contents.get(DocumentationDoc.TYPE + "\n" + name);
        if (there != null) {
            guard.checkInBuild(session, there.docRef());
            return documentWrites.writeText(there.docRef(), text);
        }
        return guard.createFilled(DocumentationDoc.TYPE, name, build, ref -> documentWrites.writeText(ref, text), false,
                null);
    }

    private Map<String, Object> earlierNotes(final Map<String, WriteGuard.BuildDoc> contents, final String name) {
        final WriteGuard.BuildDoc there = contents.get(DocumentationDoc.TYPE + "\n" + name);
        if (there == null) {
            return Map.of();
        }
        final Map<String, Object> notes = documentAccess.read(there.docRef(), DocumentationDoc.class)
                .map(doc -> SourceNotes.readNotes(BuildRecords.bodyText(doc)))
                .orElse(null);
        return NullSafe.map(notes);
    }

    /// The text of a source notes document: a markdown field dictionary, event catalogue and sources, then the
    /// notes as a block the tools read back.
    ///
    /// @param source     The log source the notes are of.
    /// @param summary    What the source is.
    /// @param fields     The field dictionary's rows.
    /// @param events     The event catalogue's rows.
    /// @param references Where the notes came from, e.g. vendor documentation.
    /// @param keptNames  The names of the source documents kept in the build.
    /// @return The document's text.
    static String notesText(final String source,
                            final String summary,
                            final List<Map<String, Object>> fields,
                            final List<Map<String, Object>> events,
                            final List<String> references,
                            final List<String> keptNames) {
        return notesText(source, summary, fields, events, references, keptNames, List.of(), false, Map.of());
    }

    /// As [#notesText(String, String, List, List, List, List)], with the kinds still to classify.
    ///
    /// @param source     The log source the notes are of.
    /// @param summary    What the source is.
    /// @param fields     The field dictionary's rows.
    /// @param events     The event catalogue's rows.
    /// @param references Where the notes came from, e.g. vendor documentation.
    /// @param keptNames  The names of the source documents kept in the build.
    /// @param pending    The kinds still to classify (classify_kinds), as field=value.
    /// @param classified Whether classify_kinds has looked at the kinds (pending then says what is left).
    /// @param kept       Block keys classify_kinds keeps (record_fields, proposed).
    /// @return The document's text.
    static String notesText(final String source,
                            final String summary,
                            final List<Map<String, Object>> fields,
                            final List<Map<String, Object>> events,
                            final List<String> references,
                            final List<String> keptNames,
                            final List<String> pending,
                            final boolean classified,
                            final Map<String, Object> kept) {
        final List<String> lines = new ArrayList<>(List.of("# " + source + " source notes", "", summary.strip(), ""));
        if (!fields.isEmpty()) {
            lines.addAll(List.of("## Field dictionary", "", "| Field | Meaning | Type | Example | Event-logging path "
                                                            + "| Basis |",
                    "| --- | --- | --- | --- | --- | --- |"));
            fields.forEach(field -> lines.add("| " + text(field.get("field")) + " | " + text(field.get("meaning"))
                                              + " | " + text(field.get("type")) + " | " + text(field.get("example"))
                                              + " | " + text(field.get("event_logging_path")) + " | "
                                              + basisText(field) + " |"));
            lines.add("");
        }
        if (!events.isEmpty()) {
            lines.addAll(List.of("## Event catalogue", "", "| Event | Told by | Description | EventDetail | TypeId | "
                                                       + "Basis |", "| --- | --- | --- | --- | --- | --- |"));
            events.forEach(event -> lines.add("| " + text(event.get("event")) + " | " + toldBy(event) + " | "
                                              + text(event.get("description")) + " | "
                                              + text(event.get("event_detail")) + " | "
                                              + text(event.get("type_id")) + " | " + basisText(event) + " |"));
            lines.add("");
        }
        if (!pending.isEmpty()) {
            lines.addAll(List.of("## Kinds still to classify", ""));
            pending.forEach(kind -> lines.add("- " + kind));
            lines.add("");
        }
        if (!references.isEmpty() || !keptNames.isEmpty()) {
            lines.addAll(List.of("## Sources", ""));
            references.forEach(reference -> lines.add("- " + reference));
            keptNames.forEach(name -> lines.add("- `" + name + "` (kept in Stroom)"));
        }
        final Map<String, Object> block = new LinkedHashMap<>();
        block.put("source", source);
        if (!summary.isBlank()) {
            block.put("summary", summary.strip());
        }
        if (classified) {
            block.put(PENDING, List.copyOf(pending));
        }
        kept.forEach(block::put);
        block.put("fields", fields);
        block.put("events", events);
        block.put("documents", keptNames);
        if (!references.isEmpty()) {
            block.put("references", references);
        }
        lines.add("");
        lines.add(SourceNotes.block(block));
        return String.join("\n", lines) + "\n";
    }

    // Earlier notes, less those given again, then the ones given now.
    private static List<Map<String, Object>> merged(final Object earlier,
                                                    final List<Map<String, Object>> given,
                                                    final List<String> keys,
                                                    final List<String> required) {
        final String key = required.getFirst();
        return merged(earlier, given, keys, required, note -> text(note.get(key)));
    }

    // The earlier notes, those the same as one given (by identity) replaced by it.
    private static List<Map<String, Object>> merged(final Object earlier,
                                                    final List<Map<String, Object>> given,
                                                    final List<String> keys,
                                                    final List<String> required,
                                                    final Function<Map<String, Object>, String> identity) {
        final Set<String> again = new LinkedHashSet<>();
        given.forEach(note -> again.add(identity.apply(note)));
        final List<Map<String, Object>> out = new ArrayList<>();
        if (earlier instanceof final List<?> list) {
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> note) {
                    final Map<String, Object> normalised = normalised(note, keys, required);
                    if (!again.contains(identity.apply(normalised))) {
                        out.add(normalised);
                    }
                }
            }
        }
        out.addAll(given);
        return out;
    }

    // Which kind of record a classified event is: what tells its records (with the kind a pattern is within), else
    // its name. Seen in testing: two kinds confirmed with the same event name, the later replaced the earlier, whose
    // records were then put to the user again.
    private static String kindOf(final Map<String, Object> event) {
        final String told = toldBy(event);
        return told.isEmpty()
                ? "event:" + text(event.get("event"))
                : told + " within " + text(event.get(WITHIN));
    }

    // The notes given for an argument: each a JSON object, with its required keys.
    private static List<Map<String, Object>> notes(final ToolCall call,
                                                   final String name,
                                                   final List<String> keys,
                                                   final List<String> needed) {
        final List<Map<String, Object>> notes = new ArrayList<>();
        final List<Map<?, ?>> given = objects(call, name);
        for (int i = 0; i < given.size(); i++) {
            for (final String key : needed) {
                if (given.get(i).get(key) == null) {
                    throw new ToolRefusal(name + "[" + i + "]." + key + " is required");
                }
            }
            final Map<String, Object> note = normalised(given.get(i), keys, needed);
            basisChecked(note, name + "[" + i + "]");
            notes.add(note);
        }
        return notes;
    }

    // What tells an event's records: its field's value, or the pattern its field matches.
    private static String toldBy(final Map<String, Object> event) {
        if (text(event.get("field")).isBlank()) {
            return "";
        }
        return "`" + (SourceNotes.isPattern(event)
                ? text(event.get("field")) + " matches " + text(event.get(SourceNotes.MATCHES))
                : text(event.get("field")) + "=" + text(event.get("value"))).replace("|", "\\|") + "`";
    }

    // A note's basis and confidence: documentation unless said, a confidence only (and always) for the agent's own
    // knowledge.
    private static void basisChecked(final Map<String, Object> note, final String where) {
        final String basis = text(note.getOrDefault("basis", DOCUMENTATION));
        if (!List.of(DOCUMENTATION, AGENT_KNOWLEDGE, USER_CONFIRMED).contains(basis)) {
            throw new ToolRefusal(where + ".basis is 'documentation' or 'agent_knowledge', not '" + basis + "'");
        }
        final String confidence = text(note.get("confidence"));
        if (AGENT_KNOWLEDGE.equals(basis) && !CONFIDENCES.contains(confidence)) {
            throw new ToolRefusal(where + ": a note from your own knowledge needs confidence 'high', 'medium' or "
                                  + "'low'");
        }
        if (DOCUMENTATION.equals(basis)) {
            note.remove("basis");
            note.remove("confidence");
        }
        if (USER_CONFIRMED.equals(basis)) {
            note.remove("confidence");
        }
    }

    /// @param notes Field or event notes.
    /// @param key   The key naming each (field or event).
    /// @return The names of those from the agent's own knowledge at low confidence, for the user to review first.
    static List<String> toReview(final List<Map<String, Object>> notes, final String key) {
        return notes.stream()
                .filter(note -> AGENT_KNOWLEDGE.equals(note.get("basis")) && "low".equals(note.get("confidence")))
                .map(note -> text(note.get(key)))
                .toList();
    }

    // How a note's basis reads in the notes document.
    private static String basisText(final Map<String, Object> note) {
        if (USER_CONFIRMED.equals(note.get("basis"))) {
            return "agent's proposal, confirmed by the user";
        }
        return AGENT_KNOWLEDGE.equals(note.get("basis"))
                ? "agent knowledge (" + text(note.get("confidence")) + ")"
                : DOCUMENTATION;
    }

    private static List<Map<String, Object>> documents(final ToolCall call) {
        final List<Map<String, Object>> documents = new ArrayList<>();
        final List<Map<?, ?>> given = objects(call, DOCUMENTS_ARG);
        for (int i = 0; i < given.size(); i++) {
            final Map<?, ?> document = given.get(i);
            if (NullSafe.isEmptyString(text(document.get("title")))) {
                throw new ToolRefusal(DOCUMENTS_ARG + "[" + i + "].title is required");
            }
            if (text(document.get("text")).isBlank() == text(document.get("uuid")).isEmpty()) {
                throw new ToolRefusal(DOCUMENTS_ARG + "[" + i + "]: give the document's text, or the uuid of the "
                                      + "Documentation doc holding it, not both");
            }
            final Map<String, Object> kept = new LinkedHashMap<>();
            for (final String key : List.of("title", "text", "uuid", "source")) {
                kept.put(key, text(document.get(key)));
            }
            documents.add(kept);
        }
        return documents;
    }

    // An argument's JSON objects: a list of them, or one alone, or either as JSON text.
    static List<Map<?, ?>> objects(final ToolCall call, final String name) {
        Object value = argsMap(call).get(name);
        if (value instanceof final String text && !text.isBlank()) {
            try {
                value = ToolJson.loads(text.strip());
            } catch (final RuntimeException e) {
                throw new ToolRefusal(name + " must be a list of objects, not text");
            }
        }
        final List<Map<?, ?>> objects = new ArrayList<>();
        if (value instanceof final Map<?, ?> one) {
            objects.add(one);
        } else if (value instanceof final List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof final Map<?, ?> item)) {
                    throw new ToolRefusal(name + "[" + i + "] must be an object");
                }
                objects.add(item);
            }
        }
        return objects;
    }

    // A note with only the keys it may have, in their order, each left out at its default; the required keys have no
    // default.
    private static Map<String, Object> normalised(final Map<?, ?> note,
                                                  final List<String> keys,
                                                  final List<String> required) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final String key : keys) {
            final Object value = note.get(key);
            if ("values".equals(key)) {
                if (value instanceof final Map<?, ?> codes && !codes.isEmpty()) {
                    final Map<String, Object> strings = new LinkedHashMap<>();
                    codes.forEach((code, meaning) -> strings.put(String.valueOf(code), text(meaning)));
                    out.put(key, strings);
                }
            } else if (SourceNotes.NAMES.equals(key)) {
                if (value instanceof final List<?> names && !names.isEmpty()) {
                    out.put(key, names.stream().map(RecordSourceNotesTool::text).toList());
                }
            } else if ("success".equals(key)) {
                if (value instanceof final Boolean bool) {
                    out.put(key, bool);
                } else if (value instanceof final String text && !text.isBlank()) {
                    out.put(key, Boolean.parseBoolean(text.strip()));
                }
            } else if (value != null && (!text(value).isEmpty() || required.contains(key))) {
                out.put(key, text(value));
            }
        }
        return out;
    }

    private static String text(final Object value) {
        return value == null
                ? ""
                : String.valueOf(value);
    }

    private static String cut(final String name) {
        return name.length() <= MAX_NAME
                ? name
                : name.substring(0, MAX_NAME);
    }

    private static ArraySchema list(final String description, final ToolSchema items) {
        return ArraySchema.builder().description(description).items(items).build();
    }

    private static ObjectSchema.Builder withBasis(final ObjectSchema.Builder builder) {
        return builder
                .addStringProperty("basis", "Where the note comes from: 'documentation' (the user's; the default) "
                                            + "or 'agent_knowledge' (what you know of the source, said as such).")
                .addStringProperty("confidence", "With basis agent_knowledge: 'high', 'medium' or 'low', how sure "
                                                 + "you are; low ones are put to the user first.");
    }

    private static ObjectSchema fieldNote() {
        return withBasis(ObjectSchema.builder())
                .addStringProperty("field", "The field's name as the sample has it (a column, key or element).")
                .addStringProperty("meaning", "What the field holds.")
                .addStringProperty("type", "Its type.")
                .addStringProperty("example", "An example value.")
                .addStringProperty("event_logging_path", "The event-logging element it belongs in, e.g. "
                                                         + "EventSource/User/Id; the draft maps the field there.")
                .addProperty("values", ObjectSchema.builder()
                        .description("The field's codes and what each means, from the documentation, e.g. {'0x0': "
                                     + "'success'}.")
                        .build())
                .required("field", "meaning")
                .build();
    }

    private static ObjectSchema eventNote() {
        return withBasis(ObjectSchema.builder())
                .addStringProperty("event", "Source event id or action, e.g. '4624' or 'CODE_TO_TOKEN'.")
                .addStringProperty("description", "What the event is.")
                .addStringProperty("event_detail", "The EventDetail action element, e.g. Authenticate, Update, "
                                                   + "Alert; for a network event, Network and its action: "
                                                   + "Network/Permit (a connection allowed), Network/Deny, "
                                                   + "Network/Connect, Network/Close.")
                .addStringProperty("type_id", "The event's TypeId.")
                .addStringProperty("field", "The field whose value shows this event in a record, e.g. 'evt'; with "
                                            + "value, the draft makes a rule for it and build_translation_xslt "
                                            + "checks the mapping against it.")
                .addStringProperty("value", "That field's value for this event, e.g. '4625'.")
                .addStringProperty(SourceNotes.MATCHES, "In place of value, for an event said in free text: a regex "
                                                        + "(XPath's: no lookaround) the field's value matches, e.g. "
                                                        + "'^Failed password for (?:invalid user )?(\\S+) from "
                                                        + "(\\S+)'.")
                .addProperty(SourceNotes.NAMES, stringArraySchema("With matches: a name for each capture group, the "
                                                                  + "field it reads, e.g. ['user', 'src_ip']."))
                .addStringProperty("action", "The action element's Action, when it has one, e.g. Logon or Logoff.")
                .addStringProperty("actor", "The field naming the user or account who acted (EventSource/User for "
                                            + "this event); left out when the records name none.")
                .addStringProperty("affected", "The field naming the user or object acted on; left out when there "
                                               + "is none.")
                .addStringProperty("affected_as", "What the affected field names, as the action element's object: "
                                                  + "User, File, Document, Group... (or its leaf, User/Name).")
                .addBooleanProperty("success", "The event's outcome, when the documentation says it, e.g. false for "
                                               + "a failed logon.")
                .required("event", "description")
                .build();
    }

    private static ObjectSchema referenceDocument() {
        return ObjectSchema.builder()
                .addStringProperty("title", "The document's title, e.g. 'Acme door controller event reference "
                                            + "v3'.")
                .addStringProperty("text", "Its text, verbatim, as Markdown or plain text, as the user gave it "
                                           + "(attached in their client). A document too long for one call goes in "
                                           + "parts, one call each, titled '<title> part 1', '<title> part 2' and "
                                           + "so on.")
                .addStringProperty("uuid", "Instead of text: a Documentation doc already in Stroom holding it (one "
                                           + "the user made in the Stroom UI, say).")
                .addStringProperty("source", "Where it came from: a URL or file name.")
                .required("title")
                .build();
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }
}
