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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.documentation.shared.DocumentationDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/// `record_source_notes` - keeps the user's vendor or event reference documentation in Stroom, and the notes
/// condensed from it, as Documentation docs in the build (the Stroom MCP server's `tools/feeds.py`
/// `record_source_notes`): each document verbatim (`<source> reference - <title>`), and `<source> source notes` with
/// the field dictionary and event catalogue, carried as a block the tools read back ([SourceNotes]). Called again for
/// the same source, the notes doc is updated: what earlier calls recorded stays unless given again.
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
            "event_logging_path", "values");
    private static final List<String> FIELD_REQUIRED = List.of("field", "meaning");
    private static final List<String> EVENT_KEYS = List.of("event", "description", "event_detail", "type_id", "field",
            "value", "action", "success");
    private static final List<String> EVENT_REQUIRED = List.of("event", "description");
    private static final int MAX_NAME = 200;

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final EventVersions eventVersions;
    private final BuildPlan plan;

    @Inject
    RecordSourceNotesTool(final WriteGuard guard,
                          final DocumentAccess documentAccess,
                          final EventVersions eventVersions,
                          final BuildPlan plan) {
        this.guard = guard;
        this.documentAccess = documentAccess;
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
                             + "Only for documentation the user gave (pasted, attached, or already in Stroom): with "
                             + "none, don't call this. Notes guessed from the sample aren't documentation, and the "
                             + "draft would follow them over the sample's own values (draft_translation_mapping "
                             + "reads those itself).")
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
                                                         + "to read and search later (find_documents content=an "
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
        final String summary = NullSafe.string(rawArg(call, SUMMARY_ARG));
        final List<Map<String, Object>> givenFields = notes(call, FIELDS_ARG, FIELD_KEYS, FIELD_REQUIRED);
        final List<Map<String, Object>> givenEvents = notes(call, EVENTS_ARG, EVENT_KEYS, EVENT_REQUIRED);
        if (!givenEvents.isEmpty()) {
            final List<String> problems = SourceNotes.catalogueProblems(givenEvents, detailCheck(context, build));
            if (!problems.isEmpty()) {
                throw new ToolRefusal("Nothing recorded; the event catalogue can't be drafted from as it is: "
                                      + String.join("; ", problems) + ". Correct the events and call again.");
            }
        }
        final List<Map<String, Object>> documents = documents(call);
        // Called again for the same source (a long manual sent a part at a time), the build's docs are updated, not
        // made twice: the build refuses a second doc of one name.
        final Map<String, WriteGuard.BuildDoc> contents = new LinkedHashMap<>();
        guard.folderContents(build).forEach(doc -> contents.putIfAbsent(doc.type() + "\n" + doc.name(), doc));
        final List<Map<String, Object>> keptDocs = keep(context.session(), contents, build, source, documents);

        final String notesName = source + SourceNotes.NOTES_SUFFIX;
        final Map<String, Object> earlier = earlierNotes(contents, notesName);
        // What earlier calls recorded stays, unless this one gives the same field or event again.
        final List<Map<String, Object>> fields = merged(earlier.get("fields"), givenFields, FIELD_KEYS,
                FIELD_REQUIRED);
        final List<Map<String, Object>> events = merged(earlier.get("events"), givenEvents, EVENT_KEYS,
                EVENT_REQUIRED);
        final Set<String> references = new LinkedHashSet<>(strings(earlier.get("references")));
        references.addAll(stringListArg(call, REFERENCES_ARG));
        final Set<String> keptNames = new LinkedHashSet<>(strings(earlier.get("documents")));
        keptDocs.forEach(doc -> keptNames.add(text(doc.get("name"))));

        final DocumentationDoc notesDoc = written(context.session(), contents, build, notesName,
                notesText(source, summary, fields, events, List.copyOf(references), List.copyOf(keptNames)));
        final long usable = events.stream()
                .filter(event -> BuildPlan.truthy(event.get("field")) && BuildPlan.truthy(event.get("value")))
                .count();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", DocumentationDoc.TYPE);
        result.put("uuid", notesDoc.getUuid());
        result.put("name", notesDoc.getName());
        result.put("fields", fields.size());
        result.put("events", events.size());
        result.put("documents_kept", keptDocs);
        result.put("hint", "draft_translation_mapping with build= drafts from these notes" + (usable < events.size()
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

    // Why an event_detail can't be an action element, from the event-logging schema of the version chosen for the
    // build (else the newest held); null when the schema is unreadable (the events are still checked for twins).
    private Function<String, String> detailCheck(final ToolCallContext context, final String build) {
        try {
            final EventSchema schema = eventVersions.eventSchema(eventVersions.chosenOrLatest(context, build));
            return detail -> SourceNotes.detailProblem(schema, detail);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "No event-logging schema to check the catalogue against: " + e.getMessage(), e);
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
            return writeText(there.docRef(), text);
        }
        return guard.createFilled(DocumentationDoc.TYPE, name, build, ref -> writeText(ref, text), false, null);
    }

    @SuppressWarnings("unchecked")
    private DocumentationDoc writeText(final DocRef ref, final String text) {
        final DocumentationDoc doc = documentAccess.read(ref, DocumentationDoc.class)
                .orElseThrow(() -> new ToolRefusal("The Documentation doc " + ref.getUuid() + " can't be read"));
        final DocumentActionHandler<DocumentationDoc> handler = documentAccess.handler(DocumentationDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no Documentation store"));
        return handler.writeDocument(BuildRecords.withBodyText(doc, text));
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

    static String notesText(final String source,
                            final String summary,
                            final List<Map<String, Object>> fields,
                            final List<Map<String, Object>> events,
                            final List<String> references,
                            final List<String> keptNames) {
        final List<String> lines = new ArrayList<>(List.of("# " + source + " source notes", "", summary.strip(), ""));
        if (!fields.isEmpty()) {
            lines.addAll(List.of("## Field dictionary", "", "| Field | Meaning | Type | Example | Event-logging path |",
                    "| --- | --- | --- | --- | --- |"));
            fields.forEach(field -> lines.add("| " + text(field.get("field")) + " | " + text(field.get("meaning"))
                                              + " | " + text(field.get("type")) + " | " + text(field.get("example"))
                                              + " | " + text(field.get("event_logging_path")) + " |"));
            lines.add("");
        }
        if (!events.isEmpty()) {
            lines.addAll(List.of("## Event catalogue", "", "| Event | Description | EventDetail | TypeId |",
                    "| --- | --- | --- | --- |"));
            events.forEach(event -> lines.add("| " + text(event.get("event")) + " | "
                                              + text(event.get("description")) + " | "
                                              + text(event.get("event_detail")) + " | "
                                              + text(event.get("type_id")) + " |"));
            lines.add("");
        }
        if (!references.isEmpty() || !keptNames.isEmpty()) {
            lines.addAll(List.of("## Sources", ""));
            references.forEach(reference -> lines.add("- " + reference));
            keptNames.forEach(kept -> lines.add("- `" + kept + "` (kept in Stroom)"));
        }
        final Map<String, Object> block = new LinkedHashMap<>();
        block.put("source", source);
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
        final Set<String> again = new LinkedHashSet<>();
        given.forEach(note -> again.add(text(note.get(key))));
        final List<Map<String, Object>> out = new ArrayList<>();
        if (earlier instanceof final List<?> list) {
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> note && !again.contains(text(note.get(key)))) {
                    out.add(normalised(note, keys, required));
                }
            }
        }
        out.addAll(given);
        return out;
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
            notes.add(normalised(given.get(i), keys, needed));
        }
        return notes;
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
    private static List<Map<?, ?>> objects(final ToolCall call, final String name) {
        Object value = argsMap(call).get(name);
        if (value instanceof final String text && !text.isBlank()) {
            try {
                value = PyJson.loads(text.strip());
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

    // A note with only the keys it may have, in their order, each left out at its default (as the Stroom MCP
    // server's model_dump(exclude_defaults=True)); the required keys have no default.
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

    private static List<String> strings(final Object value) {
        final List<String> out = new ArrayList<>();
        if (value instanceof final List<?> list) {
            list.forEach(item -> out.add(text(item)));
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

    private static ObjectSchema fieldNote() {
        return ObjectSchema.builder()
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
        return ObjectSchema.builder()
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
                .addStringProperty("action", "The action element's Action, when it has one, e.g. Logon or Logoff.")
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
