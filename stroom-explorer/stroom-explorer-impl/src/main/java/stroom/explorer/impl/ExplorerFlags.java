package stroom.explorer.impl;

import stroom.explorer.shared.NodeFlag;
import stroom.index.shared.LuceneIndexDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.state.shared.StateDoc;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExplorerFlags {
    private static final Map<String, NodeFlag> DOC_TYPE_TO_DEFAULT_FLAG_MAP = new HashMap<>();

    static {
        DOC_TYPE_TO_DEFAULT_FLAG_MAP.put(StatisticStoreDoc.DOCUMENT_TYPE, NodeFlag.DATA_SOURCE);
        DOC_TYPE_TO_DEFAULT_FLAG_MAP.put(StroomStatsStoreDoc.DOCUMENT_TYPE, NodeFlag.DATA_SOURCE);
        DOC_TYPE_TO_DEFAULT_FLAG_MAP.put(LuceneIndexDoc.DOCUMENT_TYPE, NodeFlag.DATA_SOURCE);
        DOC_TYPE_TO_DEFAULT_FLAG_MAP.put(ElasticIndexDoc.DOCUMENT_TYPE, NodeFlag.DATA_SOURCE);
        DOC_TYPE_TO_DEFAULT_FLAG_MAP.put(SolrIndexDoc.DOCUMENT_TYPE, NodeFlag.DATA_SOURCE);
        DOC_TYPE_TO_DEFAULT_FLAG_MAP.put(StateDoc.DOCUMENT_TYPE, NodeFlag.DATA_SOURCE);
    }

    // Could return a set of flags really, but for not one is fine
    public static Optional<NodeFlag> getStandardFlagByDocType(final String docType) {
        return Optional.ofNullable(docType)
                .map(DOC_TYPE_TO_DEFAULT_FLAG_MAP::get);
    }
}
