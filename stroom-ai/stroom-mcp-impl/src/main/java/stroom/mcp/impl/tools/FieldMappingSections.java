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
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.cef.CefField;
import stroom.ai.impl.tools.cef.CefKeys;
import stroom.ai.impl.tools.cef.CefLines;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.cef.CefValue;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.EventFieldDocs;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.fielddoc.IndexFieldDocs;
import stroom.ai.impl.tools.fielddoc.WrittenEventDocs;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// The Field mapping section of a pipeline's documentation: generated from the mapping (or index or CEF plan) kept with
/// the pipeline's XSLT, stepped over the sample streams, or for an XSLT written by hand from what it writes. The
/// pipeline is stepped as the user, who needs the Stepping permission, within the session's time limit for a long call.
class FieldMappingSections {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FieldMappingSections.class);
    // The UUID in an import's href, `uuid='…', name='…'`.
    private static final Pattern UUID_PART = Pattern.compile("uuid='([^']+)'");

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
    private final ReferenceEvaluation referenceEvaluation;
    private final Provider<DocumentAccess> documentAccessProvider;
    private final SteppedSamples steppedSamples;

    /// @param stepping        Steps the pipeline over the sample streams.
    /// @param cef             Steps a CEF pipeline, reading its lines and the Events they came from.
    /// @param pipelineShapes  Reads a pipeline's own XSLTs.
    /// @param eventVersions   The event-logging schemas.
    /// @param mappingDrift    Says whether the XSLT is still what its mapping generates.
    /// @param streamReader    Reads the streams' meta.
    /// @param streamSummaries Reads how populated each path of the sample Events is.
    /// @param indexValues     Reads example values from the index for fields the documents stepped have none of.
    /// @param referenceEvaluation Reads a reference data pipeline's maps, feeds and the pipelines that look them up.
    /// @param documentAccessProvider Reads an XSLT written by hand, as the user.
    /// @param steppedSamples  Steps the sample records (and the build's survey examples), kept for the session.
    @Inject
    FieldMappingSections(final PipelineStepping stepping,
                         final DraftCefMappingTool cef,
                         final PipelineShapes pipelineShapes,
                         final EventVersions eventVersions,
                         final GeneratedDrift mappingDrift,
                         final StreamReader streamReader,
                         final StreamSummaries streamSummaries,
                         final IndexValues indexValues,
                         final ReferenceEvaluation referenceEvaluation,
                         final Provider<DocumentAccess> documentAccessProvider,
                         final SteppedSamples steppedSamples) {
        this.stepping = stepping;
        this.cef = cef;
        this.pipelineShapes = pipelineShapes;
        this.eventVersions = eventVersions;
        this.mappingDrift = mappingDrift;
        this.streamReader = streamReader;
        this.streamSummaries = streamSummaries;
        this.indexValues = indexValues;
        this.referenceEvaluation = referenceEvaluation;
        this.documentAccessProvider = documentAccessProvider;
        this.steppedSamples = steppedSamples;
    }

    /// The Field mapping section generated from the kept mapping (stepped over the sample streams, with each Event
    /// marked by its rule), CEF plan (with the values the pipeline writes for the sample Events) or index plan (with
    /// the Events' path population).
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
            section = translationSection(context, build, pipeline, kept, streamIds, layout, described);
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

    /// The Field mapping section of an indexing pipeline whose XSLT keeps no plan: from the documents its own XSLT
    /// writes, stepped over the sample streams.
    ///
    /// @param context      The call's context.
    /// @param pipelineUuid The pipeline.
    /// @param streamIds    The Events streams it indexes.
    /// @return The section.
    /// @throws ToolRefusal if it has no XSLT of its own, or the streams give no documents.
    String writtenFieldsSection(final ToolCallContext context,
                                final String pipelineUuid,
                                final List<Long> streamIds) {
        final String element = ownXslt(pipelineUuid).element();
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

    /// The Field mapping section of an events pipeline whose translation XSLT keeps no mapping (written by hand): from
    /// the Events its own XSLT writes, stepped over the sample streams, by event type.
    ///
    /// @param context      The call's context.
    /// @param build        The build, for the event-logging document chosen for it.
    /// @param pipelineUuid The pipeline.
    /// @param streamIds    The sample raw streams.
    /// @param layout       [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described    The field descriptions, given and asked for.
    /// @return The section.
    /// @throws ToolRefusal if it has no XSLT of its own, or the streams give no events.
    String writtenEventsSection(final ToolCallContext context,
                                final String build,
                                final String pipelineUuid,
                                final List<Long> streamIds,
                                final String layout,
                                final Descriptions described) {
        final TranslationDoc own = ownXslt(pipelineUuid);
        final String code = documentAccessProvider.get().read(own.doc(), XsltDoc.class)
                .map(XsltDoc::getData)
                .orElseThrow(() -> new ToolRefusal("The pipeline's XSLT '" + own.doc().getName() + "' can't be "
                                                   + "read"));
        final SteppedPipeline loaded = load(pipelineUuid);
        final Map<String, String> outputs = outputMap(context, loaded, streamIds, own.element(), null);
        final List<XmlElement> events = EventFieldDocs.sampledEvents(new ArrayList<>(outputs.values()));
        if (events.isEmpty()) {
            throw new ToolRefusal(noEvents(context, loaded, streamIds, own.element(), code, outputs.size()));
        }
        return countsLine(outputs) + WrittenEventDocs.writtenEventsMarkdown(codeSchema(context, build, code), events,
                layout, described);
    }

    /// The Field mapping section of a CEF pipeline whose XSLT keeps no plan: what its lines carry, from the Events they
    /// were made from, and what is wrong with them.
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
                .append(plan.markdown(CefLines.examplesFrom(plan, stepped.lines(), given), given,
                        FieldDocs.BY_EVENT_TYPE, null));
        final List<String> problems = (List<String>) reviewed.get("problems");
        if (!NullSafe.isEmptyCollection(problems)) {
            section.append("\n### Problems in its lines\n\n");
            problems.forEach(problem -> section.append("- ").append(problem).append('\n'));
        }
        return section.toString();
    }

    /// The Reference maps section of a reference data pipeline: each map it writes, with its key and value and an
    /// example of each from the sample, the feeds that load it and the pipelines that look it up (the facts
    /// evaluate_pipeline's `reference` section gives, [ReferenceEvaluation]).
    ///
    /// @param context      The call's context.
    /// @param pipelineUuid The pipeline.
    /// @param streamIds    The Raw Reference streams it reads.
    /// @return The section.
    /// @throws ToolRefusal if stepping the streams gives no records.
    String referenceMapsSection(final ToolCallContext context,
                                final String pipelineUuid,
                                final List<Long> streamIds) {
        final SteppedPipeline loaded = load(pipelineUuid);
        final Map<String, String> outputs = outputMap(context, loaded, streamIds, loaded.outputElement(), null);
        if (outputs.isEmpty()) {
            throw new ToolRefusal("Reference maps not written: stepping streams " + streamIds + " gave no records. "
                                  + "A reference data pipeline's stream_ids are the Raw Reference streams it reads.");
        }
        final Map<String, Integer> perStream = new LinkedHashMap<>();
        outputs.keySet().forEach(key -> perStream.merge(key.split(":")[0], 1, Integer::sum));
        final Map<String, Object> facts = referenceEvaluation.reference(loaded.merged(), pipelineUuid,
                new ArrayList<>(outputs.values()));
        return ReferenceEvaluation.markdown(facts, perStream);
    }

    /// The agreed Elasticsearch index template, for the Field mapping section.
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

    /// Why stepping an events pipeline over the streams gave no events, stream by stream: missing, of another type (an
    /// Events stream where the raw input belongs), or stopped by an error before the first record.
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

    // What a source type group's section says first: the pipeline's XSLT is the group's, translating its source
    // types and passing over the rest, and what becomes of every source type is in the feed's registry.
    private static String groupLine(final SourceTypeModule module) {
        final String common = NullSafe.string(module.common());
        final String name = common.replaceFirst(".*name='(.*)'.*", "$1");
        final String feed = name.endsWith(SourceTypeGroups.COMMON_SUFFIX)
                ? name.substring(0, name.length() - SourceTypeGroups.COMMON_SUFFIX.length())
                : null;
        return "This pipeline's XSLT is source type group " + module.name() + "'s (" + String.join(", ",
                module.sourceTypes()) + "): it translates those source types' records and passes over the feed's "
               + "others, counting them. What becomes of every source type is in the feed's registry" + (feed == null
                ? ""
                : ", '" + SourceTypeRegistry.docName(feed) + "'") + ".\n\n";
    }

    // A group's mapping as one translation reads, for its field tables: the common module's parts it calls are
    // named, as the common module writes them, where a rule doesn't write its own.
    private TranslationMapping withCommonParts(final TranslationMapping group) {
        final SourceTypeModule module = group.module();
        if (module == null || module.common() == null) {
            return group.withModule(null);
        }
        final Optional<TranslationMapping> common = commonMapping(module.common());
        if (common.isEmpty()) {
            return group.withModule(null);
        }
        final List<FieldMapping> entries = new ArrayList<>();
        for (final FieldMapping entry : common.get().common()) {
            final String part = SourceTypeModule.partOf(entry.strippedPath());
            if (group.common().stream().noneMatch(own -> Objects.equals(part,
                    SourceTypeModule.partOf(own.strippedPath())))) {
                entries.add(entry);
            }
        }
        entries.addAll(group.common());
        final List<SharedTemplate> shared = new ArrayList<>(common.get().shared());
        shared.addAll(group.shared());
        return group.withCommonAndShared(entries, shared).withModule(null);
    }

    // The mapping kept with the common module an href imports by UUID.
    private Optional<TranslationMapping> commonMapping(final String href) {
        final Matcher uuid = UUID_PART.matcher(href);
        if (!uuid.find()) {
            return Optional.empty();
        }
        try {
            return documentAccessProvider.get().read(new DocRef(XsltDoc.TYPE, uuid.group(1)), XsltDoc.class)
                    .flatMap(doc -> KeptMapping.readMapping(doc.getDescription()))
                    .map(kept -> TranslationMapping.parse(kept.payload().get("mapping")));
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    private String translationSection(final ToolCallContext context,
                                      final String build,
                                      final PipelineDoc pipeline,
                                      final KeptMappings.KeptXslt kept,
                                      final List<Long> streamIds,
                                      final String layout,
                                      final Descriptions described) {
        final Map<String, Object> payload = kept.kept().payload();
        final String version = eventVersions.translationVersion(KeptCode.keptVersion(payload),
                kept.xslt().getData());
        final TranslationMapping mapping = TranslationMapping.parse(payload.get("mapping"));
        final SourceTypeModule module = mapping.module();
        final boolean group = module != null && module.isGroup();
        final EventSchema schema = eventVersions.keptSchema(context, null, payload, version);
        final XsltGenerator.Generated marked = XsltGenerator.generate(mapping, schema, version, true);
        if (!marked.ok()) {
            throw new ToolRefusal("The mapping kept with the XSLT no longer generates: " + ToolText.list(
                    marked.problems().subList(0, Math.min(3, marked.problems().size()))));
        }
        final SteppedPipeline loaded = load(pipeline.getUuid());
        final Map<String, String> markedCode = Map.of(kept.element(), marked.xslt());
        // The streams' first records and the build's survey examples beyond them (asked for by the user: seen in
        // chat 173, a stream's rarer kinds documented "(not in the sample)" from its first 200 records alone).
        final Map<String, String> outputs = steppedOutputs(context, loaded, streamIds, kept.element(),
                markedCode, build).outputs();
        final Optional<String> drift = mappingDrift.drift(kept);
        // An XSLT that isn't what its mapping generates (edited by hand) is documented as it runs: its own events,
        // each given the rule the mapping's marked XSLT gave the same record's event. Seen: a hand edit making
        // users distinct documented from what the mapping generates, duplicates and all.
        final List<XmlElement> events = drift.isPresent()
                ? EventFieldDocs.runningEvents(steppedOutputs(context, loaded, streamIds, kept.element(), null,
                        build).outputs(), outputs)
                : EventFieldDocs.sampledEvents(new ArrayList<>(outputs.values()));
        if (events.isEmpty()) {
            // A section of "(not in the sample)" documents nothing: say what the streams are and what stopped them.
            throw new ToolRefusal(noEvents(context, loaded, streamIds, kept.element(), marked.xslt(),
                    outputs.size()));
        }
        // Rewritten by hand (most events not as the mapping writes them): documented by the events it writes, by
        // event type, not by rules that no longer write them.
        final long unmarked = events.stream().filter(event -> EventFieldDocs.ruleOf(event) == null).count();
        String section = (group
                ? groupLine(module)
                : "") + countsLine(outputs) + (drift.isPresent() && unmarked * 2 > events.size()
                ? WrittenEventDocs.writtenEventsMarkdown(schema, events, layout, described)
                : EventFieldDocs.fieldMappingMarkdown(group
                        ? withCommonParts(mapping)
                        : mapping, schema, events, layout, described));
        if (drift.isPresent()) {
            final List<String> unexpressible = HandEditGate.unexpressible(kept.xslt().getData());
            section += "\nNote: " + (unexpressible.isEmpty()
                    ? capitalised(drift.get()) + "."
                    : "Its XSLT was edited by hand with what no mapping can hold (" + String.join("; ",
                            unexpressible.subList(0, Math.min(3, unexpressible.size()))) + "), so it differs from "
                      + "what its mapping generates for good.")
                       + " The values and counts above are of the XSLT as it runs; where it reads them from is the "
                       + "mapping's.\n";
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
        final List<XmlElement> sampled = stepped.events().stream().filter(Objects::nonNull).toList();
        String section = plan.markdown(CefLines.examplesFrom(plan, stepped.lines(), sampled), sampled, layout,
                described);
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
        PipelineStepping.Outputs stepped = null;
        Map<String, Object> summary = null;
        if (!streamIds.isEmpty()) {
            // What the index gets: the indexing pipeline stepped over its Events streams, its XSLT's documents read.
            stepped = steppedOutputs(context, load(pipeline.getUuid()), streamIds, kept.element(), null);
            // The population is of the Events the XSLT got for those same records, so a field some of them have
            // isn't shown with no values only because the two columns read different records (seen: one in 2.5%
            // of events, from records read spread through the first stream, with none in the records stepped).
            summary = StreamSummaries.summariseEventRecords(new ArrayList<>(stepped.inputs().values()), RECORDS);
            if (NullSafe.isEmptyMap(IndexingInputs.populationWithDataNames(summary))) {
                // No input to read (none stepped, or the steps gave none): the streams themselves.
                summary = streamSummaries.summariseEvents(streamIds, RECORDS);
            }
        }
        final Map<String, Double> population = summary == null
                ? null
                : IndexingInputs.populationWithDataNames(summary);
        if (!streamIds.isEmpty() && NullSafe.isEmptyMap(population)) {
            final List<String> kinds = streamIds.stream()
                    .map(id -> metaOrNone(id)
                            .map(meta -> id + " (" + meta.getFeedName() + ", " + meta.getTypeName() + ")")
                            .orElse(id + " (None, None)"))
                    .toList();
            throw new ToolRefusal("Field mapping not written: streams " + ToolText.list(kinds) + " hold "
                                  + "no events to document. An indexing pipeline's stream_ids are the Events streams "
                                  + "it indexes (wait_for_processing on the events pipeline lists them).");
        }
        final List<Map<String, List<String>>> documents = stepped == null
                ? null
                : IndexFieldDocs.indexDocuments(new ArrayList<>(stepped.outputs().values()));
        EventSchema schema;
        try {
            schema = eventVersions.eventSchema(context, build, eventVersions.chosenOrLatest(context, build));
        } catch (final RuntimeException e) {
            // Descriptions then come from the plan and the sample alone.
            LOGGER.debug(() -> LogUtil.message(
                    "No event-logging schema for the index's descriptions: {}",
                    e.getMessage()));
            schema = null;
        }
        // Example values from the index for the fields the documents stepped have none of, whatever their share of
        // the events (asked for by the user).
        final List<String> missing = documents == null
                ? List.of()
                : IndexValues.withoutValues(plan, documents);
        final Map<String, List<String>> fromIndex = missing.isEmpty()
                ? null
                : examplesFromIndex(context, pipeline, plan, missing);
        String section = IndexFieldDocs.indexFieldMappingMarkdown(plan, population, summary == null
                ? null
                : IndexingInputs.byAction(summary), documents, fromIndex, schema, layout, described)
                         + agreedLine(pipeline);
        if (stepped != null && stepped.stopped()) {
            section = section.stripTrailing() + "\n\n" + stoppedNote(stepped.outputs().size()) + "\n";
        }
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
            LOGGER.info(() -> LogUtil.message("No example values from the index of {}: {}", pipeline.getName(),
                    e.getMessage()), e);
            return null;
        }
    }

    /// The line saying the values come from fewer records than usual, when stepping stopped at the call's time
    /// limit.
    ///
    /// @param records How many records were stepped.
    /// @return The line.
    static String stoppedNote(final int records) {
        return "Note: Stepping stopped at this session's time limit for a call, so the shares and values above come "
               + "from " + records + " records rather than the usual " + RECORDS + ".";
    }

    // Which records the counts are of: seen in VS Code, counts from a different sample (the first records of one
    // stream, then records spread over every stream) taken for a change in how events are routed.
    private static String countsLine(final Map<String, String> outputs) {
        final Map<String, Integer> perStream = new LinkedHashMap<>();
        outputs.keySet().forEach(key -> perStream.merge(key.split(":")[0], 1, Integer::sum));
        final List<String> counted = new ArrayList<>();
        perStream.forEach((stream, count) -> counted.add(count + " of stream " + stream));
        return "Counts are of " + outputs.size() + " sample records (" + String.join(", ", counted) + ").\n\n";
    }

    // The pipeline's own XSLT (not its template's).
    private TranslationDoc ownXslt(final String pipelineUuid) {
        return pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid).stream()
                .filter(entry -> !entry.inheritedFromTemplate() && XsltDoc.TYPE.equals(entry.doc().getType()))
                .findFirst()
                .orElseThrow(() -> new ToolRefusal("The pipeline has no XSLT of its own to document: give it one, or "
                                                   + "document the template"));
    }

    // The Events schema an XSLT with no mapping writes: the fork its code writes, else the event-logging version it
    // names, from the document chosen for the build where this Stroom holds it in more than one.
    private EventSchema codeSchema(final ToolCallContext context, final String build, final String code) {
        final String version = eventVersions.translationVersion(null, code);
        final Map<String, Object> payload = new LinkedHashMap<>();
        eventVersions.forkWrittenBy(code).map(XmlSchemaDoc::getUuid)
                .ifPresent(uuid -> payload.put(EventVersions.KEPT_SCHEMA_UUID, uuid));
        return eventVersions.keptSchema(context, build, payload, version);
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
        return steppedOutputs(context, pipeline, streamIds, element, code).outputs();
    }

    private PipelineStepping.Outputs steppedOutputs(final ToolCallContext context,
                                                    final SteppedPipeline pipeline,
                                                    final List<Long> streamIds,
                                                    final String element,
                                                    final Map<String, String> code) {
        return steppedOutputs(context, pipeline, streamIds, element, code, null);
    }

    // Each record's output and input at the element, up to RECORDS spread over the streams and the build's survey
    // examples beyond them, within the call's time limit, and whether that ran out first: as stepped earlier in the
    // session where nothing they depend on has changed.
    private PipelineStepping.Outputs steppedOutputs(final ToolCallContext context,
                                                    final SteppedPipeline pipeline,
                                                    final List<Long> streamIds,
                                                    final String element,
                                                    final Map<String, String> code,
                                                    final String build) {
        try {
            return PipelineStepping.withinTimeLimit(context, () -> steppedSamples.stepped(context, pipeline,
                    streamIds, element, code, RECORDS, build));
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
