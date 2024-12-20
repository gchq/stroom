package stroom.docstore.shared;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.query.shared.QueryDoc;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.script.shared.ScriptDoc;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.state.shared.ScyllaDbDoc;
import stroom.state.shared.StateDoc;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.svg.shared.SvgImage;
import stroom.view.shared.ViewDoc;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.xmlschema.shared.XmlSchemaDoc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DocumentTypeRegistry {

    private static final Map<String, DocumentType> MAP = new HashMap<>();

    public static final DocumentType SYSTEM_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SYSTEM,
            ExplorerConstants.SYSTEM,
            ExplorerConstants.SYSTEM,
            SvgImage.DOCUMENT_SYSTEM);
    public static final DocumentType FAVOURITES_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SYSTEM,
            "Favourites",
            "Favourites",
            SvgImage.DOCUMENT_FAVOURITES);
    public static final DocumentType FOLDER_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.STRUCTURE,
            ExplorerConstants.FOLDER_TYPE,
            ExplorerConstants.FOLDER_TYPE,
            SvgImage.FOLDER);
    public static final DocumentType PROCESSOR_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.DATA_PROCESSING,
            "Processor",
            "Processor",
            SvgImage.DOCUMENT_PIPELINE);
    public static final DocumentType PROCESSOR_FILTER_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.DATA_PROCESSING,
            "ProcessorFilter",
            "Processor Filter",
            SvgImage.FILTER);
    public static final DocumentType ANALYTICS_STORE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SEARCH,
            "Analytics",
            "Analytics",
            SvgImage.DOCUMENT_SEARCHABLE);

    static {
        put(ANALYTICS_STORE_DOCUMENT_TYPE);
        put(AnalyticRuleDoc.DOCUMENT_TYPE);
        put(DashboardDoc.DOCUMENT_TYPE);
        put(DictionaryDoc.DOCUMENT_TYPE);
        put(DocumentationDoc.DOCUMENT_TYPE);
        put(ElasticClusterDoc.DOCUMENT_TYPE);
        put(ElasticIndexDoc.DOCUMENT_TYPE);
        put(FAVOURITES_DOCUMENT_TYPE);
        put(FOLDER_DOCUMENT_TYPE);
        put(FeedDoc.DOCUMENT_TYPE);
        put(KafkaConfigDoc.DOCUMENT_TYPE);
        put(LuceneIndexDoc.DOCUMENT_TYPE);
        put(PROCESSOR_DOCUMENT_TYPE);
        put(PROCESSOR_FILTER_DOCUMENT_TYPE);
        put(PipelineDoc.DOCUMENT_TYPE);
        put(QueryDoc.DOCUMENT_TYPE);
        put(ReceiveDataRules.DOCUMENT_TYPE);
        put(S3ConfigDoc.DOCUMENT_TYPE);
        put(SYSTEM_DOCUMENT_TYPE);
        put(ScriptDoc.DOCUMENT_TYPE);
        put(ScyllaDbDoc.DOCUMENT_TYPE);
        put(SolrIndexDoc.DOCUMENT_TYPE);
        put(StateDoc.DOCUMENT_TYPE);
        put(StatisticStoreDoc.DOCUMENT_TYPE);
        put(StroomStatsStoreDoc.DOCUMENT_TYPE);
        put(TextConverterDoc.DOCUMENT_TYPE);
        put(ViewDoc.DOCUMENT_TYPE);
        put(VisualisationDoc.DOCUMENT_TYPE);
        put(XmlSchemaDoc.DOCUMENT_TYPE);
        put(XsltDoc.DOCUMENT_TYPE);
    }

    private static void put(final DocumentType documentType) {
        final DocumentType existing = MAP.put(documentType.getType(), documentType);
        if (existing != null) {
            throw new RuntimeException("A document type is already registered for '" + documentType.getType() + "'");
        }
    }

    public static DocumentType get(final String type) {
        return MAP.get(type);
    }

    public static SvgImage getIcon(final String type) {
        final DocumentType documentType = MAP.get(type);
        if (documentType == null) {
            return SvgImage.DOCUMENT_SEARCHABLE;
        }
        return documentType.getIcon();
    }

    public static Collection<DocumentType> getTypes() {
        return MAP.values();
    }
}
