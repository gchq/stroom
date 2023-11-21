package stroom.index.lucene553;

import stroom.docref.DocRef;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexSystemInfoProvider;
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
import stroom.util.io.PathCreator;
import stroom.util.sysinfo.SystemInfoResult;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.lucene553.document.Document;
import org.apache.lucene553.index.IndexWriter;
import org.apache.lucene553.index.Term;
import org.apache.lucene553.search.IndexSearcher;
import org.apache.lucene553.search.MatchAllDocsQuery;
import org.apache.lucene553.search.NumericRangeQuery;
import org.apache.lucene553.search.Query;
import org.apache.lucene553.search.ScoreDoc;
import org.apache.lucene553.search.SearcherManager;
import org.apache.lucene553.search.TermQuery;
import org.apache.lucene553.search.TopDocs;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

class Lucene553SystemInfoProvider implements IndexSystemInfoProvider {

    private final IndexShardWriterCache indexShardWriterCache;
    private final MetaService metaService;
    private final IndexStore indexStore;
    private final NodeInfo nodeInfo;
    private final PathCreator pathCreator;

    @Inject
    public Lucene553SystemInfoProvider(final IndexShardWriterCache indexShardWriterCache,
                                       final MetaService metaService,
                                       final IndexStore indexStore,
                                       final NodeInfo nodeInfo,
                                       final PathCreator pathCreator) {
        this.indexShardWriterCache = indexShardWriterCache;
        this.metaService = metaService;
        this.indexStore = indexStore;
        this.nodeInfo = nodeInfo;
        this.pathCreator = pathCreator;
    }

    @Override
    public SystemInfoResult getSystemInfo(final IndexShard indexShard, final Integer limit, final Long streamId) {
        // This may be null if we don't happen to have a writer
        final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardId(indexShard.getId());
        final IndexWriter indexWriter = NullSafe
                .get(indexShardWriter, w -> ((Lucene553IndexShardWriter) w).getWriter());

        IndexShardSearcher indexShardSearcher = null;
        try {
            indexShardSearcher = new IndexShardSearcher(indexShard, indexWriter, pathCreator);
            return searchShard(indexShardSearcher, indexShard, limit, streamId);

        } finally {
            if (indexShardSearcher != null) {
                indexShardSearcher.destroy();
            }
        }
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

            return new SystemInfoResult.Builder(this.getClass().getName())
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
