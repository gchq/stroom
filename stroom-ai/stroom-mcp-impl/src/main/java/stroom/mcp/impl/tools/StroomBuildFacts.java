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
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What the plan reads about a build, from Stroom, as the user (the Stroom MCP server's `tools/plan.py` and
/// `tools/builds.py` helpers: `_build_docs`, `sample_streams`, `source_feeds`, `_survey_state`, `_translating`,
/// `_suggested_columns`, and `tools/pipeline_writes.py` `open_slots`).
class StroomBuildFacts implements BuildFacts {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomBuildFacts.class);

    /// What a survey doc's name ends with: `<feed> - Survey`.
    static final String SURVEY = " - Survey";
    /// The most characters of an example a survey doc keeps: one this long was cut short.
    static final int MAX_EXAMPLE_CHARS = 4000;
    /// How much of a stream's head is read to profile it.
    static final int HEAD_CHARS = 20_000;
    private static final Pattern SURVEY_STATE = Pattern.compile("```json survey-state\\n(.*?)\\n```",
            Pattern.DOTALL);
    private static final List<String> RAW_TYPES = List.of(StreamTypeNames.RAW_EVENTS,
            StreamTypeNames.RAW_REFERENCE);
    // Event-logging paths whose index fields make the verification dashboard's columns, in this order (seen in VS
    // Code: the user had to ask for the user column).
    private static final List<String> COLUMN_PATHS = List.of("EventTime/TimeCreated", "EventSource/User/Id",
            "EventSource/Device/HostName", "IPAddress", "TypeId", "Action", "Outcome/Success");

    private final WriteGuard guard;
    private final BuildRecords buildRecords;
    private final DocumentAccess documentAccess;
    private final StreamReader streamReader;
    private final PipelineShapes pipelineShapes;
    private final IndexTemplateAgreements agreements;
    private final BuildChecks buildChecks;
    private final KeptMappings keptMappings;
    private final ProcessorFilterAccess processorFilterAccess;
    private final ProcessorFilters processorFilters;
    private final PipelineFeeds pipelineFeeds;
    private final ProcessingStatusTool processingStatus;
    private final SampleProfiling sampleProfiler;

    @Inject
    StroomBuildFacts(final WriteGuard guard,
                     final BuildRecords buildRecords,
                     final DocumentAccess documentAccess,
                     final StreamReader streamReader,
                     final PipelineShapes pipelineShapes,
                     final IndexTemplateAgreements agreements,
                     final BuildChecks buildChecks,
                     final KeptMappings keptMappings,
                     final ProcessorFilterAccess processorFilterAccess,
                     final ProcessorFilters processorFilters,
                     final PipelineFeeds pipelineFeeds,
                     final ProcessingStatusTool processingStatus,
                     final SampleProfiling sampleProfiler) {
        this.guard = guard;
        this.buildRecords = buildRecords;
        this.documentAccess = documentAccess;
        this.streamReader = streamReader;
        this.pipelineShapes = pipelineShapes;
        this.agreements = agreements;
        this.buildChecks = buildChecks;
        this.keptMappings = keptMappings;
        this.processorFilterAccess = processorFilterAccess;
        this.processorFilters = processorFilters;
        this.pipelineFeeds = pipelineFeeds;
        this.processingStatus = processingStatus;
        this.sampleProfiler = sampleProfiler;
    }

    @Override
    public List<Map<String, Object>> buildDocs(final String build) {
        final List<WriteGuard.BuildDoc> contents = guard.folderContents(build);
        final Map<String, Object> kept = contents.isEmpty()
                ? Map.of()
                : BuildRecords.docs(buildRecords.load(build));
        final List<Map<String, Object>> docs = new ArrayList<>();
        for (final WriteGuard.BuildDoc doc : contents) {
            final Map<?, ?> entry = kept.get(doc.uuid()) instanceof final Map<?, ?> found
                    ? found
                    : Map.of();
            final Map<String, Object> listed = new LinkedHashMap<>();
            listed.put("type", doc.type());
            listed.put("uuid", doc.uuid());
            listed.put("name", doc.name());
            listed.put("path", doc.path());
            listed.put("working_copy_of", entry.get("copy_of"));
            listed.put("version_of", entry.get("version_of"));
            listed.put("role", doc.role());
            docs.add(listed);
        }
        // A stable order: approvals are bound to the exact plan built from this list.
        docs.sort(Comparator.comparing((Map<String, Object> doc) -> String.valueOf(doc.get("type")))
                .thenComparing(doc -> String.valueOf(doc.get("name")))
                .thenComparing(doc -> String.valueOf(doc.get("uuid"))));
        return docs;
    }

    @Override
    public Map<String, List<Map<String, Object>>> sampleStreams(final String build,
                                                               final List<Map<String, Object>> docs) {
        final Map<String, List<Map<String, Object>>> found = new LinkedHashMap<>();
        for (final Map<String, Object> feed : docs) {
            if (!FeedDoc.TYPE.equals(feed.get("type"))) {
                continue;
            }
            final Long created = feedCreated(feed);
            for (final String type : RAW_TYPES) {
                for (final Meta meta : ownStreams(String.valueOf(feed.get("name")), type, 20, created)) {
                    found.computeIfAbsent(type, ignored -> new ArrayList<>()).add(stream(meta));
                }
            }
        }
        return found;
    }

    @Override
    public String streamHead(final long streamId) {
        streamReader.meta(streamId);
        // Cut at its last line end when cut short: profiled with a record cut part-way through, 50 XML fragments
        // (35 KB) read as key=value, their messages' "User: x" taken for pairs, and create_pipeline refused the
        // XMLFragmentParser (Qwen, VS Code).
        return streamReader.cachedRawText(streamId, HEAD_CHARS).text();
    }

    @Override
    public Map<String, Object> profile(final String text) {
        return sampleProfiler.profile(text);
    }

    @Override
    public Optional<KeptMapping.Kept> xsltMapping(final String xsltUuid) {
        // Gone since the build was listed: nothing kept with it.
        return documentAccess.read(new DocRef(XsltDoc.TYPE, xsltUuid), XsltDoc.class)
                .flatMap(xslt -> KeptMapping.readMapping(xslt.getDescription()));
    }

    @Override
    public PipelineShapes.Shape pipelineShape(final String pipelineUuid) {
        return pipelineShapes.shape(pipelineUuid);
    }

    @Override
    public List<String> openSlots(final String pipelineUuid) {
        return openSlots(pipelineShapes.merged(pipelineUuid));
    }

    /// The properties a child must supply for the template's chain to run (the Stroom MCP server's `open_slots`):
    /// the parser's text converter (DSParser, XMLFragmentParser, CombinedParser) and the first XSLT step's xslt,
    /// where the template leaves them unset.
    ///
    /// @param merged A pipeline as Stroom runs it.
    /// @return Each as `element.property`.
    static List<String> openSlots(final MergedPipeline merged) {
        return PipelineWrites.openSlots(merged.elements(), merged.chain(), merged.properties(), null).stream()
                .map(PipelineWrites.Slot::dotted)
                .toList();
    }

    @Override
    public boolean steppedClean(final Map<String, Object> pipeline) {
        return buildRecords.steppedClean(ref(pipeline));
    }

    @Override
    public boolean verified(final Map<String, Object> pipeline) {
        return buildRecords.verified(ref(pipeline));
    }

    @Override
    public boolean validated(final Map<String, Object> pipeline) {
        return buildRecords.validated(ref(pipeline));
    }

    @Override
    public Optional<String> docName(final String type, final String uuid) {
        return documentAccess.findDocRef(uuid)
                .filter(docRef -> type.equals(docRef.getType()))
                .map(DocRef::getName);
    }

    @Override
    public List<Long> eventsStreams(final Map<String, Object> feed) {
        return ownStreams(String.valueOf(feed.get("name")), StreamTypeNames.EVENTS, 50, feedCreated(feed)).stream()
                .map(Meta::getId)
                .toList();
    }

    @Override
    public Optional<Map<String, Object>> elasticDestination(final String pipelineUuid) {
        return agreements.elasticDestination(pipelineUuid);
    }

    @Override
    public Optional<String> agreementProblem(final String pipelineUuid, final Map<String, Object> destination) {
        return agreements.pipeline(pipelineUuid)
                .flatMap(pipeline -> agreements.agreementProblem(pipeline, destination));
    }

    @Override
    public List<String> buildChecks(final List<Map<String, Object>> docs) {
        return buildChecks.checks(docs);
    }

    @Override
    public List<String> sourceFeeds(final String build, final List<Map<String, Object>> docs) {
        final List<String> found = new ArrayList<>();
        try {
            found.addAll(BuildRecords.stringList(buildRecords.load(build).get(BuildRecords.SOURCE_FEEDS)));
        } catch (final RuntimeException e) {
            // Advice and promotion's filters: an unreadable record leaves the rest to go on.
            LOGGER.debug(() -> "Unable to read the record of build " + build, e);
        }
        for (final Map<String, Object> doc : docs) {
            final String name = String.valueOf(doc.get("name"));
            if (DocumentationDoc.TYPE.equals(doc.get("type")) && name.endsWith(SURVEY)) {
                found.add(name.substring(0, name.length() - SURVEY.length()));
            }
        }
        for (final Map<String, Object> doc : docs) {
            final Object original = Objects.requireNonNullElse(doc.get("working_copy_of"), doc.get("version_of"));
            if (!PipelineDoc.TYPE.equals(doc.get("type")) || original == null) {
                continue;
            }
            final Map<Object, Object> known = new LinkedHashMap<>();
            for (final ProcessorFilter filter : processorFilterAccess.filtersOf(
                    new DocRef(PipelineDoc.TYPE, String.valueOf(original))).filters()) {
                found.addAll(pipelineFeeds.filterFeeds(ProcessorFilters.terms(filter), known, false));
            }
        }
        final Set<String> own = new LinkedHashSet<>();
        docs.stream()
                .filter(doc -> FeedDoc.TYPE.equals(doc.get("type")))
                .forEach(doc -> own.add(String.valueOf(doc.get("name")).toLowerCase(Locale.ROOT)));
        return new LinkedHashSet<>(found).stream()
                .filter(feed -> !own.contains(feed.toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Override
    public Optional<Map<String, Object>> surveyState(final List<Map<String, Object>> surveys) {
        final List<Map<String, Object>> states = new ArrayList<>();
        for (final Map<String, Object> survey : surveys) {
            // A survey doc unreadable: the plan goes on without it.
            documentAccess.read(new DocRef(DocumentationDoc.TYPE, String.valueOf(survey.get("uuid"))),
                            DocumentationDoc.class)
                    .flatMap(doc -> readState(BuildRecords.bodyText(doc)))
                    .ifPresent(states::add);
        }
        return combine(states);
    }

    /// @param states The states the survey docs keep.
    /// @return What they say together: covered (saturated) when every one is, every example's location (for
    /// step_records, at most 60), its streams, and examples of the records (to profile the feed's format); empty
    /// when there are none.
    static Optional<Map<String, Object>> combine(final List<Map<String, Object>> states) {
        if (states.isEmpty()) {
            return Optional.empty();
        }
        final List<Map<?, ?>> examples = new ArrayList<>();
        for (final Map<String, Object> state : states) {
            if (state.get("shapes") instanceof final Map<?, ?> shapes) {
                for (final Object shape : shapes.values()) {
                    if (shape instanceof final Map<?, ?> map && map.get("examples") instanceof final List<?> list) {
                        list.stream()
                                .filter(Map.class::isInstance)
                                .map(Map.class::cast)
                                .forEach(examples::add);
                    }
                }
            }
        }
        final List<Object> locations = examples.stream()
                .map(example -> example.get("location"))
                .filter(location -> location != null && !(location instanceof final Map<?, ?> map && map.isEmpty()))
                .map(location -> (Object) location)
                .toList();
        final Set<Long> streams = new TreeSet<>();
        for (final Object location : locations) {
            if (location instanceof final Map<?, ?> map && map.get("stream") != null
                && String.valueOf(map.get("stream")).matches("\\d+")) {
                streams.add(Long.parseLong(String.valueOf(map.get("stream"))));
            }
        }
        // Only whole examples make the sample: one the survey doc cut short isn't the format any more (seen in
        // testing: JSON lines of 4,720 characters, kept cut at 4,000, read as text, and the plan asked for a Data
        // Splitter).
        final List<String> whole = examples.stream()
                .map(example -> NullSafe.string(NullSafe.get(example.get("text"), String::valueOf)))
                .filter(text -> text.length() < MAX_EXAMPLE_CHARS)
                .limit(20)
                .toList();
        final List<Long> streamList = new ArrayList<>(streams);
        final Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("saturated", states.stream().allMatch(state -> Boolean.TRUE.equals(state.get("saturated"))));
        survey.put("format", states.stream()
                .map(state -> state.get("format"))
                .filter(format -> format != null && !"".equals(format))
                .findFirst()
                .orElse(null));
        survey.put("locations", !locations.isEmpty() && locations.size() <= 60
                ? locations
                : null);
        survey.put("streams", streamList.subList(Math.max(0, streamList.size() - 10), streamList.size()));
        final String sample = String.join("\n", whole);
        survey.put("sample", sample.isEmpty()
                ? null
                : sample);
        return Optional.of(survey);
    }

    /// @param markdown A survey doc's text.
    /// @return The state its fenced JSON keeps, or empty.
    static Optional<Map<String, Object>> readState(final String markdown) {
        final Matcher matcher = SURVEY_STATE.matcher(NullSafe.string(markdown));
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            final Object state = JsonUtil.readValue(matcher.group(1), Object.class);
            return state instanceof final Map<?, ?> map
                    ? Optional.of(BuildRecords.asMap(map))
                    : Optional.empty();
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Map<String, Object>> processingFilters(final String pipelineUuid) {
        final ProcessorFilterAccess.Filters filters = processorFilterAccess.filtersOf(
                new DocRef(PipelineDoc.TYPE, pipelineUuid));
        if (filters.refused() != null) {
            return List.of();
        }
        final Object listed = processingStatus.status(pipelineUuid, filters.filters()).get("filters");
        if (!(listed instanceof final List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(map -> BuildRecords.asMap((Map<?, ?>) map))
                .toList();
    }

    @Override
    public List<String> suggestedColumns(final String pipelineUuid) {
        final List<?> fields;
        try {
            fields = keptMappings.keptMapping(pipelineUuid)
                    .filter(kept -> "index".equals(kept.kind()))
                    .map(kept -> kept.payload().get("fields") instanceof final List<?> list
                            ? list
                            : List.of())
                    .orElse(List.of());
        } catch (final RuntimeException e) {
            // A suggestion: never a reason for the plan to fail.
            return List.of();
        }
        final List<String> chosen = new ArrayList<>();
        for (final String path : COLUMN_PATHS) {
            for (final Object field : fields) {
                if (field instanceof final Map<?, ?> map && map.get("name") != null
                    && NullSafe.string(NullSafe.get(map.get("source"), String::valueOf)).endsWith(path)
                    && !chosen.contains(String.valueOf(map.get("name")))) {
                    chosen.add(String.valueOf(map.get("name")));
                }
            }
        }
        return chosen.subList(0, Math.min(8, chosen.size()));
    }

    @Override
    public Optional<String> keptXsltUuid(final String pipelineUuid) {
        return keptMappings.keptXslt(pipelineUuid).map(kept -> kept.xslt().getUuid());
    }

    @Override
    public List<Map<String, Object>> translating(final List<String> feeds) {
        final List<Map<String, Object>> found = new ArrayList<>();
        for (final String feed : feeds.subList(0, Math.min(3, feeds.size()))) {
            try {
                for (final Map<String, Object> pipeline : processorFilters.pipelinesReading(feed)) {
                    if (!Boolean.TRUE.equals(pipeline.get("reads_events"))
                        && found.stream().noneMatch(other -> other.get("uuid").equals(pipeline.get("uuid")))) {
                        final Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("name", pipeline.get("name"));
                        entry.put("uuid", pipeline.get("uuid"));
                        found.add(entry);
                    }
                }
            } catch (final RuntimeException e) {
                // Advice: a Stroom that can't list its filters leaves the rest of the plan to go on.
                LOGGER.debug(() -> "Unable to find the pipelines reading " + feed, e);
            }
        }
        return found;
    }

    // The feed's own streams of a type, not deleted: Stroom keeps a deleted feed's streams under its name (the UI
    // no longer shows them), so a feed made again with the same name finds them too. Its own are those created
    // since it was.
    private List<Meta> ownStreams(final String feed, final String type, final int limit, final Long createdMs) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, type)
                .build();
        return NullSafe.list(NullSafe.get(streamReader.find(expression, limit, true), ResultPage::getValues))
                .stream()
                .map(MetaRow::getMeta)
                .filter(meta -> meta.getStatus() != Status.DELETED)
                .filter(meta -> createdMs == null || meta.getCreateMs() >= createdMs)
                .toList();
    }

    private Long feedCreated(final Map<String, Object> feed) {
        return documentAccess.read(new DocRef(FeedDoc.TYPE, String.valueOf(feed.get("uuid"))), FeedDoc.class)
                .map(FeedDoc::getCreateTimeMs)
                .orElse(null);
    }

    private static Map<String, Object> stream(final Meta meta) {
        final Map<String, Object> stream = new LinkedHashMap<>();
        stream.put("id", meta.getId());
        stream.put("feed", meta.getFeedName());
        stream.put("createMs", meta.getCreateMs());
        stream.put("status", NullSafe.get(meta.getStatus(), Status::name));
        return stream;
    }

    private static DocRef ref(final Map<String, Object> doc) {
        return new DocRef(String.valueOf(doc.getOrDefault("type", PipelineDoc.TYPE)), String.valueOf(doc.get("uuid")),
                NullSafe.get(doc.get("name"), String::valueOf));
    }
}
