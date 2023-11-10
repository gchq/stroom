package stroom.index.impl;

import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.IndexFieldsMap;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneVersion;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.QueryKey;

public interface LuceneProvider {

    LuceneShardSearcher createLuceneShardSearcher(ExpressionOperator expression,
                                                  IndexFieldsMap indexFieldsMap,
                                                  DateTimeSettings dateTimeSettings,
                                                  QueryKey queryKey);

    HighlightProvider createHighlightProvider();

    IndexShardWriter createIndexShardWriter(IndexStructure indexStructure,
                                            IndexShardKey indexShardKey,
                                            IndexShard indexShard);

    IndexSystemInfoProvider getIndexSystemInfoProvider();

    LuceneVersion getLuceneVersion();
}
