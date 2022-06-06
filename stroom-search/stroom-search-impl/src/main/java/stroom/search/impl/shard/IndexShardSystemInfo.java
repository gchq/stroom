package stroom.search.impl.shard;

import stroom.docref.DocRef;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldType;
import stroom.index.shared.IndexShard;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.BadRequestException;

/**
 * Provides system information for inspecting index shards.
 * Gets counts of documents grouped by stream id.
 * Requires the shardId as a query parameter.
 */
@Singleton
public class IndexShardSystemInfo implements HasSystemInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardSystemInfo.class);

    private static final String PARAM_NAME_STREAM_ID = "streamId";
    private static final String PARAM_NAME_LIMIT = "limit";
    private static final String PARAM_NAME_SHARD_ID = "shardId";

    private final IndexShardWriterCache indexShardWriterCache;
    private final MetaService metaService;
    private final IndexShardService indexShardService;
    private final IndexStore indexStore;
    private final NodeInfo nodeInfo;

    @Inject
    public IndexShardSystemInfo(final IndexShardWriterCache indexShardWriterCache,
                                final MetaService metaService,
                                final IndexShardService indexShardService,
                                final IndexStore indexStore,
                                final NodeInfo nodeInfo) {
        this.indexShardWriterCache = indexShardWriterCache;
        this.metaService = metaService;
        this.indexShardService = indexShardService;
        this.indexStore = indexStore;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        throw new BadRequestException(
                "You must provide a shard ID using the query parameter " + PARAM_NAME_SHARD_ID);
    }

    @Override
    public SystemInfoResult getSystemInfo(final Map<String, String> params) {
        final int limit = NullSafe.getOrElse(
                params,
                map -> map.get(PARAM_NAME_LIMIT),
                Integer::valueOf,
                10_000);
        final Long requestedStreamId = NullSafe.get(params, map -> map.get(PARAM_NAME_STREAM_ID), Long::valueOf);
        final long shardId = NullSafe.getAsOptional(
                        params,
                        map -> map.get(PARAM_NAME_SHARD_ID),
                        Long::parseLong)
                .orElseThrow(() ->
                        new BadRequestException(
                                "You must provide a shard ID using the query parameter " + PARAM_NAME_SHARD_ID));
        return getSystemInfo(shardId, limit, requestedStreamId);
    }

    @Override
    public List<ParamInfo> getParamInfo() {
        return List.of(
                ParamInfo.mandatoryParam(
                        PARAM_NAME_SHARD_ID,
                        "The id of the index shard to inspect."),
                ParamInfo.optionalParam(
                        PARAM_NAME_STREAM_ID,
                        "The id of a specific stream to query for."),
                ParamInfo.optionalParam(PARAM_NAME_LIMIT,
                        "A limit on the number of docs to return")
        );
    }

    private SystemInfoResult getSystemInfo(final long shardId,
                                           final Integer limit,
                                           final Long streamId) {


        try {
            final IndexShard indexShard = indexShardService.loadById(shardId);
            if (indexShard == null) {
                throw new RuntimeException("Unknown shardId " + shardId);
            }
            if (doesThisNodeOwnTheShard(indexShard)) {
                // This may be null if we don't happen to have a writer
                final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardId(shardId);
                final IndexWriter indexWriter = NullSafe.get(indexShardWriter, IndexShardWriter::getWriter);

                IndexShardSearcher indexShardSearcher = null;
                try {
                    indexShardSearcher = new IndexShardSearcher(indexShard, indexWriter);
                    return searchShard(indexShardSearcher, indexShard, limit, streamId);

                } finally {
                    if (indexShardSearcher != null) {
                        indexShardSearcher.destroy();
                    }
                }
            } else {
                return SystemInfoResult.builder(this)
                        .addDetail("ShardId", indexShard.getId())
                        .addDetail("ThisNode", nodeInfo.getThisNodeName())
                        .addDetail("OwningNode", indexShard.getNodeName())
                        .addDetail("Owned", false)
                        .build();
            }
        } catch (RuntimeException e) {
            return SystemInfoResult.builder(this)
                    .addError(e)
                    .build();
        }
    }

    private boolean doesThisNodeOwnTheShard(final IndexShard indexShard) {
        final String thisNodeName = nodeInfo.getThisNodeName();
        final String shardNodeName = indexShard.getNodeName();
        return thisNodeName.equals(shardNodeName);
    }

    private SystemInfoResult searchShard(final IndexShardSearcher indexShardSearcher,
                                         final IndexShard indexShard,
                                         final Integer limit,
                                         final Long streamId) {
        SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
        IndexSearcher indexSearcher = null;
        try {
            indexSearcher = searcherManager.acquire();
            final Query query = buildQuery(indexShard, streamId);

            final TopDocs topDocs = indexSearcher.search(query, limit);

            final Map<Long, Tuple2<String, LongAdder>> streamIdDocCounts = new TreeMap<>();
            for (final ScoreDoc scoreDoc : topDocs.scoreDocs) {
                consumeDocument(indexSearcher, streamIdDocCounts, scoreDoc);
            }

            var detailMap = streamIdDocCounts
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> {
                                final Tuple2<String, LongAdder> tuple2 = entry.getValue();
                                return Map.of(
                                        "MetaStatus", tuple2._1,
                                        "DocCount", tuple2._2.sum());
                            },
                            (map1, map2) -> map1,
                            TreeMap::new));
            final long docCount = streamIdDocCounts.values()
                    .stream()
                    .mapToLong(tuple2 -> tuple2._2.sum())
                    .sum();

            return SystemInfoResult.builder(this)
                    .description("Details of the contents of the shard")
                    .addDetail("ShardId", indexShard.getId())
                    .addDetail("ThisNode", nodeInfo.getThisNodeName())
                    .addDetail("OwningNode", indexShard.getNodeName())
                    .addDetail("Owned", true)
                    .addDetail("DocCountsByStreamId", detailMap)
                    .addDetail("StreamCount", streamIdDocCounts.size())
                    .addDetail("DocCount", docCount)
                    .addDetail("DocLimit", limit)
                    .addDetail("PartitionFromTime",
                            DateUtil.createNormalDateTimeString(indexShard.getPartitionFromTime()))
                    .addDetail("PartitionToTime",
                            DateUtil.createNormalDateTimeString(indexShard.getPartitionToTime()))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Error acquiring index searcher: " + e.getMessage(), e);
        } finally {
            try {
                if (indexSearcher != null) {
                    searcherManager.release(indexSearcher);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error releasing index searcher: " + e.getMessage(), e);
            }
        }
    }

    private Query buildQuery(final IndexShard indexShard, final Long streamId) {
        if (streamId == null) {
            return new MatchAllDocsQuery();
        } else {
            final IndexDoc indexDoc = indexStore.readDocument(DocRef.builder()
                    .uuid(indexShard.getIndexUuid())
                    .type(IndexDoc.DOCUMENT_TYPE)
                    .build());
            Objects.requireNonNull(indexDoc);

            IndexField streamIdField = indexDoc.getFields().stream()
                    .filter(indexField -> indexField.getFieldName().equals(IndexConstants.STREAM_ID))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Can't find field " + IndexConstants.STREAM_ID));

            if (IndexFieldType.ID.equals(streamIdField.getFieldType())) {
                return NumericRangeQuery.newLongRange(
                        IndexConstants.STREAM_ID,
                        streamId,
                        streamId,
                        true,
                        true);
            } else {
                return new TermQuery(new Term(IndexConstants.STREAM_ID, streamId.toString()));
            }
        }
    }

    private void consumeDocument(final IndexSearcher indexSearcher,
                                 final Map<Long, Tuple2<String, LongAdder>> streamIdDocCounts,
                                 final ScoreDoc scoreDoc) throws IOException {
        final Document doc = indexSearcher.doc(scoreDoc.doc);
        final String streamIdStr = doc.get(IndexConstants.STREAM_ID);
        if (streamIdStr != null) {
            final long streamId = Long.parseLong(streamIdStr);
            final Tuple2<String, LongAdder> tuple2 = streamIdDocCounts.computeIfAbsent(
                    streamId,
                    k -> {
                        // TODO not very efficient to hit each meta individually,
                        //  better to ask for the statuses of a batch
                        final Meta meta = metaService.getMeta(streamId);
                        final String metaStatus = NullSafe.getOrElse(
                                meta,
                                Meta::getStatus,
                                Status::getDisplayValue,
                                "Not present");
                        return Tuple.of(
                                metaStatus,
                                new LongAdder());
                    });
            // Increment the doc count
            tuple2._2.increment();
        }
    }
}
