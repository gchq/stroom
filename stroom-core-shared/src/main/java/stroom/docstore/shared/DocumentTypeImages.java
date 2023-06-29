package stroom.docstore.shared;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.IndexDoc;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.query.shared.QueryDoc;
import stroom.script.shared.ScriptDoc;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.svg.shared.SvgImage;
import stroom.view.shared.ViewDoc;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.xmlschema.shared.XmlSchemaDoc;

public class DocumentTypeImages {

    public static SvgImage get(final String type) {
        switch (type) {
            case AnalyticRuleDoc.DOCUMENT_TYPE:
                return AnalyticRuleDoc.ICON;
            case "AnalyticOutputStore":
                return SvgImage.DOCUMENT_ANALYTIC_OUTPUT_STORE;
            case "AnnotationsIndex":
                return SvgImage.DOCUMENT_ANNOTATIONS_INDEX;
            case DashboardDoc.DOCUMENT_TYPE:
                return DashboardDoc.ICON;
            case DictionaryDoc.DOCUMENT_TYPE:
                return DictionaryDoc.ICON;
            case ElasticClusterDoc.DOCUMENT_TYPE:
                return ElasticClusterDoc.ICON;
            case ElasticIndexDoc.DOCUMENT_TYPE:
                return ElasticIndexDoc.ICON;
            case "Favourites":
                return SvgImage.DOCUMENT_FAVOURITES;
            case FeedDoc.DOCUMENT_TYPE:
                return FeedDoc.ICON;
            case "Folder":
                return SvgImage.DOCUMENT_FOLDER;
            case IndexDoc.DOCUMENT_TYPE:
                return IndexDoc.ICON;
            case KafkaConfigDoc.DOCUMENT_TYPE:
                return KafkaConfigDoc.ICON;
            case PipelineDoc.DOCUMENT_TYPE:
                return PipelineDoc.ICON;
            case QueryDoc.DOCUMENT_TYPE:
                return QueryDoc.ICON;
            case "ReceiveDataRuleSet":
                return SvgImage.DOCUMENT_RECEIVE_DATA_RULE_SET;
            case ScriptDoc.DOCUMENT_TYPE:
                return ScriptDoc.ICON;
//            case "searchable":
//                return SvgImage.DOCUMENT_searchable;
            case "SelectAllOrNone":
                return SvgImage.DOCUMENT_SELECT_ALL_OR_NONE;
            case SolrIndexDoc.DOCUMENT_TYPE:
                return SolrIndexDoc.ICON;
            case StatisticStoreDoc.DOCUMENT_TYPE:
                return StatisticStoreDoc.ICON;
            case StroomStatsStoreDoc.DOCUMENT_TYPE:
                return StroomStatsStoreDoc.ICON;
            case "System":
                return SvgImage.DOCUMENT_SYSTEM;
            case TextConverterDoc.DOCUMENT_TYPE:
                return TextConverterDoc.ICON;
            case ViewDoc.DOCUMENT_TYPE:
                return ViewDoc.ICON;
            case VisualisationDoc.DOCUMENT_TYPE:
                return VisualisationDoc.ICON;
            case XmlSchemaDoc.DOCUMENT_TYPE:
                return XmlSchemaDoc.ICON;
            case XsltDoc.DOCUMENT_TYPE:
                return XsltDoc.ICON;
        }
        return null;
    }
}
