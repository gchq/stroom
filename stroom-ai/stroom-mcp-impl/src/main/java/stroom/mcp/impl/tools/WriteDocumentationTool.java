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
import stroom.ai.impl.tools.AcceptedErrors;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.DocumentationText;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `write_documentation` - creates or updates the Documentation doc of a pipeline in a build, or of an existing index,
/// its Field mapping and Errors sections generated. The doc is made in the build as the user ([WriteGuard]), and a doc
/// already promoted is changed through a working copy that promote_build writes back.
class WriteDocumentationTool extends AbstractBuiltInTool {

    static final String NAME = "write_documentation";
    static final String BUILD_ARG = "build";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String MARKDOWN_ARG = "markdown";
    static final String CHANGE_ARG = "change";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String ACCEPT_ERRORS_ARG = "accept_errors";
    static final String INDEX_UUID_ARG = "index_uuid";
    static final String INDEX_ITSELF_ARG = "index_itself";
    static final String DOCUMENT_MISSING_FIELDS_ARG = "document_missing_fields";
    static final String EVALUATION_ARG = "evaluation";
    static final String FIELD_DESCRIPTIONS_ARG = "field_descriptions";
    static final String FIELD_MAPPING_LAYOUT_ARG = "field_mapping_layout";
    /// What an evaluation report's name ends with: `<pipeline> - Evaluation`.
    static final String EVALUATION = " - Evaluation";

    private static final String FIELD_MAPPING = "Field mapping";
    private static final String NOT_AN_OBJECT = "field_descriptions is an object, {field: description}";

    private final BuildDocumentation docs;
    private final FieldMappingSections sections;
    private final IndexDocumentation indexDocumentation;
    private final KeptMappings keptMappings;
    private final PipelineShapes pipelineShapes;
    private final GeneratedDrift mappingDrift;
    private final DocumentAccess documentAccess;
    private final DocumentPlaces places;
    private final DocumentWrites writes;
    private final WriteGuard guard;
    private final Consent consent;
    private final BuildPlan plan;

    @Inject
    WriteDocumentationTool(final BuildDocumentation docs,
                           final FieldMappingSections sections,
                           final IndexDocumentation indexDocumentation,
                           final KeptMappings keptMappings,
                           final PipelineShapes pipelineShapes,
                           final GeneratedDrift mappingDrift,
                           final DocumentAccess documentAccess,
                           final DocumentPlaces places,
                           final DocumentWrites writes,
                           final WriteGuard guard,
                           final Consent consent,
                           final BuildPlan plan) {
        this.docs = docs;
        this.sections = sections;
        this.indexDocumentation = indexDocumentation;
        this.keptMappings = keptMappings;
        this.pipelineShapes = pipelineShapes;
        this.mappingDrift = mappingDrift;
        this.documentAccess = documentAccess;
        this.places = places;
        this.writes = writes;
        this.guard = guard;
        this.consent = consent;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It writes a Documentation doc in the build.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It steps up to 200 records of the sample, or surveys an index.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The pipeline, its XSLT and the index are read as the user; the pipeline is stepped as the "
                      + "user (Stepping) for its Field mapping section; the Documentation doc is created in the build "
                      + "folder and saved as the user (Edit).")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Create or update the Documentation doc for a pipeline in the build (same name as the "
                             + "pipeline), or, with index_uuid, for an existing index (same name as its index doc; "
                             + "the user confirms which index doc first). The reply has the doc's link to give the "
                             + "user. Promotion puts it beside the pipeline or index doc of that name unless the "
                             + "user chooses another folder. The Field mapping section is not taken from the "
                             + "markdown: it is generated from the mapping (or index plan) kept with the pipeline's "
                             + "XSLT, stepped over stream_ids, and put in place of whatever the markdown has there, "
                             + "so it always agrees with the XSLT; an XSLT edited by hand since is documented as it "
                             + "runs (its own events, by the mapping's rules), with a note of the edit. It lists "
                             + "the fields every event type has in one "
                             + "table, then a table per event type (types with roughly the same fields together); "
                             + "field_mapping_layout='compact', or an AGENTS doc's Documentation section, lays it "
                             + "out otherwise. Each field is described by what it means for this source: a "
                             + "needs_descriptions reply lists the fields not yet described, with where each comes "
                             + "from and its sample values, for the agent to describe (field_descriptions); kept "
                             + "with the doc, so an update asks only for new fields. An events pipeline whose XSLT "
                             + "keeps no mapping must bring its own Field mapping section. The Errors section is "
                             + "generated too: every kind of error processing the streams produced (from their "
                             + "Error streams), with counts and an example, and the errors the user accepted as "
                             + "benign, with their reasons; those are kept with the doc, so triage does not raise "
                             + "them again. An update replaces the body and keeps the version control rows and the "
                             + "accepted errors; its change waits, with the build's others, for one row when the "
                             + "build is promoted. Promoted with the pipeline.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, "Build name, e.g. 'acme-door-v1.3'.")
                        .addStringProperty(PIPELINE_UUID_ARG, "The pipeline documented (or index_uuid).")
                        .addStringProperty(MARKDOWN_ARG, "The full documentation, with the sections in "
                                                         + "stroom://guide/documentation (Purpose and data, "
                                                         + "Processing, Field mapping, Output, Conformance, Open "
                                                         + "items). The Field mapping section is generated here from "
                                                         + "the mapping kept with the XSLT (or the index plan); only "
                                                         + "an XSLT written by hand needs it written. The version "
                                                         + "control block is added by the tool.")
                        .addStringProperty(CHANGE_ARG, "What changed and why, in a line, e.g. 'Created' or 'Mapped "
                                                       + "CODE_TO_TOKEN': kept pending, and with the build's other "
                                                       + "changes one row of the doc's version control when the "
                                                       + "build is promoted.")
                        .addProperty(STREAM_IDS_ARG, BuildTranslationXsltTool.streamIdsSchema(
                                "The pipeline's sample streams (raw streams for an events pipeline, Events streams "
                                + "for an indexing pipeline): the Field mapping section is generated from the "
                                + "mapping kept with the XSLT, stepped over them. Required when the XSLT keeps a "
                                + "mapping."))
                        .addProperty(ACCEPT_ERRORS_ARG, ArraySchema.builder()
                                .description("Errors the user says are benign and can be ignored, each with the "
                                             + "error's element, an example message as triage showed it, and the "
                                             + "user's reason in their words. Only those the agent could not resolve "
                                             + "in its own content. Recorded after the user confirms; later reviews "
                                             + "then report them as benign, not as problems.")
                                .items(acceptedError())
                                .build())
                        .addStringProperty(INDEX_UUID_ARG, "Instead of a pipeline, an existing index to document: its "
                                                           + "Elastic Index or Lucene Index doc, confirmed with the "
                                                           + "user. The Field mapping section is generated from a "
                                                           + "survey of the index through Stroom (read_document of "
                                                           + "the index doc shows it), and the doc is named after "
                                                           + "the index doc.")
                        .addBooleanProperty(INDEX_ITSELF_ARG, "With index_uuid of an index this build made: document "
                                                              + "the index doc itself too. Only when the user asks "
                                                              + "for it: by default its documentation is its "
                                                              + "indexing pipeline's, and the call says which "
                                                              + "pipeline to document instead.")
                        .addBooleanProperty(DOCUMENT_MISSING_FIELDS_ARG, "With index_uuid: document the index though "
                                                                         + "fields its feeding pipelines write aren't "
                                                                         + "in it (listed as not in the index). Only "
                                                                         + "when the user chooses it over reindexing "
                                                                         + "or checking again: by default that is "
                                                                         + "refused, saying which fields.")
                        .addBooleanProperty(EVALUATION_ARG, "With pipeline_uuid: the markdown is an evaluation "
                                                            + "report, kept as its own doc, '<pipeline> - "
                                                            + "Evaluation', beside the pipeline once promoted; the "
                                                            + "pipeline's documentation is left as it is. Its "
                                                            + "sections are the report's, nothing generated.")
                        .addProperty(FIELD_DESCRIPTIONS_ARG, TranslationArgs.object(
                                "What each field means for this source, {field: description}, when a "
                                + "needs_descriptions reply asks, each field as the reply names it: inferred from "
                                + "its name, where it comes from and its sample values (and the user's source "
                                + "notes). A Data name (Data[@Name='x']) covers it under every element; a full "
                                + "path, that one row. Kept with the documentation, so later updates ask only for "
                                + "new fields; give one again to change it."))
                        .addStringProperty(FIELD_MAPPING_LAYOUT_ARG, "Only when the user asks for a layout: "
                                                                     + "'by_event_type' (a table of the fields "
                                                                     + "every event type has, then a section with "
                                                                     + "a table per event type) or 'compact' (the "
                                                                     + "common fields, then one table with a row "
                                                                     + "per event type). Left out: an AGENTS doc's "
                                                                     + "Documentation section decides, else "
                                                                     + "by_event_type.")
                        .required(BUILD_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Map<String, Object> result = document(call, context);
            return UserActionEntries.withActions(JsonResults.of(result), result);
        } catch (final ToolRefusal | PipelineUnreadableException e) {
            return JsonResults.refused(e.getMessage());
        }
    }

    private Map<String, Object> document(final ToolCall call, final ToolCallContext context) {
        final String build = stringArg(call, BUILD_ARG);
        if (build == null) {
            throw new ToolRefusal("Give build: the build's name, as start_onboarding or start_build named it.");
        }
        final List<Accepted> accepting = acceptedErrors(call);
        if (accepting.stream().anyMatch(a -> (a.example() + " " + NullSafe.string(a.matches()))
                .contains("EventDetail/Unknown"))) {
            // Seen: the user asked to accept "3 of 50 records come out as EventDetail/Unknown" as benign, with
            // nothing to say which events those were.
            throw new ToolRefusal("Events written as EventDetail/Unknown aren't an error to accept as benign. "
                                  + "Unknown is agreed with the user per rule in build_translation_xslt "
                                  + "(allow_unknown), whose form shows what the records hold; or map them to their "
                                  + "action element. Save the XSLT from its mapping there.");
        }
        final String body = DocumentationVersions.bodyOf(DocumentationText.sectionLevels(NullSafe.string(
                rawArg(call, MARKDOWN_ARG))));
        if (body.isBlank()) {
            throw new ToolRefusal("The documentation is empty: give the full text in markdown, with the sections in "
                                  + "stroom://guide");
        }
        // A new doc's is 'Created'; an update needs its own line (checked below).
        final String change = NullSafe.string(rawArg(call, CHANGE_ARG)).strip();
        final String layout = stringArg(call, FIELD_MAPPING_LAYOUT_ARG);
        if (layout != null && !FieldDocs.LAYOUTS.contains(layout)) {
            throw new ToolRefusal("field_mapping_layout is one of " + ToolText.list(FieldDocs.LAYOUTS)
                                  + ", not '" + layout + "'");
        }
        final Map<String, String> descriptions = fieldDescriptions(call);
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        final String indexUuid = stringArg(call, INDEX_UUID_ARG);
        if (indexUuid != null) {
            if (!accepting.isEmpty()) {
                // Not ignored as stream_ids are: the user's word that errors are benign would be lost unsaid.
                throw new ToolRefusal("accept_errors are for a pipeline's documentation, whose processing made the "
                                      + "errors; an index is surveyed through Stroom instead. Accept them with "
                                      + "write_documentation pipeline_uuid= the pipeline that made them.");
            }
            final Map<String, Object> result = new LinkedHashMap<>(indexDocumentation.documentIndex(call, context,
                    new IndexDocumentation.Request(build, indexUuid, body, change.isEmpty()
                            ? "Created"
                            : change, layout,
                            ToolArgs.flag(call, DOCUMENT_MISSING_FIELDS_ARG), descriptions,
                            ToolArgs.flag(call, INDEX_ITSELF_ARG))));
            if (!streamIds.isEmpty()) {
                // Seen: refused for them, the agent sent its whole markdown again without them. They change nothing
                // here.
                result.put("ignored", Map.of(STREAM_IDS_ARG, "an index is surveyed through Stroom, not stepped over "
                                                             + "streams: they are only for a pipeline's "
                                                             + "documentation"));
            }
            return result;
        }
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            throw new ToolRefusal("Give pipeline_uuid (a pipeline to document) or index_uuid (an existing index)");
        }
        if (ToolArgs.flag(call, EVALUATION_ARG)) {
            return writeEvaluation(context, build, pipelineUuid, body, change);
        }
        return documentPipeline(call, context, new Pipeline(build, readPipeline(pipelineUuid), body, change,
                streamIds, accepting, layout, descriptions));
    }

    private Map<String, Object> documentPipeline(final ToolCall call,
                                                 final ToolCallContext context,
                                                 final Pipeline request) {
        final PipelineDoc pipeline = request.pipeline();
        final String pipelineUuid = pipeline.getUuid();
        final DocRef pipelineRef = new DocRef(PipelineDoc.TYPE, pipelineUuid, pipeline.getName());
        final KeptMappings.KeptXslt kept = keptMappings.keptXslt(pipelineUuid).orElse(null);
        final List<String> noted = new ArrayList<>();
        final BuildDocumentation.Placed placed = docs.documentationOf(request.build(), pipeline);
        // What each field means, as the agent described it from the sample (asked for by the user), kept with the
        // doc.
        final DocRef previous = placed.existing() != null
                ? placed.existing()
                : placed.beside();
        final Map<String, String> given = new LinkedHashMap<>(docs.keptDescriptions(previous));
        given.putAll(request.descriptions());
        // Where each kept one's field came from: one whose field comes from elsewhere now is asked for again (the
        // ones given in this call are of the fields as they are).
        final Map<String, String> givenFrom = new LinkedHashMap<>(docs.keptDescriptionSources(previous));
        givenFrom.keySet().removeAll(request.descriptions().keySet());
        final Descriptions described = new Descriptions(given, givenFrom, true, docs.vendorNotes(request.build()));
        List<Long> streamIds = request.streamIds();
        if (kept != null && KeptMapping.TRANSLATION.equals(kept.kept().kind()) && !streamIds.isEmpty()) {
            // An events pipeline's own Events output, given in place of its raw sample (seen in VS Code): the raw
            // streams they were made from.
            final BuildDocumentation.Parents parents = docs.rawParents(streamIds, pipelineUuid);
            streamIds = parents.streamIds();
            noted.addAll(parents.notes());
        }
        if (kept != null && streamIds.isEmpty()) {
            // The streams the pipeline's current code last stepped clean on, kept in the build's record (seen:
            // prompts and agents left stream_ids out, and every documentation call was refused once first).
            streamIds = docs.steppedStreams(pipelineUuid);
            if (!streamIds.isEmpty()) {
                noted.add("Generated from the streams the pipeline last stepped clean on: " + streamIds);
            }
            if (!streamIds.isEmpty() && KeptMapping.TRANSLATION.equals(kept.kept().kind())) {
                final BuildDocumentation.Parents parents = docs.rawParents(streamIds, pipelineUuid);
                streamIds = parents.streamIds();
                noted.addAll(parents.notes());
            }
        }
        String body = request.body();
        String generated = null;
        // An XSLT edited since its mapping was kept is documented as it runs (its own events, by the mapping's rules,
        // with a note of the edit): documented from the mapping, the section said what the XSLT no longer writes (seen
        // in testing: TWITTER's links, saved with save_xslt, left out, and its mentions shown joined as the mapping
        // wrote them). A section the agent wrote for it is kept as written.
        final Optional<String> edited = kept != null && KeptMapping.TRANSLATION.equals(kept.kept().kind())
                ? mappingDrift.editDrift(kept)
                : Optional.empty();
        if (kept != null && !(edited.isPresent() && ownFieldMapping(body))) {
            if (streamIds.isEmpty()) {
                throw new ToolRefusal(streamsNeeded(kept));
            }
            final BuildDocumentation.Layout laid = docs.layout(context.session(), pipelineRef, request.layout());
            noted.addAll(laid.notes());
            generated = sections.fieldMappingSection(context, request.build(), pipeline, kept, streamIds,
                    laid.layout(), described);
            if (!described.wanted().isEmpty()) {
                return BuildDocumentation.needsDescriptions(described);
            }
            if (!described.given().isEmpty()) {
                generated = generated.stripTrailing() + "\n\n" + FieldDocs.descriptionsBlock(described.given());
                if (!described.sources().isEmpty()) {
                    generated += "\n\n" + FieldDocs.descriptionSourcesBlock(described.sources());
                }
            }
            if (KeptMapping.TRANSLATION.equals(kept.kept().kind())) {
                // What the user's documentation says each source field holds, where the build keeps notes from it.
                final String sourceFields = sourceFieldsMarkdown(docs.notesInBuild(request.build()));
                if (!sourceFields.isEmpty()) {
                    generated = generated.stripTrailing() + "\n\n" + sourceFields;
                }
            }
            body = KeptMapping.replaceSection(body, FIELD_MAPPING, generated, false);
        } else {
            final String stage = pipelineShapes.shape(pipelineUuid).stage();
            if ("indexing".equals(stage) || "discovery".equals(stage)) {
                // An indexing XSLT written by hand keeps no plan: the section comes from the documents it writes.
                if (streamIds.isEmpty()) {
                    throw new ToolRefusal("Give stream_ids (the Events streams the pipeline indexes): the Field "
                                          + "mapping section is generated from the documents it writes from them, "
                                          + "each field with the values the sample gave");
                }
                generated = sections.writtenFieldsSection(context, pipelineUuid, streamIds);
                body = KeptMapping.replaceSection(body, FIELD_MAPPING, generated, false);
            } else if ("forwarding".equals(stage)) {
                // A CEF pipeline written by hand keeps no plan: the section is the mapping its lines imply.
                if (streamIds.isEmpty()) {
                    throw new ToolRefusal("Give stream_ids (the Events streams the pipeline sends): the Field mapping "
                                          + "section is generated from the CEF lines it writes from them");
                }
                generated = sections.cefWrittenSection(context, pipelineUuid, streamIds);
                body = KeptMapping.replaceSection(body, FIELD_MAPPING, generated, false);
            } else if (!body.contains("## Field mapping") && "translation".equals(stage)) {
                final List<String> lost = mappingDrift.lostMappings(pipelineUuid);
                if (!lost.isEmpty()) {
                    throw new ToolRefusal(String.join(" ", lost));
                }
                throw new ToolRefusal("An events pipeline's documentation needs a '## Field mapping' section. Its "
                                      + "XSLT keeps no mapping (it was not saved by build_translation_xslt), so "
                                      + "write the section from read_document and a stepped sample, or save the "
                                      + "XSLT again from its mapping (build_translation_xslt uuid=...) and the "
                                      + "section is generated here");
            }
        }
        final String stamp = docs.today();
        if (!request.accepting().isEmpty()) {
            // The user says these are benign: confirmed by them, as the agent cannot decide it for them.
            final Map<String, Object> details = new LinkedHashMap<>();
            details.put("errors", request.accepting().stream()
                    .map(a -> NullSafe.nonBlankStringElse(a.element(), "any element") + ": "
                              + (NullSafe.isNonEmptyString(a.matches())
                            ? a.matches()
                            : first(a.example(), 200)) + " -- " + a.reason())
                    .toList());
            consent.require(call, context, Consent.Request.confirmation(
                    NAME, "Record " + request.accepting().size() + " kind(s) of error as benign for pipeline '"
                          + pipeline.getName() + "', so they are not raised again", details));
        }
        final List<AcceptedErrors.Entry> newEntries = request.accepting().stream()
                .map(a -> AcceptedErrors.entry(a.element(), a.example(), a.reason(), stamp, a.matches()))
                .toList();
        final List<Map<String, Object>> errors = docs.errorGroups(pipelineUuid, streamIds);
        final DocRef existing = placed.existing();
        final DocRef beside = placed.beside();
        String change = request.change();
        if (change.isEmpty()) {
            if (existing != null || beside != null) {
                throw new ToolRefusal("This updates the pipeline's documentation: give change, one line for its "
                                      + "version control on what changed and why, e.g. 'Mapped CODE_TO_TOKEN'");
            }
            change = "Created";
        }
        final Writer writer = new Writer(context, request.withBody(body), change, kept, newEntries, errors);
        final DocumentationDoc doc;
        if (existing != null) {
            guard.checkInBuild(context.session(), existing);
            doc = writer.write(existing);
        } else if (beside != null) {
            doc = writer.write(docs.workingCopy(placed.name(), request.build(), beside,
                    pipelineShapes.role(pipelineUuid)));
        } else {
            // Beside the pipeline it documents, in the same role subfolder.
            doc = guard.createFilled(DocumentationDoc.TYPE, placed.name(), request.build(), writer::write, false,
                    pipelineShapes.role(pipelineUuid));
        }
        final DocRef docRef = new DocRef(DocumentationDoc.TYPE, doc.getUuid(), doc.getName());
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", DocumentationDoc.TYPE);
        result.put("uuid", doc.getUuid());
        result.put("name", doc.getName());
        result.put("written", BuildDocumentation.written(existing, beside));
        if (placed.copyOf() == null && !BuildDocumentation.inBuild(placed.inBuild(), pipelineUuid) && beside != null) {
            // Not the build's plan (it has no pipeline of its own to make): what happens to this doc.
            final Map<String, Object> copied = new LinkedHashMap<>();
            copied.put("type", beside.getType());
            copied.put("uuid", beside.getUuid());
            copied.put("name", beside.getName());
            result.put("working_copy_of", copied);
            result.put("link", context.links().documentTarget(docRef));
            if (generated != null) {
                result.put("field_mapping", generated);
            }
            result.put("next", "Pipeline '" + pipeline.getName() + "' is promoted, so its documentation is changed "
                               + "through this working copy in build '" + request.build() + "'. Give the user the "
                               + "link to review it; promote_build build='" + request.build() + "' (approved by the "
                               + "user) writes it back over the promoted doc, after a backup.");
            return result;
        }
        result.put("link", context.links().documentTarget(docRef));
        UserActionEntries.withEntries(result, List.of(UserActionEntries.openDocument(context.links(), docRef,
                "review it")));
        if (generated != null) {
            result.put("field_mapping", generated);
        }
        if (!noted.isEmpty()) {
            result.put("note", String.join("; ", noted));
        }
        return plan.withNext(context, request.build(), result);
    }

    // An evaluation report, its own doc beside the pipeline (asked for by the user): saved as the pipeline's
    // documentation, it would have replaced that at promotion. An earlier report beside the pipeline is updated through
    // a working copy, written back at promotion after a backup.
    private Map<String, Object> writeEvaluation(final ToolCallContext context,
                                                final String build,
                                                final String pipelineUuid,
                                                final String body,
                                                final String change) {
        final PipelineDoc pipeline = readPipeline(pipelineUuid);
        final DocRef ref = new DocRef(PipelineDoc.TYPE, pipelineUuid, pipeline.getName());
        final Optional<String> copyOf = guard.copyOf(ref);
        final DocRef target = copyOf.isPresent()
                ? new DocRef(PipelineDoc.TYPE, copyOf.get(), readPipeline(copyOf.get()).getName())
                : ref;
        final String name = target.getName() + EVALUATION;
        final DocRef beside = places.documentationBeside(target, name).orElse(null);
        final DocRef existing = BuildDocumentation.named(docs.buildDocs(build), DocumentationDoc.TYPE, name);
        if ((existing != null || beside != null) && change.isEmpty()) {
            throw new ToolRefusal("This updates the pipeline's evaluation report: give change, one line on what is "
                                  + "new");
        }
        final String role = pipelineShapes.role(pipelineUuid);
        final Writer writer = new Writer(context, null, change.isEmpty()
                ? "Created"
                : change, null, null, null);
        final DocumentationDoc doc;
        if (existing != null) {
            guard.checkInBuild(context.session(), existing);
            doc = writer.writeReport(existing, body);
        } else if (beside != null) {
            doc = writer.writeReport(docs.workingCopy(name, build, beside, role), body);
        } else {
            doc = guard.createFilled(DocumentationDoc.TYPE, name, build, made -> writer.writeReport(made, body),
                    false, role);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", DocumentationDoc.TYPE);
        result.put("uuid", doc.getUuid());
        result.put("name", doc.getName());
        result.put("written", BuildDocumentation.written(existing, beside));
        result.put("link", context.links().documentTarget(new DocRef(DocumentationDoc.TYPE, doc.getUuid())));
        result.put("hint", "Give the user the link. Once they approve, promote_build puts it beside pipeline '"
                           + target.getName() + "'" + (beside != null
                ? " (over the earlier report, after a backup)"
                : "") + ".");
        return result;
    }

    private PipelineDoc readPipeline(final String uuid) {
        return documentAccess.read(new DocRef(PipelineDoc.TYPE, uuid), PipelineDoc.class)
                .orElseThrow(() -> new ToolRefusal("Pipeline " + uuid + " can't be read: it doesn't exist, or you "
                                                   + "don't have permission to view it"));
    }

    // Documentation goes down to the field, with the values the sample gave: it needs the sample.
    private static String streamsNeeded(final KeptMappings.KeptXslt kept) {
        final String kind = kept.kept().kind();
        final boolean discovery = kept.kept().payload() != null
                                  && JsonValues.truthy(kept.kept().payload().get("discovery"));
        final String what = KeptMapping.TRANSLATION.equals(kind) || discovery
                ? "sample raw streams"
                : "cef".equals(kind)
                        ? "Events streams it sends"
                        : "Events streams it indexes";
        final String from = KeptMapping.TRANSLATION.equals(kind)
                ? "mapping"
                : "cef".equals(kind)
                        ? "CEF plan"
                        : "index plan";
        return "Give stream_ids (the pipeline's " + what + "): the Field mapping section is generated from the "
               + from + " kept with the XSLT by stepping them, each field with the values the sample gave";
    }

    /// 'Source fields', for the Field mapping section: each source field
    /// the documentation describes, with its meaning and codes.
    ///
    /// @param notes The build's source notes ([TranslationBuilds#notesInBuild]).
    /// @return The section, or "" when they describe no field.
    static String sourceFieldsMarkdown(final Map<String, Object> notes) {
        final List<String> lines = new ArrayList<>();
        if (notes.get("fields") instanceof final List<?> fields) {
            for (final Object item : fields) {
                if (item instanceof final Map<?, ?> field) {
                    final List<String> codes = new ArrayList<>();
                    if (field.get("values") instanceof final Map<?, ?> values) {
                        values.forEach((code, meaning) -> codes.add("`" + code + "`: " + meaning));
                    }
                    lines.add("| `" + field.get("field") + "` | " + NullSafe.string(field.get("meaning") == null
                            ? null
                            : String.valueOf(field.get("meaning"))).replace("|", "/") + " | "
                              + String.join("; ", codes).replace("|", "/") + " |");
                }
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        final List<String> out = new ArrayList<>(List.of("### Source fields", "", "What the source's documentation "
                                                                                 + "says each field holds.", "",
                "| Source field | Meaning | Codes |", "| --- | --- | --- |"));
        out.addAll(lines);
        return String.join("\n", out) + "\n";
    }

    // The errors the user accepts, as given: a list of objects (or one), or either as JSON text.
    private static List<Accepted> acceptedErrors(final ToolCall call) {
        Object value = argsMap(call).get(ACCEPT_ERRORS_ARG);
        if (value instanceof final String text) {
            if (text.isBlank()) {
                return List.of();
            }
            try {
                value = ToolJson.loads(text.strip());
            } catch (final RuntimeException e) {
                throw new ToolRefusal("accept_errors is a list of objects, {element, example, reason, matches}");
            }
        }
        final List<Object> items = new ArrayList<>();
        if (value instanceof final Map<?, ?> one) {
            items.add(one);
        } else if (value instanceof final List<?> list) {
            items.addAll(list);
        }
        final List<Accepted> accepted = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof final Map<?, ?> map)) {
                throw new ToolRefusal("accept_errors[" + i + "] must be an object, {element, example, reason, "
                                      + "matches}");
            }
            for (final String key : List.of("example", "reason")) {
                if (map.get(key) == null) {
                    throw new ToolRefusal("accept_errors[" + i + "]." + key + " is required");
                }
            }
            accepted.add(new Accepted(text(map.get("element")), String.valueOf(map.get("example")),
                    String.valueOf(map.get("reason")), text(map.get("matches"))));
        }
        return accepted;
    }

    private static Map<String, String> fieldDescriptions(final ToolCall call) {
        Object value = argsMap(call).get(FIELD_DESCRIPTIONS_ARG);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof final String text) {
            if (text.isBlank()) {
                return Map.of();
            }
            try {
                value = ToolJson.loads(text);
            } catch (final RuntimeException e) {
                throw new ToolRefusal(NOT_AN_OBJECT);
            }
        }
        if (!(value instanceof final Map<?, ?> map)) {
            throw new ToolRefusal(NOT_AN_OBJECT);
        }
        final Map<String, String> out = new LinkedHashMap<>();
        map.forEach((field, description) -> out.put(String.valueOf(field), description == null
                ? ""
                : String.valueOf(description)));
        return out;
    }

    private static String text(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }

    // Whether the markdown has a Field mapping section of its own: a table, not a placeholder for the generated one.
    private static boolean ownFieldMapping(final String body) {
        final int at = body.indexOf("## " + FIELD_MAPPING);
        if (at < 0) {
            return false;
        }
        final int next = body.indexOf("\n## ", at + 3);
        final String section = next < 0
                ? body.substring(at)
                : body.substring(at, next);
        return section.lines().anyMatch(line -> line.strip().startsWith("|"));
    }

    private static String first(final String text, final int count) {
        return text.substring(0, Math.min(count, text.length()));
    }

    private static ObjectSchema acceptedError() {
        return ObjectSchema.builder()
                .description("An error the user says is benign, as triage showed it.")
                .addStringProperty("element", "The element it comes from, e.g. 'decorationFilter'.")
                .addStringProperty("example", "An example message, as triage showed it.")
                .addStringProperty("reason", "Why it is benign, in the user's words.")
                .addStringProperty("matches", "The kind of message it covers, with * for the parts that vary, "
                                              + "e.g. 'No HR record for user svc-*'; defaults to the example "
                                              + "(numbers and quoted values vary).")
                .required("example", "reason")
                .build();
    }


    // --------------------------------------------------------------------------------


    /// An error the user accepts as benign.
    ///
    /// @param element The element it comes from, or null for any.
    /// @param example An example message.
    /// @param reason  Why it is benign, in the user's words.
    /// @param matches The kind of message it covers, or null for the example's.
    private record Accepted(String element, String example, String reason, String matches) {

    }

    /// What to document of a pipeline.
    ///
    /// @param build        The build.
    /// @param pipeline     The pipeline.
    /// @param body         The documentation, without its version control.
    /// @param change       What changed, or "".
    /// @param streamIds    The sample streams given.
    /// @param accepting    The errors the user accepts.
    /// @param layout       The Field mapping layout asked for, or null.
    /// @param descriptions The field descriptions given.
    private record Pipeline(String build,
                            PipelineDoc pipeline,
                            String body,
                            String change,
                            List<Long> streamIds,
                            List<Accepted> accepting,
                            String layout,
                            Map<String, String> descriptions) {

        private Pipeline withBody(final String newBody) {
            return new Pipeline(build, pipeline, newBody, change, streamIds, accepting, layout, descriptions);
        }
    }

    // Writes the documentation into a doc: the Errors section, the title, and the change pending in its version
    // control.
    private final class Writer {

        private final ToolCallContext context;
        private final Pipeline request;
        private final String change;
        private final KeptMappings.KeptXslt kept;
        private final List<AcceptedErrors.Entry> newEntries;
        private final List<Map<String, Object>> errors;

        private Writer(final ToolCallContext context,
                       final Pipeline request,
                       final String change,
                       final KeptMappings.KeptXslt kept,
                       final List<AcceptedErrors.Entry> newEntries,
                       final List<Map<String, Object>> errors) {
            this.context = context;
            this.request = request;
            this.change = change;
            this.kept = kept;
            this.newEntries = newEntries;
            this.errors = errors;
        }

        private DocumentationDoc write(final DocRef ref) {
            final DocumentationDoc doc = writes.documentation(ref);
            final String old = BuildRecords.bodyText(doc);
            final List<AcceptedErrors.Entry> accepted = AcceptedErrors.merge(AcceptedErrors.readAccepted(old),
                    newEntries);
            // Only the generated section: the agent's own sections on errors (an evaluation's analysis) stay as
            // written.
            final String text = KeptMapping.replaceSection(request.body(), "Errors",
                    BuildDocumentation.errorsSection(errors, accepted), true);
            return writes.write(BuildRecords.withBodyText(doc, docs.withChange(context.session(),
                    DocumentationText.titled(text, doc.getName()), old, change, kept)));
        }

        private DocumentationDoc writeReport(final DocRef ref, final String body) {
            final DocumentationDoc doc = writes.documentation(ref);
            final String old = BuildRecords.bodyText(doc);
            return writes.write(BuildRecords.withBodyText(doc, docs.withChange(context.session(),
                    DocumentationText.titled(body, doc.getName()), old, change, null)));
        }
    }
}
