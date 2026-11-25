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
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.draft.SourceNotes;
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
import java.util.Set;
import java.util.function.Function;

/// `record_source_notes` - keeps the user's vendor or event reference documentation in Stroom, and the notes condensed
/// from it, as Documentation docs in the build: each document verbatim (`<source> reference - <title>`), and `<source>
/// source notes` with the field dictionary and the catalogue of event types, carried as a block the tools read back
/// ([SourceNotes]). It keeps fields and documents only: the event types, documented ones among them (basis
/// documentation), are proposed and confirmed through `catalogue_event_types`, which keeps the catalogue in the same
/// notes doc ([#recordCatalogue]). Called again for the same source, the notes doc is updated: what earlier calls
/// recorded stays unless given again.
class RecordSourceNotesTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RecordSourceNotesTool.class);

    static final String NAME = "record_source_notes";
    static final String BUILD_ARG = "build";
    static final String SOURCE_ARG = "source";
    static final String SUMMARY_ARG = "summary";
    static final String FIELDS_ARG = "fields";
    static final String REFERENCES_ARG = "references";
    static final String DOCUMENTS_ARG = "documents";
    // The argument event types were once given in: they go through catalogue_event_types now.
    private static final String EVENTS = "events";

    // A note's keys, in the order the notes keep them; the first required key names the note.
    private static final List<String> FIELD_KEYS = List.of("field", "meaning", "type", "example",
            "event_logging_path", "values", "basis", "confidence");
    private static final List<String> FIELD_REQUIRED = List.of("field", "meaning");
    private static final int MAX_NAME = 200;
    // Where a note came from: the user's documentation (the default), or the agent's own knowledge of the source.
    private static final String DOCUMENTATION = "documentation";
    /// A note from the agent's own knowledge of the source, not the user's documentation.
    static final String AGENT_KNOWLEDGE = "agent_knowledge";
    /// A catalogue entry the agent proposed and the user confirmed in a form.
    static final String USER_CONFIRMED = "user_confirmed";
    private static final List<String> CONFIDENCES = List.of("high", "medium", "low");
    /// The notes block's key for the catalogue's confirmation in progress (`catalogue_event_types`): the set last
    /// shown the user and when, and a set the user amended, until the agent sends the amendments.
    static final String CONFIRMING = "confirming";
    /// The confirmation's key for the records of the sample each event type recognises, as last checked (by TypeId),
    /// which the notes' "Event types" table shows.
    static final String RECORDS = "records";
    /// The notes block's key for where the catalogue came from: [#MADE_BY_TOOL] or [#MADE_BY_READ_BACK].
    static final String MADE_BY = "made_by";
    /// A catalogue made by catalogue_event_types, which takes the user through it (its catch-all among it): kept
    /// so when a hand edit is read back into it.
    static final String MADE_BY_TOOL = "catalogue_event_types";
    /// A catalogue read back from a hand-written translation (rebuild_mapping), never one the tool made.
    static final String MADE_BY_READ_BACK = "read back from a hand-written translation";
    // The block keys the catalogue_event_types tool keeps, carried from one write of the notes to the next.
    private static final List<String> CATALOGUE_KEYS = List.of(SourceNotes.EVENT_TYPES, SourceNotes.CATCH_ALL,
            "scope", CONFIRMING, MADE_BY);
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
                             + "dictionary. The notes are read back by the tools: draft_translation_mapping "
                             + "(build=) drafts from them (fields where the dictionary puts them), and "
                             + "write_documentation lists the source fields with their meanings. Promotion puts them "
                             + "beside the feed. The event types the documentation describes are proposed through "
                             + CatalogueEventTypesTool.NAME + " (basis documentation), which keeps the catalogue "
                             + "in the same notes.\n\n"
                             + "For documentation the user gave (pasted, attached, or already in Stroom), and for "
                             + "what you know of a source yourself (a vendor's published fields), each such note "
                             + "marked basis=agent_knowledge with a confidence. Never notes guessed from the "
                             + "sample's values: the draft would follow them over the sample "
                             + "(draft_translation_mapping reads those itself). The result's to_review lists your "
                             + "notes of low confidence: put those to the user first.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addStringProperty(SOURCE_ARG, "Source name, e.g. 'Acme door controller'.")
                        .addStringProperty(SUMMARY_ARG, "What the documentation says about the source, in a few "
                                                        + "lines.")
                        .addProperty(FIELDS_ARG, list("Field dictionary condensed from the documentation.",
                                fieldNote()))
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
        if (!objects(call, EVENTS).isEmpty()) {
            // The event types go through the catalogue, checked against every sampled record and confirmed by the
            // user; recorded here, they were never checked.
            throw new ToolRefusal("Nothing recorded: record_source_notes keeps fields and documents only. Propose "
                                  + "the event types the documentation describes through "
                                  + CatalogueEventTypesTool.NAME + " build=" + build + " proposals=[{op: add, "
                                  + "type_id, title, description, event_detail, recognised_by, basis: "
                                  + "documentation}], which checks them against the records; send the fields and "
                                  + "documents here without events.");
        }
        final String givenSummary = NullSafe.string(rawArg(call, SUMMARY_ARG));
        final List<Map<String, Object>> givenFields = notes(call, FIELDS_ARG, FIELD_KEYS, FIELD_REQUIRED);
        final List<Map<String, Object>> pathProblems = unknownPaths(context, build, givenFields);
        final List<Map<String, Object>> documents = documents(call);
        // Called again for the same source (a long manual sent a part at a time), the build's docs are updated, not
        // made twice: the build refuses a second doc of one name.
        final Map<String, WriteGuard.BuildDoc> contents = contents(build);
        final List<Map<String, Object>> keptDocs = keep(context.session(), contents, build, source, documents);

        final String notesName = notesName(contents, source);
        final Map<String, Object> earlier = earlierNotes(contents, notesName);
        final String summary = NullSafe.isBlankString(givenSummary)
                ? text(earlier.get("summary"))
                : givenSummary;
        // What earlier calls recorded stays, unless this one gives the same field again.
        final List<Map<String, Object>> fields = merged(earlier.get("fields"), givenFields, FIELD_KEYS,
                FIELD_REQUIRED);
        final Set<String> references = new LinkedHashSet<>(JsonValues.strings(earlier.get("references")));
        references.addAll(stringListArg(call, REFERENCES_ARG));
        final Set<String> keptNames = new LinkedHashSet<>(JsonValues.strings(earlier.get("documents")));
        keptDocs.forEach(doc -> keptNames.add(text(doc.get("name"))));

        final DocumentationDoc notesDoc = written(context.session(), contents, build, notesName,
                notesText(source, summary, fields, List.copyOf(references), List.copyOf(keptNames),
                        catalogueKept(earlier, Map.of())));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", DocumentationDoc.TYPE);
        result.put("uuid", notesDoc.getUuid());
        result.put("name", notesDoc.getName());
        result.put("fields", fields.size());
        result.put("documents_kept", keptDocs);
        final List<String> review = toReview(fields, "field");
        if (!review.isEmpty()) {
            // The agent's own notes it is least sure of: for the user to confirm or correct first.
            result.put("to_review", review);
        }
        if (!pathProblems.isEmpty()) {
            result.put("path_problems", pathProblems);
        }
        result.put("hint", (pathProblems.isEmpty()
                ? ""
                : pathProblems.size() + " event_logging_path(s) aren't in the event-logging schema and were left "
                  + "out (path_problems says what is allowed where each stops): call again with just those fields "
                  + "and corrected paths; the rest is kept. ")
                           + "draft_translation_mapping with build= drafts from these notes. The event types the "
                           + "documentation describes go through " + CatalogueEventTypesTool.NAME + " (basis "
                           + "documentation): checked against the records and confirmed by the user.");
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

    /// Records the build's catalogue of event types ([Catalogue]) in its source notes, as `catalogue_event_types`
    /// keeps it: the JSON block's `event_types`, `catch_all` and `scope` (replaced whole), and the confirmation in
    /// progress; the field dictionary, documents and references stay as earlier calls recorded them.
    ///
    /// @param context    The call's context.
    /// @param build      The build.
    /// @param source     The source's name (its notes doc is `<source> source notes`; the build's one notes doc
    ///                   when it has one already).
    /// @param catalogue  The catalogue.
    /// @param confirming The confirmation in progress ([#CONFIRMING]), or null for none.
    /// @return The notes doc's `uuid` and `name`.
    Map<String, Object> recordCatalogue(final ToolCallContext context,
                                        final String build,
                                        final String source,
                                        final Catalogue catalogue,
                                        final Map<String, Object> confirming) {
        final Map<String, WriteGuard.BuildDoc> contents = contents(build);
        final String notesName = notesName(contents, source);
        final Map<String, Object> earlier = earlierNotes(contents, notesName);
        final Map<String, Object> kept = new LinkedHashMap<>(catalogue.toJson());
        kept.put(CONFIRMING, NullSafe.map(confirming));
        kept.put(MADE_BY, MADE_BY_TOOL);
        final DocumentationDoc doc = written(context.session(), contents, build, notesName,
                textWithCatalogue(source, earlier, kept));
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", doc.getUuid());
        out.put("name", doc.getName());
        return out;
    }

    /// @param notes A source notes doc's notes ([SourceNotes#readNotes]).
    /// @return True if its catalogue was made by catalogue_event_types ([#MADE_BY]); notes written before the origin
    /// was kept are taken so when they keep the confirmation's state ([#CONFIRMING]), which only that tool writes.
    static boolean madeByTheTool(final Map<String, Object> notes) {
        final Object madeBy = NullSafe.map(notes).get(MADE_BY);
        return madeBy == null
                ? NullSafe.map(notes).containsKey(CONFIRMING)
                : MADE_BY_TOOL.equals(madeBy);
    }

    /// The build's source notes doc, as [#recordCatalogue] writes it.
    ///
    /// @param build  The build.
    /// @param source The source's name (its notes doc is `<source> source notes` when the build has none yet).
    /// @return The doc: its name always, its UUID once it exists (else null).
    DocRef notesDoc(final String build, final String source) {
        final Map<String, WriteGuard.BuildDoc> contents = contents(build);
        final String name = notesName(contents, NullSafe.string(source));
        final WriteGuard.BuildDoc there = contents.get(DocumentationDoc.TYPE + "\n" + name);
        return there == null
                ? new DocRef(DocumentationDoc.TYPE, null, name)
                : there.docRef();
    }

    /// The one place a source notes doc's text is made with its catalogue (for `catalogue_event_types`, and for
    /// [CatalogueNotes#write], a hand edit read back): the JSON block's catalogue keys and the "Event types" table
    /// written together, so the table never goes stale; the field dictionary, documents and references as the notes
    /// hold them.
    ///
    /// @param source  The source's name.
    /// @param earlier The notes as they are ([SourceNotes#readNotes]), or empty for none.
    /// @param given   The catalogue's keys to write (`event_types`, `catch_all`, `scope`, [#CONFIRMING]); those not
    ///                given are kept as the notes have them.
    /// @return The doc's text.
    static String textWithCatalogue(final String source,
                                    final Map<String, Object> earlier,
                                    final Map<String, Object> given) {
        final List<Map<String, Object>> fields = merged(earlier.get("fields"), List.of(), FIELD_KEYS,
                FIELD_REQUIRED);
        final String summary = NullSafe.nonBlankStringElse(text(earlier.get("summary")),
                "The source's event types, as the agent proposed them and the user confirmed them.");
        return notesText(source, summary, fields, JsonValues.strings(earlier.get("references")),
                JsonValues.strings(earlier.get("documents")), catalogueKept(earlier, given));
    }

    // The catalogue's keys of the earlier notes, with those given in their place; an empty value removes one.
    private static Map<String, Object> catalogueKept(final Map<String, Object> earlier,
                                                     final Map<String, Object> given) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final String key : CATALOGUE_KEYS) {
            final Object value = given.containsKey(key)
                    ? given.get(key)
                    : earlier.get(key);
            if (SourceNotes.EVENT_TYPES.equals(key) && value instanceof List<?>
                || value instanceof final Map<?, ?> map && !map.isEmpty()
                || value instanceof final String text && !text.isBlank()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private Map<String, WriteGuard.BuildDoc> contents(final String build) {
        final Map<String, WriteGuard.BuildDoc> contents = new LinkedHashMap<>();
        guard.folderContents(build).forEach(doc -> contents.putIfAbsent(doc.type() + "\n" + doc.name(), doc));
        return contents;
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

    /// @param context The call's context.
    /// @param build   The build.
    /// @return The event-logging schema of the build's version (else the newest held), or null when unreadable.
    EventSchema schemaOrNull(final ToolCallContext context, final String build) {
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
        final DocumentationDoc made = guard.createFilled(DocumentationDoc.TYPE, name, build,
                ref -> documentWrites.writeText(ref, text), false, null);
        if (name.endsWith(SourceNotes.NOTES_SUFFIX)) {
            // Notes made after the translation was written: its XSLT links to them now.
            SourceNotesLinks.linkTranslations(guard, documentAccess, build);
        }
        return made;
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

    /// The text of a source notes document: a markdown field dictionary, the catalogue's "Event types" table (the
    /// catch-all's row last) and sources, then the notes as a block the tools read back.
    ///
    /// @param source     The log source the notes are of.
    /// @param summary    What the source is.
    /// @param fields     The field dictionary's rows.
    /// @param references Where the notes came from, e.g. vendor documentation.
    /// @param keptNames  The names of the source documents kept in the build.
    /// @param catalogue  The catalogue's keys ([SourceNotes#CATALOGUE_KEYS]) and the confirmation in progress; none
    ///                   before the source is catalogued.
    /// @return The document's text.
    static String notesText(final String source,
                            final String summary,
                            final List<Map<String, Object>> fields,
                            final List<String> references,
                            final List<String> keptNames,
                            final Map<String, Object> catalogue) {
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
        final Catalogue read = SourceNotes.catalogue(catalogue);
        if (read != null) {
            final Map<String, Object> confirming = NullSafe.map(JsonValues.map(catalogue.get(CONFIRMING)));
            lines.addAll(CatalogueTables.notesTable(read, NullSafe.map(JsonValues.map(confirming.get(RECORDS)))));
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
        block.putAll(catalogue);
        block.put("fields", fields);
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
