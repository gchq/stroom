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

import stroom.ai.impl.tools.AcceptedErrors;
import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.InstructionRules.FieldMappingLayout;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// What write_documentation reads and writes besides the Field mapping section: where a pipeline's documentation is,
/// the streams it is generated from, the user's errors accepted as benign, and the version control of the doc. Read and
/// written as the user.
class BuildDocumentation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuildDocumentation.class);

    /// What a working copy of a Documentation doc takes from the doc it copies.
    static final List<String> DOC_FIELDS = List.of("data", "documentation");

    private final WriteGuard guard;
    private final BuildFacts facts;
    private final BuildRecords buildRecords;
    private final DocumentAccess documentAccess;
    private final DocumentPlaces places;
    private final DocumentWrites writes;
    private final StreamReader streamReader;
    private final StreamSummaries streamSummaries;
    private final AgentInstructions agentInstructions;
    private final TranslationBuilds translationBuilds;
    private final XsltHistory history;

    /// @param guard             Finds a document's build and working copy, and makes the doc in the build.
    /// @param facts             Lists a build's documents.
    /// @param buildRecords      Gives the streams a pipeline last stepped clean on.
    /// @param documentAccess    Reads pipelines, checking View.
    /// @param places            Finds the documentation beside a promoted pipeline.
    /// @param writes            Reads and writes Documentation docs.
    /// @param streamReader      Reads streams' meta.
    /// @param streamSummaries   Triages the errors processing the streams gave.
    /// @param agentInstructions Reads the AGENTS docs' Documentation layout.
    /// @param translationBuilds Reads the source notes kept in the build.
    /// @param history           Says who made a change, and when.
    @Inject
    BuildDocumentation(final WriteGuard guard,
                       final BuildFacts facts,
                       final BuildRecords buildRecords,
                       final DocumentAccess documentAccess,
                       final DocumentPlaces places,
                       final DocumentWrites writes,
                       final StreamReader streamReader,
                       final StreamSummaries streamSummaries,
                       final AgentInstructions agentInstructions,
                       final TranslationBuilds translationBuilds,
                       final XsltHistory history) {
        this.guard = guard;
        this.facts = facts;
        this.buildRecords = buildRecords;
        this.documentAccess = documentAccess;
        this.places = places;
        this.writes = writes;
        this.streamReader = streamReader;
        this.streamSummaries = streamSummaries;
        this.agentInstructions = agentInstructions;
        this.translationBuilds = translationBuilds;
        this.history = history;
    }

    /// @param build A build.
    /// @return Its documents, as the plan lists them.
    List<Map<String, Object>> buildDocs(final String build) {
        return facts.buildDocs(build);
    }

    /// Where a pipeline's documentation is.
    ///
    /// @param build    The build.
    /// @param pipeline The pipeline.
    /// @return Its name, the production pipeline a working copy is of, the build's docs, the doc of that name in
    /// the build, and one beside the pipeline outside it, changed through a working copy.
    /// @throws ToolRefusal if the production pipeline of a working copy can't be read.
    Placed documentationOf(final String build, final PipelineDoc pipeline) {
        final DocRef ref = new DocRef(PipelineDoc.TYPE, pipeline.getUuid(), pipeline.getName());
        String name = pipeline.getName();
        DocRef beside = null;
        final String copyOf = guard.copyOf(ref).orElse(null);
        if (copyOf != null) {
            // An in-place change (a working copy): the documentation is the production pipeline's, under its name,
            // and its existing doc beside it is changed through a working copy that promotion writes back after a
            // backup.
            final PipelineDoc original = documentAccess.read(new DocRef(PipelineDoc.TYPE, copyOf), PipelineDoc.class)
                    .orElseThrow(() -> new ToolRefusal("Pipeline " + copyOf + " can't be read: it doesn't exist, or "
                                                       + "you don't have permission to view it"));
            name = original.getName();
            beside = places.documentationBeside(new DocRef(PipelineDoc.TYPE, copyOf, name), name).orElse(null);
        }
        final List<Map<String, Object>> inBuild = buildDocs(build);
        if (copyOf == null && !inBuild(inBuild, pipeline.getUuid())) {
            // A pipeline outside the build, promoted already: its documentation beside it is changed the same way,
            // through a working copy in the build that promotion writes back (seen in VS Code: documented after
            // promotion, a second doc of its name was made in a new workspace folder and the promoted one left as
            // it was).
            beside = places.documentationBeside(ref, pipeline.getName()).orElse(null);
        }
        return new Placed(name, copyOf, inBuild, named(inBuild, DocumentationDoc.TYPE, name), beside);
    }

    /// The streams the build's record says the pipeline last stepped clean on.
    ///
    /// @param pipelineUuid A pipeline.
    /// @return The streams; none outside a build or before.
    List<Long> steppedStreams(final String pipelineUuid) {
        try {
            final Optional<String> build = guard.buildOf(new DocRef(PipelineDoc.TYPE, pipelineUuid));
            if (build.isEmpty()) {
                return List.of();
            }
            final List<Long> ids = new ArrayList<>();
            if (buildRecords.entry(build.get(), pipelineUuid).get("stepped_streams") instanceof final List<?> list) {
                list.forEach(id -> ids.add(((Number) id).longValue()));
            }
            return ids;
        } catch (final RuntimeException e) {
            // A default: without it, the call says to give stream_ids.
            LOGGER.debug(() -> LogUtil.message("No stepped streams recorded for {}: {}", pipelineUuid, e.getMessage()));
            return List.of();
        }
    }

    /// An Events stream this pipeline made, in place of the raw stream it was made from.
    ///
    /// @param streamIds    The streams given.
    /// @param pipelineUuid The events pipeline.
    /// @return The streams, and a note for each replaced.
    Parents rawParents(final List<Long> streamIds, final String pipelineUuid) {
        final Set<Long> out = new LinkedHashSet<>();
        final List<String> notes = new ArrayList<>();
        for (final long streamId : streamIds) {
            Meta meta;
            try {
                meta = streamReader.meta(streamId);
            } catch (final RuntimeException e) {
                meta = null;
            }
            final Long parent = meta == null
                    ? null
                    : meta.getParentMetaId();
            if (parent != null && StreamTypeNames.EVENTS.equals(meta.getTypeName())
                && pipelineUuid.equals(meta.getPipelineUuid())) {
                out.add(parent);
                notes.add("stream " + streamId + " is this pipeline's Events output: documented from raw stream "
                          + parent + ", which it was made from");
            } else {
                out.add(streamId);
            }
        }
        return new Parents(List.copyOf(out), notes);
    }

    /// The field dictionary of the vendor documentation kept in the build (record_source_notes).
    ///
    /// @param build The build.
    /// @return Its fields; none when there are none.
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> vendorNotes(final String build) {
        try {
            return NullSafe.list((List<Map<String, Object>>) translationBuilds.notesInBuild(build).get("fields"));
        } catch (final RuntimeException e) {
            // Descriptions are then the agent's and the schemas'.
            return List.of();
        }
    }

    /// @param build The build.
    /// @return The source notes kept in it, read as one.
    Map<String, Object> notesInBuild(final String build) {
        return translationBuilds.notesInBuild(build);
    }

    /// The Field mapping layout the user asked for, else the applicable AGENTS doc's, else by event type (asked for by
    /// the user).
    ///
    /// @param session The session, for the AGENTS docs' audit.
    /// @param ref     The pipeline or index documented.
    /// @param asked   The layout asked for, or null.
    /// @return The layout, and what to tell the agent of it.
    Layout layout(final ToolSession session, final DocRef ref, final String asked) {
        if (asked != null) {
            return new Layout(asked, List.of());
        }
        final Optional<FieldMappingLayout> ruled = agentInstructions.layoutRuled(session, ref);
        if (ruled.isEmpty()) {
            return new Layout(FieldDocs.BY_EVENT_TYPE, List.of());
        }
        return new Layout(ruled.get().layout(), List.of("Field mapping laid out "
                                                        + ruled.get().layout().replace('_', ' ') + ", as the AGENTS "
                                                        + "doc in " + NullSafe.nonBlankStringElse(ruled.get().from(),
                "(root)") + " says"));
    }

    /// @param previous The doc the documentation was written in before, or null.
    /// @return The field descriptions kept with it.
    Map<String, String> keptDescriptions(final DocRef previous) {
        return previous == null
                ? Map.of()
                : FieldDocs.readDescriptions(writes.text(previous));
    }

    /// @param previous The doc the documentation was written in before, or null.
    /// @return Where each kept description's field came from when it was described; none for a doc written before
    /// they were kept.
    Map<String, String> keptDescriptionSources(final DocRef previous) {
        return previous == null
                ? Map.of()
                : FieldDocs.readDescriptionSources(writes.text(previous));
    }

    /// The error groups processing the streams gave, from the Error streams this pipeline wrote for them.
    ///
    /// @param pipelineUuid The pipeline.
    /// @param streamIds    Its sample streams.
    /// @return The groups; null when no streams were given.
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> errorGroups(final String pipelineUuid, final List<Long> streamIds) {
        if (streamIds.isEmpty()) {
            return null;
        }
        final List<Map<String, Object>> groups = new ArrayList<>();
        for (final long streamId : streamIds) {
            final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                    .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, streamId)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.ERROR)
                    .build();
            final List<MetaRow> children = NullSafe.list(NullSafe.get(streamReader.find(expression, 20, true),
                    ResultPage::getValues));
            for (final MetaRow child : children) {
                final Meta meta = child.getMeta();
                if (meta != null && pipelineUuid.equals(NullSafe.nonBlankStringElse(meta.getPipelineUuid(),
                        pipelineUuid))) {
                    groups.addAll(NullSafe.list((List<Map<String, Object>>) streamSummaries.summariseErrors(
                            meta.getId()).get("groups")));
                }
            }
        }
        return groups;
    }

    /// The Errors section: what processing the sample gave, and the errors the user accepted as benign.
    ///
    /// @param groups   The error groups, or null when no streams were given.
    /// @param accepted The errors accepted.
    /// @return The section's text.
    static String errorsSection(final List<Map<String, Object>> groups, final List<AcceptedErrors.Entry> accepted) {
        final List<String> lines = new ArrayList<>();
        if (groups == null) {
            lines.add("Not measured: no processed streams were given.");
        } else if (groups.isEmpty()) {
            lines.add("Processing the sample streams produced no errors.");
        } else {
            lines.addAll(List.of("Errors processing the sample streams produced, by kind:", "",
                    "| Class | Element | Severity | Count | Example | Note |",
                    "| --- | --- | --- | --- | --- | --- |"));
            for (final Map<String, Object> g : groups) {
                final String example = first(firstMessage(g.get("examples")), 160).replace("|", "\\|")
                        .replace("\n", " ");
                lines.add("| " + g.get("class") + " | `" + g.get("element") + "` | " + g.get("severity") + " | "
                          + g.get("count") + " | " + example + " | " + (Boolean.TRUE.equals(g.get("accepted"))
                        ? NullSafe.string(Objects.toString(g.get("reason"), ""))
                        : "") + " |");
            }
        }
        if (!accepted.isEmpty()) {
            lines.addAll(List.of("", "Accepted as benign by the user, so not raised again:", ""));
            for (final AcceptedErrors.Entry e : accepted) {
                lines.add("- `" + NullSafe.nonBlankStringElse(e.element(), "any element") + "`: "
                          + first(NullSafe.string(e.example()), 160) + " (" + e.accepted() + "): " + e.reason());
            }
            lines.addAll(List.of("", AcceptedErrors.block(accepted)));
        }
        return String.join("\n", lines) + "\n";
    }

    /// `needs_descriptions`: the round asking the agent what each field means (asked for by the user: the schema's
    /// generic words, or 'the x value recorded in a Data element of Send', add nothing a reader can't see for
    /// themselves).
    ///
    /// @param described The descriptions, with those wanted.
    /// @return The round.
    static Map<String, Object> needsDescriptions(final Descriptions described) {
        final List<Map<String, Object>> fields = new ArrayList<>(described.wanted().values());
        final boolean shared = fields.stream().anyMatch(field -> JsonValues.truthy(field.get("under")));
        final Map<String, Object> round = new LinkedHashMap<>();
        round.put("status", "needs_descriptions");
        round.put("fields", fields);
        round.put("hint", "Nothing is written yet. For each field, say in one short sentence what it means for this "
                          + "source, for someone searching or reading the data: infer it from the field's name, "
                          + "where it comes from (from) and its sample values, and the user's source notes where "
                          + "they say. E.g. a Data value 'subject' under Send with values like 'Invoice 4411' is "
                          + "'Subject line of the email sent'; a CEF custom slot (cs1, deviceCustomString1) is what "
                          + "this source puts in it. schemas_say is what the vendor's documentation "
                          + "(record_source_notes) and the schemas say of it (event-logging for where it comes "
                          + "from, ECS for an index field, ArcSight's dictionary for a CEF key): build on them where "
                          + "they fit this source, the vendor's first, and use them where its values add nothing; "
                          + "never only repeat their generic words, nor where it comes from, which the table shows. "
                          + "Where the values don't make the meaning clear, say what they look like and that its "
                          + "meaning is unconfirmed, rather than guess."
                          + (fields.stream().anyMatch(field -> field.containsKey("was"))
                ? " A field with was has been described before, when it came from elsewhere: was is what that said; "
                  + "describe it as it is now."
                : "")
                          + (shared
                ? " A field named Data[@Name='x'] is a Data value read from the same source under every element in "
                  + "under: one description covers it under all of them (only where it means something else "
                  + "under one, describe that one by its full path too)."
                : "")
                          + " Then call write_documentation again with the same arguments plus "
                          + "field_descriptions={field: description}.");
        return round;
    }

    /// The doc's new text: its version control rows as they were, and this change pending until the build is promoted,
    /// when the build's changes become one row.
    ///
    /// @param session The session of the call.
    /// @param text    The doc's new text, titled.
    /// @param old     Its text before.
    /// @param change  What changed and why.
    /// @param kept    The mapping the documentation was generated from, or null.
    /// @return The text to write.
    String withChange(final ToolSession session,
                      final String text,
                      final String old,
                      final String change,
                      final KeptMappings.KeptXslt kept) {
        return DocumentationVersions.withPending(text, old, change, history.changedBy(session),
                DocumentationVersions.codeOf(kept), history.now());
    }

    /// @return Today's date in UTC, as accepted errors are dated.
    String today() {
        return history.now().toString().substring(0, 10);
    }

    /// Makes a working copy of a Documentation doc outside the build, in it: its text carries on, and promotion
    /// writes it back over the doc, after a backup.
    ///
    /// @param name   The copy's name.
    /// @param build  The build.
    /// @param beside The doc copied.
    /// @param role   The role subfolder, or null.
    /// @return The copy.
    DocRef workingCopy(final String name, final String build, final DocRef beside, final String role) {
        final DocRef made = guard.create(DocumentationDoc.TYPE, name, build, beside.getUuid(), false, role);
        writes.copyFields(beside, made, DOC_FIELDS);
        return made;
    }

    /// What became of the documentation doc, said plainly: seen, a doc just created came back as updated: false, and
    /// the agent spent two or three calls checking it had been written.
    ///
    /// @param existing The doc in the build that was updated, or null.
    /// @param beside   The doc outside it a working copy was made of, or null.
    /// @return What became of it.
    static String written(final DocRef existing, final DocRef beside) {
        if (existing != null) {
            return "updated";
        }
        return beside != null
                ? "created, as a working copy of the promoted doc (written back over it at promotion)"
                : "created";
    }

    /// @param docs A build's documents.
    /// @param uuid A document.
    /// @return True if it is one of them.
    static boolean inBuild(final List<Map<String, Object>> docs, final String uuid) {
        return docs.stream().anyMatch(doc -> uuid.equals(doc.get("uuid")));
    }

    /// @param docs A build's documents.
    /// @param type A type.
    /// @param name A name.
    /// @return The build's document of the type and name, or null.
    static DocRef named(final List<Map<String, Object>> docs, final String type, final String name) {
        return docs.stream()
                .filter(doc -> type.equals(doc.get("type")) && name.equals(doc.get("name")))
                .map(doc -> new DocRef(type, String.valueOf(doc.get("uuid")), name))
                .findFirst()
                .orElse(null);
    }

    private static String firstMessage(final Object examples) {
        if (examples instanceof final List<?> list && !list.isEmpty() && list.getFirst() instanceof final Map<?, ?> m
            && m.get("message") != null) {
            return String.valueOf(m.get("message"));
        }
        return "";
    }

    private static String first(final String text, final int count) {
        return text.substring(0, Math.min(count, text.length()));
    }


    // --------------------------------------------------------------------------------


    /// Where a pipeline's documentation is.
    ///
    /// @param name     Its name: the pipeline's, or for a working copy the production pipeline's.
    /// @param copyOf   The production pipeline a working copy is of, or null.
    /// @param inBuild  The build's documents.
    /// @param existing The doc of that name in the build, or null.
    /// @param beside   The doc beside the pipeline outside the build, or null.
    record Placed(String name, String copyOf, List<Map<String, Object>> inBuild, DocRef existing, DocRef beside) {

    }

    /// Streams, with the raw streams in place of an events pipeline's own Events.
    ///
    /// @param streamIds The streams.
    /// @param notes     What was replaced.
    record Parents(List<Long> streamIds, List<String> notes) {

    }

    /// The Field mapping layout.
    ///
    /// @param layout [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param notes  What to tell the agent of it.
    record Layout(String layout, List<String> notes) {

    }
}
