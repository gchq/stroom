package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneVersion;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.IndexFieldCache;

public interface LuceneProvider {

    LuceneShardSearcher createLuceneShardSearcher(DocRef indexDocRef,
                                                  IndexFieldCache indexFieldCache,
                                                  ExpressionOperator expression,
                                                  DateTimeSettings dateTimeSettings,
                                                  QueryKey queryKey);

    HighlightProvider createHighlightProvider();

    IndexShardWriter createIndexShardWriter(IndexShardKey indexShardKey,
                                            IndexShard indexShard,
                                            int maxDocumentCount);

    IndexSystemInfoProvider getIndexSystemInfoProvider();

    LuceneVersion getLuceneVersion();
}
