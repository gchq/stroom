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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.DocumentationText;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.fielddoc.IndexFieldDocs;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// write_documentation for an existing index (the Stroom MCP server's `tools/builds.py` `_document_index` and
/// `_own_indexing_pipeline`): its Field mapping section generated from a survey of the index through Stroom, the
/// user confirming which index doc first. A doc already beside the index doc is changed through a working copy,
/// written back (after a backup) on promotion.
class IndexDocumentation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexDocumentation.class);

    private static final String INDEXING = "Indexing";

    private final BuildDocumentation docs;
    private final DocumentAccess documentAccess;
    private final DocumentPlaces places;
    private final DocumentWrites writes;
    private final PipelineShapes pipelineShapes;
    private final Consent consent;
    private final IndexSurveys indexSurveys;
    private final IndexFields indexFields;
    private final EventVersions eventVersions;
    private final WriteGuard guard;

    /// @param docs           Where documentation is, and its version control.
    /// @param documentAccess Reads the index doc, checking View.
    /// @param places         Finds the index doc's folder and the doc beside it.
    /// @param writes         Writes the Documentation doc.
    /// @param pipelineShapes Reads the build's pipelines.
    /// @param consent        Asks the user to confirm the index doc.
    /// @param indexSurveys   Surveys the index through Stroom.
    /// @param indexFields    Looks again for fields a feeding pipeline writes.
    /// @param eventVersions  The event-logging schema, for descriptions.
    /// @param guard          Makes the doc in the build.
    @Inject
    IndexDocumentation(final BuildDocumentation docs,
                       final DocumentAccess documentAccess,
                       final DocumentPlaces places,
                       final DocumentWrites writes,
                       final PipelineShapes pipelineShapes,
                       final Consent consent,
                       final IndexSurveys indexSurveys,
                       final IndexFields indexFields,
                       final EventVersions eventVersions,
                       final WriteGuard guard) {
        this.docs = docs;
        this.documentAccess = documentAccess;
        this.places = places;
        this.writes = writes;
        this.pipelineShapes = pipelineShapes;
        this.consent = consent;
        this.indexSurveys = indexSurveys;
        this.indexFields = indexFields;
        this.eventVersions = eventVersions;
        this.guard = guard;
    }

    /// `_document_index`: the documentation of an existing index.
    ///
    /// @param call    The call, whose id tells its forms apart.
    /// @param context The call's context.
    /// @param request What to document.
    /// @return The result, or the round to return (`needs_confirmation`, `needs_descriptions`).
    /// @throws ToolRefusal if it can't be documented, saying why.
    Map<String, Object> documentIndex(final ToolCall call, final ToolCallContext context, final Request request) {
        final DocRef ref = indexDoc(request.indexUuid());
        final String indexName = ElasticIndexDoc.TYPE.equals(ref.getType())
                ? documentAccess.read(ref, ElasticIndexDoc.class).map(ElasticIndexDoc::getIndexName).orElse(null)
                : null;
        if (!request.indexItself()) {
            final Optional<Map<String, Object>> own = ownIndexingPipeline(request.build(), ref, indexName);
            if (own.isPresent()) {
                // Seen: asked to document the indexing pipeline, the agent documented the build's new index doc
                // instead, surveying it for a minute, then documented the pipeline as well.
                throw new ToolRefusal("'" + ref.getName() + "' is an index this build made: its documentation is "
                                      + "its indexing pipeline's. Call write_documentation pipeline_uuid='"
                                      + own.get().get("uuid") + "' ('" + own.get().get("name") + "') "
                                      + "stream_ids=<the Events streams it indexed> instead; index_uuid is for "
                                      + "documenting an index that exists already. Only if the user asked for a doc "
                                      + "of the index doc itself as well: index_itself=true.");
            }
        }
        // The explorer's path is the doc's folder.
        final String folder = places.folderOf(ref).orElse(null);
        final DocRef beside = places.documentationBeside(ref, ref.getName()).orElse(null);
        final DocRef existing = BuildDocumentation.named(docs.buildDocs(request.build()), DocumentationDoc.TYPE,
                ref.getName());
        // Asked before the survey, so the index is read once, after the user confirms.
        final Map<String, Object> asked = new LinkedHashMap<>();
        asked.put("index doc", folder + "/" + ref.getName() + " (" + ref.getType() + ")");
        asked.put("drafted in", "build '" + request.build() + "', as Documentation '" + ref.getName() + "'");
        asked.put("promoted (once you agree)", beside != null
                ? "written back into the existing Documentation '" + ref.getName() + "' beside it, after a backup"
                : "beside the index doc, in " + folder + ", unless you choose another folder");
        final Optional<Map<String, Object>> gate = consent.require(call, context, Consent.Request.confirmation(
                WriteDocumentationTool.NAME, "Document the existing " + (ElasticIndexDoc.TYPE.equals(ref.getType())
                        ? "Elastic Index"
                        : "Lucene Index") + " doc '" + ref.getName() + "' (" + folder + ")", asked,
                request.confirmationId()));
        if (gate.isPresent()) {
            return gate.get();
        }
        Map<String, Object> survey = indexSurveys.surveyIndex(context, ref, 100, true);
        final Map<String, PlannedField> planned = new LinkedHashMap<>();
        final Map<String, List<String>> expected = new LinkedHashMap<>();
        for (final Map<String, Object> fed : maps(survey.get("fed_by"))) {
            final FieldPlan plan = planOf(fed.get("plan"));
            if (plan == null) {
                continue;
            }
            plan.fields().forEach(field -> planned.putIfAbsent(field.name(), field));
            if (plan.discovery() == null) {
                expected.put(String.valueOf(fed.get("name")), plan.fields().stream().map(PlannedField::name)
                        .toList());
            }
        }
        final EventSchema schema = planned.isEmpty()
                ? null
                : schema(context, request.build());
        // Every field a feeding pipeline writes must be in the index, or the doc would leave it out unsaid.
        final Set<String> listed = new LinkedHashSet<>();
        maps(survey.get("fields")).forEach(field -> listed.add(String.valueOf(field.get("name"))));
        Map<String, List<String>> missingBy = Map.of();
        if (expected.values().stream().flatMap(List::stream).anyMatch(field -> !listed.contains(field))) {
            missingBy = indexFields.notInIndex(ref, expected);
            if (missingBy.isEmpty()) {
                // Listed on a later look: surveyed again, so the doc has them and their values.
                survey = indexSurveys.surveyIndex(context, ref, 100, true);
            }
        }
        if (!missingBy.isEmpty() && !request.documentMissingFields()) {
            throw new ToolRefusal("Not documented: " + IndexFields.missingFieldsSaid(missingBy,
                    NullSafe.nonBlankStringElse(indexName, ref.getName())) + " If no event carries a value for a "
                                  + "field yet (Elasticsearch maps a field only once a document has it) and the user "
                                  + "wants the doc now: document_missing_fields=true, which lists them as not in the "
                                  + "index.");
        }
        final List<String> missing = List.copyOf(new LinkedHashSet<>(missingBy.values().stream()
                .flatMap(List::stream)
                .toList()));
        final String layout = docs.layout(context.session(), ref, request.layout()).layout();
        final Map<String, String> descriptions = new LinkedHashMap<>(docs.keptDescriptions(existing != null
                ? existing
                : beside));
        descriptions.putAll(request.fieldDescriptions());
        final Descriptions described = new Descriptions(descriptions, true, docs.vendorNotes(request.build()));
        // Not in the index: as the user chose.
        String section = IndexFieldDocs.existingIndexMarkdown(survey, planned, schema, layout, described);
        if (!described.wanted().isEmpty()) {
            // The user agreed in this call: the call repeated with the descriptions isn't theirs to agree again.
            return BuildDocumentation.needsDescriptions(described, Map.of("confirmation_id", consent.reissue(context,
                    Consent.Kind.CONFIRMATION, WriteDocumentationTool.NAME, asked)));
        }
        if (!described.given().isEmpty()) {
            section = section.stripTrailing() + "\n\n" + FieldDocs.descriptionsBlock(described.given());
        }
        final String summary = IndexFieldDocs.existingIndexSummary(survey);
        final String text = DocumentationText.belowSection(KeptMapping.replaceSection(request.body(), "Field mapping",
                section, false), "Purpose and data", summary);
        final DocumentationDoc doc = write(context, request, ref, text, existing, beside);
        final String destination = beside != null
                ? "written back into the existing doc beside the index doc (" + folder + "), after a backup"
                : "beside the index doc (" + folder + ") unless they choose another folder "
                  + "(destinations={'Documentation': '<folder>'})";
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", DocumentationDoc.TYPE);
        result.put("uuid", doc.getUuid());
        result.put("name", doc.getName());
        result.put("written", BuildDocumentation.written(existing, beside));
        result.put("link", context.links().documentTarget(new DocRef(DocumentationDoc.TYPE, doc.getUuid())));
        result.put("index", survey.get("index"));
        result.put("field_mapping", section);
        result.put("data_surveyed", summary);
        if (!missing.isEmpty()) {
            result.put("not_in_index", missing);
            result.put("note", String.join(", ", missing) + ": written by the pipeline that feeds the index, but not "
                               + "in the index, documented as such (the user chose to). Once the index has them, "
                               + "write_documentation again for their values.");
        }
        result.put("next", "Give the user the link to review the draft. Once they agree, promote_build build='"
                           + request.build() + "': the doc is " + destination + ".");
        return result;
    }

    /// `_own_indexing_pipeline`: the build's own pipeline that writes to the index, when the index doc is the
    /// build's too.
    ///
    /// @param build     The build.
    /// @param index     The index doc.
    /// @param indexName An Elastic Index doc's index name, or null.
    /// @return The pipeline, as the build lists it; empty when there is none.
    Optional<Map<String, Object>> ownIndexingPipeline(final String build, final DocRef index, final String indexName) {
        final List<Map<String, Object>> buildDocs = docs.buildDocs(build);
        if (!BuildDocumentation.inBuild(buildDocs, index.getUuid())) {
            return Optional.empty();
        }
        for (final Map<String, Object> doc : buildDocs) {
            if (!PipelineDoc.TYPE.equals(doc.get("type"))) {
                continue;
            }
            final MergedPipeline merged;
            try {
                merged = pipelineShapes.merged(String.valueOf(doc.get("uuid")));
            } catch (final RuntimeException e) {
                // One Stroom can't load says nothing about the index.
                continue;
            }
            if (IndexDocs.writesTo(merged.properties(), index.getType(), index.getUuid(), indexName)) {
                return Optional.of(doc);
            }
        }
        return Optional.empty();
    }

    private DocRef indexDoc(final String uuid) {
        return places.find(uuid)
                .filter(found -> ElasticIndexDoc.TYPE.equals(found.getType())
                                 || LuceneIndexDoc.TYPE.equals(found.getType()))
                .filter(documentAccess::canView)
                .orElseThrow(() -> new ToolRefusal("No Elastic Index or Lucene Index doc " + uuid + ": find it with "
                                                   + "folder_contents name='*' types=['ElasticIndex', 'Index']"));
    }

    private DocumentationDoc write(final ToolCallContext context,
                                   final Request request,
                                   final DocRef index,
                                   final String text,
                                   final DocRef existing,
                                   final DocRef beside) {
        if (existing != null) {
            guard.checkInBuild(context.session(), existing);
            return written(context, existing, text, request.change());
        }
        if (beside != null) {
            // A working copy of the doc beside the index doc: its change log carries on, and promotion writes it
            // back.
            final DocRef copy = docs.workingCopy(index.getName(), request.build(), beside, INDEXING);
            return written(context, copy, text, request.change());
        }
        return guard.createFilled(DocumentationDoc.TYPE, index.getName(), request.build(),
                made -> written(context, made, text, request.change()), false, INDEXING);
    }

    private DocumentationDoc written(final ToolCallContext context,
                                     final DocRef target,
                                     final String text,
                                     final String change) {
        final DocumentationDoc doc = writes.documentation(target);
        final String old = BuildRecords.bodyText(doc);
        return writes.write(BuildRecords.withBodyText(doc, docs.withChange(context.session(),
                DocumentationText.titled(text, doc.getName()), old, change, null)));
    }

    private EventSchema schema(final ToolCallContext context, final String build) {
        try {
            return eventVersions.eventSchema(eventVersions.chosenOrLatest(context, build));
        } catch (final RuntimeException e) {
            // Descriptions then come from the plan and the sample alone.
            LOGGER.debug(() -> "No event-logging schema for the index's descriptions: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static FieldPlan planOf(final Object plan) {
        return plan instanceof final Map<?, ?> map && !map.isEmpty()
                ? FieldPlan.fromJson((Map<String, Object>) map)
                : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(final Object value) {
        final List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof final List<?> list) {
            list.stream()
                    .filter(Map.class::isInstance)
                    .forEach(item -> out.add((Map<String, Object>) item));
        }
        return out;
    }


    // --------------------------------------------------------------------------------


    /// What to document of an index.
    ///
    /// @param build                 The build.
    /// @param indexUuid             The Elastic Index or Lucene Index doc.
    /// @param body                  The documentation, without its version control.
    /// @param change                What changed, for its version control.
    /// @param confirmationId        From an earlier needs_confirmation reply, or null.
    /// @param layout                The Field mapping layout asked for, or null.
    /// @param documentMissingFields True to document fields a feeding pipeline writes that the index lacks.
    /// @param fieldDescriptions     The field descriptions given.
    /// @param indexItself           True to document an index doc the build made, as well as its pipeline.
    record Request(String build,
                   String indexUuid,
                   String body,
                   String change,
                   String confirmationId,
                   String layout,
                   boolean documentMissingFields,
                   Map<String, String> fieldDescriptions,
                   boolean indexItself) {

    }
}
