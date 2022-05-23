package stroom.search.impl.shard;

import stroom.docref.DocRef;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexStore;
import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldType;
import stroom.index.shared.IndexShard;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.util.NullSafe;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.BadRequestException;

@Singleton
public class IndexShardSystemInfo implements HasSystemInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardSystemInfo.class);

    private static final String PARAM_NAME_STREAM_ID = "streamId";
    private static final String PARAM_NAME_LIMIT = "limit";
    private static final String PARAM_NAME_SHARD_ID = "shardId";

    private final IndexShardSearcherCacheImpl indexShardSearcherCache;
    private final MetaService metaService;
    private final IndexShardDao indexShardDao;
    private final IndexStore indexStore;

    @Inject
    public IndexShardSystemInfo(final IndexShardSearcherCacheImpl indexShardSearcherCache,
                                final MetaService metaService,
                                final IndexShardDao indexShardDao,
                                final IndexStore indexStore) {
        this.indexShardSearcherCache = indexShardSearcherCache;
        this.metaService = metaService;
        this.indexShardDao = indexShardDao;
        this.indexStore = indexStore;
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

    private SystemInfoResult getSystemInfo(final long shardId,
                                           final Integer limit,
                                           final Long streamId) {

        final IndexShardSearcher indexShardSearcher = indexShardSearcherCache.get(shardId);
        final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
        try {
            final IndexSearcher indexSearcher = searcherManager.acquire();
            try {
//                InfoCollector collector = new InfoCollector(indexSearcher, metaService);

//                indexSearcher.search(new MatchAllDocsQuery(), collector);
                final Map<Long, Tuple2<String, LongAdder>> streamIdDocCounts = new TreeMap<>();
//                final Query query = streamId != null
//                        ? NumericRangeQuery.newLongRange(
//                        IndexConstants.STREAM_ID, streamId, streamId, true, true)
//                        : new MatchAllDocsQuery();
//                final Query query = streamId != null
//                        ? new TermQuery(new Term(IndexConstants.STREAM_ID, streamId.toString()))
//                        : new MatchAllDocsQuery();

//                final Query query = NumericRangeQuery.newLongRange(
//                        IndexConstants.STREAM_ID, 5000L, 6000L, true, true);
//                final Query query;
//                if (streamId != null) {
//                    query = new BooleanQuery.Builder()
//                            .add(NumericRangeQuery.newIntRange(
//                                            IndexConstants.STREAM_ID,
//                                            Math.toIntExact(streamId),
//                                            Math.toIntExact(streamId),
//                                            true,
//                                            true),
//                                    Occur.SHOULD)
//                            .add(new TermQuery(new Term(IndexConstants.STREAM_ID, streamId.toString())), Occur.SHOULD)
//                            .build();
//                } else {
//                    query = new MatchAllDocsQuery();
//                }
                final Query query = buildQuery(shardId, streamId);

                final TopDocs topDocs = indexSearcher.search(query, limit);

                for (final ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    consumeDocument(indexSearcher, streamIdDocCounts, scoreDoc);
                }

//                final Map<Long, Tuple2<String, LongAdder>> streamIdDocCounts = collector.getStreamIdDocCounts();

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
                return SystemInfoResult.builder()
                        .addDetail("ShardId", shardId)
                        .addDetail("DocCountsByStreamId", detailMap)
                        .addDetail("StreamCount", streamIdDocCounts.size())
                        .addDetail("DocCount", docCount)
                        .addDetail("DocLimit", limit)
                        .build();
            } finally {
                searcherManager.release(indexSearcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error acquiring index searcher: " + e.getMessage(), e);
        }
    }

    private Query buildQuery(final long shardId, final Long streamId) {
        if (streamId == null) {
            return new MatchAllDocsQuery();
        } else {
            final IndexShard indexShard = indexShardDao.fetch(shardId)
                    .orElseThrow(() -> new BadRequestException("Unknown shardId " + shardId));

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
//        final IndexableField field = doc.getField(IndexConstants.STREAM_ID);
//        LOGGER.info("field type {}", field.fieldType());
        final String streamIdStr = doc.get(IndexConstants.STREAM_ID);
        if (streamIdStr != null && streamIdDocCounts.size() <= 1_000) {
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

    @Override
    public Map<String, String> getParamInfo() {
        return Map.of(
                PARAM_NAME_STREAM_ID, "The id of a specific stream to query for.",
                PARAM_NAME_LIMIT, "A limit on the number of docs to return"
        );
    }

    private static class InfoCollector extends SimpleCollector {

        private final IndexSearcher indexSearcher;
        private final MetaService metaService;

        final Map<Long, Tuple2<String, LongAdder>> streamIdDocCounts = new TreeMap<>();

        private InfoCollector(final IndexSearcher indexSearcher, final MetaService metaService) {
            this.indexSearcher = indexSearcher;
            this.metaService = metaService;
        }

        @Override
        public void collect(final int docId) throws IOException {
            final Document doc = indexSearcher.doc(docId);
            final String streamIdStr = doc.get(IndexConstants.STREAM_ID);
            if (streamIdStr != null && streamIdDocCounts.size() <= 1_000) {
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

        @Override
        public boolean needsScores() {
            return false;
        }

        public Map<Long, Tuple2<String, LongAdder>> getStreamIdDocCounts() {
            return streamIdDocCounts;
        }
    }
}
