package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneVersion;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.IndexFieldCache;

public interface LuceneProvider {

    LuceneShardSearcher createLuceneShardSearcher(DocRef indexDocRef,
                                                  IndexFieldCache indexFieldCache,
                                                  ExpressionOperator expression,
                                                  DateTimeSettings dateTimeSettings,
                                                  QueryKey queryKey);

    HighlightProvider createHighlightProvider();

    IndexShardWriter createIndexShardWriter(IndexShard indexShard,
                                            int maxDocumentCount);

    IndexSystemInfoProvider getIndexSystemInfoProvider();

    LuceneVersion getLuceneVersion();
}
