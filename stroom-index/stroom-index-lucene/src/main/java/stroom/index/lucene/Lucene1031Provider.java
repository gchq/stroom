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
import stroom.index.impl.HighlightProvider;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexSystemInfoProvider;
import stroom.index.impl.LuceneProvider;
import stroom.index.impl.LuceneShardSearcher;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneVersion;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.IndexFieldCache;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Lucene1031Provider implements LuceneProvider {

    private final LuceneHighlightProvider highlightProvider;
    private final LuceneShardSearcherFactory shardSearcherFactory;
    private final LuceneSystemInfoProvider systemInfoProvider;
    private final LuceneIndexShardWriterFactory indexShardWriterFactory;

    @Inject
    Lucene1031Provider(final LuceneHighlightProvider highlightProvider,
                       final LuceneShardSearcherFactory shardSearcherFactory,
                       final LuceneSystemInfoProvider systemInfoProvider,
                       final LuceneIndexShardWriterFactory indexShardWriterFactory) {
        this.highlightProvider = highlightProvider;
        this.shardSearcherFactory = shardSearcherFactory;
        this.systemInfoProvider = systemInfoProvider;
        this.indexShardWriterFactory = indexShardWriterFactory;
    }

    @Override
    public LuceneShardSearcher createLuceneShardSearcher(final DocRef indexDocRef,
                                                         final IndexFieldCache indexFieldCache,
                                                         final ExpressionOperator expression,
                                                         final DateTimeSettings dateTimeSettings,
                                                         final QueryKey queryKey) {
        return shardSearcherFactory.create(
                indexDocRef,
                indexFieldCache,
                expression,
                dateTimeSettings,
                queryKey);
    }

    @Override
    public HighlightProvider createHighlightProvider() {
        return highlightProvider;
    }

    @Override
    public IndexShardWriter createIndexShardWriter(final IndexShard indexShard,
                                                   final int maxDocumentCount) {
        return indexShardWriterFactory.create(
                indexShard,
                maxDocumentCount);
    }

    @Override
    public IndexSystemInfoProvider getIndexSystemInfoProvider() {
        return systemInfoProvider;
    }

    @Override
    public LuceneVersion getLuceneVersion() {
        return LuceneVersion.LUCENE_10_3_1;
    }
}
