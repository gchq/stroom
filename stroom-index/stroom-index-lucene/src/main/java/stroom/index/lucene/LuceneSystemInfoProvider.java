/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.lucene;

import stroom.docref.DocRef;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexSystemInfoProvider;
import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.IndexFieldCache;
import stroom.util.date.DateUtil;
import stroom.util.io.PathCreator;
import stroom.util.shared.NullSafe;
import stroom.util.sysinfo.SystemInfoResult;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.inject.Inject;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

class LuceneSystemInfoProvider implements IndexSystemInfoProvider {

    private final IndexShardWriterCache indexShardWriterCache;
    private final MetaService metaService;
    private final NodeInfo nodeInfo;
    private final PathCreator pathCreator;
    private final IndexFieldCache indexFieldCache;

    @Inject
    public LuceneSystemInfoProvider(final IndexShardWriterCache indexShardWriterCache,
                                    final MetaService metaService,
                                    final NodeInfo nodeInfo,
                                    final PathCreator pathCreator,
                                    final IndexFieldCache indexFieldCache) {
        this.indexShardWriterCache = indexShardWriterCache;
        this.metaService = metaService;
        this.nodeInfo = nodeInfo;
        this.pathCreator = pathCreator;
        this.indexFieldCache = indexFieldCache;
    }

    @Override
    public SystemInfoResult getSystemInfo(final IndexShard indexShard, final Integer limit, final Long streamId) {
        // This may be null if we don't happen to have a writer
        final Optional<IndexShardWriter> optional = indexShardWriterCache.getIfPresent(indexShard.getId());
        final IndexWriter indexWriter = optional
                .map(indexShardWriter -> ((LuceneIndexShardWriter) indexShardWriter).getWriter())
                .orElse(null);

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
        final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
        IndexSearcher indexSearcher = null;
        try {
            indexSearcher = searcherManager.acquire();
            final StoredFields storedFields = indexSearcher.storedFields();
            final Query query = buildQuery(indexShard, streamId);

            final TopDocs topDocs = indexSearcher.search(query, limit);

            final Map<Long, Tuple2<String, LongAdder>> streamIdDocCounts = new TreeMap<>();
            for (final ScoreDoc scoreDoc : topDocs.scoreDocs) {
                consumeDocument(indexSearcher, storedFields, streamIdDocCounts, scoreDoc);
            }

            final Map<Long, Map<String, Object>> detailMap = streamIdDocCounts
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
        } catch (final Exception e) {
            throw new RuntimeException("Error acquiring index searcher: " + e.getMessage(), e);
        } finally {
            try {
                if (indexSearcher != null) {
                    searcherManager.release(indexSearcher);
                }
            } catch (final IOException e) {
                throw new RuntimeException("Error releasing index searcher: " + e.getMessage(), e);
            }
        }
    }

    private Query buildQuery(final IndexShard indexShard, final Long streamId) {
        if (streamId == null) {
            return new MatchAllDocsQuery();

        } else {
            final DocRef docRef = DocRef.builder()
                    .uuid(indexShard.getIndexUuid())
                    .type(LuceneIndexDoc.TYPE)
                    .build();
            final IndexField streamIdField = indexFieldCache.get(docRef, IndexConstants.STREAM_ID);
            if (streamIdField == null) {
                throw new RuntimeException("Can't find field " + IndexConstants.STREAM_ID);
            }

            if (FieldType.ID.equals(streamIdField.getFldType())) {
                return LongField.newExactQuery(IndexConstants.STREAM_ID, streamId);
            } else {
                return new TermQuery(new Term(IndexConstants.STREAM_ID, streamId.toString()));
            }
        }
    }

    private void consumeDocument(final IndexSearcher indexSearcher,
                                 final StoredFields storedFields,
                                 final Map<Long, Tuple2<String, LongAdder>> streamIdDocCounts,
                                 final ScoreDoc scoreDoc) throws IOException {
        final Document doc = storedFields.document(scoreDoc.doc);
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
