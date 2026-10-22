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

import stroom.ai.impl.tools.FeedAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.StreamReader.StreamReadException;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexResource;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.util.shared.ResultPage;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/// The processing tools' world (the Stroom MCP server's `tests/test_processing.py` `mock_stroom`): the in-memory
/// explorer and feeds of [FeedWorld], pipelines as Stroom runs them, streams the user may view, each pipeline's
/// processor filters, and Stroom's processor filter and index services as mocks that keep what was asked of them.
final class ProcessingWorld {

    static final String BUILD = "acme-v1";
    static final long FEED_MADE = 1791580000000L;   // 2026-10-09T21:06:40Z
    static final DocRef P1 = new DocRef(PipelineDoc.TYPE, "p1", "Acme");
    static final DocRef EV = new DocRef(PipelineDoc.TYPE, "ev", "Acme-Events");
    static final DocRef CLUSTER = new DocRef("ElasticCluster", "c", "ES_DEV");

    final FeedWorld feeds = new FeedWorld();
    final BuildWorld world = feeds.world;
    final ProcessorFilterAccess processorFilterAccess = Mockito.mock(ProcessorFilterAccess.class);
    final ProcessorFilterService filterService = Mockito.mock(ProcessorFilterService.class);
    final ProcessorService processorService = Mockito.mock(ProcessorService.class);
    final IndexResource indexResource = Mockito.mock(IndexResource.class);
    final Map<String, List<ProcessorFilter>> filters = new LinkedHashMap<>();
    final Map<Long, Meta> streams = new LinkedHashMap<>();
    final List<CreateProcessFilterRequest> created = new ArrayList<>();
    final Map<Long, String> errorTexts = new LinkedHashMap<>();
    final List<String> deletedShards = new ArrayList<>();
    // The processor Stroom gives each filter made from now on.
    Processor processor = Processor.builder().id(70).enabled(true).build();
    int nextFilterId = 9;

    final PipelineDocs pipelineDocs = new PipelineDocs(world.documentAccess);
    final PipelineOutputs pipelineOutputs = new PipelineOutputs(world.pipelineShapes);
    final StreamProcessing streamProcessing;
    final ProcessorFilterWrites filterWrites;
    final OutputFeeds outputFeeds;
    final ProcessingSources sources;
    final IndexTemplateAgreements agreements = new IndexTemplateAgreements(world.pipelineShapes,
            world.documentAccess);
    final ProcessingApprovals approvals;
    final IndexingRuns indexingRuns;
    final SampleStreamTexts sampleStreamTexts;
    final PromotionProcessing promotion;

    ProcessingWorld() {
        final FeedAccess feedAccess = Mockito.mock(FeedAccess.class);
        Mockito.when(feedAccess.canView(anyString())).thenReturn(true);
        streamProcessing = new StreamProcessing(feeds.streamReader, feedAccess,
                () -> Mockito.mock(ProcessorTaskService.class));
        filterWrites = new ProcessorFilterWrites(processorFilterAccess, () -> filterService, () -> processorService,
                streamProcessing, feeds.streamReader, feeds.feedNames, world.documentAccess, pipelineDocs,
                pipelineOutputs, world.guard());
        outputFeeds = new OutputFeeds(world.pipelineShapes, pipelineDocs, world.guard(), world.records(),
                feeds.buildFeeds, feeds.feedNames);
        sources = new ProcessingSources(pipelineOutputs, streamProcessing, feeds.streamReader, pipelineDocs,
                world.guard(), feeds.consent, feeds.plan, feeds.feedNames, outputFeeds);
        approvals = new ProcessingApprovals(world.pipelineShapes, pipelineOutputs, agreements, pipelineDocs,
                feeds.consent);
        indexingRuns = new IndexingRuns(world.pipelineShapes, pipelineDocs, world.guard(), streamProcessing,
                () -> indexResource, streamId -> errorTexts.getOrDefault(streamId, ""));
        sampleStreamTexts = new SampleStreamTexts(feeds.streamReader, feeds.feedNames, world.documentAccess);
        promotion = new PromotionProcessing(filterWrites, streamProcessing, pipelineOutputs, agreements,
                indexingRuns, pipelineDocs, () -> FEED_MADE + 100_000_000L);
        wireStreams();
        wireFilters();
        Mockito.when(indexResource.deleteIndexShards(anyString(), any())).thenAnswer(inv -> {
            final FindIndexShardCriteria criteria = inv.getArgument(1);
            deletedShards.add(inv.getArgument(0) + ":" + criteria.getIndexShardIdSet().getSet());
            return 1L;
        });
    }

    /// @return create_processor_filter, on this world.
    CreateProcessorFilterTool createTool() {
        return new CreateProcessorFilterTool(filterWrites, sources, approvals, indexingRuns, agreements,
                pipelineOutputs, world.pipelineShapes, sampleStreamTexts, world.records(), feeds.consent,
                feeds.plan);
    }

    /// @return reprocess_streams, on this world.
    ReprocessStreamsTool reprocessTool() {
        return new ReprocessStreamsTool(filterWrites, sources, approvals, indexingRuns, agreements,
                sampleStreamTexts, feeds.consent, feeds.plan);
    }

    /// @return set_processor_filter_enabled, on this world.
    SetProcessorFilterEnabledTool setEnabledTool() {
        return new SetProcessorFilterEnabledTool(processorFilterAccess, () -> filterService, filterWrites);
    }

    /// Adds a pipeline document, in the build (the tools' own) or elsewhere.
    PipelineDoc pipeline(final DocRef ref, final boolean inBuild, final String description, final String... tags) {
        if (inBuild) {
            world.inBuild(BUILD, PipelineDoc.TYPE, ref.getName(), ref.getUuid());
        } else {
            world.add(PipelineDoc.TYPE, ref.getName(), world.folder("System/Pipelines").uuid, ref.getUuid(), tags);
        }
        final PipelineDoc doc = PipelineDoc.builder().uuid(ref.getUuid()).name(ref.getName()).version("v0")
                .description(description).build();
        world.docs.put(ref.getUuid(), doc);
        return doc;
    }

    /// @return The pipeline's document as it is now.
    PipelineDoc pipelineDoc(final String uuid) {
        return (PipelineDoc) world.docs.get(uuid);
    }

    /// Gives a pipeline its elements (ids and types in pairs), and properties as Stroom runs it.
    PipelineLayers layer(final String uuid, final String... elements) {
        return PipelineLayers.layer(uuid).chain(elements);
    }

    /// Sets what Stroom loads for the pipeline.
    void layers(final String uuid, final PipelineLayers layer) {
        PipelineLayers.given(world.pipelineService, uuid, PipelineLayers.of(layer));
    }

    /// p1 as a translation pipeline (its StreamAppender writes Events).
    void translation(final String uuid) {
        layers(uuid, layer(uuid, "xsltFilter", "XSLTFilter", "streamAppender", "StreamAppender")
                .property("streamAppender", "streamType", "Events"));
    }

    /// p1 as an Elasticsearch indexing pipeline writing to ecs-acme-v2 on ES_DEV.
    void elastic(final String uuid) {
        layers(uuid, layer(uuid, "xsltFilter", "XSLTFilter", "elasticIndexingFilter", "ElasticIndexingFilter")
                .property("elasticIndexingFilter", "indexName", "ecs-acme-v2")
                .property("elasticIndexingFilter", "cluster", CLUSTER));
    }

    /// p1 as a Lucene indexing pipeline writing to the index.
    void lucene(final String uuid, final DocRef index) {
        layers(uuid, layer(uuid, "xsltFilter", "XSLTFilter", "indexingFilter", "IndexingFilter")
                .property("indexingFilter", "index", index));
    }

    /// The agreed index template for ecs-acme-v2, for the pipeline's code (no XSLT).
    static Map<String, Object> agreed(final String devTools) {
        final Map<String, Object> agreed = new LinkedHashMap<>();
        agreed.put("name", "ecs-acme-v2");
        agreed.put("index", "ecs-acme-v2");
        agreed.put("cluster", "ES_DEV");
        agreed.put("component_templates", List.of("ecs-base"));
        agreed.put("xslt", XsltCode.digest());
        agreed.put("agreed", "2026-10-03T09:00:00Z");
        agreed.put("dev_tools", devTools);
        return agreed;
    }

    /// @return The description of an indexing pipeline carrying the agreed template.
    static String withAgreed(final Map<String, Object> agreed) {
        return KeptMapping.withAgreedTemplate("Indexes Acme.", agreed);
    }

    /// Records a clean step of the pipeline's current code.
    void steppedClean(final DocRef pipeline) {
        world.records().recordPassed(pipeline, BuildRecords.STEPPED, null, null);
    }

    /// Adds a stream.
    Meta stream(final long id, final String feed, final String type, final String pipelineUuid, final Long parent) {
        final Meta meta = Meta.builder().id(id).feedName(feed).typeName(type).pipelineUuid(pipelineUuid)
                .parentDataId(parent).status(Status.UNLOCKED).createMs(FEED_MADE + 60_000).build();
        streams.put(id, meta);
        return meta;
    }

    /// Adds a stream as given.
    void stream(final Meta meta) {
        streams.put(meta.getId(), meta);
    }

    /// An output the pipeline wrote from the stream.
    void output(final long raw, final long id, final String type, final String pipelineUuid, final int filterId) {
        streams.put(id, Meta.builder().id(id).feedName("ACME").typeName(type).pipelineUuid(pipelineUuid)
                .parentDataId(raw).processorFilterId(filterId).status(Status.UNLOCKED).createMs(FEED_MADE + 90_000)
                .build());
    }

    /// Gives the pipeline a filter selecting the streams by id.
    ProcessorFilter idFilter(final String pipelineUuid, final int id, final long... streamIds) {
        final ExpressionOperator.Builder ids = ExpressionOperator.builder().op(ExpressionOperator.Op.OR);
        for (final long streamId : streamIds) {
            ids.addTerm(ProcessorFilterWrites.term("Id", streamId));
        }
        return filter(pipelineUuid, id, true, ids.build(), null);
    }

    /// Gives the pipeline a filter.
    ProcessorFilter filter(final String pipelineUuid,
                           final int id,
                           final boolean enabled,
                           final ExpressionOperator expression,
                           final Long minMs) {
        // The processor first: setting it sets the filter's pipeline to the processor's.
        final ProcessorFilter filter = ProcessorFilter.builder().id(id)
                .processor(Processor.builder().id(70).enabled(true).build())
                .pipelineUuid(pipelineUuid)
                .enabled(enabled)
                .queryData(QueryData.builder().expression(expression).build())
                .minMetaCreateTimeMs(minMs)
                .build();
        filters.computeIfAbsent(pipelineUuid, uuid -> new ArrayList<>()).add(filter);
        return filter;
    }

    /// @return The feed's DocRef, made in the folder (outside the build) with the stream type.
    DocRef feedAt(final String folder, final String name, final String uuid, final String streamType) {
        world.createTimeMs = FEED_MADE;
        final DocRef ref = feeds.feedAt(folder, name, uuid);
        world.docs.put(ref.getUuid(), feeds.feed(ref.getUuid()).copy().streamType(streamType).build());
        return ref;
    }

    /// @return The feed's DocRef, made in the build.
    DocRef buildFeed(final String name) {
        world.createTimeMs = FEED_MADE;
        final DocRef ref = feeds.buildFeed(BUILD, name);
        world.docs.put(ref.getUuid(), feeds.feed(ref.getUuid()).copy().streamType("Raw Events").build());
        return ref;
    }

    /// @return The last filter request's expression.
    ExpressionOperator lastExpression() {
        return created.getLast().getQueryData().getExpression();
    }

    /// @return The tool's result, read.
    static Map<String, Object> json(final ToolResult result) {
        return PortedToolCalls.json(result.text());
    }

    /// @return A call's context, from an MCP client that shows no forms.
    static ToolCallContext context() {
        return PortedToolCalls.context();
    }

    private void wireStreams() {
        Mockito.when(feeds.streamReader.metaRow(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(streams.get((Long) inv.getArgument(0))).map(ProcessingWorld::row));
        Mockito.when(feeds.streamReader.meta(anyLong())).thenAnswer(inv -> {
            final Meta meta = streams.get((Long) inv.getArgument(0));
            if (meta == null) {
                throw new StreamReadException("No stream with id " + inv.getArgument(0));
            }
            return meta;
        });
        Mockito.when(feeds.streamReader.children(anyLong(), anyInt())).thenAnswer(inv -> {
            final long parent = inv.getArgument(0);
            return streams.values().stream()
                    .filter(meta -> Objects.equals(meta.getParentMetaId(), parent))
                    .map(ProcessingWorld::row)
                    .toList();
        });
        Mockito.when(feeds.streamReader.find(any(), anyInt(), anyBoolean())).thenAnswer(inv -> {
            final ExpressionOperator expression = inv.getArgument(0);
            return new ResultPage<>(streams.values().stream()
                    .filter(selects(expression))
                    .map(ProcessingWorld::row)
                    .toList());
        });
    }

    private void wireFilters() {
        Mockito.when(processorFilterAccess.canManageProcessors()).thenReturn(true);
        Mockito.when(processorFilterAccess.filtersOf(any())).thenAnswer(inv -> {
            final DocRef pipeline = inv.getArgument(0);
            return new ProcessorFilterAccess.Filters(List.copyOf(filters.getOrDefault(pipeline.getUuid(),
                    List.of())), null);
        });
        Mockito.when(filterService.create(any(CreateProcessFilterRequest.class))).thenAnswer(inv -> {
            final CreateProcessFilterRequest request = inv.getArgument(0);
            created.add(request);
            final ProcessorFilter filter = ProcessorFilter.builder()
                    .id(nextFilterId++)
                    .processor(processor)
                    .pipelineUuid(request.getPipeline().getUuid())
                    .enabled(request.isEnabled())
                    .queryData(request.getQueryData())
                    .minMetaCreateTimeMs(request.getMinMetaCreateTimeMs())
                    .maxProcessingTasks(request.getMaxProcessingTasks())
                    .build();
            filters.computeIfAbsent(request.getPipeline().getUuid(), uuid -> new ArrayList<>()).add(filter);
            return filter;
        });
        Mockito.when(filterService.fetch(anyInt())).thenAnswer(inv -> filters.values().stream()
                .flatMap(List::stream)
                .filter(filter -> Objects.equals(filter.getId(), inv.getArgument(0)))
                .findFirst());
    }

    // The streams an expression of Id, Parent Id, Feed and Type terms (AND, or OR) selects.
    private static Predicate<Meta> selects(final ExpressionOperator expression) {
        final List<Predicate<Meta>> terms = new ArrayList<>();
        for (final ExpressionItem item : expression.getChildren()) {
            if (item instanceof final ExpressionTerm term) {
                terms.add(meta -> switch (term.getField()) {
                    case "Id" -> String.valueOf(meta.getId()).equals(term.getValue());
                    case "Parent Id" -> String.valueOf(meta.getParentMetaId()).equals(term.getValue());
                    case "Feed" -> term.getValue().equalsIgnoreCase(meta.getFeedName());
                    case "Type" -> term.getValue().equals(meta.getTypeName());
                    default -> true;
                });
            }
        }
        return expression.getOp() == ExpressionOperator.Op.OR
                ? meta -> terms.stream().anyMatch(term -> term.test(meta))
                : meta -> terms.stream().allMatch(term -> term.test(meta));
    }

    private static MetaRow row(final Meta meta) {
        return new MetaRow(meta, null, Map.of());
    }

    /// @return A feed document as it is now.
    FeedDoc feedDoc(final String uuid) {
        return feeds.feed(uuid);
    }
}
