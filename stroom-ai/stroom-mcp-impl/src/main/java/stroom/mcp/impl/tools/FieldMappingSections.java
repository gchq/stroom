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

import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.cef.CefField;
import stroom.ai.impl.tools.cef.CefKeys;
import stroom.ai.impl.tools.cef.CefLines;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.cef.CefValue;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.EventFieldDocs;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.fielddoc.IndexFieldDocs;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.SteppingResult;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// The Field mapping section of a pipeline's documentation (the Stroom MCP server's `tools/builds.py`
/// `field_mapping_section`, `written_fields_section`, `cef_written_section`, `_agreed_line` and `_no_events`):
/// generated from the mapping (or index or CEF plan) kept with the pipeline's XSLT, stepped over the sample streams,
/// or for an XSLT written by hand from what it writes. The pipeline is stepped as the user, who needs the Stepping
/// permission, within the session's time limit for a long call.
class FieldMappingSections {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FieldMappingSections.class);

    /// The most records stepped for a section.
    static final int RECORDS = 200;

    private final PipelineStepping stepping;
    private final DraftCefMappingTool cef;
    private final PipelineShapes pipelineShapes;
    private final EventVersions eventVersions;
    private final GeneratedDrift mappingDrift;
    private final StreamReader streamReader;
    private final StreamSummaries streamSummaries;
    private final IndexValues indexValues;

    /// @param stepping        Steps the pipeline over the sample streams.
    /// @param cef             Steps a CEF pipeline, reading its lines and the Events they came from.
    /// @param pipelineShapes  Reads a pipeline's own XSLTs.
    /// @param eventVersions   The event-logging schemas.
    /// @param mappingDrift    Says whether the XSLT is still what its mapping generates.
    /// @param streamReader    Reads the streams' meta.
    /// @param streamSummaries Reads how populated each path of the sample Events is.
    /// @param indexValues     Reads example values from the index for fields the sample has none of.
    @Inject
    FieldMappingSections(final PipelineStepping stepping,
                         final DraftCefMappingTool cef,
                         final PipelineShapes pipelineShapes,
                         final EventVersions eventVersions,
                         final GeneratedDrift mappingDrift,
                         final StreamReader streamReader,
                         final StreamSummaries streamSummaries,
                         final IndexValues indexValues) {
        this.stepping = stepping;
        this.cef = cef;
        this.pipelineShapes = pipelineShapes;
        this.eventVersions = eventVersions;
        this.mappingDrift = mappingDrift;
        this.streamReader = streamReader;
        this.streamSummaries = streamSummaries;
        this.indexValues = indexValues;
    }

    /// `field_mapping_section`: the Field mapping section generated from the kept mapping (stepped over the sample
    /// streams, with each Event marked by its rule), CEF plan (with the values the pipeline writes for the sample
    /// Events) or index plan (with the Events' path population).
    ///
    /// @param context   The call's context.
    /// @param build     The build, for the event-logging version chosen for it.
    /// @param pipeline  The pipeline documented.
    /// @param kept      The mapping kept with its XSLT.
    /// @param streamIds The sample streams.
    /// @param layout    [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described The field descriptions, given and asked for.
    /// @return The section, ending with the digest it was generated from.
    /// @throws ToolRefusal if it can't be generated, saying why.
    String fieldMappingSection(final ToolCallContext context,
                               final String build,
                               final PipelineDoc pipeline,
                               final KeptMappings.KeptXslt kept,
                               final List<Long> streamIds,
                               final String layout,
                               final Descriptions described) {
        final String kind = kept.kept().kind();
        final String section;
        if (KeptMapping.TRANSLATION.equals(kind)) {
            section = translationSection(context, pipeline, kept, streamIds, layout, described);
        } else if ("cef".equals(kind)) {
            section = cefSection(context, pipeline, kept, streamIds, layout, described);
        } else {
            final FieldPlan plan = FieldPlan.fromJson(kept.kept().payload());
            if (plan.discovery() != null) {
                return discoverySection(context, pipeline, kept, plan, streamIds);
            }
            section = indexSection(context, build, pipeline, kept, plan, streamIds, layout, described);
        }
        return section.stripTrailing() + "\n\n" + docMark(kept);
    }

    /// `written_fields_section`: the Field mapping section of an indexing pipeline whose XSLT keeps no plan: from
    /// the documents its own XSLT writes, stepped over the sample streams.
    ///
    /// @param context      The call's context.
    /// @param pipelineUuid The pipeline.
    /// @param streamIds    The Events streams it indexes.
    /// @return The section.
    /// @throws ToolRefusal if it has no XSLT of its own, or the streams give no documents.
    String writtenFieldsSection(final ToolCallContext context,
                                final String pipelineUuid,
                                final List<Long> streamIds) {
        final String element = pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid).stream()
                .filter(entry -> !entry.inheritedFromTemplate() && XsltDoc.TYPE.equals(entry.doc().getType()))
                .map(TranslationDoc::element)
                .findFirst()
                .orElseThrow(() -> new ToolRefusal("The pipeline has no XSLT of its own to document: give it one, or "
                                                   + "document the template"));
        final SteppedPipeline loaded = load(pipelineUuid);
        final List<Map<String, List<String>>> documents = IndexFieldDocs.indexDocuments(
                outputs(context, loaded, streamIds, element, null));
        if (documents.isEmpty()) {
            throw new ToolRefusal("Field mapping not written: stepping streams " + streamIds + " gave no documents. "
                                  + "An indexing pipeline's stream_ids are the Events streams it indexes (or the raw "
                                  + "streams, for discovery).");
        }
        return IndexFieldDocs.writtenFieldsMarkdown(documents);
    }

    /// `cef_written_section`: the Field mapping section of a CEF pipeline whose XSLT keeps no plan: what its lines
    /// carry, from the Events they were made from, and what is wrong with them.
    ///
    /// @param context      The call's context.
    /// @param pipelineUuid The pipeline.
    /// @param streamIds    The Events streams it sends.
    /// @return The section.
    /// @throws ToolRefusal if stepping gives no CEF lines.
    @SuppressWarnings("unchecked")
    String cefWrittenSection(final ToolCallContext context, final String pipelineUuid, final List<Long> streamIds) {
        final DraftCefMappingTool.Stepped stepped = cef.stepped(context, pipelineUuid, streamIds, null, RECORDS);
        if (stepped.lines().isEmpty()) {
            throw new ToolRefusal("Field mapping not written: stepping the pipeline over streams " + streamIds
                                  + " gave no CEF lines");
        }
        final List<XmlElement> given = stepped.events().stream().filter(Objects::nonNull).toList();
        final Map<String, Object> reviewed = CefLines.review(stepped.lines(), given, null);
        final List<String> header = CefLines.parse(stepped.lines().getFirst()).header();
        final Map<String, CefValue> heads = new LinkedHashMap<>();
        for (int i = 0; i < CefKeys.HEADER.size() && i + 1 < header.size(); i++) {
            heads.put(CefKeys.HEADER.get(i).name(), CefValue.constant(header.get(i + 1)));
        }
        final Map<String, List<CefField>> events = new LinkedHashMap<>();
        ((Map<?, ?>) reviewed.get("implied_mapping")).forEach((eventKind, maps) -> {
            final List<CefField> fields = new ArrayList<>();
            ((List<?>) maps).forEach(item -> {
                final Map<?, ?> map = (Map<?, ?>) item;
                fields.add(new CefField((String) map.get("path"), (String) map.get("key"), (String) map.get("label"),
                        null));
            });
            events.put(String.valueOf(eventKind), fields);
        });
        final List<Map<String, String>> notSent = new ArrayList<>();
        ((List<?>) reviewed.get("not_sent")).forEach(item -> {
            final Map<?, ?> map = (Map<?, ?>) item;
            final Map<String, String> entry = new LinkedHashMap<>();
            entry.put("path", (String) map.get("path"));
            entry.put("event_type", (String) map.get("event_type"));
            entry.put("why", "in none of its lines (" + map.get("events") + " events)");
            notSent.add(entry);
        });
        final CefPlan plan = new CefPlan(stepped.sent().get("output"), stepped.sent().get("topic"), null, true,
                heads.get("vendor"), heads.get("product"), heads.get("version"), heads.get("signature"),
                heads.get("name"), heads.get("severity"), List.of(), events, notSent, List.of(), null);
        final StringBuilder section = new StringBuilder("Its XSLT keeps no CEF plan: this is the mapping its lines "
                                                        + "imply, each key matched to the Event value it carries in "
                                                        + "the sample, and the header as the first line has it.\n\n")
                .append(plan.markdown(CefLines.examplesFrom(plan, stepped.lines(), given), FieldDocs.BY_EVENT_TYPE,
                        null));
        final List<String> problems = (List<String>) reviewed.get("problems");
        if (!NullSafe.isEmptyCollection(problems)) {
            section.append("\n### Problems in its lines\n\n");
            problems.forEach(problem -> section.append("- ").append(problem).append('\n'));
        }
        return section.toString();
    }

    /// `_agreed_line`: the agreed Elasticsearch index template, for the Field mapping section.
    ///
    /// @param pipeline The pipeline, whose description keeps the agreement.
    /// @return The line, or "" when none was agreed.
    static String agreedLine(final PipelineDoc pipeline) {
        final Map<String, Object> agreed = KeptMapping.readAgreedTemplate(pipeline == null
                ? null
                : pipeline.getDescription()).orElse(null);
        if (agreed == null) {
            return "";
        }
        final String date = first(String.valueOf(agreed.get("agreed")), 10);
        if (Boolean.TRUE.equals(agreed.get("by_user"))) {
            return "\nElasticsearch index template: the user's own, committed to the cluster (confirmed by them "
                   + date + ", not agreed here).\n";
        }
        final List<String> parts = new ArrayList<>();
        if (agreed.get("component_templates") instanceof final List<?> list) {
            list.forEach(part -> parts.add("`" + part + "`"));
        }
        return "\nElasticsearch index template `" + agreed.get("name") + "`, agreed with the user " + date
               + (parts.isEmpty()
                ? ""
                : ", composed of " + String.join(", ", parts)) + ".\n";
    }

    /// `_no_events`: why stepping an events pipeline over the streams gave no events, stream by stream: missing, of
    /// another type (an Events stream where the raw input belongs), or stopped by an error before the first record.
    ///
    /// Each stream is stepped within the session's time limit for a long call, as the records were: once it is up,
    /// the streams left are named, not stepped.
    ///
    /// @param context   The call's context.
    /// @param pipeline  The pipeline stepped.
    /// @param streamIds The streams.
    /// @param element   The XSLT's element.
    /// @param code      The XSLT stepped with.
    /// @param records   How many records were stepped.
    /// @return What to refuse with.
    String noEvents(final ToolCallContext context,
                    final SteppedPipeline pipeline,
                    final List<Long> streamIds,
                    final String element,
                    final String code,
                    final int records) {
        final List<String> found = PipelineStepping.withinTimeLimit(context, () ->
                whyNoEvents(pipeline, streamIds, element, code));
        return "Field mapping not written: stepping the pipeline over stream_ids gave no events (" + records
               + " records stepped). " + String.join("; ", found) + ". An events pipeline's stream_ids are its raw "
               + "sample streams (Raw Events, as uploaded); build_status lists them.";
    }

    // Why each stream gave no events, until the time for stepping is up.
    private List<String> whyNoEvents(final SteppedPipeline pipeline,
                                     final List<Long> streamIds,
                                     final String element,
                                     final String code) {
        final List<String> found = new ArrayList<>();
        for (int i = 0; i < streamIds.size(); i++) {
            final long streamId = streamIds.get(i);
            if (PipelineStepping.timeIsUp()) {
                found.add(notStepped(streamIds.subList(i, streamIds.size())));
                break;
            }
            final Optional<Meta> meta = metaOrNone(streamId);
            if (meta.isEmpty()) {
                found.add("stream " + streamId + " does not exist");
                continue;
            }
            final String what = "stream " + streamId + " (" + meta.get().getFeedName() + ", "
                                + meta.get().getTypeName() + ")";
            try (final SteppingWalks.Walk walk = stepping.open(pipeline, streamId, Map.of(element, code))) {
                final SteppingResult first = walk.first();
                if (PipelineStepping.isFound(first)) {
                    found.add(what + ": its records produced no Event");
                } else {
                    final Set<String> errors = new LinkedHashSet<>();
                    SteppingWalks.markers(first, null).stream()
                            .map(ErrorTriage.Marker::message)
                            .forEach(errors::add);
                    final List<String> shown = errors.stream().limit(2).toList();
                    found.add(what + ": no record stepped" + (shown.isEmpty()
                            ? ""
                            : " (" + String.join("; ", shown) + ")"));
                }
            } catch (final ToolTimeoutException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                found.add(notStepped(streamIds.subList(i, streamIds.size())));
                break;
            }
        }
        return found;
    }

    private static String notStepped(final List<Long> streamIds) {
        return (streamIds.size() == 1
                ? "stream " + streamIds.getFirst()
                : "streams " + streamIds.stream().map(String::valueOf).collect(Collectors.joining(", ")))
               + " not stepped: the time for stepping ran out";
    }

    private String translationSection(final ToolCallContext context,
                                      final PipelineDoc pipeline,
                                      final KeptMappings.KeptXslt kept,
                                      final List<Long> streamIds,
                                      final String layout,
                                      final Descriptions described) {
        final Map<String, Object> payload = kept.kept().payload();
        final String version = eventVersions.translationVersion(KeptCode.keptVersion(payload),
                kept.xslt().getData());
        final TranslationMapping mapping = TranslationMapping.parse(payload.get("mapping"));
        final EventSchema schema = eventVersions.eventSchema(version);
        final XsltGenerator.Generated marked = XsltGenerator.generate(mapping, schema, version, true);
        if (!marked.ok()) {
            throw new ToolRefusal("The mapping kept with the XSLT no longer generates: " + PyText.list(
                    marked.problems().subList(0, Math.min(3, marked.problems().size()))));
        }
        final SteppedPipeline loaded = load(pipeline.getUuid());
        final Map<String, String> outputs = outputMap(context, loaded, streamIds, kept.element(),
                Map.of(kept.element(), marked.xslt()));
        final List<XmlElement> events = EventFieldDocs.sampledEvents(new ArrayList<>(outputs.values()));
        if (events.isEmpty()) {
            // A section of "(not in the sample)" documents nothing: say what the streams are and what stopped them.
            throw new ToolRefusal(noEvents(context, loaded, streamIds, kept.element(), marked.xslt(),
                    outputs.size()));
        }
        // Which records the counts are of: seen in VS Code, counts from a different sample (the first records of
        // one stream, then records spread over every stream) taken for a change in how events are routed.
        final Map<String, Integer> perStream = new LinkedHashMap<>();
        outputs.keySet().forEach(key -> perStream.merge(key.split(":")[0], 1, Integer::sum));
        final List<String> counted = new ArrayList<>();
        perStream.forEach((stream, count) -> counted.add(count + " of stream " + stream));
        String section = "Counts are of " + outputs.size() + " sample records (" + String.join(", ", counted)
                         + ").\n\n" + EventFieldDocs.fieldMappingMarkdown(mapping, schema, events, layout, described);
        final Optional<String> drift = mappingDrift.drift(kept);
        if (drift.isPresent()) {
            section += "\nNote: " + capitalised(drift.get()) + ".\n";
        }
        return section;
    }

    // The plan's tables, with the values the pipeline writes for the sample Events (stepping sends nothing).
    private String cefSection(final ToolCallContext context,
                              final PipelineDoc pipeline,
                              final KeptMappings.KeptXslt kept,
                              final List<Long> streamIds,
                              final String layout,
                              final Descriptions described) {
        final CefPlan plan = CefPlan.fromJson(kept.kept().payload());
        final DraftCefMappingTool.Stepped stepped = cef.stepped(context, pipeline.getUuid(), streamIds,
                kept.element(), RECORDS);
        if (stepped.lines().isEmpty()) {
            throw new ToolRefusal("Field mapping not written: stepping pipeline '" + pipeline.getName() + "' over "
                                  + "streams " + streamIds + " gave no CEF lines. Its stream_ids are the Events "
                                  + "streams it sends (wait_for_processing on the events pipeline lists them).");
        }
        String section = plan.markdown(CefLines.examplesFrom(plan, stepped.lines(),
                stepped.events().stream().filter(Objects::nonNull).toList()), layout, described);
        final Optional<String> drift = mappingDrift.planDrift(kept);
        if (drift.isPresent()) {
            section += "\nNote: " + capitalised(beforeDiffers(drift.get())) + "\n";
        }
        return section;
    }

    // Raw records, not Events: the documents the pipeline writes from its sample streams.
    private String discoverySection(final ToolCallContext context,
                                    final PipelineDoc pipeline,
                                    final KeptMappings.KeptXslt kept,
                                    final FieldPlan plan,
                                    final List<Long> streamIds) {
        List<Map<String, List<String>>> documents = null;
        List<String> arrays = List.of();
        if (!streamIds.isEmpty()) {
            final List<String> outputs = outputs(context, load(pipeline.getUuid()), streamIds, kept.element(), null);
            documents = IndexFieldDocs.indexDocuments(outputs);
            arrays = IndexFieldDocs.objectArrays(outputs);
        }
        return (IndexFieldDocs.discoveryFieldMarkdown(plan, documents, arrays) + agreedLine(pipeline))
                       .stripTrailing() + "\n\n" + docMark(kept);
    }

    @SuppressWarnings("unchecked")
    private String indexSection(final ToolCallContext context,
                                final String build,
                                final PipelineDoc pipeline,
                                final KeptMappings.KeptXslt kept,
                                final FieldPlan plan,
                                final List<Long> streamIds,
                                final String layout,
                                final Descriptions described) {
        final Map<String, Object> summary = streamIds.isEmpty()
                ? null
                : streamSummaries.summariseEvents(streamIds, RECORDS);
        final Map<String, Double> population = summary == null
                ? null
                : IndexingInputs.populationWithDataNames(summary);
        if (!streamIds.isEmpty() && NullSafe.isEmptyMap(population)) {
            final List<String> kinds = streamIds.stream()
                    .map(id -> metaOrNone(id)
                            .map(meta -> id + " (" + meta.getFeedName() + ", " + meta.getTypeName() + ")")
                            .orElse(id + " (None, None)"))
                    .toList();
            throw new ToolRefusal("Field mapping not written: streams " + PyText.list(kinds) + " hold "
                                  + "no events to document. An indexing pipeline's stream_ids are the Events streams "
                                  + "it indexes (wait_for_processing on the events pipeline lists them).");
        }
        List<Map<String, List<String>>> documents = null;
        if (!streamIds.isEmpty()) {
            // What the index gets: the indexing pipeline stepped over its Events streams, its XSLT's documents read.
            documents = IndexFieldDocs.indexDocuments(outputs(context, load(pipeline.getUuid()), streamIds,
                    kept.element(), null));
        }
        EventSchema schema;
        try {
            schema = eventVersions.eventSchema(eventVersions.chosenOrLatest(context, build));
        } catch (final RuntimeException e) {
            // Descriptions then come from the plan and the sample alone.
            LOGGER.debug(() -> LogUtil.message(
                    "No event-logging schema for the index's descriptions: {}",
                    e.getMessage()));
            schema = null;
        }
        // Example values from the index for the fields the sample has none of (asked for by the user).
        final List<String> missing = population == null
                ? List.of()
                : IndexValues.notInSample(plan, population);
        final Map<String, List<String>> fromIndex = missing.isEmpty()
                ? null
                : examplesFromIndex(context, pipeline, plan, missing);
        String section = IndexFieldDocs.indexFieldMappingMarkdown(plan, population, summary == null
                ? null
                : IndexingInputs.byAction(summary), documents, fromIndex, schema, layout, described)
                         + agreedLine(pipeline);
        final Optional<String> drift = mappingDrift.planDrift(kept);
        if (drift.isPresent()) {
            section = section.stripTrailing() + "\n\nNote: " + capitalised(beforeDiffers(drift.get())) + "\n";
        }
        return section;
    }

    // A nicety: a pipeline Stroom can't merge gives none.
    private Map<String, List<String>> examplesFromIndex(final ToolCallContext context,
                                                        final PipelineDoc pipeline,
                                                        final FieldPlan plan,
                                                        final List<String> fields) {
        try {
            return indexValues.examples(context, pipelineShapes.merged(pipeline.getUuid()), plan, fields);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No example values from the index of {}: {}", pipeline.getName(),
                    e.getMessage()));
            return null;
        }
    }

    private SteppedPipeline load(final String pipelineUuid) {
        stepping.checkCanStep();
        return stepping.load(pipelineUuid);
    }

    private List<String> outputs(final ToolCallContext context,
                                 final SteppedPipeline pipeline,
                                 final List<Long> streamIds,
                                 final String element,
                                 final Map<String, String> code) {
        return new ArrayList<>(outputMap(context, pipeline, streamIds, element, code).values());
    }

    // Each record's output at the element, up to RECORDS spread over the streams, within the call's time limit.
    private Map<String, String> outputMap(final ToolCallContext context,
                                          final SteppedPipeline pipeline,
                                          final List<Long> streamIds,
                                          final String element,
                                          final Map<String, String> code) {
        try {
            return PipelineStepping.withinTimeLimit(context, () -> stepping.outputs(context, pipeline, streamIds,
                    element, code, RECORDS)).outputs();
        } finally {
            stepping.audit(context, streamIds, "Stepped stream(s) through " + pipeline.name() + " with the "
                                               + WriteDocumentationTool.NAME + " tool");
        }
    }

    private Optional<Meta> metaOrNone(final long streamId) {
        try {
            return Optional.of(streamReader.meta(streamId));
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String docMark(final KeptMappings.KeptXslt kept) {
        return KeptMapping.DOC_MARK.replace("{digest}", BuildChecks.mappingDigest(kept));
    }

    private static String beforeDiffers(final String drift) {
        final int at = drift.indexOf(" What differs");
        return at < 0
                ? drift
                : drift.substring(0, at);
    }

    private static String capitalised(final String text) {
        return text.isEmpty()
                ? text
                : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String first(final String text, final int count) {
        return text.substring(0, Math.min(count, text.length()));
    }
}
